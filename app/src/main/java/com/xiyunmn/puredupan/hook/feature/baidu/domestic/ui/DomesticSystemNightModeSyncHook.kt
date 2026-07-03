package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeHookPoints
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints

internal object DomesticSystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "DomesticSystemNightModeSyncHook",
        hookPoints = BaiduSystemNightModeHookPoints(
            baseActivityClassName = BaiduDomesticHookPoints.BASE_ACTIVITY,
            settingsActivityClassName = BaiduDomesticHookPoints.SETTINGS_ACTIVITY,
            changeSkinKtClassName = BaiduDomesticHookPoints.CHANGE_SKIN_KT,
            skinManagerClassName = BaiduDomesticHookPoints.SKIN_MANAGER,
            skinLoaderListenerClassName = BaiduDomesticHookPoints.SKIN_LOADER_LISTENER,
            settingsItemViewClassName = BaiduDomesticHookPoints.SETTINGS_ITEM_VIEW,
            changeSkinMethodResolver = DomesticChangeSkinDexKitResolver::resolve,
            allowSkinManagerApplyFallback = false,
        ),
    )

    internal fun hook(cl: ClassLoader) {
        delegate.hook(cl)
    }
}
