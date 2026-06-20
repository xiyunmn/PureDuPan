package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import java.util.Collections
import java.util.WeakHashMap

/**
 * 顶部 AI 控件移除 Hook。
 *
 * 受首页定制配置控制，默认隐藏顶部动态推广。
 */
object HomeCustomizeHook {
    private const val SEARCH_PLACEHOLDER_ID = "title_bar_search_place_holder_text"
    private const val SEARCH_AIGC_ICON_ID = "searchbox_aigc_icon"
    private const val SEARCH_AIGC_VIDEO_ID = "searchbox_aigc_video"
    private const val SEARCH_AIGC_BORDER_ANIM_ID = "searchbox_border_anim_view"
    private const val FEED_TIP_ID = "cl_feed_tip"
    private const val FEED_TIP_RECOMMENDATION_TEXT_ID = "recommendation_text"
    private const val FEED_TIP_HEADER_FIELD = "feedSettingTipViewHeader"
    private const val INIT_FEED_SETTING_TIP_HEADER_METHOD = "initFeedSettingTipHeader"
    private const val INIT_BANNER_CARD_VIEW_METHOD = "initBannerCardView"
    private const val INIT_RECENT_CARD_VIEW_METHOD = "initRecentCardView"
    private const val INIT_SAVE_CARD_VIEW_METHOD = "initSaveCardView"
    private const val INIT_STORY_CARD_VIEW_METHOD = "initStoryCardView"
    private const val HOME_BANNER_FIELD = "headerBanner"
    private const val HOME_RECENT_CARD_FIELD = "headerRecent"
    private const val HOME_SAVE_CARD_FIELD = "headerMySaves"
    private const val HOME_MEMORIES_CARD_FIELD = "headerMemories"

    private val attachedRoots = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

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
            installedCount += hookHomeSearchboxView(cl)
            installedCount += hookSearchboxAigcAnimation(cl)
            installedCount += hookFeedRecommendView(cl)
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

    private fun hookHomeSearchboxView(cl: ClassLoader): Int {
        if (!hasHomeSearchboxViewCleanupOption()) return 0
        val mod = XposedCompat.module ?: return 0
        val searchboxFragmentClassName = homeCustomizeHookPoints().searchboxFragmentClassName
        if (searchboxFragmentClassName == null) {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment host capability missing for view cleanup")
            return 0
        }
        val clazz = XposedCompat.findClassOrNull(
            searchboxFragmentClassName,
            cl,
        ) ?: run {
            XposedCompat.log("[HomeCustomizeHook] HomeSearchboxFragment class NOT FOUND for view cleanup")
            return 0
        }

        var count = 0
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
                attachHomeCustomizeWatcher(result as? View)
                result
            }
            count += 1
        } else {
            XposedCompat.log("[HomeCustomizeHook] onCreateView(LayoutInflater, ViewGroup, Bundle) NOT FOUND")
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
                attachHomeCustomizeWatcher(chain.args.firstOrNull() as? View)
                result
            }
            count += 1
        } else {
            XposedCompat.log("[HomeCustomizeHook] onViewCreated(View, Bundle) NOT FOUND")
        }

        val onResume = XposedCompat.findMethodOrNull(clazz, "onResume")
        if (onResume != null) {
            mod.hook(onResume).intercept { chain ->
                val result = chain.proceed()
                val root = runCatching {
                    val getView = chain.thisObject.javaClass.methods.firstOrNull {
                        it.name == "getView" && it.parameterTypes.isEmpty()
                    }
                    getView?.invoke(chain.thisObject) as? View
                }.getOrNull()
                attachHomeCustomizeWatcher(root)
                result
            }
            count += 1
        } else {
            XposedCompat.logD("[HomeCustomizeHook] onResume not found for view cleanup")
        }
        return count
    }

    private fun hookSearchboxAigcAnimation(cl: ClassLoader): Int {
        if (!HookSettings.isHomeSearchAigcIconHidden) return 0
        val mod = XposedCompat.module ?: return 0
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
                hideSearchboxAigcBindingViews(chain.thisObject)
                XposedCompat.logD("[HomeCustomizeHook] HomeSearchboxFragment.startSearchBoxAnim blocked")
                getBindingObject(chain.thisObject)
            }
        }
        return 1
    }

    private fun hookFeedRecommendView(cl: ClassLoader): Int {
        if (!hasFeedViewCleanupOption()) return 0
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

            count += hookHomeSectionCreatorMethods(mod, clazz, className)

            val initFeedSettingTipHeader = XposedCompat.findMethodOrNull(
                clazz,
                INIT_FEED_SETTING_TIP_HEADER_METHOD,
            )
            if (initFeedSettingTipHeader != null) {
                mod.hook(initFeedSettingTipHeader).intercept { chain ->
                    if (isFeedTipHidden()) {
                        hideFeedTipHeaderField(chain.thisObject)
                        XposedCompat.logD("[HomeCustomizeHook] $className.$INIT_FEED_SETTING_TIP_HEADER_METHOD blocked")
                        null
                    } else {
                        val result = chain.proceed()
                        hideFeedTipHeaderField(chain.thisObject)
                        result
                    }
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.$INIT_FEED_SETTING_TIP_HEADER_METHOD not found")
            }

            val initBannerCardView = XposedCompat.findMethodOrNull(
                clazz,
                INIT_BANNER_CARD_VIEW_METHOD,
            )
            if (initBannerCardView != null) {
                mod.hook(initBannerCardView).intercept { chain ->
                    val result = chain.proceed()
                    if (isHomeBannerHidden()) {
                        hideView(result as? View)
                        hideHomeBannerField(chain.thisObject)
                        XposedCompat.logD("[HomeCustomizeHook] $className.$INIT_BANNER_CARD_VIEW_METHOD hidden")
                    }
                    result
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.$INIT_BANNER_CARD_VIEW_METHOD not found")
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
                    attachHomeCustomizeWatcher(result as? View)
                    hideFeedTipHeaderField(chain.thisObject)
                    hideHomeBannerField(chain.thisObject)
                    hideHomeSectionFields(chain.thisObject)
                    result
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onCreateView not found")
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
                    attachHomeCustomizeWatcher(chain.args.firstOrNull() as? View)
                    hideFeedTipHeaderField(chain.thisObject)
                    hideHomeBannerField(chain.thisObject)
                    hideHomeSectionFields(chain.thisObject)
                    result
                }
                count += 1
            } else {
                XposedCompat.logD("[HomeCustomizeHook] $className.onViewCreated not found")
            }
        }
        return count
    }

    private fun attachHomeCustomizeWatcher(root: View?) {
        if (root == null) return
        if (!hasViewCleanupOption()) return

        scheduleApplyHomeCustomize(root)
        if (!attachedRoots.add(root)) return

        root.viewTreeObserver.addOnGlobalLayoutListener {
            runCatching { applyHomeCustomize(root) }
        }
        root.viewTreeObserver.addOnPreDrawListener {
            runCatching { applyHomeCustomize(root) }
            true
        }
        XposedCompat.log("[HomeCustomizeHook] home customize watcher attached")
    }

    private fun scheduleApplyHomeCustomize(root: View) {
        runCatching { applyHomeCustomize(root) }
        root.post { runCatching { applyHomeCustomize(root) } }
        for (delay in listOf(80L, 240L, 600L, 1200L)) {
            root.postDelayed({ runCatching { applyHomeCustomize(root) } }, delay)
        }
    }

    private fun applyHomeCustomize(root: View) {
        if (!HookSettings.isHomeCustomizeEnabled) return
        val resources = root.resources ?: return
        val packageName = root.context?.packageName ?: return

        if (HookSettings.isHomeSearchPlaceholderHidden) {
            hideViewByEntryName(root, resources, packageName, SEARCH_PLACEHOLDER_ID) {
                XposedCompat.logD("[HomeCustomizeHook] search placeholder hidden")
            }
        }
        if (HookSettings.isHomeSearchAigcIconHidden) {
            hideViewByEntryName(root, resources, packageName, SEARCH_AIGC_ICON_ID) {
                XposedCompat.logD("[HomeCustomizeHook] search AIGC icon hidden")
            }
            hideViewByEntryName(root, resources, packageName, SEARCH_AIGC_VIDEO_ID) {
                XposedCompat.logD("[HomeCustomizeHook] search AIGC video hidden")
            }
            hideViewByEntryName(root, resources, packageName, SEARCH_AIGC_BORDER_ANIM_ID) {
                XposedCompat.logD("[HomeCustomizeHook] search AIGC border animation hidden")
            }
        }
        if (HookSettings.isHomeFeedTipHidden) {
            hideFeedTipViews(root, resources, packageName)
        }
        if (HookSettings.isHomeBannerHidden) {
            hideHomeBannerViews(root, resources, packageName)
        }
        hideHomeSectionCards(root)
    }

    private fun hookHomeSectionCreatorMethods(
        mod: io.github.libxposed.api.XposedModule,
        clazz: Class<*>,
        className: String,
    ): Int {
        var count = 0
        homeSectionTargets().forEach { target ->
            val methods = clazz.declaredMethods.filter { it.name == target.methodName }
            if (methods.isEmpty()) {
                XposedCompat.logD("[HomeCustomizeHook] $className.${target.methodName} not found")
                return@forEach
            }
            methods.forEach { method ->
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isHomeSectionHidden(target)) {
                        hideView(result as? View)
                        hideHomeSectionFields(chain.thisObject, target)
                        XposedCompat.logD("[HomeCustomizeHook] ${target.label} section hidden")
                    }
                    result
                }
                count += 1
            }
        }
        return count
    }

    private fun hideFeedTipViews(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
    ) {
        hideViewByEntryName(root, resources, packageName, FEED_TIP_ID) {
            XposedCompat.logD("[HomeCustomizeHook] feed tip container hidden")
        }
        hideViewByEntryName(root, resources, packageName, FEED_TIP_RECOMMENDATION_TEXT_ID) {
            XposedCompat.logD("[HomeCustomizeHook] feed tip recommendation text hidden")
        }
        val recommendationTextId = resources.getIdentifier(
            FEED_TIP_RECOMMENDATION_TEXT_ID,
            "id",
            packageName,
        )
        if (recommendationTextId == 0) return
        val recommendationText = root.findViewById<View>(recommendationTextId) ?: return
        hideView(recommendationText.parent as? View)
    }

    private fun hideFeedTipHeaderField(fragment: Any?) {
        if (!isFeedTipHidden() || fragment == null) return
        runCatching {
            var current: Class<*>? = fragment.javaClass
            while (current != null) {
                val field = current.declaredFields.firstOrNull { it.name == FEED_TIP_HEADER_FIELD }
                if (field != null) {
                    field.isAccessible = true
                    hideView(field.get(fragment) as? View)
                    return
                }
                current = current.superclass
            }
        }
    }

    private fun hideHomeBannerField(fragment: Any?) {
        if (!isHomeBannerHidden() || fragment == null) return
        runCatching {
            var current: Class<*>? = fragment.javaClass
            while (current != null) {
                val field = current.declaredFields.firstOrNull { it.name == HOME_BANNER_FIELD }
                if (field != null) {
                    field.isAccessible = true
                    hideView(field.get(fragment) as? View)
                    return
                }
                current = current.superclass
            }
        }
    }

    private fun hideHomeSectionFields(fragment: Any?) {
        if (fragment == null) return
        homeSectionTargets().forEach { target ->
            if (isHomeSectionHidden(target)) {
                hideHomeSectionFields(fragment, target)
            }
        }
    }

    private fun hideHomeSectionFields(fragment: Any?, target: HomeSectionTarget) {
        if (fragment == null) return
        runCatching {
            var current: Class<*>? = fragment.javaClass
            while (current != null) {
                target.fieldNames.forEach { fieldName ->
                    val field = current.declaredFields.firstOrNull { it.name == fieldName }
                    if (field != null) {
                        field.isAccessible = true
                        hideView(field.get(fragment) as? View)
                    }
                }
                current = current.superclass
            }
        }
    }

    private fun hideHomeSectionCards(root: View) {
        if (HookSettings.isHomeBannerHidden) {
            hideHomeBannerCards(root)
        }
        homeSectionTargets().forEach { target ->
            if (isHomeSectionHidden(target)) {
                hideHomeSectionCards(root, target)
            }
        }
    }

    private fun hideHomeSectionCards(view: View, target: HomeSectionTarget) {
        if (matchesHomeSectionTarget(view, target)) {
            hideView(view)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            hideHomeSectionCards(group.getChildAt(index), target)
        }
    }

    private fun matchesHomeSectionTarget(view: View, target: HomeSectionTarget): Boolean {
        val className = view.javaClass.name
        val simpleName = view.javaClass.simpleName
        return target.classNames.any { targetClassName ->
            className == targetClassName || simpleName == targetClassName.substringAfterLast('.')
        }
    }

    private fun hideHomeBannerViews(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
    ) {
        hideViewByEntryName(root, resources, packageName, "header_banner") {
            XposedCompat.logD("[HomeCustomizeHook] home banner hidden by id: header_banner")
        }
        hideViewByEntryName(root, resources, packageName, "banner") {
            XposedCompat.logD("[HomeCustomizeHook] home banner hidden by id: banner")
        }
    }

    private fun hideHomeBannerCards(view: View) {
        val viewIdName = runCatching {
            if (view.id == View.NO_ID) null
            else view.resources.getResourceEntryName(view.id)
        }.getOrNull()
        if (
            viewIdName == "header_banner" ||
            viewIdName == "banner"
        ) {
            hideView(view)
        }
        val group = view as? ViewGroup ?: return
        for (index in 0 until group.childCount) {
            hideHomeBannerCards(group.getChildAt(index))
        }
    }

    private fun hideSearchboxAigcBindingViews(fragment: Any?) {
        val binding = getBindingObject(fragment) ?: return
        hideBindingView(binding, "searchboxAigcIcon")
        hideBindingView(binding, "searchboxAigcVideo")
        hideBindingView(binding, "searchboxBorderAnimView")
    }

    private fun getBindingObject(fragment: Any?): Any? {
        if (fragment == null) return null
        return runCatching {
            fragment.javaClass.fields.firstOrNull { it.name == "binding" }?.get(fragment)
                ?: fragment.javaClass.declaredFields.firstOrNull { it.name == "binding" }?.let { field ->
                    field.isAccessible = true
                    field.get(fragment)
                }
        }.getOrNull()
    }

    private fun hideBindingView(binding: Any, fieldName: String) {
        runCatching {
            val field = binding.javaClass.fields.firstOrNull { it.name == fieldName }
                ?: binding.javaClass.declaredFields.firstOrNull { it.name == fieldName }?.apply {
                    isAccessible = true
                }
                ?: return
            val view = field.get(binding) as? View ?: return
            hideView(view)
        }
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

    private fun hideViewByEntryName(
        root: View,
        resources: android.content.res.Resources,
        packageName: String,
        idName: String,
        onHidden: (() -> Unit)? = null,
    ) {
        val id = resources.getIdentifier(idName, "id", packageName)
        if (id == 0) return
        val view = root.findViewById<View>(id) ?: return
        if (hideView(view)) {
            onHidden?.invoke()
        }
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
                    hasViewCleanupOption()
            )
    }

    private fun hasViewCleanupOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                hasHomeSearchboxViewCleanupOption() ||
                    hasFeedViewCleanupOption() ||
                    hasHomeSectionHiddenOption()
            )
    }

    private fun hasHomeSearchboxViewCleanupOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                HookSettings.isHomeSearchPlaceholderHidden ||
                    HookSettings.isHomeSearchAigcIconHidden
            )
    }

    private fun hasFeedViewCleanupOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                HookSettings.isHomeFeedTipHidden ||
                    HookSettings.isHomeBannerHidden ||
                    hasHomeSectionHiddenOption()
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

    private fun hasHomeSectionHiddenOption(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            (
                HookSettings.isHomeMemoriesSectionHidden ||
                    HookSettings.isHomeSaveSectionHidden ||
                    HookSettings.isHomeRecentSectionHidden
            )
    }

    private fun isHomeSectionHidden(target: HomeSectionTarget): Boolean {
        return HookSettings.isHomeCustomizeEnabled && target.isHidden()
    }

    private fun homeSectionTargets(): List<HomeSectionTarget> {
        val points = homeCustomizeHookPoints()
        return listOf(
            HomeSectionTarget(
                label = "memories",
                methodName = INIT_STORY_CARD_VIEW_METHOD,
                fieldNames = listOf(HOME_MEMORIES_CARD_FIELD),
                classNames = points.storyCardViewClassNames,
                isHidden = { HookSettings.isHomeMemoriesSectionHidden },
            ),
            HomeSectionTarget(
                label = "save",
                methodName = INIT_SAVE_CARD_VIEW_METHOD,
                fieldNames = listOf(HOME_SAVE_CARD_FIELD),
                classNames = points.saveCardViewClassNames,
                isHidden = { HookSettings.isHomeSaveSectionHidden },
            ),
            HomeSectionTarget(
                label = "recent",
                methodName = INIT_RECENT_CARD_VIEW_METHOD,
                fieldNames = listOf(HOME_RECENT_CARD_FIELD),
                classNames = points.recentCardViewClassNames,
                isHidden = { HookSettings.isHomeRecentSectionHidden },
            ),
        )
    }

    private fun homeCustomizeHookPoints() =
        BaiduFeatureRuntime.currentHomeCustomizeHookPoints()

    private data class HomeSectionTarget(
        val label: String,
        val methodName: String,
        val fieldNames: List<String>,
        val classNames: List<String>,
        val isHidden: () -> Boolean,
    )
}
