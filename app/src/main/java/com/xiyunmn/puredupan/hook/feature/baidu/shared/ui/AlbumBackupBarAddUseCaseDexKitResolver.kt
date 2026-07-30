package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.resolver.KotlinMetadataUtils
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduAlbumBackupBarHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.AnnotationElementMatcher
import org.luckypray.dexkit.query.matchers.AnnotationEncodeArrayMatcher
import org.luckypray.dexkit.query.matchers.AnnotationMatcher
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * 定位 AlbumBackupBarAddUseCase 的真实 realExecute 方法（把备份栏 put 进 bottomBars 的入口）。
 *
 * 命中后 hook 该方法即可在数据/逻辑层短路，备份栏永不进入 bottomBars，无 View 生成。
 *
 * - 国内版 13.28.9：类名与方法名稳定未混淆，走稳定直连 fallback。
 * - 三星版（kotlin.jy2）/国际版（b8.__）：类名与 realExecute 方法名（____）均混淆，
 *   但 Kotlin @Metadata d2 数组三端都保留明文 AlbumBackupBarAddUseCase + realExecute，
 *   作为强锚点定位类，再按方法形状取真实（非 bridge）实现方法。
 *
 * bridge realExecute 会委托到真实方法，因此只需 hook 真实方法即可拦截。
 */
internal object AlbumBackupBarAddUseCaseDexKitResolver {
    const val CACHE_ID = "shared_album_backup_bar_add_use_case_v1"

    private const val TAG = "AlbumBackupBarAddUseCaseDexKitResolver"
    private const val KOTLIN_METADATA = "kotlin.Metadata"
    private const val MAP_TYPE = "java.util.Map"

    private val ADD_USE_CASE_METADATA_TOKENS = listOf(
        BaiduAlbumBackupBarHookPoints.ADD_USE_CASE_METADATA_TOKEN,
        BaiduAlbumBackupBarHookPoints.REAL_EXECUTE_METADATA_TOKEN,
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return resolveStableFallback(cl)
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findClass(
                FindClass.create()
                    .matcher(addUseCaseOwnerMatcher()),
            ).flatMap { classData ->
                classData.findMethod(
                    FindMethod.create()
                        .matcher(realExecuteMatcher()),
                )
            }.map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                )
            }
        } ?: return resolveStableFallback(cl)

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isRealExecuteShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }.sortedWith(
            // 真实实现方法优先于 bridge/synthetic
            compareByDescending<Pair<DexMethodCandidate, Method>> {
                if (it.first.isBridgeOrSynthetic()) 0 else 1
            }.thenBy { it.first.methodName },
        )

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            DexKitCompat.markTargetScanMiss(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return resolveStableFallback(cl)
        }

        val method = best.second
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        XposedCompat.log(
            "[$TAG] resolved AlbumBackupBarAddUseCase.realExecute: " +
                "${method.declaringClass.name}.${method.name}",
        )
        return method
    }

    private fun resolveStableFallback(cl: ClassLoader): Method? {
        val clazz = XposedCompat.findClassOrNull(
            BaiduAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_ADD_USE_CASE,
            cl,
        ) ?: return null
        if (!isAddUseCaseOwner(clazz)) return null
        val method = findRealExecuteMethod(clazz) ?: return null
        DexKitCompat.markTargetSuccess(
            TAG,
            CACHE_ID,
            "fallback:${method.declaringClass.name}.${method.name}",
        )
        return method
    }

    private fun validateCandidate(
        cl: ClassLoader,
        candidate: DexMethodCandidate,
        rejected: MutableList<String>,
    ): Method? {
        val method = validateRef(
            cl,
            DexKitCompat.MethodRef(candidate.className, candidate.methodName),
        )
        if (method == null) {
            rejected += "${candidate.memberName()} rejected: metadata/signature mismatch"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isAddUseCaseOwner(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isRealExecuteMethod(method)
        }?.apply { isAccessible = true }
    }

    /** 优先返回真实实现方法（非 bridge/synthetic），bridge 会委托到它。 */
    private fun findRealExecuteMethod(clazz: Class<*>): Method? {
        val shaped = clazz.declaredMethods.filter { isRealExecuteMethod(it) }
        if (shaped.isEmpty()) return null
        return (
            shaped.firstOrNull { !it.isBridge && !it.isSynthetic }
                ?: shaped.first()
            ).apply { isAccessible = true }
    }

    private fun isAddUseCaseOwner(clazz: Class<*>): Boolean {
        if (clazz.name == BaiduAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_ADD_USE_CASE) return true
        return KotlinMetadataUtils.metadataContainsAllOrAbsent(clazz, ADD_USE_CASE_METADATA_TOKENS)
    }

    private fun addUseCaseOwnerMatcher(): ClassMatcher {
        return ClassMatcher.create()
            .addAnnotation(
                AnnotationMatcher.create()
                    .type(KOTLIN_METADATA)
                    .addElement(
                        AnnotationElementMatcher.create()
                            .name("d2")
                            .arrayValue(
                                AnnotationEncodeArrayMatcher.create().apply {
                                    ADD_USE_CASE_METADATA_TOKENS.forEach(::addString)
                                },
                            ),
                    ),
            )
            .addMethod(realExecuteMatcher())
    }

    private fun realExecuteMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .returnType(Boolean::class.javaPrimitiveType!!)
            .paramTypes(BaiduAlbumBackupBarHookPoints.FILE_LIST_VIEW_MODEL, MAP_TYPE)
    }

    private fun DexMethodCandidate.isRealExecuteShape(): Boolean =
        !isConstructor &&
            returnTypeName == "boolean" &&
            paramTypeNames.size == 2 &&
            paramTypeNames[0] == BaiduAlbumBackupBarHookPoints.FILE_LIST_VIEW_MODEL &&
            paramTypeNames[1] == MAP_TYPE

    private fun DexMethodCandidate.isBridgeOrSynthetic(): Boolean =
        (modifiers and 0x40) != 0 || (modifiers and 0x1000) != 0

    private fun isRealExecuteMethod(method: Method): Boolean {
        if (method.returnType != Boolean::class.javaPrimitiveType) return false
        val params = method.parameterTypes
        if (params.size != 2) return false
        if (params[0].name != BaiduAlbumBackupBarHookPoints.FILE_LIST_VIEW_MODEL) return false
        return params[1] == java.util.Map::class.java
    }

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(5)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(5)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(5).joinToString("\n").ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }
}
