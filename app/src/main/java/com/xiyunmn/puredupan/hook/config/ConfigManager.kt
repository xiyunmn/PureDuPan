package com.xiyunmn.puredupan.hook.config

import android.content.Context
import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityState
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus
import com.xiyunmn.puredupan.hook.core.XposedCompat

object ConfigManager {
    const val USER_SETTINGS_PREFS_NAME = "wangpan_user_settings"
    const val MODULE_STATE_PREFS_NAME = "wangpan_module_state"
    const val PREFS_NAME = USER_SETTINGS_PREFS_NAME
    private const val KEY_USER_SETTINGS_VERSION_CODE = "user_settings_version_code"

    const val KEY_ENABLE_DETAILED_LOGGING = "enable_detailed_logging"
    const val KEY_BLOCK_SPLASH_INTERSTITIAL = "block_splash_interstitial"
    const val KEY_BLOCK_IN_APP_DIALOG = "block_in_app_dialog"
    const val KEY_BLOCK_UPDATE_DIALOG = "block_update_dialog"
    const val KEY_BLOCK_FULL_SCREEN_BACKUP = "block_full_screen_backup"
    const val KEY_BLOCK_SHARE_PUSH_GUIDE = "block_share_push_guide"
    const val KEY_BLOCK_APP_STORE_REVIEW = "block_app_store_review"
    const val KEY_REPLACE_BOTTOM_AI = "replace_bottom_ai"
    const val KEY_HOME_CUSTOMIZE = "home_customize"
    private const val KEY_HOME_TOP_PROMOTION_LEGACY = "remove_top_ai"
    const val KEY_HIDE_HOME_TOP_PROMOTION = "hide_home_top_promotion"
    const val KEY_HIDE_HOME_SEARCH_PLACEHOLDER = "hide_home_search_placeholder"
    const val KEY_HIDE_HOME_SEARCH_AIGC_ICON = "hide_home_search_aigc_icon"
    const val KEY_HIDE_HOME_FEED_TIP = "hide_home_feed_tip"
    const val KEY_HIDE_HOME_MEMORIES_SECTION = "hide_home_memories_section"
    const val KEY_HIDE_HOME_SAVE_SECTION = "hide_home_save_section"
    const val KEY_HIDE_HOME_RECENT_SECTION = "hide_home_recent_section"
    const val KEY_SHARE_PAGE_CUSTOMIZE = "share_page_customize"
    const val KEY_MY_PAGE_CUSTOMIZE = "my_page_customize"
    const val KEY_REMOVE_GAME_CENTER = "remove_game_center"
    const val KEY_REMOVE_ABOUT_ME_BANNER = "remove_about_me_banner"
    const val KEY_REMOVE_MY_SERVICE = "remove_my_service"
    const val KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE = "hide_about_me_coin_center_bubble"
    const val KEY_HIDE_ABOUT_ME_SIGN_IN_DOT = "hide_about_me_sign_in_dot"
    const val KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT = "hide_about_me_manage_space_text"
    const val KEY_HIDE_ABOUT_ME_REWARD_TEXT = "hide_about_me_reward_text"
    const val KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT = "hide_about_me_account_exit_text"
    const val KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT = "hide_about_me_star_skin_text"
    const val KEY_REMOVE_HOME_FAB = "remove_home_fab"
    const val KEY_HIDE_RENEW_BUTTON = "hide_renew_button"
    const val KEY_BLOCK_BOTTOM_BADGE = "block_bottom_badge"
    const val KEY_BLOCK_ALBUM_BACKUP_BAR = "block_album_backup_bar"
    private const val KEY_HIDE_MEMBER_CARD_BACKGROUND_LEGACY = "hide_member_card_background"
    const val KEY_HIDE_ABOUT_ME_AI_COIN_ASSET = "hide_about_me_ai_coin_asset"
    const val KEY_MEMBER_CARD_CUSTOMIZE = "member_card_customize"
    const val KEY_REPLACE_MEMBER_CARD_BACKGROUND = "replace_member_card_background"
    const val KEY_MEMBER_CARD_BACKGROUND_URI = "member_card_background_uri"
    const val KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS = "member_card_background_blur_radius"
    const val KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT = "member_card_background_scale_percent"
    const val KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES = "member_card_background_rotation_degrees"
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE = "member_card_background_offset_x_permille"
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE = "member_card_background_offset_y_permille"
    const val KEY_MEMBER_CARD_SIZE_ADJUST = "member_card_size_adjust"
    const val KEY_MEMBER_CARD_SIZE_WIDTH_DP = "member_card_size_width_dp"
    const val KEY_MEMBER_CARD_SIZE_HEIGHT_DP = "member_card_size_height_dp"
    const val KEY_MEMBER_CARD_DEFAULT_WIDTH_PX = "member_card_default_width_px"
    const val KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX = "member_card_default_height_px"
    const val KEY_HIDE_MEMBER_CARD_OPERATION = "hide_member_card_operation"
    const val KEY_HIDE_MEMBER_CARD_BENEFIT = "hide_member_card_benefit"
    const val KEY_HIDE_MEMBER_CARD_BENEFIT_BAR = "hide_member_card_benefit_bar"
    const val KEY_HIDE_MEMBER_CARD_SVIP_LEVEL = "hide_member_card_svip_level"
    const val KEY_HIDE_MEMBER_CARD_SVIP_STATUS = "hide_member_card_svip_status"
    const val KEY_HIDE_MEMBER_CARD_RENEW_BUTTON = "hide_member_card_renew_button"
    const val KEY_REMOVE_MEMBER_CARD_CLICK = "remove_member_card_click"
    const val KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK = "view_member_card_background_on_click"
    const val KEY_FOLLOW_SYSTEM_NIGHT_MODE = "follow_system_night_mode"
    const val KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER = "disable_garbage_clean_service_register"
    const val KEY_DISABLE_DATAPACK_SOCKET_REGISTER = "disable_datapack_socket_register"
    const val KEY_DISABLE_AIGC_BACKGROUND_COMPONENT = "disable_aigc_background_component"
    const val KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD = "disable_dynamic_plugin_auto_download"
    const val KEY_DISABLE_OEM_PUSH_SERVICE = "disable_oem_push_service"
    const val KEY_DISABLE_VIDEO_AD_PRELOAD = "disable_video_ad_preload"
    const val KEY_DISABLE_AD_SDK_INIT = "disable_ad_sdk_init"
    const val KEY_DISABLE_SWAN_PRELOAD = "disable_swan_preload"
    const val KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE = "disable_thumbnail_operator_service"
    const val KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE = "disable_incentive_business_service"
    const val KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART = "disable_media_browser_service_autostart"
    const val KEY_DISABLE_ICON_RESOURCE_DOWNLOAD = "disable_icon_resource_download"
    const val KEY_DISABLE_B2F_GUIDANCE_PREFETCH = "disable_b2f_guidance_prefetch"
    const val KEY_PERFORMANCE_OPTIMIZE = "performance_optimize"
    const val KEY_RESTRICTED_FEATURES_UNLOCKED = "restricted_features_unlocked"
    const val KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var featureAvailability: Map<String, Boolean> = emptyMap()
    @Volatile private var settingsSnapshot: SettingsSnapshot = SettingsSnapshot()
    @Volatile private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    val isDetailedLoggingEnabled: Boolean get() = settingsSnapshot.isDetailedLoggingEnabled
    val isSplashInterstitialBlockEnabled: Boolean get() = settingsSnapshot.isSplashInterstitialBlockEnabled
    val isInAppDialogBlocked: Boolean get() = settingsSnapshot.isInAppDialogBlocked
    val isUpdateDialogBlocked: Boolean get() = settingsSnapshot.isUpdateDialogBlocked
    val isFullScreenBackupBlocked: Boolean get() = settingsSnapshot.isFullScreenBackupBlocked
    val isSharePushGuideBlocked: Boolean get() = settingsSnapshot.isSharePushGuideBlocked
    val isAppStoreReviewBlocked: Boolean get() = settingsSnapshot.isAppStoreReviewBlocked
    val isBottomAiReplaced: Boolean get() = settingsSnapshot.isBottomAiReplaced
    val isHomeCustomizeEnabled: Boolean get() = settingsSnapshot.isHomeCustomizeEnabled
    val isHomeTopPromotionHidden: Boolean get() = settingsSnapshot.isHomeTopPromotionHidden
    val isHomeSearchPlaceholderHidden: Boolean get() = settingsSnapshot.isHomeSearchPlaceholderHidden
    val isHomeSearchAigcIconHidden: Boolean get() = settingsSnapshot.isHomeSearchAigcIconHidden
    val isHomeFeedTipHidden: Boolean get() = settingsSnapshot.isHomeFeedTipHidden
    val isHomeMemoriesSectionHidden: Boolean get() = settingsSnapshot.isHomeMemoriesSectionHidden
    val isHomeSaveSectionHidden: Boolean get() = settingsSnapshot.isHomeSaveSectionHidden
    val isHomeRecentSectionHidden: Boolean get() = settingsSnapshot.isHomeRecentSectionHidden
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
    val isMemberCardBenefitBarHidden: Boolean get() = settingsSnapshot.isMemberCardBenefitBarHidden
    val isMemberCardSvipLevelHidden: Boolean get() = settingsSnapshot.isMemberCardSvipLevelHidden
    val isMemberCardSvipStatusHidden: Boolean get() = settingsSnapshot.isMemberCardSvipStatusHidden
    val isMemberCardRenewButtonHidden: Boolean get() = settingsSnapshot.isMemberCardRenewButtonHidden
    val isMemberCardClickRemoved: Boolean get() = settingsSnapshot.isMemberCardClickRemoved
    val isMemberCardBackgroundViewedOnClick: Boolean get() = settingsSnapshot.isMemberCardBackgroundViewedOnClick
    val isFollowSystemNightModeEnabled: Boolean get() = settingsSnapshot.isFollowSystemNightModeEnabled
    val isPerformanceOptimizeEnabled: Boolean
        get() = settingsSnapshot.isPerformanceOptimizeEnabled
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
    val areRestrictedFeaturesUnlocked: Boolean
        get() = settingsSnapshot.areRestrictedFeaturesUnlocked

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val appCtx = context.applicationContext ?: context
            val p = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            appContext = appCtx
            prefs = p
            XposedCompat.initializeFileLogging(appCtx)
            ensureUserSettingsVersion(p)

            val snapshot = refreshUserSettingsSnapshot(p)
            logSettingsSnapshot("init", snapshot)
            ensurePrefsListener(p)
        }
    }

    fun snapshot(): SettingsSnapshot = settingsSnapshot

    fun readHomeTopPromotionHidden(p: SharedPreferences): Boolean {
        return p.getBoolean(
            KEY_HIDE_HOME_TOP_PROMOTION,
            p.getBoolean(KEY_HOME_TOP_PROMOTION_LEGACY, false),
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
                val snapshot = refreshUserSettingsSnapshot(sharedPrefs)
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
        return appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getModuleStatePrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext ?: context
        return appCtx.getSharedPreferences(MODULE_STATE_PREFS_NAME, Context.MODE_PRIVATE)
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
        val memberCardBackgroundReplaced = p.getBoolean(
            KEY_REPLACE_MEMBER_CARD_BACKGROUND,
            p.getBoolean(KEY_HIDE_MEMBER_CARD_BACKGROUND_LEGACY, false),
        )
        val memberCardBackgroundUri = p.getString(KEY_MEMBER_CARD_BACKGROUND_URI, null)
        val memberCardClickRemoved = p.getBoolean(KEY_REMOVE_MEMBER_CARD_CLICK, false)
        val memberCardBackgroundViewedOnClick =
            !memberCardClickRemoved &&
                memberCardBackgroundReplaced &&
                !memberCardBackgroundUri.isNullOrBlank() &&
                p.getBoolean(KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK, false)
        val hasMemberCardOptionEnabled =
            memberCardBackgroundReplaced ||
                p.getBoolean(KEY_MEMBER_CARD_SIZE_ADJUST, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_OPERATION, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false) ||
                p.getBoolean(KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false) ||
                memberCardClickRemoved ||
                memberCardBackgroundViewedOnClick
        val hasHomeCustomizeOptionEnabled =
            readHomeTopPromotionHidden(p) ||
                p.getBoolean(KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false) ||
                p.getBoolean(KEY_HIDE_HOME_SEARCH_AIGC_ICON, false) ||
                p.getBoolean(KEY_HIDE_HOME_FEED_TIP, false) ||
                p.getBoolean(KEY_HIDE_HOME_MEMORIES_SECTION, false) ||
                p.getBoolean(KEY_HIDE_HOME_SAVE_SECTION, false) ||
                p.getBoolean(KEY_HIDE_HOME_RECENT_SECTION, false)
        val hasSharePageOptionEnabled =
            p.getBoolean(KEY_REMOVE_HOME_FAB, false)
        val hasMyPageOptionEnabled =
            p.getBoolean(KEY_HIDE_RENEW_BUTTON, false) ||
                p.getBoolean(KEY_REMOVE_GAME_CENTER, false) ||
                p.getBoolean(KEY_REMOVE_ABOUT_ME_BANNER, false) ||
                p.getBoolean(KEY_REMOVE_MY_SERVICE, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_REWARD_TEXT, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false) ||
                p.getBoolean(KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false)
        val hasBottomBarOptionEnabled =
            p.getBoolean(KEY_REPLACE_BOTTOM_AI, false) ||
                p.getBoolean(KEY_BLOCK_BOTTOM_BADGE, false) ||
                p.getBoolean(KEY_HIDE_TAB_FILE, false) ||
                p.getBoolean(KEY_HIDE_TAB_SHARE, false) ||
                p.getBoolean(KEY_HIDE_TAB_VIP, false) ||
                p.getBoolean(KEY_HIDE_TAB_HOME, false) ||
                p.getBoolean(KEY_HIDE_TAB_MINE, false)
        val hasPerformanceOptionEnabled =
            p.getBoolean(KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER, false) ||
                p.getBoolean(KEY_DISABLE_DATAPACK_SOCKET_REGISTER, false) ||
                p.getBoolean(KEY_DISABLE_AIGC_BACKGROUND_COMPONENT, false) ||
                p.getBoolean(KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD, false) ||
                p.getBoolean(KEY_DISABLE_OEM_PUSH_SERVICE, false) ||
                p.getBoolean(KEY_DISABLE_VIDEO_AD_PRELOAD, false) ||
                p.getBoolean(KEY_DISABLE_AD_SDK_INIT, false) ||
                p.getBoolean(KEY_DISABLE_SWAN_PRELOAD, false) ||
                p.getBoolean(KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE, false) ||
                p.getBoolean(KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE, false) ||
                p.getBoolean(KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART, false) ||
                p.getBoolean(KEY_DISABLE_ICON_RESOURCE_DOWNLOAD, false) ||
                p.getBoolean(KEY_DISABLE_B2F_GUIDANCE_PREFETCH, false)

        return SettingsSnapshot(
            isDetailedLoggingEnabled = featureBoolean(KEY_ENABLE_DETAILED_LOGGING),
            isSplashInterstitialBlockEnabled = p.getBoolean(KEY_BLOCK_SPLASH_INTERSTITIAL, false),
            isInAppDialogBlocked = p.getBoolean(KEY_BLOCK_IN_APP_DIALOG, false),
            isUpdateDialogBlocked = p.getBoolean(KEY_BLOCK_UPDATE_DIALOG, false),
            isFullScreenBackupBlocked = p.getBoolean(KEY_BLOCK_FULL_SCREEN_BACKUP, false),
            isSharePushGuideBlocked = p.getBoolean(KEY_BLOCK_SHARE_PUSH_GUIDE, false),
            isAppStoreReviewBlocked = p.getBoolean(KEY_BLOCK_APP_STORE_REVIEW, false),
            isBottomAiReplaced = p.getBoolean(KEY_REPLACE_BOTTOM_AI, false),
            isHomeCustomizeEnabled = p.getBoolean(KEY_HOME_CUSTOMIZE, hasHomeCustomizeOptionEnabled),
            isHomeTopPromotionHidden = readHomeTopPromotionHidden(p),
            isHomeSearchPlaceholderHidden = p.getBoolean(KEY_HIDE_HOME_SEARCH_PLACEHOLDER, false),
            isHomeSearchAigcIconHidden = p.getBoolean(KEY_HIDE_HOME_SEARCH_AIGC_ICON, false),
            isHomeFeedTipHidden = p.getBoolean(KEY_HIDE_HOME_FEED_TIP, false),
            isHomeMemoriesSectionHidden = p.getBoolean(KEY_HIDE_HOME_MEMORIES_SECTION, false),
            isHomeSaveSectionHidden = p.getBoolean(KEY_HIDE_HOME_SAVE_SECTION, false),
            isHomeRecentSectionHidden = p.getBoolean(KEY_HIDE_HOME_RECENT_SECTION, false),
            isSharePageCustomizeEnabled = p.getBoolean(KEY_SHARE_PAGE_CUSTOMIZE, hasSharePageOptionEnabled),
            isMyPageCustomizeEnabled = p.getBoolean(KEY_MY_PAGE_CUSTOMIZE, hasMyPageOptionEnabled),
            isGameCenterRemoved = p.getBoolean(KEY_REMOVE_GAME_CENTER, false),
            isAboutMeBannerRemoved = p.getBoolean(KEY_REMOVE_ABOUT_ME_BANNER, false),
            isMyServiceRemoved = p.getBoolean(KEY_REMOVE_MY_SERVICE, false),
            isAboutMeCoinCenterBubbleHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE, false),
            isAboutMeSignInDotHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_SIGN_IN_DOT, false),
            isAboutMeManageSpaceTextHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT, false),
            isAboutMeRewardTextHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_REWARD_TEXT, false),
            isAboutMeAccountExitTextHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT, false),
            isAboutMeStarSkinTextHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT, false),
            isHomeFabRemoved = p.getBoolean(KEY_REMOVE_HOME_FAB, false),
            isRenewButtonHidden = p.getBoolean(KEY_HIDE_RENEW_BUTTON, false),
            isBottomBarBadgeBlocked = p.getBoolean(KEY_BLOCK_BOTTOM_BADGE, false),
            isAlbumBackupBarBlocked = p.getBoolean(KEY_BLOCK_ALBUM_BACKUP_BAR, false),
            isAboutMeAiCoinAssetHidden = p.getBoolean(KEY_HIDE_ABOUT_ME_AI_COIN_ASSET, false),
            isMemberCardCustomizeEnabled = p.getBoolean(KEY_MEMBER_CARD_CUSTOMIZE, hasMemberCardOptionEnabled),
            isMemberCardBackgroundReplaced = memberCardBackgroundReplaced,
            memberCardBackgroundUri = memberCardBackgroundUri,
            memberCardBackgroundBlurRadius = p.getInt(KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS, 0),
            memberCardBackgroundScalePercent = p.getInt(KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT, 100)
                .coerceIn(100, 300),
            memberCardBackgroundRotationDegrees = p.getInt(KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES, 0)
                .floorMod360(),
            memberCardBackgroundOffsetXPermille = p.getInt(KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE, 0)
                .coerceIn(-1000, 1000),
            memberCardBackgroundOffsetYPermille = p.getInt(KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE, 0)
                .coerceIn(-1000, 1000),
            isMemberCardSizeAdjusted = p.getBoolean(KEY_MEMBER_CARD_SIZE_ADJUST, false),
            memberCardWidthDp = p.getInt(KEY_MEMBER_CARD_SIZE_WIDTH_DP, 0),
            memberCardHeightDp = p.getInt(KEY_MEMBER_CARD_SIZE_HEIGHT_DP, 0),
            isMemberCardOperationHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_OPERATION, false),
            isMemberCardBenefitHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT, false),
            isMemberCardBenefitBarHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_BENEFIT_BAR, false),
            isMemberCardSvipLevelHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_SVIP_LEVEL, false),
            isMemberCardSvipStatusHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_SVIP_STATUS, false),
            isMemberCardRenewButtonHidden = p.getBoolean(KEY_HIDE_MEMBER_CARD_RENEW_BUTTON, false),
            isMemberCardClickRemoved = memberCardClickRemoved,
            isMemberCardBackgroundViewedOnClick = memberCardBackgroundViewedOnClick,
            isFollowSystemNightModeEnabled = p.getBoolean(KEY_FOLLOW_SYSTEM_NIGHT_MODE, false),
            isPerformanceOptimizeEnabled = p.getBoolean(KEY_PERFORMANCE_OPTIMIZE, hasPerformanceOptionEnabled),
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
            isBottomBarCustomEnabled = p.getBoolean(KEY_CUSTOM_BOTTOM_BAR, hasBottomBarOptionEnabled),
            isBottomBarTabFileHidden = p.getBoolean(KEY_HIDE_TAB_FILE, false),
            isBottomBarTabShareHidden = p.getBoolean(KEY_HIDE_TAB_SHARE, false),
            isBottomBarTabVipHidden = p.getBoolean(KEY_HIDE_TAB_VIP, false),
            isBottomBarTabHomeHidden = p.getBoolean(KEY_HIDE_TAB_HOME, false),
            isBottomBarTabMineHidden = p.getBoolean(KEY_HIDE_TAB_MINE, false),
            areRestrictedFeaturesUnlocked = p.getBoolean(KEY_RESTRICTED_FEATURES_UNLOCKED, false),
        )
    }

    private fun Int.floorMod360(): Int {
        return ((this % 360) + 360) % 360
    }

    fun isFeatureAvailable(featureKey: String): Boolean {
        return featureAvailability[featureKey] != false
    }

    fun applyFeatureAvailability(
        context: Context,
        featureStatusMap: Map<String, FeatureAvailabilityStatus>,
        refreshRuntime: Boolean = false,
    ) {
        if (featureStatusMap.isEmpty()) return
        featureAvailability = featureStatusMap.mapValues { (_, status) ->
            status.state != FeatureAvailabilityState.DISABLED
        }

        if (refreshRuntime) {
            val snapshot = refreshUserSettingsSnapshot(getPrefs(context))
            logSettingsSnapshot("featureAvailability", snapshot)
        }
    }

    // ── 底栏定制 (Bottom Bar Simplify) ──────────────────────────────────────

    const val KEY_CUSTOM_BOTTOM_BAR = "custom_bottom_bar"
    const val KEY_HIDE_TAB_FILE = "hide_tab_file"
    const val KEY_HIDE_TAB_SHARE = "hide_tab_share"
    const val KEY_HIDE_TAB_VIP = "hide_tab_vip"
    const val KEY_HIDE_TAB_HOME = "hide_tab_home"
    const val KEY_HIDE_TAB_MINE = "hide_tab_mine"

    val isBottomBarCustomEnabled: Boolean get() = settingsSnapshot.isBottomBarCustomEnabled
    val isBottomBarTabFileHidden: Boolean get() = settingsSnapshot.isBottomBarTabFileHidden
    val isBottomBarTabShareHidden: Boolean get() = settingsSnapshot.isBottomBarTabShareHidden
    val isBottomBarTabVipHidden: Boolean get() = settingsSnapshot.isBottomBarTabVipHidden
    val isBottomBarTabHomeHidden: Boolean get() = settingsSnapshot.isBottomBarTabHomeHidden
    val isBottomBarTabMineHidden: Boolean get() = settingsSnapshot.isBottomBarTabMineHidden

    data class BottomBarTabSelection(
        val hideFile: Boolean = false,
        val hideShare: Boolean = false,
        val hideVip: Boolean = false,
        val hideHome: Boolean = false,
        val hideMine: Boolean = false,
    ) {
        /** 可见 Tab 数量 = 总数 - 隐藏数，至少保留 1 个 */
        val visibleCount: Int get() = 5 - listOf(hideFile, hideShare, hideVip, hideHome, hideMine).count { it }

        fun hasVisibleTab(): Boolean = visibleCount >= 1
    }

    fun normalizeBottomBarSelection(selection: BottomBarTabSelection): BottomBarTabSelection {
        if (selection.hasVisibleTab()) return selection
        // 全部隐藏时恢复默认：只显示首页
        return BottomBarTabSelection(
            hideFile = true, hideShare = true, hideVip = true,
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
}
