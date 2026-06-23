package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlChangeSkinDexKitResolver {
    const val CACHE_ID = "intl_change_skin"

    private val classMetadataTokens = listOf(
        "changeSkin",
        "skinName",
        "listener",
        "SkinLoaderListener",
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    )

    fun resolve(cl: ClassLoader): Method? {
        if (!HookSettings.isExperimentalDexKitEnabled) {
            XposedCompat.logD("[IntlChangeSkinDexKitResolver] skipped: config disabled")
            return null
        }

        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            resolveRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val listenerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl)
            ?: run {
                XposedCompat.logW("[IntlChangeSkinDexKitResolver] SkinLoaderListener class NOT FOUND")
                return null
            }

        val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .modifiers(Modifier.STATIC)
                                .returnType(Void.TYPE)
                                .paramTypes(String::class.java, listenerClass),
                        ),
                ).map { methodData ->
                    DexMethodCandidate(
                        className = methodData.className,
                        methodName = methodData.name,
                        returnTypeName = methodData.returnTypeName,
                        paramTypeNames = methodData.paramTypeNames,
                        isConstructor = methodData.isConstructor,
                        modifiers = methodData.modifiers,
                    )
                }
        } ?: return null

        val candidates = scanned.mapNotNull { methodData ->
            if (!methodData.isChangeSkinShape()) return@mapNotNull null
            val method = resolveRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
            ) ?: return@mapNotNull null
            methodData to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> { metadataScore(cl, it.first.className) }
                .thenBy { it.first.className }
                .thenBy { it.first.methodName },
        )

        val best = candidates.firstOrNull()
        if (best == null) {
            XposedCompat.log("[IntlChangeSkinDexKitResolver] no candidate matched")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        val method = best.second
        XposedCompat.log(
            "[$TAG] resolved ${method.declaringClass.name}.${method.name} " +
                "score=${metadataScore(cl, method.declaringClass.name)}",
        )
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    private fun resolveRef(
        cl: ClassLoader,
        ref: DexKitCompat.MethodRef,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!metadataContainsAll(clazz, classMetadataTokens)) return null
        val listenerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl)
            ?: return null
        if (!hasSkinLoaderDelegate(clazz, listenerClass)) return null
        val method = XposedCompat.findMethodOrNull(
            clazz,
            ref.methodName,
            String::class.java,
            listenerClass,
        ) ?: return null
        if (!Modifier.isStatic(method.modifiers)) return null
        if (method.returnType != Void.TYPE) return null
        return method
    }

    private fun DexMethodCandidate.isChangeSkinShape(): Boolean =
        !isConstructor &&
            returnTypeName == "void" &&
            paramTypeNames == listOf("java.lang.String", BaiduIntlHookPoints.SKIN_LOADER_LISTENER) &&
            Modifier.isStatic(modifiers)

    private fun metadataContainsAll(clazz: Class<*>, tokens: Collection<String>): Boolean {
        val metadataTokens = metadataTokens(clazz)
        return tokens.all { token ->
            metadataTokens.any { it == token || it.contains(token) }
        }
    }

    private fun metadataScore(cl: ClassLoader, className: String): Int {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: return 0
        val listenerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl)
        val metadataTokens = metadataTokens(clazz)
        var score = classMetadataTokens.count { token ->
            metadataTokens.any { it == token || it.contains(token) }
        }
        if (listenerClass != null && hasSkinLoaderDelegate(clazz, listenerClass)) {
            score += 10
        }
        return score
    }

    private fun hasSkinLoaderDelegate(clazz: Class<*>, listenerClass: Class<*>): Boolean {
        return clazz.declaredClasses.any { nested ->
            listenerClass.isAssignableFrom(nested)
        }
    }

    private fun metadataTokens(clazz: Class<*>): Set<String> {
        val metadata = clazz.declaredAnnotations.firstOrNull {
            it.annotationClass.java.name == "kotlin.Metadata"
        } ?: return emptySet()
        val d2 = runCatching {
            metadata.annotationClass.java.getDeclaredMethod("d2").invoke(metadata) as? Array<*>
        }.getOrNull() ?: return emptySet()
        return d2.filterIsInstance<String>().toSet()
    }

    private const val TAG = "IntlChangeSkinDexKitResolver"
}
