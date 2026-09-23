package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Uses the host Compose runtime so reads participate in its snapshot observer. */
internal class HostThemeSnapshotBinding private constructor(
    private val create: Method,
    private val policy: Any,
    private val read: Method,
    private val write: Method,
) {
    fun create(defaultSkin: Boolean): Any = checkNotNull(create.invoke(null, defaultSkin, policy))

    fun read(state: Any): Boolean = read.invoke(state) as Boolean

    fun update(state: Any, defaultSkin: Boolean) {
        write.invoke(state, defaultSkin)
    }

    companion object {
        fun resolve(factory: Class<*>, policy: Class<*>, state: Class<*>, mutableState: Class<*>): HostThemeSnapshotBinding? =
            runCatching {
                if (!state.isAssignableFrom(mutableState)) return null
                val create = factory.getMethod("mutableStateOf", Any::class.java, policy)
                val equality = factory.getMethod("structuralEqualityPolicy")
                val read = state.getMethod("getValue")
                val write = mutableState.getMethod("setValue", Any::class.java)
                if (!Modifier.isStatic(create.modifiers) || !Modifier.isStatic(equality.modifiers) ||
                    !mutableState.isAssignableFrom(create.returnType) || !policy.isAssignableFrom(equality.returnType) ||
                    Modifier.isStatic(read.modifiers) || Modifier.isStatic(write.modifiers) || write.returnType != Void.TYPE
                ) return null
                HostThemeSnapshotBinding(create, checkNotNull(equality.invoke(null)), read, write)
            }.getOrNull()
    }
}
