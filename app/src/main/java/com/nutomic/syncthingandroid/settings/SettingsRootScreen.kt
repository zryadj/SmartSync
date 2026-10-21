package com.nutomic.syncthingandroid.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.SyncthingService

fun EntryProviderScope<SettingsRoute>.settingsRootEntry() {
    entry<SettingsRoute.Root> {
        SettingsRootScreen()
    }
}

/** Smartisan section label above a grouped card. */
@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        color = colorResource(R.color.sm_ink_weak),
        fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** Smartisan white rounded card holding one settings group. */
@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = colorResource(R.color.sm_card),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Column {
            content()
        }
    }
}

/** Smartisan settings row: gray linear glyph + ink title + optional summary + thin chevron. */
@Composable
private fun SettingsRow(
    iconRes: Int,
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val inkColor = colorResource(R.color.sm_ink).let { if (enabled) it else it.copy(alpha = 0.38f) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colorResource(R.color.sm_icon_gray).let { if (enabled) it else it.copy(alpha = 0.38f) },
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = inkColor, fontSize = 15.5.sp)
                if (summary != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = colorResource(R.color.sm_ink_weak).let { if (enabled) it else it.copy(alpha = 0.38f) },
                        fontSize = 12.5.sp,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = colorResource(R.color.sm_chevron),
            )
        }
        if (!last) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 64.dp),
                color = colorResource(R.color.sm_hairline),
                thickness = 1.dp,
            )
        }
    }
}

@Composable
fun SettingsRootScreen() {
    val navigator = LocalSettingsNavigator.current
    val stService = LocalSyncthingService.current
    val stServiceTick = LocalServiceUpdateTick.current

    val isSyncthingOptionsEnabled by remember(stService, stServiceTick) {
        derivedStateOf { stService != null && stService.currentState == SyncthingService.State.ACTIVE }
    }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
    ) {
        item {
            SettingsSectionLabel(stringResource(R.string.settings_group_sync))
        }
        item {
            SettingsGroupCard {
                SettingsRow(
                    iconRes = R.drawable.ic_sm_runcond,
                    title = stringResource(R.string.run_conditions_title),
                    onClick = { navigator.navigateTo(SettingsRoute.RunConditions) },
                )
                SettingsRow(
                    iconRes = R.drawable.ic_sm_theme,
                    title = stringResource(R.string.category_user_interface),
                    onClick = { navigator.navigateTo(SettingsRoute.UserInterface) },
                )
                SettingsRow(
                    iconRes = R.drawable.ic_sm_bolt,
                    title = stringResource(R.string.category_behaviour),
                    last = true,
                    onClick = { navigator.navigateTo(SettingsRoute.Behavior) },
                )
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_group_data))
        }
        item {
            SettingsGroupCard {
                SettingsRow(
                    iconRes = R.drawable.ic_sm_settings,
                    title = stringResource(R.string.category_syncthing_options),
                    enabled = isSyncthingOptionsEnabled,
                    onClick = { navigator.navigateTo(SettingsRoute.SyncthingOptions) },
                )
                SettingsRow(
                    iconRes = R.drawable.ic_sm_import_export,
                    title = stringResource(R.string.category_backup),
                    last = true,
                    onClick = { navigator.navigateTo(SettingsRoute.ImportExport) },
                )
            }
        }

        item {
            SettingsSectionLabel(stringResource(R.string.settings_group_maintenance))
        }
        item {
            SettingsGroupCard {
                SettingsRow(
                    iconRes = R.drawable.ic_sm_bug,
                    title = stringResource(R.string.category_debug),
                    onClick = { navigator.navigateTo(SettingsRoute.Troubleshooting) },
                )
                SettingsRow(
                    iconRes = R.drawable.ic_sm_flask,
                    title = stringResource(R.string.category_experimental),
                    onClick = { navigator.navigateTo(SettingsRoute.Experimental) },
                )
                SettingsRow(
                    iconRes = R.drawable.ic_sm_info,
                    title = stringResource(R.string.category_about),
                    last = true,
                    onClick = { navigator.navigateTo(SettingsRoute.About) },
                )
            }
        }
    }
}
