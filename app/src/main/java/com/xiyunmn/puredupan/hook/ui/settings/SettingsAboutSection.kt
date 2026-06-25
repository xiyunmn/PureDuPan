package com.xiyunmn.puredupan.hook.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.xiyunmn.puredupan.hook.BuildConfig
import com.xiyunmn.puredupan.hook.core.XposedCompat
import com.xiyunmn.puredupan.hook.ui.AboutInfoManager
import com.xiyunmn.puredupan.hook.ui.UiStyle
import com.xiyunmn.puredupan.hook.ui.UiText

internal data class SettingsAboutSectionHandle(
    val root: View,
    val refreshDeviceFingerprintDescription: () -> Unit,
)

internal object SettingsAboutSection {
    private const val HOST_SUFFIX_INTL = "_intl"
    private const val HOST_SUFFIX_SAMSUNG = "_samsung"

    fun create(
        context: Context,
        padding: Int,
        hostId: String?,
        versionClickListener: View.OnClickListener?,
        showDeviceFingerprint: () -> Boolean,
    ): SettingsAboutSectionHandle {
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        var deviceFingerprintDescriptionView: TextView? = null
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, padding)

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (12 * density).toInt(),
                )
            })

            addView(TextView(context).apply {
                text = UiText.Settings.ABOUT
                textSize = 12.5f
                letterSpacing = 0.04f
                setTextColor(tokens.accent)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(0, (padding * 0.7f).toInt(), 0, (padding * 0.35f).toInt())
            })

            val aboutItemsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            buildItems(context, hostId, versionClickListener, showDeviceFingerprint).forEach { item ->
                aboutItemsContainer.addView(
                    createItem(
                        context = context,
                        density = density,
                        padding = padding,
                        item = item,
                        onDescriptionCreated = if (item.title == UiText.Settings.DEVICE_FINGERPRINT_LABEL) {
                            { view: TextView -> deviceFingerprintDescriptionView = view }
                        } else {
                            null
                        },
                    ),
                )
            }
            addView(aboutItemsContainer)
        }
        return SettingsAboutSectionHandle(
            root = root,
            refreshDeviceFingerprintDescription = {
                deviceFingerprintDescriptionView?.text =
                    UiText.Settings.deviceFingerprintDesc(showDeviceFingerprint())
            },
        )
    }

    private fun buildItems(
        context: Context,
        hostId: String?,
        versionClickListener: View.OnClickListener?,
        showDeviceFingerprint: () -> Boolean,
    ): List<AboutInfoManager.AboutItem> {
        val versionInfo = buildVersionDisplayInfo(context, hostId)
        return listOf(
            AboutInfoManager.AboutItem(
                UiText.Settings.DEVICE_FINGERPRINT_LABEL,
                UiText.Settings.deviceFingerprintDesc(showDeviceFingerprint()),
                null,
                View.OnClickListener {
                    DeviceFingerprintDialog.show(
                        context = context,
                        allowHiddenDeviceFingerprint = showDeviceFingerprint(),
                    )
                },
            ),
            AboutInfoManager.AboutItem(
                UiText.Settings.VERSION,
                UiText.Settings.aboutVersionSummary(
                    hostName = versionInfo.hostName,
                    hostBuildType = versionInfo.hostBuildType,
                    hostVersion = versionInfo.hostVersion,
                    hostPackageName = versionInfo.hostPackageName,
                    moduleBuildType = versionInfo.moduleBuildType,
                    moduleVersion = versionInfo.moduleVersion,
                ),
                null,
                versionClickListener,
            ),
            AboutInfoManager.AboutItem(
                UiText.Settings.AUTHOR,
                UiText.Settings.AUTHOR_NAME,
                "https://github.com/xiyunmn/PureDuPan",
            ),
        ) + AboutInfoManager.loadCachedItemsForSettings()
    }

    private fun buildVersionDisplayInfo(context: Context, hostId: String?): VersionDisplayInfo {
        val hostVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: UiText.Settings.UNKNOWN
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        val moduleVersion = try {
            BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            UiText.Settings.UNKNOWN
        }
        return VersionDisplayInfo(
            hostName = hostDisplayName(hostId),
            hostVersion = hostVersion,
            hostBuildType = "",
            hostPackageName = context.packageName,
            moduleVersion = moduleVersion,
            moduleBuildType = if (BuildConfig.DEBUG) {
                UiText.Settings.MODULE_DEBUG_VERSION
            } else {
                UiText.Settings.MODULE_RELEASE_VERSION
            },
        )
    }

    private fun hostDisplayName(hostId: String?): String {
        return when {
            hostId?.endsWith(HOST_SUFFIX_INTL) == true -> "百度网盘 国际版"
            hostId?.endsWith(HOST_SUFFIX_SAMSUNG) == true -> "百度网盘 三星版"
            else -> "百度网盘"
        }
    }

    private fun createItem(
        context: Context,
        density: Float,
        padding: Int,
        item: AboutInfoManager.AboutItem,
        onDescriptionCreated: ((TextView) -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (padding * 0.4f).toInt(), 0, (padding * 0.4f).toInt())
            item.onClickListener?.let { listener ->
                isClickable = true
                setOnClickListener { listener.onClick(this) }
            }

            addView(TextView(context).apply {
                text = item.title
                textSize = 14.5f
                setTextColor(tokens.textPrimary)
                typeface = Typeface.DEFAULT_BOLD
                includeFontPadding = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            })

            val descriptionView = TextView(context).apply {
                text = item.description
                textSize = 13f
                setTextColor(if (item.url != null) tokens.accent else tokens.textSecondary)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                includeFontPadding = false
                setPadding(0, (3 * density).toInt(), 0, 0)

                item.url?.let { url ->
                    setOnClickListener {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        } catch (t: Throwable) {
                            XposedCompat.logW(
                                "[SettingsAboutSection] open about link failed: url=$url, msg=${t.message}"
                            )
                        }
                    }
                }
            }
            onDescriptionCreated?.invoke(descriptionView)
            addView(descriptionView)
        }
    }
}
