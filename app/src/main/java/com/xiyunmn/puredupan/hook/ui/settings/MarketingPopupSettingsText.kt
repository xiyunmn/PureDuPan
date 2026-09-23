package com.xiyunmn.puredupan.hook.ui.settings

import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.ui.UiText

internal object MarketingPopupSettingsText {
    val specs: List<PopupBlockSwitchSpec> = MarketingPopup.entries.map { option ->
        val (label, description) = when (option) {
            MarketingPopup.OPERATION_IMAGE -> UiText.Settings.BLOCK_IN_APP_DIALOG_LABEL to
                UiText.Settings.BLOCK_IN_APP_DIALOG_DESC
            MarketingPopup.OPERATION_ANIMATION -> "活动动画弹窗" to "屏蔽主界面的动画活动广告"
            MarketingPopup.OPERATION_AFX -> "活动特效弹窗" to "屏蔽主界面的特效活动广告"
            MarketingPopup.NEW_USER_OFFER -> "新人福利海报" to "屏蔽首页新人活动海报"
            MarketingPopup.NEW_USER_REWARD -> "新人任务奖励" to "屏蔽首页新人任务奖励弹窗"
            MarketingPopup.NEW_USER_REWARD_V2 -> "新人任务奖励浮层" to "屏蔽首页另一种新人奖励样式"
            MarketingPopup.COIN_PROMOTION -> "金币活动海报" to "屏蔽首页、共享页的金币活动弹窗"
            MarketingPopup.COUPON_GIFT_V3 -> "幸运优惠券包" to "屏蔽首页幸运券包推荐"
            MarketingPopup.COUPON_GIFT_V2 -> "会员领券礼包" to "屏蔽首页会员领券礼包"
            MarketingPopup.LIFE_COUPON -> "会员优惠券推荐" to "屏蔽首页会员优惠券促销"
            MarketingPopup.LIFE_PRODUCT -> "会员优惠推荐" to "屏蔽首页会员商品推荐"
            MarketingPopup.LIFE_PRODUCT_V3 -> "会员优惠推荐卡" to "屏蔽首页会员商品推荐卡片"
            MarketingPopup.LIFE_V10 -> "V10 权益推广" to "屏蔽首页 V10 权益购买引导"
            MarketingPopup.LIFE_V10_REPURCHASE -> "V10 复购优惠" to "屏蔽首页 V10 复购推荐"
            MarketingPopup.LIFE_COMBO -> "会员组合优惠" to "屏蔽首页会员组合购买推荐"
            MarketingPopup.LIFE_LIMITED -> "会员限时优惠" to "屏蔽首页会员限时权益推荐"
            MarketingPopup.LIFE_RETENTION -> "会员挽留优惠" to "屏蔽首页会员挽留促销"
            MarketingPopup.LIFE_PRICE_UPGRADE -> "会员升级优惠" to "屏蔽首页会员价格升级推荐"
            MarketingPopup.OVERDUE_UNION -> "联合会员续费" to "屏蔽首页联合会员到期续费推荐"
            MarketingPopup.OVERDUE_COUPON -> "到期领券续费" to "屏蔽首页会员到期领券推荐"
            MarketingPopup.OVERDUE_PRODUCT -> "到期购买推荐" to "屏蔽首页会员到期商品推荐"
            MarketingPopup.MY_PAGE_OFFER -> "我的页会员优惠" to "屏蔽我的页弹出的会员优惠网页"
            MarketingPopup.INCENTIVE_ENTRANCE -> "广告领空间入口" to "屏蔽主界面看广告领空间的弹窗"
            MarketingPopup.INCENTIVE_GUIDE -> "广告领权益引导" to "屏蔽主界面看广告领权益的引导"
            MarketingPopup.INCENTIVE_NEXT -> "续看广告推荐" to "屏蔽主界面领权益后再看广告的推荐"
            MarketingPopup.FREE_MODE -> "免费模式推广" to "屏蔽我的页免费模式广告引导"
            MarketingPopup.FREE_MODE_NEW -> "免费模式推广卡" to "屏蔽我的页另一种免费模式推广样式"
            MarketingPopup.FREE_MODE_FLOAT -> "免费模式广告浮条" to "屏蔽主界面、搜索页的广告权益浮条"
            MarketingPopup.MIGHTY_MARKETING -> "底部营销引导" to "屏蔽主界面底部会员、广告引导，保留普通消息"
            MarketingPopup.MODERATE_MARKETING -> "顶部营销浮条" to "屏蔽主界面顶部会员、广告浮条，保留普通消息"
            MarketingPopup.SEARCH_MEMBERSHIP -> "搜索会员购买引导" to "屏蔽全文、图片文字、高级搜索的会员购买弹窗"
            MarketingPopup.TRANSFER_COUPON -> "转存成功优惠推荐" to "屏蔽转存成功后的优惠券推荐"
            MarketingPopup.TRANSFER_LIMIT -> "转存数量升级引导" to "移除数量超限时的会员导购，保留失败提示"
            MarketingPopup.TRANSFER_SPACE -> "转存空间扩容引导" to "移除空间不足时的扩容导购，保留失败提示"
        }
        PopupBlockSwitchSpec(option.key, label, description)
    }
}
