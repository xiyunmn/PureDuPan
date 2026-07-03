package com.xiyunmn.puredupan.hook.config

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys

data class SettingsSnapshot(
    // Logging
    val isDetailedLoggingEnabled: Boolean = false,
    val isDexKitSupported: Boolean = false,

    // Splash / interstitial and dialog blocking
    val isSplashInterstitialBlockEnabled: Boolean = false,
    val isHotStartSplashRemoveEnabled: Boolean = false,
    val isInAppDialogBlocked: Boolean = false,
    val isUpdateDialogBlocked: Boolean = false,
    val isFullScreenBackupBlocked: Boolean = false,
    val isSharePushGuideBlocked: Boolean = false,
    val isAppStoreReviewBlocked: Boolean = false,
    val isNonWifiDownloadDialogBlocked: Boolean = false,
    val isNotificationPromptBlocked: Boolean = false,

    // Host UI simplification
    val isBottomAiReplaced: Boolean = false,
    val isHomeCustomizeEnabled: Boolean = false,
    val isHomeTopPromotionHidden: Boolean = false,
    val isHomeSearchPlaceholderHidden: Boolean = false,
    val isHomeSearchAigcIconHidden: Boolean = false,
    val isHomeToolbarHidden: Boolean = false,
    val isHomeFeedTipHidden: Boolean = false,
    val isHomeBannerHidden: Boolean = false,
    val isHomeMemoriesSectionHidden: Boolean = false,
    val isHomeSaveSectionHidden: Boolean = false,
    val isHomeRecentSectionHidden: Boolean = false,
    val isFilePageCustomizeEnabled: Boolean = false,
    val isFilePageBottomSafetyTipHidden: Boolean = false,
    val isSearchPageCustomizeEnabled: Boolean = false,
    val isSearchPageAiEntryHidden: Boolean = false,
    val isSearchPagePlaceholderHidden: Boolean = false,
    val isSearchPageHistoryHidden: Boolean = false,
    val isSearchPageRecommendHidden: Boolean = false,
    val isIntlSearchPageSvipBannerHidden: Boolean = false,
    val isSearchPageVoiceSearchHidden: Boolean = false,
    val isSharePageCustomizeEnabled: Boolean = false,
    val isMyPageCustomizeEnabled: Boolean = false,
    val isGameCenterRemoved: Boolean = false,
    val isAboutMeBannerRemoved: Boolean = false,
    val isMyServiceRemoved: Boolean = false,
    val isAboutMeCoinCenterBubbleHidden: Boolean = false,
    val isAboutMeSignInDotHidden: Boolean = false,
    val isAboutMeManageSpaceTextHidden: Boolean = false,
    val isAboutMeRewardTextHidden: Boolean = false,
    val isAboutMeAccountExitTextHidden: Boolean = false,
    val isAboutMeStarSkinTextHidden: Boolean = false,
    val isAboutMeFreeDataCardTextHidden: Boolean = false,
    val isHomeFabRemoved: Boolean = false,
    val isRenewButtonHidden: Boolean = false,
    val isBottomBarBadgeBlocked: Boolean = false,
    val isAlbumBackupBarBlocked: Boolean = false,
    val isAboutMeAiCoinAssetHidden: Boolean = false,
    val isMemberCardCustomizeEnabled: Boolean = false,
    val isMemberCardBackgroundReplaced: Boolean = false,
    val memberCardBackgroundUri: String? = null,
    val memberCardBackgroundBlurRadius: Int = 0,
    val memberCardBackgroundScalePercent: Int = 100,
    val memberCardBackgroundRotationDegrees: Int = 0,
    val memberCardBackgroundOffsetXPermille: Int = 0,
    val memberCardBackgroundOffsetYPermille: Int = 0,
    val isMemberCardSizeAdjusted: Boolean = false,
    val memberCardWidthDp: Int = 0,
    val memberCardHeightDp: Int = 0,
    val isMemberCardOperationHidden: Boolean = false,
    val isMemberCardBenefitHidden: Boolean = false,
    val isMemberCardFirstBenefitHidden: Boolean = false,
    val isMemberCardSecondBenefitHidden: Boolean = false,
    val isMemberCardThirdBenefitHidden: Boolean = false,
    val isMemberCardBenefitBarHidden: Boolean = false,
    val isMemberCardSvipLevelHidden: Boolean = false,
    val isMemberCardSvipStatusHidden: Boolean = false,
    val isMemberCardRenewButtonHidden: Boolean = false,
    val isIntlMemberCardSvipLevelHidden: Boolean = false,
    val isIntlMemberCardUpgradeButtonHidden: Boolean = false,
    val isMemberCardClickRemoved: Boolean = false,
    val isMemberCardBackgroundViewedOnClick: Boolean = false,
    val isNightModeSupportEnabled: Boolean = false,
    val isFollowSystemNightModeEnabled: Boolean = false,
    val isAutoDailySignInEnabled: Boolean = false,

    // Performance optimize
    val isPerformanceOptimizeEnabled: Boolean = false,
    val isIntlSplashStartupAccelerateEnabled: Boolean = false,
    val isGarbageCleanServiceRegisterDisabled: Boolean = false,
    val isDatapackSocketRegisterDisabled: Boolean = false,
    val isAigcBackgroundComponentDisabled: Boolean = false,
    val isDynamicPluginAutoDownloadDisabled: Boolean = false,
    val isOemPushServiceDisabled: Boolean = false,
    val isVideoAdPreloadDisabled: Boolean = false,
    val isAdSdkInitDisabled: Boolean = false,
    val isSwanPreloadDisabled: Boolean = false,
    val isThumbnailOperatorServiceDisabled: Boolean = false,
    val isIncentiveBusinessServiceDisabled: Boolean = false,
    val isMediaBrowserServiceAutostartDisabled: Boolean = false,
    val isIconResourceDownloadDisabled: Boolean = false,
    val isB2fGuidancePrefetchDisabled: Boolean = false,
    val isIntlOfflinePackageInitBlocked: Boolean = false,
    val isIntlFeedPreloadDelayed: Boolean = false,
    val isIntlTaskScoreRefreshDelayed: Boolean = false,
    val isIntlStoryDouyinInitBlocked: Boolean = false,
    val isIntlNonCoreDiffSocketDelayed: Boolean = false,
    val isIntlFloatViewStartupDelayed: Boolean = false,
    val isIntlAudioCircleStartupShowBlocked: Boolean = false,
    val isIntlAigcWidgetBackgroundBlocked: Boolean = false,
    val isIntlAlbumAiInitBlocked: Boolean = false,

    // Bottom bar customization
    val isBottomBarCustomEnabled: Boolean = false,
    val isBottomBarTabFileHidden: Boolean = false,
    val isBottomBarTabShareHidden: Boolean = false,
    val isBottomBarTabVipHidden: Boolean = false,
    val isBottomBarTabAigcHidden: Boolean = false,
    val isBottomBarTabHomeHidden: Boolean = false,
    val isBottomBarTabMineHidden: Boolean = false,

    // Restricted features
    val areRestrictedFeaturesUnlocked: Boolean = false,
) {
    fun isDexKitTargetFeatureEnabled(featureKey: String?): Boolean {
        return when (featureKey) {
            null -> true
            FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL -> isSplashInterstitialBlockEnabled
            FeatureKeys.KEY_REMOVE_HOT_START_SPLASH -> isHotStartSplashRemoveEnabled
            FeatureKeys.KEY_BLOCK_UPDATE_DIALOG -> isUpdateDialogBlocked
            FeatureKeys.KEY_REPLACE_BOTTOM_AI -> isBottomAiReplaced
            FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE -> isThumbnailOperatorServiceDisabled
            FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART -> isMediaBrowserServiceAutostartDisabled
            FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD -> isVideoAdPreloadDisabled
            FeatureKeys.KEY_DISABLE_SWAN_PRELOAD -> isSwanPreloadDisabled
            FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD -> isIconResourceDownloadDisabled
            FeatureKeys.KEY_AUTO_DAILY_SIGN_IN -> isAutoDailySignInEnabled
            FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD -> isDynamicPluginAutoDownloadDisabled
            FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT -> isIntlStoryDouyinInitBlocked
            FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET -> isIntlNonCoreDiffSocketDelayed
            FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT -> isIntlAlbumAiInitBlocked
            FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE -> isFollowSystemNightModeEnabled
            else -> false
        }
    }

    fun enablesDexKitTargetComparedTo(previous: SettingsSnapshot): Boolean {
        return DEXKIT_TARGET_FEATURE_KEYS.any { featureKey ->
            !previous.isDexKitTargetFeatureEnabled(featureKey) &&
                isDexKitTargetFeatureEnabled(featureKey)
        }
    }

    companion object {
        val DEXKIT_TARGET_FEATURE_KEYS = setOf(
            FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
            FeatureKeys.KEY_REMOVE_HOT_START_SPLASH,
            FeatureKeys.KEY_BLOCK_UPDATE_DIALOG,
            FeatureKeys.KEY_REPLACE_BOTTOM_AI,
            FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
            FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
            FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD,
            FeatureKeys.KEY_DISABLE_SWAN_PRELOAD,
            FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
            FeatureKeys.KEY_AUTO_DAILY_SIGN_IN,
            FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
            FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT,
            FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
            FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT,
            FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
        )
    }
}
