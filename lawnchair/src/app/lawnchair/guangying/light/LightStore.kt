package app.lawnchair.guangying.light

import android.content.Context
import android.content.SharedPreferences

/** 光控的预设、「上次的日间值」和定时。 */
class LightStore(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("guangying_light", Context.MODE_PRIVATE)

    fun preset(index: Int): Int = sp.getInt("preset_$index", PRESET_DEFAULT[index])
    fun setPreset(index: Int, value: Int) = sp.edit().putInt("preset_$index", LightMath.clamp(value)).apply()

    var lastDay: Int
        get() = sp.getInt("last_day", preset(0))
        set(value) = sp.edit().putInt("last_day", LightMath.clamp(value)).apply()

    /** 定时：每天 hour:minute 切到某个预设 */
    data class Schedule(val hour: Int, val minute: Int, val preset: Int, val enabled: Boolean) {
        fun encode() = "$hour,$minute,$preset,${if (enabled) 1 else 0}"

        companion object {
            fun decode(s: String): Schedule? {
                val p = s.split(',')
                if (p.size != 4) return null
                return runCatching {
                    Schedule(p[0].toInt().coerceIn(0, 23), p[1].toInt().coerceIn(0, 59), p[2].toInt().coerceIn(0, 2), p[3] == "1")
                }.getOrNull()
            }
        }
    }

    var schedules: List<Schedule>
        get() = (sp.getString("schedules", "") ?: "").split('|').mapNotNull { if (it.isBlank()) null else Schedule.decode(it) }
        set(value) = sp.edit().putString("schedules", value.joinToString("|") { it.encode() }).apply()

    companion object {
        val PRESET_DEFAULT = intArrayOf(850, 560, 120)
    }
}
