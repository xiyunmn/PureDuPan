package com.xiyunmn.puredupan.hook.feature.baidu.shared.ad

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object MarketingDialogBindings {
    fun offerEntry(
        owner: Class<*>,
        activityClass: Class<*>,
        stableName: String,
        observerEnclosingMethod: Method?,
    ): Method? {
        fun matches(method: Method) = method.declaringClass == owner &&
            !Modifier.isStatic(method.modifiers) && method.returnType == Void.TYPE &&
            method.parameterTypes.contentEquals(arrayOf(activityClass))

        return owner.declaredMethods.singleOrNull { it.name == stableName && matches(it) }
            ?: observerEnclosingMethod?.takeIf(::matches)
    }

    fun offerState(owner: Class<*>, liveDataClass: Class<*>): Field? =
        owner.declaredFields.singleOrNull {
            !Modifier.isStatic(it.modifiers) && liveDataClass.isAssignableFrom(it.type)
        }

    fun noArgVoid(owner: Class<*>, name: String): Method? {
        var current: Class<*>? = owner
        while (current != null) {
            val method = current.declaredMethods.singleOrNull {
                it.name == name && !Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.isEmpty() && it.returnType == Void.TYPE
            }
            if (method != null) return method.apply { isAccessible = true }
            current = current.superclass
        }
        return null
    }
}
