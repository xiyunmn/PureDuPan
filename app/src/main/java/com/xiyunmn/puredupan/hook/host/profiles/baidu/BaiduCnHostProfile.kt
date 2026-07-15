package com.xiyunmn.puredupan.hook.host.profiles.baidu

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.host.HomeCustomizeHookPoints
import com.xiyunmn.puredupan.hook.host.HostCapabilities
import com.xiyunmn.puredupan.hook.host.HostDexKitCapabilities
import com.xiyunmn.puredupan.hook.host.HostDiagnosticsCapabilities
import com.xiyunmn.puredupan.hook.host.HostFeatureCapabilities
import com.xiyunmn.puredupan.hook.host.HostHookCapabilities
import com.xiyunmn.puredupan.hook.host.HostIds
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.host.HostProfile
import com.xiyunmn.puredupan.hook.host.HostSettingsCapabilities
import com.xiyunmn.puredupan.hook.host.HostStartupHookPoints
import com.xiyunmn.puredupan.hook.host.HostUiHookPoints
import com.xiyunmn.puredupan.hook.host.features.baidu.BaiduFeatureSets
import com.xiyunmn.puredupan.hook.host.runtime.baidu.BaiduDomesticDeviceFingerprintRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSharedHookPoints

internal val BaiduCnHostProfile = HostProfile(
    id = HostIds.BAIDU_CN,
    packageName = HostPackages.BAIDU_CN,
    handledProcessNames = setOf(
        HostPackages.BAIDU_CN,
        "${HostPackages.BAIDU_CN}:pushservice",
    ),
    attachHookProcessNames = setOf(
        HostPackages.BAIDU_CN,
        "${HostPackages.BAIDU_CN}:pushservice",
    ),
    pushServiceProcessNames = setOf(
        "${HostPackages.BAIDU_CN}:pushservice",
    ),
    capabilities = HostCapabilities(
        features = HostFeatureCapabilities(
            availableKeys = BaiduFeatureSets.baiduCnAvailableKeys,
        ),
        hooks = HostHookCapabilities(
            catalogId = HostIds.BAIDU_CN,
            supportsOemPushHook = true,
        ),
        settings = HostSettingsCapabilities(
            primarySplashAdFeatureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
        ),
        uiHookPoints = HostUiHookPoints(
            mainActivityClassName = BaiduSharedHookPoints.MAIN_ACTIVITY,
            homeActivityClassName = BaiduSharedHookPoints.HOME_ACTIVITY,
            aboutMeActivityClassName = BaiduSharedHookPoints.ABOUT_ME_ACTIVITY,
            newAboutMeActivityClassName = BaiduSharedHookPoints.NEW_ABOUT_ME_ACTIVITY,
            mainActivityPresenterClassName = BaiduSharedHookPoints.MAIN_ACTIVITY_PRESENTER,
            newHomeFabFragmentClassName = BaiduSharedHookPoints.NEW_HOME_FAB_FRAGMENT,
            popupResponseClassName = BaiduSharedHookPoints.POPUP_RESPONSE,
            settingsImageResultHostActivityClassNames = listOf(
                BaiduSharedHookPoints.ABOUT_ME_ACTIVITY,
                BaiduSharedHookPoints.NEW_ABOUT_ME_ACTIVITY,
                BaiduSharedHookPoints.HOME_ACTIVITY,
                BaiduSharedHookPoints.MAIN_ACTIVITY,
            ),
            skinConfigClassName = BaiduSharedHookPoints.SKIN_CONFIG_CLASS,
            homeCustomize = HomeCustomizeHookPoints(
                searchboxFragmentClassName = BaiduSharedHookPoints.HOME_SEARCHBOX_FRAGMENT,
                searchTextFragmentClassNames = listOf(BaiduSharedHookPoints.HOME_SEARCHBOX_FRAGMENT),
                homeRootFragmentClassNames = listOf(BaiduSharedHookPoints.HOME25_FRAGMENT),
                feedFragmentClassNames = BaiduSharedHookPoints.FEED_FRAGMENT_CLASSES,
                toolbarFragmentClassNames = listOf(BaiduSharedHookPoints.HOME25_KINGKONG_FRAGMENT),
                toolbarViewIdNames = listOf(BaiduSharedHookPoints.HOME25_KINGKONG_CONTENT_LAYOUT_ID),
                storyCardViewClassNames = listOf(BaiduSharedHookPoints.HOME_STORY_CARD_VIEW),
                saveCardViewClassNames = BaiduSharedHookPoints.HOME_SAVE_CARD_VIEWS,
                recentCardDataUseCaseClassName = BaiduSharedHookPoints.HOME_RECENT_CARD_DATA_USE_CASE,
                home25aiContextCompanionClassName = BaiduSharedHookPoints.HOME25AI_CONTEXT_COMPANION,
                loadHomeBannerMethodName = BaiduSharedHookPoints.HOME25AI_LOAD_HOME_BANNER_METHOD,
            ),
        ),
        startupHookPoints = HostStartupHookPoints(
            hotStartSplashLifecycleManagerClassName = BaiduSharedHookPoints.SPLASH_LIFECYCLE_MANAGER,
            hotStartSplashBackgroundResumeAdStartMethodName =
                BaiduSharedHookPoints.SPLASH_LIFECYCLE_BACKGROUND_RESUME_AD_START_METHOD,
        ),
        dexKit = HostDexKitCapabilities(
            targetRegistryId = HostIds.BAIDU_CN,
            showStatusInSettings = true,
            stableActivityClassNames = listOf(
                BaiduSharedHookPoints.HOME_ACTIVITY,
                BaiduSharedHookPoints.MAIN_ACTIVITY,
            ),
        ),
        diagnostics = HostDiagnosticsCapabilities(
            deviceFingerprintCollector = BaiduDomesticDeviceFingerprintRuntime,
        ),
    ),
)
