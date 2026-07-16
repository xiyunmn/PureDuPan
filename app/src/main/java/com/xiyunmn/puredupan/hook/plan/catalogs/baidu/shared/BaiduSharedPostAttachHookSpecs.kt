package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.SplashBypassCore
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AboutMeGodModeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme.AboutMeCoinCenterBubbleHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.aboutme.AboutMeServiceAndSignDotHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AlbumBackupBarBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BottomBarBadgeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BottomBarStaticTabHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.FilePageCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.HomeCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.NewHomeFabRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.SettingsImagePickerResultHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduSharedPostAttachHookSpecs {
    val preAd = listOf(
        HookSpec("SettingsImagePickerResultHook", { context, _, _ ->
            context.isMain
        }, featureKey = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE) { cl -> SettingsImagePickerResultHook.hook(cl) },
    )

    val splashBypass = listOf(
        HookSpec("SplashBypassCore", { context, settings, _ ->
            context.isMain && settings.isSplashInterstitialBlockEnabled
        }, featureKey = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL) { cl -> SplashBypassCore.hook(cl) },
    )

    val middle = listOf(
        HookSpec("BottomBarStaticTabHideHook", { context, settings, _ ->
            fun enabled(featureKey: String, value: Boolean): Boolean {
                return context.isFeatureAvailable(featureKey) && value
            }

            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                (
                    enabled(FeatureKeys.KEY_HIDE_TAB_HOME, settings.isBottomBarTabHomeHidden) ||
                        enabled(FeatureKeys.KEY_HIDE_TAB_FILE, settings.isBottomBarTabFileHidden) ||
                        enabled(FeatureKeys.KEY_HIDE_TAB_SHARE, settings.isBottomBarTabShareHidden) ||
                        enabled(FeatureKeys.KEY_HIDE_TAB_VIP, settings.isBottomBarTabVipHidden) ||
                        enabled(FeatureKeys.KEY_HIDE_TAB_MINE, settings.isBottomBarTabMineHidden)
                    )
        }, featureKey = FeatureKeys.KEY_CUSTOM_BOTTOM_BAR) { cl -> BottomBarStaticTabHideHook.hook(cl) },
        HookSpec("BottomBarBadgeBlockHook", { context, settings, _ ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                settings.isBottomBarBadgeBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_BOTTOM_BADGE) { cl -> BottomBarBadgeBlockHook.hook(cl) },
        HookSpec("HomeCustomizeHook", { context, settings, derived ->
            context.isMain &&
                settings.isHomeCustomizeEnabled &&
                derived.hasHomeCustomizeOption
        }, featureKey = FeatureKeys.KEY_HOME_CUSTOMIZE) { cl -> HomeCustomizeHook.hook(cl) },
        HookSpec("FilePageCustomizeHook", { context, settings, derived ->
            context.isMain &&
                settings.isFilePageCustomizeEnabled &&
                derived.hasFilePageCustomizeOption
        }, featureKey = FeatureKeys.KEY_FILE_PAGE_CUSTOMIZE) { cl -> FilePageCustomizeHook.hook(cl) },
    )

    val myPage = listOf(
        HookSpec("AboutMeGodModeHook", { context, settings, derived ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                derived.hasMyPageCustomizeOption
        }, featureKey = FeatureKeys.KEY_MY_PAGE_CUSTOMIZE) { cl -> AboutMeGodModeHook.hook(cl) },
        HookSpec("AboutMeCoinCenterBubbleHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isAboutMeCoinCenterBubbleHidden
        }, featureKey = FeatureKeys.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE) { cl ->
            AboutMeCoinCenterBubbleHideHook.hook(cl)
        },
        HookSpec("AboutMeServiceAndSignDotHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                (
                    settings.isMyServiceRemoved ||
                        settings.isAboutMeSignInDotHidden
                )
        }, featureKey = FeatureKeys.KEY_MY_PAGE_CUSTOMIZE) { cl ->
            AboutMeServiceAndSignDotHideHook.hook(cl)
        },
    )

    val postMemberLead = listOf(
        HookSpec("AlbumBackupBarBlockHook", { context, settings, _ ->
            context.isMain && settings.isAlbumBackupBarBlocked
        }, featureKey = FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR) { cl -> AlbumBackupBarBlockHook.hook(cl) },
        HookSpec("NewHomeFabRemoveHook", { context, settings, _ ->
            context.isMain &&
                settings.isSharePageCustomizeEnabled &&
                settings.isHomeFabRemoved
        }, featureKey = FeatureKeys.KEY_REMOVE_HOME_FAB) { cl -> NewHomeFabRemoveHook.hook(cl) },
    )

    val postMemberTail = emptyList<HookSpec>()

}
