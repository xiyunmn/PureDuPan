package com.xiyunmn.puredupan.hook.ui

import android.view.View

object AboutInfoManager {
    data class AboutItem(
        val title: String,
        val description: String,
        val url: String?,
        val onClickListener: View.OnClickListener? = null,
        val actionBadgeText: String? = null,
        val onActionBadgeClick: View.OnClickListener? = null,
        val secondaryActionBadgeText: String? = null,
        val onSecondaryActionBadgeClick: View.OnClickListener? = null,
    )

    fun loadCachedItemsForSettings(): List<AboutItem> {
        return emptyList()
    }
}
