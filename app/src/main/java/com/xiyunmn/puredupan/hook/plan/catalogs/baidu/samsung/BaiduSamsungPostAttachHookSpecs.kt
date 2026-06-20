package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.samsung

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ad.SamsungBusinessOpDialogBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.startup.SamsungSplashAdBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.SamsungGameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.samsung.ui.SamsungGameCenterRuntimeBlockHook
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

    val tailEntry = listOf(
        HookSpec("SamsungHomeTitleBarModuleEntryHook", { context, _, _ ->
            context.isMain
        }) { cl -> SamsungHomeTitleBarModuleEntryHook.hook(cl) },
    )
}
