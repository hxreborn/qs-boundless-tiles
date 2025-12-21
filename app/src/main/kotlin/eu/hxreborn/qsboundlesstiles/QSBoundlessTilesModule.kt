package eu.hxreborn.qsboundlesstiles

import android.os.Handler
import android.os.Looper
import eu.hxreborn.qsboundlesstiles.hook.TileLifecycleManagerHook
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

internal lateinit var module: QSBoundlessTilesModule

class QSBoundlessTilesModule(
    base: XposedInterface,
    param: ModuleLoadedParam,
) : XposedModule(base, param) {
    init {
        module = this
        log("v${BuildConfig.VERSION_NAME} loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage) return

        log("SystemUI loaded, hooking tile services...")

        PrefsManager.init()

        var hooksSucceeded = false

        runCatching {
            TileServicesHook.hook(param.classLoader)
            log("TileServices hooked successfully")
            hooksSucceeded = true
        }.onFailure { e ->
            log("Failed to hook TileServices", e)
        }

        runCatching {
            TileLifecycleManagerHook.hook(param.classLoader)
            log("TileLifecycleManager hooked successfully")
        }.onFailure { e ->
            log("Failed to hook TileLifecycleManager", e)
        }

        if (hooksSucceeded) {
            Handler(Looper.getMainLooper()).postDelayed({
                PrefsManager.passBinder()
                log("Binder passed to app")
            }, 2000)
        }
    }

    companion object {
        private const val TAG = "QSBoundlessTiles"

        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            t?.also {
                module.log(msg, it)
                android.util.Log.e(TAG, msg, it)
            } ?: run {
                module.log(msg)
                android.util.Log.d(TAG, msg)
            }
        }
    }
}
