package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.os.Handler
import android.os.Looper
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduFilePageHookPoints
import com.xiyunmn.puredupan.hook.symbols.baidu.shared.BaiduThemeHookPoints
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.WeakHashMap

/** Rebinds file source/type chips after skin XML has overwritten their selection-dependent colors. */
internal object FileFilterThemeCompat {
    private const val TAG = "FileFilterThemeCompat"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val constructors = mutableSetOf<Constructor<*>>()
    private val adapters = WeakHashMap<Any, Method>()
    private var skinHooked = false

    private val refresh = Runnable {
        val targets = synchronized(adapters) { adapters.entries.map { it.key to it.value } }
        targets.forEach { (adapter, notifyChanged) ->
            runCatching { notifyChanged.invoke(adapter) }
                .onFailure { XposedCompat.logW("[$TAG] refresh failed: ${it.message}") }
        }
    }

    @Synchronized
    fun hook(cl: ClassLoader) {
        val mod = XposedCompat.module ?: return
        runCatching {
            val adapterBase = XposedCompat.findClassOrNull(BaiduFilePageHookPoints.RECYCLER_VIEW_ADAPTER, cl)
                ?: return
            val header = XposedCompat.findClassOrNull(BaiduFilePageHookPoints.FILE_LIST_TOOLBAR_HEADER, cl)
                ?: return
            val headerView = XposedCompat.findClassOrNull(BaiduFilePageHookPoints.FILE_LIST_TOOLBAR_HEADER_VIEW, cl)
            // The international build obfuscates this nested adapter name; its owner and base type survive.
            val sourceAdapter = headerView?.declaredClasses?.singleOrNull {
                adapterBase.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers)
            }
            val typeAdapter = XposedCompat.findClassOrNull(BaiduFilePageHookPoints.FILTER_TYPE_TAG_ADAPTER, cl)
                ?.takeIf { adapterBase.isAssignableFrom(it) }
            val notifyChanged = adapterBase.getMethod("notifyDataSetChanged")
                .takeIf { it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) } ?: return
            val skinManager = XposedCompat.findClassOrNull(BaiduThemeHookPoints.SKIN_MANAGER, cl) ?: return
            val skinUpdate = skinManager.getDeclaredMethod("notifySkinUpdate")
                .takeIf { it.returnType == Void.TYPE && !Modifier.isStatic(it.modifiers) } ?: return

            if (!skinHooked) {
                mod.hook(skinUpdate).intercept { chain ->
                    val result = chain.proceed()
                    // Wait until ALL native skin observers finish. Posting also avoids notifying a
                    // RecyclerView during layout; repeated skin notifications coalesce into one rebind.
                    mainHandler.removeCallbacks(refresh)
                    mainHandler.post(refresh)
                    result
                }
                skinHooked = true
            }
            listOfNotNull(sourceAdapter, typeAdapter).forEach { adapterClass ->
                runCatching {
                    val constructor = adapterClass.getDeclaredConstructor(header)
                    if (constructor in constructors) return@forEach
                    mod.hook(constructor).intercept { chain ->
                        val result = chain.proceed()
                        chain.thisObject?.let { adapter ->
                            // Values contain no View/adapter references, so an old page can be collected.
                            synchronized(adapters) { adapters[adapter] = notifyChanged }
                        }
                        result
                    }
                    constructors += constructor
                }.onFailure { XposedCompat.logW("[$TAG] adapter hook failed: ${it.message}") }
            }
        }.onFailure { XposedCompat.logW("[$TAG] install failed: ${it.message}") }
    }
}
