package com.xiyunmn.puredupan.hook.feature.baidu.cn.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeHookPoints
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.symbols.baidu.cn.BaiduCnHookPoints

object SystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "SystemNightModeSyncHook",
        hookPoints = BaiduSystemNightModeHookPoints(
            baseActivityClassName = BaiduCnHookPoints.BASE_ACTIVITY,
            settingsActivityClassName = BaiduCnHookPoints.SETTINGS_ACTIVITY,
            changeSkinKtClassName = BaiduCnHookPoints.CHANGE_SKIN_KT,
            changeSkinKtFallbackClassNames = listOf(
                BaiduCnHookPoints.CHANGE_SKIN_KT_13_27_8_RUNTIME,
                BaiduCnHookPoints.CHANGE_SKIN_KT_13_27_8,
            ),
            skinManagerClassName = BaiduCnHookPoints.SKIN_MANAGER,
            skinLoaderListenerClassName = BaiduCnHookPoints.SKIN_LOADER_LISTENER,
            settingsItemViewClassName = BaiduCnHookPoints.SETTINGS_ITEM_VIEW,
        ),
    )

    internal fun hook(cl: ClassLoader) {
        delegate.hook(cl)
    }
}
