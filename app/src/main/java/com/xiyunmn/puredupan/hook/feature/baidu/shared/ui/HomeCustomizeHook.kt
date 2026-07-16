package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.ArrayList

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

    private val hookState = HookState()

    internal fun hook(cl: ClassLoader) {
        if (!hasEnabledOption()) {
            XposedCompat.log("[HomeCustomizeHook] skipped: config disabled")
            return
        }
        val mod = XposedCompat.module ?: return
        if (!hookState.markInstalled()) return

        try {
            var installedCount = 0
            installedCount += hookTopBannerView(cl)
            installedCount += hookHomeSearchPlaceholderText(cl)
            installedCount += hookHomeToolbarRenderEntry(cl)
            installedCount += hookHomeToolbarRootLayout(cl)
            installedCount += hookSearchboxAigcAnimation(cl)
            installedCount += hookRecentCardDataUseCase(cl)
            installedCount += hookSaveCardViewModel(cl)
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

    private fun homeCustomizeHookPoints() =
        BaiduFeatureRuntime.currentHomeCustomizeHookPoints()
}
