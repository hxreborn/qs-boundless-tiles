package eu.hxreborn.qsboundlesstiles.ui

import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Success(
        val prefs: PrefsState,
        val xposedActive: Boolean,
        val hookStatus: Int,
        val activeQsCount: Int,
        val hasRoot: Boolean,
        val tileProviders: List<TileProviderInfo> = emptyList(),
        val tileEvents: List<TileEvent> = emptyList(),
    ) : DashboardUiState
}

data class PrefsState(
    val maxBound: Int,
    val debugLogs: Boolean,
)

enum class EventType {
    WARM,
    COLD_START,
    MEM_PRESSURE,
    LIMIT_SET,
    SERVICE_DIED,
}

data class TileEvent(
    val timestampMs: Long,
    val type: EventType,
    val tileName: String?,
    val durationMs: Long?,
    val detail: String?,
)
