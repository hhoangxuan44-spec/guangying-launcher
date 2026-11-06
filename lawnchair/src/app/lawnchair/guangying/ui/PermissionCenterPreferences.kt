package app.lawnchair.guangying.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lawnchair.guangying.permission.PermissionCenter
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R
import kotlinx.coroutines.delay

/** 权限中心：逐项显示状态；缺失时给出获取方式。 */
@Composable
fun PermissionCenterPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(PermissionCenter.items(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            items = PermissionCenter.items(context)
        }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        items = PermissionCenter.items(context)
    }

    fun launch(kind: PermissionCenter.Kind) {
        if (kind == PermissionCenter.Kind.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val intent = PermissionCenter.settingsIntent(context, kind) ?: return
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.gy_perm_no_page, Toast.LENGTH_LONG).show()
        }
    }

    PreferenceLayout(
        label = stringResource(R.string.gy_perm_center),
        modifier = modifier,
    ) {
        PreferenceGroup(description = stringResource(R.string.gy_perm_intro)) {
            for (item in items) {
                val status = stringResource(if (item.granted) R.string.gy_perm_granted else R.string.gy_perm_missing)
                val optional = if (item.optional) stringResource(R.string.gy_perm_optional) else ""
                PreferenceTemplate(
                    title = { Text(text = stringResource(item.title)) },
                    modifier = if (!item.granted && item.adbCommand == null) Modifier.clickable { launch(item.kind) } else Modifier,
                    description = {
                        Column {
                            Text(text = "$status$optional · ${stringResource(item.purpose)}")
                            if (!item.granted && item.adbCommand != null) {
                                Text(
                                    text = item.adbCommand,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                OutlinedButton(onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("adb", item.adbCommand))
                                    Toast.makeText(context, R.string.gy_perm_copied, Toast.LENGTH_SHORT).show()
                                }) { Text(text = stringResource(R.string.gy_perm_copy)) }
                            }
                            if (!item.granted && item.kind == PermissionCenter.Kind.ACCESSIBILITY) {
                                OutlinedButton(onClick = { context.startActivity(PermissionCenter.appInfoIntent(context)) }) {
                                    Text(text = stringResource(R.string.gy_perm_restricted))
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
