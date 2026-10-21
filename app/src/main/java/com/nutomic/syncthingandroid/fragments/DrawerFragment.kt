package com.nutomic.syncthingandroid.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.CheckUpdateActivity
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.recentchanges.RecentChangesActivity
import com.nutomic.syncthingandroid.webgui.WebGuiActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.service.SyncthingService.OnServiceStateChangeListener
import com.nutomic.syncthingandroid.settings.SettingsActivity
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.util.AppUpdater
import com.nutomic.syncthingandroid.util.isTelevision

class DrawerFragment : Fragment(), OnServiceStateChangeListener {

    private val stServiceRunning = mutableStateOf(false)
    private val focusRequested = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        setContent {
            ApplicationTheme {
                DrawerContent(stServiceRunning.value, focusRequested)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val drawerContainer = requireActivity().findViewById<View>(R.id.drawer)

        // inset handling: the drawer is intentionally immersive — its striped
        // canvas runs behind the status bar, so the top inset is deliberately
        // ignored here (bottom is handled by the activity content padding).
        ViewCompat.setOnApplyWindowInsetsListener(drawerContainer) { v, i ->
            v.setPadding(0, 0, 0, 0)
            i
        }
        ViewCompat.requestApplyInsets(drawerContainer)

        // width restriction
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val maxWidthPx = (360 * displayMetrics.density).toInt()
        val minGapPx = (56 * displayMetrics.density).toInt()

        val desiredWidth = minOf(maxWidthPx, screenWidth - minGapPx)

        drawerContainer.layoutParams = drawerContainer.layoutParams.apply {
            width = desiredWidth
        }
    }

    override fun onServiceStateChange(currentState: SyncthingService.State?) {
        stServiceRunning.value = currentState == SyncthingService.State.ACTIVE
    }

    fun drawerOpened() {
        focusRequested.value = true
    }
}


@Composable
private fun DrawerContent(
    stServiceRunning: Boolean,
    focusRequested: MutableState<Boolean>,
) {
    val activity = LocalActivity.current as MainActivity
    val config = LocalConfiguration.current

    val firstItemFocusRequester = remember { FocusRequester() }

    var updateState by remember { mutableStateOf<UpdateUiState?>(null) }

    LaunchedEffect(focusRequested.value) {
        if (focusRequested.value) {
            firstItemFocusRequester.requestFocus()
            focusRequested.value = false
        }
    }

    // Colors for the drawBehind stripe canvas must be captured in composition.
    val drawerCanvasColor = colorResource(R.color.bg_canvas_base)
    val drawerStripeColor = if (isSystemInDarkTheme()) Color(0x06FFFFFF) else Color(0x04000000)

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Smartisan: striped canvas painted by the drawer itself.
                drawRect(drawerCanvasColor)
                val period = 6.dp.toPx()
                val stripeWidth = 2.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(
                        drawerStripeColor,
                        topLeft = Offset(x, 0f),
                        size = Size(stripeWidth, size.height)
                    )
                    x += period
                }
            },
        // Smartisan: the stripes above are the container.
        drawerContainerColor = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            DrawerHeader()

            LazyColumn(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .weight(1f)
            ) {
                item {
                    DrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_sm_qr), null) },
                        label = { Text(stringResource(R.string.show_device_id)) },
                        onClick = {
                            activity.showQrCodeDialog()
                            activity.closeDrawer()
                        },
                        modifier = Modifier.focusRequester(firstItemFocusRequester)
                    )
                }
                item {
                    DrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_sm_recent), null) },
                        label = { Text(stringResource(R.string.recent_changes_title)) },
                        onClick = {
                            val intent = Intent(activity, RecentChangesActivity::class.java)
                            activity.startActivity(intent)
                            activity.closeDrawer()
                        },
                        enabled = stServiceRunning,
                    )
                }

                if (Constants.isDebuggable(activity) || !config.isTelevision) {
                    item {
                        DrawerItem(
                            icon = { Icon(painterResource(R.drawable.ic_sm_webgui), null) },
                            label = { Text(stringResource(R.string.web_gui_title)) },
                            onClick = {
                                activity.startActivity(Intent(activity, WebGuiActivity::class.java))
                                activity.closeDrawer()
                            },
                            enabled = stServiceRunning,
                        )
                    }
                }

                item {
                    DrawerItem(
                        icon = { Icon(painterResource(R.drawable.ic_sm_import_export), null) },
                        label = { Text(stringResource(R.string.category_backup)) },
                        onClick = {
                            val intent = Intent(activity, SettingsActivity::class.java).apply {
                                putExtra(SettingsActivity.EXTRA_START_DESTINATION, "ImportExport")
                            }
                            activity.startActivity(intent)
                            activity.closeDrawer()
                        },
                    )
                }
                item {
                    RestartDrawerItem(stServiceRunning)
                }
            }

            // No standalone divider here: the last item of the list above already
            // draws its own hairline, and a second one stacked a double line
            // right above the settings group.

            Column(
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                DrawerItem(
                    icon = { Icon(painterResource(R.drawable.ic_sm_settings), null) },
                    label = { Text(stringResource(R.string.settings_title)) },
                    onClick = {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java))
                        activity.closeDrawer()
                    },
                )
                DrawerItem(
                    icon = { Icon(painterResource(R.drawable.ic_sm_update), null) },
                    label = { Text(stringResource(R.string.check_for_updates_title)) },
                    onClick = {
                        activity.closeDrawer()
                        activity.startActivity(
                            Intent(activity, CheckUpdateActivity::class.java)
                        )
                    },
                )
                ExitDrawerItem()
            }
        }
    }

    UpdateDialogs(
        state = updateState,
        onStateChange = { updateState = it },
    )
}

@Composable
private fun RestartDrawerItem(stServiceRunning: Boolean) {
    val activity = LocalActivity.current as MainActivity
    var showAlert by rememberSaveable { mutableStateOf(false) }

    DrawerItem(
        icon = { Icon(painterResource(R.drawable.ic_sm_restart), null) },
        label = { Text(stringResource(R.string.restart)) },
        onClick = {
            showAlert = true
            activity.closeDrawer()
        },
        enabled = stServiceRunning,
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.dialog_confirm_restart)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(activity, SyncthingService::class.java).apply {
                            action = SyncthingService.ACTION_RESTART
                        }
                        activity.startService(intent)
                        showAlert = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ExitDrawerItem() {
    val activity = LocalActivity.current as MainActivity
    val prefs = activity.mPreferences

    var showAlert by rememberSaveable { mutableStateOf(false) }

    DrawerItem(
        icon = { Icon(painterResource(R.drawable.ic_sm_exit), null) },
        label = { Text(stringResource(R.string.exit)) },
        onClick = {
            val isAutostartOn = prefs.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false)
            if (isAutostartOn) {
                showAlert = true
                activity.closeDrawer()
            } else {
                activity.doExit()
            }
        },
    )
    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(R.string.dialog_exit_while_running_as_service_title)) },
            text = { Text(stringResource(R.string.dialog_exit_while_running_as_service_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        activity.doExit()
                    },
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAlert = false },
                ) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

// drawer item wrapper because NavigationDrawerItem doesn't support `enabled`
@Composable
/**
 * Smartisan settings-row style drawer item: gray linear glyph, ink label,
 * hairline divider starting after the glyph. 52dp rows, no M3 pill chrome.
 */
private fun DrawerItem(
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val inkColor = colorResource(R.color.sm_ink).let { if (enabled) it else it.copy(alpha = 0.38f) }
    val glyphColor = colorResource(R.color.sm_icon_gray).let { if (enabled) it else it.copy(alpha = 0.38f) }
    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .heightIn(min = 52.dp)
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 16.dp)
            ) {
                CompositionLocalProvider(LocalContentColor provides glyphColor, content = icon)
                Spacer(Modifier.width(20.dp))
                CompositionLocalProvider(LocalContentColor provides inkColor, content = label)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 64.dp),
            color = colorResource(R.color.sm_hairline),
            thickness = 1.dp
        )
    }
}

@Composable
private fun DrawerHeader() {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 24.dp, top = 24.dp, bottom = 18.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                // Smartisan: dark ink title, no logo mark, no device-ID code
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}


private sealed interface UpdateUiState {
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data object CheckFailed : UpdateUiState
    data class Available(val info: AppUpdater.UpdateInfo) : UpdateUiState
    data class Downloading(val progress: Int) : UpdateUiState
    data object DownloadFailed : UpdateUiState
    data object NeedPermission : UpdateUiState
}

@Composable
private fun UpdateDialogs(
    state: UpdateUiState?,
    onStateChange: (UpdateUiState?) -> Unit,
) {
    val context = LocalContext.current
    if (state == null) return
    when (state) {
        is UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.check_for_updates_title)) },
            text = { Text(stringResource(R.string.update_checking)) },
            confirmButton = {},
        )
        is UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.check_for_updates_title)) },
            text = { Text(stringResource(R.string.update_up_to_date)) },
            confirmButton = {
                TextButton(onClick = { onStateChange(null) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
        is UpdateUiState.CheckFailed -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.check_for_updates_title)) },
            text = { Text(stringResource(R.string.update_check_failed)) },
            confirmButton = {
                TextButton(onClick = { onStateChange(null) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.update_available_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_available_version, state.info.versionName))
                    val changelog = state.info.changelog
                    if (!changelog.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = changelog.lines().filter { it.isNotBlank() }
                                .joinToString("\n") { "\u00b7 " + it.trim() },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                val context = LocalContext.current
                TextButton(onClick = {
                    onStateChange(UpdateUiState.Downloading(0))
                    AppUpdater.downloadApk(
                        context = context,
                        info = state.info,
                        onProgress = { p -> onStateChange(UpdateUiState.Downloading(p)) },
                        onDone = { file ->
                            when {
                                file == null -> onStateChange(UpdateUiState.DownloadFailed)
                                !AppUpdater.canInstall(context) ->
                                    onStateChange(UpdateUiState.NeedPermission)
                                else -> {
                                    AppUpdater.installApk(context, file)
                                    onStateChange(null)
                                }
                            }
                        }
                    )
                }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { onStateChange(null) }) {
                    Text(stringResource(R.string.update_later))
                }
            },
        )
        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.check_for_updates_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.update_download_progress, state.progress))
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            },
            confirmButton = {},
        )
        is UpdateUiState.DownloadFailed -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.check_for_updates_title)) },
            text = { Text(stringResource(R.string.update_download_failed)) },
            confirmButton = {
                TextButton(onClick = { onStateChange(null) }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
        is UpdateUiState.NeedPermission -> AlertDialog(
            onDismissRequest = { onStateChange(null) },
            title = { Text(stringResource(R.string.update_install_permission_title)) },
            text = { Text(stringResource(R.string.update_install_permission_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onStateChange(null)
                    AppUpdater.openInstallPermissionSettings(context)
                }) {
                    Text(stringResource(R.string.update_open_settings))
                }
            },
        )
    }
}
