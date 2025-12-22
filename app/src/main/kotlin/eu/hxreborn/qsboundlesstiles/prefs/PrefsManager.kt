package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import io.github.libxposed.api.XposedInterface

object PrefsManager {
    const val PREFS_GROUP = "settings"
    const val KEY_MAX_BOUND = "max_bound"
    const val DEFAULT_MAX_BOUND = 3
    const val MAX_BOUND = 30

    private var remotePrefs: SharedPreferences? = null

    @Volatile private var maxBoundCache: Int = DEFAULT_MAX_BOUND

    fun init(xposed: XposedInterface) {
        log("PrefsManager.init() called")

        runCatching {
            remotePrefs = xposed.getRemotePreferences(PREFS_GROUP)
            refreshCache()

            // Hooks read from cache, listener keeps it in sync
            remotePrefs?.registerOnSharedPreferenceChangeListener { _, key ->
                log("PrefsManager: preference changed: $key")
                if (key == KEY_MAX_BOUND) refreshCache()
            }

            log("PrefsManager.init() done, maxBound=$maxBoundCache")
        }.onFailure {
            log("PrefsManager.init() failed to get remote preferences", it)
        }
    }

    private fun refreshCache() {
        val prefs =
            remotePrefs ?: run {
                log("refreshCache() remotePrefs is null")
                return
            }

        runCatching {
            maxBoundCache =
                prefs
                    .getInt(KEY_MAX_BOUND, DEFAULT_MAX_BOUND)
                    .coerceIn(DEFAULT_MAX_BOUND, MAX_BOUND)
            log("refreshCache() success: maxBound=$maxBoundCache")
        }.onFailure {
            log("refreshCache() failed", it)
        }
    }

    fun getMaxBound(): Int = maxBoundCache
}
