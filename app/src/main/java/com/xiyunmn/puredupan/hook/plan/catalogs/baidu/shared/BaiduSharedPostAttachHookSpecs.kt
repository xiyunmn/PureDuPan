package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.shared

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.shared.startup.SplashBypassCore
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AboutMeGodModeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.AlbumBackupBarBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BottomBarBadgeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.BottomBarSimplifyFeature
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
    )

    val myPage = listOf(
        HookSpec("AboutMeGodModeHook", { context, settings, derived ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                derived.hasMyPageCustomizeOption
        }, featureKey = FeatureKeys.KEY_MY_PAGE_CUSTOMIZE) { cl -> AboutMeGodModeHook.hook(cl) },
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

    val postMemberTail = listOf(
        HookSpec("BottomBarSimplifyFeature", { context, settings, derived ->
            context.isMain &&
                settings.isBottomBarCustomEnabled &&
                derived.hasBottomBarTabOption
        }, featureKey = FeatureKeys.KEY_CUSTOM_BOTTOM_BAR) { cl -> BottomBarSimplifyFeature.hook(cl) },
    )

}
