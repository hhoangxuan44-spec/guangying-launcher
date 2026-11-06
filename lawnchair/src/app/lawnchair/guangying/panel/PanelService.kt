package app.lawnchair.guangying.panel

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import app.lawnchair.guangying.GuangyingEntry
import com.android.launcher3.R

/**
 * 侧边控制面板的前台服务：在屏幕边缘放一条细把手，从边缘往里划（或点一下）滑出控制面板。
 * 需要「显示在其他应用上层」权限；没有这个权限时服务不会添加任何窗口，也不会崩溃。
 */
class PanelService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var overlay: FrameLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (!PanelPrefs(this).enabled || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        addHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        removeHandle()
        super.onDestroy()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.gy_panel_channel), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, GuangyingEntry.openPanelSettingsIntent(this), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_guangying_light)
            .setContentTitle(getString(R.string.gy_panel_running))
            .setContentText(getString(R.string.gy_panel_running_hint))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "启动前台服务失败", e)
        }
    }

    // ------------------------------------------------------------ 把手

    private fun addHandle() {
        removeHandle()
        val right = PanelPrefs(this).rightSide
        val v = View(this)
        v.background = GradientDrawable().apply {
            cornerRadius = dp(3f).toFloat()
            setColor(0x66FFFFFF)
            setStroke(1, 0x33000000)
        }
        // 触摸区域比看得见的细条宽，方便从边缘划进来
        val box = FrameLayout(this)
        box.addView(v, FrameLayout.LayoutParams(dp(4f), FrameLayout.LayoutParams.MATCH_PARENT, (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply {
            topMargin = dp(18f)
            bottomMargin = dp(18f)
            if (right) marginEnd = dp(3f) else marginStart = dp(3f)
        })
        var startX = 0f
        var opened = false
        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = e.rawX
                    opened = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val inward = if (right) startX - e.rawX else e.rawX - startX
                    if (!opened && inward > dp(18f)) {
                        opened = true
                        showPanel()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!opened) showPanel()          // 点一下也能打开
                    true
                }
                else -> false
            }
        }
        val lp = WindowManager.LayoutParams(
            dp(22f), dp(160f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        try {
            wm.addView(box, lp)
            handle = box
            // 手势导航的返回手势占着屏幕边缘：请求系统别抢这块区域（能不能生效取决于系统）
            box.post {
                if (Build.VERSION.SDK_INT >= 29) runCatching { box.systemGestureExclusionRects = listOf(Rect(0, 0, box.width, box.height)) }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "添加把手失败", e)
        }
    }

    private fun removeHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        handle = null
    }

    // ------------------------------------------------------------ 面板

    private fun showPanel() {
        if (overlay != null) return
        val right = PanelPrefs(this).rightSide
        val root = FrameLayout(this)
        root.setBackgroundColor(0x33000000)
        root.setOnClickListener { hidePanel() }
        val panel = PanelView(this)
        val width = minOf(dp(340f), resources.displayMetrics.widthPixels - dp(24f))
        root.addView(panel, FrameLayout.LayoutParams(width, FrameLayout.LayoutParams.WRAP_CONTENT, (if (right) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL).apply {
            if (right) marginEnd = dp(12f) else marginStart = dp(12f)
        })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        if (Build.VERSION.SDK_INT >= 31) {
            // 背景虚化（系统不支持就忽略）
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = 24
        }
        try {
            wm.addView(root, lp)
            overlay = root
            panel.alpha = 0f
            panel.post {
                val from = (if (right) 1 else -1) * (width + dp(24f)).toFloat()
                panel.translationX = from
                panel.alpha = 1f
                ObjectAnimator.ofFloat(panel, View.TRANSLATION_X, from, 0f).apply {
                    duration = 200
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "显示面板失败", e)
        }
    }

    private fun hidePanel() {
        val root = overlay ?: return
        val panel = root.getChildAt(0)
        val right = PanelPrefs(this).rightSide
        val to = (if (right) 1 else -1) * (panel.width + dp(24f)).toFloat()
        ObjectAnimator.ofFloat(panel, View.TRANSLATION_X, 0f, to).apply {
            duration = 200
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = removeOverlay()
            })
            start()
        }
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { wm.removeView(it) } }
        overlay = null
    }

    companion object {
        private const val TAG = "GuangyingPanel"
        private const val CHANNEL = "guangying_panel"
        private const val NOTIFICATION_ID = 7301

        fun start(context: Context) {
            if (!PanelPrefs(context).enabled || !Settings.canDrawOverlays(context)) return
            runCatching { context.startForegroundService(Intent(context, PanelService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PanelService::class.java))
        }
    }
}

/** 开机或升级后，如果面板是开启的，就重新启动服务。 */
class PanelBootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PanelService.start(context)
    }
}
