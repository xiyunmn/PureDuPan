package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticSwanPreloadResolver {
    fun resolvePrefetchEventMethod(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_STABLE_CLASS,
            cl,
        ) ?: return null
        if (!isPrefetchManagerClass(clazz, cl)) return null
        val prefetchEventClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return null
        val matches = clazz.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == prefetchEventClass
        }
        return selectPrefetchEventEntryMethod(clazz, cl, matches)?.apply { isAccessible = true }
    }

    private fun selectPrefetchEventEntryMethod(
        clazz: Class<*>,
        cl: ClassLoader,
        matches: List<Method>,
    ): Method? {
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return null
        return matches.firstOrNull { method -> implementsInterfaceMethod(managerInterface, method) }
            ?: matches.firstOrNull { method ->
                method.name.startsWith("mo") && implementsInterfaceMethodBySignature(managerInterface, method)
            }
            ?: clazz.declaredMethods.firstOrNull { method ->
                method.name == "_" &&
                    matches.any { it.name == method.name && it.parameterTypes.contentEquals(method.parameterTypes) }
            }
    }

    private fun implementsInterfaceMethod(managerInterface: Class<*>, method: Method): Boolean {
        return managerInterface.declaredMethods.any { interfaceMethod ->
            interfaceMethod.name == method.name &&
                interfaceMethod.returnType == method.returnType &&
                interfaceMethod.parameterTypes.contentEquals(method.parameterTypes)
        }
    }

    private fun implementsInterfaceMethodBySignature(managerInterface: Class<*>, method: Method): Boolean {
        return managerInterface.declaredMethods.any { interfaceMethod ->
            interfaceMethod.returnType == method.returnType &&
                interfaceMethod.parameterTypes.contentEquals(method.parameterTypes)
        }
    }

    private fun isPrefetchManagerClass(clazz: Class<*>, cl: ClassLoader): Boolean {
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return false
        if (!managerInterface.isAssignableFrom(clazz)) return false
        val hasEnvControllerField = clazz.declaredFields.any { field ->
            field.type.name == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_ENV_CONTROLLER
        }
        if (!hasEnvControllerField) return false
        return hasManagerTagField(clazz)
    }

    private fun hasManagerTagField(clazz: Class<*>): Boolean {
        return clazz.declaredFields.any { field ->
            Modifier.isStatic(field.modifiers) &&
                field.type == String::class.java &&
                runCatching {
                    field.isAccessible = true
                    field.get(null) == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_TAG
                }.getOrDefault(false)
        }
    }
}
