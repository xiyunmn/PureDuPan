package com.xiyunmn.puredupan.hook.plan.catalogs.baidu.domestic

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.feature.baidu.shared.automation.DomesticAutoDailySignInHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.BottomAiTabReplaceHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRemoveHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.GameCenterRuntimeBlockHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.RenewButtonHideHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.SystemNightModeSyncHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.entry.CnHomeTitleBarModuleEntryHook
import com.xiyunmn.puredupan.hook.feature.baidu.cn.ui.membercard.CnMemberCardCustomizeHook
import com.xiyunmn.puredupan.hook.feature.baidu.shared.ui.search.SearchPageCustomizeHook
import com.xiyunmn.puredupan.hook.plan.HookSpec

internal object BaiduDomesticPostAttachHookSpecs {
    val automation = listOf(
        HookSpec("DomesticAutoDailySignInHook", { context, settings, _ ->
            context.isMain &&
                settings.isAutoDailySignInEnabled
        }, featureKey = FeatureKeys.KEY_AUTO_DAILY_SIGN_IN) { cl -> DomesticAutoDailySignInHook.hook(cl) },
    )

    val preAd = listOf(
        HookSpec("RenewButtonHideHook", { context, settings, _ ->
            context.isMain &&
                settings.isMyPageCustomizeEnabled &&
                settings.isRenewButtonHidden
        }, featureKey = FeatureKeys.KEY_HIDE_RENEW_BUTTON) { cl -> RenewButtonHideHook.hook(cl) },
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
