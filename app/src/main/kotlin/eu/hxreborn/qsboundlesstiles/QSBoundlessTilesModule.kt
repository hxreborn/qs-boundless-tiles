package eu.hxreborn.qsboundlesstiles

import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.prefs.PrefsManager
import eu.hxreborn.qsboundlesstiles.util.Logger
import eu.hxreborn.qsboundlesstiles.util.log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class QSBoundlessTilesModule(
    base: XposedInterface,
    param: ModuleLoadedParam,
) : XposedModule(base, param) {
    init {
        Logger.init(this)
        log("v${BuildConfig.VERSION_NAME} loaded")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != BuildConfig.SYSTEMUI_PACKAGE || !param.isFirstPackage) return

        PrefsManager.init(this)

        runCatching {
            TileServicesHook.hook(this, param.classLoader)
        }.onFailure { e ->
            log("Failed to hook TileServices", e)
        }
    }
}
