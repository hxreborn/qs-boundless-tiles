package eu.hxreborn.qsboundlesstiles.ui

import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val prefs: PrefsState,
        val xposedActive: Boolean,
        val activeQsCount: Int,
        val hasRoot: Boolean,
        val tileProviders: List<TileProviderInfo> = emptyList(),
    ) : DashboardUiState
}

data class PrefsState(
    val maxBound: Int,
)
