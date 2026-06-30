package com.xiyunmn.puredupan.hook.symbols.baidu.intl

internal object BaiduIntlSocketHookPoints {
    const val SOCKET_PACKAGE_PREFIX = "com.baidu.netdisk.socket."
    const val SOCKET_MANAGER_13_11_CLASS = "com.baidu.netdisk.socket.____"
    const val SOCKET_REGISTER_13_11_METHOD = "e"

    const val CLOUD_FILE_DIFF_ACTION = "cloudfile_diff_action"
    const val CLOUD_FILE_DIFF_ACTION_COMPAT = "cloud_file_diff_acton"
    const val CLOUD_FILE_DIFF_CHECK_ACTION = "cloud_file_diff_check_action"
    const val CLOUD_IMAGE_DIFF_ACTION = "cloud_image_diff_acton"
    const val CLOUD_VIDEO_DIFF_ACTION = "cloud_video_diff_acton"
    const val SEARCH_DIFF_ACTION = "search_diff_acton"

    val REQUIRED_DIFF_ACTIONS = listOf(
        CLOUD_FILE_DIFF_ACTION,
        CLOUD_FILE_DIFF_ACTION_COMPAT,
        CLOUD_FILE_DIFF_CHECK_ACTION,
        CLOUD_IMAGE_DIFF_ACTION,
        CLOUD_VIDEO_DIFF_ACTION,
        SEARCH_DIFF_ACTION,
    )

    val IMAGE_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.cloudimage.ui.view.AlbumActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumServiceActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumTimelineActivity",
        "com.baidu.netdisk.cloudimage.ui.view.AlbumTimelineFromShortcutActivity",
        "com.baidu.netdisk.cloudimage.ui.view.FoundAlbumListActivity",
        "com.baidu.netdisk.cloudimage.ui.view.FoundAlbumDetailActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.ImageSearchActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchPhotoActivity",
    )

    val VIDEO_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.servicepage.video.ui.VideoServiceActivity",
        "com.baidu.netdisk.servicepage.video.ui.VideoTimeRecentActivity",
        "com.baidu.netdisk.servicepage.video.ui.VideoCompilationsActivity",
        "com.baidu.netdisk.video.VideoPlayerActivity",
        "com.baidu.netdisk.video.VerticalVideoPlayerActivity",
        "com.baidu.netdisk.ui.VideoLauncherActivity",
    )

    val SEARCH_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.ui.cloudfile.HomeSearchActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchCategoryActivity",
        "com.baidu.netdisk.ui.cloudfile.SearchDirectoryActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.ImageSearchActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchPhotoActivity",
        "com.baidu.netdisk.cloudimage.search.ui.view.SearchTimeLineActivity",
    )
}
