package eu.hxreborn.qsboundlesstiles.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class EventStats(
    val warmCount: Int,
    val coldCount: Int,
    val tapCount: Int,
    val avgColdMs: Long?,
    val serviceDeaths: Int,
)

internal data class BadgeStyle(
    val iconRes: Int,
    val backgroundColor: Color,
    val tintColor: Color,
)

@Stable
internal class TileActivityState {
    var expanded by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var selectedTiles by mutableStateOf(emptySet<String>())
    var selectedTypes by mutableStateOf(emptySet<EventType>())
    var expandedTimestamp by mutableStateOf<Long?>(null)
}

@Composable
internal fun rememberTileActivityState() = remember { TileActivityState() }

@Composable
internal fun TileActivityCard(
    events: List<TileEvent>,
    activityState: TileActivityState,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    val uniqueTiles =
        remember(events) {
            events.mapNotNull { it.tileName }.distinct().sorted()
        }

    val stats =
        remember(events) {
            if (events.isEmpty()) {
                null
            } else {
                val warmCount = events.count { it.type == EventType.WARM }
                val coldStarts = events.filter { it.type == EventType.COLD_START }
                val coldCount = coldStarts.size
                val tapCount = warmCount + coldCount
                val avgColdMs =
                    if (coldStarts.isNotEmpty()) {
                        coldStarts.mapNotNull { it.durationMs }.average().toLong()
                    } else {
                        null
                    }
                val serviceDeaths = events.count { it.type == EventType.SERVICE_DIED }
                EventStats(warmCount, coldCount, tapCount, avgColdMs, serviceDeaths)
            }
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingLg)) {
            val chevronRotation by animateFloatAsState(
                if (activityState.expanded) 180f else 0f,
                label = "chevron",
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { activityState.expanded = !activityState.expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.tile_activity_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (events.isNotEmpty()) {
                    Text(
                        stringResource(R.string.clear_log),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { showClearDialog = true },
                    )
                    Spacer(Modifier.width(Tokens.SpacingSm))
                }
                Icon(
                    painterResource(R.drawable.ic_expand_more_24),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = activityState.expanded) {
                Column {
                    if (events.isNotEmpty()) {
                        Spacer(Modifier.height(Tokens.SpacingSm))
                        TextField(
                            value = activityState.searchQuery,
                            onValueChange = { activityState.searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(stringResource(R.string.search_tiles))
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_search_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            trailingIcon = {
                                if (activityState.searchQuery.isNotEmpty()) {
                                    Icon(
                                        painterResource(R.drawable.ic_close_24),
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(18.dp)
                                                .clickable { activityState.searchQuery = "" },
                                    )
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(28.dp),
                            colors =
                                TextFieldDefaults.colors(
                                    unfocusedContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedContainerColor =
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                ),
                        )

                        Spacer(Modifier.height(Tokens.SpacingSm))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
                        ) {
                            EventType.entries.forEach { type ->
                                EventTypeChip(
                                    type = type,
                                    selected = type in activityState.selectedTypes,
                                    onToggle = {
                                        activityState.selectedTypes =
                                            activityState.selectedTypes.toggle(type)
                                    },
                                )
                            }
                        }

                        if (uniqueTiles.isNotEmpty()) {
                            Spacer(Modifier.height(Tokens.SpacingSm))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
                            ) {
                                uniqueTiles.forEach { tile ->
                                    val selected = tile in activityState.selectedTiles
                                    Surface(
                                        onClick = {
                                            activityState.selectedTiles =
                                                activityState.selectedTiles.toggle(tile)
                                        },
                                        shape = Tokens.ChipShape,
                                        color =
                                            if (selected) {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            } else {
                                                Color.Transparent
                                            },
                                        border =
                                            if (selected) {
                                                null
                                            } else {
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            },
                                    ) {
                                        Text(
                                            tile,
                                            modifier =
                                                Modifier.padding(
                                                    horizontal = 8.dp,
                                                    vertical = 4.dp,
                                                ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    Color.Unspecified
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        stats?.let { s ->
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color =
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
                            ) {
                                if (s.tapCount > 0) {
                                    StatChip(
                                        iconRes = R.drawable.ic_check_circle_24,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        text = stringResource(R.string.stat_warm, s.warmCount),
                                    )
                                }
                                if (s.coldCount > 0) {
                                    val avgText = s.avgColdMs?.let { " (${it}ms)" } ?: ""
                                    StatChip(
                                        iconRes = R.drawable.ic_warning_24,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        text =
                                            stringResource(R.string.stat_cold, s.coldCount) +
                                                avgText,
                                    )
                                }
                                if (s.serviceDeaths > 0) {
                                    StatChip(
                                        iconRes = R.drawable.ic_error_24,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        text = stringResource(R.string.stat_died, s.serviceDeaths),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_log_confirm_title)) },
            text = { Text(stringResource(R.string.clear_log_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClear()
                }) {
                    Text(stringResource(R.string.clear_log))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun TileEventRow(
    event: TileEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    val time =
        remember(event.timestampMs) {
            timeFormat.format(Date(event.timestampMs))
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Tokens.ScreenHorizontalPadding),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                time,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Tokens.SpacingSm))
            EventBadge(event.type)
            Spacer(Modifier.width(Tokens.SpacingSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.tileName ?: stringResource(R.string.event_system),
                    style = MaterialTheme.typography.bodyMedium,
                )
                event.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            event.durationMs?.let { ms ->
                Spacer(Modifier.width(Tokens.SpacingSm))
                DurationBadge(ms)
            }
        }

        AnimatedVisibility(visible = expanded) {
            val style = eventBadgeStyle(event.type)
            val fullTimestamp =
                remember(event.timestampMs) {
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.US,
                    ).format(Date(event.timestampMs))
                }
            val relativeTime =
                remember(event.timestampMs) {
                    formatRelativeTime(event.timestampMs)
                }
            val durationLabel =
                when {
                    event.durationMs == null -> {
                        null
                    }

                    event.type == EventType.COLD_START -> {
                        stringResource(R.string.cold_start_latency, event.durationMs)
                    }

                    else -> {
                        stringResource(R.string.latency, event.durationMs)
                    }
                }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Tokens.SpacingSm)
                        .drawBehind {
                            drawRoundRect(
                                color = style.tintColor,
                                topLeft = Offset.Zero,
                                size = Size(3.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(1.5.dp.toPx()),
                            )
                        }.padding(start = Tokens.SpacingMd),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    event.type.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.tintColor,
                )
                Text(
                    relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    fullTimestamp,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                durationLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun EventBadge(type: EventType) {
    val style = eventBadgeStyle(type)
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .background(style.backgroundColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(style.iconRes),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = style.tintColor,
        )
    }
}

@Composable
private fun EventTypeChip(
    type: EventType,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val style = eventBadgeStyle(type)
    val textColor = if (selected) style.tintColor else Color.Unspecified
    Surface(
        onClick = onToggle,
        shape = Tokens.ChipShape,
        color = if (selected) style.backgroundColor else Color.Transparent,
        border =
            if (selected) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(style.iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = style.tintColor,
            )
            Text(
                type.name.replace('_', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
            )
        }
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
internal fun eventBadgeStyle(type: EventType): BadgeStyle =
    when (type) {
        EventType.WARM -> {
            BadgeStyle(
                R.drawable.ic_check_circle_24,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        EventType.COLD_START -> {
            BadgeStyle(
                R.drawable.ic_warning_24,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        EventType.MEM_PRESSURE -> {
            BadgeStyle(
                R.drawable.ic_block_24,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        EventType.LIMIT_SET -> {
            BadgeStyle(
                R.drawable.ic_info_24,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EventType.SERVICE_DIED -> {
            BadgeStyle(
                R.drawable.ic_error_24,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }

@Composable
private fun DurationBadge(ms: Long) {
    Box(
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    Tokens.ChipShape,
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            "${ms}ms",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
internal fun StatChip(
    iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    text: String,
) {
    Surface(
        shape = Tokens.ChipShape,
        color = containerColor,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = Tokens.SpacingSm,
                    vertical = 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor,
            )
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

internal fun formatRelativeTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
