package app.lawnchair.guangying.permission

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.android.launcher3.R

/** 权限中心：逐项给出状态和获取方式。任何权限缺失时，相关功能都变灰并说明原因，不崩溃。 */
object PermissionCenter {
    enum class Kind { WRITE_SETTINGS, WRITE_SECURE_SETTINGS, OVERLAY, ACCESSIBILITY, NOTIFICATIONS }

    data class Item(
        val kind: Kind,
        @StringRes val title: Int,
        @StringRes val purpose: Int,
        val granted: Boolean,
        /** 需要用电脑 adb 授权的命令；其余权限为 null */
        val adbCommand: String? = null,
        val optional: Boolean = false,
    )

    const val SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    fun adbGrantCommand(context: Context) = "adb shell pm grant ${context.packageName} $SECURE"

    fun has(context: Context, kind: Kind): Boolean = when (kind) {
        Kind.WRITE_SETTINGS -> Settings.System.canWrite(context)
        Kind.WRITE_SECURE_SETTINGS -> context.checkSelfPermission(SECURE) == PackageManager.PERMISSION_GRANTED
        Kind.OVERLAY -> Settings.canDrawOverlays(context)
        Kind.ACCESSIBILITY -> accessibilityEnabled(context)
        Kind.NOTIFICATIONS ->
            Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun items(context: Context): List<Item> = listOf(
        Item(Kind.WRITE_SETTINGS, R.string.gy_perm_write_settings, R.string.gy_perm_write_settings_why, has(context, Kind.WRITE_SETTINGS)),
        Item(
            Kind.WRITE_SECURE_SETTINGS,
            R.string.gy_perm_secure,
            R.string.gy_perm_secure_why,
            has(context, Kind.WRITE_SECURE_SETTINGS),
            adbCommand = adbGrantCommand(context),
        ),
        Item(Kind.OVERLAY, R.string.gy_perm_overlay, R.string.gy_perm_overlay_why, has(context, Kind.OVERLAY)),
        Item(Kind.NOTIFICATIONS, R.string.gy_perm_notifications, R.string.gy_perm_notifications_why, has(context, Kind.NOTIFICATIONS)),
        Item(Kind.ACCESSIBILITY, R.string.gy_perm_accessibility, R.string.gy_perm_accessibility_why, has(context, Kind.ACCESSIBILITY), optional = true),
    )

    /** 跳转到能授权的系统页面（需要 adb 的那项返回 null，由界面显示命令） */
    fun settingsIntent(context: Context, kind: Kind): Intent? = when (kind) {
        Kind.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
        Kind.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        Kind.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Kind.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        Kind.WRITE_SECURE_SETTINGS -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 应用信息页：自己安装的 APK 要先在这里「允许受限设置」，无障碍才能打开 */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun accessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
    }
}
