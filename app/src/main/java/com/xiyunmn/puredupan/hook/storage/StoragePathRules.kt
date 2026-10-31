package com.xiyunmn.puredupan.hook.storage

import java.util.Locale

/** 统一处理路径规范化、名称校验和父目录选择。 */
object StoragePathRules {
    private const val MAX_PATH_LENGTH = 1024
    private val illegalFileName = Regex("[\\u0000-\\u001f<>:\"/\\\\|?*]")

    fun normalizeRelativePath(raw: String?): String {
        val value = raw?.trim().orEmpty().replace('\\', '/')
        if (value.isEmpty()) return ""
        if (value == "/") return ""
        val pathValue = if (value.startsWith('/')) {
            val lower = value.lowercase(Locale.ROOT)
            if (lower.startsWith("/storage/") || lower.startsWith("/sdcard/")) {
                throw IllegalArgumentException("absolute path is not allowed")
            }
            value.trimStart('/')
        } else {
            value
        }
        if (pathValue.length > MAX_PATH_LENGTH) throw IllegalArgumentException("path too long")
        val parts = pathValue.split('/').filter { it.isNotBlank() }
        if (parts.any { it == "." || it == ".." }) {
            throw IllegalArgumentException("parent traversal is not allowed")
        }
        parts.forEach { validateName(it) }
        return parts.joinToString("/")
    }

    fun validateName(name: String?): String {
        val value = name?.trim().orEmpty()
        if (value.isEmpty()) throw IllegalArgumentException("empty name")
        if (value == "." || value == "..") throw IllegalArgumentException("invalid name")
        if (value.length > 255) throw IllegalArgumentException("name too long")
        if (illegalFileName.containsMatchIn(value)) throw IllegalArgumentException("illegal file name")
        return value
    }

    /** 去除宿主默认公共目录前缀，保留网盘父目录层级。 */
    fun stripDefaultPublicPrefix(raw: String?): String {
        val normalized = normalizeRelativePath(raw)
        if (normalized.isEmpty()) return normalized
        val lower = normalized.lowercase(Locale.ROOT)
        val prefixes = listOf(
            "download/baidunetdisk",
            "download/baidu netdisk",
            "baidunetdisk",
            "baidu netdisk",
        )
        return prefixes.firstOrNull { lower == it || lower.startsWith("$it/") }
            ?.let { normalized.substring(it.length).trimStart('/') }
            ?: normalized
    }

    /** 移除外层目录时，优先使用宿主提供的任务相对父路径。 */
    fun selectDownloadParent(
        hostParent: String?,
        taskRelativeParent: String?,
        removeOuterPath: Boolean,
    ): String {
        val candidate = if (removeOuterPath && taskRelativeParent != null) {
            taskRelativeParent
        } else {
            hostParent
        }
        return stripDefaultPublicPrefix(candidate)
    }

    fun join(parent: String?, child: String?): String {
        val p = normalizeRelativePath(parent)
        val c = validateName(child)
        return if (p.isEmpty()) c else "$p/$c"
    }
}
