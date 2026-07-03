package com.xiyunmn.puredupan.hook.symbols.baidu.domestic

internal object BaiduDomesticDexKitHookPoints {
    const val ADVERTISE_SDK = "com.baidu.netdisk.advertise.AdvertiseSDK"
    const val ADVERTISE_SDK_DOWNLOAD_VIDEO_FRONT_AD_METHOD = "downloadVideoFrontAd"
    const val MARS_ADVERTISE_SDK = "com.mars.advertise.MarsAdvertiseSDK"
    const val DOWNLOAD_VIDEO_FRONT_AD_JOB = "com.mars.advertise.service.DownloadVideoFrontAdJob"
    const val DOWNLOAD_VIDEO_FRONT_AD_JOB_DESCRIPTOR =
        "Lcom/mars/advertise/service/DownloadVideoFrontAdJob;"

    const val SWAN_PREFETCH_MANAGER_STABLE_CLASS =
        "com.baidu.swan.apps.core.prefetch.SwanAppPrefetchManager"
    const val SWAN_PREFETCH_MANAGER_TAG = "SwanAppPrefetchManager"
    const val SWAN_PREFETCH_MANAGER_INTERFACE = "com.baidu.swan.apps.core.prefetch.IPrefetchManager"
    const val SWAN_PREFETCH_EVENT = "com.baidu.swan.apps.core.prefetch.PrefetchEvent"
    const val SWAN_PREFETCH_ENV_CONTROLLER = "com.baidu.swan.apps.core.prefetch.PrefetchEnvController"
    const val SWAN_CLIENT_PUPPET = "com.baidu.swan.apps.process.messaging.service.SwanClientPuppet"

    const val CLIENT_COMPUTE_MANAGER_METADATA_CLASS =
        "Lcom/baidu/netdisk/service/ClientComputeManager;"
    const val CLIENT_COMPUTE_MANAGER_STABLE_CLASS =
        "com.baidu.netdisk.service.ClientComputeManager"
    const val CLIENT_COMPUTE_MANAGER_INIT_METHOD = "init"
    const val CLIENT_COMPUTE_MANAGER_SERVER_CONFIG_METHOD = "serverConfig"

    const val THUMBNAIL_OPERATOR_UTIL =
        "com.baidu.netdisk.terminalcalc.compress.service.operator.ThumbnailOperatorUtil"
    const val THUMBNAIL_OPERATOR_UTIL_JVM_NAME = "ThumbnailOperatorUtil"
    const val THUMBNAIL_ADD_JOB_STABLE_METHOD = "addJob"
    const val CONFIG_COMPRESS_IMAGE =
        "com.baidu.netdisk.base.storage.config.ConfigCompressImage"
    const val THUMBNAIL_ADD_JOB_PARAM_CONTEXT = "context"
    const val THUMBNAIL_ADD_JOB_PARAM_COMPRESS_BEAN = "compressBean"
    const val THUMBNAIL_ADD_JOB_PARAM_CONFIG = "config"
    const val THUMBNAIL_ADD_JOB_PARAM_UID = "uid"
    const val THUMBNAIL_JOB_NAME_PREFIX = "thumbnail_"

    const val FLOAT_VIEW_STARTUP_TASK_NAME = "FloatViewStartupTask"
    const val FLOAT_VIEW_STARTUP_TASK_STABLE_CLASS = "com.baidu.netdisk.startup.task.FloatViewStartupTask"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_STABLE_METHOD = "initAudioCircleView"

    const val AUDIO_API_SHOW_AUDIO_CIRCLE_METHOD = "showAudioCircleViewManagerAudio"
    const val AUDIO_API_INIT_AUDIO_CIRCLE_METHOD = "audioCircleViewInit"

    const val DYNAMIC_PLUGIN_HELPER = "com.baidu.netdisk.dynamic.IDynamicPluginHelper"
    const val DYNAMIC_PLUGIN_DOWNLOADER = "com.baidu.netdisk.dynamic.base.PluginDownloader"
    const val DYNAMIC_PLUGIN_EXECUTOR = "com.baidu.netdisk.dynamic.base.PluginExecutor"
    const val DYNAMIC_PLUGIN_SUB_DOWNLOADER =
        "com.baidu.netdisk.dynamic.base.PluginDownloader\$SubDownloader"
    const val DYNAMIC_PLUGIN_SUB_EXECUTOR =
        "com.baidu.netdisk.dynamic.base.PluginExecutor\$SubExecutor"
    const val DYNAMIC_PLUGIN_MODEL =
        "com.baidu.netdisk.dynamic.base.model.DynamicPlugin"
    const val DYNAMIC_PLUGIN_AUTO_DOWNLOAD_CALL_SITE_TOKEN =
        "downloadAllPlugins toDownloadPlugin"
    const val DYNAMIC_PLUGIN_AUTO_INSTALL_CALL_SITE_TOKEN =
        "autoInstallByPluginId"
}
