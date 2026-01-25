package eu.hxreborn.qsboundlesstiles.hook

import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import eu.hxreborn.qsboundlesstiles.module
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedInterface.AfterHookCallback
import io.github.libxposed.api.annotations.AfterInvocation
import io.github.libxposed.api.annotations.XposedHooker
import java.lang.reflect.Field

private const val TILE_SERVICES_CLASS = "com.android.systemui.qs.external.TileServices"

object TileServicesHook {
    private var maxBoundField: Field? = null
    @Volatile private var tileServicesInstance: Any? = null

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

        tileServicesClass.declaredMethods
            .find { it.name == "setMemoryPressure" && it.parameterCount == 1 }
            ?.let { module.hook(it, SetMemoryPressureHooker::class.java) }

        PrefsManager.onMaxBoundChanged = { newValue ->
            tileServicesInstance?.let { instance ->
                setMaxBound(instance, newValue)
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
}

@XposedHooker
class TileServicesConstructorHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            TileServicesHook.storeInstance(tileServices)
            val newMax = maxOf(PrefsManager.DEFAULT_MAX_BOUND, PrefsManager.getMaxBound())
            TileServicesHook.setMaxBound(tileServices, newMax)
            log("TileServices constructed, mMaxBound=$newMax")
        }
    }
}

@XposedHooker
class SetMemoryPressureHooker : XposedInterface.Hooker {
    companion object {
        @JvmStatic
        @AfterInvocation
        fun after(callback: AfterHookCallback) {
            val tileServices = callback.thisObject ?: return
            val newMax = maxOf(PrefsManager.DEFAULT_MAX_BOUND, PrefsManager.getMaxBound())
            TileServicesHook.setMaxBound(tileServices, newMax)
        }
    }
}
