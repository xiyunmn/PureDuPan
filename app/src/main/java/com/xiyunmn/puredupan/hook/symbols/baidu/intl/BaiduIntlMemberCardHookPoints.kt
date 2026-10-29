package com.xiyunmn.puredupan.hook.symbols.baidu.intl

/**
 * 国际版会员卡（benefit-slot，AboutMeTopFragment）渲染入口落点。
 *
 * 国际版 combinedLiveData 为 Pair<IdentityBean, CenterConfig>（无 PopupResponse 运营位），
 * 因此无运营位逻辑门。整卡由 setCardUi(CenterConfig) 渲染入口驱动：其中先设 ivVipImage + 主题，
 * 最后调 setCardText(CenterConfig) 写权益/续费/svip 文案。setCardUi proceed() 后所有 binding 就绪。
 *
 * 混淆兼容：13.11.8（强混淆平台）实测类名与 setCardText/setCardUi 方法名在真实 smali 中仍明文；
 * binding 字段名混淆（f84730k…），故隐藏一律用明文资源 ID，不用 binding 字段。
 * setCardUi 与 setCardText 同签名 (CenterConfig)->void，用「setCardUi 内部 invoke setCardText」判别，
 * 抗方法名混淆；类定位用 Kotlin @Metadata d2 明文锚点，抗类名混淆。
 */
internal object BaiduIntlMemberCardHookPoints {
    const val USER_CENTER_FRAGMENT = "com.baidu.netdisk.ui.aboutme.view.UserCenterFragment"
    const val SET_USER_CENTER_INFO = "setUserCenterInfo"
    const val VIP_CENTER_CONFIG_RESPONSE =
        "com.baidu.netdisk.ui.businessplatform.home.io.model.VipCenterConfigResponse"
    /** 稳定直连类名（强混淆样本仍明文）。 */
    const val ABOUT_ME_TOP_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragment"

    /** 渲染入口方法名（样本明文；混淆时 resolver 按 invoke 判别形状定位）。 */
    const val SET_CARD_UI_METHOD = "setCardUi"
    const val SET_CARD_TEXT_METHOD = "setCardText"

    /** 签名校验用参数类型（三样本明文稳定）。 */
    const val CENTER_CONFIG =
        "com.baidu.netdisk.ui.businessplatform.home.io.model.CenterConfig"

    /** DexKit Metadata d2 锚点 token（强混淆分支保留明文）。 */
    const val ABOUT_ME_TOP_FRAGMENT_METADATA_TOKEN =
        "Lcom/baidu/netdisk/ui/aboutme/view/AboutMeTopFragment;"
    const val SET_CARD_UI_METADATA_TOKEN = "setCardUi"
    const val SET_CARD_TEXT_METADATA_TOKEN = "setCardText"
}
