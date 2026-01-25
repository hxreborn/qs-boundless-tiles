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
        if (param.packageName != BuildConfig.SYSTEMUI_PACKAGE || !param.isFirstPackage) return

        PrefsManager.init(this)

        runCatching {
            TileServicesHook.hook(param.classLoader)
        }.onFailure { e ->
            log("Failed to hook TileServices", e)
        }
    }

    companion object {
        fun log(
            msg: String,
            t: Throwable? = null,
        ) {
            if (t != null) module.log(msg, t) else module.log(msg)
        }
    }
}
