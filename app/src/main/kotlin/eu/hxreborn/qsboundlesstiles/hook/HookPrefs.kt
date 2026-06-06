package eu.hxreborn.qsboundlesstiles.hook

import android.content.SharedPreferences
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import java.util.concurrent.CopyOnWriteArrayList

// Cached snapshot of remote prefs, refreshed by the change listener registered in
// WarmTilesModule.onModuleLoaded. Hook process reads these on every intercept --
// getRemotePreferences() is a synchronous Binder IPC and must not run on a hot path.

@Volatile internal var maxBound: Int = Prefs.maxBound.default

internal val onMaxBoundChangedHandlers = CopyOnWriteArrayList<(Int) -> Unit>()

internal fun loadHookPrefs(prefs: SharedPreferences) {
    maxBound = Prefs.maxBound.read(prefs)
}
