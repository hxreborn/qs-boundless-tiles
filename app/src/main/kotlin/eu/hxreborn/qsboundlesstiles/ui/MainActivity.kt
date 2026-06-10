package eu.hxreborn.qsboundlesstiles.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.WarmTilesApp
import eu.hxreborn.qsboundlesstiles.prefs.PrefSpec
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.prefs.PrefsRepository
import eu.hxreborn.qsboundlesstiles.ui.theme.QsTheme
import eu.hxreborn.qsboundlesstiles.util.RootUtils
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MainActivity :
    ComponentActivity(),
    XposedServiceHelper.OnServiceListener {
    private var remotePrefs: SharedPreferences? = null
    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            PrefsRepository(getSharedPreferences(Prefs.GROUP, MODE_PRIVATE)) { remotePrefs },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            QsTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val onSavePref =
                    remember(viewModel) {
                        { pref: PrefSpec<*>, value: Any ->
                            @Suppress("UNCHECKED_CAST")
                            viewModel.savePref(pref as PrefSpec<Any>, value)
                        }
                    }
                val onRestartSystemUi = remember { { performRestart() } }
                DashboardScreen(
                    uiState = uiState,
                    onSavePref = onSavePref,
                    onRestartSystemUi = onRestartSystemUi,
                )
            }
        }

        WarmTilesApp.addServiceListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        WarmTilesApp.removeServiceListener(this)
    }

    override fun onResume() {
        super.onResume()
        syncPrefsToRemote()
        viewModel.refreshStats(this)
    }

    override fun onServiceBind(service: XposedService) {
        remotePrefs = service.getRemotePreferences(Prefs.GROUP)
        viewModel.setXposedActive(true)
        syncPrefsToRemote()
    }

    override fun onServiceDied(service: XposedService) {
        remotePrefs = null
        viewModel.setXposedActive(false)
    }

    private fun syncPrefsToRemote() {
        val state = viewModel.uiState.value as? DashboardUiState.Success ?: return
        val remote = remotePrefs ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            remote.edit(commit = true) {
                Prefs.maxBound.write(this, state.prefs.maxBound)
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
            if (success && !awaitSystemUiRebind()) {
                Toast
                    .makeText(
                        this@MainActivity,
                        R.string.systemui_still_restarting,
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    private suspend fun awaitSystemUiRebind(timeout: Duration = 5.seconds): Boolean =
        withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine { cont ->
                val listener =
                    object : XposedServiceHelper.OnServiceListener {
                        override fun onServiceBind(service: XposedService) {
                            WarmTilesApp.removeServiceListener(this)
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onServiceDied(service: XposedService) = Unit
                    }
                WarmTilesApp.addServiceListener(listener)
                cont.invokeOnCancellation { WarmTilesApp.removeServiceListener(listener) }
            }
        } ?: false
}
