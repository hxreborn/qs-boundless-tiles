package eu.hxreborn.qsboundlesstiles.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens

@Composable
internal fun CollapsibleCardHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        label = "chevron",
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(R.drawable.ic_expand_more_24),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TileProvidersCard(
    providers: List<TileProviderInfo>,
    initialExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingLg)) {
            CollapsibleCardHeader(
                title =
                    stringResource(
                        R.string.tile_providers_title_count,
                        providers.size,
                    ),
                expanded = expanded,
                onClick = { expanded = !expanded },
            )
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = Tokens.SpacingSm),
                ) {
                    providers.forEach { provider ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                provider.appName,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.tile_provider_count,
                                    provider.tileCount,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
