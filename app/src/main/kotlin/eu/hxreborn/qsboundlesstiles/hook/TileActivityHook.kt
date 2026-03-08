package eu.hxreborn.qsboundlesstiles.hook

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.ui.EventType
import eu.hxreborn.qsboundlesstiles.util.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

private const val CUSTOM_TILE_CLASS = "com.android.systemui.qs.external.CustomTile"
private const val TILE_LIFECYCLE_CLASS = "com.android.systemui.qs.external.TileLifecycleManager"
private const val TILE_SERVICE_MANAGER_CLASS = "com.android.systemui.qs.external.TileServiceManager"

@SuppressLint("StaticFieldLeak")
object TileActivityHook {
    @Volatile
    var mComponentField: Field? = null
        private set

    @Volatile
    var mServiceManagerField: Field? = null
        private set

    @Volatile
    var mBoundField: Field? = null
        private set

    val pendingClicks = ConcurrentHashMap<String, Long>()
    private val labelCache = ConcurrentHashMap<String, String>()

    @Volatile
    var systemUiContext: Context? = null
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
                    pm
                        .getServiceInfo(componentName, 0)
                        .loadLabel(pm)
                        .toString()
                val appLabel =
                    pm
                        .getApplicationInfo(componentName.packageName, 0)
                        .loadLabel(pm)
                        .toString()
                serviceLabel.takeIf { it != appLabel }?.let { "$it ($appLabel)" } ?: appLabel
            }.getOrDefault(componentName.shortClassName)
        }

    fun hook(
        module: XposedModule,
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
            ?.let {
                module.hook(it, HandleClickHooker::class.java)
                hookStatus = hookStatus or TileServicesHook.HOOK_HANDLE_CLICK
                log("Hooked CustomTile.handleClick")
            } ?: log("handleClick not found on CustomTile")

        lifecycleClass
            ?.declaredMethods
            ?.find { it.name == "onServiceConnected" && it.parameterCount == 2 }
            ?.let {
                module.hook(it, OnServiceConnectedHooker::class.java)
                hookStatus = hookStatus or TileServicesHook.HOOK_ON_SERVICE_CONNECTED
                log("Hooked TileLifecycleManager.onServiceConnected")
            } ?: log("onServiceConnected not found on TileLifecycleManager")

        lifecycleClass
            ?.declaredMethods
            ?.find {
                (it.name == "onBindingDied" && it.parameterCount == 1) ||
                    (it.name == "onServiceDisconnected" && it.parameterCount == 1)
            }?.let {
                module.hook(it, ServiceDiedHooker::class.java)
                hookStatus = hookStatus or TileServicesHook.HOOK_SERVICE_DIED
                log("Hooked TileLifecycleManager.${it.name}")
            } ?: log("onBindingDied/onServiceDisconnected not found")

        return hookStatus
    }
}

@XposedHooker
class HandleClickHooker : XposedInterface.Hooker {
    data class ClickContext(
        val startNanos: Long,
        val label: String,
    )

    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback): ClickContext? {
            val tile = callback.thisObject ?: return null
            val component =
                runCatching {
                    TileActivityHook.mComponentField?.get(tile) as? ComponentName
                }.getOrNull() ?: return null

            val isBound =
                runCatching {
                    val mgr = TileActivityHook.mServiceManagerField?.get(tile)
                    mgr?.let { TileActivityHook.mBoundField?.getBoolean(it) } ?: false
                }.getOrDefault(false)

            if (!isBound) {
                TileActivityHook.pendingClicks[component.flattenToString()] = System.nanoTime()
                return null
            }
            return ClickContext(System.nanoTime(), TileActivityHook.resolveLabel(component))
        }

        @JvmStatic
        @AfterInvocation
        fun after(
            @Suppress("UNUSED_PARAMETER") callback: AfterHookCallback,
            context: ClickContext?,
        ) {
            context ?: return
            val durationMs = (System.nanoTime() - context.startNanos) / 1_000_000
            PrefsManager.recordTileEvent(EventType.WARM, context.label, durationMs, null)
        }
    }
}

@XposedHooker
class OnServiceConnectedHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val componentName =
                callback.args?.getOrNull(0) as? ComponentName
                    ?: return

            val key = componentName.flattenToString()
            val clickTime =
                TileActivityHook.pendingClicks.remove(key)
                    ?: return
            val durationMs =
                (System.nanoTime() - clickTime) / 1_000_000
            val label =
                TileActivityHook.resolveLabel(componentName)
            PrefsManager.recordTileEvent(
                EventType.COLD_START,
                label,
                durationMs,
                null,
            )
        }
    }
}

@XposedHooker
class ServiceDiedHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val componentName =
                callback.args?.getOrNull(0) as? ComponentName
                    ?: return
            val label =
                TileActivityHook.resolveLabel(componentName)
            PrefsManager.recordTileEvent(
                EventType.SERVICE_DIED,
                label,
                null,
                null,
            )
        }
    }
}
