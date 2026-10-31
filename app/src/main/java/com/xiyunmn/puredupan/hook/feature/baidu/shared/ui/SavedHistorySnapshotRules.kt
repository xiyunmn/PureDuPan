package com.xiyunmn.puredupan.hook.feature.baidu.shared.ui

/** 快照用于补齐同一有序历史列表的尾部。 */
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
        // 首段保留宿主最新条目，避免缓存覆盖名称、路径和删除状态。
        return (nativeItems + snapshot.drop(nativeItems.size)).take(limit.coerceIn(1, MAX_ITEMS))
    }

    fun <T> refreshed(
        nativeItems: List<T>, fresh: List<T>, limit: Int, nativeUnchanged: Boolean, identity: (T) -> Any?,
    ): List<T>? {
        extend(nativeItems, fresh, limit, identity)?.let { return it }
        // 请求期间原生数据已变化时，丢弃旧网络结果。
        return if (nativeUnchanged) extend(fresh, fresh, limit, identity) else null
    }
}
