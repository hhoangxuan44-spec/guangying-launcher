package app.lawnchair.guangying.panel

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.PopupWindow

/** 在桌面里用手势打开控制面板：作为桌面窗口的弹层显示，不需要悬浮窗权限。 */
object PanelPopup {
    fun show(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val panel = PanelView(activity)
        val density = activity.resources.displayMetrics
        val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 340f, density).toInt()
            .coerceAtMost(density.widthPixels - 24)
        val popup = PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.animationStyle = android.R.style.Animation_Dialog
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, density).toInt()
        val right = PanelPrefs(activity).rightSide
        popup.showAtLocation(activity.window.decorView, (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL, margin, 0)
    }
}
