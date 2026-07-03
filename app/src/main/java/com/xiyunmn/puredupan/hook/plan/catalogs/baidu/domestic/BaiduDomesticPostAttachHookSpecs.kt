package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticBusinessOpDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticLuckyCouponBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticNotificationPromptBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticSharePushGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ad.DomesticUpdateDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticAdSdkInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticAigcBackgroundComponentBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticAudioCircleViewAutostartBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticB2fGuidancePrefetchBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticDatapackSocketRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticDynamicPluginAutoDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticGarbageCleanServiceRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticIconResourceDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticIncentiveBusinessServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticOemPushServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticSwanPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticThumbnailOperatorServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance.DomesticVideoAdPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup.DomesticLaunchHandoffOptimizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.startup.DomesticSplashAdBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticBottomAiTabReplaceHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticGameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticGameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticRenewButtonHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.DomesticSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.entry.DomesticAboutMeModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.entry.DomesticHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.membercard.DomesticMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.AppStoreReviewBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.FullScreenBackupBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.NonWifiDownloadDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.SvipIconGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.DomesticAutoDailySignInHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.DomesticHotStartSplashCompatHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search.SearchPageCustomizeHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduDomesticPostAttachHookSpecs {
    val entry = listOf(
        HookSpec("DomesticAboutMeModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> DomesticAboutMeModuleEntryHook.hook(cl) },
    )

    val automation = listOf(
        HookSpec("DomesticAutoDailySignInHook", { context, _, _ ->
            context.isMain
        }, featureKey = FeatureKeys.KEY_AUTO_DAILY_SIGN_IN) { cl -> DomesticAutoDailySignInHook.hook(cl) },
    )

    val performance = listOf(
        HookSpec("DomesticGarbageCleanServiceRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isGarbageCleanServiceRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER) { cl ->
            DomesticGarbageCleanServiceRegisterBlockHook.hook(cl)
        },
        HookSpec("DomesticDatapackSocketRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDatapackSocketRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DATAPACK_SOCKET_REGISTER) { cl ->
            DomesticDatapackSocketRegisterBlockHook.hook(cl)
        },
        HookSpec("DomesticAigcBackgroundComponentBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAigcBackgroundComponentDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT) { cl ->
            DomesticAigcBackgroundComponentBlockHook.hook(cl)
        },
        HookSpec("DomesticDynamicPluginAutoDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDynamicPluginAutoDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD) { cl ->
            DomesticDynamicPluginAutoDownloadBlockHook.hook(cl)
        },
        HookSpec("DomesticOemPushServiceBlockHook", { context, settings, _ ->
            context.supportsOemPushHook &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isOemPushServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_OEM_PUSH_SERVICE) { cl ->
            DomesticOemPushServiceBlockHook.hook(cl)
        },
        HookSpec("DomesticVideoAdPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isVideoAdPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD) { cl ->
            DomesticVideoAdPreloadBlockHook.hook(cl)
        },
        HookSpec("DomesticAdSdkInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAdSdkInitDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AD_SDK_INIT) { cl ->
            DomesticAdSdkInitBlockHook.hook(cl)
        },
        HookSpec("DomesticSwanPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isSwanPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_SWAN_PRELOAD) { cl ->
            DomesticSwanPreloadBlockHook.hook(cl)
        },
        HookSpec("DomesticThumbnailOperatorServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isThumbnailOperatorServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE) { cl ->
            DomesticThumbnailOperatorServiceBlockHook.hook(cl)
        },
        HookSpec("DomesticIncentiveBusinessServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIncentiveBusinessServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE) { cl ->
            DomesticIncentiveBusinessServiceBlockHook.hook(cl)
        },
        HookSpec("DomesticAudioCircleViewAutostartBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isMediaBrowserServiceAutostartDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART) { cl ->
            DomesticAudioCircleViewAutostartBlockHook.hook(cl)
        },
        HookSpec("DomesticIconResourceDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIconResourceDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD) { cl ->
            DomesticIconResourceDownloadBlockHook.hook(cl)
        },
        HookSpec("DomesticB2fGuidancePrefetchBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isB2fGuidancePrefetchDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_B2F_GUIDANCE_PREFETCH) { cl ->
            DomesticB2fGuidancePrefetchBlockHook.hook(cl)
        },
    )

    val preAd = listOf(
        HookSpec("DomesticHotStartSplashCompatHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            DomesticHotStartSplashCompatHook.hook(cl)
        },
        HookSpec("DomesticRenewButtonHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isRenewButtonHidden
        }, featureKey = FeatureKeys.KEY_HIDE_RENEW_BUTTON) { cl -> DomesticRenewButtonHideHook.hook(cl) },
    )

    val startup = listOf(
        HookSpec("DomesticSplashAdBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            DomesticSplashAdBlockHook.hook(cl)
        },
        HookSpec("DomesticLaunchHandoffOptimizeHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            DomesticLaunchHandoffOptimizeHook.hook(cl)
        },
    )

    val ad = listOf(
        HookSpec("DomesticBusinessOpDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            DomesticBusinessOpDialogBlockHook.hook(cl)
        },
        HookSpec("DomesticLuckyCouponBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            DomesticLuckyCouponBlockHook.hook(cl)
        },
        HookSpec("DomesticUpdateDialogBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isUpdateDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_UPDATE_DIALOG) { cl ->
            DomesticUpdateDialogBlockHook.hook(cl)
        },
        HookSpec("FullScreenBackupBlockHook", { context, settings, _ ->
            context.isMain && settings.isFullScreenBackupBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP) { cl ->
            FullScreenBackupBlockHook.hook(cl)
        },
        HookSpec("SvipIconGuideBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isFullScreenBackupBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP) { cl ->
            SvipIconGuideBlockHook.hook(cl)
        },
        HookSpec("DomesticSharePushGuideBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSharePushGuideBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE) { cl ->
            DomesticSharePushGuideBlockHook.hook(cl)
        },
        HookSpec("AppStoreReviewBlockHook", { context, settings, _ ->
            context.isMain && settings.isAppStoreReviewBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW) { cl ->
            AppStoreReviewBlockHook.hook(cl)
        },
        HookSpec("NonWifiDownloadDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isNonWifiDownloadDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG) { cl ->
            NonWifiDownloadDialogBlockHook.hook(cl)
        },
        HookSpec("DomesticNotificationPromptBlockHook", { context, settings, _ ->
            context.isMain && settings.isNotificationPromptBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT) { cl ->
            DomesticNotificationPromptBlockHook.hook(cl)
        },
    )

    val middleLead = listOf(
        HookSpec("DomesticBottomAiTabReplaceHook", { context, settings, _ ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                settings.isBottomAiReplaced
        }, featureKey = FeatureKeys.KEY_REPLACE_BOTTOM_AI) { cl -> DomesticBottomAiTabReplaceHook.hook(cl) },
    )

    val middleBeforeMyPage = listOf(
        HookSpec("DomesticGameCenterRuntimeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl -> DomesticGameCenterRuntimeBlockHook.hook(cl) },
        HookSpec("DomesticGameCenterRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl -> DomesticGameCenterRemoveHook.hook(cl) },
    )

    val searchPage = listOf(
        HookSpec("SearchPageCustomizeHook", { context, settings, derived ->
            context.isMain &&
                settings.isSearchPageCustomizeEnabled &&
                derived.hasSearchPageCustomizeOption
        }, featureKey = FeatureKeys.KEY_SEARCH_PAGE_CUSTOMIZE) { cl ->
            SearchPageCustomizeHook.hook(cl)
        },
    )

    val memberCard = listOf(
        HookSpec(
            "DomesticMemberCardCustomizeHook",
            { context, settings, derived ->
                context.isMain &&
                    settings.isMemberCardCustomizeEnabled &&
                    derived.hasMemberCardCustomizeOption
            },
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        ) { cl -> DomesticMemberCardCustomizeHook.hook(cl) },
    )

    val postMember = emptyList<HookSpec>()

    val tail = listOf(
        HookSpec("DomesticSystemNightModeSyncHook", { context, settings, _ ->
            context.isMain &&
                settings.isFollowSystemNightModeEnabled
        }, featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE) { cl -> DomesticSystemNightModeSyncHook.hook(cl) },
    )

    val tailEntry = listOf(
        HookSpec("DomesticHomeTitleBarModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> DomesticHomeTitleBarModuleEntryHook.hook(cl) },
    )
}
