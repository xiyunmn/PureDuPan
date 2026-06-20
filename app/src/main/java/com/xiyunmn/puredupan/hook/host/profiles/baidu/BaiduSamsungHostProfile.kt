package com.xiyunmn.puredupan.hook.host.profiles.baidu

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.host.HomeCustomizeHookPoints
import com.xiyunmn.puredupan.hook.host.HostCapabilities
import com.xiyunmn.puredupan.hook.host.HostFeatureCapabilities
import com.xiyunmn.puredupan.hook.host.HostHookCapabilities
import com.xiyunmn.puredupan.hook.host.HostIds
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.host.HostProfile
import com.xiyunmn.puredupan.hook.host.HostSettingsCapabilities
import com.xiyunmn.puredupan.hook.host.HostUiHookPoints
import com.xiyunmn.puredupan.hook.host.features.baidu.BaiduFeatureSets
import com.xiyunmn.puredupan.hook.symbols.baidu.samsung.BaiduSamsungHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSharedHookPoints

internal val BaiduSamsungHostProfile = HostProfile(
    id = HostIds.BAIDU_SAMSUNG,
    packageName = HostPackages.BAIDU_SAMSUNG,
    handledProcessNames = setOf(
        HostPackages.BAIDU_SAMSUNG,
        "${HostPackages.BAIDU_SAMSUNG}:pushservice",
    ),
    attachHookProcessNames = setOf(
        HostPackages.BAIDU_SAMSUNG,
        "${HostPackages.BAIDU_SAMSUNG}:pushservice",
    ),
    pushServiceProcessNames = setOf(
        "${HostPackages.BAIDU_SAMSUNG}:pushservice",
    ),
    capabilities = HostCapabilities(
        features = HostFeatureCapabilities(
            availableKeys = BaiduFeatureSets.baiduSamsungAvailableKeys,
        ),
        hooks = HostHookCapabilities(
            catalogId = HostIds.BAIDU_SAMSUNG,
            supportsOemPushHook = true,
        ),
        settings = HostSettingsCapabilities(
            primarySplashAdFeatureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
            memberCardLayoutMode = MemberCardLayoutMode.BENEFIT_SLOT,
        ),
        uiHookPoints = HostUiHookPoints(
            mainActivityClassName = BaiduSharedHookPoints.MAIN_ACTIVITY,
            mainActivityPresenterClassName = BaiduSharedHookPoints.MAIN_ACTIVITY_PRESENTER,
            homeActivityClassName = BaiduSharedHookPoints.HOME_ACTIVITY,
            aboutMeActivityClassName = BaiduSharedHookPoints.ABOUT_ME_ACTIVITY,
            newAboutMeActivityClassName = BaiduSharedHookPoints.NEW_ABOUT_ME_ACTIVITY,
            newHomeFabFragmentClassName = BaiduSharedHookPoints.NEW_HOME_FAB_FRAGMENT,
            popupResponseClassName = BaiduSharedHookPoints.POPUP_RESPONSE,
            skinConfigClassName = BaiduSharedHookPoints.SKIN_CONFIG_CLASS,
            settingsImageResultHostActivityClassNames = listOf(
                BaiduSharedHookPoints.ABOUT_ME_ACTIVITY,
                BaiduSharedHookPoints.NEW_ABOUT_ME_ACTIVITY,
                BaiduSharedHookPoints.HOME_ACTIVITY,
                BaiduSharedHookPoints.MAIN_ACTIVITY,
            ),
            homeCustomize = HomeCustomizeHookPoints(
                feedFragmentClassNames = BaiduSamsungHookPoints.FEED_FRAGMENT_CLASSES,
                storyCardViewClassNames = listOf(BaiduSamsungHookPoints.HOME_STORY_CARD_VIEW),
                saveCardViewClassNames = BaiduSamsungHookPoints.HOME_SAVE_CARD_VIEWS,
                recentCardViewClassNames = BaiduSamsungHookPoints.HOME_RECENT_CARD_VIEWS,
            ),
        ),
    ),
)
