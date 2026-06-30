package com.xiyunmn.puredupan.hook.feature.baidu.intl.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.dexkit.DexKitCompat
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHookPoints
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object IntlAlbumAiInitBlockHook {
    const val DIRECT_ALBUM_AI_INIT_CACHE_ID = "intl_album_ai_direct_init"

    private const val ALBUM_CONTEXT_CLASS_NAME = BaiduIntlHookPoints.ALBUM_CONTEXT
    private const val ALBUM_COMPANION_CLASS_NAME = BaiduIntlHookPoints.ALBUM_COMPANION
    private const val ALBUM_AGGREGATE_CLASS_NAME = BaiduIntlHookPoints.ALBUM_AGGREGATE
    private const val INIT_ALBUM_SERVICE_METHOD_NAME = "initAlbumService"

    private val albumAiEntryMethodNames = setOf(
        "startAiPanelActivity",
        "startAiSearchActivity",
        "getImagePreviewNlpActivityIntent",
        "jumpAiLabActivity",
        "showShareDialog",
    )

    private val albumAiEntryActivityClassNames = BaiduIntlHookPoints.ALBUM_AI_ENTRY_ACTIVITIES

    private val albumSemanticTokens = listOf(
        "initAlbumService",
        "application",
        "startAiPanelActivity",
        "startAiSearchActivity",
        "getImagePreviewNlpActivityIntent",
        "jumpAiLabActivity",
        "showShareDialog",
        "BaiduNetDiskModules_business_album",
    )

    private data class StableInitFallback(
        val method: Method,
        val receiver: Any?,
    )

    private data class DexMethodCandidate(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val paramTypeNames: List<String>,
        val isConstructor: Boolean,
        val modifiers: Int,
    )

    private val hookState = HookState()
    private val lock = Any()

    @Volatile private var directInitMethod: Method? = null
    @Volatile private var stableInitFallback: StableInitFallback? = null
    @Volatile private var pendingApplication: Application? = null
    @Volatile private var skipped = false
    @Volatile private var restored = false
    @Volatile private var restoring = false
    @Volatile private var skipCount = 0
    @Volatile private var restoreCount = 0

    internal fun hook(cl: ClassLoader) {
        if (!isEnabled()) {
            XposedCompat.log("[IntlAlbumAiInitBlockHook] skipped: config disabled")
            return
        }
        if (!hookState.markInstalled()) return

        try {
            directInitMethod = if (HookSettings.isExperimentalDexKitEnabled) {
                resolveDirectAlbumAiInitMethod(cl)
            } else {
                XposedCompat.logD("[IntlAlbumAiInitBlockHook] direct DexKit resolve skipped: config disabled")
                null
            }

            val stableInitHooks = hookStableInitMethods(cl)
            val directHooked = directInitMethod?.let { hookDirectInitMethod(it) } == true
            if (stableInitHooks == 0 && !directHooked) {
                hookState.reset()
                XposedCompat.log("[IntlAlbumAiInitBlockHook] album AI init methods NOT FOUND")
                return
            }

            val entryMethodHooks = hookAlbumAiEntryMethods(cl)
            val entryActivityHooks = hookAlbumAiEntryActivities(cl)
            XposedCompat.log(
                "[IntlAlbumAiInitBlockHook] hooks INSTALLED: " +
                    "direct=${directInitMethod?.let { "${it.declaringClass.name}.${it.name}" } ?: "missing"}, " +
                    "stableInit=$stableInitHooks, entryMethods=$entryMethodHooks, " +
                    "entryActivities=$entryActivityHooks",
            )
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.log("[IntlAlbumAiInitBlockHook] install FAILED: ${t.message}")
            XposedCompat.log(t)
        }
    }

    internal fun warmUpDexKitCache(cl: ClassLoader): Boolean {
        return resolveStableInitFallbackMethods(cl).isNotEmpty() ||
            resolveDirectAlbumAiInitMethod(cl) != null
    }

    private fun hookStableInitMethods(cl: ClassLoader): Int {
        var installed = 0
        installed += hookNamedInitMethods(ALBUM_CONTEXT_CLASS_NAME, cl)
        installed += hookNamedInitMethods(ALBUM_COMPANION_CLASS_NAME, cl)
        installed += hookNamedInitMethods(ALBUM_AGGREGATE_CLASS_NAME, cl)
        return installed
    }

    private fun hookNamedInitMethods(className: String, cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var installed = 0
        for (method in resolveNamedInitMethods(className, cl, logMissing = true)) {
            mod.hook(method).intercept { chain ->
                val application = chain.args.firstOrNull() as? Application
                if (application == null || !shouldBlockInit()) {
                    return@intercept chain.proceed()
                }

                synchronized(lock) {
                    stableInitFallback = StableInitFallback(method, chain.thisObject)
                    pendingApplication = application
                    skipped = true
                    skipCount++
                }
                XposedCompat.log(
                    "[IntlAlbumAiInitBlockHook] blocked stable album AI init: " +
                        "${method.declaringClass.name}.${method.name}, skipCount=$skipCount",
                )
                null
            }
            installed++
        }
        return installed
    }

    private fun resolveStableInitFallbackMethods(cl: ClassLoader): List<Method> {
        return listOf(
            ALBUM_CONTEXT_CLASS_NAME,
            ALBUM_COMPANION_CLASS_NAME,
            ALBUM_AGGREGATE_CLASS_NAME,
        ).flatMap { className ->
            resolveNamedInitMethods(className, cl, logMissing = false)
        }
    }

    private fun resolveNamedInitMethods(
        className: String,
        cl: ClassLoader,
        logMissing: Boolean,
    ): List<Method> {
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            if (logMissing) {
                XposedCompat.logD("[IntlAlbumAiInitBlockHook] class NOT FOUND: $className")
            }
            return emptyList()
        }

        return clazz.declaredMethods.filter { method ->
            method.name == INIT_ALBUM_SERVICE_METHOD_NAME &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Application::class.java.isAssignableFrom(method.parameterTypes[0])
        }.onEach { method ->
            method.isAccessible = true
        }
    }

    private fun hookDirectInitMethod(method: Method): Boolean {
        val mod = XposedCompat.module ?: return false
        mod.hook(method).intercept { chain ->
            val application = chain.args.firstOrNull() as? Application
            if (application == null || !shouldBlockInit()) {
                return@intercept chain.proceed()
            }

            synchronized(lock) {
                pendingApplication = application
                skipped = true
                skipCount++
            }
            XposedCompat.log(
                "[IntlAlbumAiInitBlockHook] blocked direct album AI init: " +
                    "${method.declaringClass.name}.${method.name}, skipCount=$skipCount",
            )
            null
        }
        return true
    }

    private fun shouldBlockInit(): Boolean {
        if (!isEnabled()) return false
        if (restoring || restored) return false
        return true
    }

    private fun hookAlbumAiEntryMethods(cl: ClassLoader): Int {
        var installed = 0
        installed += hookNamedEntryMethods(ALBUM_CONTEXT_CLASS_NAME, cl)
        installed += hookNamedEntryMethods(ALBUM_COMPANION_CLASS_NAME, cl)
        installed += hookNamedEntryMethods(ALBUM_AGGREGATE_CLASS_NAME, cl)
        return installed
    }

    private fun hookNamedEntryMethods(className: String, cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val clazz = XposedCompat.findClassOrNull(className, cl) ?: run {
            XposedCompat.logD("[IntlAlbumAiInitBlockHook] entry class NOT FOUND: $className")
            return 0
        }

        var installed = 0
        for (method in clazz.declaredMethods) {
            if (method.name !in albumAiEntryMethodNames) continue
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val fallbackApplication = findApplicationFromArgs(chain.args)
                restoreIfPending("album_entry:${method.name}/${method.parameterTypes.size}", fallbackApplication)
                chain.proceed()
            }
            installed++
        }
        return installed
    }

    private fun hookAlbumAiEntryActivities(cl: ClassLoader): Int {
        var installed = 0
        for (className in albumAiEntryActivityClassNames) {
            val activityClass = XposedCompat.findClassOrNull(className, cl) ?: continue
            if (hookActivityOnCreate(activityClass, "activity:onCreate:$className")) installed++
            if (hookActivityOnResume(activityClass, "activity:onResume:$className")) installed++
        }
        return installed
    }

    private fun hookActivityOnCreate(activityClass: Class<*>, reason: String): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onCreate", Bundle::class.java) ?: return false
        mod.hook(method).intercept { chain ->
            val activity = chain.thisObject as? Activity
            restoreIfPending(reason, activity?.application)
            chain.proceed()
        }
        return true
    }

    private fun hookActivityOnResume(activityClass: Class<*>, reason: String): Boolean {
        val mod = XposedCompat.module ?: return false
        val method = XposedCompat.findMethodOrNull(activityClass, "onResume") ?: return false
        mod.hook(method).intercept { chain ->
            val activity = chain.thisObject as? Activity
            restoreIfPending(reason, activity?.application)
            chain.proceed()
        }
        return true
    }

    private fun restoreIfPending(reason: String, fallbackApplication: Application?) {
        if (!isEnabled()) return
        val application = synchronized(lock) {
            if (!skipped || restored) return
            val pending = pendingApplication ?: fallbackApplication ?: return
            restored = true
            restoreCount++
            pending
        }

        try {
            restoring = true
            val direct = directInitMethod
            if (direct != null) {
                direct.invoke(null, application)
                XposedCompat.log(
                    "[IntlAlbumAiInitBlockHook] restored direct album AI init: " +
                        "reason=$reason, restoreCount=$restoreCount",
                )
                return
            }

            val fallback = stableInitFallback ?: run {
                XposedCompat.logW("[IntlAlbumAiInitBlockHook] restore skipped: init method missing, reason=$reason")
                return
            }
            fallback.method.invoke(fallback.receiver, application)
            XposedCompat.log(
                "[IntlAlbumAiInitBlockHook] restored stable album AI init: " +
                    "reason=$reason, restoreCount=$restoreCount",
            )
        } catch (t: Throwable) {
            synchronized(lock) {
                restored = false
                restoreCount--
            }
            XposedCompat.logW(
                "[IntlAlbumAiInitBlockHook] restore FAILED: reason=$reason, msg=${t.message}",
            )
            XposedCompat.log(t)
        } finally {
            restoring = false
        }
    }

    private fun findApplicationFromArgs(args: Iterable<Any?>): Application? {
        for (arg in args) {
            when (arg) {
                is Application -> return arg
                is Activity -> return arg.application
                is Context -> {
                    val appContext = arg.applicationContext
                    if (appContext is Application) return appContext
                }
            }
        }
        return null
    }

    private fun resolveDirectAlbumAiInitMethod(cl: ClassLoader): Method? {
        if (!HookSettings.isExperimentalDexKitEnabled) {
            XposedCompat.logD("[IntlAlbumAiInitBlockHook] direct DexKit resolve skipped: config disabled")
            return null
        }

        when (val cached = DexKitCompat.getCachedMethod(TAG, DIRECT_ALBUM_AI_INIT_CACHE_ID) { ref ->
            resolveDirectAlbumAiInitRef(cl, ref)
        }) {
            is DexKitCompat.CachedResult.Found -> return cached.value
            DexKitCompat.CachedResult.NotFound -> return null
            DexKitCompat.CachedResult.Miss -> Unit
        }

        val scanned = DexKitCompat.withBridge(TAG, cl) { bridge ->
                bridge.setThreadNum(1)
                bridge.findMethod(
                    FindMethod.create()
                        .matcher(
                            MethodMatcher.create()
                                .modifiers(Modifier.STATIC)
                                .returnType(Void.TYPE)
                                .paramTypes(Application::class.java),
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
        val result = scanned

        if (result.isEmpty()) {
            XposedCompat.logD("[IntlAlbumAiInitBlockHook] direct album AI init candidate not found")
            DexKitCompat.putCachedMethod(TAG, DIRECT_ALBUM_AI_INIT_CACHE_ID, null)
            return null
        }

        val candidates = result.mapNotNull { methodData ->
            val method = resolveDirectAlbumAiInitRef(
                cl,
                DexKitCompat.MethodRef(methodData.className, methodData.methodName),
            ) ?: return@mapNotNull null
            if (methodData.isConstructor) return@mapNotNull null
            if (methodData.returnTypeName != "void") return@mapNotNull null
            if (methodData.paramTypeNames != listOf("android.app.Application")) return@mapNotNull null
            if (!Modifier.isStatic(methodData.modifiers)) return@mapNotNull null
            method
        }

        if (candidates.size != 1) {
            XposedCompat.logW(
                "[IntlAlbumAiInitBlockHook] ambiguous direct album AI init method: " +
                    candidates.joinToString { "${it.declaringClass.name}.${it.name}" },
            )
            DexKitCompat.putCachedMethod(TAG, DIRECT_ALBUM_AI_INIT_CACHE_ID, null)
            return null
        }

        val method = candidates.single()
        XposedCompat.log(
            "[IntlAlbumAiInitBlockHook] resolved direct album AI init: " +
                "${method.declaringClass.name}.${method.name}",
        )
        DexKitCompat.putCachedMethod(
            TAG,
            DIRECT_ALBUM_AI_INIT_CACHE_ID,
            DexKitCompat.MethodRef(method.declaringClass.name, method.name),
        )
        return method
    }

    private fun resolveDirectAlbumAiInitRef(cl: ClassLoader, ref: DexKitCompat.MethodRef): Method? {
        val clazz = XposedCompat.findClassOrNull(ref.className, cl) ?: return null
        if (!isExpectedAlbumAiComponentClass(clazz)) return null
        return clazz.declaredMethods.firstOrNull { method ->
            method.name == ref.methodName &&
                Modifier.isStatic(method.modifiers) &&
                method.returnType == Void.TYPE &&
                method.parameterTypes.size == 1 &&
                Application::class.java.isAssignableFrom(method.parameterTypes[0])
        }?.apply { isAccessible = true }
    }

    private fun isExpectedAlbumAiComponentClass(clazz: Class<*>): Boolean {
        val tokens = metadataTokens(clazz)
        return albumSemanticTokens.all { token ->
            tokens.any { it == token || it.startsWith(token) }
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

    private fun isEnabled(): Boolean =
        HookSettings.isPerformanceOptimizeEnabled && HookSettings.isIntlAlbumAiInitBlocked

    private const val TAG = "IntlAlbumAiInitBlockHook"
}
