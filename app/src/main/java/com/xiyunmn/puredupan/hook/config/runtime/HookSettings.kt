package com.xiyunmn.puredupan.hook.config.runtime

import android.content.Context
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.SettingsSnapshot

internal object HookSettings {
    data class AboutMeOptions(
        val isMyPageCustomizeEnabled: Boolean,
        val isAboutMeBannerRemoved: Boolean,
        val isMyServiceRemoved: Boolean,
        val isAboutMeCoinCenterBubbleHidden: Boolean,
        val isAboutMeSignInDotHidden: Boolean,
        val isAboutMeAiCoinAssetHidden: Boolean,
        val isAboutMeManageSpaceTextHidden: Boolean,
        val isAboutMeRewardTextHidden: Boolean,
        val isAboutMeAccountExitTextHidden: Boolean,
        val isAboutMeStarSkinTextHidden: Boolean,
        val isAboutMeFreeDataCardTextHidden: Boolean,
    )

    fun initialize(context: Context) {
        ConfigManager.init(context)
    }

    fun settingsSnapshot(): SettingsSnapshot {
        return ConfigManager.snapshot()
    }

    fun aboutMeOptions(): AboutMeOptions {
        val snapshot = ConfigManager.snapshot()
        return AboutMeOptions(
            isMyPageCustomizeEnabled = snapshot.isMyPageCustomizeEnabled,
            isAboutMeBannerRemoved = snapshot.isAboutMeBannerRemoved,
            isMyServiceRemoved = snapshot.isMyServiceRemoved,
            isAboutMeCoinCenterBubbleHidden = snapshot.isAboutMeCoinCenterBubbleHidden,
            isAboutMeSignInDotHidden = snapshot.isAboutMeSignInDotHidden,
            isAboutMeAiCoinAssetHidden = snapshot.isAboutMeAiCoinAssetHidden,
            isAboutMeManageSpaceTextHidden = snapshot.isAboutMeManageSpaceTextHidden,
            isAboutMeRewardTextHidden = snapshot.isAboutMeRewardTextHidden,
            isAboutMeAccountExitTextHidden = snapshot.isAboutMeAccountExitTextHidden,
            isAboutMeStarSkinTextHidden = snapshot.isAboutMeStarSkinTextHidden,
            isAboutMeFreeDataCardTextHidden = snapshot.isAboutMeFreeDataCardTextHidden,
        )
    }

    fun memberCardSnapshot(): SettingsSnapshot {
        return ConfigManager.snapshot()
    }

    fun hasRecordedMemberCardDefaultSize(context: Context): Boolean {
        val prefs = ConfigManager.getModuleStatePrefs(context)
        return prefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, 0) > 0 &&
            prefs.getInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, 0) > 0
    }

    fun recordMemberCardDefaultSize(context: Context, width: Int, height: Int) {
        ConfigManager.getModuleStatePrefs(context)
            .edit()
            .putInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX, width)
            .putInt(ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX, height)
            .apply()
    }

    val isExperimentalDexKitEnabled: Boolean
        get() = ConfigManager.isExperimentalDexKitEnabled

    val isSplashInterstitialBlockEnabled: Boolean
        get() = ConfigManager.isSplashInterstitialBlockEnabled

    val isHotStartSplashRemoveEnabled: Boolean
        get() = ConfigManager.isHotStartSplashRemoveEnabled

    val isInAppDialogBlocked: Boolean
        get() = ConfigManager.isInAppDialogBlocked

    val isUpdateDialogBlocked: Boolean
        get() = ConfigManager.isUpdateDialogBlocked

    val isFullScreenBackupBlocked: Boolean
        get() = ConfigManager.isFullScreenBackupBlocked

    val isSharePushGuideBlocked: Boolean
        get() = ConfigManager.isSharePushGuideBlocked

    val isAppStoreReviewBlocked: Boolean
        get() = ConfigManager.isAppStoreReviewBlocked

    val isNonWifiDownloadDialogBlocked: Boolean
        get() = ConfigManager.isNonWifiDownloadDialogBlocked

    val isIntlSplashStartupAccelerateEnabled: Boolean
        get() = ConfigManager.isIntlSplashStartupAccelerateEnabled

    val isBottomAiReplaced: Boolean
        get() = ConfigManager.isBottomAiReplaced

    val isSharePageCustomizeEnabled: Boolean
        get() = ConfigManager.isSharePageCustomizeEnabled

    val isMyPageCustomizeEnabled: Boolean
        get() = ConfigManager.isMyPageCustomizeEnabled

    val isGameCenterRemoved: Boolean
        get() = ConfigManager.isGameCenterRemoved

    val isHomeFabRemoved: Boolean
        get() = ConfigManager.isHomeFabRemoved

    val isRenewButtonHidden: Boolean
        get() = ConfigManager.isRenewButtonHidden

    val isBottomBarBadgeBlocked: Boolean
        get() = ConfigManager.isBottomBarBadgeBlocked

    val isAlbumBackupBarBlocked: Boolean
        get() = ConfigManager.isAlbumBackupBarBlocked

    val isFollowSystemNightModeEnabled: Boolean
        get() = ConfigManager.isFollowSystemNightModeEnabled

    val isBottomBarCustomEnabled: Boolean
        get() = ConfigManager.isBottomBarCustomEnabled

    val isBottomBarTabFileHidden: Boolean
        get() = ConfigManager.isBottomBarTabFileHidden

    val isBottomBarTabShareHidden: Boolean
        get() = ConfigManager.isBottomBarTabShareHidden

    val isBottomBarTabVipHidden: Boolean
        get() = ConfigManager.isBottomBarTabVipHidden

    val isBottomBarTabAigcHidden: Boolean
        get() = ConfigManager.isBottomBarTabAigcHidden

    val isBottomBarTabHomeHidden: Boolean
        get() = ConfigManager.isBottomBarTabHomeHidden

    val isBottomBarTabMineHidden: Boolean
        get() = ConfigManager.isBottomBarTabMineHidden

    val isHomeCustomizeEnabled: Boolean
        get() = ConfigManager.isHomeCustomizeEnabled

    val isHomeTopPromotionHidden: Boolean
        get() = ConfigManager.isHomeTopPromotionHidden

    val isHomeSearchPlaceholderHidden: Boolean
        get() = ConfigManager.isHomeSearchPlaceholderHidden

    val isHomeSearchAigcIconHidden: Boolean
        get() = ConfigManager.isHomeSearchAigcIconHidden

    val isHomeFeedTipHidden: Boolean
        get() = ConfigManager.isHomeFeedTipHidden

    val isHomeBannerHidden: Boolean
        get() = ConfigManager.isHomeBannerHidden

    val isHomeMemoriesSectionHidden: Boolean
        get() = ConfigManager.isHomeMemoriesSectionHidden

    val isHomeSaveSectionHidden: Boolean
        get() = ConfigManager.isHomeSaveSectionHidden

    val isHomeRecentSectionHidden: Boolean
        get() = ConfigManager.isHomeRecentSectionHidden

    val isPerformanceOptimizeEnabled: Boolean
        get() = ConfigManager.isPerformanceOptimizeEnabled

    val isGarbageCleanServiceRegisterDisabled: Boolean
        get() = ConfigManager.isGarbageCleanServiceRegisterDisabled

    val isDatapackSocketRegisterDisabled: Boolean
        get() = ConfigManager.isDatapackSocketRegisterDisabled

    val isAigcBackgroundComponentDisabled: Boolean
        get() = ConfigManager.isAigcBackgroundComponentDisabled

    val isDynamicPluginAutoDownloadDisabled: Boolean
        get() = ConfigManager.isDynamicPluginAutoDownloadDisabled

    val isOemPushServiceDisabled: Boolean
        get() = ConfigManager.isOemPushServiceDisabled

    val isVideoAdPreloadDisabled: Boolean
        get() = ConfigManager.isVideoAdPreloadDisabled

    val isAdSdkInitDisabled: Boolean
        get() = ConfigManager.isAdSdkInitDisabled

    val isSwanPreloadDisabled: Boolean
        get() = ConfigManager.isSwanPreloadDisabled

    val isThumbnailOperatorServiceDisabled: Boolean
        get() = ConfigManager.isThumbnailOperatorServiceDisabled

    val isIncentiveBusinessServiceDisabled: Boolean
        get() = ConfigManager.isIncentiveBusinessServiceDisabled

    val isMediaBrowserServiceAutostartDisabled: Boolean
        get() = ConfigManager.isMediaBrowserServiceAutostartDisabled

    val isIconResourceDownloadDisabled: Boolean
        get() = ConfigManager.isIconResourceDownloadDisabled

    val isB2fGuidancePrefetchDisabled: Boolean
        get() = ConfigManager.isB2fGuidancePrefetchDisabled

    val isIntlOfflinePackageInitBlocked: Boolean
        get() = ConfigManager.isIntlOfflinePackageInitBlocked

    val isIntlFeedPreloadDelayed: Boolean
        get() = ConfigManager.isIntlFeedPreloadDelayed

    val isIntlTaskScoreRefreshDelayed: Boolean
        get() = ConfigManager.isIntlTaskScoreRefreshDelayed

    val isIntlStoryDouyinInitBlocked: Boolean
        get() = ConfigManager.isIntlStoryDouyinInitBlocked

    val isIntlNonCoreDiffSocketDelayed: Boolean
        get() = ConfigManager.isIntlNonCoreDiffSocketDelayed

    val isIntlFloatViewStartupDelayed: Boolean
        get() = ConfigManager.isIntlFloatViewStartupDelayed

    val isIntlAudioCircleStartupShowBlocked: Boolean
        get() = ConfigManager.isIntlAudioCircleStartupShowBlocked

    val isIntlAigcWidgetBackgroundBlocked: Boolean
        get() = ConfigManager.isIntlAigcWidgetBackgroundBlocked

    val isIntlAlbumAiInitBlocked: Boolean
        get() = ConfigManager.isIntlAlbumAiInitBlocked
}
