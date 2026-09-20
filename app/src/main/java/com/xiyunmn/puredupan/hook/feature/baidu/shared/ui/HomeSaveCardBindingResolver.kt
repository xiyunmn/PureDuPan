package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Reflection stays within registered save cards and their directly owned callbacks/bindings. */
internal object HomeSaveCardBindingResolver {
    private val writers = mutableMapOf<Class<*>, Method?>()

    data class CapturedCallback(val method: Method, val owner: Field)

    fun stateCollector(collector: Class<*>, card: Class<*>): CapturedCallback? =
        capturedCallback(collector, card) { method ->
            method.returnType == Any::class.java && method.parameterTypes.size == 2 &&
                isUiState(method.parameterTypes[0]) &&
                method.parameterTypes[1].name == "kotlin.coroutines.Continuation"
        }

    fun tabSelectionCallback(listener: Class<*>, card: Class<*>, tab: Class<*>): CapturedCallback? =
        capturedCallback(listener, card) { method ->
            method.name == "onTabSelected" && method.returnType == Void.TYPE &&
                method.parameterTypes.contentEquals(arrayOf(tab))
        }

    private fun capturedCallback(
        callback: Class<*>, card: Class<*>, matches: (Method) -> Boolean,
    ): CapturedCallback? {
        val owner = callback.declaredFields.singleOrNull {
            !Modifier.isStatic(it.modifiers) && it.type == card
        } ?: return null
        val method = callback.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && !it.isBridge && matches(it)
        } ?: return null
        return CapturedCallback(method.apply { isAccessible = true }, owner.apply { isAccessible = true })
    }

    fun isUiState(clazz: Class<*>): Boolean {
        if (clazz.isPrimitive || clazz == Any::class.java) return false
        val fields = clazz.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        return fields.count { List::class.java.isAssignableFrom(it.type) } >= 2 &&
            fields.any { it.type.isEnum } &&
            fields.any { it.type == Boolean::class.javaPrimitiveType } &&
            clazz.declaredMethods.any { method ->
                !Modifier.isStatic(method.modifiers) && method.returnType == clazz &&
                    method.parameterTypes.size >= 4 &&
                    method.parameterTypes.count { List::class.java.isAssignableFrom(it) } >= 2 &&
                    method.parameterTypes.any { it == Boolean::class.javaPrimitiveType }
            }
    }

    fun subscriptionBinder(card: Class<*>, item: Class<*>, view: Class<*>): Pair<Method, Method>? {
        val method = card.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && !it.isBridge && it.returnType == Void.TYPE &&
                it.parameterTypes.size == 2 &&
                it.parameterTypes[0].simpleName.endsWith("SubscribeToUpdatesLayoutBinding") &&
                it.parameterTypes[1] == item
        } ?: return null
        val binding = method.parameterTypes[0]
        val bind = binding.declaredMethods.singleOrNull {
            Modifier.isStatic(it.modifiers) && it.returnType == binding &&
                it.parameterTypes.contentEquals(arrayOf(view))
        } ?: return null
        return method.apply { isAccessible = true } to bind.apply { isAccessible = true }
    }

    fun listWriter(viewModel: Class<*>): Method? = synchronized(writers) {
        if (writers.containsKey(viewModel)) return@synchronized writers[viewModel]
        val writer = viewModel.declaredMethods.singleOrNull {
            !Modifier.isStatic(it.modifiers) && !it.isBridge && it.returnType == Void.TYPE &&
                it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType, List::class.java))
        }?.apply { isAccessible = true }
        writers[viewModel] = writer
        writer
    }
}
