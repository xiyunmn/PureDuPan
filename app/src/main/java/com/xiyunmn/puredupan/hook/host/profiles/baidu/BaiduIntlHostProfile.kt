package com.xiyunmn.puredupan.hook.host.profiles.baidu

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.config.model.MemberCardLayoutMode
import com.xiyunmn.puredupan.hook.host.HomeCustomizeHookPoints
import com.xiyunmn.puredupan.hook.host.HostCapabilities
import com.xiyunmn.puredupan.hook.host.HostDexKitCapabilities
import com.xiyunmn.puredupan.hook.host.HostFeatureCapabilities
import com.xiyunmn.puredupan.hook.host.HostHookCapabilities
import com.xiyunmn.puredupan.hook.host.HostIds
import com.xiyunmn.puredupan.hook.host.HostPackages
import com.xiyunmn.puredupan.hook.host.HostProfile
import com.xiyunmn.puredupan.hook.host.HostSettingsCapabilities
import com.xiyunmn.puredupan.hook.host.HostUiHookPoints
import com.xiyunmn.puredupan.hook.host.features.baidu.BaiduFeatureSets
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSharedHookPoints

internal val BaiduIntlHostProfile = HostProfile(
    id = HostIds.BAIDU_INTL,
    packageName = HostPackages.BAIDU_INTL,
    handledProcessNames = setOf(HostPackages.BAIDU_INTL),
    attachHookProcessNames = setOf(HostPackages.BAIDU_INTL),
    capabilities = HostCapabilities(
        features = HostFeatureCapabilities(
            availableKeys = BaiduFeatureSets.baiduIntlAvailableKeys,
        ),
        hooks = HostHookCapabilities(
            catalogId = HostIds.BAIDU_INTL,
        ),
        settings = HostSettingsCapabilities(
            primarySplashAdFeatureKey = FeatureKeys.KEY_REMOVE_HOT_START_SPLASH,
            memberCardLayoutMode = MemberCardLayoutMode.BENEFIT_SLOT,
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
                homeRootFragmentClassNames = listOf(BaiduSharedHookPoints.NEW_FH_HOME_FRAGMENT),
                feedFragmentClassNames = BaiduSharedHookPoints.FEED_FRAGMENT_CLASSES,
                toolbarViewIdNames = listOf(BaiduSharedHookPoints.INTL_VAJRA_AREA_ID),
                storyCardViewClassNames = listOf(BaiduSharedHookPoints.HOME_STORY_CARD_VIEW),
                saveCardViewClassNames = BaiduSharedHookPoints.HOME_SAVE_CARD_VIEWS,
                recentCardViewClassNames = BaiduSharedHookPoints.HOME_RECENT_CARD_VIEWS,
                home25aiContextCompanionClassName = BaiduSharedHookPoints.HOME25AI_CONTEXT_COMPANION,
                loadHomeBannerMethodName = BaiduSharedHookPoints.HOME25AI_LOAD_HOME_BANNER_METHOD,
            ),
        ),
        dexKit = HostDexKitCapabilities(
            targetRegistryId = HostIds.BAIDU_INTL,
            showStatusInSettings = true,
            stableActivityClassNames = listOf(
                BaiduSharedHookPoints.MAIN_ACTIVITY,
                BaiduSharedHookPoints.HOME_ACTIVITY,
            ),
        ),
    ),
)
