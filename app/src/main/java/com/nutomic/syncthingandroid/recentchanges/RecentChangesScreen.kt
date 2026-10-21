package com.nutomic.syncthingandroid.recentchanges

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.isTelevision
import java.text.DateFormat
import java.util.Date

// Smartisan palette, resolved per composition so day/night both work.
@Composable private fun smInk(): Color = colorResource(R.color.sm_ink)
@Composable private fun smInkWeak(): Color = colorResource(R.color.sm_ink_weak)
@Composable private fun smHairline(): Color = colorResource(R.color.sm_hairline)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentChangesScreen(
    changes: List<RecentChange>,
    isRefreshing: Boolean,
    showExactTimes: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExactTimes: () -> Unit,
    onItemClick: (RecentChange) -> Unit,
) {
    val configuration = LocalConfiguration.current

    // Kept alongside pull-to-refresh: on Android TV the list is driven by a D-pad, where a pull
    // gesture is not reachable.
    val refreshAction: @Composable () -> Unit = {
        IconButton(onClick = onRefresh) {
            Icon(
                painter = painterResource(R.drawable.baseline_refresh_24),
                contentDescription = stringResource(R.string.refresh),
                tint = smInk(),
            )
        }
    }

    Scaffold(
        // Smartisan: let the striped window canvas show through.
        containerColor = Color.Transparent,
        topBar = {
            if (configuration.isTelevision) {
                // Plain bar because of the limited vertical space, and no back button: TV remotes
                // have a dedicated one. Mirrors SettingsScaffold.
                TopAppBar(
                    title = { Text(stringResource(R.string.recent_changes_title)) },
                    actions = {
                        refreshAction()
                        OverflowMenu(
                            showExactTimes = showExactTimes,
                            onToggleExactTimes = onToggleExactTimes,
                        )
                    },
                )
            } else {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.recent_changes_title),
                            color = smInk(),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        refreshAction()
                        OverflowMenu(
                            showExactTimes = showExactTimes,
                            onToggleExactTimes = onToggleExactTimes,
                        )
                    },
                )
            }
        },
    ) { paddingValues ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        val listContent: @Composable BoxScope.() -> Unit = {
            if (changes.isEmpty()) {
                EmptyState(Modifier.align(Alignment.Center))
            } else {
                // One continuous white card, rows separated by hairlines (Smartisan list style).
                Surface(
                    color = colorResource(R.color.sm_card),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                ) {
                    LazyColumn {
                        itemsIndexed(changes) { index, change ->
                            RecentChangeItem(
                                change = change,
                                showExactTimes = showExactTimes,
                                showDivider = index != changes.lastIndex,
                                onClick = { onItemClick(change) },
                            )
                        }
                    }
                }
            }
        }

        if (configuration.isTelevision) {
            // No pull-to-refresh on TV. The gesture is unreachable with a D-pad anyway, and the
            // indicator's nested-scroll hook also reacts to the programmatic scrolling that focus
            // movement causes: moving focus back up to the first row starts a "pull" that no gesture
            // ever releases, leaving the spinner stuck on screen. The top bar's refresh action is the
            // TV entry point.
            Box(modifier = containerModifier, content = listContent)
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = containerModifier,
                content = listContent,
            )
        }
    }
}

/** Internal rather than private so it can be previewed on its own; see RecentChangesPreviews.kt. */
@Composable
internal fun RecentChangeItem(
    change: RecentChange,
    showExactTimes: Boolean,
    showDivider: Boolean = false,
    onClick: () -> Unit = {},
) {
    // Deleted entries no longer exist on disk, so they are not actionable — but they must still be
    // focusable. `clickable(enabled = false)` also drops focusability, which made D-pad navigation
    // skip them: focus jumped over a run of deleted rows, and because focus movement is what scrolls
    // a LazyColumn on TV, the rows in the middle of a long run could never be brought into view.
    // Focusable-without-clickable keeps them reachable while leaving them inert, and needs an
    // explicit indication so the focus highlight is still drawn.
    val interactionSource = remember { MutableInteractionSource() }
    val rowModifier = if (change.canOpen) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
            .focusable(interactionSource = interactionSource)
    }

    Column(modifier = rowModifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(
                imageVector = change.icon,
                contentDescription = null,
                tint = change.iconTint(),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = change.folderPath,
                    color = smInkWeak(),
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = change.filename,
                    color = smInk(),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                ChangeMetadata(change, showExactTimes)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = smHairline(),
                thickness = 1.dp,
            )
        }
    }
}

/**
 * Time and modifying device on a single line, labelled by icons rather than "Time:" / "Device:"
 * prefixes. The device name takes the remaining width and ellipsizes; the timestamp keeps its
 * intrinsic width so it never truncates first.
 */
@Composable
private fun ChangeMetadata(change: RecentChange, showExactTimes: Boolean) {
    val timeText = change.timeText(showExactTimes)
    val timeLabel = stringResource(R.string.modification_time, timeText)
    val deviceLabel = stringResource(R.string.modified_by_device, change.modifiedByName)
    val hasDevice = change.modifiedByName.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The icons carry the labels visually; keep the fully labelled translated strings for
        // screen readers so dropping the prefixes costs no accessibility.
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = if (hasDevice) "$timeLabel, $deviceLabel" else timeLabel
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = smInkWeak(),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = timeText,
            color = smInkWeak(),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (hasDevice) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = smInkWeak(),
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = change.modifiedByName,
                color = smInkWeak(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Relative ("2 minutes ago") by default, absolute when the user opts in. Relative wins for a recent-
 * activity feed because the useful question is "how fresh is this", and
 * [DateUtils.getRelativeTimeSpanString] switches to a date on its own once an entry is old enough, so
 * long-past events do not read as "3,000,000 minutes ago".
 *
 * Falls back to the timestamp exactly as Syncthing reported it if it could not be parsed.
 */
@Composable
private fun RecentChange.timeText(showExactTimes: Boolean): String {
    val millis = timeMillis ?: return rawTime
    return if (showExactTimes) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(millis))
    } else {
        // Recomputed on every recomposition, which the foreground poll triggers every few seconds,
        // so the label stays current without a dedicated ticker.
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS,
        ).toString()
    }
}

@Composable
private fun OverflowMenu(
    showExactTimes: Boolean,
    onToggleExactTimes: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Drop the panel right below the kebab button (48dp touch target).
    val yOffset = with(LocalDensity.current) { 44.dp.roundToPx() }
    // Pull the panel left by half the IconButton padding so its right edge
    // lines up with the 24dp kebab glyph itself, not the 48dp touch target.
    val xOffset = with(LocalDensity.current) { 12.dp.roundToPx() }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = colorResource(R.color.sm_ink),
            )
        }
        if (expanded) {
            Popup(
                // Right edge hugs the kebab the menu drops from — never overshoots
                // toward the screen edge the way the M3 menu did.
                alignment = Alignment.TopEnd,
                offset = IntOffset(-xOffset, yOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                // Smartisan compact popup: white rounded card, 40dp row.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorResource(R.color.sm_card),
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clickable {
                                onToggleExactTimes()
                                expanded = false
                            }
                            .padding(start = 14.dp, end = 16.dp),
                    ) {
                        if (showExactTimes) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colorResource(R.color.sm_ink),
                                modifier = Modifier.size(16.dp),
                            )
                        } else {
                            // Keeps the label aligned whether or not the check is drawn.
                            Spacer(Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.show_exact_times),
                            color = colorResource(R.color.sm_ink),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Rule,
            contentDescription = null,
            tint = smInkWeak(),
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.no_recent_changes),
            color = smInkWeak(),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private val RecentChange.icon: ImageVector
    get() = when (type) {
        ChangeType.DIR -> when (action) {
            ChangeAction.ADDED -> Icons.Outlined.CreateNewFolder
            ChangeAction.DELETED -> Icons.Outlined.FolderDelete
            ChangeAction.MODIFIED -> Icons.Outlined.Folder
            ChangeAction.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }
        ChangeType.FILE -> when (action) {
            ChangeAction.ADDED -> Icons.AutoMirrored.Outlined.NoteAdd
            ChangeAction.DELETED -> Icons.Outlined.Delete
            ChangeAction.MODIFIED -> Icons.Outlined.EditNote
            ChangeAction.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }
        ChangeType.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    }

/** Smartisan status colors: green added / blue modified / red deleted / gray unknown. */
@Composable
private fun RecentChange.iconTint(): Color = when (action) {
    ChangeAction.ADDED -> colorResource(R.color.text_green)
    ChangeAction.DELETED -> colorResource(R.color.text_red)
    ChangeAction.MODIFIED -> colorResource(R.color.text_blue)
    ChangeAction.UNKNOWN -> smInkWeak()
}
