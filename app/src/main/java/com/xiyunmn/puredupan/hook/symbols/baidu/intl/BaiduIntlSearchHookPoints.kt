package com.xiyunmn.puredupan.hook.symbols.baidu.intl

internal object BaiduIntlSearchHookPoints {
    const val FLUTTER_BUSINESS_ACTIVITY = "com.baidu.netdisk.flutter.ui.FlutterBusinessActivity"
    const val SEARCH_ROUTE = "/netdisk/search"
    const val FLUTTER_RESULT_HANDLER = "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler\$1"
    const val EXPERIMENT_CONFIG = "com.baidu.component.experiment.model.ExperimentConfig"
    const val EXPERIMENT_CONFIG_STORE = "t2.___"

    const val PATH_FIELD = "path"
    const val ON_CREATE_METHOD = "onCreate"
    const val ON_RESUME_METHOD = "onResume"
    const val ON_PAUSE_METHOD = "onPause"
    const val RESULT_SUCCESS_METHOD = "success"
    const val GET_ADVANCED_SEARCH_BEFORE_BANNER_METHOD = "getAdvancedSearchBeforeBanner"
    const val GET_ADVANCED_SEARCH_AFTER_BANNER_METHOD = "getAdvancedSearchAfterBanner"
    const val GET_SCENE_EXPERIMENT_INT_METHOD = "___"

    const val PATH_INTENT_EXTRA = "path"
    const val EXTRA_PATH_INTENT_EXTRA = "extra_path"

    const val BLOCKED_URL = "/puredupan/blocked"
    const val DISABLED_EXPERIMENT_VALUE = 0

    val historyNetworkPaths = emptyList<String>()

    val recommendNetworkPaths = listOf(
        "/richsearch/recquery/get",
        "/recommend/query/list",
        "/richsearch/public/get",
        "/recent/list",
        "/recent/listv2",
    )

    const val SEARCH_HISTORY_STORAGE_KEY = "flutter.search_history"
    const val SEARCH_HISTORY_STORAGE_KEY_PREFIX = "flutter.search_history_"
    const val SEARCH_HISTORY_RECOMMEND_ITEM_STORAGE_KEY_PREFIX = "flutter.search_history_recommend_item"
    const val LAST_PERSON_RECOMMEND_STORAGE_KEY = "flutter.last_person_recommend"

    val recommendPayloadMarkers = listOf(
        "query_list",
        "recommendations",
        "hot_recommend",
        "normal_recommend",
        "cover_url",
        "android_jump_url",
        "query_confidence",
        "sug_type",
    )

    val recommendTagMarkers = listOf(
        "tag_id=",
        "tag_name=",
        "tagId=",
        "tagName=",
    )

    val advancedSearchBannerExperimentKeys = setOf(
        "advanced_search_before_banner",
        "advanced_search_after_banner",
    )
}
