package app.lawnchair.guangying

import android.content.Context
import android.content.Intent
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.GuangyingLight
import app.lawnchair.ui.preferences.navigation.GuangyingPanel
import app.lawnchair.ui.preferences.navigation.GuangyingPermissions

/** 光影桌面新增功能的入口。从桌面设置页、快捷设置开关、手势都从这里打开。 */
object GuangyingEntry {
    fun openLightIntent(context: Context): Intent = PreferenceActivity.createIntent(context, GuangyingLight)

    fun openPanelSettingsIntent(context: Context): Intent = PreferenceActivity.createIntent(context, GuangyingPanel)

    fun openPermissionsIntent(context: Context): Intent = PreferenceActivity.createIntent(context, GuangyingPermissions)
}
