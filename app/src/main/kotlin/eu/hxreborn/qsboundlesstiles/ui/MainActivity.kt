package eu.hxreborn.qsboundlesstiles.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import eu.hxreborn.qsboundlesstiles.QSBoundlessTilesApp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.prefs.PrefSpec
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsRepositoryImpl
import eu.hxreborn.qsboundlesstiles.provider.HookDataProvider
import eu.hxreborn.qsboundlesstiles.ui.theme.QsTheme
import eu.hxreborn.qsboundlesstiles.util.RootUtils
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainActivity :
    ComponentActivity(),
    XposedServiceHelper.OnServiceListener {
    private lateinit var viewModel: DashboardViewModel
    private var remotePrefs: SharedPreferences? = null
    private val hookDataPrefs: SharedPreferences by lazy {
        getSharedPreferences(HookDataProvider.PREFS_NAME, MODE_PRIVATE)
    }
    private var hookDataListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val localPrefs = getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)
        val repository = PrefsRepositoryImpl(localPrefs) { remotePrefs }
        viewModel =
            ViewModelProvider(
                this,
                DashboardViewModelFactory(repository),
            )[DashboardViewModelImpl::class.java]

        setContent {
            QsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                    uiState = uiState,
                    onSavePref = { pref, value ->
                        @Suppress("UNCHECKED_CAST")
                        viewModel.savePref(pref as PrefSpec<Any>, value)
                    },
                    onRestartSystemUi = { performRestart() },
                    onClearEvents = { clearTileEvents() },
                )
            }
        }

        QSBoundlessTilesApp.addServiceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        QSBoundlessTilesApp.removeServiceListener(this)
        hookDataListener?.let { hookDataPrefs.unregisterOnSharedPreferenceChangeListener(it) }
        hookDataListener = null
    }

    override fun onResume() {
        super.onResume()
        syncPrefsToRemote()
        refreshHookData()
        viewModel.refreshStats(this)
    }

    override fun onServiceBind(service: XposedService) {
        remotePrefs = service.getRemotePreferences(Prefs.GROUP)
        viewModel.setXposedActive(true)
        refreshHookData()
        runOnUiThread { syncPrefsToRemote() }

        if (hookDataListener == null) {
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == HookDataProvider.KEY_TILE_EVENTS) refreshTileEvents()
                    if (key == HookDataProvider.KEY_HOOK_STATUS) refreshHookStatus()
                }
            hookDataListener = listener
            hookDataPrefs.registerOnSharedPreferenceChangeListener(listener)
        }
    }

    override fun onServiceDied(service: XposedService) {
        remotePrefs = null
        viewModel.setXposedActive(false)
    }

    private fun refreshHookData() {
        refreshHookStatus()
        refreshTileEvents()
    }

    private fun refreshHookStatus() {
        val status = hookDataPrefs.getInt(HookDataProvider.KEY_HOOK_STATUS, 0)
        viewModel.setHookStatus(status)
    }

    private fun refreshTileEvents() {
        val raw = hookDataPrefs.getString(HookDataProvider.KEY_TILE_EVENTS, "") ?: ""
        viewModel.setTileEvents(raw)
    }

    private fun clearTileEvents() {
        contentResolver.call(
            HookDataProvider.CONTENT_URI,
            HookDataProvider.METHOD_CLEAR_EVENTS,
            null,
            null,
        )
        viewModel.setTileEvents("")
    }

    private fun syncPrefsToRemote() {
        val state = viewModel.uiState.value
        if (state is DashboardUiState.Success) {
            remotePrefs?.edit(commit = true) {
                Prefs.maxBound.write(this, state.prefs.maxBound)
                Prefs.debugLogs.write(this, state.prefs.debugLogs)
            }
        }
    }

    private fun performRestart() {
        lifecycleScope.launch {
            if (!RootUtils.isRootAvailable()) {
                Toast
                    .makeText(
                        this@MainActivity,
                        R.string.restart_systemui_no_root,
                        Toast.LENGTH_LONG,
                    ).show()
                return@launch
            }
            val success = RootUtils.restartSystemUI()
            val msg =
                if (success) R.string.restart_systemui_success else R.string.restart_systemui_failed
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            if (success) {
                val rebound = awaitSystemUiRebind()
                if (!rebound) {
                    Toast
                        .makeText(
                            this@MainActivity,
                            R.string.systemui_still_restarting,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }

    private suspend fun awaitSystemUiRebind(timeout: Duration = 5.seconds): Boolean =
        withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val listener =
                    object : XposedServiceHelper.OnServiceListener {
                        override fun onServiceBind(service: XposedService) {
                            QSBoundlessTilesApp.removeServiceListener(this)
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onServiceDied(service: XposedService) = Unit
                    }
                QSBoundlessTilesApp.addServiceListener(listener)
                cont.invokeOnCancellation { QSBoundlessTilesApp.removeServiceListener(listener) }
            }
        } ?: false
}
