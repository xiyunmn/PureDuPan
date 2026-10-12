package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeRecentScrollRangeResolver {
    data class ResolvedMethods(val callback: Method, val updateSize: Method)

    fun resolve(cl: ClassLoader): ResolvedMethods? {
        val clazz = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.HOME_FRAGMENT, cl)
            ?: return null
        val updateSize = clazz.declaredMethods.singleOrNull { method ->
            method.name == BaiduHomeCardHookPoints.HOME_TOP_SIZE_METHOD &&
                !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }?.apply { isAccessible = true } ?: return null
        val callback = clazz.declaredMethods.singleOrNull { method ->
            method.name.startsWith(BaiduHomeCardHookPoints.HOME_TOP_SIZE_CALLBACK_PREFIX) &&
                Modifier.isStatic(method.modifiers) && !method.isSynthetic && !method.isBridge &&
                method.returnType.name == "kotlin.Unit" &&
                method.parameterTypes.contentEquals(
                    arrayOf(clazz, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
                )
        }?.apply { isAccessible = true } ?: return null
        return ResolvedMethods(callback, updateSize)
    }
}
