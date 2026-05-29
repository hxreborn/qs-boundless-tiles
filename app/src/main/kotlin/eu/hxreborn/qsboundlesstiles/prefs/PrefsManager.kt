package eu.hxreborn.qsboundlesstiles.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import eu.hxreborn.qsboundlesstiles.provider.HookDataProvider
import eu.hxreborn.qsboundlesstiles.util.log
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.Executors

object PrefsManager {
    // Writing hook status is cross-process Binder IPC plus a synchronous disk commit, and the
    // constructor hook fires it during SystemUI startup when the app process is usually dead.
    // Offload to one daemon thread so SystemUI's startup thread never blocks on it.
    private val ioExecutor =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "qsbt-prefs-io").apply { isDaemon = true }
        }

    // SystemUI Context, captured from the hooked TileServices; used to reach the app's provider.
    @Volatile
    var systemUiContext: Context? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

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

    @Volatile
    private var hookStatusFlushed = false

    // Strong references prevent GC (RemotePreferences uses WeakHashMap for listeners)
    private var prefChangeListener: OnSharedPreferenceChangeListener? = null
    private var hookStatusRetryListener: OnSharedPreferenceChangeListener? = null

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

            // syncPrefsToRemote() in the app writes remotePrefs on every onResume, firing this
            // listener — the signal that the app process is alive and the provider is reachable.
            hookStatusRetryListener =
                OnSharedPreferenceChangeListener { _, _ ->
                    if (!hookStatusFlushed && hookStatus != 0) flushHookStatus()
                }.also(prefs::registerOnSharedPreferenceChangeListener)

            log("PrefsManager initialized")
        }.onFailure { log("PrefsManager.init() failed", it) }
    }

    fun setHookStatus(status: Int) {
        hookStatus = status
    }

    fun flushHookStatus() {
        val status = hookStatus.toString()
        ioExecutor.execute {
            val context = systemUiContext ?: return@execute
            val ok =
                runCatching {
                    context.contentResolver.call(
                        HookDataProvider.CONTENT_URI,
                        HookDataProvider.METHOD_WRITE_HOOK_STATUS,
                        status,
                        null,
                    )
                }.onFailure { e ->
                    if (e !is IllegalArgumentException) log("Hook status flush failed", e)
                }.isSuccess
            if (ok) hookStatusFlushed = true
        }
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
