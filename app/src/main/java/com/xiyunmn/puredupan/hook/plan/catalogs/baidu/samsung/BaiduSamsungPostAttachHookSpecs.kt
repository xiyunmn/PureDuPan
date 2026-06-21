package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.LuckyCouponBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.SharePushGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.UpdateDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.IconResourceDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.BottomAiTabReplaceHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.RenewButtonHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.SystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.entry.CnHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.membercard.CnMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.AppStoreReviewBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.FullScreenBackupBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.NonWifiDownloadDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.SvipIconGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search.SearchPageCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad.SamsungBusinessOpDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad.SamsungNotificationPromptBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungAdSdkInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungAigcBackgroundComponentBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungAudioCircleViewAutostartBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungB2fGuidancePrefetchBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungDatapackSocketRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungDynamicPluginAutoDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungGarbageCleanServiceRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungIncentiveBusinessServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungOemPushServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungSwanPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungThumbnailOperatorServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.performance.SamsungVideoAdPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.startup.SamsungLaunchHandoffOptimizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.startup.SamsungSplashAdBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry.SamsungAboutMeModuleEntryHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduSamsungPostAttachHookSpecs {
    val entry = listOf(
        HookSpec("SamsungAboutMeModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> SamsungAboutMeModuleEntryHook.hook(cl) },
    )

    val preAd = listOf(
        HookSpec("RenewButtonHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isRenewButtonHidden
        }, featureKey = FeatureKeys.KEY_HIDE_RENEW_BUTTON) { cl -> RenewButtonHideHook.hook(cl) },
    )

    val startup = listOf(
        HookSpec("SamsungSplashAdBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            SamsungSplashAdBlockHook.hook(cl)
        },
        HookSpec("SamsungLaunchHandoffOptimizeHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            SamsungLaunchHandoffOptimizeHook.hook(cl)
        },
    )

    val ad = listOf(
        HookSpec("SamsungBusinessOpDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            SamsungBusinessOpDialogBlockHook.hook(cl)
        },
        HookSpec("LuckyCouponBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            LuckyCouponBlockHook.hook(cl)
        },
        HookSpec("UpdateDialogBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isUpdateDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_UPDATE_DIALOG) { cl ->
            UpdateDialogBlockHook.hook(cl)
        },
        HookSpec("NonWifiDownloadDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isNonWifiDownloadDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG) { cl ->
            NonWifiDownloadDialogBlockHook.hook(cl)
        },
        HookSpec("FullScreenBackupBlockHook", { context, settings, _ ->
            context.isMain && settings.isFullScreenBackupBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP) { cl ->
            FullScreenBackupBlockHook.hook(cl)
        },
        HookSpec("SvipIconGuideBlockHook", { context, settings, _ ->
            context.isMain && settings.isFullScreenBackupBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP) { cl ->
            SvipIconGuideBlockHook.hook(cl)
        },
        HookSpec("SharePushGuideBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSharePushGuideBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE) { cl ->
            SharePushGuideBlockHook.hook(cl)
        },
        HookSpec("AppStoreReviewBlockHook", { context, settings, _ ->
            context.isMain && settings.isAppStoreReviewBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW) { cl ->
            AppStoreReviewBlockHook.hook(cl)
        },
        HookSpec("SamsungNotificationPromptBlockHook", { context, settings, _ ->
            context.isMain && settings.isNotificationPromptBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT) { cl ->
            SamsungNotificationPromptBlockHook.hook(cl)
        },
    )

    val middleLead = listOf(
        HookSpec("BottomAiTabReplaceHook", { context, settings, _ ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                settings.isBottomAiReplaced
        }, featureKey = FeatureKeys.KEY_REPLACE_BOTTOM_AI) { cl -> BottomAiTabReplaceHook.hook(cl) },
    )

    val middleBeforeMyPage = listOf(
        HookSpec("GameCenterRuntimeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl -> GameCenterRuntimeBlockHook.hook(cl) },
        HookSpec("GameCenterRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl -> GameCenterRemoveHook.hook(cl) },
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
            "CnMemberCardCustomizeHook",
            { context, settings, derived ->
                context.isMain &&
                    settings.isMemberCardCustomizeEnabled &&
                    derived.hasMemberCardCustomizeOption
            },
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        ) { cl -> CnMemberCardCustomizeHook.hook(cl) },
    )

    val postMember = emptyList<HookSpec>()

    val performance = listOf(
        HookSpec("SamsungB2fGuidancePrefetchBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isB2fGuidancePrefetchDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_B2F_GUIDANCE_PREFETCH) { cl ->
            SamsungB2fGuidancePrefetchBlockHook.hook(cl)
        },
        HookSpec("SamsungAudioCircleViewAutostartBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isMediaBrowserServiceAutostartDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART) { cl ->
            SamsungAudioCircleViewAutostartBlockHook.hook(cl)
        },
        HookSpec("SamsungOemPushServiceBlockHook", { context, settings, _ ->
            context.supportsOemPushHook &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isOemPushServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_OEM_PUSH_SERVICE) { cl ->
            SamsungOemPushServiceBlockHook.hook(cl)
        },
        HookSpec("SamsungSwanPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isSwanPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_SWAN_PRELOAD) { cl ->
            SamsungSwanPreloadBlockHook.hook(cl)
        },
        HookSpec("SamsungAdSdkInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAdSdkInitDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AD_SDK_INIT) { cl ->
            SamsungAdSdkInitBlockHook.hook(cl)
        },
        HookSpec("SamsungGarbageCleanServiceRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isGarbageCleanServiceRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER) { cl ->
            SamsungGarbageCleanServiceRegisterBlockHook.hook(cl)
        },
        HookSpec("SamsungDatapackSocketRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDatapackSocketRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DATAPACK_SOCKET_REGISTER) { cl ->
            SamsungDatapackSocketRegisterBlockHook.hook(cl)
        },
        HookSpec("SamsungDynamicPluginAutoDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDynamicPluginAutoDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD) { cl ->
            SamsungDynamicPluginAutoDownloadBlockHook.hook(cl)
        },
        HookSpec("SamsungAigcBackgroundComponentBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAigcBackgroundComponentDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT) { cl ->
            SamsungAigcBackgroundComponentBlockHook.hook(cl)
        },
        HookSpec("SamsungIncentiveBusinessServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIncentiveBusinessServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE) { cl ->
            SamsungIncentiveBusinessServiceBlockHook.hook(cl)
        },
        HookSpec("SamsungThumbnailOperatorServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isThumbnailOperatorServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE) { cl ->
            SamsungThumbnailOperatorServiceBlockHook.hook(cl)
        },
        HookSpec("SamsungVideoAdPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isVideoAdPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD) { cl ->
            SamsungVideoAdPreloadBlockHook.hook(cl)
        },
        HookSpec("IconResourceDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIconResourceDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD) { cl ->
            IconResourceDownloadBlockHook.hook(cl)
        },
    )

    val tail = listOf(
        HookSpec("SystemNightModeSyncHook", { context, settings, _ ->
            context.isMain &&
                settings.isFollowSystemNightModeEnabled
        }, featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE) { cl -> SystemNightModeSyncHook.hook(cl) },
    )

    val tailEntry = listOf(
        HookSpec("CnHomeTitleBarModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> CnHomeTitleBarModuleEntryHook.hook(cl) },
    )
}
