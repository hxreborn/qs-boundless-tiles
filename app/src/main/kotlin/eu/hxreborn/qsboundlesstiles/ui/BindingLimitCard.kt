package eu.hxreborn.qsboundlesstiles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens

@Composable
internal fun BindingLimitCard(
    state: DashboardUiState.Success,
    onValueCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = Prefs.maxBound.range!!
    val clamped =
        state.prefs.maxBound
            .coerceIn(range)
            .toFloat()
    var sliderValue by remember(clamped) {
        mutableFloatStateOf(clamped)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingLg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tile_limit_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(R.string.tile_limit_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    sliderValue.toInt().toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Tokens.SpacingSm))
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it.toInt().toFloat()
                },
                onValueChangeFinished = {
                    onValueCommit(sliderValue.toInt())
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
            )
        }
    }
}
