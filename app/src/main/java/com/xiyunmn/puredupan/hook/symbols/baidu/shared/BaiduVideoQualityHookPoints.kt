package com.xiyunmn.puredupan.hook.symbols.baidu.shared

/** 客户端视频画质权限的定位符号。 */
internal object BaiduVideoQualityHookPoints {
    const val MEMBER_PRIVILEGE_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext"
    const val MEMBER_PRIVILEGE_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_platform_business_member_privilege.MemberPrivilegeContext\$Companion"
    const val VIDEO_PRIVILEGE =
        "com.baidu.netdisk.video.business.VideoPrivilege"

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
