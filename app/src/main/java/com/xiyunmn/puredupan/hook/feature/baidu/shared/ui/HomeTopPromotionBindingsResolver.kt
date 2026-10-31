package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduSharedHookPoints
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 按方法签名定位三个宿主的顶部运营和商业广告数据源。 */
internal object HomeTopPromotionBindingsResolver {
    data class Bindings(val requests: List<Method>, val emptySource: Constructor<*>)

    fun resolve(cl: ClassLoader): List<Bindings> =
        BaiduSharedHookPoints.HOME_TOP_BANNER_HELPERS.mapNotNull { name ->
            runCatching { resolveOwner(cl, name) }.getOrElse {
                XposedCompat.log("[HomeTopPromotionBindingsResolver] $name FAILED: ${it.message}")
                null
            }
        }

    private fun resolveOwner(cl: ClassLoader, name: String): Bindings? {
        val owner = XposedCompat.findClassOrNull(name, cl)
            ?: return null
        if (owner.declaredConstructors.none {
                it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            }) return null
        val liveData = XposedCompat.findClassOrNull("androidx.lifecycle.LiveData", cl) ?: return null
        val mutableLiveData = XposedCompat.findClassOrNull("androidx.lifecycle.MutableLiveData", cl)
            ?: return null
        if (!liveData.isAssignableFrom(mutableLiveData)) return null
        val requests = owner.declaredMethods.filter {
            !Modifier.isStatic(it.modifiers) && !it.isSynthetic && !it.isBridge &&
                it.parameterCount == 0 && it.returnType == liveData
        }
        // 校验顶部广告的两个数据入口，结构不符时放弃匹配。
        if (requests.size != 2) {
            XposedCompat.log("[HomeTopPromotionBindingsResolver] $name ambiguous sources: ${requests.size}")
            return null
        }
        val constructor = mutableLiveData.getConstructor(Any::class.java)
        requests.forEach { it.isAccessible = true }
        return Bindings(requests, constructor)
    }
}
