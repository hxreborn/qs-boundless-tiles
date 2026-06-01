package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.log
import eu.hxreborn.qsboundlesstiles.logDebug
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"

object TileServicesHook {
    @Volatile private var maxBoundField: Field? = null

    @Volatile private var recalculateMethod: Method? = null

    @Volatile var tileServicesInstance: Any? = null
        private set

    fun hook(
        module: XposedInterface,
        classLoader: ClassLoader,
    ) {
        log("Hooking TileServices on API ${Build.VERSION.SDK_INT}")

        val tileServicesClass =
            classLoader.loadOrNull(TILE_SERVICES_CLASS) ?: run {
                log("TileServices class not found, aborting")
                return
            }

        maxBoundField = tileServicesClass.accessibleFieldOrNull("mMaxBound")
        if (maxBoundField == null) {
            log("mMaxBound field not found, aborting")
            return
        }

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val ts = chain.thisObject ?: return@intercept result
                tileServicesInstance = ts
                applyUserMaxBound(ts)
                log("TileServices constructed, mMaxBound=${PrefsManager.maxBound}")
                result
            }
        }

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
            } ?: log("recalculateBindAllowance not found -- live binding updates unavailable")

        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("Live updated mMaxBound=$newValue")
            }
        }

        log("Hooked TileServices")
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
