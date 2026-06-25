package com.xiyunmn.puredupan.hook.host

import android.content.Context

internal interface HostDeviceFingerprintCollector {
    fun collect(context: Context): Map<String, Any?>
}
