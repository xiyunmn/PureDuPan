package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduMarketingPageHookPoints
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

internal object MarketingPopupContext {
    private data class Accessors(val methods: List<Method>, val fields: List<Field>)
    private val accessors = ConcurrentHashMap<Class<*>, Accessors>()

    fun activity(value: Any?): Activity? {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        fun unwrap(candidate: Any?, depth: Int): Activity? {
            if (candidate == null || depth > 12 || !visited.add(candidate)) return null
            return when (candidate) {
                is Activity -> candidate
                is Dialog -> unwrap(candidate.context, depth + 1)
                is View -> unwrap(candidate.context, depth + 1)
                is ContextWrapper -> unwrap(candidate.baseContext, depth + 1)
                is Context -> null
                else -> {
                    val binding = accessors.getOrPut(candidate.javaClass) { resolve(candidate.javaClass) }
                    binding.methods.firstNotNullOfOrNull { method ->
                        unwrap(runCatching { method.invoke(candidate) }.getOrNull(), depth + 1)
                    } ?: binding.fields.firstNotNullOfOrNull { field ->
                        unwrap(runCatching { field.get(candidate) }.getOrNull(), depth + 1)
                    }
                }
            }
        }
        return unwrap(value, 0)
    }

    fun isMainPage(activity: Activity?): Boolean {
        var type: Class<*>? = activity?.javaClass
        while (type != null && Activity::class.java.isAssignableFrom(type)) {
            if (type.name in BaiduMarketingPageHookPoints.mainPages) return true
            type = type.superclass
        }
        return false
    }

    fun isSearchPage(activity: Activity?): Boolean {
        if (activity == null) return false
        if (activity.javaClass.name in BaiduMarketingPageHookPoints.searchPages) return true
        if (activity.javaClass.name != BaiduMarketingPageHookPoints.flutterActivity) return false
        val route = runCatching {
            activity.javaClass.getMethod("getUrl").invoke(activity) as? String
        }.getOrNull() ?: runCatching {
            activity.javaClass.getField("path").get(activity) as? String
        }.getOrNull() ?: runCatching {
            activity.intent?.getStringExtra("path") ?: activity.intent?.getStringExtra("extra_path")
        }.getOrNull()
        return route == BaiduMarketingPageHookPoints.searchRoute
    }

    private fun resolve(type: Class<*>): Accessors {
        val methods = listOf("getActivity", "getContext").mapNotNull { name ->
            runCatching { type.getMethod(name) }.getOrNull()?.takeIf {
                Context::class.java.isAssignableFrom(it.returnType)
            }
        }
        val fields = mutableListOf<Field>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            val owner = current
            for (name in listOf("activity", "mActivity", "context", "mContext")) {
                runCatching { owner.getDeclaredField(name) }.getOrNull()?.takeIf {
                    Context::class.java.isAssignableFrom(it.type)
                }?.let { field ->
                    runCatching { field.isAccessible = true }.onSuccess { fields += field }
                }
            }
            current = current.superclass
        }
        return Accessors(methods, fields)
    }
}
