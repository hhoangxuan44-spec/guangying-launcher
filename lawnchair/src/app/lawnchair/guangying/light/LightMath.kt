package app.lawnchair.guangying.light

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 光控刻度：把「屏幕亮度 + 系统极暗」合成一个 0–1000 的连续刻度。
 *
 *  300–1000（亮）  关闭自适应亮度，按感知曲线（gamma 2.2）映射到 screen_brightness 1–255，极暗关闭
 *  0–300（暗）     screen_brightness 固定为 1，极暗强度从 0 平滑升到 100
 * 两段在 300 处衔接：亮度正好是 1、极暗强度正好是 0，拖动时亮度连续。
 */
object LightMath {
    const val MAX = 1000
    const val SPLIT = 300
    const val GAMMA = 2.2

    fun clamp(scale: Int): Int = scale.coerceIn(0, MAX)

    /** 刻度 → 系统亮度 1–255 */
    fun brightnessFor(scale: Int): Int {
        val s = clamp(scale)
        if (s <= SPLIT) return 1
        val t = (s - SPLIT) / (MAX - SPLIT).toDouble()
        return 1 + (254 * t.pow(GAMMA)).roundToInt()
    }

    /** 刻度 → 极暗强度 0–100 */
    fun dimLevelFor(scale: Int): Int {
        val s = clamp(scale)
        return if (s >= SPLIT) 0 else (100.0 * (SPLIT - s) / SPLIT).roundToInt()
    }

    /** 系统亮度 / 极暗状态 → 刻度（反过来读当前状态） */
    fun scaleFor(brightness: Int, dimActive: Boolean, dimLevel: Int): Int {
        if (dimActive && dimLevel > 0) return (SPLIT - dimLevel * SPLIT / 100.0).roundToInt()
        val t = ((brightness - 1).coerceIn(0, 254) / 254.0).pow(1.0 / GAMMA)
        return SPLIT + (t * (MAX - SPLIT)).roundToInt()
    }
}
