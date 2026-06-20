package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal enum class PerformanceSettingsSection {
    INTL_BLOCK_INIT,
    INTL_DELAYED_BEHAVIOR,
    INTL_OTHER_BEHAVIOR,
    POST_INIT,
    STARTUP_PREFETCH,
    RUNTIME_SERVICE,
    AD_INCENTIVE,
}

internal data class PerformanceSwitchSpec(
    val key: String,
    val label: String,
    val description: String,
    val section: PerformanceSettingsSection,
)

internal object PerformanceSettingsRegistry {
    val specs: List<PerformanceSwitchSpec> = listOf(
        PerformanceSwitchSpec(
            SettingsUserState.KEY_BLOCK_INTL_OFFLINE_PACKAGE_INIT,
            UiText.Settings.BLOCK_INTL_OFFLINE_PACKAGE_INIT_LABEL,
            UiText.Settings.BLOCK_INTL_OFFLINE_PACKAGE_INIT_DESC,
            PerformanceSettingsSection.INTL_BLOCK_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DELAY_INTL_FEED_PRELOAD,
            UiText.Settings.DELAY_INTL_FEED_PRELOAD_LABEL,
            UiText.Settings.DELAY_INTL_FEED_PRELOAD_DESC,
            PerformanceSettingsSection.INTL_DELAYED_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DELAY_INTL_TASK_SCORE_REFRESH,
            UiText.Settings.DELAY_INTL_TASK_SCORE_REFRESH_LABEL,
            UiText.Settings.DELAY_INTL_TASK_SCORE_REFRESH_DESC,
            PerformanceSettingsSection.INTL_DELAYED_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_BLOCK_INTL_STORY_DOUYIN_INIT,
            UiText.Settings.BLOCK_INTL_STORY_DOUYIN_INIT_LABEL,
            UiText.Settings.BLOCK_INTL_STORY_DOUYIN_INIT_DESC,
            PerformanceSettingsSection.INTL_BLOCK_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DELAY_INTL_NON_CORE_DIFF_SOCKET,
            UiText.Settings.DELAY_INTL_NON_CORE_DIFF_SOCKET_LABEL,
            UiText.Settings.DELAY_INTL_NON_CORE_DIFF_SOCKET_DESC,
            PerformanceSettingsSection.INTL_DELAYED_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DELAY_INTL_FLOAT_VIEW_STARTUP,
            UiText.Settings.DELAY_INTL_FLOAT_VIEW_STARTUP_LABEL,
            UiText.Settings.DELAY_INTL_FLOAT_VIEW_STARTUP_DESC,
            PerformanceSettingsSection.INTL_DELAYED_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW,
            UiText.Settings.BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW_LABEL,
            UiText.Settings.BLOCK_INTL_AUDIO_CIRCLE_STARTUP_SHOW_DESC,
            PerformanceSettingsSection.INTL_OTHER_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_BLOCK_INTL_AIGC_WIDGET_BACKGROUND,
            UiText.Settings.BLOCK_INTL_AIGC_WIDGET_BACKGROUND_LABEL,
            UiText.Settings.BLOCK_INTL_AIGC_WIDGET_BACKGROUND_DESC,
            PerformanceSettingsSection.INTL_OTHER_BEHAVIOR,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_BLOCK_INTL_ALBUM_AI_INIT,
            UiText.Settings.BLOCK_INTL_ALBUM_AI_INIT_LABEL,
            UiText.Settings.BLOCK_INTL_ALBUM_AI_INIT_DESC,
            PerformanceSettingsSection.INTL_BLOCK_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER,
            UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_LABEL,
            UiText.Settings.DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER_DESC,
            PerformanceSettingsSection.POST_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_DATAPACK_SOCKET_REGISTER,
            UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_LABEL,
            UiText.Settings.DISABLE_DATAPACK_SOCKET_REGISTER_DESC,
            PerformanceSettingsSection.POST_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT,
            UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_LABEL,
            UiText.Settings.DISABLE_AIGC_BACKGROUND_COMPONENT_DESC,
            PerformanceSettingsSection.POST_INIT,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD,
            UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_LABEL,
            UiText.Settings.DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD_DESC,
            PerformanceSettingsSection.STARTUP_PREFETCH,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_SWAN_PRELOAD,
            UiText.Settings.DISABLE_SWAN_PRELOAD_LABEL,
            UiText.Settings.DISABLE_SWAN_PRELOAD_DESC,
            PerformanceSettingsSection.STARTUP_PREFETCH,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_ICON_RESOURCE_DOWNLOAD,
            UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_LABEL,
            UiText.Settings.DISABLE_ICON_RESOURCE_DOWNLOAD_DESC,
            PerformanceSettingsSection.STARTUP_PREFETCH,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_B2F_GUIDANCE_PREFETCH,
            UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_LABEL,
            UiText.Settings.DISABLE_B2F_GUIDANCE_PREFETCH_DESC,
            PerformanceSettingsSection.STARTUP_PREFETCH,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_OEM_PUSH_SERVICE,
            UiText.Settings.DISABLE_OEM_PUSH_SERVICE_LABEL,
            UiText.Settings.DISABLE_OEM_PUSH_SERVICE_DESC,
            PerformanceSettingsSection.RUNTIME_SERVICE,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE,
            UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_LABEL,
            UiText.Settings.DISABLE_THUMBNAIL_OPERATOR_SERVICE_DESC,
            PerformanceSettingsSection.RUNTIME_SERVICE,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART,
            UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_LABEL,
            UiText.Settings.DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART_DESC,
            PerformanceSettingsSection.RUNTIME_SERVICE,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_AD_SDK_INIT,
            UiText.Settings.DISABLE_AD_SDK_INIT_LABEL,
            UiText.Settings.DISABLE_AD_SDK_INIT_DESC,
            PerformanceSettingsSection.AD_INCENTIVE,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_VIDEO_AD_PRELOAD,
            UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_LABEL,
            UiText.Settings.DISABLE_VIDEO_AD_PRELOAD_DESC,
            PerformanceSettingsSection.AD_INCENTIVE,
        ),
        PerformanceSwitchSpec(
            SettingsUserState.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE,
            UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_LABEL,
            UiText.Settings.DISABLE_INCENTIVE_BUSINESS_SERVICE_DESC,
            PerformanceSettingsSection.AD_INCENTIVE,
        ),
    )

    fun specsIn(section: PerformanceSettingsSection): List<PerformanceSwitchSpec> {
        return specs.filter { it.section == section }
    }
}
