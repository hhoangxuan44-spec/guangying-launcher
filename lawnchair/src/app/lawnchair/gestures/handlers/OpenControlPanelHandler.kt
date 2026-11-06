package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.guangying.panel.PanelPopup

/** 光影桌面新增的手势动作：打开控制面板（亮度 / 极暗 / 音量 / 开关）。真正的内容在 app.lawnchair.guangying.panel。 */
class OpenControlPanelHandler(context: Context) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        launcher.runOnUiThread { PanelPopup.show(launcher) }
    }
}
