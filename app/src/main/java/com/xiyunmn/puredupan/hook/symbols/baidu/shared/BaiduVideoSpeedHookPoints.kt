package com.xiyunmn.puredupan.hook.symbols.baidu.shared

/**
 * Stable anchors for unlocking online video playback speed.
 *
 * Privilege entry and Present class names stay readable across weak/strong
 * domestic builds and the intl host; Present method names may be obfuscated.
 */
internal object BaiduVideoSpeedHookPoints {
    const val MEMBER_PRIVILEGE_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext"
    const val MEMBER_PRIVILEGE_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext\$Companion"
    const val VIDEO_SPEED_UP_PRESENT =
        "com.baidu.netdisk.video.logic.layer.area.speed.VideoSpeedUpPresent"
    const val VIDEO_PRIVILEGE =
        "com.baidu.netdisk.video.business.VideoPrivilege"

    /**
     * 13.11.9（intl）R8 全局剥离 @Metadata 后，明文类 `VideoPrivilege` 消失，
     * 真实类混淆为 `sz0.a`（ctor 取 FragmentActivity，入口 `sz0.b._(Context)`）。
     * `onLineSpeedEnable(boolean)` → `c(boolean)`，`speedEnable(SpeedPanelUIState)` → `g(SpeedPanelUIState)`，
     * 两者方法体均调 [PRIVILEGE_MEDIA_SPEED_ENABLE_METHOD]。owner 现改用
     * `boolean X(SpeedPanelUIState)` 方法形状锚定（intl 全 APK 仅 sz0.a 声明此形状），
     * 摆脱已被剥离的 @Metadata。旧明文类名保留兼容弱混淆样本。
     */
    const val VIDEO_PRIVILEGE_OBFUSCATED = "sz0.a"

    const val SPEED_PANEL_UI_STATE =
        "com.baidu.netdisk.video.logic.layer.area.speed.SpeedPanelUIState"

    const val PRIVILEGE_MEDIA_SPEED_ENABLE_METHOD = "privilegeMediaSpeedEnable"
    const val HAS_SPEED_PRIVILEGE_METHOD = "hasSpeedPrivilege"
    const val IS_SPEED_UP_ONLINE_ENABLE_METHOD = "isSpeedUpOnlineEnable"
    const val SPEED_ENABLE_METHOD = "speedEnable"
    const val ONLINE_SPEED_ENABLE_METHOD = "onLineSpeedEnable"

    const val E_VIDEO_PRIVILEGE_KEY = "e_video_privilege"
    const val PRIVILEGE_MEDIA_SPEED_ENABLE_STAT_KEY = "privilege_media_speed_enable"

    const val PRESENT_METADATA_TOKEN =
        "Lcom/baidu/netdisk/video/logic/layer/area/speed/VideoSpeedUpPresent;"
    const val VIDEO_PRIVILEGE_METADATA_TOKEN =
        "Lcom/baidu/netdisk/video/business/VideoPrivilege;"
    const val VIDEO_PRIVILEGE_SIMPLE_NAME = "VideoPrivilege"
    const val PRESENT_SIMPLE_NAME = "VideoSpeedUpPresent"
    const val SPEED_PANEL_UI_STATE_SIMPLE_NAME = "SpeedPanelUIState"
}
