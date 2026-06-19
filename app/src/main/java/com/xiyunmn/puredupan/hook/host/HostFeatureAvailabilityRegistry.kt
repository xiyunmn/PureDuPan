package com.xiyunmn.puredupan.hook.host

import com.xiyunmn.puredupan.hook.config.ConfigManager
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityState
import com.xiyunmn.puredupan.hook.config.model.FeatureAvailabilityStatus

internal object HostFeatureAvailabilityRegistry {
    private val disabledStatus = FeatureAvailabilityStatus(
        state = FeatureAvailabilityState.DISABLED,
    )

    private val cnDisabledKeys = setOf(
        ConfigManager.KEY_HIDE_TAB_AIGC,
        ConfigManager.KEY_HIDE_HOME_BANNER,
        ConfigManager.KEY_HIDE_ABOUT_ME_FREE_DATA_CARD_TEXT,
        ConfigManager.KEY_ACCELERATE_INTL_SPLASH_STARTUP,
        ConfigManager.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT,
        ConfigManager.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT,
        ConfigManager.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT,
        ConfigManager.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL,
        ConfigManager.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON,
        ConfigManager.KEY_DELAY_INTL_OFFLINE_PACKAGE_SYNC,
    )

    private val intlDisabledKeys = setOf(
        ConfigManager.KEY_BLOCK_IN_APP_DIALOG,
        ConfigManager.KEY_BLOCK_UPDATE_DIALOG,
        ConfigManager.KEY_BLOCK_FULL_SCREEN_BACKUP,
        ConfigManager.KEY_BLOCK_SHARE_PUSH_GUIDE,
        ConfigManager.KEY_BLOCK_APP_STORE_REVIEW,
        ConfigManager.KEY_REPLACE_BOTTOM_AI,
        ConfigManager.KEY_HIDE_HOME_TOP_PROMOTION,
        ConfigManager.KEY_HIDE_HOME_SEARCH_PLACEHOLDER,
        ConfigManager.KEY_HIDE_HOME_SEARCH_AIGC_ICON,
        ConfigManager.KEY_HIDE_TAB_VIP,
        ConfigManager.KEY_HIDE_RENEW_BUTTON,
        ConfigManager.KEY_REMOVE_GAME_CENTER,
        ConfigManager.KEY_HIDE_ABOUT_ME_AI_COIN_ASSET,
        ConfigManager.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT,
        ConfigManager.KEY_BLOCK_ALBUM_BACKUP_BAR,
        ConfigManager.KEY_HIDE_MEMBER_CARD_OPERATION,
        ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT,
        ConfigManager.KEY_HIDE_MEMBER_CARD_BENEFIT_BAR,
        ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_LEVEL,
        ConfigManager.KEY_HIDE_MEMBER_CARD_SVIP_STATUS,
        ConfigManager.KEY_HIDE_MEMBER_CARD_RENEW_BUTTON,
        ConfigManager.KEY_FOLLOW_SYSTEM_NIGHT_MODE,
        ConfigManager.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER,
        ConfigManager.KEY_DISABLE_DATAPACK_SOCKET_REGISTER,
        ConfigManager.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT,
        ConfigManager.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
        ConfigManager.KEY_DISABLE_OEM_PUSH_SERVICE,
        ConfigManager.KEY_DISABLE_VIDEO_AD_PRELOAD,
        ConfigManager.KEY_DISABLE_AD_SDK_INIT,
        ConfigManager.KEY_DISABLE_SWAN_PRELOAD,
        ConfigManager.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
        ConfigManager.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE,
        ConfigManager.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
        ConfigManager.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
        ConfigManager.KEY_DISABLE_B2F_GUIDANCE_PREFETCH,
    )

    fun featureStatusMapFor(packageName: String): Map<String, FeatureAvailabilityStatus> {
        return when (HostRegistry.requireByPackageName(packageName).flavor) {
            HostFlavor.BAIDU_CN -> cnDisabledKeys.associateWith { disabledStatus }
            HostFlavor.BAIDU_INTL -> intlDisabledKeys.associateWith { disabledStatus }
        }
    }
}
