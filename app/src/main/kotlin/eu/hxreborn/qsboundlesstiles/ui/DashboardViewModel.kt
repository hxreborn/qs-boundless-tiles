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

abstract class DashboardViewModel : ViewModel() {
    abstract val uiState: StateFlow<DashboardUiState>

    abstract fun <T : Any> savePref(
        pref: PrefSpec<T>,
        value: T,
    )

    abstract fun setXposedActive(active: Boolean)

    abstract fun setHookStatus(status: Int)

    abstract fun refreshStats(context: Context)

    abstract fun setTileEvents(raw: String)
}

private data class DeviceStats(
    val hasRoot: Boolean = false,
    val activeQsCount: Int = 0,
    val tileProviders: List<TileProviderInfo> = emptyList(),
)

class DashboardViewModelImpl(
    private val repository: PrefsRepository,
) : DashboardViewModel() {
    private val xposedActive = MutableStateFlow(false)
    private val hookStatus = MutableStateFlow(0)
    private val deviceStats = MutableStateFlow(DeviceStats())
    private val tileEvents = MutableStateFlow<List<TileEvent>>(emptyList())

    override val uiState: StateFlow<DashboardUiState> =
        combine(
            repository.state,
            xposedActive,
            hookStatus,
            deviceStats,
            tileEvents,
        ) { prefs, xposed, hook, stats, events ->
            DashboardUiState.Success(
                prefs = prefs,
                xposedActive = xposed,
                hookStatus = hook,
                activeQsCount = stats.activeQsCount,
                hasRoot = stats.hasRoot,
                tileProviders = stats.tileProviders,
                tileEvents = events,
            )
        }.stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5_000L),
            initialValue = DashboardUiState.Loading,
        )

    override fun <T : Any> savePref(
        pref: PrefSpec<T>,
        value: T,
    ) = repository.save(pref, value)

    override fun setXposedActive(active: Boolean) {
        xposedActive.value = active
    }

    override fun setHookStatus(status: Int) {
        hookStatus.value = status
    }

    override fun refreshStats(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = RootUtils.isRootAvailable()
            val qsCount = if (root) RootUtils.getActiveQsTileCount() else 0
            val providers = TileScanner.getThirdPartyTileProviders(context)
            deviceStats.value = DeviceStats(root, qsCount, providers)
        }
    }

    override fun setTileEvents(raw: String) {
        tileEvents.value =
            raw
                .lines()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split("|", limit = 5)
                    if (parts.size >= 2) {
                        TileEvent(
                            timestampMs = parts[0].toLongOrNull() ?: return@mapNotNull null,
                            type =
                                runCatching { EventType.valueOf(parts[1]) }.getOrNull()
                                    ?: return@mapNotNull null,
                            tileName = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                            durationMs = parts.getOrNull(3)?.toLongOrNull(),
                            detail = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        null
                    }
                }
    }
}

class DashboardViewModelFactory(
    private val repository: PrefsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DashboardViewModelImpl(repository) as T
}
