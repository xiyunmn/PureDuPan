package com.xiyunmn.puredupan.hook.ui

import android.content.Context
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.host.HostRuntimeState
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

internal object HostThemeRuntime {
    private val readers = ConcurrentHashMap<Class<*>, Method>()
    private val observedWriters = mutableSetOf<Method>()

    fun isNight(context: Context): Boolean? = runCatching {
        val className = HostRuntimeState.skinConfigClassNameFor(context) ?: return null
        val owner = XposedCompat.findClassOrNull(className, context.classLoader) ?: return null
        val reader = readers.getOrPut(owner) {
            owner.getDeclaredMethod("isDefaultSkin", Context::class.java).apply {
                check(Modifier.isStatic(modifiers) && returnType == Boolean::class.javaPrimitiveType)
                isAccessible = true
            }
        }
        // 皮肤变化监听安装失败时，仍可读取当前皮肤。
        runCatching { observeSkinChanges(owner) }
        !(reader.invoke(null, context.applicationContext ?: context) as Boolean)
    }.getOrNull()

    private fun observeSkinChanges(owner: Class<*>) {
        val module = XposedCompat.module ?: return
        val writer = owner.getDeclaredMethod("saveSkinPath", Context::class.java, String::class.java)
        if (!Modifier.isStatic(writer.modifiers) || writer.returnType != Void.TYPE) return
        synchronized(observedWriters) {
            if (writer in observedWriters) return
            writer.isAccessible = true
            module.hook(writer).intercept { chain ->
                val result = chain.proceed()
                HostThemeChangeDispatcher.notifyChanged("host-skin-path")
                result
            }
            observedWriters.add(writer)
        }
    }
}
