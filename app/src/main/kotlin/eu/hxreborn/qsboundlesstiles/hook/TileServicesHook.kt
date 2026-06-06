package eu.hxreborn.qsboundlesstiles.hook

import android.os.Build
import eu.hxreborn.qsboundlesstiles.log
import eu.hxreborn.qsboundlesstiles.logDebug
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
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
        log("hook start api=${Build.VERSION.SDK_INT}")

        val tileServicesClass =
            classLoader.loadOrNull(TILE_SERVICES_CLASS) ?: run {
                log("class missing name=$TILE_SERVICES_CLASS")
                return
            }

        maxBoundField = tileServicesClass.accessibleFieldOrNull("mMaxBound")
        if (maxBoundField == null) {
            log("field missing name=mMaxBound")
            return
        }

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                val ts = chain.thisObject ?: return@intercept result
                tileServicesInstance = ts
                applyUserMaxBound(ts)
                log("hooked constructor maxBound=$maxBound")
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
                        logDebug { "setMemoryPressure restored maxBound=$maxBound" }
                    }
                    result
                }
            } ?: log("method missing name=setMemoryPressure")

        tileServicesClass.declaredMethods
            .find { it.name == "recalculateBindAllowance" && it.parameterCount == 0 }
            ?.also { recalculateMethod = it.apply { isAccessible = true } }
            ?.let { method ->
                module.hook(method).intercept { chain ->
                    chain.thisObject?.let { ts ->
                        applyUserMaxBound(ts)
                        logDebug { "recalculateBindAllowance set maxBound=$maxBound" }
                    }
                    chain.proceed()
                }
            } ?: log("method missing name=recalculateBindAllowance")

        onMaxBoundChangedHandlers.add { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("live updated maxBound=$newValue")
            }
        }

        log("hook done")
    }

    private fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure { log("set maxBound failed", it) }
    }

    private fun applyUserMaxBound(tileServices: Any) {
        setMaxBound(tileServices, maxBound.coerceAtLeast(Prefs.maxBound.default))
    }
}
