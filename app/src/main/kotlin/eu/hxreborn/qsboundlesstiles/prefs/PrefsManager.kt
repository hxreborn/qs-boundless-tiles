package eu.hxreborn.qsboundlesstiles.prefs

import eu.hxreborn.qsboundlesstiles.hook.TileActivityHook
import eu.hxreborn.qsboundlesstiles.provider.HookDataProvider
import eu.hxreborn.qsboundlesstiles.ui.EventType
import eu.hxreborn.qsboundlesstiles.util.log
import io.github.libxposed.api.XposedInterface

object PrefsManager {
    @Volatile
    private var remotePrefs: android.content.SharedPreferences? = null

    @Volatile
    var maxBound: Int = Prefs.maxBound.default
        private set

    @Volatile
    var debugLogs: Boolean = Prefs.debugLogs.default
        private set

    @Volatile
    var hookStatus: Int = 0
        private set

    @Volatile
    var onMaxBoundChanged: ((Int) -> Unit)? = null

    // Strong reference prevents GC (RemotePreferences uses WeakHashMap for listeners)
    private var prefChangeListener:
        android.content.SharedPreferences.OnSharedPreferenceChangeListener? =
        null

    fun init(xposed: XposedInterface) {
        runCatching {
            remotePrefs = xposed.getRemotePreferences(Prefs.GROUP)
            refreshCache()
            val listener =
                android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    runCatching {
                        val oldMaxBound = maxBound
                        refreshCache()
                        if (key == Prefs.maxBound.key && maxBound != oldMaxBound) {
                            onMaxBoundChanged?.invoke(maxBound)
                        }
                    }.onFailure { log("Preference change handler failed", it) }
                }
            prefChangeListener = listener
            remotePrefs?.registerOnSharedPreferenceChangeListener(listener)
            log("PrefsManager initialized")
        }.onFailure { log("PrefsManager.init() failed", it) }
    }

    fun setHookStatus(status: Int) {
        hookStatus = status
    }

    fun flushHookStatus() {
        callProvider(HookDataProvider.METHOD_WRITE_HOOK_STATUS, hookStatus.toString())
    }

    fun recordTileEvent(
        type: EventType,
        tileName: String?,
        durationMs: Long?,
        detail: String?,
    ) {
        val entry =
            listOf(
                System.currentTimeMillis().toString(),
                type.name,
                tileName ?: "",
                durationMs?.toString() ?: "",
                detail ?: "",
            ).joinToString("|")
        callProvider(HookDataProvider.METHOD_RECORD_TILE_EVENT, entry)
    }

    private fun callProvider(
        method: String,
        arg: String?,
    ) {
        val context = TileActivityHook.systemUiContext ?: return
        runCatching {
            context.contentResolver.call(HookDataProvider.CONTENT_URI, method, arg, null)
        }.onFailure { log("Provider call '$method' failed", it) }
    }

    private fun refreshCache() {
        runCatching {
            remotePrefs?.let { prefs ->
                maxBound = Prefs.maxBound.read(prefs)
                debugLogs = Prefs.debugLogs.read(prefs)
            }
        }.onFailure { log("refreshCache() failed", it) }
    }
}
