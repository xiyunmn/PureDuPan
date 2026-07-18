package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduHomeCardHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.List as JavaList
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData

internal object HomeRecentItemLimitDexKitResolver {
    val sourceCacheIds = (0..2).map { index -> "shared_home_recent_limit_source_v2_$index" }
    val cacheWriterCacheIds = (0..2).map { index -> "shared_home_recent_limit_cache_writer_v2_$index" }
    val stateWriterCacheIds = (0..2).map { index -> "shared_home_recent_limit_state_writer_v2_$index" }
    val cacheIds = sourceCacheIds + cacheWriterCacheIds + stateWriterCacheIds

    private const val TAG = "HomeRecentItemLimitDexKitResolver"
    private const val SOURCE_LOG_ANCHOR =
        "NewHomeRecentCardViewModel onRequestSuccess 更新数据 all data = "

    data class ResolvedMethods(
        val source: Method,
        val cacheWriter: Method,
        val stateWriter: Method,
    )

    private data class MethodRefs(
        val source: DexKitCompat.MethodRef,
        val cacheWriter: DexKitCompat.MethodRef,
        val stateWriter: DexKitCompat.MethodRef,
    )

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl).isNotEmpty()
    }

    fun resolve(cl: ClassLoader): List<ResolvedMethods> {
        val cached = readCached(cl)
        if (cached.isNotEmpty()) return cached

        val refs = scan(cl)
        refs.forEachIndexed(::cacheRefs)
        return readCached(cl).ifEmpty { resolveStableFallback(cl) }
    }

    private fun readCached(cl: ClassLoader): List<ResolvedMethods> {
        return sourceCacheIds.indices.mapNotNull { index ->
            val source = cachedMethod(cl, sourceCacheIds[index]) ?: return@mapNotNull null
            val cacheWriter = cachedMethod(cl, cacheWriterCacheIds[index]) ?: return@mapNotNull null
            val stateWriter = cachedMethod(cl, stateWriterCacheIds[index]) ?: return@mapNotNull null
            if (
                source.declaringClass != cacheWriter.declaringClass ||
                source.declaringClass != stateWriter.declaringClass
            ) {
                return@mapNotNull null
            }
            ResolvedMethods(source, cacheWriter, stateWriter)
        }
    }

    private fun cachedMethod(cl: ClassLoader, id: String): Method? {
        return when (val cached = DexKitCompat.getCachedMethod(TAG, id) { ref -> validateRef(cl, ref) }) {
            is DexKitCompat.CachedResult.Found -> cached.value
            DexKitCompat.CachedResult.Miss,
            DexKitCompat.CachedResult.NotFound -> null
        }
    }

    private fun scan(cl: ClassLoader): List<MethodRefs> {
        val refs = DexKitCompat.withBridge(TAG, resolverId = sourceCacheIds.first(), cl = cl) {
            bridge ->
            bridge.setThreadNum(1)
            val sources = BaiduHomeCardHookPoints.DOMESTIC_RECENT_CARD_VIEW_MODELS
                .plus(BaiduHomeCardHookPoints.INTL_RECENT_CARD_VIEW_MODELS)
                .distinct()
                .flatMap { className ->
                    bridge.findMethod(
                        FindMethod.create().matcher(
                            MethodMatcher.create()
                                .declaredClass(className)
                                .usingStrings(SOURCE_LOG_ANCHOR),
                        ),
                    )
                }
            sources.mapNotNull(::classifySource)
                .distinctBy { refs -> refs.source.className }
                .sortedBy { refs -> refs.source.className }
                .take(sourceCacheIds.size)
        }.orEmpty()
        if (refs.isEmpty()) {
            XposedCompat.logW("[$TAG] recent item limit methods unresolved")
        }
        return refs
    }

    private fun classifySource(source: MethodData): MethodRefs? {
        if (!isListVoidInstanceMethod(source)) return null
        val consumers = source.invokes.filter { invoked ->
            invoked.className == source.className && isListVoidInstanceMethod(invoked)
        }
        val cacheWriter = consumers.firstOrNull(::isCacheWriter) ?: return null
        val stateWriter = consumers.firstOrNull(::isStateWriter) ?: return null
        return MethodRefs(
            source = source.toRef(),
            cacheWriter = cacheWriter.toRef(),
            stateWriter = stateWriter.toRef(),
        )
    }

    private fun isCacheWriter(method: MethodData): Boolean {
        return method.invokes.any { invoked ->
            invoked.className == "androidx.lifecycle.ViewModelKt" ||
                invoked.className.startsWith("kotlinx.coroutines.BuildersKt")
        }
    }

    private fun isStateWriter(method: MethodData): Boolean {
        return method.invokes.any { invoked ->
            invoked.className.contains("MutableStateFlow") && invoked.name == "compareAndSet"
        }
    }

    private fun isListVoidInstanceMethod(method: MethodData): Boolean {
        return method.isMethod &&
            !Modifier.isStatic(method.modifiers) &&
            method.returnTypeName == "void" &&
            method.paramTypeNames == listOf(JavaList::class.java.name)
    }

    private fun MethodData.toRef(): DexKitCompat.MethodRef {
        return DexKitCompat.MethodRef(className, name)
    }

    private fun cacheRefs(index: Int, refs: MethodRefs) {
        if (index !in sourceCacheIds.indices) return
        DexKitCompat.putCachedMethod(TAG, sourceCacheIds[index], refs.source)
        DexKitCompat.putCachedMethod(TAG, cacheWriterCacheIds[index], refs.cacheWriter)
        DexKitCompat.putCachedMethod(TAG, stateWriterCacheIds[index], refs.stateWriter)
    }

    private fun resolveStableFallback(cl: ClassLoader): List<ResolvedMethods> {
        val classNames = BaiduHomeCardHookPoints.DOMESTIC_RECENT_CARD_VIEW_MODELS
            .plus(BaiduHomeCardHookPoints.INTL_RECENT_CARD_VIEW_MODELS)
            .distinct()
        return classNames.mapNotNull { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@mapNotNull null
            val source = findNamedListMethod(clazz, "onRequestSuccess") ?: return@mapNotNull null
            val cacheWriter = findNamedListMethod(clazz, "saveByPersonConfig") ?: return@mapNotNull null
            val stateWriter = findNamedListMethod(clazz, "updateNewStateByData") ?: return@mapNotNull null
            ResolvedMethods(source, cacheWriter, stateWriter)
        }.also { methods ->
            methods.forEachIndexed { index, resolved ->
                cacheRefs(
                    index,
                    MethodRefs(
                        resolved.source.toRef(),
                        resolved.cacheWriter.toRef(),
                        resolved.stateWriter.toRef(),
                    ),
                )
            }
        }
    }

    private fun findNamedListMethod(clazz: Class<*>, name: String): Method? {
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == name && isListVoidInstanceMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isListVoidInstanceMethod(method)
        }?.apply { isAccessible = true }
    }

    private fun isListVoidInstanceMethod(method: Method): Boolean {
        return !Modifier.isStatic(method.modifiers) &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            JavaList::class.java.isAssignableFrom(method.parameterTypes[0])
    }

    private fun Method.toRef(): DexKitCompat.MethodRef {
        return DexKitCompat.MethodRef(declaringClass.name, name)
    }

}
