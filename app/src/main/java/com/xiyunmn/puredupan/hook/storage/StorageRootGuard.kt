package com.xiyunmn.puredupan.hook.storage

import android.net.Uri
import android.provider.DocumentsContract
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.io.File
import java.nio.file.AccessDeniedException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Narrow guard for new direct children of shared-storage root. It never intercepts file I/O. */
object StorageRootGuard {
    private val allowedRoots = setOf(
        "android", "alarms", "audiobooks", "dcim", "documents", "download", "movies",
        "music", "notifications", "pictures", "podcasts", "ringtones", "recordings",
        "baidu", "baidunetdisk", "baiduyuedu", "baiduwenku", "baidubox", "wechatcloudbackup",
        "com.jifen.ac", "com.jifen.op",
    )
    private val logged = ConcurrentHashMap<String, Long>()

    fun shouldBlock(path: String?, snapshot: StorageRedirectSnapshot): Boolean {
        if (!snapshot.rootGuardEnabled || path.isNullOrBlank()) return false
        val parsed = parseSharedRootPath(path) ?: return false
        if (parsed.first.isEmpty()) return false
        if (parsed.first.lowercase(Locale.ROOT) in allowedRoots) return false
        val targetFirst = authorizedTargetFirstSegment(snapshot.downloadTreeUri)
        if (targetFirst != null && parsed.first.equals(targetFirst, ignoreCase = true)) return false
        // mkdirs on an existing historical root is harmless; only reject a genuinely new child.
        val direct = File(parsed.second)
        if (direct.exists()) return false
        logBlocked(path, snapshot.hostPackageName)
        return true
    }

    fun accessDenied(path: String?, snapshot: StorageRedirectSnapshot): AccessDeniedException {
        logBlocked(path.orEmpty(), snapshot.hostPackageName)
        return AccessDeniedException(path)
    }

    private fun parseSharedRootPath(raw: String): Pair<String, String>? {
        val normalized = raw.replace('\\', '/').trimEnd('/')
        val root = when {
            normalized == "/storage/emulated/0" -> return null
            normalized.startsWith("/storage/emulated/0/") -> "/storage/emulated/0/"
            normalized.startsWith("/sdcard/") -> "/sdcard/"
            normalized.startsWith("/sdcard") && normalized.length == 7 -> return null
            else -> return null
        }
        val rest = normalized.removePrefix(root)
        val first = rest.substringBefore('/').trim()
        if (first.isEmpty()) return null
        return first to "$root$first"
    }

    private fun authorizedTargetFirstSegment(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        return runCatching {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
            if (id.startsWith("primary:")) id.substringAfter(':').trim('/').substringBefore('/').takeIf { it.isNotBlank() }
            else null
        }.getOrNull()
    }

    private fun logBlocked(path: String, host: String) {
        val key = "$host|$path"
        val now = System.currentTimeMillis()
        val previous = logged.putIfAbsent(key, now)
        if (previous == null || now - previous > 15_000L) {
            logged[key] = now
            val stack = Throwable().stackTrace
                .drop(2)
                .take(6)
                .joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            XposedCompat.logW(
                "root_guard_blocked path=$path host=$host caller=${Thread.currentThread().name} stack=$stack",
            )
        }
    }
}
