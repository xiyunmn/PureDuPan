package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Activity
import android.app.AlertDialog
import android.os.Looper
import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingTransferHookPoints as Points
import java.lang.ref.WeakReference
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap
import org.json.JSONObject

internal object MarketingSearchTransferBlockHook {
    private const val TAG = "MarketingSearchTransferBlockHook"
    private val installed = mutableSetOf<Member>()
    private val limitStates = WeakHashMap<Any, LimitState>()

    private data class LimitState(val activity: WeakReference<Activity>, val errno: Int, val count: Int)

    fun hook(cl: ClassLoader) {
        installSearch(cl)
        installCoupon(cl)
        installSpace(cl)
        installLimit(cl)
    }

    private fun enabled(option: MarketingPopup) = HookSettings.isMarketingPopupBlocked(option)

    private fun installSearch(cl: ClassLoader) {
        attempt("search provider") {
            val owner = XposedCompat.findClassOrNull(Points.BUSINESS_PROVIDER, cl) ?: return@attempt
            owner.declaredMethods.filter {
                it.name == Points.SEARCH_GUIDE_METHOD && !Modifier.isStatic(it.modifiers) &&
                    it.returnType == Void.TYPE &&
                    it.parameterTypes.map(Class<*>::getName) == listOf(
                        Points.FRAGMENT_ACTIVITY, "int", "java.lang.String", "int", Points.DIALOG_CALLBACK,
                    )
            }.forEach { method ->
                install(method) { _, args ->
                    if (!enabled(MarketingPopup.SEARCH_MEMBERSHIP)) return@install false
                    val cancel = callbackCancellation(args[4]) ?: return@install false
                    cancel()
                    true
                }
            }
        }
        attempt("Flutter search result") {
            val owner = XposedCompat.findClassOrNull(Points.FLUTTER_ROUTER, cl) ?: return@attempt
            val callType = XposedCompat.findClassOrNull(Points.FLUTTER_METHOD_CALL, cl) ?: return@attempt
            val resultType = XposedCompat.findClassOrNull(Points.FLUTTER_RESULT, cl) ?: return@attempt
            val methodField = callType.getField("method")
            val argument = callType.getMethod("argument", String::class.java)
            val success = resultType.getMethod("success", Any::class.java)
            val method = owner.getDeclaredMethod("onMethodCall", callType, resultType).takeIf {
                it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers)
            } ?: return@attempt
            install(method) { _, args ->
                if (!enabled(MarketingPopup.SEARCH_MEMBERSHIP)) return@install false
                val call = args[0] ?: return@install false
                if (!MarketingSearchTransferRules.isSearchPurchase(
                        methodField.get(call), argument.invoke(call, "sid"),
                    )
                ) return@install false
                // Complete the exact purchase request as cancelled, without invoking any purchase callback.
                success.invoke(args[1], MarketingSearchTransferRules.SEARCH_CANCEL_RESULT)
                true
            }
        }
    }

    private fun installCoupon(cl: ClassLoader) = attempt("transfer coupon") {
        val owner = XposedCompat.findClassOrNull(Points.TRANSFER_VIEW_MODEL, cl) ?: return@attempt
        val method = owner.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterTypes.map(Class<*>::getName) == listOf("android.app.Activity", Points.FUNCTION0)
        } ?: return@attempt
        install(method) { _, args ->
            if (!enabled(MarketingPopup.TRANSFER_COUPON)) return@install false
            val noCoupon = functionCancellation(args[1]) ?: return@install false
            // This is the native no-coupon continuation. Constructing/dismissing DiscountCenterDialog
            // would finish its Activity and interrupt the surrounding successful-save flow.
            noCoupon()
            true
        }
    }

    private fun installSpace(cl: ClassLoader) {
        listOf(Points.GUIDE_CONTEXT, Points.GUIDE_COMPANION).forEach { name ->
            attempt("transfer space $name") {
                val owner = XposedCompat.findClassOrNull(name, cl) ?: return@attempt
                owner.declaredMethods.filter {
                    it.name == Points.SHOW_NO_SPACE_METHOD && it.returnType == Void.TYPE &&
                        MarketingSearchTransferRules.isSpaceEntry(it.parameterTypes.map(Class<*>::getName))
                }.forEach { method ->
                    install(method) { _, args ->
                        if (!enabled(MarketingPopup.TRANSFER_SPACE)) return@install false
                        val activity = args[0] as? Activity ?: return@install false
                        val bean = args[1] ?: return@install false
                        if (!MarketingSearchTransferRules.isTransferSpace(getter(bean, "getSubScene"))) {
                            return@install false
                        }
                        val cancel = if (args.size == 3) {
                            callbackCancellation(args[2])
                        } else {
                            beanCancellation(bean)
                        } ?: return@install false
                        val request = getterIfPresent(bean, "getRequestParams") as? String
                        val finishContainer = request?.let {
                            runCatching { JSONObject(it).optBoolean(Points.DISMISS_FINISH_KEY, false) }
                                .getOrDefault(false)
                        } ?: false
                        showFailure(activity, "save_file_fail_no_space", "空间不足，无法保存") {
                            cancel()
                            if (finishContainer) finish(activity)
                        }
                    }
                }
            }
        }
    }

    private fun installLimit(cl: ClassLoader) = attempt("transfer limit owner") {
        val owners = linkedSetOf<Class<*>>()
        XposedCompat.findClassOrNull(Points.TRANSFER_LIMIT_GUIDE, cl)?.let(owners::add)
        // International TransferFileLimitGuide is obfuscated. Its type is retained in the known
        // SaveResultReceiver's narrow (presenter, limitManager, fileCount) dispatch contract.
        val receiver = XposedCompat.findClassOrNull(Points.SAVE_RESULT_RECEIVER, cl)
        receiver?.declaredMethods?.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                it.parameterTypes.size == 3 && it.parameterTypes[0].name == Points.SHARE_PRESENTER &&
                it.parameterTypes[2] == Int::class.javaPrimitiveType
        }?.map { it.parameterTypes[1] }?.distinct()?.singleOrNull()?.let(owners::add)
        owners.forEach { owner -> attempt("transfer limit ${owner.name}") { installLimitOwner(owner, cl) } }
    }

    private fun installLimitOwner(owner: Class<*>, cl: ClassLoader) {
        val constructor = owner.declaredConstructors.singleOrNull {
            it.parameterTypes.toList() == listOf(
                Int::class.javaPrimitiveType, Activity::class.java,
                Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
        } ?: return
        val promotions = owner.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                MarketingSearchTransferRules.isLimitPromotion(it.parameterTypes.map(Class<*>::getName))
        }
        val entry = owner.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && it.returnType == Void.TYPE &&
                MarketingSearchTransferRules.isLimitEntry(it.parameterTypes.map(Class<*>::getName))
        } ?: return
        if (promotions.size != 1) return
        val activityField = owner.declaredFields.singleOrNull {
            !Modifier.isStatic(it.modifiers) && Activity::class.java.isAssignableFrom(it.type)
        }?.apply { isAccessible = true } ?: return
        val mod = XposedCompat.module ?: return
        synchronized(installed) {
            if (constructor !in installed) {
                constructor.isAccessible = true
                mod.hook(constructor).intercept { chain ->
                    val result = chain.proceed()
                    val instance = chain.thisObject
                    val activity = chain.args[1] as? Activity
                    if (instance != null && activity != null) {
                        synchronized(limitStates) {
                            limitStates[instance] = LimitState(
                                WeakReference(activity), chain.args[0] as Int, chain.args[3] as Int,
                            )
                        }
                    }
                    result
                }
                installed += constructor
            }
        }
        install(promotions.single()) { instance, args ->
            if (!enabled(MarketingPopup.TRANSFER_LIMIT) || args[1] != false) return@install false
            // hideBuyBtn=true is the existing-coupon path. Leave its confirmation and quota unchanged.
            val activity = activityField.get(instance) as? Activity ?: return@install false
            showFailure(activity, "save_file_over_limit", "本次保存文件数超过上限，减少些文件再试试吧") {
                if (args[2] == true) finish(activity)
            }
        }
        val privilegeMethod = XposedCompat.findClassOrNull(Points.GUIDE_CONTEXT, cl)?.declaredMethods
            ?.singleOrNull {
                it.name == Points.TRANSFER_PRIVILEGE_ENABLED && Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.map(Class<*>::getName) == listOf(Points.FRAGMENT_ACTIVITY) &&
                    it.returnType in listOf(Boolean::class.javaPrimitiveType, java.lang.Boolean::class.java)
            }
        install(entry) { instance, _ ->
            if (!enabled(MarketingPopup.TRANSFER_LIMIT)) return@install false
            val state = synchronized(limitStates) { limitStates[instance] } ?: return@install false
            val activity = state.activity.get() ?: return@install false
            val newGuide = if (entry.parameterCount == 1) false else {
                privilegeMethod?.invoke(null, activity) as? Boolean
            }
            if (!MarketingSearchTransferRules.isOldLimitUpgrade(state.errno, state.count, newGuide)) {
                return@install false
            }
            // The old -33 branch has no coupon choice; its Cancel action finishes the save container.
            showFailure(activity, "save_file_over_limit", "本次保存文件数超过上限，减少些文件再试试吧") {
                finish(activity)
            }
        }
    }

    private fun install(method: Method, handler: (Any?, List<Any?>) -> Boolean) {
        val mod = XposedCompat.module ?: return
        synchronized(installed) {
            if (method in installed) return
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val handled = runCatching { handler(chain.thisObject, chain.args.toList()) }
                    .onFailure { XposedCompat.logW("[$TAG] ${method.name}: ${it.javaClass.simpleName}") }
                    .getOrDefault(false)
                if (handled) null else chain.proceed()
            }
            installed += method
        }
    }

    private fun callbackCancellation(callback: Any?): (() -> Unit)? {
        return MarketingSearchTransferRules.cancellation(callback) {
            XposedCompat.logW("[$TAG] cancel callback: ${it.javaClass.simpleName}")
        }
    }

    private fun functionCancellation(callback: Any?): (() -> Unit)? {
        if (callback == null) return {}
        val invoke = callback.javaClass.getMethod("invoke").apply { isAccessible = true }
        return { safely { invoke.invoke(callback) } }
    }

    private fun beanCancellation(bean: Any): (() -> Unit)? {
        val close = functionCancellation(getter(bean, "getOnClose")) ?: return null
        val dismiss = functionCancellation(getter(bean, "getOnDismiss")) ?: return null
        return { close(); dismiss() }
    }

    private fun getter(instance: Any, name: String): Any? =
        instance.javaClass.getMethod(name).invoke(instance)

    private fun getterIfPresent(instance: Any, name: String): Any? =
        instance.javaClass.methods.singleOrNull { it.name == name && it.parameterCount == 0 }?.invoke(instance)

    private fun showFailure(activity: Activity, messageResource: String, fallback: String, onDismiss: () -> Unit): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper() || activity.isFinishing || activity.isDestroyed) return false
        val dialog = AlertDialog.Builder(activity)
            .setTitle(hostText(activity, "save_file_fail", "保存失败"))
            .setMessage(hostText(activity, messageResource, fallback))
            .setPositiveButton(hostText(activity, "know_it", "知道了"), null)
            .create()
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
        return true
    }

    private fun hostText(activity: Activity, name: String, fallback: String): String {
        val id = activity.resources.getIdentifier(name, "string", activity.packageName)
        return if (id == 0) fallback else activity.getString(id)
    }

    private fun finish(activity: Activity) {
        if (!activity.isFinishing && !activity.isDestroyed) activity.finish()
    }

    private inline fun safely(action: () -> Unit) {
        runCatching(action).onFailure { XposedCompat.logW("[$TAG] cancel callback: ${it.javaClass.simpleName}") }
    }

    private inline fun attempt(label: String, action: () -> Unit) {
        runCatching(action).onFailure { XposedCompat.logW("[$TAG] $label: ${it.javaClass.simpleName}") }
    }
}
