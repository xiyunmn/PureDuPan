package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui.search

import android.content.Intent
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlSearchHookPoints
import org.json.JSONObject
import java.util.ArrayList
import java.util.HashMap
import java.lang.reflect.Method

internal object IntlSearchPageCustomizeHook {
    private const val TAG = "IntlSearchPageCustomizeHook"

    private val installedMethods = mutableSetOf<Method>()

    @Volatile
    private var isSearchRouteActive = false

    @Synchronized
    fun hook(cl: ClassLoader) {
        if (!isEnabled()) return
        val mod = XposedCompat.module ?: return
        // Pigeon hydrates Flutter preferences when the engine starts, before the search route opens.
        var installed = hookSearchStorage(cl) + hookSearchPlaceholder(cl) + hookAdvancedSearchBannerExperiment(cl)
        val activityClass = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.FLUTTER_BUSINESS_ACTIVITY, cl)
        if (activityClass != null && HookSettings.isSearchPageRecommendHidden) {
            installed += installOnce(XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java)) { method ->
                mod.hook(method).intercept { chain ->
                    isSearchRouteActive = isSearchActivity(chain.thisObject)
                    chain.proceed()
                }
            }
            installed += installOnce(XposedCompat.findMethodOrNull(activityClass, "onResume")) { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    isSearchRouteActive = isSearchActivity(chain.thisObject)
                    result
                }
            }
            installed += installOnce(XposedCompat.findMethodOrNull(activityClass, "onPause")) { method ->
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isSearchActivity(chain.thisObject)) isSearchRouteActive = false
                    result
                }
            }
            val resultClass = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.FLUTTER_RESULT_HANDLER, cl)
            val resultMethod = resultClass?.let {
                XposedCompat.findMethodOrNull(it, BaiduIntlSearchHookPoints.RESULT_SUCCESS_METHOD, Any::class.java)
            }
            installed += installOnce(resultMethod) { method ->
                mod.hook(method).intercept { chain ->
                    val original = chain.args.firstOrNull()
                    val replacement = sanitizeFlutterResult(original)
                    if (replacement !== original) chain.proceed(arrayOf(replacement)) else chain.proceed()
                }
            }
        }
        XposedCompat.log("[$TAG] hooks installed: count=$installed total=${installedMethods.size}")
    }

    private fun hookSearchPlaceholder(cl: ClassLoader): Int {
        if (!HookSettings.isSearchPagePlaceholderHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val fragmentClass = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.FLUTTER_BUSINESS_FRAGMENT, cl)
            ?: return 0
        // The public getters are inherited from FlutterBoostFragment in 13.11.13.
        // Filter the final route arguments, after both Bundle and JSON parameters have merged.
        val routeGetter = runCatching {
            fragmentClass.getMethod(BaiduIntlSearchHookPoints.FLUTTER_ROUTE_METHOD)
        }.getOrNull() ?: return 0
        val paramsGetter = runCatching {
            fragmentClass.getMethod(BaiduIntlSearchHookPoints.FLUTTER_ROUTE_PARAMS_METHOD)
        }.getOrNull()?.takeIf { Map::class.java.isAssignableFrom(it.returnType) }
        return installOnce(paramsGetter) { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isSearchPagePlaceholderHidden &&
                    fragmentClass.isInstance(chain.thisObject) && result is Map<*, *>
                ) {
                    val route = runCatching { routeGetter.invoke(chain.thisObject) }.getOrNull()
                    IntlSearchPlaceholderRules.sanitize(route, result)
                } else result
            }
        }
    }

    private fun hookSearchStorage(cl: ClassLoader): Int {
        if (!HookSettings.isSearchPageHistoryHidden && !HookSettings.isSearchPageRecommendHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(BaiduIntlSearchHookPoints.FLUTTER_PREFERENCES_PLUGIN, cl)
            ?: return 0
        val getter = XposedCompat.findMethodOrNull(clazz, "getAll", String::class.java, List::class.java)
            ?.takeIf { Map::class.java.isAssignableFrom(it.returnType) }
        return installOnce(getter) { method ->
            mod.hook(method).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isSearchPageCustomizeEnabled && result is Map<*, *>) {
                    IntlSearchStorageRules.sanitize(
                        result,
                        hideHistory = HookSettings.isSearchPageHistoryHidden,
                        hideRecommend = HookSettings.isSearchPageRecommendHidden,
                    )
                } else result
            }
        }
    }

    private fun hookAdvancedSearchBannerExperiment(cl: ClassLoader): Int {
        if (!HookSettings.isIntlSearchPageSvipBannerHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val companion = XposedCompat.findClassOrNull(
            BaiduIntlSearchHookPoints.EXPERIMENT_CONTEXT_COMPANION, cl,
        ) ?: return 0
        return BaiduIntlSearchHookPoints.advancedSearchBannerMethods.sumOf { name ->
            val target = XposedCompat.findMethodOrNull(companion, name)
                ?.takeIf { it.returnType == Boolean::class.javaObjectType }
            installOnce(target) { method ->
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isSearchPageCustomizeEnabled && HookSettings.isIntlSearchPageSvipBannerHidden) {
                        false
                    } else chain.proceed()
                }
            }
        }
    }

    private fun installOnce(method: Method?, install: (Method) -> Unit): Int {
        if (method == null || method in installedMethods) return 0
        return runCatching {
            install(method)
            installedMethods += method
            1
        }.getOrElse {
            XposedCompat.logW("[$TAG] ${method.name} install failed: ${it.message}")
            0
        }
    }

    private fun sanitizeFlutterResult(value: Any?): Any? {
        if (!isSearchRouteActive || !HookSettings.isSearchPageCustomizeEnabled || value == null) {
            return value
        }
        val text = runCatching { value.toString() }.getOrDefault("")
        val className = runCatching { value.javaClass.name }.getOrDefault("")
        if (HookSettings.isSearchPageRecommendHidden) {
            when {
                className == "java.lang.String" && isRecommendUrl(text) -> {
                    XposedCompat.logD("[$TAG] blocked search recommend url")
                    return BaiduIntlSearchHookPoints.BLOCKED_URL
                }
                value is Map<*, *> && containsAny(text, BaiduIntlSearchHookPoints.recommendPayloadMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend payload result")
                    return HashMap<Any?, Any?>()
                }
                value is List<*> && containsAny(text, BaiduIntlSearchHookPoints.recommendTagMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend tag result")
                    return ArrayList<Any?>()
                }
                value is List<*> && containsAny(text, BaiduIntlSearchHookPoints.recommendPayloadMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend payload list")
                    return ArrayList<Any?>()
                }
                value is JSONObject && containsAny(text, BaiduIntlSearchHookPoints.recommendPayloadMarkers) -> {
                    XposedCompat.logD("[$TAG] cleared search recommend json result")
                    return JSONObject()
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

    private fun isRecommendUrl(text: String): Boolean {
        return containsAny(text, BaiduIntlSearchHookPoints.recommendNetworkPaths)
    }

    private fun containsAny(text: String, markers: Collection<String>): Boolean {
        return markers.any { marker -> text.contains(marker) }
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isSearchPageCustomizeEnabled &&
            (HookSettings.isSearchPageHistoryHidden ||
                HookSettings.isSearchPagePlaceholderHidden ||
                HookSettings.isSearchPageRecommendHidden ||
                HookSettings.isIntlSearchPageSvipBannerHidden)
    }
}
