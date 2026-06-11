package com.xiyunmn.puredupan.hook.ui

import android.view.View

object AboutInfoManager {
    data class AboutItem(
        val title: String,
        val description: String,
        val url: String?,
        val onClickListener: View.OnClickListener? = null,
    )

    fun loadCachedItemsForSettings(): List<AboutItem> {
        return emptyList()
    }
}
