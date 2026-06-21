package com.xiyunmn.puredupan.hook.host.features.baidu

import com.xiyunmn.puredupan.hook.config.model.FeatureKeys

internal object BaiduFeatureSets {
    val baiduCnAvailableKeys: Set<String>
        get() = mergeKeys(
            baiduSharedDiagnostics,
            baiduSharedStartup,
            baiduSharedContentBlock,
            baiduSharedHomeCustomize,
            baiduSharedFilePageCustomize,
            baiduSharedShareCustomize,
            baiduSharedMyPageCustomize,
            baiduSharedUiBlock,
            baiduSharedMemberCardCustomize,
            baiduSharedBottomBarCustomize,
            baiduSharedPerformanceCustomize,
            baiduCnStartup,
            baiduCnHomeCustomize,
            baiduCnSearchPageCustomize,
            baiduCnMyPageCustomize,
            baiduCnMemberCardCustomize,
            baiduCnBottomBarCustomize,
            baiduCnPerformanceCustomize,
            baiduCnThemeCustomize,
        )

    val baiduIntlAvailableKeys: Set<String>
        get() = mergeKeys(
            baiduSharedDiagnostics,
            baiduSharedStartup,
            baiduSharedContentBlock,
            baiduSharedHomeCustomize,
            baiduSharedFilePageCustomize,
            baiduSharedShareCustomize,
            baiduSharedMyPageCustomize,
            baiduSharedUiBlock,
            baiduSharedMemberCardCustomize,
            baiduSharedBottomBarCustomize,
            baiduSharedPerformanceCustomize,
            baiduIntlStartup,
            baiduIntlHomeCustomize,
            baiduIntlMyPageCustomize,
            baiduIntlMemberCardCustomize,
            baiduIntlBottomBarCustomize,
            baiduIntlPerformanceCustomize,
            baiduIntlDexKit,
        )

    val baiduSamsungAvailableKeys: Set<String>
        get() = mergeKeys(
            baiduCnAvailableKeys,
            baiduSamsungAd,
        )

    private val baiduSharedDiagnostics = listOf(
        FeatureKeys.KEY_ENABLE_DETAILED_LOGGING,
    )

    private val baiduSharedStartup = listOf(
        FeatureKeys.KEY_BLOCK_SPLASH_INTERSTITIAL,
    )

    private val baiduSharedContentBlock = listOf(
        FeatureKeys.KEY_BLOCK_APP_STORE_REVIEW,
        FeatureKeys.KEY_BLOCK_FULL_SCREEN_BACKUP,
        FeatureKeys.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
    )

    private val baiduSharedHomeCustomize = listOf(
        FeatureKeys.KEY_HOME_CUSTOMIZE,
        FeatureKeys.KEY_HIDE_HOME_FEED_TIP,
        FeatureKeys.KEY_HIDE_HOME_MEMORIES_SECTION,
        FeatureKeys.KEY_HIDE_HOME_RECENT_SECTION,
        FeatureKeys.KEY_HIDE_HOME_SAVE_SECTION,
        FeatureKeys.KEY_REMOVE_HOME_FAB,
    )

    private val baiduSharedShareCustomize = listOf(
        FeatureKeys.KEY_SHARE_PAGE_CUSTOMIZE,
    )

    private val baiduSharedFilePageCustomize = listOf(
        FeatureKeys.KEY_FILE_PAGE_CUSTOMIZE,
        FeatureKeys.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP,
    )

    private val baiduSharedMyPageCustomize = listOf(
        FeatureKeys.KEY_MY_PAGE_CUSTOMIZE,
        FeatureKeys.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT,
        FeatureKeys.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE,
        FeatureKeys.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT,
        FeatureKeys.KEY_HIDE_ABOUT_ME_REWARD_TEXT,
        FeatureKeys.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT,
        FeatureKeys.KEY_REMOVE_ABOUT_ME_BANNER,
        FeatureKeys.KEY_REMOVE_GAME_CENTER,
        FeatureKeys.KEY_REMOVE_MY_SERVICE,
    )

    private val baiduSharedUiBlock = listOf(
        FeatureKeys.KEY_BLOCK_ALBUM_BACKUP_BAR,
    )

    private val baiduSharedMemberCardCustomize = listOf(
        FeatureKeys.KEY_MEMBER_CARD_CUSTOMIZE,
        FeatureKeys.KEY_REPLACE_MEMBER_CARD_BACKGROUND,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_URI,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_SCALE_PERCENT,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_ROTATION_DEGREES,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_X_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_BACKGROUND_OFFSET_Y_PERMILLE,
        FeatureKeys.KEY_MEMBER_CARD_SIZE_ADJUST,
        FeatureKeys.KEY_MEMBER_CARD_SIZE_WIDTH_DP,
        FeatureKeys.KEY_MEMBER_CARD_SIZE_HEIGHT_DP,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_WIDTH_PX,
        FeatureKeys.KEY_MEMBER_CARD_DEFAULT_HEIGHT_PX,
        FeatureKeys.KEY_REMOVE_MEMBER_CARD_CLICK,
        FeatureKeys.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK,
    )

    private val baiduSharedBottomBarCustomize = listOf(
        FeatureKeys.KEY_BLOCK_BOTTOM_BADGE,
        FeatureKeys.KEY_CUSTOM_BOTTOM_BAR,
        FeatureKeys.KEY_HIDE_TAB_FILE,
        FeatureKeys.KEY_HIDE_TAB_SHARE,
        FeatureKeys.KEY_HIDE_TAB_HOME,
        FeatureKeys.KEY_HIDE_TAB_MINE,
    )

    private val baiduSharedPerformanceCustomize = listOf(
        FeatureKeys.KEY_PERFORMANCE_OPTIMIZE,
    )

    private val baiduCnStartup = listOf(
        FeatureKeys.KEY_BLOCK_IN_APP_DIALOG,
        FeatureKeys.KEY_BLOCK_SHARE_PUSH_GUIDE,
        FeatureKeys.KEY_BLOCK_UPDATE_DIALOG,
    )

    private val baiduCnHomeCustomize = listOf(
        FeatureKeys.KEY_HIDE_HOME_SEARCH_AIGC_ICON,
        FeatureKeys.KEY_HIDE_HOME_SEARCH_PLACEHOLDER,
        FeatureKeys.KEY_HIDE_HOME_TOP_PROMOTION,
    )

    private val baiduCnSearchPageCustomize = listOf(
        FeatureKeys.KEY_SEARCH_PAGE_CUSTOMIZE,
        FeatureKeys.KEY_HIDE_SEARCH_PAGE_AI_ENTRY,
        FeatureKeys.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER,
        FeatureKeys.KEY_HIDE_SEARCH_PAGE_RECOMMEND,
    )

    private val baiduCnMyPageCustomize = listOf(
        FeatureKeys.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET,
        FeatureKeys.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT,
        FeatureKeys.KEY_HIDE_RENEW_BUTTON,
        FeatureKeys.KEY_REMOVE_GAME_CENTER,
    )

    private val baiduCnMemberCardCustomize = listOf(
        FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_OPERATION,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_SVIP_STATUS,
    )

    private val baiduCnBottomBarCustomize = listOf(
        FeatureKeys.KEY_HIDE_TAB_AIGC,
        FeatureKeys.KEY_HIDE_TAB_VIP,
        FeatureKeys.KEY_REPLACE_BOTTOM_AI,
    )

    private val baiduCnPerformanceCustomize = listOf(
        FeatureKeys.KEY_DISABLE_AD_SDK_INIT,
        FeatureKeys.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT,
        FeatureKeys.KEY_DISABLE_B2F_GUIDANCE_PREFETCH,
        FeatureKeys.KEY_DISABLE_DATAPACK_SOCKET_REGISTER,
        FeatureKeys.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
        FeatureKeys.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER,
        FeatureKeys.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
        FeatureKeys.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE,
        FeatureKeys.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
        FeatureKeys.KEY_DISABLE_OEM_PUSH_SERVICE,
        FeatureKeys.KEY_DISABLE_SWAN_PRELOAD,
        FeatureKeys.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
        FeatureKeys.KEY_DISABLE_VIDEO_AD_PRELOAD,
    )

    private val baiduCnThemeCustomize = listOf(
        FeatureKeys.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
    )

    private val baiduIntlStartup = listOf(
        FeatureKeys.KEY_ACCELERATE_INTL_SPLASH_STARTUP,
        FeatureKeys.KEY_REMOVE_HOT_START_SPLASH,
    )

    private val baiduIntlHomeCustomize = listOf(
        FeatureKeys.KEY_HIDE_HOME_BANNER,
    )

    private val baiduIntlMyPageCustomize = listOf(
        FeatureKeys.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT,
    )

    private val baiduIntlMemberCardCustomize = listOf(
        FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL,
        FeatureKeys.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT,
        FeatureKeys.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT,
    )

    private val baiduIntlBottomBarCustomize = listOf(
        FeatureKeys.KEY_HIDE_TAB_AIGC,
    )

    private val baiduSamsungAd = listOf(
        FeatureKeys.KEY_BLOCK_NOTIFICATION_PROMPT,
    )

    private val baiduIntlPerformanceCustomize = listOf(
        FeatureKeys.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND,
        FeatureKeys.KEY_BLOCK_INTL_ALBUM_AI_INIT,
        FeatureKeys.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW,
        FeatureKeys.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT,
        FeatureKeys.KEY_BLOCK_INTL_STORY_DOUYIN_INIT,
        FeatureKeys.KEY_DELAY_INTL_FEED_PRELOAD,
        FeatureKeys.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP,
        FeatureKeys.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
        FeatureKeys.KEY_DELAY_INTL_TASK_SCORE_REFRESH,
    )

    private val baiduIntlDexKit = listOf(
        FeatureKeys.KEY_ENABLE_EXPERIMENTAL_DEXKIT,
    )

    private fun mergeKeys(vararg groups: Iterable<String>): Set<String> {
        return groups.flatMapTo(linkedSetOf()) { it }
    }
}
