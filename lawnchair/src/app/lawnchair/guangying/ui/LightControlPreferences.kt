package app.lawnchair.guangying.ui

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.guangying.light.LightController
import app.lawnchair.guangying.light.LightMath
import app.lawnchair.guangying.light.LightScheduler
import app.lawnchair.guangying.light.LightStore
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.GuangyingPermissions
import com.android.launcher3.R
import kotlinx.coroutines.delay

/** 光控：一条 0–1000 的滑块同时控制屏幕亮度和系统「极暗」，加预设、定时和快捷开关。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LightControlPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val controller = remember { LightController(context) }
    val store = remember { LightStore(context) }
    var canWrite by remember { mutableStateOf(controller.canWriteSettings) }
    var canSecure by remember { mutableStateOf(controller.canWriteSecure) }
    var scale by remember { mutableIntStateOf(controller.read()) }
    var schedules by remember { mutableStateOf(store.schedules) }
    var presetTick by remember { mutableIntStateOf(0) }       // 预设改了就刷新界面

    // 权限在系统设置页里改完回来，这里每秒重新检查一次
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            canWrite = controller.canWriteSettings
            canSecure = controller.canWriteSecure
        }
    }

    val lowest = if (canSecure) 0 else LightMath.SPLIT
    fun set(value: Int) {
        val v = value.coerceIn(lowest, LightMath.MAX)
        scale = v
        controller.apply(v)
    }

    PreferenceLayout(
        label = stringResource(R.string.gy_light_title),
        modifier = modifier,
    ) {
        if (!canWrite) {
            WarningPreference(text = stringResource(R.string.gy_light_need_write))
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(R.string.gy_perm_center),
                    subtitle = stringResource(R.string.gy_perm_center_sub),
                    onClick = { navController.navigate(GuangyingPermissions) },
                )
            }
        }

        PreferenceGroup(
            heading = stringResource(R.string.gy_light_current),
            description = if (canSecure) null else stringResource(R.string.gy_light_need_secure),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(if (canWrite) 1f else 0.38f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = scale.toString(),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val b = LightMath.brightnessFor(scale)
                val lv = LightMath.dimLevelFor(scale)
                Text(
                    text = if (lv > 0) stringResource(R.string.gy_light_info_dim, b, lv) else stringResource(R.string.gy_light_info, b),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton(enabled = canWrite, label = "−", onStep = { set(scale - 1) }, onLongStep = { set(scale - 25) })
                    Slider(
                        value = scale.toFloat(),
                        onValueChange = { set(it.toInt()) },
                        valueRange = lowest.toFloat()..LightMath.MAX.toFloat(),
                        enabled = canWrite,
                        modifier = Modifier.weight(1f),
                    )
                    StepButton(enabled = canWrite, label = "+", onStep = { set(scale + 1) }, onLongStep = { set(scale + 25) })
                }
            }
        }

        PreferenceGroup(
            heading = stringResource(R.string.gy_light_presets),
            description = stringResource(R.string.gy_light_presets_hint),
        ) {
            val names = listOf(R.string.gy_preset_day, R.string.gy_preset_read, R.string.gy_preset_night)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .alpha(if (canWrite) 1f else 0.38f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (i in 0..2) {
                    val value = remember(presetTick) { store.preset(i) }
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                enabled = canWrite,
                                onClick = { set(store.preset(i)) },
                                onLongClick = {
                                    store.setPreset(i, scale)
                                    presetTick++
                                    Toast.makeText(context, context.getString(R.string.gy_light_preset_saved, scale), Toast.LENGTH_SHORT).show()
                                },
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(text = stringResource(names[i]), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        PreferenceGroup(
            heading = stringResource(R.string.gy_light_schedule),
            description = stringResource(R.string.gy_light_schedule_hint),
        ) {
            val presetNames = listOf(R.string.gy_preset_day, R.string.gy_preset_read, R.string.gy_preset_night).map { stringResource(it) }
            fun save(list: List<LightStore.Schedule>) {
                schedules = list
                store.schedules = list
                LightScheduler.reschedule(context)
            }
            schedules.forEachIndexed { index, s ->
                PreferenceTemplate(
                    title = { Text(text = "%02d:%02d  →  %s".format(s.hour, s.minute, presetNames[s.preset])) },
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> save(schedules.toMutableList().also { it[index] = s.copy(hour = h, minute = m) }) }, s.hour, s.minute, true).show()
                        },
                        onLongClick = {
                            AlertDialog.Builder(context)
                                .setItems(presetNames.toTypedArray()) { _, which -> save(schedules.toMutableList().also { it[index] = s.copy(preset = which) }) }
                                .show()
                        },
                    ),
                    description = { Text(text = stringResource(R.string.gy_light_schedule_row_hint)) },
                    endWidget = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .combinedClickable(onClick = { save(schedules.toMutableList().also { it.removeAt(index) }) }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(imageVector = Icons.Rounded.Delete, contentDescription = stringResource(R.string.gy_light_delete))
                            }
                            Switch(
                                checked = s.enabled,
                                onCheckedChange = { on -> save(schedules.toMutableList().also { it[index] = s.copy(enabled = on) }) },
                            )
                        }
                    },
                )
            }
            if (schedules.size < LightScheduler.maxSlots()) {
                ClickablePreference(
                    label = stringResource(R.string.gy_light_schedule_add),
                    onClick = {
                        TimePickerDialog(context, { _, h, m -> save(schedules + LightStore.Schedule(h, m, 2, true)) }, 22, 0, true).show()
                    },
                )
            }
        }

        PreferenceGroup(heading = stringResource(R.string.gy_light_more)) {
            ClickablePreference(
                label = stringResource(R.string.gy_perm_center),
                subtitle = stringResource(R.string.gy_perm_center_sub),
                onClick = { navController.navigate(GuangyingPermissions) },
            )
            PreferenceTemplate(
                title = { Text(text = stringResource(R.string.gy_light_tile_title)) },
                description = { Text(text = stringResource(R.string.gy_light_tile_hint)) },
            )
        }
    }
}

/** 滑块两侧的 − / +：点一下 ±1，长按 ±25 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepButton(enabled: Boolean, label: String, onStep: () -> Unit, onLongStep: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .combinedClickable(enabled = enabled, onClick = onStep, onLongClick = onLongStep),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (label == "+") Icons.Rounded.Add else Icons.Rounded.Remove,
            contentDescription = label,
            modifier = Modifier.width(24.dp),
        )
    }
}
