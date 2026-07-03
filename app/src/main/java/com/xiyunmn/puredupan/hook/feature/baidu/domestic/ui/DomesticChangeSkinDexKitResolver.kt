package com.xiyunmn.puredupan.hook.feature.baidu.domestic.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.domestic.BaiduDomesticHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object DomesticChangeSkinDexKitResolver {
    const val CACHE_ID = "domestic_change_skin"

    private const val SKIN_MANAGER_LOAD_DESCRIPTOR =
        "Lcom/netdisk/themeskin/loader/SkinManager;->loadDefaultUpdateSkin" +
            "(Ljava/lang/String;Lcom/netdisk/themeskin/listener/SkinLoaderListener;)V"
    private const val SKIN_MANAGER_RESTORE_DESCRIPTOR =
        "Lcom/netdisk/themeskin/loader/SkinManager;->restoreDefaultTheme()V"
    private const val MIN_DEX_CANDIDATE_SCORE = 70

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
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref -> resolveRef(cl, ref) }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        if (XposedCompat.findClassOrNull(BaiduDomesticHookPoints.SKIN_LOADER_LISTENER, cl) == null) {
            XposedCompat.logW("[$TAG] SkinLoaderListener class NOT FOUND")
            return null
        }

        val memoryScanned = scanChangeSkinCandidates(cl, useMemoryDexFile = true)
            ?: return null
        val best = selectBestCandidate(cl, memoryScanned)
            ?: scanChangeSkinCandidates(cl, useMemoryDexFile = false)
                ?.let { selectBestCandidate(cl, it) }
        if (best == null) {
            XposedCompat.log("[$TAG] no candidate matched")
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        val method = best.second
        XposedCompat.log(
            "[$TAG] resolved ${method.declaringClass.name}.${method.name} " +
                "score=${score(best.first)}",
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
        val listenerClass = XposedCompat.findClassOrNull(BaiduDomesticHookPoints.SKIN_LOADER_LISTENER, cl)
            ?: return null
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
            paramTypeNames == listOf("java.lang.String", BaiduDomesticHookPoints.SKIN_LOADER_LISTENER) &&
            Modifier.isStatic(modifiers)

    private fun selectBestCandidate(
        cl: ClassLoader,
        scanned: List<DexMethodCandidate>,
    ): Pair<DexMethodCandidate, Method>? {
        return scanned.mapNotNull { methodData ->
            if (!methodData.isChangeSkinShape()) return@mapNotNull null
            if (score(methodData) < MIN_DEX_CANDIDATE_SCORE) return@mapNotNull null
            val method = resolveRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
            ) ?: return@mapNotNull null
            methodData to method
        }.sortedWith(
            compareByDescending<Pair<DexMethodCandidate, Method>> { score(it.first) }
                .thenBy { it.first.className }
                .thenBy { it.first.methodName },
        ).firstOrNull()
    }

    private fun scanChangeSkinCandidates(
        cl: ClassLoader,
        useMemoryDexFile: Boolean,
    ): List<DexMethodCandidate>? {
        return DexKitCompat.withBridge(TAG, cl, useMemoryDexFile = useMemoryDexFile, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            val candidates = bridge.findMethod(
                FindMethod.create()
                    .matcher(
                        MethodMatcher.create()
                            .modifiers(Modifier.STATIC)
                            .returnType("void")
                            .paramTypes(
                                "java.lang.String",
                                BaiduDomesticHookPoints.SKIN_LOADER_LISTENER,
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

    private fun score(methodData: DexMethodCandidate): Int {
        var score = 0
        if (SKIN_MANAGER_LOAD_DESCRIPTOR in methodData.invokeDescriptors) {
            score += 40
        }
        if (SKIN_MANAGER_RESTORE_DESCRIPTOR in methodData.invokeDescriptors) {
            score += 30
        }
        return score
    }

    private const val TAG = "DomesticChangeSkinDexKitResolver"
}
