package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.Home25ThemeHookPoints

internal object SystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "IntlSystemNightModeSyncHook",
        hookPoints = Home25ThemeHookPoints.create(
            beforeApplyDarkSkin = IntlNightModeSkinAssetInstaller::ensureDarkSkinAvailable,
        ),
    )

    internal fun hook(cl: ClassLoader) {
        IntlChainInfoThemeCompat.hook(cl)
        delegate.hook(cl)
    }
}
