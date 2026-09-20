package com.xiyunmn.puredupan.hook.symbols.baidu.intl

internal object BaiduIntlSearchHookPoints {
    const val FLUTTER_BUSINESS_ACTIVITY = "com.baidu.netdisk.flutter.ui.FlutterBusinessActivity"
    const val FLUTTER_BUSINESS_FRAGMENT = "com.baidu.netdisk.flutter.ui.FlutterBusinessFragment"
    const val FLUTTER_ROUTE_METHOD = "getUrl"
    const val FLUTTER_ROUTE_PARAMS_METHOD = "getUrlParams"
    const val FLUTTER_PREFERENCES_PLUGIN = "io.flutter.plugins.sharedpreferences.LegacySharedPreferencesPlugin"
    const val SEARCH_ROUTE = "/netdisk/search"
    const val FLUTTER_RESULT_HANDLER = "io.flutter.plugin.common.MethodChannel\$IncomingMethodCallHandler\$1"

    const val EXPERIMENT_CONTEXT_COMPANION =
        "rubik.generate.context.bd_netdisk_com_baidu_netdisk_component_experiment.ExperimentContext\$Companion"

    const val PATH_FIELD = "path"
    const val RESULT_SUCCESS_METHOD = "success"

    const val PATH_INTENT_EXTRA = "path"
    const val EXTRA_PATH_INTENT_EXTRA = "extra_path"

    const val STATIC_KEYWORD_PARAM = "staticKeyword"
    const val RECOMMEND_WORD_PARAM = "recommendWord"
    const val AUTO_SEARCH_PARAM = "autoSearch"

    const val BLOCKED_URL = "/puredupan/blocked"

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

    val advancedSearchBannerMethods = listOf(
        "isAdvancedSearchBeforeBannerEnabled",
        "isAdvancedSearchAfterBannerEnabled",
    )
}
