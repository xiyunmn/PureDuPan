package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.settings.registry.SettingsUserState
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class PopupBlockSwitchSpec(val key: String, val label: String, val description: String)

internal enum class PopupBlockSettingsSection(val title: String) {
    DOWNLOAD("下载提示"),
    GENERAL("通用提示"),
    MARKETING("运营推广"),
    MEMBERSHIP("会员优惠"),
    DESKTOP("桌面引导"),
}

internal object PopupBlockSettingsRegistry {
    fun sectionFor(key: String): PopupBlockSettingsSection = when {
        key == SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG -> PopupBlockSettingsSection.DOWNLOAD
        key in desktopKeys -> PopupBlockSettingsSection.DESKTOP
        key in membershipKeys -> PopupBlockSettingsSection.MEMBERSHIP
        key == SettingsUserState.KEY_REMOVE_HOME_FAB || key in marketingKeys -> PopupBlockSettingsSection.MARKETING
        else -> PopupBlockSettingsSection.GENERAL
    }

    private val desktopKeys = setOf(
        MarketingPopup.SEARCH_WIDGET.key,
        MarketingPopup.NOVEL_WIDGET.key,
        MarketingPopup.RECENT_WIDGET.key,
        MarketingPopup.SHARE_WIDGET.key,
        MarketingPopup.AIGC_WIDGET.key,
        MarketingPopup.STORY_WIDGET.key,
        MarketingPopup.LISTEN_NOTE_WIDGET.key,
    )
    private val membershipKeys = setOf(
        MarketingPopup.COUPON_GIFT_V3.key,
        MarketingPopup.COUPON_GIFT_V2.key,
        MarketingPopup.LIFE_COUPON.key,
        MarketingPopup.LIFE_PRODUCT.key,
        MarketingPopup.LIFE_PRODUCT_V3.key,
        MarketingPopup.LIFE_V10.key,
        MarketingPopup.LIFE_V10_REPURCHASE.key,
        MarketingPopup.LIFE_COMBO.key,
        MarketingPopup.LIFE_LIMITED.key,
        MarketingPopup.LIFE_RETENTION.key,
        MarketingPopup.LIFE_PRICE_UPGRADE.key,
        MarketingPopup.OVERDUE_UNION.key,
        MarketingPopup.OVERDUE_COUPON.key,
        MarketingPopup.OVERDUE_PRODUCT.key,
        MarketingPopup.MY_PAGE_OFFER.key,
        MarketingPopup.SEARCH_MEMBERSHIP.key,
        MarketingPopup.TRANSFER_COUPON.key,
        MarketingPopup.TRANSFER_LIMIT.key,
        MarketingPopup.TRANSFER_SPACE.key,
    )
    private val marketingKeys = MarketingPopup.entries.mapTo(hashSetOf()) { it.key }

    val specs: List<PopupBlockSwitchSpec> = listOf(
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_NON_WIFI_DOWNLOAD_DIALOG,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_LABEL,
            UiText.Settings.BLOCK_NON_WIFI_DOWNLOAD_DIALOG_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_REMOVE_HOME_FAB,
            UiText.Settings.REMOVE_HOME_FAB_LABEL,
            UiText.Settings.REMOVE_HOME_FAB_DESC,
        ),
    ) + MarketingPopupSettingsText.specs + listOf(
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_NOTIFICATION_PROMPT,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_LABEL,
            UiText.Settings.BLOCK_NOTIFICATION_PROMPT_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_UPDATE_DIALOG,
            UiText.Settings.BLOCK_UPDATE_DIALOG_LABEL,
            UiText.Settings.BLOCK_UPDATE_DIALOG_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_FULL_SCREEN_BACKUP,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_LABEL,
            UiText.Settings.BLOCK_FULL_SCREEN_BACKUP_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_APP_STORE_REVIEW,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_LABEL,
            UiText.Settings.BLOCK_APP_STORE_REVIEW_DESC,
        ),
        PopupBlockSwitchSpec(
            SettingsUserState.KEY_BLOCK_SHARE_PUSH_GUIDE,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_LABEL,
            UiText.Settings.BLOCK_SHARE_PUSH_GUIDE_DESC,
        ),
    )

}
