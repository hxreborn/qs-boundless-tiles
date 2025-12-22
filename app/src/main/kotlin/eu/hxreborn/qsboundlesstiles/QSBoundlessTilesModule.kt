package eu.hxreborn.qsboundlesstiles

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
        if (!param.isFirstPackage || param.packageName != SYSTEMUI_PACKAGE || hooked) return
        hooked = true

        log("SystemUI loaded, hooking tile services...")

        PrefsManager.init(this)

        runCatching {
            TileServicesHook.hook(param.classLoader)
            log("TileServices hooked successfully, maxBound=${PrefsManager.getMaxBound()}")
        }.onFailure { e ->
            log("Failed to hook TileServices", e)
        }
    }

    companion object {
        private const val TAG = "QSBoundlessTiles"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"
        private var hooked = false

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
