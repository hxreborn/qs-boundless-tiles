package eu.hxreborn.qsboundlesstiles.hook

import android.content.Context
import android.os.Build
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
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
    const val HOOK_ALL =
        HOOK_CONSTRUCTOR or HOOK_SET_MEMORY_PRESSURE or HOOK_RECALCULATE_BIND_ALLOWANCE

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
                result
            }
        }
        hookStatus = hookStatus or HOOK_CONSTRUCTOR

        tileServicesClass.declaredMethods
            .find { it.name == "setMemoryPressure" && it.parameterCount == 1 }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    chain.thisObject?.let { ts ->
                        applyUserMaxBound(ts)
                        logDebug {
                            "setMemoryPressure: restored mMaxBound=${PrefsManager.maxBound}"
                        }
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

        PrefsManager.setHookStatus(hookStatus)

        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("Live updated mMaxBound=$newValue")
            }
        }

        log("Hooked TileServices (status=0b${hookStatus.toString(2).padStart(3, '0')})")
    }

    private fun extractContext(tileServices: Any) {
        val context =
            generateSequence<Class<*>>(tileServices.javaClass) { it.superclass }
                .take(20)
                .firstNotNullOfOrNull { cls ->
                    cls.accessibleFieldOrNull("mContext")?.get(tileServices) as? Context
                }

        if (context != null) {
            PrefsManager.systemUiContext = context
            return
        }

        runCatching {
            Class
                .forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        }.onFailure { log("Failed to extract SystemUI context", it) }
            .getOrNull()
            ?.let { PrefsManager.systemUiContext = it }
    }

    private fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure { log("Failed to set mMaxBound", it) }
    }

    private fun applyUserMaxBound(tileServices: Any) {
        setMaxBound(tileServices, PrefsManager.maxBound.coerceAtLeast(Prefs.maxBound.default))
    }
}
