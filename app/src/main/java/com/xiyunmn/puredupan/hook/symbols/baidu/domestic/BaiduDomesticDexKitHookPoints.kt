package com.xiyunmn.puredupan.hook.symbols.baidu.domestic

internal object BaiduDomesticDexKitHookPoints {
    const val CLIENT_COMPUTE_MANAGER_METADATA_CLASS =
        "Lcom/baidu/netdisk/service/ClientComputeManager;"
    const val CLIENT_COMPUTE_MANAGER_INIT_METHOD = "init"
    const val CLIENT_COMPUTE_MANAGER_SERVER_CONFIG_METHOD = "serverConfig"

    const val THUMBNAIL_OPERATOR_UTIL =
        "com.baidu.netdisk.terminalcalc.compress.service.operator.ThumbnailOperatorUtil"
    const val THUMBNAIL_OPERATOR_UTIL_JVM_NAME = "ThumbnailOperatorUtil"
    const val CONFIG_COMPRESS_IMAGE =
        "com.baidu.netdisk.base.storage.config.ConfigCompressImage"
    const val THUMBNAIL_ADD_JOB_PARAM_CONTEXT = "context"
    const val THUMBNAIL_ADD_JOB_PARAM_COMPRESS_BEAN = "compressBean"
    const val THUMBNAIL_ADD_JOB_PARAM_CONFIG = "config"
    const val THUMBNAIL_ADD_JOB_PARAM_UID = "uid"
    const val THUMBNAIL_JOB_NAME_PREFIX = "thumbnail_"

    const val FLOAT_VIEW_STARTUP_TASK_METADATA_CLASS =
        "Lcom/baidu/netdisk/startup/task/FloatViewStartupTask;"
    const val FLOAT_VIEW_STARTUP_TASK_GET_TASK_NAME_METHOD = "getTaskName"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_TASK_QUERY_TIP_VIEW_METHOD = "initTaskQueryTipView"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_AUDIO_CIRCLE_VIEW_METHOD = "initAudioCircleView"
    const val FLOAT_VIEW_STARTUP_TASK_INIT_RETURN_THIRD_APP_VIEW_METHOD = "initReturnThirdAppView"
    const val FLOAT_VIEW_STARTUP_TASK_NAME = "FloatViewStartupTask"

    const val AUDIO_API_SHOW_AUDIO_CIRCLE_METHOD = "showAudioCircleViewManagerAudio"
}
