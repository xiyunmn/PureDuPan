package com.xiyunmn.puredupan.hook.plan

import com.xiyunmn.puredupan.hook.config.SettingsSnapshot
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.host.HostProfile

internal object HookInstallPlanner {
    fun shouldHandleProcess(host: HostProfile, processName: String): Boolean {
        return host.shouldHandleProcess(processName)
    }

    fun shouldInstallAttachHook(host: HostProfile, processName: String): Boolean {
        return host.shouldInstallAttachHook(processName)
    }

    fun postAttachPlan(
        host: HostProfile,
        processName: String,
        settings: SettingsSnapshot,
    ): HookInstallPlan {
        val hostContext = HookPlanHostContext.from(host, processName)
        val context = PlanContext(hostContext)
        val derived = deriveSettings(context, settings)
        val entries = HostHookCatalogs.forHost(hostContext)
            .postAttachSpecs()
            .filter { spec -> spec.isFeatureAvailableFor(context) && spec.enabled(context, settings, derived) }
            .map { spec -> HookInstallEntry(spec.id, spec.install) }
        return HookInstallPlan(processName, "postAttach", entries)
    }

    private fun deriveSettings(
        context: PlanContext,
        settings: SettingsSnapshot,
    ): DerivedSettings {
        fun enabled(featureKey: String, value: Boolean): Boolean {
            return context.isFeatureAvailable(featureKey) && value
        }

        return DerivedSettings(
            hasMemberCardCustomizeOption =
                enabled(FeatureKeys.KEY_REPLACE_MEMBER_CARD_BACKGROUND, settings.isMemberCardBackgroundReplaced) ||
                    enabled(FeatureKeys.KEY_MEMBER_CARD_SIZE_ADJUST, settings.isMemberCardSizeAdjusted) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_OPERATION, settings.isMemberCardOperationHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT, settings.isMemberCardBenefitHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT, settings.isMemberCardFirstBenefitHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT, settings.isMemberCardSecondBenefitHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT, settings.isMemberCardThirdBenefitHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, settings.isMemberCardBenefitBarHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, settings.isMemberCardSvipLevelHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_STATUS, settings.isMemberCardSvipStatusHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, settings.isMemberCardRenewButtonHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL, settings.isIntlMemberCardSvipLevelHidden) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON,
                        settings.isIntlMemberCardUpgradeButtonHidden,
                    ) ||
                    enabled(FeatureKeys.KEY_REMOVE_MEMBER_CARD_CLICK, settings.isMemberCardClickRemoved) ||
                    enabled(
                        FeatureKeys.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK,
                        settings.isMemberCardBackgroundViewedOnClick,
                    ),
            hasHomeCustomizeOption =
                enabled(FeatureKeys.KEY_HIDE_HOME_TOP_PROMOTION, settings.isHomeTopPromotionHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_SEARCH_PLACEHOLDER, settings.isHomeSearchPlaceholderHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_SEARCH_AIGC_ICON, settings.isHomeSearchAigcIconHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_TOOLBAR, settings.isHomeToolbarHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_FEED_TIP, settings.isHomeFeedTipHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_BANNER, settings.isHomeBannerHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_MEMORIES_SECTION, settings.isHomeMemoriesSectionHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_SAVE_SECTION, settings.isHomeSaveSectionHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_HOME_RECENT_SECTION, settings.isHomeRecentSectionHidden) ||
                    enabled(
                        FeatureKeys.KEY_HOME_RECENT_ITEM_LIMIT_ENABLED,
                        settings.isHomeRecentItemLimitEnabled,
                    ) ||
                    enabled(
                        FeatureKeys.KEY_HOME_SAVE_VERTICAL_LAYOUT,
                        settings.isHomeSaveVerticalLayoutEnabled,
                    ),
            hasFilePageCustomizeOption =
                enabled(
                    FeatureKeys.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP,
                    settings.isFilePageBottomSafetyTipHidden,
                ),
            hasDownloadPageCustomizeOption =
                enabled(
                    FeatureKeys.KEY_HIDE_DOWNLOAD_PAGE_GAME_GUIDE,
                    settings.isDownloadPageGameGuideHidden,
                ) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_DOWNLOAD_PAGE_PROMOTION_AD,
                        settings.isDownloadPagePromotionAdHidden,
                    ) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_DOWNLOAD_PAGE_MEMBER_PROMOTION,
                        settings.isDownloadPageMemberPromotionHidden,
                    ),
            hasSearchPageCustomizeOption =
                enabled(FeatureKeys.KEY_HIDE_SEARCH_PAGE_AI_ENTRY, settings.isSearchPageAiEntryHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER, settings.isSearchPagePlaceholderHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_SEARCH_PAGE_HISTORY, settings.isSearchPageHistoryHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_SEARCH_PAGE_RECOMMEND, settings.isSearchPageRecommendHidden) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_INTL_SEARCH_PAGE_SVIP_BANNER,
                        settings.isIntlSearchPageSvipBannerHidden,
                    ) ||
                    enabled(FeatureKeys.KEY_HIDE_SEARCH_PAGE_VOICE_SEARCH, settings.isSearchPageVoiceSearchHidden),
            hasMyPageCustomizeOption =
                enabled(
                    FeatureKeys.KEY_MY_PAGE_CONTENT_AUTO_FOLLOW_MEMBER_CARD,
                    settings.isMyPageContentAutoFollowMemberCardEnabled,
                ) ||
                    enabled(
                        FeatureKeys.KEY_MY_PAGE_CONTENT_MANUAL_OFFSET,
                        settings.isMyPageContentManualOffsetEnabled,
                    ) ||
                    enabled(FeatureKeys.KEY_HIDE_RENEW_BUTTON, settings.isRenewButtonHidden) ||
                    enabled(FeatureKeys.KEY_REMOVE_GAME_CENTER, settings.isGameCenterRemoved) ||
                    enabled(FeatureKeys.KEY_REMOVE_ABOUT_ME_BANNER, settings.isAboutMeBannerRemoved) ||
                    enabled(FeatureKeys.KEY_REMOVE_MY_SERVICE, settings.isMyServiceRemoved) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE,
                        settings.isAboutMeCoinCenterBubbleHidden,
                    ) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, settings.isAboutMeSignInDotHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, settings.isAboutMeAiCoinAssetHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, settings.isAboutMeManageSpaceTextHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_REWARD_TEXT, settings.isAboutMeRewardTextHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, settings.isAboutMeAccountExitTextHidden) ||
                    enabled(FeatureKeys.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, settings.isAboutMeStarSkinTextHidden) ||
                    enabled(
                        FeatureKeys.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT,
                        settings.isAboutMeFreeDataCardTextHidden,
                    ),
        )
    }
}
