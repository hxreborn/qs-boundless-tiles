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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.prefs.PrefSpec
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo
import eu.hxreborn.qsboundlesstiles.ui.theme.QsTheme
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onSavePref: (pref: PrefSpec<*>, value: Any) -> Unit,
    onRestartSystemUi: () -> Unit,
    onClearEvents: () -> Unit = {},
) {
    var showRestartDialog by remember { mutableStateOf(false) }
    val state = uiState as? DashboardUiState.Success

    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier.nestedScroll(
                scrollBehavior.nestedScrollConnection,
            ),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(stringResource(R.string.app_name))
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom =
                        innerPadding.calculateBottomPadding() + 24.dp,
                    start = Tokens.ScreenHorizontalPadding,
                    end = Tokens.ScreenHorizontalPadding,
                ),
            verticalArrangement =
                Arrangement.spacedBy(Tokens.SpacingMd),
        ) {
            item(key = "status") {
                StatusBanner(state = state)
            }

            if (state != null) {
                item(key = "binding_limit") {
                    BindingLimitCard(
                        state = state,
                        onValueCommit = { value ->
                            onSavePref(
                                Prefs.maxBound,
                                value.coerceIn(Prefs.maxBound.range!!),
                            )
                        },
                    )
                }

                if (state.tileProviders.isNotEmpty()) {
                    item(key = "providers") {
                        TileProvidersCard(
                            providers = state.tileProviders,
                        )
                    }
                }

                item(key = "activity") {
                    TileActivityCard(
                        events = state.tileEvents,
                        onClear = onClearEvents,
                    )
                }

                item(key = "actions") {
                    RestartAction(
                        hasRoot = state.hasRoot,
                        onClick = { showRestartDialog = true },
                    )
                }
            }

            item(key = "footer_spacer") {
                Spacer(
                    Modifier.windowInsetsBottomHeight(
                        WindowInsets.safeDrawing,
                    ),
                )
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(stringResource(R.string.restart_systemui))
            },
            text = {
                Text(stringResource(R.string.restart_warning))
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    onRestartSystemUi()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}


private enum class StatusLevel { SUCCESS, WARNING, ERROR }

@Composable
private fun statusLevel(state: DashboardUiState.Success?): StatusLevel =
    when {
        state == null || !state.xposedActive -> {
            StatusLevel.ERROR
        }

        (
            state.hookStatus and
                TileServicesHook.HOOK_CONSTRUCTOR
        ) == 0 -> {
            StatusLevel.WARNING
        }

        !state.hasRoot -> {
            StatusLevel.WARNING
        }

        state.activeQsCount > state.prefs.maxBound -> {
            StatusLevel.WARNING
        }

        else -> {
            StatusLevel.SUCCESS
        }
    }

@Composable
private fun StatusBanner(state: DashboardUiState.Success?) {
    val level = statusLevel(state)
    val containerColor =
        when (level) {
            StatusLevel.SUCCESS -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            StatusLevel.WARNING -> {
                MaterialTheme.colorScheme.tertiaryContainer
            }

            StatusLevel.ERROR -> {
                MaterialTheme.colorScheme.errorContainer
            }
        }
    val contentColor =
        when (level) {
            StatusLevel.SUCCESS -> {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            StatusLevel.WARNING -> {
                MaterialTheme.colorScheme.onTertiaryContainer
            }

            StatusLevel.ERROR -> {
                MaterialTheme.colorScheme.onErrorContainer
            }
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
                    style =
                        MaterialTheme.typography.titleMedium,
                    color = contentColor,
                )
                Text(
                    text = statusSubtitle(state),
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun statusTitle(state: DashboardUiState.Success?): String {
    val isActive = state?.xposedActive == true
    val isFullyHooked =
        (state?.hookStatus ?: 0) and
            TileServicesHook.HOOK_CONSTRUCTOR != 0
    return when {
        !isActive -> stringResource(R.string.module_inactive)
        !isFullyHooked -> stringResource(R.string.module_partially_active)
        !state.hasRoot -> stringResource(R.string.module_no_root_title)
        else -> stringResource(R.string.module_active)
    }
}

@Composable
private fun statusSubtitle(state: DashboardUiState.Success?): String {
    val isActive = state?.xposedActive == true
    val isFullyHooked =
        (state?.hookStatus ?: 0) and
            TileServicesHook.HOOK_CONSTRUCTOR != 0
    return when {
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
            stringResource(
                R.string.status_all_bound,
                state.activeQsCount,
            )
        }
    }
}



@Composable
private fun BindingLimitCard(
    state: DashboardUiState.Success,
    onValueCommit: (Int) -> Unit,
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
        modifier = Modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingLg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tile_limit_title),
                        style =
                            MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            R.string.tile_limit_subtitle,
                        ),
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
                Text(
                    sliderValue.toInt().toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
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
                valueRange =
                    range.first.toFloat()..range.last.toFloat(),
            )
        }
    }
}



@Composable
private fun CollapsibleCardHeader(
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
private fun TileProvidersCard(
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
                    modifier =
                        Modifier.padding(
                            top = Tokens.SpacingSm,
                        ),
                ) {
                    providers.forEach { provider ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            horizontalArrangement =
                                Arrangement.SpaceBetween,
                        ) {
                            Text(
                                provider.appName,
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                            )
                            Text(
                                stringResource(
                                    R.string.tile_provider_count,
                                    provider.tileCount,
                                ),
                                style =
                                    MaterialTheme.typography
                                        .bodySmall,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}



private data class EventStats(
    val warmCount: Int,
    val coldCount: Int,
    val tapCount: Int,
    val avgColdMs: Long?,
    val serviceDeaths: Int,
)

@Composable
private fun TileActivityCard(
    events: List<TileEvent>,
    onClear: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTiles by remember {
        mutableStateOf(emptySet<String>())
    }
    var selectedTypes by remember {
        mutableStateOf(emptySet<EventType>())
    }
    var expandedTimestamp by remember {
        mutableStateOf<Long?>(null)
    }

    val filtered =
        remember(events, searchQuery, selectedTiles, selectedTypes) {
            events.filter { e ->
                val name = e.tileName ?: "System"
                (
                    searchQuery.isBlank() ||
                        name.contains(searchQuery, true)
                ) &&
                    (
                        selectedTiles.isEmpty() ||
                            name in selectedTiles
                    ) &&
                    (
                        selectedTypes.isEmpty() ||
                            e.type in selectedTypes
                    )
            }
        }

    val uniqueTiles =
        remember(events) {
            events
                .mapNotNull { it.tileName }
                .distinct()
                .sorted()
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
        modifier = Modifier.fillMaxWidth(),
        shape = Tokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(Tokens.SpacingLg)) {
            val chevronRotation by animateFloatAsState(
                if (expanded) 180f else 0f,
                label = "chevron",
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.tile_activity_title,
                    ),
                    style =
                        MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (events.isNotEmpty()) {
                    Text(
                        stringResource(R.string.clear_log),
                        style =
                            MaterialTheme.typography
                                .labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                        modifier =
                            Modifier.clickable {
                                showClearDialog = true
                            },
                    )
                    Spacer(Modifier.width(Tokens.SpacingSm))
                }
                Icon(
                    painterResource(
                        R.drawable.ic_expand_more_24,
                    ),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                    tint =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (events.isNotEmpty()) {
                        Spacer(Modifier.height(Tokens.SpacingSm))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                                if (searchQuery.isNotEmpty()) {
                                    Icon(
                                        painterResource(R.drawable.ic_close_24),
                                        contentDescription = null,
                                        modifier =
                                            Modifier
                                                .size(18.dp)
                                                .clickable {
                                                    searchQuery = ""
                                                },
                                    )
                                }
                            },
                            singleLine = true,
                            textStyle =
                                MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(28.dp),
                            colors =
                                TextFieldDefaults.colors(
                                    unfocusedContainerColor =
                                        MaterialTheme.colorScheme
                                            .surfaceContainerHigh,
                                    focusedContainerColor =
                                        MaterialTheme.colorScheme
                                            .surfaceContainerHigh,
                                    unfocusedIndicatorColor =
                                        Color.Transparent,
                                    focusedIndicatorColor =
                                        Color.Transparent,
                                ),
                        )

                        Spacer(Modifier.height(Tokens.SpacingSm))
                        Row(
                            modifier =
                                Modifier.horizontalScroll(
                                    rememberScrollState(),
                                ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    Tokens.SpacingSm,
                                ),
                        ) {
                            EventType.entries.forEach { type ->
                                val selected = type in selectedTypes
                                val (iconRes, bgColor, tint) =
                                    eventBadgeStyle(type)
                                Surface(
                                    onClick = {
                                        selectedTypes =
                                            if (selected) {
                                                selectedTypes - type
                                            } else {
                                                selectedTypes + type
                                            }
                                    },
                                    shape = Tokens.ChipShape,
                                    color =
                                        if (selected) {
                                            bgColor
                                        } else {
                                            Color.Transparent
                                        },
                                    border =
                                        if (selected) {
                                            null
                                        } else {
                                            BorderStroke(
                                                1.dp,
                                                MaterialTheme
                                                    .colorScheme
                                                    .outlineVariant,
                                            )
                                        },
                                ) {
                                    Row(
                                        modifier =
                                            Modifier.padding(
                                                horizontal = 8.dp,
                                                vertical = 4.dp,
                                            ),
                                        verticalAlignment =
                                            Alignment
                                                .CenterVertically,
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                4.dp,
                                            ),
                                    ) {
                                        Icon(
                                            painterResource(
                                                iconRes,
                                            ),
                                            contentDescription =
                                            null,
                                            modifier =
                                                Modifier.size(
                                                    14.dp,
                                                ),
                                            tint = tint,
                                        )
                                        Text(
                                            type.name
                                                .replace(
                                                    '_',
                                                    ' ',
                                                ),
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .labelSmall,
                                            color =
                                                if (selected) {
                                                    tint
                                                } else {
                                                    Color
                                                        .Unspecified
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        if (uniqueTiles.isNotEmpty()) {
                            Spacer(Modifier.height(Tokens.SpacingSm))
                            Row(
                                modifier =
                                    Modifier.horizontalScroll(
                                        rememberScrollState(),
                                    ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        Tokens.SpacingSm,
                                    ),
                            ) {
                                uniqueTiles.forEach { tile ->
                                    val selected =
                                        tile in selectedTiles
                                    Surface(
                                        onClick = {
                                            selectedTiles =
                                                if (selected) {
                                                    selectedTiles - tile
                                                } else {
                                                    selectedTiles + tile
                                                }
                                        },
                                        shape =
                                            Tokens.ChipShape,
                                        color =
                                            if (selected) {
                                                MaterialTheme
                                                    .colorScheme
                                                    .secondaryContainer
                                            } else {
                                                Color
                                                    .Transparent
                                            },
                                        border =
                                            if (selected) {
                                                null
                                            } else {
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme
                                                        .colorScheme
                                                        .outlineVariant,
                                                )
                                            },
                                    ) {
                                        Text(
                                            tile,
                                            modifier =
                                                Modifier
                                                    .padding(
                                                        horizontal =
                                                            8.dp,
                                                        vertical =
                                                            4.dp,
                                                    ),
                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .labelSmall,
                                            color =
                                                if (selected) {
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSecondaryContainer
                                                } else {
                                                    Color
                                                        .Unspecified
                                                },
                                        )
                                    }
                                }
                            }
                        }

                        stats?.let { s ->
                            HorizontalDivider(
                                modifier =
                                    Modifier.padding(
                                        vertical = 4.dp,
                                    ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .outlineVariant
                                        .copy(alpha = 0.5f),
                            )
                            Row(
                                modifier =
                                    Modifier.horizontalScroll(
                                        rememberScrollState(),
                                    ),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        Tokens.SpacingSm,
                                    ),
                            ) {
                                if (s.tapCount > 0) {
                                    StatChip(
                                        iconRes =
                                            R.drawable
                                                .ic_check_circle_24,
                                        containerColor =
                                            MaterialTheme.colorScheme
                                                .primaryContainer,
                                        contentColor =
                                            MaterialTheme.colorScheme
                                                .onPrimaryContainer,
                                        text =
                                            stringResource(
                                                R.string.stat_warm,
                                                s.warmCount,
                                            ),
                                    )
                                }
                                if (s.coldCount > 0) {
                                    val avgText =
                                        s.avgColdMs?.let {
                                            " (${it}ms)"
                                        } ?: ""
                                    StatChip(
                                        iconRes =
                                            R.drawable.ic_warning_24,
                                        containerColor =
                                            MaterialTheme.colorScheme
                                                .errorContainer,
                                        contentColor =
                                            MaterialTheme.colorScheme
                                                .onErrorContainer,
                                        text =
                                            stringResource(
                                                R.string.stat_cold,
                                                s.coldCount,
                                            ) + avgText,
                                    )
                                }
                                if (s.serviceDeaths > 0) {
                                    StatChip(
                                        iconRes =
                                            R.drawable.ic_error_24,
                                        containerColor =
                                            MaterialTheme.colorScheme
                                                .errorContainer,
                                        contentColor =
                                            MaterialTheme.colorScheme
                                                .onErrorContainer,
                                        text =
                                            stringResource(
                                                R.string.stat_died,
                                                s.serviceDeaths,
                                            ),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(Tokens.SpacingSm))

                        filtered
                            .asReversed()
                            .forEachIndexed { index, event ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color =
                                            MaterialTheme
                                                .colorScheme
                                                .outlineVariant
                                                .copy(alpha = 0.5f),
                                    )
                                }
                                TileEventRow(
                                    event = event,
                                    expanded = expandedTimestamp == event.timestampMs,
                                    onToggle = {
                                        expandedTimestamp =
                                            if (expandedTimestamp == event.timestampMs) {
                                                null
                                            } else {
                                                event.timestampMs
                                            }
                                    },
                                )
                            }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    stringResource(
                        R.string.clear_log_confirm_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        R.string.clear_log_confirm_text,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClear()
                }) {
                    Text(stringResource(R.string.clear_log))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearDialog = false },
                ) {
                    Text(
                        stringResource(android.R.string.cancel),
                    )
                }
            },
        )
    }
}

@Composable
private fun TileEventRow(
    event: TileEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val timeFormat =
        remember {
            DateFormat.getTimeFormat(context)
        }
    val time =
        remember(event.timestampMs) {
            timeFormat.format(Date(event.timestampMs))
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
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
                    event.tileName
                        ?: stringResource(R.string.event_system),
                    style = MaterialTheme.typography.bodyMedium,
                )
                event.detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }
            event.durationMs?.let { ms ->
                Spacer(Modifier.width(Tokens.SpacingSm))
                DurationBadge(ms)
            }
        }

        AnimatedVisibility(visible = expanded) {
            val (_, _, accentColor) = eventBadgeStyle(event.type)
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
                        stringResource(
                            R.string.cold_start_latency,
                            event.durationMs,
                        )
                    }

                    else -> {
                        stringResource(
                            R.string.latency,
                            event.durationMs,
                        )
                    }
                }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Tokens.SpacingSm)
                        .drawBehind {
                            drawRoundRect(
                                color = accentColor,
                                topLeft = Offset.Zero,
                                size =
                                    Size(
                                        3.dp.toPx(),
                                        size.height,
                                    ),
                                cornerRadius =
                                    CornerRadius(
                                        1.5.dp.toPx(),
                                    ),
                            )
                        }.padding(
                            start = Tokens.SpacingMd,
                        ),
                verticalArrangement =
                    Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    event.type.name.replace('_', ' '),
                    style =
                        MaterialTheme.typography.labelSmall,
                    color = accentColor,
                )
                Text(
                    relativeTime,
                    style =
                        MaterialTheme.typography.labelSmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
                Text(
                    fullTimestamp,
                    style =
                        MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
                durationLabel?.let {
                    Text(
                        it,
                        style =
                            MaterialTheme.typography
                                .labelSmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EventBadge(type: EventType) {
    val (iconRes, bgColor, tint) = eventBadgeStyle(type)
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .background(bgColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
    }
}

@Composable
private fun eventBadgeStyle(type: EventType): Triple<Int, Color, Color> =
    when (type) {
        EventType.WARM -> {
            Triple(
                R.drawable.ic_check_circle_24,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        EventType.COLD_START -> {
            Triple(
                R.drawable.ic_warning_24,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        EventType.MEM_PRESSURE -> {
            Triple(
                R.drawable.ic_block_24,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }

        EventType.LIMIT_SET -> {
            Triple(
                R.drawable.ic_info_24,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        EventType.SERVICE_DIED -> {
            Triple(
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
            formatDuration(ms),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatDuration(ms: Long): String = "${ms}ms"

@Composable
private fun StatChip(
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
            horizontalArrangement =
                Arrangement.spacedBy(4.dp),
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

private fun formatRelativeTime(timestampMs: Long): String {
    val diff = System.currentTimeMillis() - timestampMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}



@Composable
private fun RestartAction(
    hasRoot: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(vertical = Tokens.SpacingSm),
        horizontalAlignment = Alignment.Start,
    ) {
        FilledTonalButton(onClick = onClick) {
            Text(
                stringResource(R.string.restart_systemui),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (!hasRoot) {
            Text(
                stringResource(R.string.restart_requires_root),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}



private val previewProviders =
    listOf(
        TileProviderInfo("Shizuku", 2),
        TileProviderInfo("Tailscale", 1),
        TileProviderInfo("KDE Connect", 3),
        TileProviderInfo("Caffeine", 1),
    )

private val previewEvents =
    listOf(
        TileEvent(
            System.currentTimeMillis() - 6000,
            EventType.LIMIT_SET,
            null,
            null,
            "mMaxBound=12",
        ),
        TileEvent(
            System.currentTimeMillis() - 4000,
            EventType.WARM,
            "Caffeine (Toolkit tiles)",
            3L,
            null,
        ),
        TileEvent(
            System.currentTimeMillis() - 3000,
            EventType.COLD_START,
            "Shizuku",
            1200L,
            null,
        ),
        TileEvent(
            System.currentTimeMillis() - 1000,
            EventType.MEM_PRESSURE,
            null,
            null,
            "Memory pressure intercepted, limit preserved at 12",
        ),
    )

private val previewState =
    DashboardUiState.Success(
        prefs =
            PrefsState(
                maxBound = 12,
                debugLogs = true,
            ),
        xposedActive = true,
        hookStatus = TileServicesHook.HOOK_ALL,
        activeQsCount = 8,
        hasRoot = true,
        tileProviders = previewProviders,
        tileEvents = previewEvents,
    )

@Preview(name = "Dashboard")
@Composable
private fun DashboardPreview() {
    QsTheme {
        DashboardScreen(
            uiState = previewState,
            onSavePref = { _, _ -> },
            onRestartSystemUi = {},
        )
    }
}

