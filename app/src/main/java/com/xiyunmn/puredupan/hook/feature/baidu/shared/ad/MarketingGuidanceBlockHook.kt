package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Activity
import android.app.Dialog
import android.os.Looper
import android.view.View
import com.xiyunmn.puredupan.hook.config.model.MarketingPopup
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingGuidanceHookPoints as Points
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object MarketingGuidanceBlockHook {
    private const val TAG = "MarketingGuidanceBlockHook"
    private val installed = mutableSetOf<Method>()

    @Synchronized
    fun hook(cl: ClassLoader) {
        if (XposedCompat.module == null) return
        if (HookSettings.isMarketingPopupBlocked(MarketingPopup.MIGHTY_MARKETING)) {
            attempt("mighty") { installGuidance(cl, Points.MIGHTY, MarketingPopup.MIGHTY_MARKETING, 1) }
        }
        if (HookSettings.isMarketingPopupBlocked(MarketingPopup.MODERATE_MARKETING)) {
            attempt("moderate") { installGuidance(cl, Points.MODERATE, MarketingPopup.MODERATE_MARKETING, 2) }
        }
        if (!BaiduFeatureRuntime.isCurrentIntlHost() &&
            HookSettings.isMarketingPopupBlocked(MarketingPopup.FREE_MODE_FLOAT)
        ) {
            attempt("free mode") { installFreeMode(cl) }
        }
    }

    private class SceneBinding(
        val sceneField: Field,
        val activityField: Field,
        val fromPushField: Field?,
        val scheme: Field,
        val guideType: Field,
        val sceneId: Field,
        val feature: Field,
        val eventType: Field?,
        val eventName: Field?,
        val dismiss: Method,
    ) {
        fun isMarketing(instance: Any, guide: Int): Boolean = runCatching {
            val activity = activityField.get(instance) as? Activity
            val scene = sceneField.get(instance) ?: return@runCatching false
            MarketingGuidanceRules.shouldBlockGuidance(
                MarketingPopupContext.isMainPage(activity),
                guide,
                MarketingGuidanceRules.Payload(
                    guideType = guideType.get(scene) as? Int,
                    scene = sceneId.get(scene) as? Int,
                    feature = feature.get(scene) as? Int,
                    scheme = scheme.get(scene) as? String,
                    eventType = eventType?.get(scene) as? Int,
                    eventName = eventName?.get(scene) as? String,
                    generativePush = fromPushField?.get(instance) == true,
                ),
            )
        }.getOrDefault(false)
    }

    private fun installGuidance(cl: ClassLoader, name: String, option: MarketingPopup, guide: Int) {
        val mod = XposedCompat.module ?: return
        val type = XposedCompat.findClassOrNull(name, cl) ?: return
        if (guide == 1 && !Dialog::class.java.isAssignableFrom(type)) return
        if (guide == 2 && !View::class.java.isAssignableFrom(type)) return
        val show = method(type, Points.SHOW, Void.TYPE) ?: return
        if (show in installed) return
        val model = XposedCompat.findClassOrNull(Points.SCENE, cl) ?: return
        val sceneField = singleField(type, model) ?: return
        val activityField = singleField(type, Activity::class.java) ?: return
        val booleans = type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == Boolean::class.javaPrimitiveType }
        if (booleans.size > 1) return
        val eventType = serializedField(model, Points.EVENT_TYPE, Int::class.javaObjectType)
        val eventName = serializedField(model, Points.EVENT_NAME, String::class.java)
        // International 13.11.13 has neither field; current domestic models have both.
        if ((eventType == null) != (eventName == null)) return
        val binding = SceneBinding(
            sceneField,
            activityField,
            booleans.singleOrNull()?.apply { isAccessible = true },
            serializedField(model, Points.SCHEME, String::class.java) ?: return,
            serializedField(model, Points.GUIDE_TYPE, Int::class.javaObjectType) ?: return,
            serializedField(model, Points.SCENE_ID, Int::class.javaObjectType) ?: return,
            serializedField(model, Points.FEATURE, Int::class.javaObjectType) ?: return,
            eventType,
            eventName,
            method(type, Points.DISMISS, Void.TYPE) ?: return,
        )
        mod.hook(show).intercept { chain ->
            val instance = chain.thisObject
            val block = instance != null && onMainThread() && HookSettings.isMarketingPopupBlocked(option) &&
                binding.isMarketing(instance, guide)
            // Native show installs its queue callback and float timer; native dismiss completes both.
            val result = chain.proceed()
            if (block) attempt("dismiss $name") { binding.dismiss.invoke(instance) }
            result
        }
        installed += show
    }

    private class FreeModeBinding(
        val config: Method,
        val switch: Method,
        val pages: Field,
        val nodeKey: Field,
        val remove: Method,
    ) {
        fun shouldBlock(manager: Any?, activity: Activity?, hostAllows: Boolean): Boolean {
            if (manager == null || activity == null || !onMainThread() ||
                !HookSettings.isMarketingPopupBlocked(MarketingPopup.FREE_MODE_FLOAT)
            ) return false
            return runCatching {
                val configValue = config.invoke(manager) ?: return false
                MarketingGuidanceRules.shouldBlockFreeFloat(
                    onTargetPage = MarketingPopupContext.isMainPage(activity) || MarketingPopupContext.isSearchPage(activity),
                    hostAllowsFloat = hostAllows,
                    switchStatus = switch.invoke(manager) as? String,
                    nodeKey = nodeKey.get(configValue) as? String,
                    configuredPages = pages.get(configValue) as? String,
                    activityClassName = activity.javaClass.name,
                )
            }.getOrDefault(false)
        }

        fun remove(manager: Any?, activity: Activity?) {
            // closeAll=false leaves other pages and the saved native switch untouched.
            attempt("remove free float") { remove.invoke(manager, activity, false) }
        }
    }

    private fun installFreeMode(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        val type = XposedCompat.findClassOrNull(Points.FREE_MODE_MANAGER, cl) ?: return
        val configType = XposedCompat.findClassOrNull(Points.FREE_MODE_CONFIG, cl) ?: return
        val binding = FreeModeBinding(
            method(type, Points.GET_CONFIG, configType) ?: return,
            method(type, Points.GET_SWITCH, String::class.java) ?: return,
            serializedField(configType, Points.FREE_MODE_PAGES, String::class.java) ?: return,
            serializedField(configType, Points.NODE_KEY, String::class.java) ?: return,
            method(type, Points.REMOVE_FLOAT, Void.TYPE, Activity::class.java, Boolean::class.javaPrimitiveType!!) ?: return,
        )
        method(type, Points.SUPPORT_CONTEXT, Boolean::class.javaPrimitiveType!!, Activity::class.java)?.let { method ->
            if (method !in installed) attempt("supportCtx") {
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.args.firstOrNull() as? Activity
                    if (binding.shouldBlock(chain.thisObject, activity, result == true)) {
                        binding.remove(chain.thisObject, activity)
                        false
                    } else result
                }
                installed += method
            }
        }
        method(type, Points.ATTACH_FLOAT, Void.TYPE, Activity::class.java)?.let { method ->
            if (method !in installed) attempt("attachFloatView") {
                mod.hook(method).intercept { chain ->
                    val activity = chain.args.firstOrNull() as? Activity
                    if (binding.shouldBlock(chain.thisObject, activity, true)) {
                        binding.remove(chain.thisObject, activity)
                        null
                    } else chain.proceed()
                }
                installed += method
            }
        }
        method(type, Points.CHANGE_FLOAT_VISIBLE, Void.TYPE, Boolean::class.javaPrimitiveType!!, Activity::class.java)?.let { method ->
            if (method !in installed) attempt("changeFloatVisible") {
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.args.getOrNull(1) as? Activity
                    if (chain.args.firstOrNull() == true && binding.shouldBlock(chain.thisObject, activity, true)) {
                        binding.remove(chain.thisObject, activity)
                    }
                    result
                }
                installed += method
            }
        }
    }

    private fun serializedField(type: Class<*>, key: String, fieldType: Class<*>): Field? =
        type.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && field.type == fieldType && field.declaredAnnotations.any { annotation ->
                val annotationType = annotation.annotationClass.java
                annotationType.name == Points.SERIALIZED_NAME &&
                    runCatching { annotationType.getDeclaredMethod("value").invoke(annotation) == key }.getOrDefault(false)
            }
        }.singleOrNull()?.apply { isAccessible = true }

    private fun singleField(type: Class<*>, fieldType: Class<*>): Field? =
        type.declaredFields.filter { !Modifier.isStatic(it.modifiers) && it.type == fieldType }
            .singleOrNull()?.apply { isAccessible = true }

    private fun method(type: Class<*>, name: String, returns: Class<*>, vararg params: Class<*>): Method? =
        type.declaredMethods.singleOrNull {
            it.name == name && !Modifier.isStatic(it.modifiers) && !Modifier.isAbstract(it.modifiers) &&
                it.returnType == returns && it.parameterTypes.contentEquals(params)
        }?.apply { isAccessible = true }

    private fun onMainThread() = Looper.myLooper() == Looper.getMainLooper()

    private inline fun attempt(action: String, block: () -> Unit) {
        runCatching(block).onFailure { XposedCompat.logW("[$TAG] $action: ${it.javaClass.simpleName}") }
    }
}
