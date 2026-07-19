package com.xiyunmn.puredupan.hook.feature.baidu.intl.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.xiyunmn.puredupan.hook.config.runtime.HookSettings
import com.xiyunmn.puredupan.hook.core.HookState
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.feature.baidu.shared.runtime.BaiduFeatureRuntime
import com.xiyunmn.puredupan.hook.symbols.baidu.intl.BaiduIntlHomeSaveCardHookPoints
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

internal object IntlHomeSaveCardCustomizeHook {
    private enum class StateListSlot(val legacySuffix: String) {
        SAVED(".SavedDataInfo"),
        SUBSCRIPTION(".UpdatedDataInfo"),
    }

    private val hookState = HookState()
    private val verticalGroups = Collections.synchronizedMap(WeakHashMap<ViewGroup, Boolean>())
    private val dynamicRows =
        Collections.synchronizedMap(WeakHashMap<ViewGroup, MutableList<View>>())
    private val rowHeights = Collections.synchronizedMap(WeakHashMap<ViewGroup, Int>())
    private val saveHistoryRequests = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val saveHistoryCache = Collections.synchronizedMap(WeakHashMap<Any, List<Any>>())
    private val subscriptionItemsForCall = ThreadLocal<List<*>?>()
    private const val SAVED_HISTORY_CACHE_KEY_PREFIX = "intl_saved_history_v1_"

    fun hook(cl: ClassLoader) {
        if (!isEnabled() || !hookState.markInstalled()) return
        try {
            val count = hookSaveCardView(cl) + hookSubscriptionResponse(cl)
            if (count == 0) {
                hookState.reset()
                XposedCompat.log("[IntlHomeSaveCardCustomizeHook] no hooks installed")
                return
            }
            XposedCompat.log("[IntlHomeSaveCardCustomizeHook] hooks INSTALLED: count=$count")
        } catch (t: Throwable) {
            hookState.reset()
            XposedCompat.logW("[IntlHomeSaveCardCustomizeHook] install failed: ${t.message}")
        }
    }

    private fun hookSaveCardView(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        var count = 0
        val hookedStateClasses = mutableSetOf<Class<*>>()
        val classNames = BaiduFeatureRuntime.currentHomeCustomizeHookPoints().saveCardViewClassNames
        classNames.distinct().forEach { className ->
            val clazz = XposedCompat.findClassOrNull(className, cl) ?: return@forEach
            clazz.declaredMethods.filter(::isStateRenderMethod).forEach { method ->
                val stateClass = method.parameterTypes[0]
                if (hookedStateClasses.add(stateClass)) {
                    count += hookStateCopyMethods(mod, stateClass)
                }
                method.isAccessible = true
                mod.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isEnabled()) {
                        applyVerticalLayout(
                            cardView = chain.thisObject as? ViewGroup,
                            state = chain.args.firstOrNull(),
                        )
                    }
                    result
                }
                count++
            }
            val tabLayoutClassName = className.replace(
                ".ui.view.fragment.NewHomeSaveCardView",
                ".ui.view.view.NewHomeSaveCardTabLayout",
            )
            XposedCompat.findClassOrNull(tabLayoutClassName, cl)?.declaredMethods
                ?.filter { method ->
                    method.name == "selectTab" &&
                        method.returnType == Void.TYPE &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0].name.endsWith(".TabLayout\$Tab")
                }
                ?.forEach { method ->
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val result = chain.proceed()
                        if (isEnabled()) {
                            findAncestorCardView(chain.thisObject as? View, clazz)
                                ?.let(::restoreContentAreaHeight)
                        }
                        result
                    }
                    count++
                }
            val groupClassName = className.replace(
                ".ui.view.fragment.NewHomeSaveCardView",
                ".ui.view.view.HorizontalScrollViewGroup",
            )
            XposedCompat.findClassOrNull(groupClassName, cl)?.let { groupClass ->
                listOf("onInterceptTouchEvent", "onTouchEvent").forEach { methodName ->
                    XposedCompat.findMethodOrNull(groupClass, methodName, MotionEvent::class.java)?.let { method ->
                        mod.hook(method).intercept { chain ->
                            val group = chain.thisObject as? ViewGroup
                            if (group != null && verticalGroups.containsKey(group)) {
                                false
                            } else {
                                chain.proceed()
                            }
                        }
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun hookSubscriptionResponse(cl: ClassLoader): Int {
        val mod = XposedCompat.module ?: return 0
        val points = BaiduFeatureRuntime.currentHomeCustomizeHookPoints()
        var count = 0
        points.saveCardViewModelClassNames.distinct().forEach { viewModelClassName ->
            val viewModelClass = XposedCompat.findClassOrNull(viewModelClassName, cl) ?: return@forEach
            val observerClasses = buildList {
                XposedCompat.findClassOrNull(
                    viewModelClassName + BaiduIntlHomeSaveCardHookPoints.UPDATE_OBSERVER_SUFFIX,
                    cl,
                )?.let(::add)
                addAll(viewModelClass.declaredClasses)
            }.distinct().filter { nested ->
                nested.declaredFields.any { field -> field.type == viewModelClass } &&
                    nested.declaredMethods.any(::isObserverChangedMethod)
            }
            observerClasses.forEach { observerClass ->
                observerClass.declaredMethods.filter(::isObserverChangedMethod).forEach { method ->
                    method.isAccessible = true
                    mod.hook(method).intercept { chain ->
                        val subscriptionItems = if (isEnabled()) {
                            readSubscriptionItems(chain.args.firstOrNull())
                        } else {
                            null
                        }
                        subscriptionItemsForCall.set(subscriptionItems)
                        try {
                            chain.proceed()
                        } finally {
                            subscriptionItemsForCall.remove()
                        }
                    }
                    count++
                }
            }
        }
        return count
    }

    private fun isObserverChangedMethod(method: Method): Boolean {
        return method.name == "onChanged" &&
            method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1
    }

    private fun isStateRenderMethod(method: Method): Boolean {
        return method.returnType == Void.TYPE &&
            method.parameterTypes.size == 1 &&
            isSaveCardUiStateClass(method.parameterTypes[0])
    }

    private fun isSaveCardUiStateClass(clazz: Class<*>): Boolean {
        if (clazz.isPrimitive || clazz == Any::class.java) return false
        val fields = clazz.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        val listCount = fields.count { List::class.java.isAssignableFrom(it.type) }
        val hasEnum = fields.any { it.type.isEnum }
        val hasBoolean = fields.any { it.type == Boolean::class.javaPrimitiveType }
        val hasCopy = clazz.declaredMethods.any { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == clazz &&
                method.parameterTypes.size >= 4 &&
                method.parameterTypes.count { List::class.java.isAssignableFrom(it) } >= 2 &&
                method.parameterTypes.any { it == Boolean::class.javaPrimitiveType }
        }
        return listCount >= 2 && hasEnum && hasBoolean && hasCopy
    }

    private fun hookStateCopyMethods(
        mod: io.github.libxposed.api.XposedModule,
        stateClass: Class<*>,
    ): Int {
        val methods = stateClass.declaredMethods.filter { method ->
            method.returnType == stateClass &&
                method.parameterTypes.count { List::class.java.isAssignableFrom(it) } == 2
        }
        methods.forEach { method ->
            method.isAccessible = true
            mod.hook(method).intercept { chain ->
                val subscriptionItems = subscriptionItemsForCall.get()
                if (isEnabled() && subscriptionItems != null) {
                    val args = chain.args.toTypedArray()
                    val listIndexes = method.parameterTypes.indices.filter { index ->
                        List::class.java.isAssignableFrom(method.parameterTypes[index])
                    }
                    listIndexes.getOrNull(1)?.let { index ->
                        args[index] = subscriptionItems
                    }
                    XposedCompat.logD {
                        "[IntlHomeSaveCardCustomizeHook] subscription state copy: " +
                            "method=${method.name}, visible=${subscriptionItems.size}"
                    }
                    return@intercept chain.proceed(args)
                }
                chain.proceed()
            }
        }
        return methods.size
    }

    private fun applyVerticalLayout(cardView: ViewGroup?, state: Any?) {
        if (cardView == null || state == null) return
        val saveGroup = findHostView<ViewGroup>(
            cardView,
            BaiduIntlHomeSaveCardHookPoints.SAVE_GROUP_ID,
        )
        val subscriptionGroup = findHostView<ViewGroup>(
            cardView,
            BaiduIntlHomeSaveCardHookPoints.SUBSCRIPTION_GROUP_ID,
        )
        verticalizeGroup(
            group = saveGroup,
            rowIdNames = BaiduIntlHomeSaveCardHookPoints.SAVE_ROW_IDS,
            innerIdNames = BaiduIntlHomeSaveCardHookPoints.SAVE_INNER_IDS,
        )
        verticalizeGroup(
            group = subscriptionGroup,
            rowIdNames = BaiduIntlHomeSaveCardHookPoints.SUBSCRIPTION_ROW_IDS,
            innerIdNames = listOf("update_root"),
        )

        updateRows(
            cardView = cardView,
            group = saveGroup,
            items = readStateList(state, StateListSlot.SAVED),
            isSubscription = false,
        )
        updateRows(
            cardView = cardView,
            group = subscriptionGroup,
            items = readStateList(state, StateListSlot.SUBSCRIPTION),
            isSubscription = true,
        )
        restoreContentAreaHeight(cardView)
        enforceWrapContentHeight(saveGroup)
        enforceWrapContentHeight(subscriptionGroup)

        findViewModel(cardView)?.let { viewModel ->
            expandSavedItems(viewModel, cardView.context, cardView.javaClass.classLoader)
        }
    }

    private fun findAncestorCardView(view: View?, cardViewClass: Class<*>): ViewGroup? {
        var current: Any? = view
        while (current is View) {
            if (cardViewClass.isInstance(current)) return current as? ViewGroup
            current = current.parent
        }
        return null
    }

    private fun restoreContentAreaHeight(cardView: ViewGroup) {
        val contentArea = findHostView<ViewGroup>(
            cardView,
            BaiduIntlHomeSaveCardHookPoints.CONTENT_AREA_ID,
        ) ?: return
        val groups = listOfNotNull(
            findHostView<ViewGroup>(cardView, BaiduIntlHomeSaveCardHookPoints.SAVE_GROUP_ID),
            findHostView<ViewGroup>(cardView, BaiduIntlHomeSaveCardHookPoints.SUBSCRIPTION_GROUP_ID),
        )
        val visibleGroup = groups.firstOrNull {
            it.visibility == View.VISIBLE && verticalGroups.containsKey(it)
        } ?: return
        groups.forEach { group ->
            enforceWrapContentHeight(group)
            enforceWrapContentHeight(group.getChildAt(0))
        }

        val wrapper = visibleGroup?.getChildAt(0) as? ViewGroup
        val visibleRows = wrapper?.childrenList().orEmpty().filter { it.visibility == View.VISIBLE }
        val fallbackRowHeight = visibleGroup?.let(rowHeights::get)
        val requiredHeight = visibleRows.sumOf { row ->
            row.layoutParams?.height?.takeIf { it > 0 } ?: fallbackRowHeight ?: 0
        }
        val params = contentArea.layoutParams ?: return
        params.height = requiredHeight.takeIf { it > 0 } ?: ViewGroup.LayoutParams.WRAP_CONTENT
        contentArea.layoutParams = params
        wrapper?.requestLayout()
        visibleGroup?.requestLayout()
        contentArea.requestLayout()
        cardView.requestLayout()
        XposedCompat.logD {
            "[IntlHomeSaveCardCustomizeHook] tab content height restored: " +
                "rows=${visibleRows.size}, height=${params.height}"
        }
    }

    private fun verticalizeGroup(
        group: ViewGroup?,
        rowIdNames: List<String>,
        innerIdNames: List<String>,
    ) {
        if (group == null || verticalGroups.containsKey(group)) return
        val rows = rowIdNames.mapNotNull { findHostView<View>(group, it) }
        if (rows.isEmpty()) return
        val rowHeight = group.layoutParams?.height?.takeIf { it > 0 }
            ?: rows.firstNotNullOfOrNull { it.layoutParams?.height?.takeIf { height -> height > 0 } }
            ?: return
        val wrapper = LinearLayout(group.context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        rows.forEach { row ->
            (row.parent as? ViewGroup)?.removeView(row)
            row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rowHeight)
            wrapper.addView(row)
        }
        innerIdNames.forEach { idName ->
            findHostView<View>(wrapper, idName)?.let { inner ->
                inner.layoutParams = inner.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                }
            }
        }
        group.removeAllViews()
        group.addView(
            wrapper,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        enforceWrapContentHeight(group)
        verticalGroups[group] = true
        rowHeights[group] = rowHeight
        group.scrollTo(0, 0)
    }

    private fun updateRows(
        cardView: ViewGroup,
        group: ViewGroup?,
        items: List<*>,
        isSubscription: Boolean,
    ) {
        if (group == null || !verticalGroups.containsKey(group)) return
        val wrapper = group.getChildAt(0) as? LinearLayout ?: return
        dynamicRows.remove(group)?.forEach { row ->
            (row.parent as? ViewGroup)?.removeView(row)
        }
        val visibleCount = minOf(HookSettings.homeSaveItemLimit.coerceIn(1, 10), items.size)
        for (index in 0 until minOf(3, wrapper.childCount)) {
            wrapper.getChildAt(index).visibility = if (index < visibleCount) View.VISIBLE else View.GONE
        }
        val createdRows = mutableListOf<View>()
        for (index in 3 until visibleCount) {
            val item = items[index] ?: continue
            val row = inflateRow(cardView, isSubscription) ?: break
            row.tag = index.toString()
            row.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                rowHeights[group] ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val bound = if (isSubscription) {
                bindSubscriptionRow(cardView, row, item, index)
            } else {
                bindSavedRow(cardView, row, item)
            }
            if (!bound) break
            wrapper.addView(row)
            createdRows += row
        }
        if (createdRows.isNotEmpty()) dynamicRows[group] = createdRows
        group.requestLayout()
        XposedCompat.logD {
            "[IntlHomeSaveCardCustomizeHook] rows updated: " +
                "subscription=$isSubscription, source=${items.size}, visible=$visibleCount"
        }
    }

    private fun inflateRow(cardView: ViewGroup, isSubscription: Boolean): View? {
        val layoutId = cardView.resources.getIdentifier(
            BaiduIntlHomeSaveCardHookPoints.SAVE_CARD_LAYOUT,
            "layout",
            cardView.context.packageName,
        )
        if (layoutId == 0) return null
        val template = LayoutInflater.from(cardView.context).inflate(layoutId, null, false)
        val rowId = if (isSubscription) "link_root_one_layout" else "root_one_layout"
        val row = findHostView<View>(template, rowId) ?: return null
        (row.parent as? ViewGroup)?.removeView(row)
        row.visibility = View.VISIBLE
        return row
    }

    private fun bindSavedRow(cardView: ViewGroup, row: View, item: Any): Boolean {
        val method = cardView.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 7 &&
                View::class.java.isAssignableFrom(candidate.parameterTypes[0]) &&
                candidate.parameterTypes[6].isInstance(item)
        }?.apply { isAccessible = true } ?: return false
        val constraint = findHostView<View>(row, "fh_cl_one") ?: return false
        val args = arrayOf(
            constraint,
            findHostView<View>(row, "fh_one_img"),
            findHostView<View>(row, "fh_one_play"),
            findHostView<View>(row, "fh_one_title"),
            findHostView<View>(row, "fh_one_time"),
            findHostView<View>(row, "fh_one_to_path"),
            item,
        )
        return runCatching {
            method.invoke(cardView, *args)
            constraint.setOnClickListener { openSavedItem(cardView, item) }
            constraint.layoutParams = constraint.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            true
        }.getOrDefault(false)
    }

    private fun bindSubscriptionRow(
        cardView: ViewGroup,
        row: View,
        item: Any,
        index: Int,
    ): Boolean {
        val method = cardView.javaClass.declaredMethods.firstOrNull { candidate ->
            candidate.returnType == Void.TYPE &&
                candidate.parameterTypes.size == 2 &&
                isViewBindingType(candidate.parameterTypes[0]) &&
                candidate.parameterTypes[1].isInstance(item)
        }?.apply { isAccessible = true } ?: return false
        val updateRoot = findHostView<View>(row, "update_root") ?: return false
        val bindingType = method.parameterTypes[0]
        val bindMethod = bindingType.declaredMethods.firstOrNull { candidate ->
            Modifier.isStatic(candidate.modifiers) &&
                candidate.returnType == bindingType &&
                candidate.parameterTypes.contentEquals(arrayOf(View::class.java))
        }?.apply { isAccessible = true } ?: return false
        return runCatching {
            method.invoke(cardView, bindMethod.invoke(null, updateRoot), item)
            (cardView as? View.OnClickListener)?.let(row::setOnClickListener)
            findHostView<View>(row, "update_file_root")?.setOnClickListener {
                invokeIntMethod(cardView, "updateFilePreview", index)
            }
            findHostView<View>(row, "update_path")?.setOnClickListener {
                invokeIntMethod(cardView, "updateFilePtahPreview", index)
            }
            findHostView<View>(row, "to_save")?.setOnClickListener {
                invokeIntMethod(cardView, "updateFileTransfer", index)
            }
            updateRoot.layoutParams = updateRoot.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
            }
            true
        }.getOrDefault(false)
    }

    private fun openSavedItem(cardView: ViewGroup, item: Any) {
        runCatching {
            val viewModel = findViewModel(cardView) ?: return
            val activity = findActivity(cardView.context) ?: return
            val previewMethod = viewModel.javaClass.declaredMethods.firstOrNull { method ->
                method.returnType == Void.TYPE &&
                    method.parameterTypes.size == 2 &&
                    Activity::class.java.isAssignableFrom(method.parameterTypes[0]) &&
                    method.parameterTypes[1].isInstance(item)
            }?.apply { isAccessible = true } ?: return
            previewMethod.invoke(viewModel, activity, item)
        }
    }

    private fun invokeIntMethod(owner: Any, methodName: String, value: Int) {
        runCatching {
            val method = owner.javaClass.declaredMethods.firstOrNull { candidate ->
                candidate.name == methodName &&
                    candidate.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
            }?.apply { isAccessible = true } ?: return
            method.invoke(owner, value)
        }
    }

    private tailrec fun findActivity(context: Context?): Activity? {
        return when (context) {
            is Activity -> context
            is ContextWrapper -> findActivity(context.baseContext)
            else -> null
        }
    }

    private fun readSubscriptionItems(result: Any?): List<*>? {
        if (result == null) return null
        return runCatching {
            val source = extractNestedList(result) ?: return@runCatching null
            val limited = source.filter { item ->
                val status = item?.let { value ->
                    invokeNoArg(value, "getStatus") ?: readSerializedField(value, "status")
                } as? Number
                status?.toInt() == 0
            }.take(HookSettings.homeSaveItemLimit.coerceIn(1, 10))
            XposedCompat.logD {
                "[IntlHomeSaveCardCustomizeHook] subscription response: " +
                    "source=${source.size}, visible=${limited.size}"
            }
            limited
        }.onFailure { error ->
            XposedCompat.logW("[IntlHomeSaveCardCustomizeHook] subscription response read failed: ${error.message}")
        }.getOrNull()
    }

    private fun expandSavedItems(viewModel: Any, context: Context, cl: ClassLoader?) {
        cl ?: return
        val stateFlow = findStateFlow(viewModel) ?: return
        val current = invokeNoArg(stateFlow, "getValue") ?: return
        val currentList = readStateList(current, StateListSlot.SAVED)
        val limit = HookSettings.homeSaveItemLimit.coerceIn(1, 10)
        if (currentList.size >= limit) return
        saveHistoryCache[viewModel]?.let { cached ->
            updateStateList(stateFlow, cached.take(limit), StateListSlot.SAVED)
            return
        }
        if (saveHistoryRequests.put(viewModel, true) == true) return

        var observing = false
        runCatching {
            val targetClass = currentList.firstNotNullOfOrNull { it?.javaClass }
                ?: error("saved item class unavailable")
            val credentials = resolveAccountCredentials(cl)
            readPersistentSavedCache(context, credentials.uid, targetClass)?.let { cached ->
                val merged = mergeSavedItems(currentList, cached, limit)
                if (merged.size > currentList.size) {
                    saveHistoryCache[viewModel] = merged
                    updateStateList(stateFlow, merged, StateListSlot.SAVED)
                    XposedCompat.logD {
                        "[IntlHomeSaveCardCustomizeHook] saved history cache applied: " +
                            "native=${currentList.size}, cached=${cached.size}, visible=${merged.size}"
                    }
                }
            }
            val uid = credentials.uid
            val bduss = credentials.bduss
            val managerClass = XposedCompat.findClassOrNull(
                BaiduIntlHomeSaveCardHookPoints.TRANSFER_SAVED_MANAGER,
                cl,
            ) ?: error("TransferSavedManager unavailable")
            val handlableClass = XposedCompat.findClassOrNull(
                BaiduIntlHomeSaveCardHookPoints.HANDLABLE_MANAGER,
                cl,
            ) ?: error("HandlableManager unavailable")
            val constructor = managerClass.declaredConstructors.firstOrNull { constructor ->
                constructor.parameterTypes.size == 2 &&
                    Context::class.java.isAssignableFrom(constructor.parameterTypes[0])
            }?.apply { isAccessible = true } ?: error("transfer saved constructor unavailable")
            val executorType = constructor.parameterTypes[1]
            val executor = handlableClass.declaredMethods.firstOrNull { method ->
                Modifier.isStatic(method.modifiers) &&
                    method.parameterTypes.isEmpty() &&
                    executorType.isAssignableFrom(method.returnType)
            }?.apply { isAccessible = true }?.invoke(null) ?: error("executor unavailable")
            val service = constructor.newInstance(context.applicationContext, executor)
                ?: error("transfer saved service unavailable")
            val fetch = managerClass.declaredMethods.firstOrNull { method ->
                method.parameterTypes.size == 4 &&
                    method.parameterTypes.drop(1).all { it == Int::class.javaPrimitiveType } &&
                    method.parameterTypes[0].declaredConstructors.any { evidenceConstructor ->
                        evidenceConstructor.parameterTypes.contentEquals(
                            arrayOf(String::class.java, String::class.java),
                        )
                    }
            }?.apply { isAccessible = true } ?: error("fetchSavedList unavailable")
            val evidenceClass = fetch.parameterTypes[0]
            val evidence = evidenceClass.getDeclaredConstructor(String::class.java, String::class.java)
                .newInstance(uid, bduss)
            val liveData = fetch.invoke(service, evidence, 1, limit, 0)
                ?: error("saved history LiveData unavailable")
            observing = observeSavedHistory(
                liveData = liveData,
                cl = cl,
                viewModel = viewModel,
                stateFlow = stateFlow,
                targetClass = targetClass,
                limit = limit,
                context = context,
                uid = uid,
            )
            if (observing) {
                XposedCompat.logD(
                    "[IntlHomeSaveCardCustomizeHook] saved history requested: limit=$limit",
                )
            }
        }.onFailure { error ->
            XposedCompat.logW("[IntlHomeSaveCardCustomizeHook] saved history request failed: ${error.message}")
        }
        if (!observing) saveHistoryRequests.remove(viewModel)
    }

    private fun observeSavedHistory(
        liveData: Any,
        cl: ClassLoader,
        viewModel: Any,
        stateFlow: Any,
        targetClass: Class<*>,
        limit: Int,
        context: Context,
        uid: String,
    ): Boolean {
        val observerClass = XposedCompat.findClassOrNull(
            BaiduIntlHomeSaveCardHookPoints.ANDROIDX_OBSERVER,
            cl,
        ) ?: return false
        val observeForever = liveData.javaClass.methods.firstOrNull { method ->
            method.name == "observeForever" && method.parameterTypes.contentEquals(arrayOf(observerClass))
        } ?: return false
        val removeObserver = liveData.javaClass.methods.firstOrNull { method ->
            method.name == "removeObserver" && method.parameterTypes.contentEquals(arrayOf(observerClass))
        }
        lateinit var observer: Any
        observer = Proxy.newProxyInstance(
            observerClass.classLoader,
            arrayOf(observerClass),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "onChanged" -> {
                        val result = args?.firstOrNull()
                        val history = result?.let(::extractTransferList)
                        val resultName = result?.javaClass?.simpleName.orEmpty()
                        XposedCompat.logD {
                            "[IntlHomeSaveCardCustomizeHook] saved history result: " +
                                "state=$resultName, source=${history?.size}"
                        }
                        if (history != null) {
                            removeObserver?.invoke(liveData, observer)
                            val mapped = history.mapNotNull { source ->
                                source?.let { mapSavedItem(it, targetClass) }
                            }.take(limit)
                            if (mapped.isNotEmpty()) {
                                saveHistoryCache[viewModel] = mapped
                                updateStateList(stateFlow, mapped, StateListSlot.SAVED)
                                writePersistentSavedCache(context, uid, targetClass, mapped)
                                XposedCompat.logD(
                                    "[IntlHomeSaveCardCustomizeHook] saved history applied: " +
                                        "source=${history.size}, visible=${mapped.size}",
                                )
                            } else {
                                XposedCompat.logW(
                                    "[IntlHomeSaveCardCustomizeHook] saved history mapping produced no items: " +
                                        "source=${history.size}",
                                )
                            }
                            saveHistoryRequests.remove(viewModel)
                        } else if (resultName.isNotEmpty() && resultName != "Operating") {
                            removeObserver?.invoke(liveData, observer)
                            saveHistoryRequests.remove(viewModel)
                            val errorNumber = result?.let { invokeNoArg(it, "getErrorNumber") }
                            val errorMessage = result?.let { invokeNoArg(it, "getErrorMessage") }
                            XposedCompat.logW(
                                "[IntlHomeSaveCardCustomizeHook] saved history failed: " +
                                    "state=$resultName, code=$errorNumber, message=$errorMessage",
                            )
                        }
                        null
                    }
                    "equals" -> proxy === args?.firstOrNull()
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "PureDuPanIntlSavedHistoryObserver"
                    else -> null
                }
            },
        )
        observeForever.invoke(liveData, observer)
        return true
    }

    private fun extractTransferList(result: Any): List<*>? {
        return extractNestedList(result, preferredListGetter = "getTransferList")
    }

    private fun mapSavedItem(source: Any, targetClass: Class<*>): Any? {
        return runCatching {
            val (gson, toJson, fromJson) = gsonMethods(targetClass.classLoader) ?: return null
            fromJson.invoke(gson, toJson.invoke(gson, source), targetClass)
        }.getOrNull()
    }

    private fun mergeSavedItems(
        nativeItems: List<*>,
        cachedItems: List<Any>,
        limit: Int,
    ): List<Any> {
        val merged = LinkedHashMap<String, Any>()
        (nativeItems + cachedItems).filterNotNull().forEach { item ->
            val identity = savedItemIdentity(item) ?: "object:${item.hashCode()}:${item}"
            merged.putIfAbsent(identity, item)
        }
        return merged.values.take(limit)
    }

    private fun savedItemIdentity(item: Any): String? {
        val fsId = (invokeNoArg(item, "getFsId") ?: readSerializedField(item, "fs_id")) as? Number
        if (fsId != null && fsId.toLong() != 0L) return "fs:${fsId.toLong()}"
        val path = (invokeNoArg(item, "getPath") ?: readSerializedField(item, "path")) as? String
        return path?.takeIf(String::isNotEmpty)?.let { "path:$it" }
    }

    private fun extractNestedList(
        root: Any,
        preferredListGetter: String = "getData",
    ): List<*>? {
        var current: Any? = root
        repeat(4) {
            val value = current ?: return null
            (invokeNoArg(value, preferredListGetter) as? List<*>)?.let { return it }
            readFirstListField(value)?.let { return it }
            current = invokeNoArg(value, "getData") ?: readFirstObjectField(value)
        }
        return null
    }

    private fun readFirstListField(owner: Any): List<*>? {
        return instanceFields(owner.javaClass).firstNotNullOfOrNull { field ->
            if (!List::class.java.isAssignableFrom(field.type)) return@firstNotNullOfOrNull null
            field.isAccessible = true
            runCatching { field.get(owner) as? List<*> }.getOrNull()
        }
    }

    private fun readFirstObjectField(owner: Any): Any? {
        return instanceFields(owner.javaClass).firstNotNullOfOrNull { field ->
            val type = field.type
            if (
                type.isPrimitive || type.isEnum ||
                CharSequence::class.java.isAssignableFrom(type) ||
                Number::class.java.isAssignableFrom(type) ||
                List::class.java.isAssignableFrom(type)
            ) {
                return@firstNotNullOfOrNull null
            }
            field.isAccessible = true
            runCatching { field.get(owner) }.getOrNull()
        }
    }

    private fun readSerializedField(owner: Any, serializedName: String): Any? {
        return instanceFields(owner.javaClass).firstNotNullOfOrNull { field ->
            val matches = field.declaredAnnotations.any { annotation ->
                if (!annotation.annotationClass.java.name.endsWith(".SerializedName")) return@any false
                runCatching {
                    annotation.annotationClass.java.getMethod("value").invoke(annotation) == serializedName
                }.getOrDefault(false)
            }
            if (!matches) return@firstNotNullOfOrNull null
            field.isAccessible = true
            runCatching { field.get(owner) }.getOrNull()
        }
    }

    private fun instanceFields(clazz: Class<*>): Sequence<java.lang.reflect.Field> {
        return generateSequence(clazz) { current -> current.superclass }
            .flatMap { current -> current.declaredFields.asSequence() }
            .filter { field -> !Modifier.isStatic(field.modifiers) }
    }

    private fun readPersistentSavedCache(
        context: Context,
        uid: String,
        targetClass: Class<*>,
    ): List<Any>? {
        return runCatching {
            val json = HookSettings.getModuleStatePrefs(context)
                .getString(SAVED_HISTORY_CACHE_KEY_PREFIX + uid, null)
                ?: return null
            val gsonMethods = gsonMethods(targetClass.classLoader) ?: return null
            val arrayClass = java.lang.reflect.Array.newInstance(targetClass, 0).javaClass
            (gsonMethods.third.invoke(gsonMethods.first, json, arrayClass) as? Array<*>)
                ?.filterNotNull()
                ?.filter { targetClass.isInstance(it) }
                ?.map { it as Any }
                ?.takeIf { it.isNotEmpty() }
        }.onFailure { error ->
            XposedCompat.logW(
                "[IntlHomeSaveCardCustomizeHook] saved history cache read failed: ${error.message}",
            )
        }.getOrNull()
    }

    private fun writePersistentSavedCache(
        context: Context,
        uid: String,
        targetClass: Class<*>,
        items: List<Any>,
    ) {
        runCatching {
            val gsonMethods = gsonMethods(targetClass.classLoader) ?: return
            val gson = gsonMethods.first
            val toJson = gsonMethods.second
            HookSettings.getModuleStatePrefs(context)
                .edit()
                .putString(SAVED_HISTORY_CACHE_KEY_PREFIX + uid, toJson.invoke(gson, items) as String)
                .apply()
        }.onFailure { error ->
            XposedCompat.logW(
                "[IntlHomeSaveCardCustomizeHook] saved history cache write failed: ${error.message}",
            )
        }
    }

    private fun gsonMethods(classLoader: ClassLoader?): Triple<Any, Method, Method>? {
        val cl = classLoader ?: return null
        val gsonClass = XposedCompat.findClassOrNull(BaiduIntlHomeSaveCardHookPoints.GSON, cl) ?: return null
        val gson = gsonClass.getDeclaredConstructor().newInstance()
        val toJson = gsonClass.methods.firstOrNull { method ->
            method.name == "toJson" && method.parameterTypes.contentEquals(arrayOf(Any::class.java))
        } ?: return null
        val fromJson = gsonClass.methods.firstOrNull { method ->
            method.name == "fromJson" &&
                method.parameterTypes.contentEquals(arrayOf(String::class.java, Class::class.java))
        } ?: return null
        return Triple(gson, toJson, fromJson)
    }

    private data class AccountCredentials(val uid: String, val bduss: String)

    private fun resolveAccountCredentials(cl: ClassLoader): AccountCredentials {
        val accountClass = XposedCompat.findClassOrNull(
            BaiduIntlHomeSaveCardHookPoints.ACCOUNT_UTILS,
            cl,
        ) ?: error("AccountUtils unavailable")
        val account = accountClass.declaredMethods.firstOrNull { method ->
            method.name == "k" && Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() && method.returnType == accountClass
        }?.apply { isAccessible = true }?.invoke(null) ?: accountClass.declaredMethods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) && method.parameterTypes.isEmpty() && method.returnType == accountClass
        }?.apply { isAccessible = true }?.invoke(null) ?: error("AccountUtils instance unavailable")
        val stringGetters = accountClass.declaredMethods.filter { method ->
            !Modifier.isStatic(method.modifiers) && method.parameterTypes.isEmpty() &&
                method.returnType == String::class.java
        }.onEach { it.isAccessible = true }
        val uid = invokeNamedStringGetter(account, stringGetters, listOf("getUid", "x"))
            ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: stringGetters.firstNotNullOfOrNull { method ->
                (method.invoke(account) as? String)?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            }
            ?: error("uid unavailable")
        val bduss = invokeNamedStringGetter(account, stringGetters, listOf("getBduss", "d"))
            ?.takeIf { it.length >= 64 }
            ?: stringGetters.firstNotNullOfOrNull { method ->
                (method.invoke(account) as? String)?.takeIf { it.length >= 64 }
            }
            ?: error("bduss unavailable")
        return AccountCredentials(uid, bduss)
    }

    private fun updateStateList(stateFlow: Any, list: List<*>, slot: StateListSlot) {
        val current = invokeNoArg(stateFlow, "getValue") ?: return
        val currentList = readStateList(current, slot)
        if (currentList == list) return
        val copy = current.javaClass.declaredMethods.firstOrNull { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.returnType == current.javaClass &&
                method.parameterTypes.size == 5 &&
                method.parameterTypes.count { List::class.java.isAssignableFrom(it) } == 2
        }?.apply { isAccessible = true } ?: return
        val fields = current.javaClass.declaredFields.onEach { it.isAccessible = true }
        val state = fields.first { copy.parameterTypes[0].isAssignableFrom(it.type) }.get(current)
        val saveList = if (slot == StateListSlot.SAVED) list else readStateList(current, StateListSlot.SAVED)
        val updateList = if (slot == StateListSlot.SUBSCRIPTION) list else readStateList(current, StateListSlot.SUBSCRIPTION)
        val hasMore = fields.first { it.type == Boolean::class.javaPrimitiveType }.getBoolean(current)
        val extra = fields.firstOrNull { field ->
            !Modifier.isStatic(field.modifiers) && copy.parameterTypes[4].isAssignableFrom(field.type)
        }?.get(current)
        val expanded = copy.invoke(current, state, saveList, updateList, hasMore, extra)
        stateFlow.javaClass.methods.firstOrNull { method ->
            method.name == "setValue" && method.parameterTypes.size == 1
        }?.invoke(stateFlow, expanded)
    }

    private fun readStateList(state: Any, slot: StateListSlot): List<*> {
        val fields = state.javaClass.declaredFields.filter { field ->
            !Modifier.isStatic(field.modifiers) && List::class.java.isAssignableFrom(field.type)
        }
        val field = fields.getOrNull(slot.ordinal)
            ?: fields.firstOrNull { it.genericType.typeName.contains(slot.legacySuffix) }
            ?: fields.firstOrNull { candidate ->
                candidate.isAccessible = true
                val values = candidate.get(state) as? List<*>
                values?.firstOrNull()?.javaClass?.name?.endsWith(slot.legacySuffix) == true
            }
            ?: return emptyList<Any>()
        field.isAccessible = true
        return field.get(state) as? List<*> ?: emptyList<Any>()
    }

    private fun findViewModel(owner: Any): Any? {
        owner.javaClass.declaredMethods.asSequence()
            .filter { method ->
                !Modifier.isStatic(method.modifiers) && method.parameterTypes.isEmpty()
            }
            .forEach { method ->
                method.isAccessible = true
                val candidate = runCatching { method.invoke(owner) }.getOrNull()
                if (candidate != null && findStateFlow(candidate) != null) return candidate
            }
        owner.javaClass.declaredFields.forEach { field ->
            field.isAccessible = true
            val value = runCatching { field.get(owner) }.getOrNull() ?: return@forEach
            if (findStateFlow(value) != null) return value
            val lazyValue = runCatching { invokeNoArg(value, "getValue") }.getOrNull()
            if (lazyValue != null && findStateFlow(lazyValue) != null) return lazyValue
        }
        return null
    }

    private fun findStateFlow(viewModel: Any): Any? {
        return viewModel.javaClass.declaredFields.firstNotNullOfOrNull { field ->
            runCatching {
                field.isAccessible = true
                val value = field.get(viewModel) ?: return@runCatching null
                val current = invokeNoArg(value, "getValue") ?: return@runCatching null
                value.takeIf { isSaveCardUiStateClass(current.javaClass) }
            }.getOrNull()
        }
    }

    private fun isViewBindingType(clazz: Class<*>): Boolean {
        return clazz.declaredMethods.any { method ->
            Modifier.isStatic(method.modifiers) &&
                method.returnType == clazz &&
                method.parameterTypes.size == 1 &&
                View::class.java.isAssignableFrom(method.parameterTypes[0])
        }
    }

    private fun invokeNoArg(owner: Any, methodName: String): Any? {
        val method = (owner.javaClass.methods.asSequence() + owner.javaClass.declaredMethods.asSequence())
            .firstOrNull { candidate ->
                candidate.name == methodName && candidate.parameterTypes.isEmpty()
            } ?: return null
        method.isAccessible = true
        return method.invoke(owner)
    }

    private fun invokeNamedStringGetter(
        owner: Any,
        methods: List<Method>,
        names: List<String>,
    ): String? {
        return names.firstNotNullOfOrNull { name ->
            methods.firstOrNull { it.name == name }?.let { method ->
                runCatching { method.invoke(owner) as? String }.getOrNull()
            }
        }
    }

    private fun enforceWrapContentHeight(view: View?) {
        val params = view?.layoutParams ?: return
        if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            view.layoutParams = params
        }
        view.requestLayout()
    }

    private fun ViewGroup.childrenList(): List<View> {
        return List(childCount, ::getChildAt)
    }

    private inline fun <reified T : View> findHostView(root: View, idName: String): T? {
        val id = root.resources.getIdentifier(idName, "id", root.context.packageName)
        if (id == 0) return null
        return root.findViewById(id) as? T
    }

    private fun isEnabled(): Boolean {
        return HookSettings.isHomeCustomizeEnabled &&
            HookSettings.isHomeSaveVerticalLayoutEnabled &&
            !HookSettings.isHomeSaveSectionHidden
    }
}
