package com.xiyunmn.puredupan.hook.config

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostPackages
import java.util.concurrent.atomic.AtomicBoolean

/** 跨进程同步启动图开关，用户设置由各宿主保存。 */
internal object StartupSplashSettingsSync {
    private const val ACTION = "com.xiyunmn.puredupan.hook.SYNC_STARTUP_SPLASH"
    private const val IDENTITY_ACTION = "com.xiyunmn.puredupan.hook.STARTUP_SPLASH_IDENTITY"
    private const val IDENTITY = "identity"
    private const val ENABLED = "enabled"
    private const val PREFS = "puredupan_startup_splash_v1"
    private val failureLogged = AtomicBoolean(false)
    private var sender: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null
    @Volatile private var enabledHosts: Set<String> = emptySet()

    fun publish(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT < 31 || Application.getProcessName() != context.packageName ||
            context.packageName !in HostPackages.baiduPackages
        ) return
        runCatching {
            val identity = sender ?: PendingIntent.getBroadcast(
                context, 0, Intent(IDENTITY_ACTION).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).also { sender = it }
            context.sendBroadcast(Intent(ACTION).setPackage(HostPackages.SYSTEM_UI)
                .putExtra(IDENTITY, identity).putExtra(ENABLED, enabled)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
        }.onFailure { logFailure(it) }
    }

    @Suppress("DEPRECATION")
    fun initializeSystemUi(context: Context) {
        if (receiver != null || context.packageName != HostPackages.SYSTEM_UI) return
        val storage = context.createDeviceProtectedStorageContext()
        val prefs = storage.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabledHosts = prefs.all.filterValues { it == true }.keys.toSet()
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                runCatching {
                    val identity = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(IDENTITY, PendingIntent::class.java)
                    } else intent.getParcelableExtra<PendingIntent>(IDENTITY)
                    val packageName = identity?.creatorPackage ?: return
                    if (packageName !in HostPackages.baiduPackages || !intent.hasExtra(ENABLED)) return
                    // 通过 Android 提供的 PendingIntent 创建者身份校验来源。
                    val key = key(identity.creatorUid, packageName)
                    val enabled = intent.getBooleanExtra(ENABLED, false)
                    if ((key in enabledHosts) == enabled) return
                    enabledHosts = if (enabled) enabledHosts + key else enabledHosts - key
                    prefs.edit().apply {
                        if (enabled) putBoolean(key, true) else remove(key)
                    }.apply()
                }.onFailure { logFailure(it) }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            storage.registerReceiver(listener, IntentFilter(ACTION), Context.RECEIVER_EXPORTED)
        } else storage.registerReceiver(listener, IntentFilter(ACTION))
        receiver = listener
    }

    fun isEnabled(info: ApplicationInfo): Boolean = key(info.uid, info.packageName) in enabledHosts

    private fun key(uid: Int, packageName: String) = "$uid:$packageName"

    private fun logFailure(error: Throwable) {
        if (failureLogged.compareAndSet(false, true)) {
            XposedCompat.logW("[StartupSplashSettingsSync] ${error.javaClass.simpleName}: ${error.message}")
        }
    }
}
