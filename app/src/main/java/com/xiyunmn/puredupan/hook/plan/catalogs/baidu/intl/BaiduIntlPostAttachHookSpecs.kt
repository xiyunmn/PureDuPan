package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.intl

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ad.IntlNonWifiDownloadDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.automation.IntlAutoDailySignInHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.AppStoreReviewBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.FullScreenBackupBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ad.SvipIconGuideBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAigcWidgetBackgroundBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAlbumAiInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlAudioCircleStartupShowBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlFeedPreloadDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlFloatViewStartupDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlNonCoreDiffSocketDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlOfflinePackageInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlStoryDouyinInitBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.performance.IntlTaskScoreRefreshDelayHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.startup.IntlLaunchHandoffOptimizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.startup.hotstart.IntlHotStartSplashRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.NightModeSupportHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.SystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.entry.IntlAboutMeModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.entry.IntlHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.membercard.IntlMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduIntlPostAttachHookSpecs {
    val automation = listOf(
        HookSpec("IntlAutoDailySignInHook", { context, _, _ ->
            context.isMain
        }, featureKey = FeatureKeys.KEY_AUTO_DAILY_SIGN_IN) { cl ->
            IntlAutoDailySignInHook.hook(cl)
        },
    )

    val entry = listOf(
        HookSpec("IntlAboutMeModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> IntlAboutMeModuleEntryHook.hook(cl) },
    )

    val hotStart = listOf(
        HookSpec("IntlHotStartSplashRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isHotStartSplashRemoveEnabled
        }, featureKey = FeatureKeys.KEY_REMOVE_HOT_START_SPLASH) { cl ->
            IntlHotStartSplashRemoveHook.hook(cl)
        },
    )

    val ad = listOf(
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
        HookSpec("AppStoreReviewBlockHook", { context, settings, _ ->
            context.isMain && settings.isAppStoreReviewBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW) { cl ->
            AppStoreReviewBlockHook.hook(cl)
        },
        HookSpec("IntlNonWifiDownloadDialogBlockHook", { context, settings, _ ->
            context.isMain && settings.isNonWifiDownloadDialogBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG) { cl ->
            IntlNonWifiDownloadDialogBlockHook.hook(cl)
        },
    )

    val memberCard = listOf(
        HookSpec(
            "IntlMemberCardCustomizeHook",
            { context, settings, derived ->
                context.isMain &&
                    settings.isMemberCardCustomizeEnabled &&
                    derived.hasMemberCardCustomizeOption
            },
            featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        ) { cl -> IntlMemberCardCustomizeHook.hook(cl) },
    )

    val theme = listOf(
        HookSpec("NightModeSupportHook", { context, settings, _ ->
            context.isMain &&
                settings.isNightModeSupportEnabled
        }, featureKey = FeatureKeys.KEY_ENABLE_NIGHT_MODE_SUPPORT) { cl ->
            NightModeSupportHook.hook(cl)
        },
        HookSpec("IntlSystemNightModeSyncHook", { context, settings, _ ->
            context.isMain &&
                (settings.isNightModeSupportEnabled ||
                    settings.isFollowSystemNightModeEnabled)
        }, featureKey = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE) { cl ->
            SystemNightModeSyncHook.hook(cl)
        },
    )

    val startup = listOf(
        HookSpec("IntlLaunchHandoffOptimizeHook", { context, settings, _ ->
            context.isMain &&
                settings.isIntlSplashStartupAccelerateEnabled
        }, featureKey = FeatureKeys.KEY_ACCELERATE_INTL_SPLASH_STARTUP) { cl ->
            IntlLaunchHandoffOptimizeHook.hook(cl)
        },
    )

    val performance = listOf(
        HookSpec("IntlOfflinePackageInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlOfflinePackageInitBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT) { cl ->
            IntlOfflinePackageInitBlockHook.hook(cl)
        },
        HookSpec("IntlFeedPreloadDelayHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlFeedPreloadDelayed
        }, featureKey = FeatureKeys.KEY_DELAY_INTL_FEED_PRELOAD) { cl ->
            IntlFeedPreloadDelayHook.hook(cl)
        },
        HookSpec("IntlTaskScoreRefreshDelayHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlTaskScoreRefreshDelayed
        }, featureKey = FeatureKeys.KEY_DELAY_INTL_TASK_SCORE_REFRESH) { cl ->
            IntlTaskScoreRefreshDelayHook.hook(cl)
        },
        HookSpec("IntlStoryDouyinInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlStoryDouyinInitBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT) { cl ->
            IntlStoryDouyinInitBlockHook.hook(cl)
        },
        HookSpec("IntlNonCoreDiffSocketDelayHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlNonCoreDiffSocketDelayed
        }, featureKey = FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET) { cl ->
            IntlNonCoreDiffSocketDelayHook.hook(cl)
        },
        HookSpec("IntlFloatViewStartupDelayHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlFloatViewStartupDelayed
        }, featureKey = FeatureKeys.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP) { cl ->
            IntlFloatViewStartupDelayHook.hook(cl)
        },
        HookSpec("IntlAudioCircleStartupShowBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlAudioCircleStartupShowBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW) { cl ->
            IntlAudioCircleStartupShowBlockHook.hook(cl)
        },
        HookSpec("IntlAigcWidgetBackgroundBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlAigcWidgetBackgroundBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND) { cl ->
            IntlAigcWidgetBackgroundBlockHook.hook(cl)
        },
        HookSpec("IntlAlbumAiInitBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isPerformanceOptimizeEnabled &&
                settings.isIntlAlbumAiInitBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT) { cl ->
            IntlAlbumAiInitBlockHook.hook(cl)
        },
    )

    val tailEntry = listOf(
        HookSpec("IntlHomeTitleBarModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> IntlHomeTitleBarModuleEntryHook.hook(cl) },
    )
}
