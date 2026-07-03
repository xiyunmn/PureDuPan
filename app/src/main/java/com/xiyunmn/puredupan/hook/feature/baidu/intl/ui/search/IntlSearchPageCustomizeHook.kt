package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import android.content.Intent
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchHookPoints
import java.util.ArrayList
import java.util.HashMap

internal object IntlSearchPageCustomizeHook {
    private const val TAG = "IntlSearchPageCustomizeHook"

    private val hookState = HookState()

    @Volatile
    private var isSearchRouteActive = false

    fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[$TAG] skipped: config disabled")
            return
        }
        if (!hookState.markInstalled()) return

        try {
            val activityClass = XposedCompat.findClassOrNull(
                BaiduIntlSearchHookPoints.FLUTTER_BUSINESS_ACTIVITY,
                cl,
            ) ?: run {
                hookState.reset()
                XposedCompat.log("[$TAG] FlutterBusinessActivity NOT FOUND")
                return
            }
            val mod = XposedCompat.module ?: run {
                hookState.reset()
                return
            }
            var installed = 0

            XposedCompat.findMethodOrNull(
                activityClass,
                BaiduIntlSearchHookPoints.ON_CREATE_METHOD,
                Bundle::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    isSearchRouteActive = isSearchActivity(chain.thisObject)
                    chain.proceed()
                }
                installed++
            } ?: XposedCompat.log("[$TAG] FlutterBusinessActivity.onCreate(Bundle) NOT FOUND")

            XposedCompat.findMethodOrNull(activityClass, BaiduIntlSearchHookPoints.ON_RESUME_METHOD)?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    isSearchRouteActive = isSearchActivity(chain.thisObject)
                    result
                }
                installed++
            } ?: XposedCompat.log("[$TAG] FlutterBusinessActivity.onResume() NOT FOUND")

            XposedCompat.findMethodOrNull(activityClass, BaiduIntlSearchHookPoints.ON_PAUSE_METHOD)?.let { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isSearchActivity(chain.thisObject)) {
                        isSearchRouteActive = false
                    }
                    result
                }
                installed++
            } ?: XposedCompat.log("[$TAG] FlutterBusinessActivity.onPause() NOT FOUND")

            val resultHandlerClass = XposedCompat.findClassOrNull(
                BaiduIntlSearchHookPoints.FLUTTER_RESULT_HANDLER,
                cl,
            )
            if (resultHandlerClass == null) {
                XposedCompat.log("[$TAG] Flutter result handler NOT FOUND")
            } else {
                XposedCompat.findMethodOrNull(
                    resultHandlerClass,
                    BaiduIntlSearchHookPoints.RESULT_SUCCESS_METHOD,
                    Any::class.java,
                )?.let { method ->
                    mod.hook(method).intercept { chain ->
                        val original = chain.args.firstOrNull()
                        val replacement = sanitizeFlutterResult(original)
                        if (replacement !== original) {
                            chain.proceed(arrayOf(replacement))
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                } ?: XposedCompat.log("[$TAG] MethodChannel.Result.success(Object) NOT FOUND")
            }

            installed += hookAdvancedSearchBannerExperiment(cl)

            if (installed == 0) {
                hookState.reset()
                XposedCompat.log("[$TAG] no hooks installed")
                return
            }
            XposedCompat.log("[$TAG] hooks INSTALLED: count=$installed")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[$TAG] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookAdvancedSearchBannerExperiment(cl: ClassLoader): Int {
        if (!HookSettings.isIntlSearchPageSvipBannerHidden) return 0
        val mod = XposedCompat.module ?: return 0
        var installed = 0

        val experimentConfigClass = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.EXPERIMENT_CONFIG, cl)
        if (experimentConfigClass == null) {
            XposedCompat.log("[$TAG] ExperimentConfig NOT FOUND")
        } else {
            listOf(
                BaiduIntlSearchHookPoints.GET_ADVANCED_SEARCH_BEFORE_BANNER_METHOD,
                BaiduIntlSearchHookPoints.GET_ADVANCED_SEARCH_AFTER_BANNER_METHOD,
            ).forEach { methodName ->
                XposedCompat.findMethodOrNull(experimentConfigClass, methodName)?.let { method ->
                    mod.hook(method).intercept { chain ->
                        if (
                            HookSettings.isSearchPageCustomizeEnabled &&
                            HookSettings.isIntlSearchPageSvipBannerHidden
                        ) {
                            BaiduIntlSearchHookPoints.DISABLED_EXPERIMENT_VALUE
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                } ?: XposedCompat.log("[$TAG] ExperimentConfig.$methodName() NOT FOUND")
            }
        }

        val experimentStoreClass = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.EXPERIMENT_CONFIG_STORE, cl)
        if (experimentStoreClass == null) {
            XposedCompat.log("[$TAG] ExperimentConfig store NOT FOUND")
        } else {
            XposedCompat.findMethodOrNull(
                experimentStoreClass,
                BaiduIntlSearchHookPoints.GET_SCENE_EXPERIMENT_INT_METHOD,
                String::class.java,
            )?.let { method ->
                mod.hook(method).intercept { chain ->
                    val key = chain.args.firstOrNull() as? String
                    if (
                        HookSettings.isSearchPageCustomizeEnabled &&
                        HookSettings.isIntlSearchPageSvipBannerHidden &&
                        key in BaiduIntlSearchHookPoints.advancedSearchBannerExperimentKeys
                    ) {
                        BaiduIntlSearchHookPoints.DISABLED_EXPERIMENT_VALUE
                    } else {
                        chain.proceed()
                    }
                }
                installed++
            } ?: XposedCompat.log("[$TAG] ExperimentConfigStore.getSceneExperimentInt(String) NOT FOUND")
        }

        return installed
    }

    private fun sanitizeFlutterResult(value: Any?): Any? {
        if (!isSearchRouteActive || !HookSettings.isSearchPageCustomizeEnabled || value == null) {
            return value
        }
        val text = runCatching { value.toString() }.getOrDefault("")
        val className = runCatching { value.javaClass.name }.getOrDefault("")
        if (HookSettings.isSearchPagePlaceholderHidden) {
            when {
                className == "java.lang.String" && isSearchHintUrl(text) -> {
                    XposedCompat.logD("[$TAG] blocked search hint url")
                    return BaiduIntlSearchHookPoints.BLOCKED_URL
                }
            }
        }
        if (HookSettings.isSearchPageHistoryHidden) {
            when {
                className == "java.lang.String" && isHistoryUrl(text) -> {
                    XposedCompat.logD("[$TAG] blocked search history url")
                    return BaiduIntlSearchHookPoints.BLOCKED_URL
                }
                isMapClass(className) && containsAny(text, BaiduIntlSearchHookPoints.historyStorageMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search history storage result")
                    return HashMap<Any?, Any?>()
                }
            }
        }
        if (HookSettings.isSearchPageRecommendHidden) {
            when {
                className == "java.lang.String" && isRecommendUrl(text) -> {
                    XposedCompat.logD("[$TAG] blocked search recommend url")
                    return BaiduIntlSearchHookPoints.BLOCKED_URL
                }
                isMapClass(className) && containsAny(text, BaiduIntlSearchHookPoints.recommendStorageMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend storage result")
                    return HashMap<Any?, Any?>()
                }
                className == "java.util.ArrayList" &&
                    containsAny(text, BaiduIntlSearchHookPoints.recommendTagMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend tag result")
                    return ArrayList<Any?>()
                }
            }
        }
        return value
    }

    private fun isSearchActivity(activity: Any?): Boolean {
        if (activity == null) return false
        val fieldPathMatches = runCatching {
            XposedCompat.findField(activity.javaClass, BaiduIntlSearchHookPoints.PATH_FIELD).get(activity) ==
                BaiduIntlSearchHookPoints.SEARCH_ROUTE
        }.getOrDefault(false)
        if (fieldPathMatches) return true

        return (runCatching {
            val intent = activity.javaClass.getMethod("getIntent").invoke(activity) as? Intent
            val extras = intent?.extras
            extras?.getString(BaiduIntlSearchHookPoints.PATH_INTENT_EXTRA) ?:
                extras?.getString(BaiduIntlSearchHookPoints.EXTRA_PATH_INTENT_EXTRA)
        }.getOrNull()) == BaiduIntlSearchHookPoints.SEARCH_ROUTE
    }

    private fun isSearchHintUrl(text: String): Boolean {
        return containsAny(text, BaiduIntlSearchHookPoints.searchHintNetworkPaths)
    }

    private fun isHistoryUrl(text: String): Boolean {
        return containsAny(text, BaiduIntlSearchHookPoints.historyNetworkPaths)
    }

    private fun isRecommendUrl(text: String): Boolean {
        return containsAny(text, BaiduIntlSearchHookPoints.recommendNetworkPaths)
    }

    private fun containsAny(text: String, markers: Collection<String>): Boolean {
        return markers.any { marker -> text.contains(marker) }
    }

    private fun isMapClass(className: String): Boolean {
        return className == "java.util.HashMap" || className == "java.util.LinkedHashMap"
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isSearchPageCustomizeEnabled &&
            (HookSettings.isSearchPagePlaceholderHidden ||
                HookSettings.isSearchPageHistoryHidden ||
                HookSettings.isSearchPageRecommendHidden ||
                HookSettings.isIntlSearchPageSvipBannerHidden)
    }
}
