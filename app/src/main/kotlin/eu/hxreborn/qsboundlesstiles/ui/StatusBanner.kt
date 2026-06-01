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
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens

@Composable
internal fun StatusBanner(
    state: DashboardUiState.Success?,
    modifier: Modifier = Modifier,
) {
    val active = state?.xposedActive == true
    val (iconRes, containerColor, contentColor) =
        if (active) {
            Triple(
                R.drawable.ic_check_circle_24,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            Triple(
                R.drawable.ic_warning_24,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }

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
                    text = statusTitle(active, state?.hasRoot ?: false),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = statusSubtitle(state, active),
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
    hasRoot: Boolean,
): String =
    when {
        !isActive -> stringResource(R.string.module_inactive)
        !hasRoot -> stringResource(R.string.module_no_root_title)
        else -> stringResource(R.string.module_active)
    }

@Composable
internal fun statusSubtitle(
    state: DashboardUiState.Success?,
    isActive: Boolean,
): String =
    when {
        state == null || !isActive -> {
            stringResource(R.string.module_inactive_subtitle)
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
