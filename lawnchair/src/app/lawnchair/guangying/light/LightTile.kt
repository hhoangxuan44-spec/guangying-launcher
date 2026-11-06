package app.lawnchair.guangying.light

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.lawnchair.guangying.GuangyingEntry
import com.android.launcher3.R

/** 快捷设置开关「光控」：点一下在夜间预设和上次的日间值之间切换；长按打开光控页面。 */
class LightTile : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val c = LightController(this)
        if (!c.canWriteSettings) {          // 还没授权：打开光控去授权
            open()
            return
        }
        c.toggleNight()
        refresh()
    }

    private fun open() {
        val i = GuangyingEntry.openLightIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }

    private fun refresh() {
        val t = qsTile ?: return
        val c = LightController(this)
        val ok = c.canWriteSettings
        val night = ok && c.isNight()
        t.state = if (night) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = getString(R.string.gy_tile_label)
        t.icon = Icon.createWithResource(this, R.drawable.ic_guangying_light)
        t.subtitle = if (!ok) getString(R.string.gy_tile_need_auth) else if (night) getString(R.string.gy_tile_night) else getString(R.string.gy_tile_day)
        t.updateTile()
    }
}
