package app.lawnchair.guangying.panel

import android.content.Context
import android.content.SharedPreferences

/** 侧边控制面板的设置：是否开启、把手在哪一侧。 */
class PanelPrefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("guangying_panel", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    /** true：把手在屏幕右边；false：左边 */
    var rightSide: Boolean
        get() = sp.getBoolean("right", true)
        set(value) = sp.edit().putBoolean("right", value).apply()
}
