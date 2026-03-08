package eu.hxreborn.qsboundlesstiles.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens

internal enum class StatusLevel { SUCCESS, WARNING, ERROR }

@Composable
internal fun statusLevel(state: DashboardUiState.Success?): StatusLevel =
    when {
        state == null || !state.xposedActive -> StatusLevel.ERROR
        (state.hookStatus and TileServicesHook.HOOK_CONSTRUCTOR) == 0 -> StatusLevel.WARNING
        !state.hasRoot -> StatusLevel.WARNING
        state.activeQsCount > state.prefs.maxBound -> StatusLevel.WARNING
        else -> StatusLevel.SUCCESS
    }

@Composable
internal fun StatusBanner(state: DashboardUiState.Success?) {
    val level = statusLevel(state)
    val containerColor =
        when (level) {
            StatusLevel.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
            StatusLevel.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
            StatusLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        }
    val contentColor =
        when (level) {
            StatusLevel.SUCCESS -> MaterialTheme.colorScheme.onPrimaryContainer
            StatusLevel.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
            StatusLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        }
    val iconRes =
        when (level) {
            StatusLevel.SUCCESS -> R.drawable.ic_check_circle_24
            StatusLevel.WARNING -> R.drawable.ic_info_24
            StatusLevel.ERROR -> R.drawable.ic_warning_24
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(Tokens.SpacingLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
            Spacer(Modifier.width(Tokens.SpacingLg))
            Column {
                Text(
                    text = statusTitle(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = statusSubtitle(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
internal fun statusTitle(state: DashboardUiState.Success?): String {
    val isActive = state?.xposedActive == true
    val isFullyHooked =
        (state?.hookStatus ?: 0) and TileServicesHook.HOOK_CONSTRUCTOR != 0
    return when {
        !isActive -> stringResource(R.string.module_inactive)
        !isFullyHooked -> stringResource(R.string.module_partially_active)
        !state.hasRoot -> stringResource(R.string.module_no_root_title)
        else -> stringResource(R.string.module_active)
    }
}

@Composable
internal fun statusSubtitle(state: DashboardUiState.Success?): String {
    val isActive = state?.xposedActive == true
    val isFullyHooked =
        (state?.hookStatus ?: 0) and TileServicesHook.HOOK_CONSTRUCTOR != 0
    return when {
        state == null || !isActive -> stringResource(R.string.module_inactive_subtitle)
        !isFullyHooked -> stringResource(R.string.module_partial_subtitle)
        !state.hasRoot -> stringResource(R.string.module_no_root_subtitle)
        state.activeQsCount > state.prefs.maxBound ->
            stringResource(
                R.string.status_cold_start,
                state.activeQsCount - state.prefs.maxBound,
                state.activeQsCount,
            )
        else -> stringResource(R.string.status_all_bound, state.activeQsCount)
    }
}
