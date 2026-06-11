package com.xiyunmn.puredupan.hook.core

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

internal object HookFileLogger {
    private const val LOG_DIR = "wangpanhook/logs"
    private const val MAIN_LOG_FILE = "wangpanhook.log"
    private const val ERROR_LOG_FILE = "wangpanhook-error.log"
    private const val MAX_LOG_BYTES = 512 * 1024L
    private const val MAX_PENDING_LINES = 200

    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val pendingLines = ArrayDeque<String>()

    @Volatile private var logDir: File? = null
    @Volatile private var processName: String = ""
    @Volatile private var headerWritten = false

    data class ClearResult(
        val path: String,
        val deletedCount: Int,
        val failedCount: Int,
    ) {
        val success: Boolean
            get() = failedCount == 0
    }

    fun setProcessName(name: String) {
        processName = name
    }

    fun initialize(context: Context) {
        val appCtx = context.applicationContext ?: context
        val targetDir = File(appCtx.cacheDir, LOG_DIR)
        synchronized(lock) {
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                Log.w("WangPanHook", "[HookFileLogger] create log dir failed: ${targetDir.absolutePath}")
                return
            }
            logDir = targetDir

            // Write header once
            if (!headerWritten) {
                writeHeaderLocked(appCtx)
                headerWritten = true
            }

            while (pendingLines.isNotEmpty()) {
                appendLineLocked(pendingLines.removeFirst())
            }
        }
    }

    fun write(priority: Int, tag: String, message: String) {
        val line = formatLine(priority, tag, message)
        synchronized(lock) {
            val dir = logDir
            if (dir == null) {
                addPendingLineLocked(line)
                return
            }
            appendLineLocked(line)

            // Also write ERROR/WARN to separate error log
            if (priority >= android.util.Log.WARN) {
                appendToErrorLogLocked(line)
            }
        }
    }

    fun clear(context: Context?): ClearResult {
        val dir = synchronized(lock) {
            pendingLines.clear()
            logDir ?: context?.let { File((it.applicationContext ?: it).cacheDir, LOG_DIR) }
        }
        val path = dir?.absolutePath.orEmpty()
        if (dir == null || !dir.exists()) {
            return ClearResult(path = path, deletedCount = 0, failedCount = 0)
        }

        var deleted = 0
        var failed = 0
        dir.walkBottomUp().forEach { file ->
            if (file == dir) return@forEach
            if (file.delete()) {
                deleted++
            } else {
                failed++
            }
        }
        return ClearResult(path = path, deletedCount = deleted, failedCount = failed)
    }

    fun logDirectoryPath(context: Context?): String {
        val dir = logDir ?: context?.let { File((it.applicationContext ?: it).cacheDir, LOG_DIR) }
        return dir?.absolutePath.orEmpty()
    }

    private fun addPendingLineLocked(line: String) {
        while (pendingLines.size >= MAX_PENDING_LINES) {
            pendingLines.removeFirst()
        }
        pendingLines.addLast(line)
    }

    private fun appendLineLocked(line: String) {
        val dir = logDir ?: return
        if (!dir.exists() && !dir.mkdirs()) return
        val file = logFileForProcess(dir)
        rotateIfNeeded(file)
        runCatching {
            file.appendText("$line\n", Charsets.UTF_8)
        }.onFailure { t ->
            Log.w("WangPanHook", "[HookFileLogger] append failed: ${t.message}")
        }
    }

    private fun appendToErrorLogLocked(line: String) {
        val dir = logDir ?: return
        if (!dir.exists()) return
        val file = errorLogFileForProcess(dir)
        rotateIfNeeded(file)
        runCatching {
            file.appendText("$line\n", Charsets.UTF_8)
        }.onFailure { t ->
            Log.w("WangPanHook", "[HookFileLogger] append error log failed: ${t.message}")
        }
    }

    private fun writeHeaderLocked(context: Context) {
        val header = buildString {
            appendLine("=== WangPanHook v${com.xiyunmn.puredupan.hook.BuildConfig.VERSION_NAME} (${com.xiyunmn.puredupan.hook.BuildConfig.VERSION_CODE}) ===")
            appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Host: ${context.packageName} ${getHostVersionName(context)}")
            appendLine("Process: $processName (PID ${Process.myPid()})")
            appendLine("Time: ${synchronized(timestampFormat) { timestampFormat.format(Date()) }}")
            appendLine("=== Log Start ===")
        }
        runCatching {
            val file = logFileForProcess(logDir ?: return)
            file.appendText(header, Charsets.UTF_8)
        }
    }

    private fun getHostVersionName(context: Context): String {
        return try {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName} ($versionCode)"
        } catch (t: Throwable) {
            "unknown"
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() <= MAX_LOG_BYTES) return
        val backup = File(file.parentFile, "${file.name}.1")
        runCatching {
            if (backup.exists()) backup.delete()
            file.renameTo(backup)
        }.onFailure { t ->
            Log.w("WangPanHook", "[HookFileLogger] rotate failed: ${t.message}")
        }
    }

    private fun logFileForProcess(dir: File): File {
        val name = processName
        if (name.isBlank() || name == Constants.TARGET_PACKAGE) {
            return File(dir, MAIN_LOG_FILE)
        }
        val suffix = name
            .removePrefix("${Constants.TARGET_PACKAGE}:")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "unknown" }
        return File(dir, "wangpanhook-$suffix.log")
    }

    private fun errorLogFileForProcess(dir: File): File {
        val name = processName
        if (name.isBlank() || name == Constants.TARGET_PACKAGE) {
            return File(dir, ERROR_LOG_FILE)
        }
        val suffix = name
            .removePrefix("${Constants.TARGET_PACKAGE}:")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "unknown" }
        return File(dir, "wangpanhook-$suffix-error.log")
    }

    private fun formatLine(priority: Int, tag: String, message: String): String {
        val level = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
        val timestamp = synchronized(timestampFormat) {
            timestampFormat.format(Date())
        }
        val thread = Thread.currentThread().name
        return "$timestamp ${Process.myPid()} $level/$tag [$thread] $message"
    }
}
