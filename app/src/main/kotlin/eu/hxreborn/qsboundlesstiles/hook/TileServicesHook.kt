package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.ui.EventType
import eu.hxreborn.qsboundlesstiles.util.log
import eu.hxreborn.qsboundlesstiles.util.logDebug
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"

object TileServicesHook {
    const val HOOK_CONSTRUCTOR = 1
    const val HOOK_SET_MEMORY_PRESSURE = 2
    const val HOOK_RECALCULATE_BIND_ALLOWANCE = 4
    const val HOOK_HANDLE_CLICK = 8
    const val HOOK_ON_SERVICE_CONNECTED = 16
    const val HOOK_SERVICE_DIED = 32
    const val HOOK_ALL =
        HOOK_CONSTRUCTOR or HOOK_SET_MEMORY_PRESSURE or HOOK_RECALCULATE_BIND_ALLOWANCE or
            HOOK_HANDLE_CLICK or HOOK_ON_SERVICE_CONNECTED or HOOK_SERVICE_DIED

    @Volatile private var maxBoundField: Field? = null

    @Volatile private var recalculateMethod: Method? = null

    @Volatile var tileServicesInstance: Any? = null
        internal set

    fun hook(
        module: XposedInterface,
        classLoader: ClassLoader,
    ) {
        log("Hooking TileServices on API ${Build.VERSION.SDK_INT}")

        val tileServicesClass =
            classLoader.loadOrNull(TILE_SERVICES_CLASS) ?: run {
                log("TileServices class not found, aborting")
                PrefsManager.setHookStatus(0)
                return
            }

        maxBoundField = tileServicesClass.accessibleFieldOrNull("mMaxBound")
        if (maxBoundField == null) {
            log("mMaxBound field not found, aborting")
            PrefsManager.setHookStatus(0)
            return
        }

        var hookStatus = 0

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val ts = chain.thisObject ?: return@intercept result
                tileServicesInstance = ts
                extractContext(ts)
                applyUserMaxBound(ts)
                log("TileServices constructed, mMaxBound=${PrefsManager.maxBound}")
                PrefsManager.flushHookStatus()
                PrefsManager.recordTileEvent(
                    EventType.LIMIT_SET,
                    null,
                    null,
                    "mMaxBound=${PrefsManager.maxBound}",
                )
                result
            }
        }
        hookStatus = hookStatus or HOOK_CONSTRUCTOR

        tileServicesClass.declaredMethods
            .find { it.name == "setMemoryPressure" && it.parameterCount == 1 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val ts = chain.thisObject ?: return@intercept result
                    applyUserMaxBound(ts)
                    val memPressure = chain.args[0] as? Boolean ?: return@intercept result
                    logDebug {
                        "setMemoryPressure($memPressure): " +
                            "restored mMaxBound=${PrefsManager.maxBound}"
                    }
                    if (memPressure) {
                        PrefsManager.recordTileEvent(
                            EventType.MEM_PRESSURE,
                            null,
                            null,
                            "Memory pressure intercepted, limit preserved at " +
                                "${PrefsManager.maxBound}",
                        )
                    }
                    result
                }
                hookStatus = hookStatus or HOOK_SET_MEMORY_PRESSURE
            } ?: log("setMemoryPressure not found (removed in Android 15+, not needed)")

        tileServicesClass.declaredMethods
            .find { it.name == "recalculateBindAllowance" && it.parameterCount == 0 }
            ?.also { recalculateMethod = it.apply { isAccessible = true } }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    chain.thisObject?.let { ts ->
                        applyUserMaxBound(ts)
                        logDebug {
                            "recalculateBindAllowance: set mMaxBound=${PrefsManager.maxBound}"
                        }
                    }
                    chain.proceed()
                }
                hookStatus = hookStatus or HOOK_RECALCULATE_BIND_ALLOWANCE
            } ?: log("recalculateBindAllowance not found -- live binding updates unavailable")

        hookStatus = hookStatus or TileActivityHook.hook(module, classLoader)

        PrefsManager.setHookStatus(hookStatus)

        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("Live updated mMaxBound=$newValue")
                PrefsManager.recordTileEvent(
                    EventType.LIMIT_SET,
                    null,
                    null,
                    "mMaxBound=$newValue",
                )
            }
        }

        log("Hooked TileServices (status=0b${hookStatus.toString(2).padStart(6, '0')})")
    }

    fun extractContext(tileServices: Any) {
        val context =
            generateSequence<Class<*>>(tileServices.javaClass) { it.superclass }
                .take(20)
                .firstNotNullOfOrNull { cls ->
                    cls
                        .accessibleFieldOrNull("mContext")
                        ?.get(tileServices) as? android.content.Context
                }

        context?.let {
            TileActivityHook.setContext(it)
            return
        }

        runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            val app =
                activityThread
                    .getMethod("currentApplication")
                    .invoke(null) as? android.content.Context
            app?.let { TileActivityHook.setContext(it) }
        }.onFailure { log("Failed to extract SystemUI context", it) }
    }

    fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure { log("Failed to set mMaxBound", it) }
    }

    fun applyUserMaxBound(tileServices: Any) {
        val newMax = PrefsManager.maxBound.coerceAtLeast(Prefs.maxBound.default)
        setMaxBound(tileServices, newMax)
    }
}
