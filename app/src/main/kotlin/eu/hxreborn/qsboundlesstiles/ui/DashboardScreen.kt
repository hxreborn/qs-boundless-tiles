package eu.hxreborn.qsboundlesstiles.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.hxreborn.qsboundlesstiles.R
import eu.hxreborn.qsboundlesstiles.hook.TileServicesHook
import eu.hxreborn.qsboundlesstiles.prefs.PrefSpec
import eu.hxreborn.qsboundlesstiles.prefs.Prefs
import eu.hxreborn.qsboundlesstiles.scanner.TileProviderInfo
import eu.hxreborn.qsboundlesstiles.ui.theme.QsTheme
import eu.hxreborn.qsboundlesstiles.ui.theme.Tokens

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
    val activityState = rememberTileActivityState()

    val filteredReversed =
        remember(
            state?.tileEvents,
            activityState.searchQuery,
            activityState.selectedTiles,
            activityState.selectedTypes,
        ) {
            val events = state?.tileEvents ?: emptyList()
            events
                .filter { e ->
                    val name = e.tileName ?: "System"
                    (
                        activityState.searchQuery.isBlank() ||
                            name.contains(activityState.searchQuery, true)
                    ) &&
                        (
                            activityState.selectedTiles.isEmpty() ||
                                name in activityState.selectedTiles
                        ) &&
                        (
                            activityState.selectedTypes.isEmpty() ||
                                e.type in activityState.selectedTypes
                        )
                }.reversed()
        }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            item(key = "status") {
                StatusBanner(
                    state = state,
                    modifier =
                        Modifier.padding(
                            horizontal = Tokens.ScreenHorizontalPadding,
                            vertical = Tokens.SpacingMd / 2,
                        ),
                )
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
                        modifier =
                            Modifier.padding(
                                horizontal = Tokens.ScreenHorizontalPadding,
                                vertical = Tokens.SpacingMd,
                            ),
                    )
                }

                if (state.tileProviders.isNotEmpty()) {
                    item(key = "providers") {
                        TileProvidersCard(
                            providers = state.tileProviders,
                            modifier =
                                Modifier.padding(
                                    horizontal = Tokens.ScreenHorizontalPadding,
                                    vertical = Tokens.SpacingMd / 2,
                                ),
                        )
                    }
                }

                item(key = "activity_header") {
                    TileActivityCard(
                        events = state.tileEvents,
                        activityState = activityState,
                        onClear = onClearEvents,
                        modifier =
                            Modifier.padding(
                                start = Tokens.ScreenHorizontalPadding,
                                end = Tokens.ScreenHorizontalPadding,
                                top = Tokens.SpacingMd,
                            ),
                    )
                }

                if (activityState.expanded && filteredReversed.isNotEmpty()) {
                    items(
                        items = filteredReversed,
                        key = { it.timestampMs },
                        contentType = { "event" },
                    ) { event ->
                        val isLast = event === filteredReversed.last()
                        val shape =
                            if (isLast) {
                                RoundedCornerShape(
                                    bottomStart = Tokens.CardRadius,
                                    bottomEnd = Tokens.CardRadius,
                                )
                            } else {
                                RectangleShape
                            }
                        Surface(
                            modifier =
                                Modifier
                                    .padding(horizontal = Tokens.ScreenHorizontalPadding),
                            shape = shape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column {
                                HorizontalDivider(
                                    color =
                                        MaterialTheme.colorScheme.outlineVariant
                                            .copy(alpha = 0.5f),
                                )
                                TileEventRow(
                                    event = event,
                                    expanded = activityState.expandedTimestamp == event.timestampMs,
                                    onToggle = {
                                        activityState.expandedTimestamp =
                                            if (activityState.expandedTimestamp ==
                                                event.timestampMs
                                            ) {
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

                item(key = "actions") {
                    RestartAction(
                        hasRoot = state.hasRoot,
                        onClick = { showRestartDialog = true },
                        modifier =
                            Modifier.padding(
                                start = Tokens.ScreenHorizontalPadding,
                                end = Tokens.ScreenHorizontalPadding,
                                top = Tokens.SpacingMd,
                            ),
                    )
                }
            }

            item(key = "footer_spacer") {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_systemui)) },
            text = { Text(stringResource(R.string.restart_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showRestartDialog = false
                    onRestartSystemUi()
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun RestartAction(
    hasRoot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
