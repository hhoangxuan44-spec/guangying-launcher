package app.lawnchair.guangying.ui

import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.guangying.panel.PanelPopup
import app.lawnchair.guangying.panel.PanelPrefs
import app.lawnchair.guangying.panel.PanelService
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.GuangyingPermissions
import com.android.launcher3.R
import kotlinx.coroutines.delay

/** 控制面板的设置：开关侧边把手、选择把手在哪一边、手势用法、现场试用。 */
@Composable
fun PanelPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val prefs = remember { PanelPrefs(context) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var right by remember { mutableStateOf(prefs.rightSide) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            canOverlay = Settings.canDrawOverlays(context)
        }
    }

    fun apply() {
        if (enabled && canOverlay) {
            PanelService.stop(context)
            PanelService.start(context)
        } else {
            PanelService.stop(context)
        }
    }

    PreferenceLayout(
        label = stringResource(R.string.gy_panel_title),
        modifier = modifier,
    ) {
        if (!canOverlay) {
            WarningPreference(text = stringResource(R.string.gy_panel_need_overlay))
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(R.string.gy_perm_center),
                    subtitle = stringResource(R.string.gy_perm_center_sub),
                    onClick = { navController.navigate(GuangyingPermissions) },
                )
            }
        }

        PreferenceGroup(description = stringResource(R.string.gy_panel_switch_desc)) {
            PreferenceTemplate(
                title = { Text(text = stringResource(R.string.gy_panel_switch)) },
                enabled = canOverlay,
                modifier = Modifier.clickable(enabled = canOverlay) {
                    enabled = !enabled
                    prefs.enabled = enabled
                    apply()
                },
                endWidget = {
                    Switch(
                        checked = enabled && canOverlay,
                        enabled = canOverlay,
                        onCheckedChange = {
                            enabled = it
                            prefs.enabled = it
                            apply()
                        },
                    )
                },
            )
            PreferenceTemplate(
                title = { Text(text = stringResource(R.string.gy_panel_side)) },
                description = {
                    Text(text = stringResource(if (right) R.string.gy_panel_side_right else R.string.gy_panel_side_left))
                },
                modifier = Modifier.clickable {
                    right = !right
                    prefs.rightSide = right
                    apply()
                },
            )
        }

        PreferenceGroup(description = stringResource(R.string.gy_panel_gesture_hint)) {
            ClickablePreference(
                label = stringResource(R.string.gy_panel_preview),
                onClick = {
                    (context as? android.app.Activity)?.let { PanelPopup.show(it) }
                },
            )
        }
    }
}
