package app.lawnchair.guangying.light

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/** 把光控刻度写进系统设置。没有权限时什么都不做、不抛异常。 */
class LightController(context: Context) {
    private val context = context.applicationContext
    private val store = LightStore(context)

    val canWriteSettings: Boolean get() = Settings.System.canWrite(context)
    val canWriteSecure: Boolean
        get() = context.checkSelfPermission(SECURE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** 读当前刻度（读系统设置不需要权限） */
    fun read(): Int = runCatching {
        val r = context.contentResolver
        val b = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128)
        val on = Settings.Secure.getInt(r, KEY_ACTIVE, 0) == 1
        val lv = Settings.Secure.getInt(r, KEY_LEVEL, 0)
        LightMath.scaleFor(b, on, lv)
    }.getOrDefault(700)

    /**
     * 应用刻度。没有 WRITE_SETTINGS 返回 false；没有 WRITE_SECURE_SETTINGS 时只改亮度、不碰极暗。
     */
    fun apply(scale: Int): Boolean {
        val s = LightMath.clamp(scale)
        if (!canWriteSettings) return false
        return try {
            val r = context.contentResolver
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, LightMath.brightnessFor(s))
            if (canWriteSecure) {
                val lv = LightMath.dimLevelFor(s)
                if (lv > 0) {
                    Settings.Secure.putInt(r, KEY_LEVEL, lv)
                    Settings.Secure.putInt(r, KEY_ACTIVE, 1)
                } else if (Settings.Secure.getInt(r, KEY_ACTIVE, 0) == 1) {
                    Settings.Secure.putInt(r, KEY_ACTIVE, 0)
                }
            }
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "写入亮度失败", e)
            false
        }
    }

    fun applyPreset(index: Int): Boolean = apply(store.preset(index))

    /** 夜间预设 ⇄ 上次的日间值。返回切换后的刻度，没有权限返回 -1。 */
    fun toggleNight(): Int {
        val night = store.preset(2)
        val cur = read()
        val target = if (cur <= night + 30) {
            store.lastDay
        } else {
            store.lastDay = cur
            night
        }
        return if (apply(target)) target else -1
    }

    fun isNight(): Boolean = read() <= store.preset(2) + 30

    companion object {
        const val SECURE_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
        private const val KEY_ACTIVE = "reduce_bright_colors_activated"
        private const val KEY_LEVEL = "reduce_bright_colors_level"
        private const val TAG = "GuangyingLight"
    }
}
