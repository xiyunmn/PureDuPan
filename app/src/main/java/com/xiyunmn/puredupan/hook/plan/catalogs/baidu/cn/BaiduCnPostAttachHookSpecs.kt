package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.cn

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.BusinessOpDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.LuckyCouponBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.SharePushGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ad.UpdateDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.AppStoreReviewBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.FullScreenBackupBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.NonWifiDownloadDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.SvipIconGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.AdSdkInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.AigcBackgroundComponentBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.AudioCircleViewAutostartBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.B2fGuidancePrefetchBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.DatapackSocketRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.DynamicPluginAutoDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.GarbageCleanServiceRegisterBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.IconResourceDownloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.IncentiveBusinessServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.OemPushServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.SwanPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.ThumbnailOperatorServiceBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.performance.VideoAdPreloadBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.startup.hotstart.CnHotStartSplashRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.entry.CnAboutMeModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.entry.CnHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.membercard.CnMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.BottomAiTabReplaceHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.RenewButtonHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.SystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduCnPostAttachHookSpecs {
    val entry = listOf(
        HookSpec("CnAboutMeModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> CnAboutMeModuleEntryHook.hook(cl) },
    )

    val preAd = listOf(
        HookSpec("RenewButtonHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isRenewButtonHidden
        }, featureKey = FeatureKeys.KEY_HIDE_RENEW_BUTTON) { cl -> RenewButtonHideHook.hook(cl) },
    )

    val startup = listOf(
        HookSpec("CnHotStartSplashRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            CnHotStartSplashRemoveHook.hook(cl)
        },
    )

    val ad = listOf(
        HookSpec("BusinessOpDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            BusinessOpDialogBlockHook.hook(cl)
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
        HookSpec("NonWifiDownloadDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isNonWifiDownloadDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG) { cl ->
            NonWifiDownloadDialogBlockHook.hook(cl)
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
        HookSpec("GarbageCleanServiceRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isGarbageCleanServiceRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER) { cl ->
            GarbageCleanServiceRegisterBlockHook.hook(cl)
        },
        HookSpec("DatapackSocketRegisterBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDatapackSocketRegisterDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DATAPACK_SOCKET_REGISTER) { cl ->
            DatapackSocketRegisterBlockHook.hook(cl)
        },
        HookSpec("AigcBackgroundComponentBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAigcBackgroundComponentDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT) { cl ->
            AigcBackgroundComponentBlockHook.hook(cl)
        },
        HookSpec("DynamicPluginAutoDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isDynamicPluginAutoDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD) { cl ->
            DynamicPluginAutoDownloadBlockHook.hook(cl)
        },
        HookSpec("OemPushServiceBlockHook", { context, settings, _ ->
            context.supportsOemPushHook &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isOemPushServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_OEM_PUSH_SERVICE) { cl ->
            OemPushServiceBlockHook.hook(cl)
        },
        HookSpec("VideoAdPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isVideoAdPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD) { cl ->
            VideoAdPreloadBlockHook.hook(cl)
        },
        HookSpec("AdSdkInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isAdSdkInitDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_AD_SDK_INIT) { cl ->
            AdSdkInitBlockHook.hook(cl)
        },
        HookSpec("SwanPreloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isSwanPreloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_SWAN_PRELOAD) { cl ->
            SwanPreloadBlockHook.hook(cl)
        },
        HookSpec("ThumbnailOperatorServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isThumbnailOperatorServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE) { cl ->
            ThumbnailOperatorServiceBlockHook.hook(cl)
        },
        HookSpec("IncentiveBusinessServiceBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIncentiveBusinessServiceDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE) { cl ->
            IncentiveBusinessServiceBlockHook.hook(cl)
        },
        HookSpec("AudioCircleViewAutostartBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isMediaBrowserServiceAutostartDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART) { cl ->
            AudioCircleViewAutostartBlockHook.hook(cl)
        },
        HookSpec("IconResourceDownloadBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIconResourceDownloadDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD) { cl ->
            IconResourceDownloadBlockHook.hook(cl)
        },
        HookSpec("B2fGuidancePrefetchBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isB2fGuidancePrefetchDisabled
        }, featureKey = FeatureKeys.KEY_DISABLE_B2F_GUIDANCE_PREFETCH) { cl ->
            B2fGuidancePrefetchBlockHook.hook(cl)
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
