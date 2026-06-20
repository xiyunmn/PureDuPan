package com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeHookPoints
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints

object SamsungSystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "SamsungSystemNightModeSyncHook",
        hookPoints = BaiduSystemNightModeHookPoints(
            baseActivityClassName = BaiduSamsungHookPoints.BASE_ACTIVITY,
            settingsActivityClassName = BaiduSamsungHookPoints.SETTINGS_ACTIVITY,
            changeSkinKtClassName = BaiduSamsungHookPoints.CHANGE_SKIN_KT,
            skinLoaderListenerClassName = BaiduSamsungHookPoints.SKIN_LOADER_LISTENER,
            settingsItemViewClassName = BaiduSamsungHookPoints.SETTINGS_ITEM_VIEW,
        ),
    )

    internal fun hook(cl: ClassLoader) {
        delegate.hook(cl)
    }
}
