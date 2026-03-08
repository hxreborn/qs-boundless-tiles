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

    fun init(xposed: XposedInterface) {
        runCatching {
            remotePrefs = xposed.getRemotePreferences(Prefs.GROUP)
            refreshCache()
            remotePrefs?.registerOnSharedPreferenceChangeListener { _, key ->
                runCatching {
                    val oldMaxBound = maxBound
                    refreshCache()
                    if (key == Prefs.maxBound.key && maxBound != oldMaxBound) {
                        onMaxBoundChanged?.invoke(maxBound)
                    }
                }.onFailure { log("Preference change handler failed", it) }
            }
            log("PrefsManager initialized")
        }.onFailure { log("PrefsManager.init() failed", it) }
    }

    fun setHookStatus(status: Int) {
        hookStatus = status
    }

    fun flushHookStatus() {
        val context = TileActivityHook.systemUiContext ?: return
        runCatching {
            context.contentResolver.call(
                HookDataProvider.CONTENT_URI,
                HookDataProvider.METHOD_WRITE_HOOK_STATUS,
                hookStatus.toString(),
                null,
            )
        }.onFailure { log("Failed to flush hook status", it) }
    }

    fun recordTileEvent(
        type: EventType,
        tileName: String?,
        durationMs: Long?,
        detail: String?,
    ) {
        val context = TileActivityHook.systemUiContext ?: return
        val entry =
            listOf(
                System.currentTimeMillis().toString(),
                type.name,
                tileName ?: "",
                durationMs?.toString() ?: "",
                detail ?: "",
            ).joinToString("|")
        runCatching {
            context.contentResolver.call(
                HookDataProvider.CONTENT_URI,
                HookDataProvider.METHOD_RECORD_TILE_EVENT,
                entry,
                null,
            )
        }.onFailure { log("Failed to record tile event", it) }
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
