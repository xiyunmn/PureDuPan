package com.xiyunmn.puredupan.hook.settings.registry

import android.content.Context
import android.content.SharedPreferences
import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus

internal object SettingsUserState {
    const val KEY_ENABLE_DETAILED_LOGGING = ConfigManager.KEY_ENABLE_DETAILED_LOGGING
    const val KEY_ENABLE_EXPERIMENTAL_DEXKIT = ConfigManager.KEY_ENABLE_EXPERIMENTAL_DEXKIT
    const val KEY_BLOCK_SPLASH_INTERSTITIAL = ConfigManager.KEY_BLOCK_SPLASH_INTERSTITIAL
    const val KEY_REMOVE_HOT_START_SPLASH = ConfigManager.KEY_REMOVE_HOT_START_SPLASH
    const val KEY_BLOCK_IN_APP_DIALOG = ConfigManager.KEY_BLOCK_IN_APP_DIALOG
    const val KEY_BLOCK_UPDATE_DIALOG = ConfigManager.KEY_BLOCK_UPDATE_DIALOG
    const val KEY_BLOCK_FULL_SCREEN_BACKUP = ConfigManager.KEY_BLOCK_FULL_SCREEN_BACKUP
    const val KEY_BLOCK_SHARE_PUSH_GUIDE = ConfigManager.KEY_BLOCK_SHARE_PUSH_GUIDE
    const val KEY_BLOCK_APP_STORE_REVIEW = ConfigManager.KEY_BLOCK_APP_STORE_REVIEW
    const val KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG =
        ConfigManager.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG
    const val KEY_BLOCK_NOTIFICATION_PROMPT = ConfigManager.KEY_BLOCK_NOTIFICATION_PROMPT
    const val KEY_REPLACE_BOTTOM_AI = ConfigManager.KEY_REPLACE_BOTTOM_AI
    const val KEY_HOME_CUSTOMIZE = ConfigManager.KEY_HOME_CUSTOMIZE
    const val KEY_HIDE_HOME_TOP_PROMOTION = ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION
    const val KEY_HIDE_HOME_SEARCH_PLACEHOLDER = ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER
    const val KEY_HIDE_HOME_SEARCH_AIGC_ICON = ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON
    const val KEY_HIDE_HOME_TOOLBAR = ConfigManager.KEY_HIDE_HOME_TOOLBAR
    const val KEY_HIDE_HOME_FEED_TIP = ConfigManager.KEY_HIDE_HOME_FEED_TIP
    const val KEY_HIDE_HOME_BANNER = ConfigManager.KEY_HIDE_HOME_BANNER
    const val KEY_HIDE_HOME_MEMORIES_SECTION = ConfigManager.KEY_HIDE_HOME_MEMORIES_SECTION
    const val KEY_HIDE_HOME_SAVE_SECTION = ConfigManager.KEY_HIDE_HOME_SAVE_SECTION
    const val KEY_HIDE_HOME_RECENT_SECTION = ConfigManager.KEY_HIDE_HOME_RECENT_SECTION
    const val KEY_FILE_PAGE_CUSTOMIZE = ConfigManager.KEY_FILE_PAGE_CUSTOMIZE
    const val KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP =
        ConfigManager.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP
    const val KEY_SEARCH_PAGE_CUSTOMIZE = ConfigManager.KEY_SEARCH_PAGE_CUSTOMIZE
    const val KEY_HIDE_SEARCH_PAGE_AI_ENTRY = ConfigManager.KEY_HIDE_SEARCH_PAGE_AI_ENTRY
    const val KEY_HIDE_SEARCH_PAGE_PLACEHOLDER = ConfigManager.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER
    const val KEY_HIDE_SEARCH_PAGE_RECOMMEND = ConfigManager.KEY_HIDE_SEARCH_PAGE_RECOMMEND
    const val KEY_SHARE_PAGE_CUSTOMIZE = ConfigManager.KEY_SHARE_PAGE_CUSTOMIZE
    const val KEY_MY_PAGE_CUSTOMIZE = ConfigManager.KEY_MY_PAGE_CUSTOMIZE
    const val KEY_REMOVE_GAME_CENTER = ConfigManager.KEY_REMOVE_GAME_CENTER
    const val KEY_REMOVE_ABOUT_ME_BANNER = ConfigManager.KEY_REMOVE_ABOUT_ME_BANNER
    const val KEY_REMOVE_MY_SERVICE = ConfigManager.KEY_REMOVE_MY_SERVICE
    const val KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE = ConfigManager.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE
    const val KEY_HIDE_ABOUT_ME_SIGN_IN_DOT = ConfigManager.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT
    const val KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT = ConfigManager.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT
    const val KEY_HIDE_ABOUT_ME_REWARD_TEXT = ConfigManager.KEY_HIDE_ABOUT_ME_REWARD_TEXT
    const val KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT = ConfigManager.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT
    const val KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT = ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT
    const val KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT = ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT
    const val KEY_REMOVE_HOME_FAB = ConfigManager.KEY_REMOVE_HOME_FAB
    const val KEY_HIDE_RENEW_BUTTON = ConfigManager.KEY_HIDE_RENEW_BUTTON
    const val KEY_BLOCK_BOTTOM_BADGE = ConfigManager.KEY_BLOCK_BOTTOM_BADGE
    const val KEY_BLOCK_ALBUM_BACKUP_BAR = ConfigManager.KEY_BLOCK_ALBUM_BACKUP_BAR
    const val KEY_HIDE_ABOUT_ME_AI_COIN_ASSET = ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET
    const val KEY_MEMBER_CARD_CUSTOMIZE = ConfigManager.KEY_MEMBER_CARD_CUSTOMIZE
    const val KEY_REPLACE_MEMBER_CARD_BACKGROUND = ConfigManager.KEY_REPLACE_MEMBER_CARD_BACKGROUND
    const val KEY_MEMBER_CARD_BACKGROUND_URI = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_URI
    const val KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS
    const val KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT
    const val KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE
    const val KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE = ConfigManager.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE
    const val KEY_MEMBER_CARD_SIZE_ADJUST = ConfigManager.KEY_MEMBER_CARD_SIZE_ADJUST
    const val KEY_MEMBER_CARD_SIZE_WIDTH_DP = ConfigManager.KEY_MEMBER_CARD_SIZE_WIDTH_DP
    const val KEY_MEMBER_CARD_SIZE_HEIGHT_DP = ConfigManager.KEY_MEMBER_CARD_SIZE_HEIGHT_DP
    const val KEY_MEMBER_CARD_DEFAULT_WIDTH_PX = ConfigManager.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX
    const val KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX = ConfigManager.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX
    const val KEY_HIDE_MEMBER_CARD_OPERATION = ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION
    const val KEY_HIDE_MEMBER_CARD_BENEFIT = ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT = ConfigManager.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT = ConfigManager.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT = ConfigManager.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT
    const val KEY_HIDE_MEMBER_CARD_BENEFIT_BAR = ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR
    const val KEY_HIDE_MEMBER_CARD_SVIP_LEVEL = ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL
    const val KEY_HIDE_MEMBER_CARD_SVIP_STATUS = ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS
    const val KEY_HIDE_MEMBER_CARD_RENEW_BUTTON = ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON
    const val KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL = ConfigManager.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL
    const val KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON = ConfigManager.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON
    const val KEY_REMOVE_MEMBER_CARD_CLICK = ConfigManager.KEY_REMOVE_MEMBER_CARD_CLICK
    const val KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK = ConfigManager.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK
    const val KEY_FOLLOW_SYSTEM_NIGHT_MODE = ConfigManager.KEY_FOLLOW_SYSTEM_NIGHT_MODE
    const val KEY_ACCELERATE_INTL_SPLASH_STARTUP = ConfigManager.KEY_ACCELERATE_INTL_SPLASH_STARTUP
    const val KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER =
        ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER
    const val KEY_DISABLE_DATAPACK_SOCKET_REGISTER = ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER
    const val KEY_DISABLE_AIGC_BACKGROUND_COMPONENT = ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT
    const val KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD = ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD
    const val KEY_DISABLE_OEM_PUSH_SERVICE = ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE
    const val KEY_DISABLE_VIDEO_AD_PRELOAD = ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD
    const val KEY_DISABLE_AD_SDK_INIT = ConfigManager.KEY_DISABLE_AD_SDK_INIT
    const val KEY_DISABLE_SWAN_PRELOAD = ConfigManager.KEY_DISABLE_SWAN_PRELOAD
    const val KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE = ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE
    const val KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE = ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE
    const val KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART =
        ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART
    const val KEY_DISABLE_ICON_RESOURCE_DOWNLOAD = ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD
    const val KEY_DISABLE_B2F_GUIDANCE_PREFETCH = ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH
    const val KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT = ConfigManager.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT
    const val KEY_DELAY_INTL_FEED_PRELOAD = ConfigManager.KEY_DELAY_INTL_FEED_PRELOAD
    const val KEY_DELAY_INTL_TASK_SCORE_REFRESH = ConfigManager.KEY_DELAY_INTL_TASK_SCORE_REFRESH
    const val KEY_BLOCK_INTL_STORY_DOUYIN_INIT = ConfigManager.KEY_BLOCK_INTL_STORY_DOUYIN_INIT
    const val KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET = ConfigManager.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET
    const val KEY_DELAY_INTL_FLOAT_VIEW_STARTUP = ConfigManager.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP
    const val KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW =
        ConfigManager.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW
    const val KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND = ConfigManager.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND
    const val KEY_BLOCK_INTL_ALBUM_AI_INIT = ConfigManager.KEY_BLOCK_INTL_ALBUM_AI_INIT
    const val KEY_PERFORMANCE_OPTIMIZE = ConfigManager.KEY_PERFORMANCE_OPTIMIZE
    const val KEY_CUSTOM_BOTTOM_BAR = ConfigManager.KEY_CUSTOM_BOTTOM_BAR
    const val KEY_HIDE_TAB_FILE = ConfigManager.KEY_HIDE_TAB_FILE
    const val KEY_HIDE_TAB_SHARE = ConfigManager.KEY_HIDE_TAB_SHARE
    const val KEY_HIDE_TAB_VIP = ConfigManager.KEY_HIDE_TAB_VIP
    const val KEY_HIDE_TAB_AIGC = ConfigManager.KEY_HIDE_TAB_AIGC
    const val KEY_HIDE_TAB_HOME = ConfigManager.KEY_HIDE_TAB_HOME
    const val KEY_HIDE_TAB_MINE = ConfigManager.KEY_HIDE_TAB_MINE

    val areRestrictedFeaturesUnlocked: Boolean
        get() = ConfigManager.areRestrictedFeaturesUnlocked

    data class BottomBarTabSelection(
        val hideFile: Boolean = false,
        val hideShare: Boolean = false,
        val hideVip: Boolean = false,
        val hideAigc: Boolean = false,
        val hideHome: Boolean = false,
        val hideMine: Boolean = false,
    ) {
        val visibleCount: Int
            get() = 6 - listOf(hideFile, hideShare, hideVip, hideAigc, hideHome, hideMine).count { it }

        fun hasVisibleTab(): Boolean = visibleCount >= 1
    }

    fun getPrefs(context: Context): SharedPreferences {
        return ConfigManager.getPrefs(context)
    }

    fun getModuleStatePrefs(context: Context): SharedPreferences {
        return ConfigManager.getModuleStatePrefs(context)
    }

    fun applyFeatureAvailability(
        context: Context,
        featureStatusMap: Map<String, FeatureAvailabilityStatus>,
        refreshRuntime: Boolean = false,
    ) {
        ConfigManager.applyFeatureAvailability(
            context = context,
            featureStatusMap = featureStatusMap,
            refreshRuntime = refreshRuntime,
        )
    }

    fun isDisclaimerAccepted(context: Context): Boolean {
        return ConfigManager.isDisclaimerAccepted(context)
    }

    fun setDisclaimerAccepted(context: Context) {
        ConfigManager.setDisclaimerAccepted(context)
    }

    fun setRestrictedFeaturesUnlocked(context: Context, unlocked: Boolean) {
        ConfigManager.setRestrictedFeaturesUnlocked(context, unlocked)
    }

    fun readHomeTopPromotionHidden(prefs: SharedPreferences): Boolean {
        return ConfigManager.readHomeTopPromotionHidden(prefs)
    }

    fun normalizeBottomBarSelection(selection: BottomBarTabSelection): BottomBarTabSelection {
        if (selection.hasVisibleTab()) return selection
        return BottomBarTabSelection(
            hideFile = true,
            hideShare = true,
            hideVip = true,
            hideAigc = true,
            hideHome = false,
            hideMine = true,
        )
    }

    fun resetUserSettings(context: Context): Boolean {
        return ConfigManager.resetUserSettings(context)
    }
}
