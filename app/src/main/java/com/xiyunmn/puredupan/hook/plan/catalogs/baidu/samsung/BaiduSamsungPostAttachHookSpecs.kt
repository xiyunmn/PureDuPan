package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad.SamsungBusinessOpDialogBlockHook
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
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.startup.SamsungSplashAdBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.SamsungGameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.SamsungGameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.SamsungSystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry.SamsungAboutMeModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.entry.SamsungHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.membercard.SamsungMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduSamsungPostAttachHookSpecs {
    val entry = listOf(
        HookSpec("SamsungAboutMeModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> SamsungAboutMeModuleEntryHook.hook(cl) },
    )

    val startup = listOf(
        HookSpec("SamsungSplashAdBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl ->
            SamsungSplashAdBlockHook.hook(cl)
        },
    )

    val ad = listOf(
        HookSpec("SamsungBusinessOpDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isInAppDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG) { cl ->
            SamsungBusinessOpDialogBlockHook.hook(cl)
        },
    )

    val myPage = listOf(
        HookSpec("SamsungGameCenterRuntimeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl ->
            SamsungGameCenterRuntimeBlockHook.hook(cl)
        },
        HookSpec("SamsungGameCenterRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isGameCenterRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_GAME_CENTER) { cl ->
            SamsungGameCenterRemoveHook.hook(cl)
        },
    )

    val memberCard = listOf(
        HookSpec(
            "SamsungMemberCardCustomizeHook",
            { context, settings, derived ->
                context.isMain &&
                    settings.isMemberCardCustomizeEnabled &&
                    derived.hasMemberCardCustomizeOption
            },
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        ) { cl -> SamsungMemberCardCustomizeHook.hook(cl) },
    )

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
            context.isPushService &&
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
    )

    val tail = listOf(
        HookSpec("SamsungSystemNightModeSyncHook", { context, settings, _ ->
            context.isMain &&
                settings.isFollowSystemNightModeEnabled
        }, featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE) { cl ->
            SamsungSystemNightModeSyncHook.hook(cl)
        },
    )

    val tailEntry = listOf(
        HookSpec("SamsungHomeTitleBarModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> SamsungHomeTitleBarModuleEntryHook.hook(cl) },
    )
}
