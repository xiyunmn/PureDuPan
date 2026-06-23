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

    private fun baiduIntlTexts(): Map<String, SettingsText> {
        return commonTexts().apply {
            this[SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE] = text(
                "会员卡片定制",
                "管理「我的」页会员卡片背景、点击事件、等级福利和升级按钮",
            )
        }
    }

    private fun baiduSamsungTexts(): Map<String, SettingsText> {
        return commonTexts().apply {
            this[SettingsUserState.KEY_BLOCK_SPLASH_INTERSTITIAL] = text(
                "移除开屏、热重载广告",
                UiText.Settings.BLOCK_SPLASH_INTERSTITIAL_DESC,
            )
            this[SettingsUserState.KEY_BLOCK_IN_APP_DIALOG] = text(
                "屏蔽应用内弹窗",
                "拦截网盘运行期间弹出的运营活动弹窗",
            )
            this[SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG] = text(
                "移除下载 WiFi 未连接弹窗",
                "未连接 WiFi 时点击下载，自动确认并直接创建下载任务",
            )
            this[SettingsUserState.KEY_BLOCK_NOTIFICATION_PROMPT] = text(
                "移除消息通知弹窗",
                "拦截应用内消息通知引导弹窗",
            )
            this[SettingsUserState.KEY_HIDE_HOME_BANNER] = text(
                "轮播图广告",
                "隐藏「首页」信息流头部轮播图广告区域",
            )
            this[SettingsUserState.KEY_HIDE_HOME_FEED_TIP] = text(
                "开启推荐提示",
                "隐藏「首页」信息流上方的开启推荐提示条",
            )
            this[SettingsUserState.KEY_HIDE_HOME_MEMORIES_SECTION] = text(
                "回忆",
                "隐藏「首页」信息流头部的回忆板块",
            )
            this[SettingsUserState.KEY_HIDE_HOME_SAVE_SECTION] = text(
                "转存",
                "隐藏「首页」信息流头部的转存板块",
            )
            this[SettingsUserState.KEY_HIDE_HOME_RECENT_SECTION] = text(
                "最近",
                "隐藏「首页」信息流头部的最近板块",
            )
            this[SettingsUserState.KEY_REMOVE_HOME_FAB] = text(
                "广告悬浮窗",
                "移除「共享」页面右下角广告悬浮球",
            )
            this[SettingsUserState.KEY_REMOVE_ABOUT_ME_BANNER] = text(
                "横幅广告",
                "隐藏「我的」页底部横幅推广区域",
            )
            this[SettingsUserState.KEY_REMOVE_MY_SERVICE] = text(
                "服务入口",
                "隐藏「我的」页服务入口",
            )
            this[SettingsUserState.KEY_REMOVE_GAME_CENTER] = text(
                "游戏中心",
                "隐藏「我的」页游戏中心入口",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_COIN_CENTER_BUBBLE] = text(
                "任务卡片悬浮球和推广文本",
                "隐藏「我的」页任务卡片悬浮球和推广文本",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_SIGN_IN_DOT] = text(
                "签到小红点",
                "隐藏「我的」页右上角签到入口旁的小红点",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_MANAGE_SPACE_TEXT] = text(
                "管理空间",
                "隐藏「设置与服务」中的管理空间入口文本",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_REWARD_TEXT] = text(
                "领奖励",
                "隐藏「设置与服务」中的领奖励入口文本",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_ACCOUNT_EXIT_TEXT] = text(
                "账号、退出",
                "隐藏「设置与服务」中的账号、退出入口文本",
            )
            this[SettingsUserState.KEY_HIDE_ABOUT_ME_STAR_SKIN_TEXT] = text(
                "明星皮肤上线啦",
                "隐藏「个性设置」中的明星皮肤入口文本",
            )
            this[SettingsUserState.KEY_MEMBER_CARD_CUSTOMIZE] = text(
                "会员卡片定制",
                "管理「我的」页会员卡片背景、点击事件、等级福利和升级按钮",
            )
            this[SettingsUserState.KEY_HIDE_INTL_MEMBER_CARD_SVIP_LEVEL] = text(
                "SVIP 等级",
                "隐藏会员卡片顶部的 SVIP 等级徽标",
            )
            this[SettingsUserState.KEY_HIDE_INTL_MEMBER_CARD_UPGRADE_BUTTON] = text(
                "升级按钮",
                "隐藏会员卡片右上角的升级按钮",
            )
            this[SettingsUserState.KEY_HIDE_MEMBER_CARD_FIRST_BENEFIT] = text(
                "等级福利卡片 1",
                "隐藏会员卡片第一张等级福利卡片",
            )
            this[SettingsUserState.KEY_HIDE_MEMBER_CARD_SECOND_BENEFIT] = text(
                "等级福利卡片 2",
                "隐藏会员卡片第二张等级福利卡片",
            )
            this[SettingsUserState.KEY_HIDE_MEMBER_CARD_THIRD_BENEFIT] = text(
                "专属权益",
                "隐藏会员卡片第三张专属权益卡片；当等级福利与专属权益同时隐藏时，会一并隐藏分隔线",
            )
            this[SettingsUserState.KEY_BLOCK_BOTTOM_BADGE] = text(
                "共享红色角标",
                "移除底部导航栏「共享」右上角红色数字角标",
            )
            this[SettingsUserState.KEY_PERFORMANCE_OPTIMIZE] = text(
                UiText.Settings.PERFORMANCE_OPTIMIZE_LABEL,
                UiText.Settings.PERFORMANCE_OPTIMIZE_DESC,
            )
            this[SettingsUserState.KEY_DISABLE_B2F_GUIDANCE_PREFETCH] = text(
                "B2F 引导数据启动预拉取",
                "阻止启动异步阶段预拉取回流引导和运营弹窗数据",
            )
            this[SettingsUserState.KEY_DISABLE_MEDIA_BROWSER_SERVICE_AUTOSTART] = text(
                "音频媒体服务自启动",
                "阻止启动期拉起音频媒体服务；手动打开音频仍按需启动",
            )
            this[SettingsUserState.KEY_DISABLE_OEM_PUSH_SERVICE] = text(
                "OEM 厂商推送组件唤起",
                "阻止华为/荣耀/小米/OPPO/VIVO/魅族推送组件在主进程或推送进程中被唤起",
            )
            this[SettingsUserState.KEY_DISABLE_SWAN_PRELOAD] = text(
                "Swan 小程序运行时预加载",
                "阻止启动后提前拉起小程序运行时，手动打开相关页面时仍按需加载",
            )
            this[SettingsUserState.KEY_DISABLE_AD_SDK_INIT] = text(
                "广告 SDK 下载服务",
                "阻止广告 SDK 下载服务启动，保留网盘下载服务",
            )
            this[SettingsUserState.KEY_DISABLE_GARBAGE_CLEAN_SERVICE_REGISTER] = text(
                "清理组件服务注册",
                "跳过启动后置阶段的清理组件服务注册",
            )
            this[SettingsUserState.KEY_DISABLE_DATAPACK_SOCKET_REGISTER] = text(
                "流量包权益 Socket 注册",
                "跳过启动后置阶段的流量包权益变化 Socket 注册",
            )
            this[SettingsUserState.KEY_DISABLE_DYNAMIC_PLUGIN_AUTO_DOWNLOAD] = text(
                "边缘动态插件自动下载/安装",
                "阻止 OCR、图片转 Office、识图、人脸检测等边缘插件后台自动下载/安装",
            )
            this[SettingsUserState.KEY_DISABLE_AIGC_BACKGROUND_COMPONENT] = text(
                "AIGC 小组件后台刷新",
                "阻止 AIGC 小组件后台刷新和资源解压，不拦截用户主动打开 AI 页面",
            )
            this[SettingsUserState.KEY_DISABLE_INCENTIVE_BUSINESS_SERVICE] = text(
                "激励业务服务",
                "阻止激励任务、看广告领权益、广告应用下载与相关统计服务启动",
            )
            this[SettingsUserState.KEY_DISABLE_THUMBNAIL_OPERATOR_SERVICE] = text(
                "缩略图端计算任务",
                "阻止端计算管理器初始化和缩略图任务提交",
            )
            this[SettingsUserState.KEY_DISABLE_VIDEO_AD_PRELOAD] = text(
                "视频前贴广告素材预下载",
                "阻止前后台切回时预下载视频前贴广告素材，不影响普通视频播放链路",
            )
            this[SettingsUserState.KEY_ENABLE_NIGHT_MODE_SUPPORT] = text(
                UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_LABEL,
                UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_DESC,
            )
            this[SettingsUserState.KEY_FOLLOW_SYSTEM_NIGHT_MODE] = text(
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_LABEL,
                UiText.Settings.FOLLOW_SYSTEM_NIGHT_MODE_INTL_DESC,
            )
        }
    }

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
            SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG to text(
                UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_LABEL,
                UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_DESC,
            ),
            SettingsUserState.KEY_BLOCK_NOTIFICATION_PROMPT to text(
                UiText.Settings.BLOCK_NOTIFICATION_PROMPT_LABEL,
                UiText.Settings.BLOCK_NOTIFICATION_PROMPT_DESC,
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
            SettingsUserState.KEY_HIDE_HOME_TOOLBAR to text(
                UiText.Settings.HIDE_HOME_TOOLBAR_LABEL,
                UiText.Settings.HIDE_HOME_TOOLBAR_DESC,
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
            SettingsUserState.KEY_FILE_PAGE_CUSTOMIZE to text(
                UiText.Settings.FILE_PAGE_CUSTOMIZE_LABEL,
                UiText.Settings.FILE_PAGE_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP to text(
                UiText.Settings.HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP_LABEL,
                UiText.Settings.HIDE_FILE_PAGE_BOTTOM_SAFETY_TIP_DESC,
            ),
            SettingsUserState.KEY_SEARCH_PAGE_CUSTOMIZE to text(
                UiText.Settings.SEARCH_PAGE_CUSTOMIZE_LABEL,
                UiText.Settings.SEARCH_PAGE_CUSTOMIZE_DESC,
            ),
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_AI_ENTRY to text(
                UiText.Settings.HIDE_SEARCH_PAGE_AI_ENTRY_LABEL,
                UiText.Settings.HIDE_SEARCH_PAGE_AI_ENTRY_DESC,
            ),
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_PLACEHOLDER to text(
                UiText.Settings.HIDE_SEARCH_PAGE_PLACEHOLDER_LABEL,
                UiText.Settings.HIDE_SEARCH_PAGE_PLACEHOLDER_DESC,
            ),
            SettingsUserState.KEY_HIDE_SEARCH_PAGE_RECOMMEND to text(
                UiText.Settings.HIDE_SEARCH_PAGE_RECOMMEND_LABEL,
                UiText.Settings.HIDE_SEARCH_PAGE_RECOMMEND_DESC,
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
            SettingsUserState.KEY_ENABLE_NIGHT_MODE_SUPPORT to text(
                UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_LABEL,
                UiText.Settings.ENABLE_NIGHT_MODE_SUPPORT_DESC,
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
        )
    }

    private fun text(label: String, description: String?): SettingsText {
        return SettingsText(label = label, description = description)
    }
}
