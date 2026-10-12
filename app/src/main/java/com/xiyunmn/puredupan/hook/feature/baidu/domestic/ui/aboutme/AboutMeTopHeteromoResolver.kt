package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui.aboutme

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduAboutMeTopHeteromoHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object AboutMeTopHeteromoResolver {
    fun resolve(cl: ClassLoader): Class<*>? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduAboutMeTopHeteromoHookPoints.ABOUT_ME_TOP_FRAGMENT_HETEROMO,
            cl,
        ) ?: return null
        return clazz.takeIf { findMyCardHasOperationMethod(it) != null }
    }

    fun findMyCardHasOperationMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduAboutMeTopHeteromoHookPoints.POPUP_RESPONSE
        }?.apply { isAccessible = true }
    }

    fun findSetCardTextMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].name == BaiduAboutMeTopHeteromoHookPoints.CENTER_CONFIG
        }?.apply { isAccessible = true }
    }

    fun findSetCardUiMethod(clazz: Class<*>): Method? {
        return clazz.declaredMethods.singleOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0].name == BaiduAboutMeTopHeteromoHookPoints.CENTER_CONFIG &&
                method.parameterTypes[1].name == BaiduAboutMeTopHeteromoHookPoints.POPUP_RESPONSE
        }?.apply { isAccessible = true }
    }
}
