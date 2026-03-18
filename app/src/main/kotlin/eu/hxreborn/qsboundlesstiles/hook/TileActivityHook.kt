package eu.hxreborn.qsboundlesstiles.hook

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.ui.EventType
import eu.hxreborn.qsboundlesstiles.util.log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

private const val CUSTOM_TILE_CLASS = "com.android.systemui.qs.external.CustomTile"
private const val TILE_LIFECYCLE_CLASS =
    "com.android.systemui.qs.external.TileLifecycleManager"
private const val TILE_SERVICE_MANAGER_CLASS =
    "com.android.systemui.qs.external.TileServiceManager"

@SuppressLint("StaticFieldLeak")
object TileActivityHook {
    @Volatile var mComponentField: Field? = null
        private set

    @Volatile var mServiceManagerField: Field? = null
        private set

    @Volatile var mBoundField: Field? = null
        private set

    val pendingClicks = ConcurrentHashMap<String, Long>()
    private val labelCache = ConcurrentHashMap<String, String>()

    @Volatile var systemUiContext: Context? = null
        private set

    fun setContext(context: Context) {
        systemUiContext = context
    }

    fun resolveLabel(componentName: ComponentName): String =
        labelCache.getOrPut(componentName.flattenToShortString()) {
            val ctx = systemUiContext ?: return@getOrPut componentName.shortClassName
            runCatching {
                val pm = ctx.packageManager
                val serviceLabel =
                    pm.getServiceInfo(componentName, 0).loadLabel(pm).toString()
                val appLabel =
                    pm
                        .getApplicationInfo(componentName.packageName, 0)
                        .loadLabel(pm)
                        .toString()
                serviceLabel
                    .takeIf { it != appLabel }
                    ?.let { "$it ($appLabel)" } ?: appLabel
            }.getOrDefault(componentName.shortClassName)
        }

    fun hook(
        module: XposedInterface,
        classLoader: ClassLoader,
    ): Int {
        var hookStatus = 0

        val customTileClass = classLoader.loadOrNull(CUSTOM_TILE_CLASS)
        val tileServiceManagerClass = classLoader.loadOrNull(TILE_SERVICE_MANAGER_CLASS)
        val lifecycleClass = classLoader.loadOrNull(TILE_LIFECYCLE_CLASS)

        if (customTileClass == null) {
            log("CustomTile class not found, tile activity tracking unavailable")
            return 0
        }

        mComponentField = customTileClass.accessibleFieldOrNull("mComponent")
        mServiceManagerField = customTileClass.accessibleFieldOrNull("mServiceManager")
        mBoundField = tileServiceManagerClass?.accessibleFieldOrNull("mBound")

        customTileClass.declaredMethods
            .find { it.name == "handleClick" }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val tile = chain.thisObject ?: return@intercept chain.proceed()
                    val component =
                        runCatching {
                            mComponentField?.get(tile) as? ComponentName
                        }.getOrNull() ?: return@intercept chain.proceed()
                    val isBound =
                        runCatching {
                            val mgr = mServiceManagerField?.get(tile)
                            mgr?.let { m -> mBoundField?.getBoolean(m) } ?: false
                        }.getOrDefault(false)
                    if (!isBound) {
                        pendingClicks[component.flattenToString()] = System.nanoTime()
                        return@intercept chain.proceed()
                    }
                    val startNanos = System.nanoTime()
                    val label = resolveLabel(component)
                    val result = chain.proceed()
                    val durationMs = (System.nanoTime() - startNanos) / 1_000_000
                    PrefsManager.recordTileEvent(
                        EventType.WARM,
                        label,
                        durationMs,
                        null,
                    )
                    result
                }
                hookStatus = hookStatus or TileServicesHook.HOOK_HANDLE_CLICK
                log("Hooked CustomTile.handleClick")
            } ?: log("handleClick not found on CustomTile")

        lifecycleClass
            ?.declaredMethods
            ?.find { it.name == "onServiceConnected" && it.parameterCount == 2 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val cn =
                        chain.args[0] as? ComponentName
                            ?: return@intercept result
                    val clickTime =
                        pendingClicks.remove(cn.flattenToString())
                            ?: return@intercept result
                    val durationMs = (System.nanoTime() - clickTime) / 1_000_000
                    PrefsManager.recordTileEvent(
                        EventType.COLD_START,
                        resolveLabel(cn),
                        durationMs,
                        null,
                    )
                    result
                }
                hookStatus = hookStatus or TileServicesHook.HOOK_ON_SERVICE_CONNECTED
                log("Hooked TileLifecycleManager.onServiceConnected")
            } ?: log("onServiceConnected not found on TileLifecycleManager")

        lifecycleClass
            ?.declaredMethods
            ?.find {
                (it.name == "onBindingDied" && it.parameterCount == 1) ||
                    (it.name == "onServiceDisconnected" && it.parameterCount == 1)
            }?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val cn =
                        chain.args[0] as? ComponentName
                            ?: return@intercept result
                    PrefsManager.recordTileEvent(
                        EventType.SERVICE_DIED,
                        resolveLabel(cn),
                        null,
                        null,
                    )
                    result
                }
                hookStatus = hookStatus or TileServicesHook.HOOK_SERVICE_DIED
                log("Hooked TileLifecycleManager.${method.name}")
            } ?: log("onBindingDied/onServiceDisconnected not found")

        return hookStatus
    }
}
