package eu.hxreborn.qsboundlesstiles.ui

import androidx.compose.runtime.Immutable
import eu.hxreborn.qsboundlesstiles.prefs.AppPrefs
import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    @Immutable
    data class Success(
        val prefs: AppPrefs,
        val xposedActive: Boolean,
        val activeQsCount: Int,
        val hasRoot: Boolean,
        val tileProviders: List<TileProviderInfo> = emptyList(),
    ) : DashboardUiState
}
