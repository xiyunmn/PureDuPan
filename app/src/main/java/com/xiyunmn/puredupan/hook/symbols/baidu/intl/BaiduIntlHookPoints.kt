package com.xiyunmn.puredupan.hook.symbols.baidu.intl

/**
 * Stable hook points verified against Baidu Netdisk international host.
 */
internal object BaiduIntlHookPoints {
    const val BASE_ACTIVITY = "com.baidu.netdisk.BaseActivity"
    const val SETTINGS_ACTIVITY = "com.baidu.netdisk.ui.SettingsActivity"
    const val SKIN_MANAGER = "com.netdisk.themeskin.loader.SkinManager"
    const val SKIN_CONFIG = "com.netdisk.themeskin.SkinConfig"
    const val SKIN_LOADER_LISTENER = "com.netdisk.themeskin.listener.SkinLoaderListener"
    const val SETTINGS_ITEM_VIEW = "com.baidu.netdisk.ui.widget.SettingsItemView"
    const val DARK_SETTINGS_ID_NAME = "dark_settings"
    const val DARK_SKIN_THEME = "dark_theme.skin"

    const val DEFAULT_MAIN_ACTIVITY = "com.baidu.netdisk.ui.DefaultMainActivity"
    const val NAVIGATE_ACTIVITY = "com.baidu.netdisk.ui.Navigate"
    const val SPLASH_AD_ACTIVITY = "com.baidu.netdisk.advertise.ui.SplashAdActivity"
    const val AUDIO_API = "com.baidu.netdisk.audio.main.provider.MAudioApi"
    const val AUDIO_PLAYER_ACTIVITY = "com.baidu.netdisk.audio.ui.AudioPlayerActivity"
    const val TASK_QUERY_API = "com.baidu.netdisk.component.filesystem.provider.TaskQueryApi"
    const val TASK_SCORE_MANAGER = "com.baidu.netdisk.task.TaskScoreManager"
    const val EXTERNAL_ACCOUNT_UTILS = "com.baidu.netdisk.account.external.AccountUtils"
    const val VIP_CHANNEL_ACTIVITY = "com.baidu.netdisk.ui.vipchannel.VipChannelActivity"
    const val RETURN_THIRD_APP_VIEW = "com.baidu.netdisk.module.sharelink.ReturnThirdAppView"

    const val AIGC_CLOUD_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_aigc_cloud.AigcCloudContext"
    const val AIGC_CLOUD_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_aigc_cloud.AigcCloudContext\$Companion"
    const val AIGC_CLOUD_AGGREGATE =
        "rubik.generate.aggregate.bd_netdisk_com_baidu_netdisk_aigc_cloud.AigcCloudAggregate"

    const val NEW_FEED_HOME_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_newfeedhome.NewfeedhomeContext"
    const val NEW_FEED_HOME_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_newfeedhome.NewfeedhomeContext\$Companion"
    const val NEW_FEED_HOME_TITLE_BAR_FRAGMENT =
        "com.baidu.netdisk.newfeedhome.feedhome.ui.view.fragment.FHTitleBarFragment"

    const val DYNAMIC_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_dynamic.DynamicContext"
    const val FAST_WEB_VIEW_CLIENT = "com.baidu.netdisk.webview.FastWebViewClient"
    const val OFFLINE_H5_PACKAGE_ACTIVITY = "com.baidu.netdisk.ui.webview.OfflineH5PackageActivity"

    const val ALBUM_CONTEXT =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_service_album.AlbumContext"
    const val ALBUM_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_service_album.AlbumContext\$Companion"
    const val ALBUM_AGGREGATE =
        "rubik.generate.aggregate.bd_netdisk_com_baidu_netdisk_service_album.AlbumAggregate"
    val ALBUM_AI_ENTRY_ACTIVITIES = listOf(
        "com.baidu.netdisk.service.album.preview.ui.view.AiPanelActivity",
        "com.baidu.netdisk.service.album.preview.ui.view.AiSearchActivity",
        "com.baidu.netdisk.service.album.nlp.ui.view.ImagePreviewNlpActivity",
        "com.baidu.netdisk.cloudimage.ailab.AiLabRouterActivity",
        "com.mars.united.aigc.i2i.ui.view.AigcI2IActivity",
    )

    const val ABOUT_ME_TOP_FRAGMENT =
        "com.baidu.netdisk.ui.aboutme.view.AboutMeTopFragment"
    const val ABOUT_ME_TOP_FRAGMENT_ON_VIEW_CREATED_METHOD = "onViewCreated"
}
