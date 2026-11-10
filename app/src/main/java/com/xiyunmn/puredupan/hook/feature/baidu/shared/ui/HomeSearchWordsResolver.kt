package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object HomeSearchWordsResolver {
    fun resolve(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.HOME_TITLE_VIEW_MODEL, cl)
            ?: return null
        if (clazz.declaredFields.none { it.type.name == BaiduHomeCardHookPoints.SEARCH_CONTENT_HELPER }) {
            return null
        }
        // 三宿主的搜索词初始化入口签名一致，国际版方法名经过混淆。
        return clazz.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
                method.parameterTypes.let { types ->
                    types.size == 2 && types[0] == Context::class.java &&
                        types[1].name == "androidx.lifecycle.Lifecycle"
                }
        }?.apply { isAccessible = true }
    }
}
