package com.xiyunmn.puredupan.hook.symbols.baidu.domestic

/**
 * 国内版/三星版会员卡（AboutMeTopFragmentHeteromo）渲染入口落点。
 *
 * 整卡由三个渲染入口 + 一个逻辑门驱动：
 * - [MY_CARD_HAS_OPERATION_METHOD](PopupResponse):boolean —— 运营位显隐逻辑门；返回 false 时
 *   宿主自身 GONE operationLayout + ivBgOperation（B 类，纯逻辑层）。
 * - [SET_CARD_TEXT_METHOD](CenterConfig):void —— 写 tvEnter/tvDurationContent/tvVipNumber/
 *   viewLine/clFirst..clFifthCard/clAboutmeTop 点击。
 * - [SET_CARD_UI_METHOD](CenterConfig, PopupResponse):void —— 调 setCardText 后设 ivVipImage、ivBg、主题。
 *
 * 混淆兼容：百度弱混淆分支与强混淆分支都会发布正式版。
 * - 弱/未混淆分支（国内 13.28.9/13.27.8、三星 13.27.8 已核验）：类名、方法名全明文，走稳定直连。
 * - 强混淆分支：类名与私有方法名被混淆，但 Kotlin @Metadata d2 数组保留明文类 token 与方法 token
 *   （同相册备份栏 9.2 已验证的抗混淆手段），由 DexKit resolver 用 [HETEROMO_METADATA_TOKEN] +
 *   方法 token 定位类，锚点方法 [MY_CARD_HAS_OPERATION_METHOD] 形状 (PopupResponse)->boolean。
 * 隐藏 binding 视图统一用明文资源 ID（resources.getIdentifier），不依赖 binding 字段反射，本就抗混淆。
 */
internal object BaiduMemberCardHeteromoHookPoints {
    /** 稳定直连类名（弱/未混淆分支）。 */
    const val ABOUT_ME_TOP_FRAGMENT_HETEROMO =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragmentHeteromo"

    /** 渲染入口方法名（弱/未混淆分支明文；强混淆分支被混淆，resolver 按签名反射定位）。 */
    const val SET_CARD_TEXT_METHOD = "setCardText"
    const val SET_CARD_UI_METHOD = "setCardUi"
    const val MY_CARD_HAS_OPERATION_METHOD = "myCardHasOperation"

    /** 签名校验用参数类型（三样本明文稳定）。 */
    const val CENTER_CONFIG =
        "com.baidu.netdisk.ui.businessplatform.home.io.model.CenterConfig"
    const val POPUP_RESPONSE = "com.baidu.netdisk.operation.io.PopupResponse"

    /** DexKit Metadata d2 锚点 token（强混淆分支保留明文）。 */
    const val HETEROMO_METADATA_TOKEN =
        "Lcom/baidu/netdisk/ui/aboutme/view/AboutMeTopFragmentHeteromo;"
    const val SET_CARD_TEXT_METADATA_TOKEN = "setCardText"
    const val SET_CARD_UI_METADATA_TOKEN = "setCardUi"
    const val MY_CARD_HAS_OPERATION_METADATA_TOKEN = "myCardHasOperation"
}
