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

    @Volatile private var xposedRef: XposedInterface? = null

    @Volatile private var listenerRegistered = false

    var onMaxBoundChanged: ((Int) -> Unit)? = null

    fun init(xposed: XposedInterface) {
        xposedRef = xposed
        tryInitRemotePrefs()
    }

    private fun tryInitRemotePrefs(): Boolean {
        val xposed = xposedRef ?: return false
        return runCatching {
            remotePrefs = xposed.getRemotePreferences(PREFS_GROUP)
            log("tryInitRemotePrefs: remotePrefs=${remotePrefs != null}")
            if (remotePrefs != null) {
                refreshCache()
                if (!listenerRegistered) {
                    listenerRegistered = true
                    remotePrefs?.registerOnSharedPreferenceChangeListener { _, key ->
                        if (key == KEY_MAX_BOUND) refreshCache()
                    }
                }
                true
            } else {
                log("RemotePreferences unavailable")
                false
            }
        }.getOrElse {
            log("RemotePreferences init failed", it)
            false
        }
    }

    private fun refreshCache() {
        runCatching {
            val rawValue = remotePrefs?.getInt(KEY_MAX_BOUND, DEFAULT_MAX_BOUND)
            maxBoundCache = rawValue?.coerceIn(DEFAULT_MAX_BOUND, MAX_BOUND) ?: DEFAULT_MAX_BOUND
            log("refreshCache: raw=$rawValue, cached=$maxBoundCache")
            onMaxBoundChanged?.invoke(maxBoundCache)
        }.onFailure {
            log("refreshCache() failed", it)
        }
    }

    fun getMaxBound(): Int {
        // Lazy retry if initial init failed
        if (remotePrefs == null) {
            tryInitRemotePrefs()
        }
        return maxBoundCache
    }
}
