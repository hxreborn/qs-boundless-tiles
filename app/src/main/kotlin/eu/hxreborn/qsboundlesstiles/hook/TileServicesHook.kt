package eu.hxreborn.qsboundlesstiles.hook

import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import eu.hxreborn.qsboundlesstiles.module
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.XposedInterface.BeforeHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.BeforeInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"

object TileServicesHook {
    private var maxBoundField: Field? = null
    private var recalculateMethod: Method? = null

    @Volatile
    private var tileServicesInstance: Any? = null

    fun hook(classLoader: ClassLoader) {
        val tileServicesClass = classLoader.loadClass(TILE_SERVICES_CLASS)

        maxBoundField =
            runCatching {
                tileServicesClass.getDeclaredField("mMaxBound").apply { isAccessible = true }
            }.onFailure {
                log("mMaxBound field not found, aborting", it)
            }.getOrNull()

        if (maxBoundField == null) return

        tileServicesClass.declaredConstructors.forEach { constructor ->
            module.hook(constructor, TileServicesConstructorHooker::class.java)
        }

        // Fallback for OEM builds where recalculateBindAllowance may be renamed/inlined
        tileServicesClass.declaredMethods
            .find {
                it.name == "setMemoryPressure" &&
                    it.parameterCount == 1
            }?.let { module.hook(it, SetMemoryPressureHooker::class.java) }

        // setMemoryPressure sets mMaxBound=1 then calls recalculateBindAllowance synchronously
        // Before-hook here restores mMaxBound before binding evaluation on each tile
        tileServicesClass.declaredMethods
            .find {
                it.name == "recalculateBindAllowance" &&
                    it.parameterCount == 0
            }?.also { recalculateMethod = it.apply { isAccessible = true } }
            ?.let { module.hook(it, RecalculateBindAllowanceHooker::class.java) }

        // Trigger recalculateBindAllowance on pref change so new mMaxBound applies immediately
        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
                runCatching { recalculateMethod?.invoke(instance) }
                log("Live updated mMaxBound=$newValue")
            }
        }

        log("Hooked TileServices")
    }

    fun storeInstance(tileServices: Any) {
        tileServicesInstance = tileServices
    }

    fun setMaxBound(
        tileServices: Any,
        value: Int,
    ) {
        runCatching {
            maxBoundField?.setInt(tileServices, value)
        }.onFailure {
            log("Failed to set mMaxBound", it)
        }
    }

    fun applyUserMaxBound(tileServices: Any) {
        val newMax = maxOf(PrefsManager.DEFAULT_MAX_BOUND, PrefsManager.getMaxBound())
        setMaxBound(tileServices, newMax)
    }
}

@XposedHooker
class TileServicesConstructorHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            TileServicesHook.storeInstance(tileServices)
            TileServicesHook.applyUserMaxBound(tileServices)
            log("TileServices constructed, mMaxBound=${PrefsManager.getMaxBound()}")
        }
    }
}

@XposedHooker
class SetMemoryPressureHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            callback.thisObject?.let {
                TileServicesHook.applyUserMaxBound(it)
                log("setMemoryPressure: restored mMaxBound=${PrefsManager.getMaxBound()}")
            }
        }
    }
}

@XposedHooker
class RecalculateBindAllowanceHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @BeforeInvocation
        fun before(callback: BeforeHookCallback) {
            callback.thisObject?.let {
                TileServicesHook.applyUserMaxBound(it)
                log("recalculateBindAllowance: set mMaxBound=${PrefsManager.getMaxBound()}")
            }
        }
    }
}
