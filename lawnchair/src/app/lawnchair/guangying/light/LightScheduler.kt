package app.lawnchair.guangying.light

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** 每天几点切到哪个预设：用 AlarmManager 登记，开机、改时间、升级后重新登记。 */
object LightScheduler {
    const val ACTION_ALARM = "app.lawnchair.guangying.LIGHT_ALARM"
    private const val EXTRA_PRESET = "preset"
    private const val MAX_SLOTS = 12

    private fun pending(context: Context, slot: Int, preset: Int): PendingIntent {
        val i = Intent(context, LightReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_PRESET, preset)
        return PendingIntent.getBroadcast(context, 100 + slot, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    /** 按当前保存的定时重新登记全部闹钟 */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java) ?: return
        val list = LightStore(app).schedules
        for (slot in 0 until MAX_SLOTS) {
            val s = list.getOrNull(slot)
            if (s == null || !s.enabled) {
                am.cancel(pending(app, slot, 0))
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(s.hour, s.minute), pending(app, slot, s.preset))
            }
        }
    }

    fun maxSlots() = MAX_SLOTS

    internal fun presetOf(intent: Intent): Int = intent.getIntExtra(EXTRA_PRESET, 0)
}

class LightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            LightScheduler.ACTION_ALARM -> {
                LightController(context).applyPreset(LightScheduler.presetOf(intent).coerceIn(0, 2))
                LightScheduler.reschedule(context)      // 排下一天
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> LightScheduler.reschedule(context)
        }
    }
}
