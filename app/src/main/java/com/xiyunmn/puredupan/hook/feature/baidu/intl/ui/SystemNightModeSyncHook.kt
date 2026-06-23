package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeHookPoints
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BaiduSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints

object SystemNightModeSyncHook {
    private val delegate = BaiduSystemNightModeSyncHook(
        logTag = "IntlSystemNightModeSyncHook",
        hookPoints = BaiduSystemNightModeHookPoints(
            baseActivityClassName = BaiduIntlHookPoints.BASE_ACTIVITY,
            settingsActivityClassName = BaiduIntlHookPoints.SETTINGS_ACTIVITY,
            skinLoaderListenerClassName = BaiduIntlHookPoints.SKIN_LOADER_LISTENER,
            settingsItemViewClassName = BaiduIntlHookPoints.SETTINGS_ITEM_VIEW,
            skinManagerClassName = BaiduIntlHookPoints.SKIN_MANAGER,
            darkSkinTheme = BaiduIntlHookPoints.DARK_SKIN_THEME,
            changeSkinMethodResolver = IntlChangeSkinDexKitResolver::resolve,
            settingsSwitchViewIdName = BaiduIntlHookPoints.DARK_SETTINGS_ID_NAME,
            beforeApplyDarkSkin = IntlNightModeSkinAssetInstaller::ensureDarkSkinAvailable,
        ),
    )

    internal fun hook(cl: ClassLoader) {
        delegate.hook(cl)
    }
}
