package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduBottomBarHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduThemeHookPoints

internal object Home25ThemeHookPoints {
    fun create(beforeApplyDarkSkin: ((Activity) -> Boolean)? = null) = BaiduSystemNightModeHookPoints(
        baseActivityClassName = BaiduThemeHookPoints.BASE_ACTIVITY,
        settingsActivityClassName = BaiduThemeHookPoints.SETTINGS_ACTIVITY,
        changeSkinKtClassName = BaiduThemeHookPoints.CHANGE_SKIN_KT,
        skinManagerClassName = BaiduThemeHookPoints.SKIN_MANAGER,
        skinConfigClassName = BaiduThemeHookPoints.SKIN_CONFIG,
        skinLoaderListenerClassName = BaiduThemeHookPoints.SKIN_LOADER_LISTENER,
        settingsItemViewClassName = BaiduThemeHookPoints.SETTINGS_ITEM_VIEW,
        changeSkinMethodResolver = BaiduChangeSkinDexKitResolver::resolve,
        beforeApplyDarkSkin = beforeApplyDarkSkin,
        bottomBarHomeFoldedFieldNames = BaiduBottomBarHookPoints.HOME25_FOLDED_FIELDS,
        bottomBarThemeRefreshMethodNames = listOf(BaiduBottomBarHookPoints.INIT_TABS_SKIN_METHOD),
        bottomBarThemeRefreshCompletionMethodName = BaiduBottomBarHookPoints.REFRESH_TAB_VIEW_TEXT_METHOD,
        gateColdStartBottomBar = true,
    )
}
