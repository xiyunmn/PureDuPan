package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

/** A snapshot may only supply the tail of the same ordered native history. */
internal object SavedHistorySnapshotRules {
    const val MAX_ITEMS = 10
    const val MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L

    fun isFresh(writtenAt: Long, now: Long): Boolean =
        writtenAt > 0 && now >= writtenAt && now - writtenAt <= MAX_AGE_MILLIS

    fun <T> extend(
        nativeItems: List<T>,
        snapshot: List<T>,
        limit: Int,
        identity: (T) -> Any?,
    ): List<T>? {
        if (nativeItems.isEmpty() || snapshot.size < nativeItems.size || snapshot.size > MAX_ITEMS) {
            return null
        }
        val keys = snapshot.map { identity(it) ?: return null }
        if (keys.distinct().size != keys.size) return null
        if (nativeItems.indices.any { identity(nativeItems[it]) != keys[it] }) return null
        // 首段必须保留宿主最新对象（名称、路径、缩略图、删除标记等），不被缓存覆盖。
        return (nativeItems + snapshot.drop(nativeItems.size)).take(limit.coerceIn(1, MAX_ITEMS))
    }

    fun <T> refreshed(
        nativeItems: List<T>, fresh: List<T>, limit: Int, nativeUnchanged: Boolean, identity: (T) -> Any?,
    ): List<T>? {
        extend(nativeItems, fresh, limit, identity)?.let { return it }
        // 真实网络结果可替换旧缓存；请求期间若原生已收到另一批数据，则不能反向覆盖。
        return if (nativeUnchanged) extend(fresh, fresh, limit, identity) else null
    }
}
