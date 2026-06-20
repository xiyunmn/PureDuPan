package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class SettingsText(
    val label: String,
    val description: String?,
)

internal class SettingsTextResolver internal constructor(
    private val textsByKey: Map<String, SettingsText>,
) {
    fun text(key: String, fallbackLabel: String, fallbackDescription: String?): SettingsText {
        return textsByKey[key] ?: SettingsText(
            label = fallbackLabel,
            description = fallbackDescription,
        )
    }
}

internal object SettingsHostTextCatalog {
    private const val HOST_SUFFIX_CN = "_cn"
    private const val HOST_SUFFIX_INTL = "_intl"
    private const val HOST_SUFFIX_SAMSUNG = "_samsung"

    fun forHostId(hostId: String?): SettingsTextResolver {
        val texts = when {
            hostId?.endsWith(HOST_SUFFIX_CN) == true -> baiduCnTexts()
            hostId?.endsWith(HOST_SUFFIX_INTL) == true -> baiduIntlTexts()
            hostId?.endsWith(HOST_SUFFIX_SAMSUNG) == true -> baiduSamsungTexts()
            else -> commonTexts()
        }
        return SettingsTextResolver(texts)
    }

    private fun baiduCnTexts(): Map<String, SettingsText> = commonTexts()

    private fun baiduIntlTexts(): Map<String, SettingsText> = commonTexts()

    private fun baiduSamsungTexts(): Map<String, SettingsText> = commonTexts()

    private fun commonTexts(): LinkedHashMap<String, SettingsText> {
        return linkedMapOf(
            SettingsUserState.KEY_BLOCK_SPLASH_INTERSTITIAL to text(
                UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_LABEL,
                UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_DESC,
            ),
            SettingsUserState.KEY_REMOVE_HOT_START_SPLASH to text(
                UiText.Settings.REMOVE_HOT_START_SPLASH_LABEL,
                UiText.Settings.REMOVE_HOT_START_SPLASH_DESC,
            ),
            SettingsUserState.KEY_BLOCK_IN_APP_DIALOG to text(
                UiText.Settings.BLOCK_IN_APP_DIALOG_LABEL,
                UiText.Settings.BLOCK_IN_APP_DIALOG_DESC,
            ),
            SettingsUserState.KEY_BLOCK_UPDATE_DIALOG to text(
                UiText.Settings.BLOCK_UPDATE_DIALOG_LABEL,
                UiText.Settings.BLOCK_UPDATE_DIALOG_DESC,
            ),
            SettingsUserState.KEY_BLOCK_FULL_SCREEN_BACKUP to text(
                UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_LABEL,
                UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_DESC,
            ),
            SettingsUserState.KEY_BLOCK_SHARE_PUSH_GUIDE to text(
                UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_LABEL,
                UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_DESC,
            ),
            SettingsUserState.KEY_BLOCK_APP_STORE_REVIEW to text(
                UiText.Settings.BLOCK_APP_STORE_REVIEW_LABEL,
                UiText.Settings.BLOCK_APP_STORE_REVIEW_DESC,
            ),
            SettingsUserState.KEY_HOME_CUSTOMIZE to text(
                UiText.Settings.HOME_CUSTOMIZE_LABEL,
                UiText.Settings.HOME_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_TOP_PROMOTION to text(
                UiText.Settings.HIDE_HOME_TOP_PROMOTION_LABEL,
                UiText.Settings.HIDE_HOME_TOP_PROMOTION_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_SEARCH_PLACEHOLDER to text(
                UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_LABEL,
                UiText.Settings.HIDE_HOME_SEARCH_PLACEHOLDER_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_SEARCH_AIGC_ICON to text(
                UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_LABEL,
                UiText.Settings.HIDE_HOME_SEARCH_AIGC_ICON_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_FEED_TIP to text(
                UiText.Settings.HIDE_HOME_FEED_TIP_LABEL,
                UiText.Settings.HIDE_HOME_FEED_TIP_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_BANNER to text(
                UiText.Settings.HIDE_HOME_BANNER_LABEL,
                UiText.Settings.HIDE_HOME_BANNER_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_MEMORIES_SECTION to text(
                UiText.Settings.HIDE_HOME_MEMORIES_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_MEMORIES_SECTION_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_SAVE_SECTION to text(
                UiText.Settings.HIDE_HOME_SAVE_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_SAVE_SECTION_DESC,
            ),
            SettingsUserState.KEY_HIDE_HOME_RECENT_SECTION to text(
                UiText.Settings.HIDE_HOME_RECENT_SECTION_LABEL,
                UiText.Settings.HIDE_HOME_RECENT_SECTION_DESC,
            ),
            SettingsUserState.KEY_SHARE_PAGE_CUSTOMIZE to text(
                UiText.Settings.SHARE_PAGE_CUSTOMIZE_LABEL,
                UiText.Settings.SHARE_PAGE_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_REMOVE_HOME_FAB to text(
                UiText.Settings.REMOVE_HOME_FAB_LABEL,
                UiText.Settings.REMOVE_HOME_FAB_DESC,
            ),
            SettingsUserState.KEY_MY_PAGE_CUSTOMIZE to text(
                UiText.Settings.MY_PAGE_CUSTOMIZE_LABEL,
                UiText.Settings.MY_PAGE_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_HIDE_RENEW_BUTTON to text(
                UiText.Settings.HIDE_RENEW_BUTTON_LABEL,
                UiText.Settings.HIDE_RENEW_BUTTON_DESC,
            ),
            SettingsUserState.KEY_REMOVE_GAME_CENTER to text(
                UiText.Settings.REMOVE_GAME_CENTER_LABEL,
                UiText.Settings.REMOVE_GAME_CENTER_DESC,
            ),
            SettingsUserState.KEY_REMOVE_ABOUT_ME_BANNER to text(
                UiText.Settings.REMOVE_ABOUT_ME_BANNER_LABEL,
                UiText.Settings.REMOVE_ABOUT_ME_BANNER_DESC,
            ),
            SettingsUserState.KEY_REMOVE_MY_SERVICE to text(
                UiText.Settings.REMOVE_MY_SERVICE_LABEL,
                UiText.Settings.REMOVE_MY_SERVICE_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE to text(
                UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_COIN_CENTER_BUBBLE_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT to text(
                UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_SIGN_IN_DOT_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET to text(
                UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_AI_COIN_ASSET_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT to text(
                UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_MANAGE_SPACE_TEXT_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_REWARD_TEXT to text(
                UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_REWARD_TEXT_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT to text(
                UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT to text(
                UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_STAR_SKIN_TEXT_DESC,
            ),
            SettingsUserState.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT to text(
                UiText.Settings.HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT_LABEL,
                UiText.Settings.HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT_DESC,
            ),
            SettingsUserState.KEY_BLOCK_ALBUM_BACKUP_BAR to text(
                UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_LABEL,
                UiText.Settings.BLOCK_ALBUM_BACKUP_BAR_DESC,
            ),
            SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE to text(
                UiText.Settings.MEMBER_CARD_CUSTOMIZE_LABEL,
                UiText.Settings.MEMBER_CARD_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_REPLACE_MEMBER_CARD_BACKGROUND to text(
                UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_LABEL,
                UiText.Settings.REPLACE_MEMBER_CARD_BACKGROUND_DESC,
            ),
            SettingsUserState.KEY_MEMBER_CARD_BACKGROUND_BLUR_RADIUS to text(
                UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_LABEL,
                UiText.Settings.MEMBER_CARD_BACKGROUND_BLUR_DESC,
            ),
            SettingsUserState.KEY_MEMBER_CARD_SIZE_ADJUST to text(
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_LABEL,
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
            ),
            SettingsUserState.KEY_MEMBER_CARD_SIZE_WIDTH_DP to text(
                UiText.Settings.MEMBER_CARD_WIDTH_LABEL,
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
            ),
            SettingsUserState.KEY_MEMBER_CARD_SIZE_HEIGHT_DP to text(
                UiText.Settings.MEMBER_CARD_SIZE_HEIGHT_LABEL,
                UiText.Settings.MEMBER_CARD_SIZE_ADJUST_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_OPERATION to text(
                UiText.Settings.HIDE_MEMBER_CARD_OPERATION_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_OPERATION_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_BENEFIT to text(
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT to text(
                UiText.Settings.HIDE_MEMBER_CARD_FIRST_BENEFIT_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_FIRST_BENEFIT_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT to text(
                UiText.Settings.HIDE_MEMBER_CARD_SECOND_BENEFIT_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_SECOND_BENEFIT_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT to text(
                UiText.Settings.HIDE_MEMBER_CARD_THIRD_BENEFIT_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_THIRD_BENEFIT_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR to text(
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_BAR_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_BENEFIT_BAR_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL to text(
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_LEVEL_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_LEVEL_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_SVIP_STATUS to text(
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_STATUS_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_SVIP_STATUS_DESC,
            ),
            SettingsUserState.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON to text(
                UiText.Settings.HIDE_MEMBER_CARD_RENEW_BUTTON_LABEL,
                UiText.Settings.HIDE_MEMBER_CARD_RENEW_BUTTON_DESC,
            ),
            SettingsUserState.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL to text(
                UiText.Settings.HIDE_INTL_MEMBER_CARD_SVIP_LEVEL_LABEL,
                UiText.Settings.HIDE_INTL_MEMBER_CARD_SVIP_LEVEL_DESC,
            ),
            SettingsUserState.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON to text(
                UiText.Settings.HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON_LABEL,
                UiText.Settings.HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON_DESC,
            ),
            SettingsUserState.KEY_REMOVE_MEMBER_CARD_CLICK to text(
                UiText.Settings.REMOVE_MEMBER_CARD_CLICK_LABEL,
                UiText.Settings.REMOVE_MEMBER_CARD_CLICK_DESC,
            ),
            SettingsUserState.KEY_VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK to text(
                UiText.Settings.VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK_LABEL,
                UiText.Settings.VIEW_MEMBER_CARD_BACKGROUND_ON_CLICK_DESC,
            ),
            SettingsUserState.KEY_CUSTOM_BOTTOM_BAR to text(
                UiText.Settings.CUSTOM_BOTTOM_BAR_LABEL,
                UiText.Settings.CUSTOM_BOTTOM_BAR_DESC,
            ),
            SettingsUserState.KEY_REPLACE_BOTTOM_AI to text(
                UiText.Settings.REPLACE_BOTTOM_AI_LABEL,
                UiText.Settings.REPLACE_BOTTOM_AI_DESC,
            ),
            SettingsUserState.KEY_BLOCK_BOTTOM_BADGE to text(
                UiText.Settings.BLOCK_BOTTOM_BADGE_LABEL,
                UiText.Settings.BLOCK_BOTTOM_BADGE_DESC,
            ),
            SettingsUserState.KEY_HIDE_TAB_HOME to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_HOME_LABEL,
                null,
            ),
            SettingsUserState.KEY_HIDE_TAB_FILE to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_FILE_LABEL,
                null,
            ),
            SettingsUserState.KEY_HIDE_TAB_SHARE to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_SHARE_LABEL,
                null,
            ),
            SettingsUserState.KEY_HIDE_TAB_VIP to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_VIP_LABEL,
                null,
            ),
            SettingsUserState.KEY_HIDE_TAB_AIGC to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_AIGC_LABEL,
                null,
            ),
            SettingsUserState.KEY_HIDE_TAB_MINE to text(
                UiText.Settings.BOTTOM_BAR_HIDE_TAB_MINE_LABEL,
                null,
            ),
            SettingsUserState.KEY_FOLLOW_SYSTEM_NIGHT_MODE to text(
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_LABEL,
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_DESC,
            ),
            SettingsUserState.KEY_PERFORMANCE_OPTIMIZE to text(
                UiText.Settings.PERFORMANCE_OPTIMIZE_LABEL,
                UiText.Settings.PERFORMANCE_OPTIMIZE_DESC,
            ),
            SettingsUserState.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER to text(
                UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_LABEL,
                UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_DESC,
            ),
            SettingsUserState.KEY_DISABLE_DATAPACK_SOCKET_REGISTER to text(
                UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_LABEL,
                UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_DESC,
            ),
            SettingsUserState.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT to text(
                UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_LABEL,
                UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_DESC,
            ),
            SettingsUserState.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD to text(
                UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_LABEL,
                UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DESC,
            ),
            SettingsUserState.KEY_DISABLE_OEM_PUSH_SERVICE to text(
                UiText.Settings.DISABLE_OEM_PUSH_SERVICE_LABEL,
                UiText.Settings.DISABLE_OEM_PUSH_SERVICE_DESC,
            ),
            SettingsUserState.KEY_DISABLE_VIDEO_AD_PRELOAD to text(
                UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_LABEL,
                UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_DESC,
            ),
            SettingsUserState.KEY_DISABLE_AD_SDK_INIT to text(
                UiText.Settings.DISABLE_AD_SDK_INIT_LABEL,
                UiText.Settings.DISABLE_AD_SDK_INIT_DESC,
            ),
            SettingsUserState.KEY_DISABLE_SWAN_PRELOAD to text(
                UiText.Settings.DISABLE_SWAN_PRELOAD_LABEL,
                UiText.Settings.DISABLE_SWAN_PRELOAD_DESC,
            ),
            SettingsUserState.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE to text(
                UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_LABEL,
                UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_DESC,
            ),
            SettingsUserState.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE to text(
                UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_LABEL,
                UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_DESC,
            ),
            SettingsUserState.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART to text(
                UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_LABEL,
                UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_DESC,
            ),
            SettingsUserState.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD to text(
                UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_LABEL,
                UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_DESC,
            ),
            SettingsUserState.KEY_DISABLE_B2F_GUIDANCE_PREFETCH to text(
                UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_LABEL,
                UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_DESC,
            ),
            SettingsUserState.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT to text(
                UiText.Settings.BLOCK_INTL_OFFLINE_PACKAGE_INIT_LABEL,
                UiText.Settings.BLOCK_INTL_OFFLINE_PACKAGE_INIT_DESC,
            ),
            SettingsUserState.KEY_DELAY_INTL_FEED_PRELOAD to text(
                UiText.Settings.DELAY_INTL_FEED_PRELOAD_LABEL,
                UiText.Settings.DELAY_INTL_FEED_PRELOAD_DESC,
            ),
            SettingsUserState.KEY_DELAY_INTL_TASK_SCORE_REFRESH to text(
                UiText.Settings.DELAY_INTL_TASK_SCORE_REFRESH_LABEL,
                UiText.Settings.DELAY_INTL_TASK_SCORE_REFRESH_DESC,
            ),
            SettingsUserState.KEY_BLOCK_INTL_STORY_DOUYIN_INIT to text(
                UiText.Settings.BLOCK_INTL_STORY_DOUYIN_INIT_LABEL,
                UiText.Settings.BLOCK_INTL_STORY_DOUYIN_INIT_DESC,
            ),
            SettingsUserState.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET to text(
                UiText.Settings.DELAY_INTL_NON_CORE_DIFF_SOCKET_LABEL,
                UiText.Settings.DELAY_INTL_NON_CORE_DIFF_SOCKET_DESC,
            ),
            SettingsUserState.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP to text(
                UiText.Settings.DELAY_INTL_FLOAT_VIEW_STARTUP_LABEL,
                UiText.Settings.DELAY_INTL_FLOAT_VIEW_STARTUP_DESC,
            ),
            SettingsUserState.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW to text(
                UiText.Settings.BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW_LABEL,
                UiText.Settings.BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW_DESC,
            ),
            SettingsUserState.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND to text(
                UiText.Settings.BLOCK_INTL_AIGC_WIDGET_BACKGROUND_LABEL,
                UiText.Settings.BLOCK_INTL_AIGC_WIDGET_BACKGROUND_DESC,
            ),
            SettingsUserState.KEY_BLOCK_INTL_ALBUM_AI_INIT to text(
                UiText.Settings.BLOCK_INTL_ALBUM_AI_INIT_LABEL,
                UiText.Settings.BLOCK_INTL_ALBUM_AI_INIT_DESC,
            ),
            SettingsUserState.KEY_ENABLE_EXPERIMENTAL_DEXKIT to text(
                UiText.Settings.EXPERIMENTAL_DEXKIT_LABEL,
                UiText.Settings.EXPERIMENTAL_DEXKIT_DESC,
            ),
            SettingsUserState.KEY_ENABLE_DETAILED_LOGGING to text(
                UiText.Settings.DETAILED_LOGGING_LABEL,
                UiText.Settings.DETAILED_LOGGING_DESC,
            ),
        )
    }

    private fun text(label: String, description: String?): SettingsText {
        return SettingsText(label = label, description = description)
    }
}
