package com.xiyunmn.puredupan.hook.config

import android.content.Context
import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.config.model.FeatureKeys
import com.xiyunmn.puredupan.hook.config.runtime.ConfigHostRuntime
import com.xiyunmn.puredupan.hook.config.runtime.DexKitSettingsRuntime
import com.xiyunmn.puredupan.hook.config.runtime.FeatureAvailabilityRuntime
import com.xiyunmn.puredupan.hook.core.XposedCompat

object ConfigManager {
    const val USER_SETTINGS_PREFS_NAME = "wangpan_user_settings"
    const val MODULE_STATE_PREFS_NAME = "wangpan_module_state"
    const val PREFS_NAME = USER_SETTINGS_PREFS_NAME
    private const val KEY_USER_SETTINGS_VERSION_CODE = "user_settings_version_code"

    const val KEY_ENABLE_DETAILED_LOGGING = FeatureKeys.KEY_ENABLE_DETAILED_LOGGING
    const val KEY_ENABLE_EXPERIMENTAL_DEXKIT = FeatureKeys.KEY_ENABLE_EXPERIMENTAL_DEXKIT
    const val KEY_BLOCK_SPLASH_INTERSTITIAL = FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL
    const val KEY_REMOVE_HOT_START_SPLASH = FeatureKeys.KEY_REMOVE_HOT_START_SPLASH
    const val KEY_BLOCK_IN_APP_DIALOG = FeatureKeys.KEY_BLOCK_IN_APP_DIALOG
    const val KEY_BLOCK_UPDATE_DIALOG = FeatureKeys.KEY_BLOCK_UPDATE_DIALOG
    const val KEY_BLOCK_FULL_SCREEN_BACKUP = FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP
    const val KEY_BLOCK_SHARE_PUSH_GUIDE = FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE
    const val KEY_BLOCK_APP_STORE_REVIEW = FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW
    const val KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG = FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG
    const val KEY_BLOCK_NOTIFICATION_PROMPT = FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT
    private const val LEGACY_BLOCK_SAMSUNG_NON_WIFI_DOWNLOAD_DIALOG =
        "block_samsung_non_wifi_download_dialog"
    const val KEY_REPLACE_BOTTOM_AI = FeatureKeys.KEY_REPLACE_BOTTOM_AI
    const val KEY_HOME_CUSTOMIZE = FeatureKeys.KEY_HOME_CUSTOMIZE
    private const val LEGACY_HOME_TOP_PROMOTION = "remove_top_ai"
    const val KEY_HIDE_HOME_TOP_PROMOTION = FeatureKeys.KEY_HIDE_HOME_TOP_PROMOTION
    const val KEY_HIDE_HOME_SEARCH_PLACEHOLDER = FeatureKeys.KEY_HIDE_HOME_SEARCH_PLACEHOLDER
    const val KEY_HIDE_HOME_SEARCH_AIGC_ICON = FeatureKeys.KEY_HIDE_HOME_SEARCH_AIGC_ICON
    const val KEY_HIDE_HOME_FEED_TIP = FeatureKeys.KEY_HIDE_HOME_FEED_TIP
    const val KEY_HIDE_HOME_BANNER = FeatureKeys.KEY_HIDE_HOME_BANNER
    const val KEY_HIDE_HOME_MEMORIES_SECTION = FeatureKeys.KEY_HIDE_HOME_MEMORIES_SECTION
    const val KEY_HIDE_HOME_SAVE_SECTION = FeatureKeys.KEY_HIDE_HOME_SAVE_SECTION
    const val KEY_HIDE_HOME_RECENT_SECTION = FeatureKeys.KEY_HIDE_HOME_RECENT_SECTION
    const val KEY_FILE_PAGE_CUSTOMIZE = FeatureKeys.KEY_FILE_PAGE_CUSTOMIZE
    const val KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP = FeatureKeys.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP
    const val KEY_SEARCH_PAGE_CUSTOMIZE = FeatureKeys.KEY_SEARCH_PAGE_CUSTOMIZE
    const val KEY_HIDE_SEARCH_PAGE_AI_ENTRY = FeatureKeys.KEY_HIDE_SEARCH_PAGE_AI_ENTRY
    const val KEY_HIDE_SEARCH_PAGE_PLACEHOLDER = FeatureKeys.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER
    const val KEY_HIDE_SEARCH_PAGE_RECOMMEND = FeatureKeys.KEY_HIDE_SEARCH_PAGE_RECOMMEND
    const val KEY_SHARE_PAGE_CUSTOMIZE = FeatureKeys.KEY_SHARE_PAGE_CUSTOMIZE
    const val KEY_MY_PAGE_CUSTOMIZE = FeatureKeys.KEY_MY_PAGE_CUSTOMIZE
    const val KEY_REMOVE_GAME_CENTER = FeatureKeys.KEY_REMOVE_GAME_CENTER
    const val KEY_REMOVE_ABOUT_ME_BANNER = FeatureKeys.KEY_REMOVE_ABOUT_ME_BANNER
    const val KEY_REMOVE_MY_SERVICE = FeatureKeys.KEY_REMOVE_MY_SERVICE
    const val KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE = FeatureKeys.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE
    const val KEY_HIDE_ABOUT_ME_SIGN_IN_DOT = FeatureKeys.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT
    const val KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT = FeatureKeys.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT
    const val KEY_HIDE_ABOUT_ME_REWARD_TEXT = FeatureKeys.KEY_HIDE_ABOUT_ME_REWARD_TEXT
    const val KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT = FeatureKeys.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT
    const val KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT = FeatureKeys.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT
    const val KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT = FeatureKeys.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT
    const val KEY_REMOVE_HOME_FAB = FeatureKeys.KEY_REMOVE_HOME_FAB
    const val KEY_HIDE_RENEW_BUTTON = FeatureKeys.KEY_HIDE_RENEW_BUTTON
    const val KEY_BLOCK_BOTTOM_BADGE = FeatureKeys.KEY_BLOCK_BOTTOM_BADGE
    const val KEY_BLOCK_ALBUM_BACKUP_BAR = FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR
    private const val LEGACY_HIDE_MEMBER_CARD_BACKGROUND = "hide_member_card_background"
    const val KEY_HIDE_ABOUT_ME_AI_COIN_ASSET = FeatureKeys.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET
    const val KEY_MEMBER_CARD_CUSTOMIZE = FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE
    const val KEY_REPLACE_MEMBER_CARD_BACKGROUND = FeatureKeys.KEY_REPLACE_MEMBER_CARD_BACKGROUND
    const val KEY_MEMBER_CARD_BACKGROUND_URI = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_URI
    const val KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS
    const val KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT
    const val KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE = FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE
    const val KEY_MEMBER_CARD_SIZE_ADJUST = FeatureKeys.KEY_MEMBER_CARD_SIZE_ADJUST
    const val KEY_MEMBER_CARD_SIZE_WIDTH_DP = FeatureKeys.KEY_MEMBER_CARD_SIZE_WIDTH_DP
    const val KEY_MEMBER_CARD_SIZE_HEIGHT_DP = FeatureKeys.KEY_MEMBER_CARD_SIZE_HEIGHT_DP
    const val KEY_MEMBER_CARD_DEFAULT_WIDTH_PX = FeatureKeys.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX
    const val KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX = FeatureKeys.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX
    const val KEY_HIDE_MEMBER_CARD_OPERATION = FeatureKeys.KEY_HIDE_MEMBER_CARD_OPERATION
    const val KEY_HIDE_MEMBER_CARD_BENEFIT = FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT = FeatureKeys.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT = FeatureKeys.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT = FeatureKeys.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_BENEFIT_BAR = FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR
    const val KEY_HIDE_MEMBER_CARD_SVIP_LEVEL = FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL
    const val KEY_HIDE_MEMBER_CARD_SVIP_STATUS = FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_STATUS
    const val KEY_HIDE_MEMBER_CARD_RENEW_BUTTON = FeatureKeys.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON
    const val KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL = FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL
    const val KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON = FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON
    const val KEY_REMOVE_MEMBER_CARD_CLICK = FeatureKeys.KEY_REMOVE_MEMBER_CARD_CLICK
    const val KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK = FeatureKeys.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK
    const val KEY_FOLLOW_SYSTEM_NIGHT_MODE = FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE
    const val KEY_ACCELERATE_INTL_SPLASH_STARTUP = FeatureKeys.KEY_ACCELERATE_INTL_SPLASH_STARTUP
    const val KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER = FeatureKeys.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER
    const val KEY_DISABLE_DATAPACK_SOCKET_REGISTER = FeatureKeys.KEY_DISABLE_DATAPACK_SOCKET_REGISTER
    const val KEY_DISABLE_AIGC_BACKGROUND_COMPONENT = FeatureKeys.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT
    const val KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD = FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD
    const val KEY_DISABLE_OEM_PUSH_SERVICE = FeatureKeys.KEY_DISABLE_OEM_PUSH_SERVICE
    const val KEY_DISABLE_VIDEO_AD_PRELOAD = FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD
    const val KEY_DISABLE_AD_SDK_INIT = FeatureKeys.KEY_DISABLE_AD_SDK_INIT
    const val KEY_DISABLE_SWAN_PRELOAD = FeatureKeys.KEY_DISABLE_SWAN_PRELOAD
    const val KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE = FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE
    const val KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE = FeatureKeys.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE
    const val KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART = FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART
    const val KEY_DISABLE_ICON_RESOURCE_DOWNLOAD = FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD
    const val KEY_DISABLE_B2F_GUIDANCE_PREFETCH = FeatureKeys.KEY_DISABLE_B2F_GUIDANCE_PREFETCH
    const val KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT = FeatureKeys.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT
    const val KEY_DELAY_INTL_FEED_PRELOAD = FeatureKeys.KEY_DELAY_INTL_FEED_PRELOAD
    const val KEY_DELAY_INTL_TASK_SCORE_REFRESH = FeatureKeys.KEY_DELAY_INTL_TASK_SCORE_REFRESH
    const val KEY_BLOCK_INTL_STORY_DOUYIN_INIT = FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT
    const val KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET = FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET
    const val KEY_DELAY_INTL_FLOAT_VIEW_STARTUP = FeatureKeys.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP
    const val KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW = FeatureKeys.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW
    const val KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND = FeatureKeys.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND
    const val KEY_BLOCK_INTL_ALBUM_AI_INIT = FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT
    const val KEY_PERFORMANCE_OPTIMIZE = FeatureKeys.KEY_PERFORMANCE_OPTIMIZE
    const val KEY_RESTRICTED_FEATURES_UNLOCKED = "restricted_features_unlocked"
    const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var activePrefsName: String? = null
    @Volatile private var activeModuleStatePrefsName: String? = null
    @Volatile private var settingsSnapshot: SettingsSnapshot = SettingsSnapshot()
    @Volatile private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val isDetailedLoggingEnabled: Boolean get() = settingsSnapshot.isDetailedLoggingEnabled
    val isExperimentalDexKitEnabled: Boolean get() = settingsSnapshot.isExperimentalDexKitEnabled
    val isSplashInterstitialBlockEnabled: Boolean get() = settingsSnapshot.isSplashInterstitialBlockEnabled
    val isHotStartSplashRemoveEnabled: Boolean get() = settingsSnapshot.isHotStartSplashRemoveEnabled
    val isInAppDialogBlocked: Boolean get() = settingsSnapshot.isInAppDialogBlocked
    val isUpdateDialogBlocked: Boolean get() = settingsSnapshot.isUpdateDialogBlocked
    val isFullScreenBackupBlocked: Boolean get() = settingsSnapshot.isFullScreenBackupBlocked
    val isSharePushGuideBlocked: Boolean get() = settingsSnapshot.isSharePushGuideBlocked
    val isAppStoreReviewBlocked: Boolean get() = settingsSnapshot.isAppStoreReviewBlocked
    val isNonWifiDownloadDialogBlocked: Boolean
        get() = settingsSnapshot.isNonWifiDownloadDialogBlocked
    val isNotificationPromptBlocked: Boolean
        get() = settingsSnapshot.isNotificationPromptBlocked
    val isBottomAiReplaced: Boolean get() = settingsSnapshot.isBottomAiReplaced
    val isHomeCustomizeEnabled: Boolean get() = settingsSnapshot.isHomeCustomizeEnabled
    val isHomeTopPromotionHidden: Boolean get() = settingsSnapshot.isHomeTopPromotionHidden
    val isHomeSearchPlaceholderHidden: Boolean get() = settingsSnapshot.isHomeSearchPlaceholderHidden
    val isHomeSearchAigcIconHidden: Boolean get() = settingsSnapshot.isHomeSearchAigcIconHidden
    val isHomeFeedTipHidden: Boolean get() = settingsSnapshot.isHomeFeedTipHidden
    val isHomeBannerHidden: Boolean get() = settingsSnapshot.isHomeBannerHidden
    val isHomeMemoriesSectionHidden: Boolean get() = settingsSnapshot.isHomeMemoriesSectionHidden
    val isHomeSaveSectionHidden: Boolean get() = settingsSnapshot.isHomeSaveSectionHidden
    val isHomeRecentSectionHidden: Boolean get() = settingsSnapshot.isHomeRecentSectionHidden
    val isFilePageCustomizeEnabled: Boolean get() = settingsSnapshot.isFilePageCustomizeEnabled
    val isFilePageBottomSafetyTipHidden: Boolean get() = settingsSnapshot.isFilePageBottomSafetyTipHidden
    val isSearchPageCustomizeEnabled: Boolean get() = settingsSnapshot.isSearchPageCustomizeEnabled
    val isSearchPageAiEntryHidden: Boolean get() = settingsSnapshot.isSearchPageAiEntryHidden
    val isSearchPagePlaceholderHidden: Boolean get() = settingsSnapshot.isSearchPagePlaceholderHidden
    val isSearchPageRecommendHidden: Boolean get() = settingsSnapshot.isSearchPageRecommendHidden
    val isSharePageCustomizeEnabled: Boolean get() = settingsSnapshot.isSharePageCustomizeEnabled
    val isMyPageCustomizeEnabled: Boolean get() = settingsSnapshot.isMyPageCustomizeEnabled
    val isGameCenterRemoved: Boolean get() = settingsSnapshot.isGameCenterRemoved
    val isAboutMeBannerRemoved: Boolean get() = settingsSnapshot.isAboutMeBannerRemoved
    val isMyServiceRemoved: Boolean get() = settingsSnapshot.isMyServiceRemoved
    val isAboutMeCoinCenterBubbleHidden: Boolean get() = settingsSnapshot.isAboutMeCoinCenterBubbleHidden
    val isAboutMeSignInDotHidden: Boolean get() = settingsSnapshot.isAboutMeSignInDotHidden
    val isAboutMeManageSpaceTextHidden: Boolean get() = settingsSnapshot.isAboutMeManageSpaceTextHidden
    val isAboutMeRewardTextHidden: Boolean get() = settingsSnapshot.isAboutMeRewardTextHidden
    val isAboutMeAccountExitTextHidden: Boolean get() = settingsSnapshot.isAboutMeAccountExitTextHidden
    val isAboutMeStarSkinTextHidden: Boolean get() = settingsSnapshot.isAboutMeStarSkinTextHidden
    val isAboutMeFreeDataCardTextHidden: Boolean get() = settingsSnapshot.isAboutMeFreeDataCardTextHidden
    val isHomeFabRemoved: Boolean get() = settingsSnapshot.isHomeFabRemoved
    val isRenewButtonHidden: Boolean get() = settingsSnapshot.isRenewButtonHidden
    val isBottomBarBadgeBlocked: Boolean get() = settingsSnapshot.isBottomBarBadgeBlocked
    val isAlbumBackupBarBlocked: Boolean get() = settingsSnapshot.isAlbumBackupBarBlocked
    val isAboutMeAiCoinAssetHidden: Boolean get() = settingsSnapshot.isAboutMeAiCoinAssetHidden
    val isMemberCardCustomizeEnabled: Boolean get() = settingsSnapshot.isMemberCardCustomizeEnabled
    val isMemberCardBackgroundReplaced: Boolean get() = settingsSnapshot.isMemberCardBackgroundReplaced
    val memberCardBackgroundUri: String? get() = settingsSnapshot.memberCardBackgroundUri
    val memberCardBackgroundBlurRadius: Int get() = settingsSnapshot.memberCardBackgroundBlurRadius
    val memberCardBackgroundScalePercent: Int get() = settingsSnapshot.memberCardBackgroundScalePercent
    val memberCardBackgroundRotationDegrees: Int get() = settingsSnapshot.memberCardBackgroundRotationDegrees
    val memberCardBackgroundOffsetXPermille: Int get() = settingsSnapshot.memberCardBackgroundOffsetXPermille
    val memberCardBackgroundOffsetYPermille: Int get() = settingsSnapshot.memberCardBackgroundOffsetYPermille
    val isMemberCardSizeAdjusted: Boolean get() = settingsSnapshot.isMemberCardSizeAdjusted
    val memberCardWidthDp: Int get() = settingsSnapshot.memberCardWidthDp
    val memberCardHeightDp: Int get() = settingsSnapshot.memberCardHeightDp
    val isMemberCardOperationHidden: Boolean get() = settingsSnapshot.isMemberCardOperationHidden
    val isMemberCardBenefitHidden: Boolean get() = settingsSnapshot.isMemberCardBenefitHidden
    val isMemberCardFirstBenefitHidden: Boolean get() = settingsSnapshot.isMemberCardFirstBenefitHidden
    val isMemberCardSecondBenefitHidden: Boolean get() = settingsSnapshot.isMemberCardSecondBenefitHidden
    val isMemberCardThirdBenefitHidden: Boolean get() = settingsSnapshot.isMemberCardThirdBenefitHidden
    val isMemberCardBenefitBarHidden: Boolean get() = settingsSnapshot.isMemberCardBenefitBarHidden
    val isMemberCardSvipLevelHidden: Boolean get() = settingsSnapshot.isMemberCardSvipLevelHidden
    val isMemberCardSvipStatusHidden: Boolean get() = settingsSnapshot.isMemberCardSvipStatusHidden
    val isMemberCardRenewButtonHidden: Boolean get() = settingsSnapshot.isMemberCardRenewButtonHidden
    val isIntlMemberCardSvipLevelHidden: Boolean get() = settingsSnapshot.isIntlMemberCardSvipLevelHidden
    val isIntlMemberCardUpgradeButtonHidden: Boolean get() = settingsSnapshot.isIntlMemberCardUpgradeButtonHidden
    val isMemberCardClickRemoved: Boolean get() = settingsSnapshot.isMemberCardClickRemoved
    val isMemberCardBackgroundViewedOnClick: Boolean get() = settingsSnapshot.isMemberCardBackgroundViewedOnClick
    val isFollowSystemNightModeEnabled: Boolean get() = settingsSnapshot.isFollowSystemNightModeEnabled
    val isPerformanceOptimizeEnabled: Boolean
        get() = settingsSnapshot.isPerformanceOptimizeEnabled
    val isIntlSplashStartupAccelerateEnabled: Boolean
        get() = settingsSnapshot.isIntlSplashStartupAccelerateEnabled
    val isGarbageCleanServiceRegisterDisabled: Boolean
        get() = settingsSnapshot.isGarbageCleanServiceRegisterDisabled
    val isDatapackSocketRegisterDisabled: Boolean
        get() = settingsSnapshot.isDatapackSocketRegisterDisabled
    val isAigcBackgroundComponentDisabled: Boolean
        get() = settingsSnapshot.isAigcBackgroundComponentDisabled
    val isDynamicPluginAutoDownloadDisabled: Boolean
        get() = settingsSnapshot.isDynamicPluginAutoDownloadDisabled
    val isOemPushServiceDisabled: Boolean
        get() = settingsSnapshot.isOemPushServiceDisabled
    val isVideoAdPreloadDisabled: Boolean
        get() = settingsSnapshot.isVideoAdPreloadDisabled
    val isAdSdkInitDisabled: Boolean
        get() = settingsSnapshot.isAdSdkInitDisabled
    val isSwanPreloadDisabled: Boolean
        get() = settingsSnapshot.isSwanPreloadDisabled
    val isThumbnailOperatorServiceDisabled: Boolean
        get() = settingsSnapshot.isThumbnailOperatorServiceDisabled
    val isIncentiveBusinessServiceDisabled: Boolean
        get() = settingsSnapshot.isIncentiveBusinessServiceDisabled
    val isMediaBrowserServiceAutostartDisabled: Boolean
        get() = settingsSnapshot.isMediaBrowserServiceAutostartDisabled
    val isIconResourceDownloadDisabled: Boolean
        get() = settingsSnapshot.isIconResourceDownloadDisabled
    val isB2fGuidancePrefetchDisabled: Boolean
        get() = settingsSnapshot.isB2fGuidancePrefetchDisabled
    val isIntlOfflinePackageInitBlocked: Boolean
        get() = settingsSnapshot.isIntlOfflinePackageInitBlocked
    val isIntlFeedPreloadDelayed: Boolean
        get() = settingsSnapshot.isIntlFeedPreloadDelayed
    val isIntlTaskScoreRefreshDelayed: Boolean
        get() = settingsSnapshot.isIntlTaskScoreRefreshDelayed
    val isIntlStoryDouyinInitBlocked: Boolean
        get() = settingsSnapshot.isIntlStoryDouyinInitBlocked
    val isIntlNonCoreDiffSocketDelayed: Boolean
        get() = settingsSnapshot.isIntlNonCoreDiffSocketDelayed
    val isIntlFloatViewStartupDelayed: Boolean
        get() = settingsSnapshot.isIntlFloatViewStartupDelayed
    val isIntlAudioCircleStartupShowBlocked: Boolean
        get() = settingsSnapshot.isIntlAudioCircleStartupShowBlocked
    val isIntlAigcWidgetBackgroundBlocked: Boolean
        get() = settingsSnapshot.isIntlAigcWidgetBackgroundBlocked
    val isIntlAlbumAiInitBlocked: Boolean
        get() = settingsSnapshot.isIntlAlbumAiInitBlocked
    val areRestrictedFeaturesUnlocked: Boolean
        get() = settingsSnapshot.areRestrictedFeaturesUnlocked

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        val targetPrefsName = namespacedUserSettingsPrefsName(appCtx.packageName)
        if (prefs != null && activePrefsName == targetPrefsName) return
        synchronized(this) {
            if (prefs != null && activePrefsName == targetPrefsName) return
            prefsListener?.let { listener ->
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            prefsListener = null
            val p = appCtx.getSharedPreferences(targetPrefsName, Context.MODE_PRIVATE)
            appContext = appCtx
            prefs = p
            activePrefsName = targetPrefsName
            activeModuleStatePrefsName = namespacedModuleStatePrefsName(appCtx.packageName)
            XposedCompat.setDetailedLoggingProvider { shouldOutputDetailedLogs() }
            DexKitSettingsRuntime.bindRuntimeProvider(
                appContextProvider = { getAppContext() },
                moduleStatePrefsProvider = { context -> getModuleStatePrefs(context) },
            )
            applyFeatureAvailabilityInternal(
                ConfigHostRuntime.featureStatusMapForPackage(appCtx.packageName),
            )
            migrateLegacyPrefsIfNeeded(appCtx, p, targetPrefsName)
            ensureUserSettingsVersion(p)

            val snapshot = refreshUserSettingsSnapshot(p)
            if (snapshot.isDetailedLoggingEnabled) {
                XposedCompat.initializeFileLogging(appCtx)
            }
            logSettingsSnapshot("init", snapshot)
            ensurePrefsListener(p)
        }
    }

    fun snapshot(): SettingsSnapshot = settingsSnapshot

    fun readHomeTopPromotionHidden(p: SharedPreferences): Boolean {
        return p.getBoolean(
            KEY_HIDE_HOME_TOP_PROMOTION,
            p.getBoolean(LEGACY_HOME_TOP_PROMOTION, false),
        )
    }

    private fun replaceSettingsSnapshot(snapshot: SettingsSnapshot) {
        settingsSnapshot = snapshot
    }

    private fun ensurePrefsListener(p: SharedPreferences) {
        if (prefsListener != null) return
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, _ ->
            synchronized(this@ConfigManager) {
                if (prefs !== sharedPrefs) return@OnSharedPreferenceChangeListener
                val wasDexKitEnabled = settingsSnapshot.isExperimentalDexKitEnabled
                val snapshot = refreshUserSettingsSnapshot(sharedPrefs)
                if (!wasDexKitEnabled && snapshot.isExperimentalDexKitEnabled) {
                    DexKitSettingsRuntime.markFullScanPendingFromConfigListener()
                }
                if (snapshot.isDetailedLoggingEnabled) {
                    appContext?.let { XposedCompat.initializeFileLogging(it) }
                }
                logSettingsSnapshot("prefsChanged", snapshot)
            }
        }
        prefsListener = listener
        p.registerOnSharedPreferenceChangeListener(listener)
    }

    private fun ensureUserSettingsVersion(p: SharedPreferences) {
        val currentVersion = BuildConfig.VERSION_CODE
        val minSupportedVersion = BuildConfig.MIN_SUPPORTED_USER_SETTINGS_VERSION_CODE
            .coerceAtMost(currentVersion)
        val storedVersion = p.getInt(KEY_USER_SETTINGS_VERSION_CODE, 0)

        if (storedVersion < minSupportedVersion) {
            p.edit()
                .clear()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
            XposedCompat.log(
                "[ConfigManager] user settings reset: " +
                    "storedVersion=$storedVersion, minSupportedVersion=$minSupportedVersion, " +
                    "currentVersion=$currentVersion"
            )
            return
        }

        if (storedVersion != currentVersion) {
            p.edit()
                .putInt(KEY_USER_SETTINGS_VERSION_CODE, currentVersion)
                .apply()
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        prefs?.let { return it }
        init(context)
        prefs?.let { return it }
        val appCtx = context.applicationContext ?: context
        val prefsName = namespacedUserSettingsPrefsName(appCtx.packageName)
        return appCtx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    fun getModuleStatePrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext ?: context
        return appCtx.getSharedPreferences(
            namespacedModuleStatePrefsName(appCtx.packageName),
            Context.MODE_PRIVATE,
        )
    }

    fun getAppContext(): Context? = appContext

    fun resetRuntimeAfterUserDataClear(context: Context) {
        synchronized(this) {
            prefsListener?.let { listener ->
                prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            }
            prefsListener = null
            prefs = null
            appContext = null
            activePrefsName = null
            activeModuleStatePrefsName = null
            settingsSnapshot = SettingsSnapshot()
        }
        init(context.applicationContext ?: context)
    }

    fun shouldOutputDetailedLogs(): Boolean {
        return settingsSnapshot.isDetailedLoggingEnabled
    }

    private fun refreshUserSettingsSnapshot(p: SharedPreferences): SettingsSnapshot {
        val snapshot = buildSettingsSnapshot(p)
        replaceSettingsSnapshot(snapshot)
        return snapshot
    }

    private fun logSettingsSnapshot(reason: String, snapshot: SettingsSnapshot) {
        if (!snapshot.isDetailedLoggingEnabled) return
        val sanitizedSnapshot = snapshot.copy(
            memberCardBackgroundUri = snapshot.memberCardBackgroundUri?.let { "<set>" },
        )
        XposedCompat.logD("[ConfigManager] settings snapshot($reason): $sanitizedSnapshot")
    }

    private fun buildSettingsSnapshot(p: SharedPreferences): SettingsSnapshot {
        fun featureBoolean(key: String, defaultValue: Boolean = false): Boolean {
            return p.getBoolean(key, defaultValue) && isFeatureAvailable(key)
        }
        val memberCardBackgroundReplaced = featureBoolean(
            KEY_REPLACE_MEMBER_CARD_BACKGROUND,
            p.getBoolean(LEGACY_HIDE_MEMBER_CARD_BACKGROUND, false) &&
                isFeatureAvailable(KEY_REPLACE_MEMBER_CARD_BACKGROUND),
        )
        val memberCardBackgroundUri = p.getString(KEY_MEMBER_CARD_BACKGROUND_URI, null)
        val memberCardClickRemoved = featureBoolean(KEY_REMOVE_MEMBER_CARD_CLICK, false)
        val memberCardBackgroundViewedOnClick =
            !memberCardClickRemoved &&
                memberCardBackgroundReplaced &&
                !memberCardBackgroundUri.isNullOrBlank() &&
                featureBoolean(KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK, false)
        val hasMemberCardOptionEnabled =
            memberCardBackgroundReplaced ||
                featureBoolean(KEY_MEMBER_CARD_SIZE_ADJUST, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_OPERATION, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false) ||
                featureBoolean(KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false) ||
                featureBoolean(KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL, false) ||
                featureBoolean(KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON, false) ||
                memberCardClickRemoved ||
                memberCardBackgroundViewedOnClick
        val hasHomeCustomizeOptionEnabled =
            featureBoolean(KEY_HIDE_HOME_TOP_PROMOTION, readHomeTopPromotionHidden(p)) ||
                featureBoolean(KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false) ||
                featureBoolean(KEY_HIDE_HOME_SEARCH_AIGC_ICON, false) ||
                featureBoolean(KEY_HIDE_HOME_FEED_TIP, false) ||
                featureBoolean(KEY_HIDE_HOME_BANNER, false) ||
                featureBoolean(KEY_HIDE_HOME_MEMORIES_SECTION, false) ||
                featureBoolean(KEY_HIDE_HOME_SAVE_SECTION, false) ||
                featureBoolean(KEY_HIDE_HOME_RECENT_SECTION, false)
        val hasFilePageOptionEnabled =
            featureBoolean(KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP, false)
        val hasSearchPageOptionEnabled =
            featureBoolean(KEY_HIDE_SEARCH_PAGE_AI_ENTRY, false) ||
                featureBoolean(KEY_HIDE_SEARCH_PAGE_PLACEHOLDER, false) ||
                featureBoolean(KEY_HIDE_SEARCH_PAGE_RECOMMEND, false)
        val hasSharePageOptionEnabled =
            featureBoolean(KEY_REMOVE_HOME_FAB, false)
        val hasMyPageOptionEnabled =
            featureBoolean(KEY_HIDE_RENEW_BUTTON, false) ||
                featureBoolean(KEY_REMOVE_GAME_CENTER, false) ||
                featureBoolean(KEY_REMOVE_ABOUT_ME_BANNER, false) ||
                featureBoolean(KEY_REMOVE_MY_SERVICE, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_REWARD_TEXT, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false) ||
                featureBoolean(KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT, false)
        val hasBottomBarOptionEnabled =
            featureBoolean(KEY_REPLACE_BOTTOM_AI, false) ||
                featureBoolean(KEY_BLOCK_BOTTOM_BADGE, false) ||
                featureBoolean(KEY_HIDE_TAB_FILE, false) ||
                featureBoolean(KEY_HIDE_TAB_SHARE, false) ||
                featureBoolean(KEY_HIDE_TAB_VIP, false) ||
                featureBoolean(KEY_HIDE_TAB_AIGC, false) ||
                featureBoolean(KEY_HIDE_TAB_HOME, false) ||
                featureBoolean(KEY_HIDE_TAB_MINE, false)
        val hasPerformanceOptionEnabled =
            featureBoolean(KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER, false) ||
                featureBoolean(KEY_DISABLE_DATAPACK_SOCKET_REGISTER, false) ||
                featureBoolean(KEY_DISABLE_AIGC_BACKGROUND_COMPONENT, false) ||
                featureBoolean(KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD, false) ||
                featureBoolean(KEY_DISABLE_OEM_PUSH_SERVICE, false) ||
                featureBoolean(KEY_DISABLE_VIDEO_AD_PRELOAD, false) ||
                featureBoolean(KEY_DISABLE_AD_SDK_INIT, false) ||
                featureBoolean(KEY_DISABLE_SWAN_PRELOAD, false) ||
                featureBoolean(KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE, false) ||
                featureBoolean(KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE, false) ||
                featureBoolean(KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART, false) ||
                featureBoolean(KEY_DISABLE_ICON_RESOURCE_DOWNLOAD, false) ||
                featureBoolean(KEY_DISABLE_B2F_GUIDANCE_PREFETCH, false) ||
                featureBoolean(KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT, false) ||
                featureBoolean(KEY_DELAY_INTL_FEED_PRELOAD, false) ||
                featureBoolean(KEY_DELAY_INTL_TASK_SCORE_REFRESH, false) ||
                featureBoolean(KEY_BLOCK_INTL_STORY_DOUYIN_INIT, false) ||
                featureBoolean(KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET, false) ||
                featureBoolean(KEY_DELAY_INTL_FLOAT_VIEW_STARTUP, false) ||
                featureBoolean(KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW, false) ||
                featureBoolean(KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND, false) ||
                featureBoolean(KEY_BLOCK_INTL_ALBUM_AI_INIT, false)

        return SettingsSnapshot(
            isDetailedLoggingEnabled = p.getBoolean(KEY_ENABLE_DETAILED_LOGGING, false),
            isExperimentalDexKitEnabled = p.getBoolean(KEY_ENABLE_EXPERIMENTAL_DEXKIT, false) &&
                doesCurrentHostSupportExperimentalDexKit(),
            isSplashInterstitialBlockEnabled = featureBoolean(KEY_BLOCK_SPLASH_INTERSTITIAL, false),
            isHotStartSplashRemoveEnabled = featureBoolean(KEY_REMOVE_HOT_START_SPLASH, false),
            isInAppDialogBlocked = featureBoolean(KEY_BLOCK_IN_APP_DIALOG, false),
            isUpdateDialogBlocked = featureBoolean(KEY_BLOCK_UPDATE_DIALOG, false),
            isFullScreenBackupBlocked = featureBoolean(KEY_BLOCK_FULL_SCREEN_BACKUP, false),
            isSharePushGuideBlocked = featureBoolean(KEY_BLOCK_SHARE_PUSH_GUIDE, false),
            isAppStoreReviewBlocked = featureBoolean(KEY_BLOCK_APP_STORE_REVIEW, false),
            isNonWifiDownloadDialogBlocked = featureBoolean(
                KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
                p.getBoolean(LEGACY_BLOCK_SAMSUNG_NON_WIFI_DOWNLOAD_DIALOG, false) &&
                    isFeatureAvailable(KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG),
            ),
            isNotificationPromptBlocked = featureBoolean(KEY_BLOCK_NOTIFICATION_PROMPT, false),
            isBottomAiReplaced = featureBoolean(KEY_REPLACE_BOTTOM_AI, false),
            isHomeCustomizeEnabled = featureBoolean(KEY_HOME_CUSTOMIZE, hasHomeCustomizeOptionEnabled),
            isHomeTopPromotionHidden = featureBoolean(KEY_HIDE_HOME_TOP_PROMOTION, readHomeTopPromotionHidden(p)),
            isHomeSearchPlaceholderHidden = featureBoolean(KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false),
            isHomeSearchAigcIconHidden = featureBoolean(KEY_HIDE_HOME_SEARCH_AIGC_ICON, false),
            isHomeFeedTipHidden = featureBoolean(KEY_HIDE_HOME_FEED_TIP, false),
            isHomeBannerHidden = featureBoolean(KEY_HIDE_HOME_BANNER, false),
            isHomeMemoriesSectionHidden = featureBoolean(KEY_HIDE_HOME_MEMORIES_SECTION, false),
            isHomeSaveSectionHidden = featureBoolean(KEY_HIDE_HOME_SAVE_SECTION, false),
            isHomeRecentSectionHidden = featureBoolean(KEY_HIDE_HOME_RECENT_SECTION, false),
            isFilePageCustomizeEnabled = featureBoolean(KEY_FILE_PAGE_CUSTOMIZE, hasFilePageOptionEnabled),
            isFilePageBottomSafetyTipHidden = featureBoolean(KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP, false),
            isSearchPageCustomizeEnabled = featureBoolean(KEY_SEARCH_PAGE_CUSTOMIZE, hasSearchPageOptionEnabled),
            isSearchPageAiEntryHidden = featureBoolean(KEY_HIDE_SEARCH_PAGE_AI_ENTRY, false),
            isSearchPagePlaceholderHidden = featureBoolean(KEY_HIDE_SEARCH_PAGE_PLACEHOLDER, false),
            isSearchPageRecommendHidden = featureBoolean(KEY_HIDE_SEARCH_PAGE_RECOMMEND, false),
            isSharePageCustomizeEnabled = featureBoolean(KEY_SHARE_PAGE_CUSTOMIZE, hasSharePageOptionEnabled),
            isMyPageCustomizeEnabled = featureBoolean(KEY_MY_PAGE_CUSTOMIZE, hasMyPageOptionEnabled),
            isGameCenterRemoved = featureBoolean(KEY_REMOVE_GAME_CENTER, false),
            isAboutMeBannerRemoved = featureBoolean(KEY_REMOVE_ABOUT_ME_BANNER, false),
            isMyServiceRemoved = featureBoolean(KEY_REMOVE_MY_SERVICE, false),
            isAboutMeCoinCenterBubbleHidden = featureBoolean(KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false),
            isAboutMeSignInDotHidden = featureBoolean(KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false),
            isAboutMeManageSpaceTextHidden = featureBoolean(KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false),
            isAboutMeRewardTextHidden = featureBoolean(KEY_HIDE_ABOUT_ME_REWARD_TEXT, false),
            isAboutMeAccountExitTextHidden = featureBoolean(KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false),
            isAboutMeStarSkinTextHidden = featureBoolean(KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false),
            isAboutMeFreeDataCardTextHidden = featureBoolean(KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT, false),
            isHomeFabRemoved = featureBoolean(KEY_REMOVE_HOME_FAB, false),
            isRenewButtonHidden = featureBoolean(KEY_HIDE_RENEW_BUTTON, false),
            isBottomBarBadgeBlocked = featureBoolean(KEY_BLOCK_BOTTOM_BADGE, false),
            isAlbumBackupBarBlocked = featureBoolean(KEY_BLOCK_ALBUM_BACKUP_BAR, false),
            isAboutMeAiCoinAssetHidden = featureBoolean(KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false),
            isMemberCardCustomizeEnabled = featureBoolean(KEY_MEMBER_CARD_CUSTOMIZE, hasMemberCardOptionEnabled),
            isMemberCardBackgroundReplaced = memberCardBackgroundReplaced,
            memberCardBackgroundUri = memberCardBackgroundUri,
            memberCardBackgroundBlurRadius = if (isFeatureAvailable(KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS)) {
                p.getInt(KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS, 0)
            } else {
                0
            },
            memberCardBackgroundScalePercent = p.getInt(KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
                .coerceIn(100, 300),
            memberCardBackgroundRotationDegrees = p.getInt(KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
                .floorMod360(),
            memberCardBackgroundOffsetXPermille = p.getInt(KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
                .coerceIn(-1000, 1000),
            memberCardBackgroundOffsetYPermille = p.getInt(KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
                .coerceIn(-1000, 1000),
            isMemberCardSizeAdjusted = featureBoolean(KEY_MEMBER_CARD_SIZE_ADJUST, false),
            memberCardWidthDp = if (isFeatureAvailable(KEY_MEMBER_CARD_SIZE_WIDTH_DP)) {
                p.getInt(KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0)
            } else {
                0
            },
            memberCardHeightDp = if (isFeatureAvailable(KEY_MEMBER_CARD_SIZE_HEIGHT_DP)) {
                p.getInt(KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0)
            } else {
                0
            },
            isMemberCardOperationHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_OPERATION, false),
            isMemberCardBenefitHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT, false),
            isMemberCardFirstBenefitHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT, false),
            isMemberCardSecondBenefitHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT, false),
            isMemberCardThirdBenefitHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT, false),
            isMemberCardBenefitBarHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false),
            isMemberCardSvipLevelHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false),
            isMemberCardSvipStatusHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false),
            isMemberCardRenewButtonHidden = featureBoolean(KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false),
            isIntlMemberCardSvipLevelHidden = featureBoolean(KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL, false),
            isIntlMemberCardUpgradeButtonHidden = featureBoolean(
                KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON,
                false,
            ),
            isMemberCardClickRemoved = memberCardClickRemoved,
            isMemberCardBackgroundViewedOnClick = memberCardBackgroundViewedOnClick,
            isFollowSystemNightModeEnabled = featureBoolean(KEY_FOLLOW_SYSTEM_NIGHT_MODE, false),
            isPerformanceOptimizeEnabled = featureBoolean(KEY_PERFORMANCE_OPTIMIZE, hasPerformanceOptionEnabled),
            isIntlSplashStartupAccelerateEnabled = featureBoolean(
                KEY_ACCELERATE_INTL_SPLASH_STARTUP,
                false,
            ),
            isGarbageCleanServiceRegisterDisabled = featureBoolean(
                KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER,
                false,
            ),
            isDatapackSocketRegisterDisabled = featureBoolean(
                KEY_DISABLE_DATAPACK_SOCKET_REGISTER,
                false,
            ),
            isAigcBackgroundComponentDisabled = featureBoolean(
                KEY_DISABLE_AIGC_BACKGROUND_COMPONENT,
                false,
            ),
            isDynamicPluginAutoDownloadDisabled = featureBoolean(
                KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
                false,
            ),
            isOemPushServiceDisabled = featureBoolean(
                KEY_DISABLE_OEM_PUSH_SERVICE,
                false,
            ),
            isVideoAdPreloadDisabled = featureBoolean(
                KEY_DISABLE_VIDEO_AD_PRELOAD,
                false,
            ),
            isAdSdkInitDisabled = featureBoolean(
                KEY_DISABLE_AD_SDK_INIT,
                false,
            ),
            isSwanPreloadDisabled = featureBoolean(
                KEY_DISABLE_SWAN_PRELOAD,
                false,
            ),
            isThumbnailOperatorServiceDisabled = featureBoolean(
                KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
                false,
            ),
            isIncentiveBusinessServiceDisabled = featureBoolean(
                KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE,
                false,
            ),
            isMediaBrowserServiceAutostartDisabled = featureBoolean(
                KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
                false,
            ),
            isIconResourceDownloadDisabled = featureBoolean(
                KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
                false,
            ),
            isB2fGuidancePrefetchDisabled = featureBoolean(
                KEY_DISABLE_B2F_GUIDANCE_PREFETCH,
                false,
            ),
            isIntlOfflinePackageInitBlocked = featureBoolean(
                KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT,
                false,
            ),
            isIntlFeedPreloadDelayed = featureBoolean(
                KEY_DELAY_INTL_FEED_PRELOAD,
                false,
            ),
            isIntlTaskScoreRefreshDelayed = featureBoolean(
                KEY_DELAY_INTL_TASK_SCORE_REFRESH,
                false,
            ),
            isIntlStoryDouyinInitBlocked = featureBoolean(
                KEY_BLOCK_INTL_STORY_DOUYIN_INIT,
                false,
            ),
            isIntlNonCoreDiffSocketDelayed = featureBoolean(
                KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
                false,
            ),
            isIntlFloatViewStartupDelayed = featureBoolean(
                KEY_DELAY_INTL_FLOAT_VIEW_STARTUP,
                false,
            ),
            isIntlAudioCircleStartupShowBlocked = featureBoolean(
                KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW,
                false,
            ),
            isIntlAigcWidgetBackgroundBlocked = featureBoolean(
                KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND,
                false,
            ),
            isIntlAlbumAiInitBlocked = featureBoolean(
                KEY_BLOCK_INTL_ALBUM_AI_INIT,
                false,
            ),
            isBottomBarCustomEnabled = featureBoolean(KEY_CUSTOM_BOTTOM_BAR, hasBottomBarOptionEnabled),
            isBottomBarTabFileHidden = featureBoolean(KEY_HIDE_TAB_FILE, false),
            isBottomBarTabShareHidden = featureBoolean(KEY_HIDE_TAB_SHARE, false),
            isBottomBarTabVipHidden = featureBoolean(KEY_HIDE_TAB_VIP, false),
            isBottomBarTabAigcHidden = featureBoolean(KEY_HIDE_TAB_AIGC, false),
            isBottomBarTabHomeHidden = featureBoolean(KEY_HIDE_TAB_HOME, false),
            isBottomBarTabMineHidden = featureBoolean(KEY_HIDE_TAB_MINE, false),
            areRestrictedFeaturesUnlocked = p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false),
        )
    }

    private fun doesCurrentHostSupportExperimentalDexKit(): Boolean {
        val packageName = appContext?.packageName ?: XposedCompat.currentPackageName() ?: return false
        return ConfigHostRuntime.supportsExperimentalDexKit(packageName)
    }

    private fun Int.floorMod360(): Int {
        return ((this % 360) + 360) % 360
    }

    fun isFeatureAvailable(featureKey: String): Boolean {
        return FeatureAvailabilityRuntime.isAvailable(featureKey)
    }

    fun applyFeatureAvailability(
        context: Context,
        featureStatusMap: Map<String, FeatureAvailabilityStatus>,
        refreshRuntime: Boolean = false,
    ) {
        applyFeatureAvailabilityInternal(featureStatusMap)

        if (refreshRuntime) {
            val snapshot = refreshUserSettingsSnapshot(getPrefs(context))
            logSettingsSnapshot("featureAvailability", snapshot)
        }
    }

    private fun applyFeatureAvailabilityInternal(
        featureStatusMap: Map<String, FeatureAvailabilityStatus>,
    ) {
        FeatureAvailabilityRuntime.apply(featureStatusMap)
    }

    // ── 底栏Tab定制 (Bottom Bar Simplify) ───────────────────────────────────

    const val KEY_CUSTOM_BOTTOM_BAR = FeatureKeys.KEY_CUSTOM_BOTTOM_BAR
    const val KEY_HIDE_TAB_FILE = FeatureKeys.KEY_HIDE_TAB_FILE
    const val KEY_HIDE_TAB_SHARE = FeatureKeys.KEY_HIDE_TAB_SHARE
    const val KEY_HIDE_TAB_VIP = FeatureKeys.KEY_HIDE_TAB_VIP
    const val KEY_HIDE_TAB_AIGC = FeatureKeys.KEY_HIDE_TAB_AIGC
    const val KEY_HIDE_TAB_HOME = FeatureKeys.KEY_HIDE_TAB_HOME
    const val KEY_HIDE_TAB_MINE = FeatureKeys.KEY_HIDE_TAB_MINE

    val isBottomBarCustomEnabled: Boolean get() = settingsSnapshot.isBottomBarCustomEnabled
    val isBottomBarTabFileHidden: Boolean get() = settingsSnapshot.isBottomBarTabFileHidden
    val isBottomBarTabShareHidden: Boolean get() = settingsSnapshot.isBottomBarTabShareHidden
    val isBottomBarTabVipHidden: Boolean get() = settingsSnapshot.isBottomBarTabVipHidden
    val isBottomBarTabAigcHidden: Boolean get() = settingsSnapshot.isBottomBarTabAigcHidden
    val isBottomBarTabHomeHidden: Boolean get() = settingsSnapshot.isBottomBarTabHomeHidden
    val isBottomBarTabMineHidden: Boolean get() = settingsSnapshot.isBottomBarTabMineHidden

    data class BottomBarTabSelection(
        val hideFile: Boolean = false,
        val hideShare: Boolean = false,
        val hideVip: Boolean = false,
        val hideAigc: Boolean = false,
        val hideHome: Boolean = false,
        val hideMine: Boolean = false,
    ) {
        /** 可见 Tab 数量 = 总数 - 隐藏数，至少保留 1 个 */
        val visibleCount: Int get() = 6 - listOf(hideFile, hideShare, hideVip, hideAigc, hideHome, hideMine).count { it }

        fun hasVisibleTab(): Boolean = visibleCount >= 1
    }

    fun normalizeBottomBarSelection(selection: BottomBarTabSelection): BottomBarTabSelection {
        if (selection.hasVisibleTab()) return selection
        // 全部隐藏时恢复默认：只显示首页
        return BottomBarTabSelection(
            hideFile = true, hideShare = true, hideVip = true, hideAigc = true,
            hideHome = false, hideMine = true,
        )
    }

    fun isDisclaimerAccepted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DISCLAIMER_ACCEPTED, false)
    }

    fun setDisclaimerAccepted(context: Context) {
        val p = getPrefs(context)
        if (p.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).commit()) {
            refreshUserSettingsSnapshot(p)
        }
    }

    fun setRestrictedFeaturesUnlocked(context: Context, unlocked: Boolean) {
        val p = getPrefs(context)
        if (p.edit().putBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, unlocked).commit()) {
            refreshUserSettingsSnapshot(p)
        }
    }

    fun resetUserSettings(context: Context): Boolean {
        val p = getPrefs(context)
        val success = p.edit()
            .clear()
            .putInt(KEY_USER_SETTINGS_VERSION_CODE, BuildConfig.VERSION_CODE)
            .commit()
        if (success) {
            val snapshot = refreshUserSettingsSnapshot(p)
            logSettingsSnapshot("resetUserSettings", snapshot)
        }
        return success
    }

    fun userSettingsPrefsNameFor(packageName: String): String {
        return namespacedUserSettingsPrefsName(packageName)
    }

    fun moduleStatePrefsNameFor(packageName: String): String {
        return namespacedModuleStatePrefsName(packageName)
    }

    private fun namespacedUserSettingsPrefsName(packageName: String): String {
        return "${USER_SETTINGS_PREFS_NAME}_${sanitizePackageName(packageName)}"
    }

    private fun namespacedModuleStatePrefsName(packageName: String): String {
        return "${MODULE_STATE_PREFS_NAME}_${sanitizePackageName(packageName)}"
    }

    private fun sanitizePackageName(packageName: String): String {
        val fallbackPackageName = ConfigHostRuntime.canonicalPackageNameOrSelf(packageName)
        val effectivePackageName = packageName.ifBlank { fallbackPackageName.ifBlank { "unknown" } }
        return effectivePackageName
            .replace(':', '_')
            .replace('.', '_')
    }

    private fun migrateLegacyPrefsIfNeeded(
        context: Context,
        namespacedPrefs: SharedPreferences,
        targetPrefsName: String,
    ) {
        if (namespacedPrefs.all.isNotEmpty()) return
        if (targetPrefsName == PREFS_NAME) return
        val legacyPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyAll = legacyPrefs.all
        if (legacyAll.isEmpty()) return

        val editor = namespacedPrefs.edit()
        for ((key, value) in legacyAll) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
        if (editor.commit()) {
            XposedCompat.log("[ConfigManager] migrated legacy prefs to $targetPrefsName")
        }
    }
}
