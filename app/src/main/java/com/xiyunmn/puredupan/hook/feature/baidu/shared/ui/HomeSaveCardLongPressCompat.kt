package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

import android.view.View
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.XposedCompat
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.WeakHashMap

/** Binds only rows created/reparented by the vertical save-card layout. All calls run on the UI thread. */
internal object HomeSaveCardLongPressCompat {
    private val bindings = mutableMapOf<Pair<Class<*>, Class<*>>, HomeSaveCardLongPressBinding?>()
    private val installed = mutableSetOf<Method>()
    private val rows = WeakHashMap<View, Row>()
    private val activePopups = WeakHashMap<Any, Row>()
    private val pendingPopup = ThreadLocal<Pair<Any, Row>?>()

    private class Row(card: Any, view: View, content: View, val index: Int, val subscription: Boolean,
                      val binding: HomeSaveCardLongPressBinding) {
        val card = WeakReference(card)
        val view = WeakReference(view)
        val content = WeakReference(content)
        var clearing = false
    }

    fun bind(card: View, row: View, index: Int, subscription: Boolean) {
        if (rows[row]?.let { it.card.get() === card && it.index == index && it.subscription == subscription } == true) return
        val binding = bindings.getOrPut(card.javaClass to row.javaClass) {
            HomeSaveCardLongPressBinding.resolve(card.javaClass, row.javaClass, View::class.java)
        } ?: return
        val contentName = if (subscription) "update_root" else when (index) {
            1 -> "fh_cl_two"
            2 -> "fh_cl_three"
            else -> "fh_cl_one" // Extra rows are inflated from the first native row layout.
        }
        val id = row.resources.getIdentifier(contentName, "id", row.context.packageName)
        val content = row.findViewById<View>(id) ?: return
        val cleanup = if (subscription) binding.subscriptionCleanup else binding.savedCleanup
        if (!cleanup.parameterTypes[2].isInstance(content)) return
        runCatching {
            install(binding)
            if (binding.onLongClick !in installed || binding.popupCallback !in installed) return
            binding.listenerSetter.invoke(row, card)
            row.tag = index.toString()
            rows[row] = Row(card, row, content, index, subscription, binding)
            row.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    val target = rows[v] ?: return
                    val owner = target.card.get() ?: return
                    if (activePopups[owner] === target) dismiss(owner)
                }
            })
        }.onFailure { XposedCompat.logW("[HomeSaveCardLongPressCompat] bind failed: ${it.message}") }
    }

    private fun install(binding: HomeSaveCardLongPressBinding) {
        val mod = XposedCompat.module ?: return
        // Install cleanup first so a partially installed long-click hook cannot leave a highlighted row.
        if (binding.popupCallback !in installed) {
            mod.hook(binding.popupCallback).intercept { chain ->
                val card = chain.thisObject ?: return@intercept chain.proceed()
                val remove = chain.args[0] as? Boolean ?: return@intercept chain.proceed()
                val target = if (remove) activePopups[card] else
                    pendingPopup.get()?.takeIf { it.first === card }?.second
                if (target == null) return@intercept chain.proceed()
                updatePopup(card, target, remove)
                null
            }
            installed += binding.popupCallback
        }
        if (binding.onLongClick !in installed) {
            mod.hook(binding.onLongClick).intercept { chain ->
                val card = chain.thisObject ?: return@intercept chain.proceed()
                val view = chain.args.firstOrNull() as? View ?: return@intercept chain.proceed()
                val target = rows[view]
                if (!isEnabled() || target == null || target.card.get() !== card) return@intercept chain.proceed()
                if (!view.isShown) return@intercept null
                dismiss(card)
                val previous = pendingPopup.get()
                pendingPopup.set(card to target)
                try {
                    // The native method validates row.tag, then captures the actual item/index in its menu data.
                    // Restore carousel state immediately; delayed dismiss callbacks use activePopups instead.
                    target.binding.withRowIndex(card, target.index, target.subscription) { chain.proceed() }
                } catch (error: Throwable) {
                    dismiss(card)
                    throw error
                } finally {
                    if (previous == null) pendingPopup.remove() else pendingPopup.set(previous)
                }
            }
            installed += binding.onLongClick
        }
    }

    /** Data rebinding must close a menu that still refers to the previous item at this row index. */
    fun dismiss(card: Any) {
        activePopups[card]?.let { updatePopup(card, it, true) }
    }

    private fun updatePopup(card: Any, target: Row, remove: Boolean) {
        if (target.clearing) return // Native dismissal can synchronously re-enter its cleanup callback.
        val row = target.view.get()
        val content = target.content.get()
        if (row == null || content == null) {
            if (activePopups[card] === target) activePopups.remove(card)
            return
        }
        if (remove) target.clearing = true else activePopups[card] = target
        try {
            val cleanup = if (target.subscription) target.binding.subscriptionCleanup else target.binding.savedCleanup
            cleanup.invoke(card, remove, row, content)
        } catch (error: Exception) {
            XposedCompat.logW("[HomeSaveCardLongPressCompat] popup cleanup failed: ${error.message}")
        } finally {
            if (remove) {
                if (activePopups[card] === target) activePopups.remove(card)
                target.clearing = false
            }
        }
    }

    private fun isEnabled() = HookSettings.isHomeCustomizeEnabled &&
        HookSettings.isHomeSaveVerticalLayoutEnabled && !HookSettings.isHomeSaveSectionHidden
}
