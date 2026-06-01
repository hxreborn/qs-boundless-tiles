package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import eu.hxreborn.qsboundlesstiles.log
import io.github.libxposed.api.XposedInterface

object PrefsManager {
    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    var maxBound: Int = Prefs.maxBound.default
        private set

    @Volatile
    var onMaxBoundChanged: ((Int) -> Unit)? = null

    // Strong reference prevents GC (RemotePreferences uses WeakHashMap for listeners)
    private var prefChangeListener: OnSharedPreferenceChangeListener? = null

    fun init(xposed: XposedInterface) {
        runCatching {
            val prefs = xposed.getRemotePreferences(Prefs.GROUP).also { remotePrefs = it }
            refreshCache()

            prefChangeListener =
                OnSharedPreferenceChangeListener { _, key ->
                    runCatching {
                        val oldMaxBound = maxBound
                        refreshCache()
                        if (key == Prefs.maxBound.key && maxBound != oldMaxBound) {
                            onMaxBoundChanged?.invoke(maxBound)
                        }
                    }.onFailure { log("Preference change handler failed", it) }
                }.also(prefs::registerOnSharedPreferenceChangeListener)

            log("PrefsManager initialized")
        }.onFailure { log("PrefsManager.init() failed", it) }
    }

    private fun refreshCache() {
        runCatching {
            remotePrefs?.let { prefs ->
                maxBound = Prefs.maxBound.read(prefs)
            }
        }.onFailure { log("refreshCache() failed", it) }
    }
}
