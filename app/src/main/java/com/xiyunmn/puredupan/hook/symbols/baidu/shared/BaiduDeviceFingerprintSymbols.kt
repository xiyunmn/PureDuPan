package com.xiyunmn.puredupan.hook.symbols.baidu.shared

internal object BaiduDeviceFingerprintSymbols {
    const val APP_COMMON = "com.baidu.netdisk.kernel.architecture.AppCommon"
    const val DEVICE_ID = "com.baidu.android.common.util.DeviceId"
    const val COMMON_PARAM = "com.baidu.android.common.util.CommonParam"
    const val SOFIRE_FH = "com.baidu.sofire.ac.FH"
    const val SWAN_UUID = "com.baidu.swan.uuid.SwanUUID"

    val appCommonStringFields: List<Pair<String, String>> = listOf(
        "appCommonAndroidId" to "sAndroidId",
        "appCommonOaid" to "OAID",
        "appCommonHonorOaid" to "HONOR_OAID",
        "appCommonDevuid" to "DEVUID",
        "appCommonPdevuid" to "PDEVUID",
        "appCommonC3Aid" to "C3_AID",
        "appCommonDeviceId" to "sDeviceId",
        "appCommonImei" to "sImei",
        "appCommonImsi" to "sImsi",
        "appCommonMac" to "sMac",
        "appCommonChannel" to "CHANNEL_NUM",
    )
}
