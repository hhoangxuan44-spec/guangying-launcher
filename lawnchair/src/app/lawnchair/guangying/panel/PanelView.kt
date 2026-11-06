package app.lawnchair.guangying.panel

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import app.lawnchair.guangying.light.LightController
import app.lawnchair.guangying.light.LightMath
import app.lawnchair.guangying.light.LightStore
import com.android.launcher3.R

/**
 * 控制面板：亮度滑块（含极暗）、三个预设、音量滑块、极暗 / 勿扰 / 自动旋转三个开关。
 * 用普通 View 写（不用 Compose），这样悬浮窗服务和桌面里的弹窗都能直接用。
 * 缺权限时对应控件变灰，点一下说明原因或跳转授权，不会崩溃。
 */
class PanelView(context: Context) : LinearLayout(context) {
    private val light = LightController(context)
    private val store = LightStore(context)
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)
    private val nm: NotificationManager? = context.getSystemService(NotificationManager::class.java)

    private val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val panelBg: Int
    private val fg: Int
    private val sub: Int
    private val accent: Int
    private val chipBg: Int

    private val brightValue: TextView
    private val brightBar: SeekBar
    private val brightHint: TextView
    private val volumeBar: SeekBar
    private val volumeValue: TextView
    private val dimChip: TextView
    private val dndChip: TextView
    private val rotateChip: TextView
    private var quiet = false

    private fun sys(id: Int, fallback: Int): Int = if (Build.VERSION.SDK_INT >= 31) context.getColor(id) else fallback

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    init {
        orientation = VERTICAL
        // 跟随系统深浅色和莫奈取色（Android 12 以上），半透明
        panelBg = if (dark) sys(android.R.color.system_neutral1_900, 0xFF1B1B1F.toInt()) else sys(android.R.color.system_neutral1_50, 0xFFF4F4F8.toInt())
        fg = if (dark) sys(android.R.color.system_neutral1_50, Color.WHITE) else sys(android.R.color.system_neutral1_900, 0xFF1B1B1F.toInt())
        sub = if (dark) sys(android.R.color.system_neutral2_300, 0xFFC4C6D0.toInt()) else sys(android.R.color.system_neutral2_600, 0xFF5B5E67.toInt())
        accent = if (dark) sys(android.R.color.system_accent1_200, 0xFFAEC6FF.toInt()) else sys(android.R.color.system_accent1_600, 0xFF3F5F90.toInt())
        chipBg = if (dark) sys(android.R.color.system_neutral1_700, 0xFF3A3B40.toInt()) else sys(android.R.color.system_neutral1_200, 0xFFDDDEE4.toInt())

        val bg = GradientDrawable()
        bg.cornerRadius = dp(28f).toFloat()
        bg.setColor((panelBg and 0x00FFFFFF) or (0xE6 shl 24))
        background = bg
        setPadding(dp(20f), dp(18f), dp(20f), dp(18f))
        elevation = dp(12f).toFloat()

        addView(label(context.getString(R.string.gy_panel_title), 18f, fg, bold = true))

        // 亮度
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(label(context.getString(R.string.gy_panel_brightness), 14f, sub), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        brightValue = label("", 14f, fg)
        row.addView(brightValue)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14f) })
        brightBar = seek(LightMath.MAX)
        brightBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (quiet || !fromUser) return
                var v = p
                if (!light.canWriteSecure && v < LightMath.SPLIT) {
                    v = LightMath.SPLIT
                    quiet = true
                    s.progress = v
                    quiet = false
                }
                light.apply(v)
                brightValue.text = v.toString()
            }

            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
        addView(brightBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40f)))
        brightHint = label("", 12f, sub)
        addView(brightHint)

        // 预设
        val presets = LinearLayout(context)
        val names = intArrayOf(R.string.gy_preset_day, R.string.gy_preset_read, R.string.gy_preset_night)
        for (i in 0..2) {
            val c = chip(context.getString(names[i]))
            c.setOnClickListener {
                if (!light.canWriteSettings) {
                    toast(R.string.gy_light_need_write)
                } else {
                    val v = store.preset(i)
                    light.apply(if (!light.canWriteSecure && v < LightMath.SPLIT) LightMath.SPLIT else v)
                    refresh()
                }
            }
            presets.addView(c, LayoutParams(0, dp(40f), 1f).apply { if (i > 0) marginStart = dp(8f) })
        }
        addView(presets, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8f) })

        // 音量
        val vrow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        vrow.addView(label(context.getString(R.string.gy_panel_volume), 14f, sub), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        volumeValue = label("", 14f, fg)
        vrow.addView(volumeValue)
        addView(vrow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18f) })
        volumeBar = seek(audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15)
        volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (quiet || !fromUser) return
                runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0) }
                volumeValue.text = "$p / ${s.max}"
            }

            override fun onStartTrackingTouch(s: SeekBar) = Unit
            override fun onStopTrackingTouch(s: SeekBar) = Unit
        })
        addView(volumeBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(40f)))

        // 开关
        val toggles = LinearLayout(context)
        dimChip = chip(context.getString(R.string.gy_panel_dim))
        dndChip = chip(context.getString(R.string.gy_panel_dnd))
        rotateChip = chip(context.getString(R.string.gy_panel_rotate))
        dimChip.setOnClickListener { toggleDim() }
        dndChip.setOnClickListener { toggleDnd() }
        rotateChip.setOnClickListener { toggleRotate() }
        toggles.addView(dimChip, LayoutParams(0, dp(44f), 1f))
        toggles.addView(dndChip, LayoutParams(0, dp(44f), 1f).apply { marginStart = dp(8f) })
        toggles.addView(rotateChip, LayoutParams(0, dp(44f), 1f).apply { marginStart = dp(8f) })
        addView(toggles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16f) })

        refresh()
    }

    // ------------------------------------------------------------ 状态

    /** 面板每次显示时调用，读取系统当前状态 */
    fun refresh() {
        quiet = true
        val v = light.read()
        brightBar.progress = v
        brightValue.text = v.toString()
        brightBar.isEnabled = light.canWriteSettings
        brightBar.alpha = if (light.canWriteSettings) 1f else 0.38f
        brightHint.text = when {
            !light.canWriteSettings -> context.getString(R.string.gy_panel_need_write)
            !light.canWriteSecure -> context.getString(R.string.gy_panel_need_secure)
            else -> ""
        }
        brightHint.visibility = if (brightHint.text.isEmpty()) View.GONE else View.VISIBLE

        val max = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        val cur = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        volumeBar.max = max
        volumeBar.progress = cur
        volumeValue.text = "$cur / $max"
        quiet = false

        mark(dimChip, dimOn(), light.canWriteSecure)
        mark(dndChip, dndOn(), true)
        mark(rotateChip, rotateOn(), light.canWriteSettings)
    }

    private fun dimOn() = runCatching { Settings.Secure.getInt(context.contentResolver, "reduce_bright_colors_activated", 0) == 1 }.getOrDefault(false)
    private fun dndOn() = (nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) != NotificationManager.INTERRUPTION_FILTER_ALL
    private fun rotateOn() = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1 }.getOrDefault(true)

    private fun toggleDim() {
        if (!light.canWriteSecure) {
            toast(R.string.gy_panel_dim_need)
            return
        }
        runCatching {
            val r = context.contentResolver
            val on = dimOn()
            if (!on && Settings.Secure.getInt(r, "reduce_bright_colors_level", 0) <= 0) Settings.Secure.putInt(r, "reduce_bright_colors_level", 50)
            Settings.Secure.putInt(r, "reduce_bright_colors_activated", if (on) 0 else 1)
        }
        refresh()
    }

    private fun toggleDnd() {
        val m = nm ?: return
        if (!m.isNotificationPolicyAccessGranted) {
            toast(R.string.gy_panel_dnd_need)
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        runCatching {
            m.setInterruptionFilter(
                if (dndOn()) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            )
        }
        postDelayed({ refresh() }, 200)
    }

    private fun toggleRotate() {
        if (!light.canWriteSettings) {
            toast(R.string.gy_panel_need_write)
            return
        }
        runCatching { Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotateOn()) 0 else 1) }
        refresh()
    }

    // ------------------------------------------------------------ 小工具

    private fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun seek(max: Int): SeekBar = SeekBar(context).apply {
        this.max = max
        progressTintList = ColorStateList.valueOf(accent)
        thumbTintList = ColorStateList.valueOf(accent)
        progressBackgroundTintList = ColorStateList.valueOf(chipBg)
        setPadding(dp(8f), 0, dp(8f), 0)
    }

    private fun chip(text: String): TextView = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(fg)
        background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(chipBg)
        }
    }

    /** 开关亮起 / 熄灭；没有权限时变灰 */
    private fun mark(chip: TextView, on: Boolean, enabled: Boolean) {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(14f).toFloat()
        bg.setColor(if (on && enabled) accent else chipBg)
        chip.background = bg
        chip.setTextColor(if (on && enabled) (if (dark) 0xFF10131A.toInt() else Color.WHITE) else fg)
        chip.alpha = if (enabled) 1f else 0.4f
    }
}
