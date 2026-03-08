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
internal fun StatusBanner(
    state: DashboardUiState.Success?,
    modifier: Modifier = Modifier,
) {
    val level = statusLevel(state)
    val (iconRes, containerColor, contentColor) =
        when (level) {
            StatusLevel.SUCCESS -> {
                Triple(
                    R.drawable.ic_check_circle_24,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            StatusLevel.WARNING -> {
                Triple(
                    R.drawable.ic_info_24,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            StatusLevel.ERROR -> {
                Triple(
                    R.drawable.ic_warning_24,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

    val isActive = state?.xposedActive ?: false
    val isFullyHooked = (state?.hookStatus ?: 0) and TileServicesHook.HOOK_CONSTRUCTOR != 0

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    text = statusTitle(isActive, isFullyHooked, state?.hasRoot ?: false),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = statusSubtitle(state, isActive, isFullyHooked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
internal fun statusTitle(
    isActive: Boolean,
    isFullyHooked: Boolean,
    hasRoot: Boolean,
): String =
    when {
        !isActive -> stringResource(R.string.module_inactive)
        !isFullyHooked -> stringResource(R.string.module_partially_active)
        !hasRoot -> stringResource(R.string.module_no_root_title)
        else -> stringResource(R.string.module_active)
    }

@Composable
internal fun statusSubtitle(
    state: DashboardUiState.Success?,
    isActive: Boolean,
    isFullyHooked: Boolean,
): String =
    when {
        state == null || !isActive -> {
            stringResource(R.string.module_inactive_subtitle)
        }

        !isFullyHooked -> {
            stringResource(R.string.module_partial_subtitle)
        }

        !state.hasRoot -> {
            stringResource(R.string.module_no_root_subtitle)
        }

        state.activeQsCount > state.prefs.maxBound -> {
            stringResource(
                R.string.status_cold_start,
                state.activeQsCount - state.prefs.maxBound,
                state.activeQsCount,
            )
        }

        else -> {
            stringResource(R.string.status_all_bound, state.activeQsCount)
        }
    }
