package com.nutomic.syncthingandroid.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.rememberPreferenceState


fun EntryProviderScope<SettingsRoute>.settingsBehaviorEntry() {
    entry<SettingsRoute.Behavior> {
        SettingsBehaviorScreen()
    }
}


@Composable
fun SettingsBehaviorScreen() {

    val autoStart = rememberPreferenceState(Constants.PREF_START_SERVICE_ON_BOOT, false)
    val broadcast = rememberPreferenceState(Constants.PREF_BROADCAST_SERVICE_CONTROL, false)
    val overwrite = rememberPreferenceState(Constants.PREF_ALLOW_OVERWRITE_FILES, false)

    SettingsScaffold(
        title = stringResource(R.string.category_behaviour),
    ) {
        item {
            SettingsGroupCard(modifier = Modifier.padding(top = 14.dp)) {
                SwitchPreference(
                    title = { Text(stringResource(R.string.behaviour_autostart_title)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_sm_power),
                            contentDescription = null,
                            tint = colorResource(R.color.sm_icon_gray),
                            modifier = Modifier.width(24.dp),
                        )
                    },
                    summary = { Text(stringResource(R.string.behaviour_autostart_summary)) },
                    state = autoStart,
                )
                SwitchPreference(
                    title = { Text(stringResource(R.string.broadcast_service_control_title))},
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_sm_bolt),
                            contentDescription = null,
                            tint = colorResource(R.color.sm_icon_gray),
                            modifier = Modifier.width(24.dp),
                        )
                    },
                    summary = { Text(stringResource(R.string.broadcast_service_control_summary))},
                    state = broadcast,
                )
                SwitchPreference(
                    title = { Text(stringResource(R.string.allow_overwrite_files_title)) },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_sm_folder),
                            contentDescription = null,
                            tint = colorResource(R.color.sm_icon_gray),
                            modifier = Modifier.width(24.dp),
                        )
                    },
                    summary = { Text(stringResource(R.string.allow_overwrite_files_summary))},
                    state = overwrite,
                )
            }
        }
    }
}
