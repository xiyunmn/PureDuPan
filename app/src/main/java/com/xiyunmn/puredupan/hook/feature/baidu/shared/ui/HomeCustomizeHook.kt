package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.Collections
import java.util.WeakHashMap

/**
 * 顶部 AI 控件移除 Hook。
 *
 * 受首页定制配置控制，默认隐藏顶部动态推广。
 */
object HomeCustomizeHook {
    private const val SET_SEARCH_TEXT_METHOD = "setSearchText"
    private const val SEARCH_PLACEHOLDER_BINDING_FIELD = "titleBarSearchPlaceHolderText"
    private const val TEXT_FLIPPER_CLASS_TOKEN = "TextFlipper"
    private const val HOME25_TOP_CONTAINER_ID = "home25ai_v1"
    private const val HOME25_CONTENT_ID = "home25ai_content"
    private const val HOME25_SEARCHBOX_CONTENT_ID = "searchbox_content"
    private const val FEED_CONTAINER_ID = "feed_container"
    private const val INIT_FEED_SETTING_TIP_HEADER_METHOD = "initFeedSettingTipHeader"
    private const val EXPECT_KT_CLASS = "com.mars.united.core.architecture.ExpectKt"
    private const val EXPECT_SUCCESS_METHOD = "success"
    private const val NATIVE_RECENT_ITEM_LIMIT = 3

    private val hookState = HookState()
    private val recentItemLimitHookState = HookState()
    private val recentScrollRangeHookState = HookState()
    private val recentLimitForCall = ThreadLocal<List<*>?>()
    private val verticalSaveGroups = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private val verticalSaveHeightGuards = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val verticalSaveDynamicRows =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, MutableList<View>>())
    private val verticalSaveRowHeights =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, Int>())
    private val saveHistoryRequests = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val saveHistoryCache = Collections.synchronizedMap(WeakHashMap<Any, List<Any>>())

    internal fun hook(cl: ClassLoader) {
        if (!hasEnabledOption()) {
            XposedCompat.log("[HomeCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        val recentItemLimitHookCount = hookRecentCardItemLimit(cl) + hookRecentScrollRange(cl)
        if (!hookState.markInstalled()) return

        try {
            var installedCount = recentItemLimitHookCount
            installedCount += hookTopBannerView(cl)
            installedCount += hookHomeSearchPlaceholderText(cl)
            installedCount += hookHomeToolbarRenderEntry(cl)
            installedCount += hookHomeToolbarRootLayout(cl)
            installedCount += hookSearchboxAigcAnimation(cl)
            installedCount += hookRecentCardDataUseCase(cl)
            installedCount += hookSaveCardViewModel(cl)
            installedCount += hookSaveCardVerticalLayout(cl)
            installedCount += hookHiddenFeedScrollContainer(cl)
            installedCount += hookHomeStoryCardRenderEntry(cl)
            installedCount += hookHomeHeaderCardRenderEntries(cl)
            installedCount += hookFeedSettingTipRenderEntry(cl)
            installedCount += hookHomeBannerCardRenderEntry(cl)
            installedCount += hookStartupHomeBannerPreload(cl)

            if (installedCount == 0) {
                XposedCompat.log("[HomeCustomizeHook] no hooks installed")
                hookState.reset()
                return
            }
            XposedCompat.log("[HomeCustomizeHook] hooks INSTALLED: count=$installedCount")
        } catch (e: Exception) {
            hookState.reset()
            XposedCompat.log("[HomeCustomizeHook] FAILED: ${e.message}")
            XposedCompat.log(e)
        }
    }

    private fun hookTopBannerView(cl: ClassLoader): Int {
        if (!isTopPromotionHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val searchboxFragmentClassName = homeCustomizeHookPoints().searchboxFragmentClassName
        if (searchboxFragmentClassName == null) {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment host capability missing")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(
            searchboxFragmentClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, "initBanner")
            ?: run {
                XposedCompat.log("[HomeCustomizeHook] initBanner NOT FOUND")
                return 0
            }
        mod.hook(method).intercept { chain ->
            if (isTopPromotionHidden()) {
                XposedCompat.logD("[HomeCustomizeHook] HomeSearchboxFragment.initBanner blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hookHomeSearchPlaceholderText(cl: ClassLoader): Int {
        if (!isSearchPlaceholderHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val fragmentClasses = (
            points.searchTextFragmentClassNames.ifEmpty {
                listOfNotNull(points.searchboxFragmentClassName)
            }
            ).distinct()
        if (fragmentClasses.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] search text fragment host capabilities missing")
            return 0
        }

        var count = 0
        fragmentClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for search placeholder hook")
                return@forEach
            }
            val method = findSearchTextMethod(clazz) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className.$SET_SEARCH_TEXT_METHOD not found")
                return@forEach
            }
            mod.hook(method).intercept { chain ->
                if (isSearchPlaceholderHidden()) {
                    // 渲染入口 no-op：阻断 TextFlipper 文案与右侧 Drawable 写入。
                    // 布局默认文案由 onViewCreated 一次性折叠处理，不再在每次 setSearchText 后做 View 兜底。
                    XposedCompat.logD("[HomeCustomizeHook] $className.$SET_SEARCH_TEXT_METHOD blocked")
                    null
                } else {
                    chain.proceed()
                }
            }
            count += 1

            val onViewCreated = XposedCompat.findMethodOrNull(
                clazz,
                "onViewCreated",
                View::class.java,
                Bundle::class.java,
            )
            if (onViewCreated != null) {
                mod.hook(onViewCreated).intercept { chain ->
                    val result = chain.proceed()
                    if (isSearchPlaceholderHidden() && hideSearchPlaceholderBindingView(chain.thisObject)) {
                        XposedCompat.logD(
                            "[HomeCustomizeHook] $className.onViewCreated placeholder collapsed",
                        )
                    }
                    result
                }
                count += 1
            }
        }
        return count
    }

    private fun hookHomeToolbarRenderEntry(cl: ClassLoader): Int {
        if (!isHomeToolbarHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val toolbarFragmentClasses = homeCustomizeHookPoints().toolbarFragmentClassNames.distinct()
        if (toolbarFragmentClasses.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] home toolbar fragment host capabilities missing")
            return 0
        }

        var count = 0
        toolbarFragmentClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for toolbar render hook")
                return@forEach
            }

            val onCreateView = XposedCompat.findMethodOrNull(
                clazz,
                "onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Bundle::class.java,
            )
            if (onCreateView != null) {
                mod.hook(onCreateView).intercept { chain ->
                    if (isHomeToolbarHidden()) {
                        XposedCompat.logD("[HomeCustomizeHook] $className.onCreateView blocked")
                        createCollapsedView(
                            inflaterArg = chain.args.getOrNull(0),
                            containerArg = chain.args.getOrNull(1),
                        ) ?: chain.proceed()
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onCreateView not found for toolbar render hook")
            }

            val onViewCreated = XposedCompat.findMethodOrNull(
                clazz,
                "onViewCreated",
                View::class.java,
                Bundle::class.java,
            )
            if (onViewCreated != null) {
                mod.hook(onViewCreated).intercept { chain ->
                    if (isHomeToolbarHidden()) {
                        XposedCompat.logD("[HomeCustomizeHook] $className.onViewCreated blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onViewCreated not found for toolbar render hook")
            }

            val onResume = XposedCompat.findMethodOrNull(clazz, "onResume")
            if (onResume != null) {
                mod.hook(onResume).intercept { chain ->
                    if (isHomeToolbarHidden()) {
                        XposedCompat.logD("[HomeCustomizeHook] $className.onResume blocked")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onResume not found for toolbar render hook")
            }
        }
        return count
    }

    private fun hookHomeToolbarRootLayout(cl: ClassLoader): Int {
        if (!isHomeToolbarHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val rootFragmentClasses = homeCustomizeHookPoints().homeRootFragmentClassNames.distinct()
        if (rootFragmentClasses.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] home root fragment host capabilities missing")
            return 0
        }

        var count = 0
        rootFragmentClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for toolbar root layout hook")
                return@forEach
            }

            val onCreateView = XposedCompat.findMethodOrNull(
                clazz,
                "onCreateView",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Bundle::class.java,
            )
            if (onCreateView != null) {
                mod.hook(onCreateView).intercept { chain ->
                    val result = chain.proceed()
                    adjustHomeToolbarRootLayout(result as? View)
                    result
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onCreateView not found for toolbar root layout hook")
            }

            val onViewCreated = XposedCompat.findMethodOrNull(
                clazz,
                "onViewCreated",
                View::class.java,
                Bundle::class.java,
            )
            if (onViewCreated != null) {
                mod.hook(onViewCreated).intercept { chain ->
                    val result = chain.proceed()
                    adjustHomeToolbarRootLayout(chain.args.firstOrNull() as? View)
                    result
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onViewCreated not found for toolbar root layout hook")
            }
        }
        return count
    }

    private fun findSearchTextMethod(clazz: Class<*>): Method? {
        val methods = clazz.declaredMethods.filter { method ->
            method.name == SET_SEARCH_TEXT_METHOD &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1
        }
        return (
            methods.firstOrNull { method -> isSearchWordType(method.parameterTypes[0]) }
                ?: methods.singleOrNull()
            )?.apply { isAccessible = true }
    }

    private fun isSearchWordType(type: Class<*>): Boolean {
        val typeName = type.name
        return typeName.contains("FHHomeTitleViewModel\$SearchWord") ||
            (
                typeName.endsWith("\$SearchWord") &&
                    typeName.contains("FHHomeTitleViewModel")
                )
    }

    private fun createCollapsedView(inflaterArg: Any?, containerArg: Any?): View? {
        val context = (inflaterArg as? LayoutInflater)?.context
            ?: (containerArg as? View)?.context
            ?: return null
        return createCollapsedFrameLayout(context)
    }

    private fun createCollapsedFrameLayout(context: Context): FrameLayout {
        return FrameLayout(context).apply {
            visibility = View.GONE
            alpha = 0f
            isEnabled = false
            isClickable = false
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
            layoutParams = ViewGroup.LayoutParams(0, 0)
        }
    }

    private fun hookSearchboxAigcAnimation(cl: ClassLoader): Int {
        if (!HookSettings.isHomeSearchAigcIconHidden) return 0
        val mod = XposedCompat.module ?: return 0
        var count = 0
        val searchboxFragmentClassName = homeCustomizeHookPoints().searchboxFragmentClassName
        if (searchboxFragmentClassName == null) {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment host capability missing for AIGC animation")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(
            searchboxFragmentClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment class NOT FOUND for AIGC animation")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(clazz, "startSearchBoxAnim")
            ?: run {
                XposedCompat.logD("[HomeCustomizeHook] startSearchBoxAnim not found")
                return 0
            }
        mod.hook(method).intercept { chain ->
            if (!HookSettings.isHomeCustomizeEnabled || !HookSettings.isHomeSearchAigcIconHidden) {
                chain.proceed()
            } else {
                // 动画入口 no-op：不加载 mp4、不 play、不触发 border/icon 动画。
                // 布局默认可见的 searchbox_aigc_icon 由 onViewCreated 一次性折叠，不再在此做 View 兜底。
                XposedCompat.logD("[HomeCustomizeHook] HomeSearchboxFragment.startSearchBoxAnim blocked")
                getBindingObject(chain.thisObject)
            }
        }
        count += 1

        val onViewCreated = XposedCompat.findMethodOrNull(
            clazz,
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
        )
        if (onViewCreated != null) {
            mod.hook(onViewCreated).intercept { chain ->
                val result = chain.proceed()
                if (HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeSearchAigcIconHidden) {
                    hideSearchboxAigcBindingViews(chain.thisObject)
                    XposedCompat.logD(
                        "[HomeCustomizeHook] HomeSearchboxFragment.onViewCreated aigc views collapsed",
                    )
                }
                result
            }
            count += 1
        }
        return count
    }

    private fun hookRecentCardDataUseCase(cl: ClassLoader): Int {
        if (!HookSettings.isHomeRecentSectionHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val classNames = homeCustomizeHookPoints().recentCardDataUseCaseClassNames.distinct()
        if (classNames.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] recent card data use case host capability missing")
            return 0
        }
        val successMethod = XposedCompat.findClassOrNull(EXPECT_KT_CLASS, cl)
            ?.let { XposedCompat.findMethodOrNull(it, EXPECT_SUCCESS_METHOD, Any::class.java) }
            ?: run {
                XposedCompat.log("[HomeCustomizeHook] ExpectKt.success(Object) NOT FOUND")
                return 0
            }

        var count = 0
        classNames.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for recent data hook")
                return@forEach
            }
            val methods = clazz.declaredMethods.filter(::isRecentCardDataUseCaseInvokeMethod)
            if (methods.isEmpty()) {
                XposedCompat.logD("[HomeCustomizeHook] recent data invoke not found: $className")
                return@forEach
            }
            methods.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeRecentSectionHidden) {
                        XposedCompat.logD("[HomeCustomizeHook] recent card data blocked: ${clazz.name}.${method.name}")
                        successMethod.invoke(null, emptyList<Any>())
                    } else {
                        chain.proceed()
                    }
                }
            }
            count += methods.size
        }
        if (count == 0) {
            XposedCompat.log("[HomeCustomizeHook] recent card data use case invoke method NOT FOUND")
        }
        return count
    }

    private fun isRecentCardDataUseCaseInvokeMethod(method: Method): Boolean {
        return method.returnType == Any::class.java &&
            method.parameterTypes.size == 2 &&
            ArrayList::class.java.isAssignableFrom(method.parameterTypes[0]) &&
            method.parameterTypes[1].name == "kotlin.coroutines.Continuation"
    }

    private fun hookSaveCardViewModel(cl: ClassLoader): Int {
        if (!HookSettings.isHomeSaveSectionHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val classNames = points.saveCardViewModelClassNames.distinct()
        if (classNames.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] save card view model host capability missing")
            return 0
        }

        var count = 0
        classNames.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for save card hook")
                return@forEach
            }
            val hookedMethods = mutableSetOf<Method>()
            count += hookSaveCardViewModelMethods(
                mod = mod,
                clazz = clazz,
                methodNames = points.saveCardNoArgBlockedMethodNames,
                label = "save card no-arg data method",
                hookedMethods = hookedMethods,
                matcher = ::isSaveCardNoArgVoidMethod,
            )
            count += hookSaveCardViewModelMethods(
                mod = mod,
                clazz = clazz,
                methodNames = points.saveCardSetListMethodNames,
                label = "save card set list method",
                hookedMethods = hookedMethods,
                matcher = ::isSaveCardSetListMethod,
            )
            count += hookSaveCardViewModelMethods(
                mod = mod,
                clazz = clazz,
                methodNames = points.saveCardSetRecommendMethodNames,
                label = "save card recommend method",
                hookedMethods = hookedMethods,
                matcher = ::isSaveCardSetRecommendMethod,
            )
            count += hookSaveCardViewModelMethods(
                mod = mod,
                clazz = clazz,
                methodNames = points.saveCardRedPotMethodNames,
                label = "save card red pot method",
                hookedMethods = hookedMethods,
                matcher = ::isSaveCardRedPotMethod,
            )
        }
        if (count == 0) {
            XposedCompat.log("[HomeCustomizeHook] save card view model methods NOT FOUND")
        }
        return count
    }

    private fun hookSaveCardViewModelMethods(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodNames: List<String>,
        label: String,
        hookedMethods: MutableSet<Method>,
        matcher: (Method) -> Boolean,
    ): Int {
        var count = 0
        methodNames.forEach { methodName ->
            val methods = clazz.declaredMethods.filter { method ->
                method.name == methodName && matcher(method)
            }
            if (methods.isEmpty()) {
                XposedCompat.logD("[HomeCustomizeHook] $label not found: ${clazz.name}.$methodName")
                return@forEach
            }
            methods.forEach { method ->
                if (!hookedMethods.add(method)) return@forEach
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    if (HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeSaveSectionHidden) {
                        XposedCompat.logD("[HomeCustomizeHook] $label blocked: ${clazz.name}.${method.name}")
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            }
        }
        return count
    }

    private fun isSaveCardNoArgVoidMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE && method.parameterTypes.isEmpty()
    }

    private fun isSaveCardSetListMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
            java.util.List::class.java.isAssignableFrom(method.parameterTypes[1])
    }

    private fun isSaveCardSetRecommendMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 2 &&
            method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
            method.parameterTypes[1].name.endsWith(".SaveCardState")
    }

    private fun isSaveCardRedPotMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == Boolean::class.javaPrimitiveType
    }

    private fun hookRecentCardItemLimit(cl: ClassLoader): Int {
        if (!isRecentItemLimitCustomized() || HookSettings.isHomeRecentSectionHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val methodSets = HomeRecentItemLimitDexKitResolver.resolve(cl)
        if (methodSets.isEmpty()) {
            XposedCompat.logD("[HomeCustomizeHook] recent item limit methods not resolved")
            return 0
        }
        if (!recentItemLimitHookState.markInstalled()) return 0

        return try {
            methodSets.forEach { methods ->
                mod.hook(methods.source).intercept { chain ->
                    if (!isRecentItemLimitCustomized() || HookSettings.isHomeRecentSectionHidden) {
                        return@intercept chain.proceed()
                    }
                    val source = chain.args.firstOrNull() as? List<*>
                    val limited = source?.take(HookSettings.homeRecentItemLimit)
                    recentLimitForCall.set(limited)
                    XposedCompat.logD {
                        "[HomeCustomizeHook] recent source: ${methods.source.declaringClass.name}, " +
                            "source=${source?.size}, limited=${limited?.size}"
                    }
                    try {
                        val result = chain.proceed()
                        if (limited != null) {
                            runCatching {
                                methods.cacheWriter.invoke(chain.thisObject, limited)
                            }.onFailure { error ->
                                XposedCompat.logW(
                                    "[HomeCustomizeHook] recent cache rewrite failed: ${error.message}",
                                )
                            }
                            runCatching {
                                methods.stateWriter.invoke(chain.thisObject, limited)
                            }.onFailure { error ->
                                XposedCompat.logW(
                                    "[HomeCustomizeHook] recent state rewrite failed: ${error.message}",
                                )
                            }
                            XposedCompat.logD {
                                "[HomeCustomizeHook] recent final rewrite: " +
                                    "${methods.source.declaringClass.name}, size=${limited.size}"
                            }
                        }
                        result
                    } finally {
                        recentLimitForCall.remove()
                    }
                }
                listOf(methods.cacheWriter, methods.stateWriter).forEach { method ->
                    mod.hook(method).intercept { chain ->
                        val limited = recentLimitForCall.get()
                        if (
                            limited != null &&
                            isRecentItemLimitCustomized() &&
                            !HookSettings.isHomeRecentSectionHidden
                        ) {
                            chain.args[0] = limited
                        }
                        XposedCompat.logD {
                            "[HomeCustomizeHook] recent writer: " +
                                "${method.declaringClass.name}.${method.name}, " +
                                "input=${(chain.args.firstOrNull() as? List<*>)?.size}, " +
                                "activeLimit=${limited?.size}"
                        }
                        chain.proceed()
                    }
                }
            }
            XposedCompat.log(
                "[HomeCustomizeHook] recent item limit hooks installed: " +
                    "${methodSets.joinToString { it.source.declaringClass.name }}, " +
                    "limit=${HookSettings.homeRecentItemLimit}",
            )
            methodSets.size * 3
        } catch (t: Throwable) {
            recentItemLimitHookState.reset()
            XposedCompat.logW("[HomeCustomizeHook] recent item limit hook failed: ${t.message}")
            0
        }
    }

    private fun hookRecentScrollRange(cl: ClassLoader): Int {
        if (!isRecentScrollRangeAdjustmentEnabled()) return 0
        if (!homeCustomizeHookPoints().supportsRecentScrollRangeAdjustment) return 0
        val mod = XposedCompat.module ?: return 0
        val methods = HomeRecentScrollRangeDexKitResolver.resolve(cl) ?: run {
            XposedCompat.logD("[HomeCustomizeHook] recent scroll range methods not resolved")
            return 0
        }
        if (!recentScrollRangeHookState.markInstalled()) return 0

        return try {
            mod.hook(methods.callback).intercept { chain ->
                if (!isRecentScrollRangeAdjustmentEnabled()) {
                    return@intercept chain.proceed()
                }
                val fragment = chain.args.getOrNull(0) ?: return@intercept chain.proceed()
                val height = chain.args.getOrNull(2) as? Int ?: return@intercept chain.proceed()
                runCatching {
                    methods.updateSize.invoke(fragment, height)
                    XposedCompat.logD(
                        "[HomeCustomizeHook] recent scroll range updated: topHeight=$height",
                    )
                    Unit
                }.getOrElse { error ->
                    XposedCompat.logW(
                        "[HomeCustomizeHook] recent scroll range update failed: ${error.message}",
                    )
                    chain.proceed()
                }
            }
            XposedCompat.log(
                "[HomeCustomizeHook] recent scroll range hook installed: " +
                    "${methods.callback.declaringClass.name}.${methods.callback.name}",
            )
            1
        } catch (t: Throwable) {
            recentScrollRangeHookState.reset()
            XposedCompat.logW("[HomeCustomizeHook] recent scroll range hook failed: ${t.message}")
            0
        }
    }

    private fun hookSaveCardVerticalLayout(cl: ClassLoader): Int {
        if (!isSaveCardVerticalLayoutEnabled() || HookSettings.isHomeSaveSectionHidden) return 0
        val mod = XposedCompat.module ?: return 0
        var count = 0
        val groupClasses = linkedSetOf<String>()
        val points = homeCustomizeHookPoints()

        points.saveCardViewClassNames.distinct().forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            val initView = clazz.declaredMethods.firstOrNull { method ->
                method.name == "initView" &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.isEmpty()
            }?.apply { isAccessible = true } ?: return@forEach
            mod.hook(initView).intercept { chain ->
                val result = chain.proceed()
                if (isSaveCardVerticalLayoutEnabled() && !HookSettings.isHomeSaveSectionHidden) {
                    applySaveCardVerticalLayout(chain.thisObject as? View)
                }
                result
            }
            clazz.declaredMethods.filter(::isSaveCardStateRenderMethod).forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isSaveCardVerticalLayoutEnabled() && !HookSettings.isHomeSaveSectionHidden) {
                        applySaveCardVerticalLayout(
                            cardView = chain.thisObject as? View,
                            state = chain.args.firstOrNull(),
                        )
                    }
                    result
                }
                count++
            }
            groupClasses += className.replace(
                ".ui.view.fragment.NewHomeSaveCardView",
                ".ui.view.view.HorizontalScrollViewGroup",
            )
            count++
        }

        count += hookSaveCardUpdateObservers(
            mod = mod,
            cl = cl,
            viewModelClassNames = points.saveCardViewModelClassNames,
        )

        groupClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            listOf("onInterceptTouchEvent", "onTouchEvent").forEach { methodName ->
                val method = XposedCompat.findMethodOrNull(clazz, methodName, MotionEvent::class.java)
                    ?: return@forEach
                mod.hook(method).intercept { chain ->
                    val group = chain.thisObject as? ViewGroup
                    if (group != null && verticalSaveGroups.containsKey(group)) {
                        false
                    } else {
                        chain.proceed()
                    }
                }
                count++
            }
        }
        if (count > 0) {
            XposedCompat.log("[HomeCustomizeHook] save card vertical layout hooks installed: count=$count")
        }
        return count
    }

    private fun hookHiddenFeedScrollContainer(cl: ClassLoader): Int {
        val saveEnabled = isSaveCardVerticalLayoutEnabled() && !HookSettings.isHomeSaveSectionHidden
        if (!isRecentScrollRangeAdjustmentEnabled() && !saveEnabled) return 0
        if (!homeCustomizeHookPoints().supportsRecentScrollRangeAdjustment) return 0
        val mod = XposedCompat.module ?: return 0
        var count = 0
        homeCustomizeHookPoints().feedFragmentClassNames.distinct().forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            val methods = clazz.declaredMethods.filter { method ->
                method.name == BaiduHomeCardHookPoints.HIDE_FEED_LIST_METHOD &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType
            }
            methods.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val feedVisible = chain.args.firstOrNull() as? Boolean ?: return@intercept result
                    val fragmentView = invokeNoArg(chain.thisObject, "getView") as? View
                        ?: return@intercept result
                    val content = findHostView<View>(fragmentView, "stickyContentView")
                        ?: return@intercept result
                    val targetAlpha = if (feedVisible) 1f else 0f
                    val stateChanged =
                        content.alpha != targetAlpha || content.isEnabled != feedVisible
                    content.visibility = View.VISIBLE
                    if (content.alpha != targetAlpha) content.alpha = targetAlpha
                    if (content.isEnabled != feedVisible) content.isEnabled = feedVisible
                    if (stateChanged) {
                        XposedCompat.logD(
                            "[HomeCustomizeHook] hidden feed scroll container preserved: " +
                                "feedVisible=$feedVisible, host=${clazz.name}",
                        )
                    }
                    result
                }
                count++
            }
        }
        if (count > 0) {
            XposedCompat.log(
                "[HomeCustomizeHook] hidden feed scroll container hooks installed: count=$count",
            )
        }
        return count
    }

    private fun isSaveCardStateRenderMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            isSaveCardUiStateClass(method.parameterTypes[0])
    }

    private fun isSaveCardUiStateClass(clazz: Class<*>): Boolean {
        if (clazz.isPrimitive || clazz == Any::class.java) return false
        val fields = clazz.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        val listCount = fields.count { List::class.java.isAssignableFrom(it.type) }
        val hasEnum = fields.any { it.type.isEnum }
        val hasBoolean = fields.any { it.type == Boolean::class.javaPrimitiveType }
        val hasCopy = clazz.declaredMethods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == clazz &&
                method.parameterTypes.size >= 4 &&
                method.parameterTypes.count { List::class.java.isAssignableFrom(it) } >= 2 &&
                method.parameterTypes.any { it == Boolean::class.javaPrimitiveType }
        }
        return listCount >= 2 && hasEnum && hasBoolean && hasCopy
    }

    private fun hookSaveCardUpdateObservers(
        mod: io.github.libxposed.api.XposedModule,
        cl: ClassLoader,
        viewModelClassNames: List<String>,
    ): Int {
        var count = 0
        viewModelClassNames.distinct().forEach { viewModelClassName ->
            val observerClassName =
                "$viewModelClassName\$updateCardInfo\$\$inlined\$observerOnlyOnce\$1"
            val observerClass = XposedCompat.findClassOrNull(observerClassName, cl) ?: run {
                XposedCompat.logD(
                    "[HomeCustomizeHook] save update observer not found: $observerClassName",
                )
                return@forEach
            }
            observerClass.declaredMethods.filter { method ->
                method.name == "onChanged" &&
                    method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 1
            }.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isSaveCardVerticalLayoutEnabled() && !HookSettings.isHomeSaveSectionHidden) {
                        expandSaveCardUpdateState(
                            observer = chain.thisObject,
                            result = chain.args.firstOrNull(),
                        )
                        findSaveCardViewModel(chain.thisObject)?.let { viewModel ->
                            expandSavedItems(viewModel, cl)
                        }
                    }
                    result
                }
                count++
            }
        }
        return count
    }

    private fun applySaveCardVerticalLayout(cardView: View?, state: Any? = null) {
        if (cardView !is ViewGroup) return
        val saveGroup = findHostView<ViewGroup>(cardView, "horizontalScrollView")
        val subscribeGroup = findHostView<ViewGroup>(cardView, "linkHorizontalScrollView")
        val contentArea = findHostView<View>(cardView, "fh_save_date_area")

        verticalizeSaveGroup(
            group = saveGroup,
            rowIdNames = listOf("root_one_layout", "root_two_layout", "root_three_layout"),
            innerIdNames = listOf("fh_cl_one", "fh_cl_two", "fh_cl_three"),
        )
        verticalizeSaveGroup(
            group = subscribeGroup,
            rowIdNames = listOf("link_root_one_layout", "link_root_two_layout", "link_root_three_layout"),
            innerIdNames = listOf("update_root"),
        )
        if (state != null) {
            val subscriptionItems = readSaveCardStateList(state, ".UpdatedDataInfo")
            updateVerticalSaveRows(
                cardView = cardView,
                group = saveGroup,
                items = readSaveCardStateList(state, ".SavedDataInfo"),
                isSubscription = false,
            )
            updateVerticalSaveRows(
                cardView = cardView,
                group = subscribeGroup,
                items = subscriptionItems,
                isSubscription = true,
            )
            findSaveCardViewModel(cardView)?.let { viewModel ->
                val classLoader = cardView.javaClass.classLoader
                if (classLoader != null) expandSavedItems(viewModel, classLoader)
            } ?: XposedCompat.logD(
                "[HomeCustomizeHook] save card view model unavailable: ${cardView.javaClass.name}",
            )
        }
        enforceWrapContentHeight(contentArea)
        installSaveCardHeightGuard(contentArea, saveGroup, subscribeGroup)
        if (
            saveGroup != null && verticalSaveGroups.containsKey(saveGroup) ||
            subscribeGroup != null && verticalSaveGroups.containsKey(subscribeGroup)
        ) {
            XposedCompat.logD(
                "[HomeCustomizeHook] save card vertical layout applied: ${cardView.javaClass.name}",
            )
        }
    }

    private fun updateVerticalSaveRows(
        cardView: ViewGroup,
        group: ViewGroup?,
        items: List<*>,
        isSubscription: Boolean,
    ) {
        if (group == null || !verticalSaveGroups.containsKey(group)) return
        val wrapper = group.getChildAt(0) as? LinearLayout ?: return
        verticalSaveDynamicRows.remove(group)?.forEach { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }

        val limit = HookSettings.homeSaveItemLimit.coerceIn(1, 10)
        val visibleCount = minOf(limit, items.size)
        for (index in 0 until minOf(3, wrapper.childCount)) {
            wrapper.getChildAt(index).visibility = if (index < visibleCount) View.VISIBLE else View.GONE
        }

        val dynamicRows = mutableListOf<View>()
        for (index in 3 until visibleCount) {
            val item = items[index] ?: continue
            val row = inflateSaveCardRow(cardView, isSubscription) ?: break
            row.tag = index.toString()
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                verticalSaveRowHeights[group]
                    ?: wrapper.getChildAt(0).layoutParams.height,
            )
            val bound = if (isSubscription) {
                bindSubscriptionRow(cardView, row, item)
            } else {
                bindSavedRow(cardView, row, item)
            }
            if (!bound) break
            wrapper.addView(row)
            dynamicRows += row
        }
        if (dynamicRows.isNotEmpty()) {
            verticalSaveDynamicRows[group] = dynamicRows
        }
        group.requestLayout()
        XposedCompat.logD {
            "[HomeCustomizeHook] save card rows updated: " +
                "subscription=$isSubscription, source=${items.size}, visible=$visibleCount"
        }
    }

    private fun inflateSaveCardRow(cardView: ViewGroup, isSubscription: Boolean): View? {
        val layoutName = when {
            cardView.javaClass.name.contains(".guest25ai.") -> "guest_home25ai_fragment_feed_save"
            cardView.javaClass.name.contains(".home25ai.") -> "home25ai_fragment_feed_save"
            else -> "new_fh_fragment_save"
        }
        val layoutId = cardView.resources.getIdentifier(
            layoutName,
            "layout",
            cardView.context.packageName,
        )
        if (layoutId == 0) return null
        val template = LayoutInflater.from(cardView.context).inflate(layoutId, null, false)
        val rowId = if (isSubscription) "link_root_one_layout" else "root_one_layout"
        val row = findHostView<View>(template, rowId) ?: return null
        (row.parent as? ViewGroup)?.removeView(row)
        row.visibility = View.VISIBLE
        return row
    }

    private fun bindSavedRow(cardView: ViewGroup, row: View, item: Any): Boolean {
        val method = cardView.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 7 &&
                candidate.parameterTypes[0].name.endsWith(".UIConstraintLayout") &&
                candidate.parameterTypes[6].isInstance(item)
        }?.apply { isAccessible = true } ?: return false
        val constraint = findHostView<View>(row, "fh_cl_one") ?: return false
        val args = arrayOf(
            constraint,
            findHostView<View>(row, "fh_one_img"),
            findHostView<View>(row, "fh_one_play"),
            findHostView<View>(row, "fh_one_title"),
            findHostView<View>(row, "fh_one_time"),
            findHostView<View>(row, "fh_one_to_path"),
            item,
        )
        return runCatching {
            method.invoke(cardView, *args)
            constraint.setOnClickListener { openSavedItem(cardView, item) }
            constraint.layoutParams = constraint.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            true
        }.getOrElse { error ->
            XposedCompat.logW("[HomeCustomizeHook] bind extra saved row failed: ${error.message}")
            false
        }
    }

    private fun bindSubscriptionRow(cardView: ViewGroup, row: View, item: Any): Boolean {
        val method = cardView.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 2 &&
                candidate.parameterTypes[0].name.endsWith(".SubscribeToUpdatesLayoutBinding") &&
                candidate.parameterTypes[1].isInstance(item)
        }?.apply { isAccessible = true } ?: return false
        val updateRoot = findHostView<View>(row, "update_root") ?: return false
        val bindingType = method.parameterTypes[0]
        val bindMethod = bindingType.declaredMethods.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.returnType == bindingType &&
                candidate.parameterTypes.contentEquals(arrayOf(View::class.java))
        }?.apply { isAccessible = true } ?: return false
        return runCatching {
            val binding = bindMethod.invoke(null, updateRoot)
            method.invoke(cardView, binding, item)
            (cardView as? View.OnClickListener)?.let(row::setOnClickListener)
            updateRoot.layoutParams = updateRoot.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            true
        }.getOrElse { error ->
            XposedCompat.logW(
                "[HomeCustomizeHook] bind extra subscription row failed: ${error.message}",
            )
            false
        }
    }

    private fun openSavedItem(cardView: ViewGroup, item: Any) {
        runCatching {
            val viewModelGetter = cardView.javaClass.declaredMethods.firstOrNull { method ->
                method.parameterTypes.isEmpty() &&
                    method.returnType.name.endsWith(".NewHomeSaveCardViewModel")
            }?.apply { isAccessible = true } ?: return
            val viewModel = viewModelGetter.invoke(cardView) ?: return
            val activity = findActivity(cardView.context) ?: return
            val previewMethod = viewModel.javaClass.declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    Activity::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1].isInstance(item)
            }?.apply { isAccessible = true } ?: return
            previewMethod.invoke(viewModel, activity, item)
        }.onFailure { error ->
            XposedCompat.logW("[HomeCustomizeHook] open extra saved item failed: ${error.message}")
        }
    }

    private tailrec fun findActivity(context: Context?): Activity? {
        return when (context) {
            is Activity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }
    }

    private fun readSaveCardStateList(state: Any, itemClassSuffix: String): List<*> {
        val fields = state.javaClass.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) &&
                List::class.java.isAssignableFrom(field.type)
        }
        val field = fields.firstOrNull { it.genericType.typeName.contains(itemClassSuffix) }
            ?: fields.firstOrNull { candidate ->
                candidate.isAccessible = true
                val values = candidate.get(state) as? List<*>
                values?.firstOrNull()?.javaClass?.name?.endsWith(itemClassSuffix) == true
            }
        field ?: return emptyList<Any>()
        field.isAccessible = true
        return field.get(state) as? List<*> ?: emptyList<Any>()
    }

    private fun expandSaveCardUpdateState(observer: Any?, result: Any?) {
        if (observer == null || result == null) return
        runCatching {
            val response = invokeNoArg(result, "getData") ?: return
            val info = invokeNoArg(response, "getData") ?: return
            val source = invokeNoArg(info, "getData") as? List<*> ?: return
            val limited = source.filter { item ->
                val status = item?.let { invokeNoArg(it, "getStatus") } as? Number
                status?.toInt() == 0
            }.take(HookSettings.homeSaveItemLimit.coerceIn(1, 10))

            val viewModel = observer.javaClass.declaredFields.firstNotNullOfOrNull { field ->
                field.isAccessible = true
                field.get(observer)?.takeIf { value ->
                    value.javaClass.name.endsWith(".NewHomeSaveCardViewModel")
                }
            } ?: return
            val stateFlow = viewModel.javaClass.declaredFields.firstNotNullOfOrNull { field ->
                field.isAccessible = true
                val value = field.get(viewModel) ?: return@firstNotNullOfOrNull null
                val current = invokeNoArg(value, "getValue") ?: return@firstNotNullOfOrNull null
                value.takeIf { current.javaClass.name.endsWith(".SaveCardUiState") }
            } ?: return
            val current = invokeNoArg(stateFlow, "getValue") ?: return
            val currentUpdates = readSaveCardStateList(current, ".UpdatedDataInfo")
            if (currentUpdates == limited) return
            val copy = current.javaClass.declaredMethods.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == current.javaClass &&
                    method.parameterTypes.size == 5 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                    List::class.java.isAssignableFrom(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true } ?: return
            val fields = current.javaClass.declaredFields.onEach { it.isAccessible = true }
            val stateValue = fields.first { copy.parameterTypes[0].isAssignableFrom(it.type) }.get(current)
            val saveList = readSaveCardStateList(current, ".SavedDataInfo")
            val hasMore = fields.first { it.type == Boolean::class.javaPrimitiveType }.getBoolean(current)
            val extraInfo = fields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    copy.parameterTypes[4].isAssignableFrom(field.type)
            }?.get(current)
            val expandedState = copy.invoke(
                current,
                stateValue,
                saveList,
                limited,
                hasMore,
                extraInfo,
            )
            val setValue = stateFlow.javaClass.methods.firstOrNull { method ->
                method.name == "setValue" && method.parameterTypes.size == 1
            } ?: return
            setValue.invoke(stateFlow, expandedState)
            XposedCompat.logD {
                "[HomeCustomizeHook] subscription state expanded: " +
                    "source=${source.size}, visible=${limited.size}"
            }
        }.onFailure { error ->
            XposedCompat.logW(
                "[HomeCustomizeHook] expand subscription state failed: ${error.message}",
            )
        }
    }

    private fun expandSavedItems(viewModel: Any, cl: ClassLoader) {
        val stateFlow = findSaveCardStateFlow(viewModel) ?: run {
            XposedCompat.logD(
                "[HomeCustomizeHook] save card state flow unavailable: ${viewModel.javaClass.name}",
            )
            return
        }
        val current = invokeNoArg(stateFlow, "getValue") ?: return
        val currentSaveList = readSaveCardStateList(current, ".SavedDataInfo")
        val limit = HookSettings.homeSaveItemLimit.coerceIn(1, 10)
        if (currentSaveList.size >= limit) return

        saveHistoryCache[viewModel]?.let { cached ->
            updateSaveCardStateSaveList(stateFlow, cached.take(limit))
            return
        }
        if (saveHistoryRequests.put(viewModel, true) == true) return

        var observing = false
        runCatching {
            val targetItemClass = currentSaveList.firstNotNullOfOrNull { it?.javaClass }
                ?: error("saved item target class unavailable")
            val accountClass = XposedCompat.findClassOrNull(
                BaiduHomeCardHookPoints.ACCOUNT_UTILS,
                cl,
            ) ?: error("AccountUtils unavailable")
            val account = invokeStaticNoArg(accountClass, "getInstance")
                ?: error("AccountUtils instance unavailable")
            val uid = invokeNoArg(account, "getUid") as? String ?: error("uid unavailable")
            val bduss = invokeNoArg(account, "getBduss") as? String ?: error("bduss unavailable")
            if (uid.isEmpty() || bduss.isEmpty()) error("account credentials empty")

            val evidenceClass = XposedCompat.findClassOrNull(
                BaiduHomeCardHookPoints.EVIDENCE,
                cl,
            ) ?: error("Evidence unavailable")
            val evidence = evidenceClass.getDeclaredConstructor(String::class.java, String::class.java)
                .newInstance(uid, bduss)
            val applicationClass = XposedCompat.findClassOrNull(
                BaiduHomeCardHookPoints.BASE_APPLICATION,
                cl,
            ) ?: error("BaseApplication unavailable")
            val application = invokeStaticNoArg(applicationClass, "getInstance")
                ?: error("BaseApplication instance unavailable")
            val serviceKtClass = XposedCompat.findClassOrNull(
                BaiduHomeCardHookPoints.TRANSFER_SAVED_SERVICE_KT,
                cl,
            ) ?: error("transfer saved service factory unavailable")
            val serviceFactory = serviceKtClass.declaredMethods.firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.size == 1 &&
                    Context::class.java.isAssignableFrom(method.parameterTypes[0])
            }?.apply { isAccessible = true } ?: error("transfer saved service method unavailable")
            val service = serviceFactory.invoke(null, application)
                ?: error("transfer saved service unavailable")
            val fetchMethod = service.javaClass.methods.firstOrNull { method ->
                method.name == "fetchSavedList" &&
                    method.parameterTypes.size == 4 &&
                    method.parameterTypes.drop(1).all { it == Int::class.javaPrimitiveType }
            } ?: error("fetchSavedList unavailable")
            val liveData = fetchMethod.invoke(service, evidence, 1, limit, 0)
                ?: error("saved history LiveData unavailable")
            observing = observeSavedHistory(
                liveData = liveData,
                cl = cl,
                viewModel = viewModel,
                stateFlow = stateFlow,
                targetItemClass = targetItemClass,
                limit = limit,
            )
            if (observing) {
                XposedCompat.logD(
                    "[HomeCustomizeHook] full saved history requested: limit=$limit",
                )
            }
        }.onFailure { error ->
            XposedCompat.logW(
                "[HomeCustomizeHook] full saved history request failed: ${error.message}",
            )
        }
        if (!observing) saveHistoryRequests.remove(viewModel)
    }

    private fun observeSavedHistory(
        liveData: Any,
        cl: ClassLoader,
        viewModel: Any,
        stateFlow: Any,
        targetItemClass: Class<*>,
        limit: Int,
    ): Boolean {
        val observerClass = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.ANDROIDX_OBSERVER, cl)
            ?: return false
        val observeForever = liveData.javaClass.methods.firstOrNull { method ->
            method.name == "observeForever" &&
                method.parameterTypes.contentEquals(arrayOf(observerClass))
        } ?: return false
        val removeObserver = liveData.javaClass.methods.firstOrNull { method ->
            method.name == "removeObserver" &&
                method.parameterTypes.contentEquals(arrayOf(observerClass))
        }
        lateinit var observer: Any
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "onChanged" -> {
                    val result = args?.firstOrNull()
                    val response = result?.let { invokeNoArg(it, "getData") }
                    val info = response?.let { invokeNoArg(it, "getData") }
                    val history = info?.let { invokeNoArg(it, "getTransferList") } as? List<*>
                    if (history == null) {
                        val resultName = result?.javaClass?.simpleName.orEmpty()
                        if (resultName != "Operating" && resultName.isNotEmpty()) {
                            removeObserver?.invoke(liveData, observer)
                            saveHistoryRequests.remove(viewModel)
                            XposedCompat.logW(
                                "[HomeCustomizeHook] full saved history failed: state=$resultName",
                            )
                        }
                        return@InvocationHandler null
                    }
                    removeObserver?.invoke(liveData, observer)
                    val mapped = history.orEmpty().mapNotNull { source ->
                        source?.let { mapSavedHistoryItem(it, targetItemClass) }
                    }.take(limit)
                    if (mapped.isNotEmpty()) {
                        saveHistoryCache[viewModel] = mapped
                        updateSaveCardStateSaveList(stateFlow, mapped)
                        XposedCompat.logD(
                            "[HomeCustomizeHook] full saved history applied: " +
                                "source=${history.size}, visible=${mapped.size}",
                        )
                    } else {
                        XposedCompat.logW(
                            "[HomeCustomizeHook] full saved history mapping produced no items: " +
                                "source=${history.size}",
                        )
                    }
                    saveHistoryRequests.remove(viewModel)
                    null
                }
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PureDuPanSavedHistoryObserver"
                else -> null
            }
        }
        observer = Proxy.newProxyInstance(
            observerClass.classLoader,
            arrayOf(observerClass),
            handler,
        )
        observeForever.invoke(liveData, observer)
        return true
    }

    private fun mapSavedHistoryItem(source: Any, targetClass: Class<*>): Any? {
        runCatching {
            val classLoader = targetClass.classLoader ?: return@runCatching null
            val gsonClass = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.GSON, classLoader)
                ?: return@runCatching null
            val gson = gsonClass.getDeclaredConstructor().newInstance()
            val toJson = gsonClass.methods.first { method ->
                method.name == "toJson" &&
                    method.parameterTypes.contentEquals(arrayOf(Any::class.java))
            }
            val fromJson = gsonClass.methods.first { method ->
                method.name == "fromJson" &&
                    method.parameterTypes.contentEquals(arrayOf(String::class.java, Class::class.java))
            }
            val json = toJson.invoke(gson, source) as? String ?: return@runCatching null
            fromJson.invoke(gson, json, targetClass)
        }.getOrNull()?.let { return it }

        val constructor = targetClass.declaredConstructors.firstOrNull { ctor ->
            val types = ctor.parameterTypes
            types.size == 17 &&
                types[0] == Int::class.javaPrimitiveType &&
                types[2] == Long::class.javaPrimitiveType &&
                types[3] == Int::class.javaPrimitiveType
        }?.apply { isAccessible = true } ?: return null
        val fsId = (invokeNoArg(source, "getFsId") as? String)?.toLongOrNull() ?: 0L
        val isDir = (invokeNoArg(source, "isDir") as? Number)?.toInt() ?: 0
        val args = arrayOf(
            (invokeNoArg(source, "getFileCategory") as? Number)?.toInt() ?: 0,
            invokeNoArg(source, "getDlink"),
            fsId,
            isDir,
            (invokeNoArg(source, "getLocalCtime") as? Number)?.toLong() ?: 0L,
            (invokeNoArg(source, "getLocalMtime") as? Number)?.toLong() ?: 0L,
            invokeNoArg(source, "getMd5") as? String ?: "",
            invokeNoArg(source, "getParentPath"),
            invokeNoArg(source, "getPath"),
            invokeNoArg(source, "getServerCtime"),
            invokeNoArg(source, "getServerFilename"),
            invokeNoArg(source, "getServerMtime"),
            invokeNoArg(source, "getSize"),
            invokeNoArg(source, "getTransferTime"),
            invokeNoArg(source, "isDelete"),
            null,
            (invokeNoArg(source, "getExtentLong") as? Number)?.toLong() ?: 0L,
        )
        return runCatching { constructor.newInstance(*args) }.getOrNull()
    }

    private fun updateSaveCardStateSaveList(stateFlow: Any, saveList: List<Any>) {
        runCatching {
            val current = invokeNoArg(stateFlow, "getValue") ?: return
            if (!isSaveCardUiStateClass(current.javaClass)) return
            val currentSaveList = readSaveCardStateList(current, ".SavedDataInfo")
            if (currentSaveList == saveList) return
            val copy = current.javaClass.declaredMethods.firstOrNull { method ->
                !Modifier.isStatic(method.modifiers) &&
                    method.returnType == current.javaClass &&
                    method.parameterTypes.size == 5 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[1]) &&
                    List::class.java.isAssignableFrom(method.parameterTypes[2]) &&
                    method.parameterTypes[3] == Boolean::class.javaPrimitiveType
            }?.apply { isAccessible = true } ?: return
            val fields = current.javaClass.declaredFields.onEach { it.isAccessible = true }
            val stateValue = fields.first { copy.parameterTypes[0].isAssignableFrom(it.type) }.get(current)
            val updateList = readSaveCardStateList(current, ".UpdatedDataInfo")
            val hasMore = fields.first { it.type == Boolean::class.javaPrimitiveType }.getBoolean(current)
            val extraInfo = fields.firstOrNull { field ->
                !Modifier.isStatic(field.modifiers) &&
                    copy.parameterTypes[4].isAssignableFrom(field.type)
            }?.get(current)
            val expandedState = copy.invoke(
                current,
                stateValue,
                saveList,
                updateList,
                hasMore,
                extraInfo,
            )
            val setValue = stateFlow.javaClass.methods.firstOrNull { method ->
                method.name == "setValue" && method.parameterTypes.size == 1
            } ?: return
            setValue.invoke(stateFlow, expandedState)
        }.onFailure { error ->
            XposedCompat.logW(
                "[HomeCustomizeHook] update full saved history state failed: ${error.message}",
            )
        }
    }

    private fun findSaveCardViewModel(owner: Any): Any? {
        owner.javaClass.declaredMethods.firstOrNull { method ->
            method.parameterTypes.isEmpty() &&
                method.returnType.name.endsWith(".NewHomeSaveCardViewModel")
        }?.let { getter ->
            getter.isAccessible = true
            return getter.invoke(owner)
        }
        return owner.javaClass.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true
            field.get(owner)?.takeIf { value ->
                value.javaClass.name.endsWith(".NewHomeSaveCardViewModel")
            }
        }
    }

    private fun findSaveCardStateFlow(viewModel: Any): Any? {
        return viewModel.javaClass.declaredFields.firstNotNullOfOrNull { field ->
            field.isAccessible = true
            val value = field.get(viewModel) ?: return@firstNotNullOfOrNull null
            val current = invokeNoArg(value, "getValue") ?: return@firstNotNullOfOrNull null
            value.takeIf { isSaveCardUiStateClass(current.javaClass) }
        }
    }

    private fun invokeStaticNoArg(clazz: Class<*>, methodName: String): Any? {
        val method = clazz.methods.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.name == methodName &&
                candidate.parameterTypes.isEmpty()
        } ?: return null
        method.isAccessible = true
        return method.invoke(null)
    }

    private fun invokeNoArg(instance: Any, methodName: String): Any? {
        val method = (instance.javaClass.methods.asSequence() +
            instance.javaClass.declaredMethods.asSequence()).firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterTypes.isEmpty()
        } ?: return null
        method.isAccessible = true
        return method.invoke(instance)
    }

    private fun verticalizeSaveGroup(
        group: ViewGroup?,
        rowIdNames: List<String>,
        innerIdNames: List<String>,
    ) {
        if (group == null || verticalSaveGroups.containsKey(group)) return
        val rows = rowIdNames.mapNotNull { idName -> findHostView<View>(group, idName) }
        if (rows.isEmpty()) return
        val rowHeightPx = group.layoutParams?.height?.takeIf { it > 0 }
            ?: rows.firstNotNullOfOrNull { row ->
                row.layoutParams?.height?.takeIf { it > 0 }
            }
            ?: return
        val wrapper = LinearLayout(group.context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        rows.forEach { row ->
            (row.parent as? ViewGroup)?.removeView(row)
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx)
            wrapper.addView(row)
        }
        innerIdNames.forEach { idName ->
            findHostView<View>(wrapper, idName)?.let { inner ->
                inner.layoutParams = inner.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }
        group.removeAllViews()
        group.addView(
            wrapper,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        enforceWrapContentHeight(group)
        verticalSaveGroups[group] = true
        verticalSaveRowHeights[group] = rowHeightPx
        group.scrollTo(0, 0)
        group.requestLayout()
    }


    private fun installSaveCardHeightGuard(
        contentArea: View?,
        saveGroup: ViewGroup?,
        subscribeGroup: ViewGroup?,
    ) {
        if (contentArea == null || verticalSaveHeightGuards.containsKey(contentArea)) return
        val observer = contentArea.viewTreeObserver
        if (!observer.isAlive) {
            contentArea.post { installSaveCardHeightGuard(contentArea, saveGroup, subscribeGroup) }
            return
        }
        verticalSaveHeightGuards[contentArea] = true
        observer.addOnPreDrawListener(
            ViewTreeObserver.OnPreDrawListener {
                if (!isSaveCardVerticalLayoutEnabled() || HookSettings.isHomeSaveSectionHidden) {
                    return@OnPreDrawListener true
                }
                val changed = enforceWrapContentHeight(contentArea) or
                    enforceWrapContentHeight(saveGroup) or
                    enforceWrapContentHeight(subscribeGroup)
                if (changed) {
                    contentArea.requestLayout()
                    XposedCompat.logD(
                        "[HomeCustomizeHook] save card height restored before draw",
                    )
                }
                !changed
            },
        )
    }

    private fun enforceWrapContentHeight(view: View?): Boolean {
        val params = view?.layoutParams ?: return false
        if (params.height == ViewGroup.LayoutParams.WRAP_CONTENT) return false
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = params
        return true
    }

    private inline fun <reified T : View> findHostView(root: View, idName: String): T? {
        val id = root.resources.getIdentifier(idName, "id", root.context.packageName)
        if (id == 0) return null
        return root.findViewById(id) as? T
    }

    private fun hookHomeStoryCardRenderEntry(cl: ClassLoader): Int {
        if (!HookSettings.isHomeMemoriesSectionHidden) return 0
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val className = points.storyCardRenderContextClassName
        val methodName = points.storyCardRenderMethodName
        if (className == null || methodName == null) {
            XposedCompat.log("[HomeCustomizeHook] story card render host capability missing")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.log("[HomeCustomizeHook] story card render context class NOT FOUND")
            return 0
        }
        val methods = clazz.declaredMethods.filter { method ->
            method.name == methodName && isHomeStoryCardRenderMethod(method)
        }
        if (methods.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] story card render method NOT FOUND")
            return 0
        }
        methods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeMemoriesSectionHidden) {
                    XposedCompat.logD("[HomeCustomizeHook] story card render blocked: ${clazz.name}.${method.name}")
                    val context = chain.args.firstOrNull { it is Context } as? Context
                    context?.let(::createCollapsedFrameLayout) ?: chain.proceed()
                } else {
                    chain.proceed()
                }
            }
        }
        return methods.size
    }

    private fun isHomeStoryCardRenderMethod(method: Method): Boolean {
        return FrameLayout::class.java.isAssignableFrom(method.returnType) &&
            method.parameterTypes.any { Context::class.java.isAssignableFrom(it) }
    }

    private fun hookHomeHeaderCardRenderEntries(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val feedFragmentClasses = points.feedFragmentClassNames.distinct()
        if (feedFragmentClasses.isEmpty()) return 0

        var count = 0
        feedFragmentClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found for header card render hook")
                return@forEach
            }
            count += hookHomeHeaderCardRenderEntry(
                mod = mod,
                clazz = clazz,
                methodName = points.feedRecentCardRenderMethodName,
                enabled = { HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeRecentSectionHidden },
                label = "recent card render",
                blockBeforeCreate = false,
            )
            count += hookHomeHeaderCardRenderEntry(
                mod = mod,
                clazz = clazz,
                methodName = points.feedSaveCardRenderMethodName,
                enabled = { HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeSaveSectionHidden },
                label = "save card render",
                blockBeforeCreate = false,
            )
            count += hookHomeHeaderCardRenderEntry(
                mod = mod,
                clazz = clazz,
                methodName = points.feedStoryCardRenderMethodName,
                enabled = { HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeMemoriesSectionHidden },
                label = "story card render",
                blockBeforeCreate = true,
            )
        }
        return count
    }

    private fun hookHomeHeaderCardRenderEntry(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        methodName: String?,
        enabled: () -> Boolean,
        label: String,
        blockBeforeCreate: Boolean,
    ): Int {
        if (methodName == null || !enabled()) return 0
        val methods = clazz.declaredMethods.filter { method ->
            method.name == methodName &&
                View::class.java.isAssignableFrom(method.returnType)
        }
        if (methods.isEmpty()) {
            XposedCompat.logD("[HomeCustomizeHook] $label not found: ${clazz.name}.$methodName")
            return 0
        }
        methods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                if (!enabled()) return@intercept chain.proceed()
                if (blockBeforeCreate && FrameLayout::class.java.isAssignableFrom(method.returnType)) {
                    val context = fragmentContext(chain.thisObject)
                    if (context != null) {
                        XposedCompat.logD("[HomeCustomizeHook] $label blocked: ${clazz.name}.${method.name}")
                        return@intercept createCollapsedFrameLayout(context)
                    }
                }
                val result = chain.proceed()
                if (collapseView(result as? View)) {
                    XposedCompat.logD("[HomeCustomizeHook] $label collapsed: ${clazz.name}.${method.name}")
                }
                result
            }
        }
        return methods.size
    }

    private fun fragmentContext(fragment: Any?): Context? {
        if (fragment == null) return null
        return runCatching {
            XposedCompat.findMethodOrNull(fragment.javaClass, "requireContext")?.invoke(fragment) as? Context
                ?: XposedCompat.findMethodOrNull(fragment.javaClass, "getContext")?.invoke(fragment) as? Context
        }.getOrNull()
    }

    /**
     * Feed tip 是「开启推荐」提示条，不是推荐本身。
     *
     * 宿主只在 [initFeedSettingTipHeader] 里 inflate binding 并写入
     * [feedSettingTipViewHeader]；[hideFeedList] 仅在该字段非空时 show/gone。
     * 因此渲染入口 no-op 即可阻止提示条创建，无需字段级 View 隐藏，
     * 也不会改写 [KEY_HOME_PAGE_SHOW_RECOMMENDED] 配置。
     */
    private fun hookFeedSettingTipRenderEntry(cl: ClassLoader): Int {
        if (!isFeedTipHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        var count = 0
        val feedFragmentClasses = homeCustomizeHookPoints().feedFragmentClassNames.distinct()
        if (feedFragmentClasses.isEmpty()) {
            XposedCompat.log("[HomeCustomizeHook] feed fragment host capabilities missing")
            return 0
        }
        feedFragmentClasses.forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
                XposedCompat.logD("[HomeCustomizeHook] $className not found, skipped")
                return@forEach
            }

            val initFeedSettingTipHeader = XposedCompat.findMethodOrNull(
                clazz,
                INIT_FEED_SETTING_TIP_HEADER_METHOD,
            )
            if (initFeedSettingTipHeader != null) {
                mod.hook(initFeedSettingTipHeader).intercept { chain ->
                    if (isFeedTipHidden()) {
                        XposedCompat.logD(
                            "[HomeCustomizeHook] $className.$INIT_FEED_SETTING_TIP_HEADER_METHOD blocked",
                        )
                        null
                    } else {
                        chain.proceed()
                    }
                }
                count += 1
            } else {
                XposedCompat.logD(
                    "[HomeCustomizeHook] $className.$INIT_FEED_SETTING_TIP_HEADER_METHOD not found",
                )
            }
        }
        return count
    }

    private fun hookHomeBannerCardRenderEntry(cl: ClassLoader): Int {
        if (!isHomeBannerHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val className = points.netdiskContextCompanionClassName
        val methodName = points.newHomeBannerCardViewMethodName
        if (className == null || methodName == null) {
            XposedCompat.log("[HomeCustomizeHook] new home banner render host capability missing")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.log("[HomeCustomizeHook] NetdiskContext.Companion class NOT FOUND")
            return 0
        }
        val fragmentActivityClass = XposedCompat.findClassOrNull("androidx.fragment.app.FragmentActivity", cl)
            ?: run {
                XposedCompat.log("[HomeCustomizeHook] FragmentActivity class NOT FOUND")
                return 0
            }
        val lifecycleOwnerClass = XposedCompat.findClassOrNull("androidx.lifecycle.LifecycleOwner", cl)
            ?: run {
                XposedCompat.log("[HomeCustomizeHook] LifecycleOwner class NOT FOUND")
                return 0
            }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            methodName,
            Context::class.java,
            AttributeSet::class.java,
            fragmentActivityClass,
            lifecycleOwnerClass,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] NetdiskContext.getNewHomeBannerCardView(...) NOT FOUND")
            return 0
        }
        if (!FrameLayout::class.java.isAssignableFrom(method.returnType)) {
            XposedCompat.log("[HomeCustomizeHook] NetdiskContext.getNewHomeBannerCardView return type mismatch")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val context = chain.args.getOrNull(0) as? Context
            if (isHomeBannerHidden() && context != null) {
                XposedCompat.logD("[HomeCustomizeHook] NetdiskContext.getNewHomeBannerCardView blocked")
                createCollapsedFrameLayout(context)
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun adjustHomeToolbarRootLayout(root: View?) {
        if (!isHomeToolbarHidden() || root == null) return
        adjustHomeToolbarRootLayoutNow(root)
        root.post { runCatching { adjustHomeToolbarRootLayoutNow(root) } }
    }

    private fun adjustHomeToolbarRootLayoutNow(root: View) {
        if (!isHomeToolbarHidden()) return
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return

        homeCustomizeHookPoints().toolbarViewIdNames.forEach { idName ->
            val id = resources.getIdentifier(idName, "id", packageName)
            if (id == 0) return@forEach
            val view = root.findViewById<View>(id) ?: return@forEach
            if (collapseView(view)) {
                XposedCompat.logD("[HomeCustomizeHook] home toolbar container collapsed: $idName")
            }
        }
        adjustHome25ContentOffset(root, resources, packageName)
        adjustIntlFeedContainerOffset(root, resources, packageName)
    }

    private fun hideSearchboxAigcBindingViews(fragment: Any?) {
        val binding = getBindingObject(fragment) ?: return
        hideBindingView(binding, "searchboxAigcIcon")
        hideBindingView(binding, "searchboxAigcVideo")
        hideBindingView(binding, "searchboxBorderAnimView")
    }

    private fun hideSearchPlaceholderBindingView(fragment: Any?): Boolean {
        val binding = getBindingObject(fragment) ?: return false
        return hideBindingView(binding, SEARCH_PLACEHOLDER_BINDING_FIELD) ||
            hideFirstBindingViewByType(binding, TEXT_FLIPPER_CLASS_TOKEN)
    }

    private fun getBindingObject(fragment: Any?): Any? {
        if (fragment == null) return null
        return runCatching {
            findFieldInHierarchy(fragment.javaClass) { it.name == "binding" }?.get(fragment)
        }.getOrNull()
    }

    private fun hideBindingView(binding: Any, fieldName: String): Boolean {
        return runCatching {
            val field = findFieldInHierarchy(binding.javaClass) { it.name == fieldName } ?: return false
            val view = field.get(binding) as? View ?: return false
            collapseView(view)
        }.getOrDefault(false)
    }

    private fun hideFirstBindingViewByType(binding: Any, classNameToken: String): Boolean {
        return runCatching {
            val field = findFieldInHierarchy(binding.javaClass) { field ->
                View::class.java.isAssignableFrom(field.type) &&
                    field.type.name.contains(classNameToken)
            } ?: return false
            val view = field.get(binding) as? View ?: return false
            collapseView(view)
        }.getOrDefault(false)
    }

    private fun findFieldInHierarchy(clazz: Class<*>, predicate: (Field) -> Boolean): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            current.declaredFields.firstOrNull(predicate)?.let { field ->
                field.isAccessible = true
                return field
            }
            current = current.superclass
        }
        return null
    }

    private fun hideView(view: View?): Boolean {
        if (view == null) return false
        if (view.visibility == View.GONE && view.alpha == 0f && !view.isEnabled && !view.isClickable) return false
        view.animate()?.cancel()
        view.visibility = View.GONE
        view.alpha = 0f
        view.isEnabled = false
        view.isClickable = false
        (view.parent as? ViewGroup)?.requestLayout()
        return true
    }

    private fun collapseView(view: View?): Boolean {
        if (view == null) return false
        var changed = hideView(view)

        if (view.minimumHeight != 0) {
            view.minimumHeight = 0
            changed = true
        }
        if (view.paddingLeft != 0 || view.paddingTop != 0 || view.paddingRight != 0 || view.paddingBottom != 0) {
            view.setPadding(0, 0, 0, 0)
            changed = true
        }

        val params = view.layoutParams
        if (params != null) {
            if (params.height != 0) {
                params.height = 0
                changed = true
            }
            if (params is ViewGroup.MarginLayoutParams) {
                if (
                    params.leftMargin != 0 ||
                    params.topMargin != 0 ||
                    params.rightMargin != 0 ||
                    params.bottomMargin != 0
                ) {
                    params.setMargins(0, 0, 0, 0)
                    changed = true
                }
            }
            if (changed) {
                view.layoutParams = params
            }
        }

        if (changed) {
            view.requestLayout()
            (view.parent as? ViewGroup)?.requestLayout()
        }
        return changed
    }

    private fun adjustHome25ContentOffset(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
    ) {
        val contentId = resources.getIdentifier(HOME25_CONTENT_ID, "id", packageName)
        val topContainerId = resources.getIdentifier(HOME25_TOP_CONTAINER_ID, "id", packageName)
        val searchboxId = resources.getIdentifier(HOME25_SEARCHBOX_CONTENT_ID, "id", packageName)
        if (contentId == 0 || topContainerId == 0 || searchboxId == 0) return

        val content = root.findViewById<View>(contentId) ?: return
        val topContainer = root.findViewById<View>(topContainerId) ?: return
        val searchbox = root.findViewById<View>(searchboxId) ?: return

        fun adjustContentOffset() {
            val targetTranslationY = searchbox.bottom + topContainer.paddingBottom
            if (targetTranslationY <= 0) return
            if (content.translationY != targetTranslationY.toFloat()) {
                content.translationY = targetTranslationY.toFloat()
                content.requestLayout()
                (content.parent as? ViewGroup)?.requestLayout()
                XposedCompat.logD("[HomeCustomizeHook] home content offset collapsed: $targetTranslationY")
            }
        }

        topContainer.requestLayout()
        adjustContentOffset()
        content.post { adjustContentOffset() }
    }

    private fun adjustIntlFeedContainerOffset(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
    ) {
        val feedId = resources.getIdentifier(FEED_CONTAINER_ID, "id", packageName)
        if (feedId == 0) return
        val feed = root.findViewById<View>(feedId) ?: return
        var changed = false
        if (feed.translationY != 0f) {
            feed.translationY = 0f
            changed = true
        }
        val params = feed.layoutParams ?: return
        if (params is ViewGroup.MarginLayoutParams && params.topMargin != 0) {
            params.topMargin = 0
            changed = true
        }
        if (setIntFieldIfPresent(params, "topToTop", 0)) {
            changed = true
        }
        if (setIntFieldIfPresent(params, "topToBottom", -1)) {
            changed = true
        }
        if (changed) {
            feed.layoutParams = params
            feed.requestLayout()
            (feed.parent as? ViewGroup)?.requestLayout()
            XposedCompat.logD("[HomeCustomizeHook] intl feed container offset collapsed")
        }
    }

    private fun setIntFieldIfPresent(target: Any, fieldName: String, value: Int): Boolean {
        return runCatching {
            val field = findFieldInHierarchy(target.javaClass) { it.name == fieldName } ?: return false
            val currentValue = field.getInt(target)
            if (currentValue == value) return false
            field.setInt(target, value)
            true
        }.getOrDefault(false)
    }

    private fun hookStartupHomeBannerPreload(cl: ClassLoader): Int {
        if (!isTopPromotionHidden()) return 0
        val mod = XposedCompat.module ?: return 0
        val points = homeCustomizeHookPoints()
        val home25aiContextCompanionClassName = points.home25aiContextCompanionClassName
        val loadHomeBannerMethodName = points.loadHomeBannerMethodName
        if (home25aiContextCompanionClassName == null || loadHomeBannerMethodName == null) {
            XposedCompat.log("[HomeCustomizeHook] Home25ai banner preload host capabilities missing")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(
            home25aiContextCompanionClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] Home25aiContext.Companion class NOT FOUND")
            return 0
        }
        val method = XposedCompat.findMethodOrNull(
            clazz,
            loadHomeBannerMethodName,
            Context::class.java,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] Home25aiContext.loadHomeBanner(Context) NOT FOUND")
            return 0
        }
        mod.hook(method).intercept { chain ->
            val context = chain.args.getOrNull(0) as? Context
            if (isTopPromotionHidden() && context is Application) {
                XposedCompat.logD("[HomeCustomizeHook] Home25aiContext.loadHomeBanner startup preload blocked")
                null
            } else {
                chain.proceed()
            }
        }
        return 1
    }

    private fun hasEnabledOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                HookSettings.isHomeTopPromotionHidden ||
                    HookSettings.isHomeSearchPlaceholderHidden ||
                    HookSettings.isHomeSearchAigcIconHidden ||
                    HookSettings.isHomeToolbarHidden ||
                    HookSettings.isHomeMemoriesSectionHidden ||
                    HookSettings.isHomeSaveSectionHidden ||
                    HookSettings.isHomeRecentSectionHidden ||
                    isRecentItemLimitEnabled() ||
                    HookSettings.isHomeSaveVerticalLayoutEnabled ||
                    hasFeedRenderHookOption()
            )
    }

    private fun hasFeedRenderHookOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                HookSettings.isHomeFeedTipHidden ||
                    HookSettings.isHomeBannerHidden
            )
    }

    private fun isTopPromotionHidden(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeTopPromotionHidden
    }

    private fun isFeedTipHidden(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeFeedTipHidden
    }

    private fun isHomeBannerHidden(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeBannerHidden
    }

    private fun isSearchPlaceholderHidden(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeSearchPlaceholderHidden
    }

    private fun isHomeToolbarHidden(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeToolbarHidden
    }

    private fun isRecentItemLimitCustomized(): Boolean {
        return isRecentItemLimitEnabled() &&
            HookSettings.homeRecentItemLimit != NATIVE_RECENT_ITEM_LIMIT
    }

    private fun isRecentItemLimitEnabled(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeRecentItemLimitEnabled
    }

    private fun isRecentScrollRangeAdjustmentEnabled(): Boolean {
        return isRecentItemLimitEnabled() && !HookSettings.isHomeRecentSectionHidden
    }

    private fun isSaveCardVerticalLayoutEnabled(): Boolean {
        return HookSettings.isHomeCustomizeEnabled && HookSettings.isHomeSaveVerticalLayoutEnabled
    }

    private fun homeCustomizeHookPoints() =
        BaiduFeatureRuntime.currentHomeCustomizeHookPoints()
}
