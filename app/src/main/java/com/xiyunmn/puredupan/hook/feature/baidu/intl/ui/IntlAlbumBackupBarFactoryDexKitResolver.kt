package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlAlbumBackupBarHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.ClassMatcher
import org.luckypray.dexkit.query.matchers.MethodMatcher

/**
 * 定位国际版备份栏「渲染入口」——浮动栏工厂里实例化 AlbumBackupBarView 的那个方法。
 *
 * 国际版 13.11.9 R8 全局剥离 @Metadata，数据层 AddUseCase（`w7.__`，形状
 * `boolean(IFileListViewModel,Map)`）与全 APK 103 个同形状候选撞形、无字符串锚点，
 * 静态无法唯一定位。改走渲染入口：
 *
 * 备份栏 View（[BaiduIntlAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_VIEW]，`@Tag("AlbumBackupBarView")`
 * 明文类）全 APK 唯一实例化点是浮动栏工厂 `w7.h`（实现明文接口 [FLOATING_BAR_FACTORY_INTERFACE]）
 * 的 `"albumbackup_bar"` 分支——`new AlbumBackupBarView(activity, ...)`。
 *
 * 锚点组合（两者皆是 R8 不剥离的结构信息）：
 * 1. 方法 declaring class **实现 IFloatingBarFactory**（接口关系运行时必需，不剥离）；
 * 2. 方法体 **invoke AlbumBackupBarView.\<init\>**（明文类构造器）。
 *
 * intl 全 APK 唯一命中 `w7.h` 的工厂方法。命中后 hook 该方法，afterHook 中
 * `result is AlbumBackupBarView → 返回 null`，精确只拦截备份栏，同工厂的
 * garbage / probationary / select_size 栏不受影响。返回 null 被 FloatingBarManager +
 * 消费端完美接住（仅打 log，不 addView），无副作用、无 View 树扫描。
 *
 * 无明文 fallback：工厂类名（`w7.h`）与方法名（`mo9446_`）均混淆，唯一稳定锚点即
 * 上述两条结构 + DexKit。国内/三星保留 @Metadata，仍走共享数据层 AddUseCase 路径，
 * 本入口仅国际版启用。
 */
internal object IntlAlbumBackupBarFactoryDexKitResolver {
    const val CACHE_ID = "intl_album_backup_bar_factory_v1"

    private const val TAG = "IntlAlbumBackupBarFactoryDexKitResolver"
    private const val MAX_DIAGNOSTIC_CANDIDATES = 5

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
        val invokeDescriptors: Set<String>,
    ) {
        fun memberName(): String = "$className.$methodName"
    }

    fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolve(cl) != null
    }

    /** 返回浮动栏工厂里实例化 AlbumBackupBarView 的方法，供 hook 挂渲染入口。 */
    fun resolve(cl: ClassLoader): Method? {
        when (val cached = DexKitCompat.getCachedMethod(TAG, CACHE_ID) { ref ->
            validateRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val candidates = DexKitCompat.withBridge(TAG, cl, resolverId = CACHE_ID) { bridge ->
            bridge.setThreadNum(1)
            bridge.findMethod(
                FindMethod.create()
                    .matcher(factoryMethodMatcher()),
            ).map { methodData ->
                DexMethodCandidate(
                    className = methodData.className,
                    methodName = methodData.name,
                    returnTypeName = methodData.returnTypeName,
                    paramTypeNames = methodData.paramTypeNames,
                    isConstructor = methodData.isConstructor,
                    modifiers = methodData.modifiers,
                    invokeDescriptors = methodData.invokes.map { it.descriptor }.toSet(),
                )
            }
        } ?: return null

        val rejected = mutableListOf<String>()
        val matches = candidates.mapNotNull { candidate ->
            if (!candidate.isFactoryMethodShape()) return@mapNotNull null
            val method = validateCandidate(cl, candidate, rejected) ?: return@mapNotNull null
            candidate to method
        }

        val best = matches.firstOrNull()
        if (best == null) {
            val diagnostic = buildDiagnostic(candidates, matches, rejected)
            XposedCompat.logW("[$TAG] album backup bar factory method unresolved: $diagnostic")
            DexKitCompat.markTargetError(TAG, CACHE_ID, diagnostic)
            DexKitCompat.putCachedMethod(TAG, CACHE_ID, null)
            return null
        }

        val method = best.second
        DexKitCompat.putCachedMethod(
            TAG,
            CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        XposedCompat.log(
            "[$TAG] resolved album backup bar factory method: " +
                "${method.declaringClass.name}.${method.name}",
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
            rejected += "${candidate.memberName()} rejected: not a floating bar factory method"
        }
        return method
    }

    private fun validateRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!implementsFloatingBarFactory(clazz, cl)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName && isFactoryMethodShape(method)
        }?.apply { isAccessible = true }
    }

    /** 工厂方法：非静态、非构造、返回引用类型（创建出的 View）、带参数。 */
    private fun isFactoryMethodShape(method: Method): Boolean {
        if (Modifier.isStatic(method.modifiers)) return false
        if (method.returnType.isPrimitive || method.returnType == Void.TYPE) return false
        return method.parameterTypes.isNotEmpty()
    }

    private fun DexMethodCandidate.isFactoryMethodShape(): Boolean =
        !isConstructor &&
            !Modifier.isStatic(modifiers) &&
            returnTypeName != "void" &&
            !isPrimitiveReturn(returnTypeName) &&
            paramTypeNames.isNotEmpty()

    private fun isPrimitiveReturn(name: String): Boolean =
        name in PRIMITIVE_RETURN_NAMES

    private fun implementsFloatingBarFactory(clazz: Class<*>, cl: ClassLoader): Boolean {
        val iface = XposedCompat.findClassOrNull(
            BaiduIntlAlbumBackupBarHookPoints.FLOATING_BAR_FACTORY_INTERFACE,
            cl,
        ) ?: return false
        return iface.isAssignableFrom(clazz)
    }

    private fun factoryMethodMatcher(): MethodMatcher {
        return MethodMatcher.create()
            .declaredClass(
                ClassMatcher.create()
                    .addInterface(BaiduIntlAlbumBackupBarHookPoints.FLOATING_BAR_FACTORY_INTERFACE),
            )
            .addInvoke(
                MethodMatcher.create()
                    .declaredClass(BaiduIntlAlbumBackupBarHookPoints.ALBUM_BACKUP_BAR_VIEW)
                    .name(BaiduIntlAlbumBackupBarHookPoints.CONSTRUCTOR_METHOD_NAME),
            )
    }

    private fun buildDiagnostic(
        candidates: List<DexMethodCandidate>,
        matches: List<Pair<DexMethodCandidate, Method>>,
        rejected: List<String>,
    ): String {
        val topCandidates = candidates.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { candidate ->
                "${candidate.memberName()} ${candidate.returnTypeName}(${candidate.paramTypeNames.joinToString()})"
            }
            .ifBlank { "-" }
        val topMatches = matches.take(MAX_DIAGNOSTIC_CANDIDATES)
            .joinToString("\n") { (candidate, method) ->
                "${candidate.memberName()} -> ${method.declaringClass.name}.${method.name}"
            }
            .ifBlank { "-" }
        val rejectedText = rejected.take(MAX_DIAGNOSTIC_CANDIDATES).joinToString("\n").ifBlank { "-" }
        return buildString {
            append("candidateCount=").append(candidates.size).append('\n')
            append("matchCount=").append(matches.size).append('\n')
            append("topCandidates=\n").append(topCandidates).append('\n')
            append("topMatches=\n").append(topMatches).append('\n')
            append("rejected=\n").append(rejectedText)
        }
    }

    private val PRIMITIVE_RETURN_NAMES = setOf(
        "boolean", "byte", "char", "short", "int", "long", "float", "double",
    )
}
