package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Dialog
import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingDialogHookPoints as Points
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList

internal object MarketingDialogBlockHook {
    private const val TAG = "MarketingDialogBlockHook"

    private data class Binding(
        val target: Class<*>,
        val option: MarketingPopup,
        val beforeShow: Boolean,
        val close: (Any) -> Unit,
    )

    private val installed = mutableMapOf<Method, CopyOnWriteArrayList<Binding>>()
    private val offerEntries = mutableSetOf<Method>()

    fun hook(cl: ClassLoader) {
        fragment(cl, MarketingPopup.OPERATION_IMAGE, Points.OPERATION_IMAGE)
        fragment(cl, MarketingPopup.NEW_USER_OFFER, Points.NEW_USER_OFFER)
        fragment(cl, MarketingPopup.NEW_USER_REWARD, Points.NEW_USER_REWARD, "safeDismiss")
        fragment(cl, MarketingPopup.NEW_USER_REWARD_V2, Points.NEW_USER_REWARD_V2, "safeDismiss")
        fragment(cl, MarketingPopup.COIN_PROMOTION, Points.COIN_PROMOTION, "safeDismiss")
        fragment(cl, MarketingPopup.FREE_MODE, Points.FREE_MODE)
        fragment(cl, MarketingPopup.FREE_MODE_NEW, Points.FREE_MODE_NEW)

        animatedDialog(cl, MarketingPopup.OPERATION_ANIMATION, Points.OPERATION_ANIMATION)
        animatedDialog(cl, MarketingPopup.OPERATION_AFX, Points.OPERATION_AFX)

        listOf(
            MarketingPopup.COUPON_GIFT_V3 to Points.COUPON_GIFT_V3,
            MarketingPopup.COUPON_GIFT_V2 to Points.COUPON_GIFT_V2,
            MarketingPopup.LIFE_COUPON to Points.LIFE_COUPON,
            MarketingPopup.LIFE_PRODUCT to Points.LIFE_PRODUCT,
            MarketingPopup.LIFE_PRODUCT_V3 to Points.LIFE_PRODUCT_V3,
            MarketingPopup.LIFE_V10 to Points.LIFE_V10,
            MarketingPopup.LIFE_V10_REPURCHASE to Points.LIFE_V10_REPURCHASE,
            MarketingPopup.LIFE_COMBO to Points.LIFE_COMBO,
            MarketingPopup.LIFE_LIMITED to Points.LIFE_LIMITED,
            MarketingPopup.LIFE_RETENTION to Points.LIFE_RETENTION,
            MarketingPopup.LIFE_PRICE_UPGRADE to Points.LIFE_PRICE_UPGRADE,
            MarketingPopup.OVERDUE_UNION to Points.OVERDUE_UNION,
            MarketingPopup.OVERDUE_COUPON to Points.OVERDUE_COUPON,
            MarketingPopup.OVERDUE_PRODUCT to Points.OVERDUE_PRODUCT,
        ).forEach { (option, name) -> priorityDialog(cl, option, name, "performDismissCloseClick") }

        listOf(
            MarketingPopup.INCENTIVE_ENTRANCE to Points.INCENTIVE_ENTRANCE,
            MarketingPopup.INCENTIVE_GUIDE to Points.INCENTIVE_GUIDE,
            MarketingPopup.INCENTIVE_NEXT to Points.INCENTIVE_NEXT,
        ).forEach { (option, name) -> priorityDialog(cl, option, name, "deletePriority") }

        myPageOffer(cl)
    }

    private fun target(cl: ClassLoader, option: MarketingPopup, name: String): Class<*>? {
        if (!HookSettings.isMarketingPopupBlocked(option)) return null
        return runCatching { XposedCompat.findClassOrNull(name, cl) }
            .onFailure { logFailure("resolve $name", it) }.getOrNull()
    }

    private fun fragment(
        cl: ClassLoader,
        option: MarketingPopup,
        name: String,
        closeName: String = "dismissAllowingStateLoss",
    ) {
        val clazz = target(cl, option, name) ?: return
        runCatching {
            val start = MarketingDialogBindings.noArgVoid(clazz, "onStart") ?: return
            val close = MarketingDialogBindings.noArgVoid(clazz, closeName) ?: return
            val getDialog = clazz.methods.singleOrNull {
                it.name == "getDialog" && it.parameterTypes.isEmpty() &&
                    Dialog::class.java.isAssignableFrom(it.returnType)
            } ?: return
            getDialog.isAccessible = true
            install(start, Binding(clazz, option, false) { instance ->
                val dialog = getDialog.invoke(instance) as? Dialog
                closeHidden(dialog) { close.invoke(instance) }
            })
        }.onFailure { logFailure("install $name", it) }
    }

    private fun animatedDialog(cl: ClassLoader, option: MarketingPopup, name: String) {
        val clazz = target(cl, option, name) ?: return
        if (!Dialog::class.java.isAssignableFrom(clazz)) return
        runCatching {
            val show = MarketingDialogBindings.noArgVoid(clazz, "show") ?: return
            install(show, Binding(clazz, option, false) { instance ->
                val dialog = instance as Dialog
                closeHidden(dialog) { dialog.dismiss() }
            })
        }.onFailure { logFailure("install $name", it) }
    }

    private fun priorityDialog(
        cl: ClassLoader,
        option: MarketingPopup,
        name: String,
        cleanupName: String,
    ) {
        val clazz = target(cl, option, name) ?: return
        if (!Dialog::class.java.isAssignableFrom(clazz)) return
        runCatching {
            val show = MarketingDialogBindings.noArgVoid(clazz, "show") ?: return
            val cleanup = MarketingDialogBindings.noArgVoid(clazz, cleanupName) ?: return
            install(show, Binding(clazz, option, true) { instance ->
                // The host cleanup deletes priority 107/111 and preserves its close callback.
                // Do not invoke a button: some close buttons launch another promotion.
                cleanup.invoke(instance)
                if (cleanupName == "deletePriority") (instance as Dialog).dismiss()
            })
        }.onFailure { logFailure("install $name", it) }
    }

    private fun install(method: Method, binding: Binding) {
        val mod = XposedCompat.module ?: return
        synchronized(installed) {
            installed[method]?.let { bindings ->
                if (bindings.none { it.target == binding.target && it.option == binding.option }) {
                    bindings += binding
                }
                return
            }
            val bindings = CopyOnWriteArrayList(listOf(binding))
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val instance = chain.thisObject
                val selected = bindings.firstOrNull {
                    instance != null && it.target.isInstance(instance) &&
                        HookSettings.isMarketingPopupBlocked(it.option) &&
                        MarketingPopupContext.isMainPage(MarketingPopupContext.activity(instance))
                }
                if (selected == null || instance == null) return@intercept chain.proceed()
                if (selected.beforeShow && close(selected, instance)) return@intercept null
                val result = chain.proceed()
                if (!selected.beforeShow) close(selected, instance)
                result
            }
            installed[method] = bindings
            XposedCompat.logD("[$TAG] installed ${method.declaringClass.name}.${method.name}")
        }
    }

    private fun close(binding: Binding, instance: Any): Boolean =
        runCatching { binding.close(instance) }
            .onFailure { logFailure("close ${binding.option}", it) }.isSuccess

    private fun closeHidden(dialog: Dialog?, close: () -> Unit) {
        val decor = dialog?.window?.decorView
        val originalAlpha = decor?.alpha
        decor?.alpha = 0f
        try {
            close()
        } finally {
            // Dialog dismissal detaches its window synchronously, including DialogFragment's
            // dismissAllowingStateLoss. Preserve the view state if the host reuses the instance.
            if (originalAlpha != null) decor?.alpha = originalAlpha
        }
    }

    private fun myPageOffer(cl: ClassLoader) {
        val clazz = target(cl, MarketingPopup.MY_PAGE_OFFER, Points.USER_ACTIVITY_VIEW_MODEL) ?: return
        runCatching {
            val activityClass = XposedCompat.findClassOrNull(Points.FRAGMENT_ACTIVITY, cl) ?: return
            val liveDataClass = XposedCompat.findClassOrNull(Points.MUTABLE_LIVE_DATA, cl) ?: return
            val observer = XposedCompat.findClassOrNull(Points.MY_OFFER_OBSERVER, cl)
            val entry = MarketingDialogBindings.offerEntry(
                clazz, activityClass, Points.MY_OFFER_ENTRY, observer?.enclosingMethod,
            ) ?: return
            val state = MarketingDialogBindings.offerState(clazz, liveDataClass) ?: return
            val setValue = liveDataClass.getMethod("setValue", Any::class.java)
            state.isAccessible = true
            entry.isAccessible = true
            setValue.isAccessible = true
            val mod = XposedCompat.module ?: return
            synchronized(offerEntries) {
                if (entry in offerEntries) return
                mod.hook(entry).intercept { chain ->
                    val activity = MarketingPopupContext.activity(chain.args.firstOrNull())
                    if (!HookSettings.isMarketingPopupBlocked(MarketingPopup.MY_PAGE_OFFER) ||
                        !MarketingPopupContext.isMainPage(activity)
                    ) return@intercept chain.proceed()
                    // Match the host's disabled branch, so the page observer does not keep waiting.
                    val declined = runCatching {
                        val liveData = state.get(chain.thisObject) ?: return@runCatching false
                        setValue.invoke(liveData, false)
                        true
                    }.onFailure { logFailure("decline 3C1 offer", it) }.getOrDefault(false)
                    if (declined) null else chain.proceed()
                }
                offerEntries += entry
            }
        }.onFailure { logFailure("install 3C1 offer", it) }
    }

    private fun logFailure(action: String, error: Throwable) {
        XposedCompat.logW("[$TAG] $action: ${error.javaClass.simpleName}: ${error.message}")
    }
}
