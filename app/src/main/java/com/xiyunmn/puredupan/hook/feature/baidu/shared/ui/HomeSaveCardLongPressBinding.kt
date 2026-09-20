package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Native menu contracts shared by the registered Home25/newfeedhome save cards. */
internal class HomeSaveCardLongPressBinding private constructor(
    val onLongClick: Method,
    val popupCallback: Method,
    val listenerSetter: Method,
    val savedCleanup: Method,
    val subscriptionCleanup: Method,
    private val savedIndex: Field,
    private val subscriptionIndex: Field,
) {
    fun <T> withRowIndex(card: Any, index: Int, subscription: Boolean, action: () -> T): T {
        require(index >= 0)
        val field = if (subscription) subscriptionIndex else savedIndex
        val previous = field.getInt(card)
        field.setInt(card, index)
        return try {
            action()
        } finally {
            field.setInt(card, previous)
        }
    }

    companion object {
        fun resolve(card: Class<*>, row: Class<*>, view: Class<*>): HomeSaveCardLongPressBinding? {
            fun method(name: String, matches: (Method) -> Boolean): Method? =
                card.declaredMethods.singleOrNull {
                    it.name == name && !Modifier.isStatic(it.modifiers) && !it.isBridge &&
                        it.returnType == Void.TYPE && matches(it)
                }?.apply { isAccessible = true }

            fun index(name: String): Field? = card.declaredFields.singleOrNull {
                it.name == name && it.type == Int::class.javaPrimitiveType && !Modifier.isStatic(it.modifiers)
            }?.apply { isAccessible = true }

            fun cleanup(name: String) = method(name) {
                it.parameterTypes.size == 3 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes[1] == row && view.isAssignableFrom(it.parameterTypes[2])
            }

            val listenerSetter = row.declaredMethods.singleOrNull {
                it.name == "setOnLongClickWithCoordinate" && !Modifier.isStatic(it.modifiers) &&
                    it.returnType == Void.TYPE && it.parameterTypes.size == 1 &&
                    it.parameterTypes[0].isInterface && it.parameterTypes[0].isAssignableFrom(card)
            }?.apply { isAccessible = true } ?: return null
            return HomeSaveCardLongPressBinding(
                onLongClick = method("onLongClick") {
                    it.parameterTypes.contentEquals(arrayOf(view, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType))
                } ?: return null,
                popupCallback = method("removeLongClickEvent") {
                    it.parameterTypes.size == 2 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                        it.parameterTypes[1].simpleName == "HorizontalScrollViewGroup" &&
                        view.isAssignableFrom(it.parameterTypes[1])
                } ?: return null,
                listenerSetter = listenerSetter,
                savedCleanup = cleanup("removeSavePopupWindow") ?: return null,
                subscriptionCleanup = cleanup("removeClearPopupWindow") ?: return null,
                savedIndex = index("currentIndex") ?: return null,
                subscriptionIndex = index("linkCurrentIndex") ?: return null,
            )
        }
    }
}
