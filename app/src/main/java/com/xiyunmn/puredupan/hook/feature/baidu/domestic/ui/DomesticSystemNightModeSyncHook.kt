package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.Home25ThemeHookPoints

internal object DomesticSystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "DomesticSystemNightModeSyncHook",
        hookPoints = Home25ThemeHookPoints.create(),
    )

    internal fun hook(cl: ClassLoader) = delegate.hook(cl)
}
