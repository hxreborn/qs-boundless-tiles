package eu.hxreborn.qsboundlesstiles.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import eu.hxreborn.qsboundlesstiles.prefs.PrefSpec
import eu.hxreborn.qsboundlesstiles.prefs.PrefsRepository
import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo
import eu.hxreborn.qsboundlesstiles.scanner.TileScanner
import eu.hxreborn.qsboundlesstiles.util.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private data class DeviceStats(
    val hasRoot: Boolean = false,
    val activeQsCount: Int = 0,
    val tileProviders: List<TileProviderInfo> = emptyList(),
)

class DashboardViewModel(
    private val repository: PrefsRepository,
) : ViewModel() {
    private val xposedActive = MutableStateFlow(false)
    private val deviceStats = MutableStateFlow(DeviceStats())

    val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.state,
            xposedActive,
            deviceStats,
        ) { prefs, xposed, stats ->
            DashboardUiState.Success(
                prefs = prefs,
                xposedActive = xposed,
                activeQsCount = stats.activeQsCount,
                hasRoot = stats.hasRoot,
                tileProviders = stats.tileProviders,
            )
        }.stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5_000L),
            initialValue = DashboardUiState.Loading,
        )

    fun <T : Any> savePref(
        pref: PrefSpec<T>,
        value: T,
    ) {
        viewModelScope.launch(Dispatchers.IO) { repository.save(pref, value) }
    }

    fun setXposedActive(active: Boolean) {
        xposedActive.value = active
    }

    fun refreshStats(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootUtils.isRootAvailable()
            val qsCount = if (root) RootUtils.getActiveQsTileCount() else 0
            val providers = TileScanner.getThirdPartyTileProviders(context)
            deviceStats.value = DeviceStats(root, qsCount, providers)
        }
    }
}

class DashboardViewModelFactory(
    private val repository: PrefsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DashboardViewModel(repository) as T
}
