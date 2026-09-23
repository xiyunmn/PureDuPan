package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduThemeHookPoints
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Makes KMP search's plain SkinConfig read observable without replacing its composition. */
internal object ComposeSearchThemeCompat {
    private const val TAG = "ComposeSearchThemeCompat"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val installed = mutableSetOf<Method>()
    private val states = WeakHashMap<Activity, Any>()

    @Synchronized
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        runCatching {
            val activityClass = XposedCompat.findClassOrNull(BaiduThemeHookPoints.KMP_SHARED_ACTIVITY, cl) ?: return
            val searchScene = runCatching {
                activityClass.getDeclaredField("isSearchScene").apply { isAccessible = true }
            }.getOrNull() ?: return
            val skinConfig = XposedCompat.findClassOrNull(BaiduThemeHookPoints.SKIN_CONFIG, cl) ?: return
            val isDefaultSkin = skinConfig.getMethod("isDefaultSkin", Context::class.java)
            val skinManager = XposedCompat.findClassOrNull(BaiduThemeHookPoints.SKIN_MANAGER, cl) ?: return
            val skinUpdate = skinManager.getDeclaredMethod("notifySkinUpdate")
            val binding = HostThemeSnapshotBinding.resolve(
                XposedCompat.findClassOrNull(BaiduThemeHookPoints.COMPOSE_SNAPSHOT_STATE, cl) ?: return,
                XposedCompat.findClassOrNull(BaiduThemeHookPoints.COMPOSE_MUTATION_POLICY, cl) ?: return,
                XposedCompat.findClassOrNull(BaiduThemeHookPoints.COMPOSE_STATE, cl) ?: return,
                XposedCompat.findClassOrNull(BaiduThemeHookPoints.COMPOSE_MUTABLE_STATE, cl) ?: return,
            ) ?: return

            val refresh = Runnable {
                val targets = synchronized(states) { states.entries.map { it.key to it.value } }
                targets.forEach { (activity, state) ->
                    if (activity.isDestroyed || activity.isFinishing) {
                        synchronized(states) { states.remove(activity) }
                    } else {
                        runCatching {
                            // Application context bypasses the Activity-only read hook below.
                            binding.update(state, isDefaultSkin.invoke(null, activity.applicationContext) as Boolean)
                            refreshStatusBar(activity, isDefaultSkin)
                        }.onFailure { XposedCompat.logW("[$TAG] refresh failed: ${it.message}") }
                    }
                }
            }
            if (skinUpdate !in installed) {
                mod.hook(skinUpdate).intercept { chain ->
                    val result = chain.proceed()
                    mainHandler.removeCallbacks(refresh)
                    mainHandler.post(refresh)
                    result
                }
                installed += skinUpdate
            }
            if (isDefaultSkin !in installed) {
                mod.hook(isDefaultSkin).intercept { chain ->
                    val result = chain.proceed()
                    val activity = chain.args.firstOrNull() as? Activity
                    if (result is Boolean && activity != null && activityClass.isInstance(activity)) {
                        runCatching {
                            if (searchScene.getBoolean(activity)) {
                                val state = synchronized(states) {
                                    states.getOrPut(activity) { binding.create(result) }
                                }
                                // KmpSharedActivity reads SkinConfig inside its root composable.
                                // Reading this host MutableState registers that scope for recomposition.
                                // Preserve SkinConfig's real result, including reads outside Compose.
                                binding.read(state)
                                // EdgeToEdge uses WindowInsetsController on Android 11+, while
                                // the host skin callback only updates the legacy visibility flags.
                                activity.window.decorView.postOnAnimation {
                                    refreshStatusBar(activity, isDefaultSkin)
                                }
                            }
                        }.onFailure { XposedCompat.logW("[$TAG] state read failed: ${it.message}") }
                    }
                    result
                }
                installed += isDefaultSkin
            }
        }.onFailure { XposedCompat.logW("[$TAG] install failed: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    private fun refreshStatusBar(activity: Activity, isDefaultSkin: Method) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            val light = isDefaultSkin.invoke(null, activity.applicationContext) as Boolean
            val window = activity.window
            if (Build.VERSION.SDK_INT >= 30) {
                val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                window.insetsController?.setSystemBarsAppearance(if (light) mask else 0, mask)
            } else {
                val decor = window.decorView
                val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                decor.systemUiVisibility = if (light) decor.systemUiVisibility or mask
                else decor.systemUiVisibility and mask.inv()
            }
        }.onFailure { XposedCompat.logW("[$TAG] status bar refresh failed: ${it.message}") }
    }
}
