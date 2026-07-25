package com.xiyunmn.puredupan.hook.symbols.baidu.shared

/**
 * Stable anchors for unlocking online video quality client gates.
 *
 * Scope is intentionally limited to video-play quality privileges only.
 * Do not use global SVIP identity hooks here.
 */
internal object BaiduVideoQualityHookPoints {
    const val MEMBER_PRIVILEGE_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext"
    const val MEMBER_PRIVILEGE_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext\$Companion"
    const val VIDEO_PRIVILEGE =
        "com.baidu.netdisk.video.business.VideoPrivilege"

    /**
     * 13.11.9（intl）R8 全局剥离 @Metadata 后，明文类 `VideoPrivilege` 消失，真实类
     * 混淆为 `sz0.a`（与倍速同一个类，ctor 取 FragmentActivity）。画质门体
     * `___/e/b/d/f` 均为 `MemberPrivilegeContext.Companion.privilegeVideoPlay*Enabled() || …`。
     * owner 改用 `boolean X(SpeedPanelUIState)` 方法形状锚定（intl 全 APK 仅 sz0.a
     * 声明此形状），摆脱已被剥离的 @Metadata。旧明文类名保留兼容弱混淆/国内样本。
     */
    const val VIDEO_PRIVILEGE_OBFUSCATED = "sz0.a"
    const val SPEED_PANEL_UI_STATE =
        "com.baidu.netdisk.video.logic.layer.area.speed.SpeedPanelUIState"

    const val PRIVILEGE_VIDEO_PLAY_HD_METHOD = "privilegeVideoPlayHdEnabled"
    const val PRIVILEGE_VIDEO_PLAY_FHD_METHOD = "privilegeVideoPlayFhdEnabled"
    const val PRIVILEGE_VIDEO_PLAY_ORIGINAL_METHOD = "privilegeVideoPlayOriginalEnabled"

    const val CAN_PLAY_720_METHOD = "canPlay720"
    const val IS_SUPPORT_FHD_METHOD = "isSupportFHD"
    const val PLAY_HD_ENABLED_METHOD = "playHdEnabled"
    const val PLAY_FHD_ENABLED_METHOD = "playFhdEnabled"
    const val PLAY_ORIGINAL_ENABLED_METHOD = "playOriginalEnabled"

    const val VIDEO_PRIVILEGE_METADATA_TOKEN =
        "Lcom/baidu/netdisk/video/business/VideoPrivilege;"
    const val VIDEO_PRIVILEGE_SIMPLE_NAME = "VideoPrivilege"
    const val SPEED_PANEL_UI_STATE_SIMPLE_NAME = "SpeedPanelUIState"
}
