package eu.hxreborn.qsboundlesstiles.prefs

import android.content.SharedPreferences
import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesModule.Companion.log
import io.github.libxposed.api.XposedInterface

object PrefsManager {
    const val PREFS_GROUP = "settings"
    const val KEY_MAX_BOUND = "max_bound"
    const val DEFAULT_MAX_BOUND = 3
    const val MAX_BOUND = 30

    @Volatile private var remotePrefs: SharedPreferences? = null

    @Volatile private var maxBoundCache: Int = DEFAULT_MAX_BOUND

    fun init(xposed: XposedInterface) {
        runCatching {
            remotePrefs = xposed.getRemotePreferences(PREFS_GROUP)
            refreshCache()

            remotePrefs?.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_MAX_BOUND) refreshCache()
            }
        }.onFailure {
            log("PrefsManager.init() failed", it)
        }
    }

    private fun refreshCache() {
        runCatching {
            maxBoundCache =
                remotePrefs
                    ?.getInt(KEY_MAX_BOUND, DEFAULT_MAX_BOUND)
                    ?.coerceIn(DEFAULT_MAX_BOUND, MAX_BOUND)
                    ?: DEFAULT_MAX_BOUND
        }.onFailure {
            log("refreshCache() failed", it)
        }
    }

    fun getMaxBound(): Int = maxBoundCache
}
