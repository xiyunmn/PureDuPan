package com.xiyunmn.puredupan.hook.dexkit

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.luckypray.dexkit.DexKitBridge

internal object DexKitCompat {
    private const val LIB_NAME = "dexkit"
    private const val LIB_FILE_NAME = "libdexkit.so"
    private const val CACHE_SCHEMA = 5
    private const val CACHE_PREFIX = "dexkit_method_cache_v$CACHE_SCHEMA"
    private const val STATUS_PREFIX = "dexkit_target_status_v$CACHE_SCHEMA"
    private const val KEY_FORCE_FULL_SCAN = "dexkit_force_full_scan"
    private const val KEY_FORCE_FULL_SCAN_REASON = "dexkit_force_full_scan_reason"
    private const val STATUS_FOUND = "found"
    private const val STATUS_NOT_FOUND = "not_found"
    private const val TARGET_STATE_SUCCESS = "success"
    private const val TARGET_STATE_ERROR = "error"
    private const val TARGET_STATE_SCANNING = "scanning"

    @Volatile private var loadState: LoadState = LoadState.Unknown
    @Volatile private var runtimeProvider: RuntimeProvider? = null
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    private val scanAllowed = object : ThreadLocal<Boolean>() {
        override fun initialValue(): Boolean = false
    }

    data class MethodRef(
        val className: String,
        val methodName: String,
    )

    data class TargetStatus(
        val id: String,
        val state: String,
        val detail: String?,
        val updatedAt: Long,
    ) {
        val success: Boolean
            get() = state == TARGET_STATE_SUCCESS
    }

    sealed class CachedResult<out T> {
        data class Found<T>(val value: T) : CachedResult<T>()
        data object NotFound : CachedResult<Nothing>()
        data object Miss : CachedResult<Nothing>()
    }

    private sealed class LoadState {
        data object Unknown : LoadState()
        data object Available : LoadState()
        data class Unavailable(val reason: String) : LoadState()
    }

    private data class CacheEntry(
        val fingerprint: String,
        val status: String,
        val className: String? = null,
        val methodName: String? = null,
    )

    private data class RuntimeProvider(
        val appContextProvider: () -> Context?,
        val moduleStatePrefsProvider: (Context) -> SharedPreferences,
    )

    internal fun setRuntimeProvider(
        appContextProvider: () -> Context?,
        moduleStatePrefsProvider: (Context) -> SharedPreferences,
    ) {
        runtimeProvider = RuntimeProvider(
            appContextProvider = appContextProvider,
            moduleStatePrefsProvider = moduleStatePrefsProvider,
        )
    }

    fun <T> withBridge(
        tag: String,
        cl: ClassLoader,
        useMemoryDexFile: Boolean = true,
        resolverId: String? = null,
        block: (DexKitBridge) -> T,
    ): T? {
        if (scanAllowed.get() != true) {
            XposedCompat.logD("[$tag] DexKit scan skipped outside warm-up")
            return null
        }
        if (!ensureLoaded(tag)) return null
        return try {
            XposedCompat.logD("[$tag] DexKit bridge create: useMemoryDexFile=$useMemoryDexFile")
            DexKitBridge.create(cl, useMemoryDexFile).use(block)
        } catch (t: UnsatisfiedLinkError) {
            markUnavailable("${t.javaClass.simpleName}: ${t.message}")
            resolverId?.let { markTargetError(tag, it, buildBridgeFailureDetail(t)) }
            XposedCompat.logD("[$tag] DexKit unavailable: ${t.message}")
            null
        } catch (t: Throwable) {
            resolverId?.let { markTargetError(tag, it, buildBridgeFailureDetail(t)) }
            XposedCompat.logW("[$tag] DexKit bridge failed: ${t.message}")
            null
        }
    }

    fun <T> runWithScanningAllowed(block: () -> T): T {
        val previous = scanAllowed.get() == true
        scanAllowed.set(true)
        return try {
            block()
        } finally {
            scanAllowed.set(previous)
        }
    }

    fun <T> getCachedMethod(
        tag: String,
        resolverId: String,
        resolve: (MethodRef) -> T?,
    ): CachedResult<T> {
        val fingerprint = hostFingerprint() ?: return CachedResult.Miss
        val keyPrefix = cacheKeyPrefix(resolverId)
        val entry = memoryCache[resolverId]?.takeIf { it.fingerprint == fingerprint }
            ?: readCacheEntry(keyPrefix, fingerprint)
            ?: return CachedResult.Miss

        memoryCache[resolverId] = entry
        return when (entry.status) {
            STATUS_NOT_FOUND -> {
                XposedCompat.logD("[$tag] DexKit negative cache hit: $resolverId")
                CachedResult.NotFound
            }
            STATUS_FOUND -> {
                val ref = MethodRef(
                    className = entry.className.orEmpty(),
                    methodName = entry.methodName.orEmpty(),
                )
                val resolved = resolve(ref)
                if (resolved != null) {
                    XposedCompat.logD(
                        "[$tag] DexKit cache hit: $resolverId -> ${ref.className}.${ref.methodName}",
                    )
                    CachedResult.Found(resolved)
                } else {
                    clearCachedMethod(tag, resolverId)
                    CachedResult.Miss
                }
            }
            else -> {
                clearCachedMethod(tag, resolverId)
                CachedResult.Miss
            }
        }
    }

    fun putCachedMethod(tag: String, resolverId: String, ref: MethodRef?) {
        val fingerprint = hostFingerprint() ?: return
        val keyPrefix = cacheKeyPrefix(resolverId)
        val prefs = statePrefs() ?: return
        if (ref == null) {
            prefs.edit()
                .putString("$keyPrefix.fingerprint", fingerprint)
                .putString("$keyPrefix.status", STATUS_NOT_FOUND)
                .remove("$keyPrefix.className")
                .remove("$keyPrefix.methodName")
                .apply()
            memoryCache[resolverId] = CacheEntry(
                fingerprint = fingerprint,
                status = STATUS_NOT_FOUND,
            )
            XposedCompat.logD("[$tag] DexKit negative cache updated: $resolverId")
            return
        }
        val entry = CacheEntry(
            fingerprint = fingerprint,
            status = STATUS_FOUND,
            className = ref.className,
            methodName = ref.methodName,
        )
        prefs.edit()
            .putString("$keyPrefix.fingerprint", entry.fingerprint)
            .putString("$keyPrefix.status", entry.status)
            .putString("$keyPrefix.className", entry.className)
            .putString("$keyPrefix.methodName", entry.methodName)
            .apply()
        memoryCache[resolverId] = entry
        val value = "${ref.className}.${ref.methodName}"
        XposedCompat.logD("[$tag] DexKit cache updated: $resolverId -> $value")
        recordTargetStatus(tag, resolverId, TARGET_STATE_SUCCESS, value)
    }

    fun shouldSkipScan(tag: String, resolverId: String, forceFullScan: Boolean = false): Boolean {
        if (forceFullScan) return false
        val status = readTargetStatus(resolverId) ?: return false
        if (status.state != TARGET_STATE_ERROR) return false
        XposedCompat.logD(
            "[$tag] DexKit scan skipped by cached failure: " +
                "$resolverId detail=${status.detail.orEmpty().lineSequence().firstOrNull().orEmpty()}",
        )
        return true
    }

    fun clearCachedMethod(tag: String, resolverId: String) {
        val keyPrefix = cacheKeyPrefix(resolverId)
        statePrefs()?.edit()
            ?.remove("$keyPrefix.fingerprint")
            ?.remove("$keyPrefix.status")
            ?.remove("$keyPrefix.className")
            ?.remove("$keyPrefix.methodName")
            ?.apply()
        memoryCache.remove(resolverId)
        XposedCompat.logD("[$tag] DexKit cache cleared: $resolverId")
    }

    fun clearCachedMethods(tag: String, resolverIds: Collection<String>) {
        val prefs = statePrefs() ?: return
        val editor = prefs.edit()
        resolverIds.forEach { resolverId ->
            val keyPrefix = cacheKeyPrefix(resolverId)
            editor
                .remove("$keyPrefix.fingerprint")
                .remove("$keyPrefix.status")
                .remove("$keyPrefix.className")
                .remove("$keyPrefix.methodName")
            removeTargetStatus(editor, resolverId)
            memoryCache.remove(resolverId)
        }
        editor.apply()
        XposedCompat.logD("[$tag] DexKit caches cleared: ${resolverIds.joinToString()}")
    }

    fun markFullScanPending(reason: String) {
        statePrefs()?.edit()
            ?.putBoolean(KEY_FORCE_FULL_SCAN, true)
            ?.putString(KEY_FORCE_FULL_SCAN_REASON, reason)
            ?.apply()
        XposedCompat.logD("[DexKitCompat] full scan pending: $reason")
    }

    fun consumeFullScanPending(): Boolean {
        val prefs = statePrefs() ?: return false
        if (!prefs.getBoolean(KEY_FORCE_FULL_SCAN, false)) return false
        val reason = prefs.getString(KEY_FORCE_FULL_SCAN_REASON, null).orEmpty()
        prefs.edit()
            .remove(KEY_FORCE_FULL_SCAN)
            .remove(KEY_FORCE_FULL_SCAN_REASON)
            .apply()
        XposedCompat.log("[DexKitCompat] consuming pending full scan: $reason")
        return true
    }

    fun markTargetScanning(tag: String, resolverId: String) {
        recordTargetStatus(tag, resolverId, TARGET_STATE_SCANNING, "scanning")
    }

    fun markTargetSuccess(tag: String, resolverId: String, detail: String?) {
        recordTargetStatus(
            tag,
            resolverId,
            TARGET_STATE_SUCCESS,
            detail?.takeIf { it.isNotBlank() } ?: "resolved or fallback available",
        )
    }

    fun markTargetError(tag: String, resolverId: String, detail: String?) {
        recordTargetStatus(
            tag,
            resolverId,
            TARGET_STATE_ERROR,
            detail?.takeIf { it.isNotBlank() } ?: "scan failed",
        )
    }

    fun readTargetStatus(resolverId: String): TargetStatus? {
        val fingerprint = hostFingerprint() ?: return null
        val prefs = statePrefs() ?: return null
        val keyPrefix = targetStatusKeyPrefix(resolverId)
        val storedFingerprint = prefs.getString("$keyPrefix.fingerprint", null) ?: return null
        if (storedFingerprint != fingerprint) return null
        val state = prefs.getString("$keyPrefix.state", null) ?: return null
        return TargetStatus(
            id = resolverId,
            state = state,
            detail = prefs.getString("$keyPrefix.detail", null),
            updatedAt = prefs.getLong("$keyPrefix.updatedAt", 0L),
        )
    }

    private fun ensureLoaded(tag: String): Boolean {
        when (val state = loadState) {
            LoadState.Available -> return true
            is LoadState.Unavailable -> {
                XposedCompat.logD("[$tag] DexKit skipped: ${state.reason}")
                return false
            }
            LoadState.Unknown -> Unit
        }

        synchronized(this) {
            when (val state = loadState) {
                LoadState.Available -> return true
                is LoadState.Unavailable -> {
                    XposedCompat.logD("[$tag] DexKit skipped: ${state.reason}")
                    return false
                }
                LoadState.Unknown -> Unit
            }

            val loaded = loadFromModuleNativeDir(tag) || loadFromLibraryPath(tag)
            if (!loaded && loadState is LoadState.Unknown) {
                markUnavailable("native library not loaded")
            }
            return loaded
        }
    }

    private fun loadFromModuleNativeDir(tag: String): Boolean {
        val hostContext = appContext() ?: return false
        val moduleContext = runCatching {
            hostContext.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        }.getOrNull() ?: return false
        val nativeDir = moduleContext.applicationInfo?.nativeLibraryDir?.takeIf { it.isNotBlank() } ?: return false
        val libFile = File(nativeDir, LIB_FILE_NAME)
        if (!libFile.isFile) return false

        return runCatching {
            System.load(libFile.absolutePath)
            loadState = LoadState.Available
            XposedCompat.logD("[$tag] DexKit native loaded: ${libFile.absolutePath}")
            true
        }.getOrElse {
            markUnavailable("${it.javaClass.simpleName}: ${it.message}")
            XposedCompat.logD("[$tag] DexKit native load failed: ${it.message}")
            false
        }
    }

    private fun loadFromLibraryPath(tag: String): Boolean {
        return runCatching {
            System.loadLibrary(LIB_NAME)
            loadState = LoadState.Available
            XposedCompat.logD("[$tag] DexKit native loaded by System.loadLibrary")
            true
        }.getOrElse {
            markUnavailable("${it.javaClass.simpleName}: ${it.message}")
            XposedCompat.logD("[$tag] DexKit loadLibrary failed: ${it.message}")
            false
        }
    }

    private fun markUnavailable(reason: String?) {
        loadState = LoadState.Unavailable(reason?.takeIf { it.isNotBlank() } ?: "unknown native load failure")
    }

    private fun buildBridgeFailureDetail(t: Throwable): String {
        val stack = t.stackTraceToString()
            .lineSequence()
            .take(8)
            .joinToString("\n")
        return buildString {
            append("bridge failure").append('\n')
            append("throwable=").append(t::class.java.name).append('\n')
            append("message=").append(t.message ?: "-").append('\n')
            append("stacktrace=\n").append(stack)
        }
    }

    private fun readCacheEntry(keyPrefix: String, fingerprint: String): CacheEntry? {
        val prefs = statePrefs() ?: return null
        val storedFingerprint = prefs.getString("$keyPrefix.fingerprint", null) ?: return null
        if (storedFingerprint != fingerprint) return null
        val status = prefs.getString("$keyPrefix.status", null) ?: return null
        return CacheEntry(
            fingerprint = storedFingerprint,
            status = status,
            className = prefs.getString("$keyPrefix.className", null),
            methodName = prefs.getString("$keyPrefix.methodName", null),
        )
    }

    private fun statePrefs(): SharedPreferences? {
        val provider = runtimeProvider ?: return null
        val context = appContext() ?: return null
        return runCatching { provider.moduleStatePrefsProvider(context) }.getOrNull()
    }

    private fun appContext(): Context? {
        return runCatching { runtimeProvider?.appContextProvider?.invoke() }
            .getOrNull()
    }

    private fun cacheKeyPrefix(resolverId: String): String =
        "$CACHE_PREFIX.${resolverId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

    private fun recordTargetStatus(
        tag: String,
        resolverId: String,
        state: String,
        detail: String?,
    ) {
        val fingerprint = hostFingerprint() ?: return
        val keyPrefix = targetStatusKeyPrefix(resolverId)
        statePrefs()?.edit()
            ?.putString("$keyPrefix.fingerprint", fingerprint)
            ?.putString("$keyPrefix.state", state)
            ?.putString("$keyPrefix.detail", detail)
            ?.putLong("$keyPrefix.updatedAt", System.currentTimeMillis())
            ?.apply()
        XposedCompat.logD("[$tag] DexKit status updated: $resolverId -> $state, $detail")
    }

    private fun removeTargetStatus(
        editor: android.content.SharedPreferences.Editor,
        resolverId: String,
    ) {
        val keyPrefix = targetStatusKeyPrefix(resolverId)
        editor
            .remove("$keyPrefix.fingerprint")
            .remove("$keyPrefix.state")
            .remove("$keyPrefix.detail")
            .remove("$keyPrefix.updatedAt")
    }

    private fun targetStatusKeyPrefix(resolverId: String): String =
        "$STATUS_PREFIX.${resolverId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

    private fun hostFingerprint(): String? {
        val context = appContext() ?: return null
        val packageName = context.packageName
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
        }.getOrNull() ?: return null
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return "$CACHE_SCHEMA|$packageName|${info.versionName.orEmpty()}|$versionCode|" +
            "${BuildConfig.VERSION_NAME}|${BuildConfig.VERSION_CODE}"
    }
}
