package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlChangeSkinDexKitResolver {
    const val CACHE_ID = "intl_change_skin"

    private const val SKIN_MANAGER_LOAD_DESCRIPTOR =
        "Lcom/netdisk/themeskin/loader/SkinManager;->loadDefaultUpdateSkin" +
            "(Ljava/lang/String;Lcom/netdisk/themeskin/listener/SkinLoaderListener;)V"
    private const val SKIN_MANAGER_RESTORE_DESCRIPTOR =
        "Lcom/netdisk/themeskin/loader/SkinManager;->restoreDefaultTheme()V"
    private const val MIN_REFLECTIVE_CLASS_SCORE = 6
    private const val MIN_DEX_CANDIDATE_SCORE = 12

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
        val invokeDescriptors: Set<String>,
    )

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            resolveRef(cl, ref, requireClassScore = true)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        if (XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl) == null) {
            XposedCompat.logW("[IntlChangeSkinDexKitResolver] SkinLoaderListener class NOT FOUND")
            return null
        }

        val memoryScanned = scanChangeSkinCandidates(cl, useMemoryDexFile = true)
            ?: return null
        val best = selectBestCandidate(cl, memoryScanned)
            ?: scanChangeSkinCandidates(cl, useMemoryDexFile = false)
                ?.let { selectBestCandidate(cl, it) }
        if (best == null) {
            XposedCompat.log("[IntlChangeSkinDexKitResolver] no candidate matched")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        val method = best.second
        XposedCompat.log(
            "[$TAG] resolved ${method.declaringClass.name}.${method.name} " +
                "score=${score(cl, best.first)}",
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
        requireClassScore: Boolean,
    ): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        val listenerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl)
            ?: return null
        if (requireClassScore && reflectiveClassScore(clazz, listenerClass) < MIN_REFLECTIVE_CLASS_SCORE) {
            return null
        }
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

    private fun selectBestCandidate(
        cl: ClassLoader,
        scanned: List<DexMethodCandidate>,
    ): Pair<DexMethodCandidate, Method>? {
        return scanned.mapNotNull { methodData ->
            if (!methodData.isChangeSkinShape()) return@mapNotNull null
            val method = resolveRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
                requireClassScore = false,
            ) ?: return@mapNotNull null
            if (score(cl, methodData) < MIN_DEX_CANDIDATE_SCORE) {
                return@mapNotNull null
            }
            methodData to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> { score(cl, it.first) }
                .thenBy { it.first.className }
                .thenBy { it.first.methodName },
        ).firstOrNull()
    }

    private fun scanChangeSkinCandidates(
        cl: ClassLoader,
        useMemoryDexFile: Boolean,
    ): List<DexMethodCandidate>? {
        return DexKitCompat.withBridge(TAG, cl, useMemoryDexFile = useMemoryDexFile) { bridge ->
            bridge.setThreadNum(1)
            val candidates = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .modifiers(Modifier.STATIC)
                            .returnType("void")
                            .paramTypes(
                                "java.lang.String",
                                BaiduIntlHookPoints.SKIN_LOADER_LISTENER,
                            ),
                    ),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    invokeDescriptors = runCatching {
                        methodData.invokes.map { it.descriptor }.toSet()
                    }.getOrDefault(emptySet()),
                )
            }
            XposedCompat.logD(
                "[$TAG] DexKit scan candidates=${candidates.size}, useMemoryDexFile=$useMemoryDexFile",
            )
            candidates
        }
    }

    private fun score(cl: ClassLoader, methodData: DexMethodCandidate): Int {
        val clazz = XposedCompat.findClassOrNull(methodData.className, cl) ?: return 0
        val listenerClass = XposedCompat.findClassOrNull(BaiduIntlHookPoints.SKIN_LOADER_LISTENER, cl)
        var score = listenerClass?.let { reflectiveClassScore(clazz, it) } ?: 0
        if (SKIN_MANAGER_LOAD_DESCRIPTOR in methodData.invokeDescriptors) {
            score += 40
        }
        if (SKIN_MANAGER_RESTORE_DESCRIPTOR in methodData.invokeDescriptors) {
            score += 30
        }
        return score
    }

    private fun reflectiveClassScore(clazz: Class<*>, listenerClass: Class<*>): Int {
        val metadataTokens = metadataTokens(clazz)
        var score = classMetadataTokens.sumOf { token ->
            if (metadataTokens.any { it.equals(token, ignoreCase = true) || it.contains(token, ignoreCase = true) }) {
                2
            } else {
                0
            }
        }
        if (hasSkinLoaderDelegate(clazz, listenerClass)) {
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
