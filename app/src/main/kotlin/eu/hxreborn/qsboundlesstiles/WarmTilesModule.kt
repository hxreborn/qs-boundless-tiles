package eu.hxreborn.qsboundlesstiles

import android.content.SharedPreferences
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.hook.loadHookPrefs
import eu.hxreborn.qsboundlesstiles.hook.onMaxBoundChangedHandlers
import eu.hxreborn.qsboundlesstiles.hook.maxBound
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

@PublishedApi
internal lateinit var module: WarmTilesModule

class WarmTilesModule : XposedModule() {
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        module = this
        log("loaded version=${BuildConfig.VERSION_NAME}")
        runCatching {
            val prefs = getRemotePreferences(Prefs.GROUP)
            loadHookPrefs(prefs)
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    runCatching {
                        val old = maxBound
                        loadHookPrefs(sp)
                        if (key == Prefs.maxBound.key && maxBound != old) {
                            onMaxBoundChangedHandlers.forEach { it(maxBound) }
                        }
                    }.onFailure { log("prefs reload failed", it) }
                }
            prefsListener = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }.onFailure { log("prefs init failed", it) }
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != BuildConfig.SYSTEMUI_PACKAGE || !param.isFirstPackage) return
        runCatching {
            TileServicesHook.hook(this, param.classLoader)
        }.onFailure { e -> log("hook failed pkg=${param.packageName}", e) }
    }
}
