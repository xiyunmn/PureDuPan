package com.xiyunmn.puredupan.hook.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.config.ConfigManager
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.luckypray.dexkit.DexKitBridge

internal object DexKitCompat {
    private const val LIB_NAME = "dexkit"
    private const val LIB_FILE_NAME = "libdexkit.so"
    private const val CACHE_SCHEMA = 1
    private const val CACHE_PREFIX = "dexkit_method_cache_v$CACHE_SCHEMA"
    private const val STATUS_FOUND = "found"
    private const val STATUS_NOT_FOUND = "not_found"

    @Volatile private var loadState: LoadState = LoadState.Unknown
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()

    data class MethodRef(
        val className: String,
        val methodName: String,
    )

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

    fun <T> withBridge(
        tag: String,
        cl: ClassLoader,
        useMemoryDexFile: Boolean = false,
        block: (DexKitBridge) -> T,
    ): T? {
        if (!ensureLoaded(tag)) return null
        return try {
            DexKitBridge.create(cl, useMemoryDexFile).use(block)
        } catch (t: UnsatisfiedLinkError) {
            markUnavailable("${t.javaClass.simpleName}: ${t.message}")
            XposedCompat.logD("[$tag] DexKit unavailable: ${t.message}")
            null
        } catch (t: Throwable) {
            XposedCompat.logW("[$tag] DexKit bridge failed: ${t.message}")
            null
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
                XposedCompat.logD("[$tag] DexKit cache hit: $resolverId not found")
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
        val entry = if (ref == null) {
            CacheEntry(fingerprint = fingerprint, status = STATUS_NOT_FOUND)
        } else {
            CacheEntry(
                fingerprint = fingerprint,
                status = STATUS_FOUND,
                className = ref.className,
                methodName = ref.methodName,
            )
        }
        val prefs = statePrefs() ?: return
        prefs.edit()
            .putString("$keyPrefix.fingerprint", entry.fingerprint)
            .putString("$keyPrefix.status", entry.status)
            .putString("$keyPrefix.className", entry.className)
            .putString("$keyPrefix.methodName", entry.methodName)
            .apply()
        memoryCache[resolverId] = entry
        val value = ref?.let { "${it.className}.${it.methodName}" } ?: "not found"
        XposedCompat.logD("[$tag] DexKit cache updated: $resolverId -> $value")
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
        val hostContext = ConfigManager.getAppContext() ?: return false
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

    private fun statePrefs() = ConfigManager.getAppContext()?.let { context ->
        ConfigManager.getModuleStatePrefs(context)
    }

    private fun cacheKeyPrefix(resolverId: String): String =
        "$CACHE_PREFIX.${resolverId.replace(Regex("[^A-Za-z0-9_.-]"), "_")}"

    private fun hostFingerprint(): String? {
        val context = ConfigManager.getAppContext() ?: return null
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
        return "$CACHE_SCHEMA|$packageName|${info.versionName.orEmpty()}|$versionCode"
    }
}
