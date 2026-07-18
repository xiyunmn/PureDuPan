package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData

internal object HomeRecentScrollRangeDexKitResolver {
    const val CALLBACK_CACHE_ID = "shared_home_recent_scroll_callback_v1"
    const val UPDATE_SIZE_CACHE_ID = "shared_home_recent_scroll_update_size_v1"
    val cacheIds = listOf(CALLBACK_CACHE_ID, UPDATE_SIZE_CACHE_ID)

    private const val TAG = "HomeRecentScrollRangeDexKitResolver"
    private const val TOP_SIZE_LOG_ANCHOR = "onTopSizeChanged   height = "

    data class ResolvedMethods(
        val callback: Method,
        val updateSize: Method,
    )

    private data class MethodRefs(
        val callback: DexKitCompat.MethodRef,
        val updateSize: DexKitCompat.MethodRef,
    )

    fun warmUpDexKitCache(cl: ClassLoader): Boolean = resolve(cl) != null

    fun resolve(cl: ClassLoader): ResolvedMethods? {
        readCached(cl)?.let { return it }

        val refs = scan(cl)
        if (refs != null) {
            cacheRefs(refs)
            XposedCompat.log(
                "[$TAG] resolved recent scroll range methods: " +
                    "${refs.callback.className}.${refs.callback.methodName}, " +
                    "${refs.updateSize.className}.${refs.updateSize.methodName}",
            )
            readCached(cl)?.let { return it }
        }
        return resolveStableFallback(cl)
    }

    private fun readCached(cl: ClassLoader): ResolvedMethods? {
        val callback = cachedMethod(cl, CALLBACK_CACHE_ID, ::validateCallbackRef) ?: return null
        val updateSize = cachedMethod(cl, UPDATE_SIZE_CACHE_ID, ::validateUpdateSizeRef) ?: return null
        if (callback.declaringClass != updateSize.declaringClass) return null
        return ResolvedMethods(callback, updateSize)
    }

    private fun cachedMethod(
        cl: ClassLoader,
        id: String,
        validator: (ClassLoader, DexKitCompat.MethodRef) -> Method?,
    ): Method? {
        return when (val cached = DexKitCompat.getCachedMethod(TAG, id) { ref -> validator(cl, ref) }) {
            is DexKitCompat.CachedResult.Found -> cached.value
            DexKitCompat.CachedResult.Miss,
            DexKitCompat.CachedResult.NotFound -> null
        }
    }

    private fun scan(cl: ClassLoader): MethodRefs? {
        val refs = DexKitCompat.withBridge(TAG, resolverId = CALLBACK_CACHE_ID, cl = cl) { bridge ->
            bridge.setThreadNum(1)
            val updateMethods = bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create()
                        .declaredClass(BaiduHomeCardHookPoints.HOME_FRAGMENT)
                        .usingStrings(TOP_SIZE_LOG_ANCHOR),
                ),
            ).filter(::isUpdateSizeMethod)
            val callbacks = bridge.findMethod(
                FindMethod.create().matcher(
                    MethodMatcher.create()
                        .declaredClass(BaiduHomeCardHookPoints.HOME_FRAGMENT)
                        .returnType("kotlin.Unit")
                        .paramTypes(
                            BaiduHomeCardHookPoints.HOME_FRAGMENT,
                            "int",
                            "int",
                        ),
                ),
            ).filter(::isCallbackMethod)

            updateMethods.firstNotNullOfOrNull { updateSize ->
                val callback = callbacks.firstOrNull { candidate ->
                    candidate.invokes.any { invoked ->
                        invoked.className == updateSize.className && invoked.name == updateSize.name
                    }
                } ?: return@firstNotNullOfOrNull null
                MethodRefs(callback.toRef(), updateSize.toRef())
            }
        }
        if (refs == null) {
            XposedCompat.logD("[$TAG] recent scroll range methods unresolved by DexKit")
        }
        return refs
    }

    private fun cacheRefs(refs: MethodRefs) {
        DexKitCompat.putCachedMethod(TAG, CALLBACK_CACHE_ID, refs.callback)
        DexKitCompat.putCachedMethod(TAG, UPDATE_SIZE_CACHE_ID, refs.updateSize)
    }

    private fun resolveStableFallback(cl: ClassLoader): ResolvedMethods? {
        val clazz = XposedCompat.findClassOrNull(BaiduHomeCardHookPoints.HOME_FRAGMENT, cl)
            ?: return null
        val updateSize = clazz.declaredMethods.firstOrNull { method ->
            method.name == BaiduHomeCardHookPoints.HOME_TOP_SIZE_METHOD &&
                isUpdateSizeMethod(method)
        }?.apply { isAccessible = true } ?: return null
        val callback = clazz.declaredMethods.firstOrNull { method ->
            method.name.startsWith(BaiduHomeCardHookPoints.HOME_TOP_SIZE_CALLBACK_PREFIX) &&
                isCallbackMethod(method)
        }?.apply { isAccessible = true } ?: return null
        return ResolvedMethods(callback, updateSize)
    }

    private fun validateCallbackRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isCallbackMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun validateUpdateSizeRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isUpdateSizeMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isUpdateSizeMethod(method: MethodData): Boolean {
        return method.isMethod &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnTypeName == "void" &&
            method.paramTypeNames == listOf("int")
    }

    private fun isCallbackMethod(method: MethodData): Boolean {
        return method.isMethod &&
            Modifier.isStatic(method.modifiers) &&
            method.returnTypeName == "kotlin.Unit" &&
            method.paramTypeNames == listOf(
                BaiduHomeCardHookPoints.HOME_FRAGMENT,
                "int",
                "int",
            )
    }

    private fun isUpdateSizeMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            method.parameterTypes[0] == Int::class.javaPrimitiveType
    }

    private fun isCallbackMethod(method: Method): Boolean {
        return Modifier.isStatic(method.modifiers) &&
            method.returnType.name == "kotlin.Unit" &&
            method.parameterTypes.size == 3 &&
            method.parameterTypes[0].name == BaiduHomeCardHookPoints.HOME_FRAGMENT &&
            method.parameterTypes[1] == Int::class.javaPrimitiveType &&
            method.parameterTypes[2] == Int::class.javaPrimitiveType
    }

    private fun MethodData.toRef(): DexKitCompat.MethodRef =
        DexKitCompat.MethodRef(className, name)
}
