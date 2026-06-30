package com.xiyunmn.puredupan.hook.feature.baidu.domestic.performance

import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticDexKitHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object DomesticSwanPreloadResolver {
    const val PREFETCH_EVENT_CACHE_ID = "domestic_swan_prefetch_event"

    private const val TAG = "DomesticSwanPreloadResolver"

    fun warmUpPrefetchEventCache(cl: ClassLoader): Boolean {
        return resolvePrefetchEventMethod(cl) != null
    }

    fun resolvePrefetchEventMethod(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, PREFETCH_EVENT_CACHE_ID) { ref ->
            validatePrefetchEventRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val clazz = resolvePrefetchManagerClass(cl) ?: return null
        val prefetchEventClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return null

        if (!isPrefetchManagerClass(clazz, cl)) {
            DexKitCompat.markTargetError(TAG, PREFETCH_EVENT_CACHE_ID, "prefetch manager validation failed")
            DexKitCompat.putCachedMethod(TAG, PREFETCH_EVENT_CACHE_ID, null)
            return null
        }

        val matches = clazz.declaredMethods.filter { method ->
            method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == prefetchEventClass
        }
        val method = selectPrefetchEventEntryMethod(clazz, cl, matches)
        if (method == null) {
            DexKitCompat.markTargetError(
                TAG,
                PREFETCH_EVENT_CACHE_ID,
                "prefetch event entry method unresolved, matchCount=${matches.size}, " +
                    "methods=${matches.joinToString { it.name }}",
            )
            DexKitCompat.putCachedMethod(TAG, PREFETCH_EVENT_CACHE_ID, null)
            return null
        }

        method.isAccessible = true
        DexKitCompat.putCachedMethod(
            TAG,
            PREFETCH_EVENT_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    fun resolveClientPuppetPreloadFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_CLIENT_PUPPET,
            cl,
        ) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == "v0" &&
                method.returnType == clazz &&
                method.parameterTypes.size == 2 &&
                Context::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                method.parameterTypes[1] == Bundle::class.java
        }?.apply { isAccessible = true }
    }

    private fun validatePrefetchEventRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val prefetchEventClass = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_EVENT,
            cl,
        ) ?: return null
        if (!isPrefetchManagerClass(clazz, cl)) return null
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                method.returnType == Void.TYPE &&
                !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == prefetchEventClass &&
                implementsInterfaceMethod(managerInterface, method)
        }?.apply { isAccessible = true }
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

    private fun resolvePrefetchManagerClass(cl: ClassLoader): Class<*>? {
        return prefetchManagerClassNames()
            .asSequence()
            .mapNotNull { className -> XposedCompat.findClassOrNull(className, cl) }
            .firstOrNull { clazz -> isPrefetchManagerClass(clazz, cl) }
    }

    private fun prefetchManagerClassNames(): List<String> =
        listOf(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER,
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_JADX_NAME,
        )

    private fun isPrefetchManagerClass(clazz: Class<*>, cl: ClassLoader): Boolean {
        if (clazz.name !in prefetchManagerClassNames()) return false
        val managerInterface = XposedCompat.findClassOrNull(
            BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_INTERFACE,
            cl,
        ) ?: return false
        if (!managerInterface.isAssignableFrom(clazz)) return false
        val hasEnvControllerField = clazz.declaredFields.any { field ->
            field.type.name == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_ENV_CONTROLLER
        }
        if (!hasEnvControllerField) return false
        return runCatching {
            clazz.getDeclaredField("____").apply { isAccessible = true }
                .get(null) == BaiduDomesticDexKitHookPoints.SWAN_PREFETCH_MANAGER_TAG
        }.getOrDefault(false)
    }
}
