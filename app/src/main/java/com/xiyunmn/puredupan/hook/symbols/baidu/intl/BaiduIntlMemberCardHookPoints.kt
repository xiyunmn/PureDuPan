package com.xiyunmn.puredupan.hook.symbols.baidu.intl

/** 国际版会员卡入口及定位标识。通过 setCardUi 对 setCardText 的调用区分渲染方法，控件按资源名定位。 */
internal object BaiduIntlMemberCardHookPoints {
    const val USER_CENTER_FRAGMENT = "com.baidu.netdisk.ui.aboutme.view.UserCenterFragment"
    const val SET_USER_CENTER_INFO = "setUserCenterInfo"
    const val VIP_CENTER_CONFIG_RESPONSE =
        "com.baidu.netdisk.ui.businessplatform.home.io.model.VipCenterConfigResponse"
    /** 已验证的会员卡类名。 */
    const val ABOUT_ME_TOP_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragment"

    /** 渲染方法的稳定名称，混淆时通过调用关系定位。 */
    const val SET_CARD_UI_METHOD = "setCardUi"
    const val SET_CARD_TEXT_METHOD = "setCardText"

    /** 用于校验渲染方法签名的参数类型。 */
    const val CENTER_CONFIG =
        "com.baidu.netdisk.ui.businessplatform.home.io.model.CenterConfig"

    /** 用于 DexKit 类定位的 Metadata d2 标识。 */
    const val ABOUT_ME_TOP_FRAGMENT_METADATA_TOKEN =
        "Lcom/baidu/netdisk/ui/aboutme/view/AboutMeTopFragment;"
    const val SET_CARD_UI_METADATA_TOKEN = "setCardUi"
    const val SET_CARD_TEXT_METADATA_TOKEN = "setCardText"
}
