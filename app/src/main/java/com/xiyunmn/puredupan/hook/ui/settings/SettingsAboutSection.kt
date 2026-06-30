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
        setShowDeviceFingerprint: (Boolean) -> Unit,
    ): SettingsAboutSectionHandle {
        val density = context.resources.displayMetrics.density
        val tokens = UiStyle.tokens(context)
        var deviceFingerprintDescriptionView: TextView? = null
        var deviceFingerprintToggleBadgeView: TextView? = null
        fun refreshDeviceFingerprintUi() {
            val shouldShow = showDeviceFingerprint()
            deviceFingerprintDescriptionView?.let { view ->
                val description = deviceFingerprintDescription(context, shouldShow)
                view.text = description
                view.visibility = if (description.isBlank()) View.GONE else View.VISIBLE
            }
            deviceFingerprintToggleBadgeView?.text = deviceFingerprintToggleBadgeText(shouldShow)
        }
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
            buildItems(
                context,
                hostId,
                versionClickListener,
                showDeviceFingerprint,
                setShowDeviceFingerprint = { enabled ->
                    setShowDeviceFingerprint(enabled)
                    refreshDeviceFingerprintUi()
                },
            ).forEach { item ->
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
                        onSecondaryActionBadgeCreated = if (item.title == UiText.Settings.DEVICE_FINGERPRINT_LABEL) {
                            { view: TextView -> deviceFingerprintToggleBadgeView = view }
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
            refreshDeviceFingerprintDescription = ::refreshDeviceFingerprintUi,
        )
    }

    private fun buildItems(
        context: Context,
        hostId: String?,
        versionClickListener: View.OnClickListener?,
        showDeviceFingerprint: () -> Boolean,
        setShowDeviceFingerprint: (Boolean) -> Unit,
    ): List<AboutInfoManager.AboutItem> {
        val versionInfo = buildVersionDisplayInfo(context, hostId)
        return listOf(
            AboutInfoManager.AboutItem(
                UiText.Settings.DEVICE_FINGERPRINT_LABEL,
                deviceFingerprintDescription(context, showDeviceFingerprint()),
                null,
                actionBadgeText = UiText.Settings.DEVICE_FINGERPRINT_DESC,
                onActionBadgeClick = View.OnClickListener {
                    DeviceFingerprintDialog.show(
                        context = context,
                        allowHiddenDeviceFingerprint = showDeviceFingerprint(),
                    )
                },
                secondaryActionBadgeText = deviceFingerprintToggleBadgeText(showDeviceFingerprint()),
                onSecondaryActionBadgeClick = View.OnClickListener {
                    setShowDeviceFingerprint(!showDeviceFingerprint())
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

    private fun deviceFingerprintDescription(
        context: Context,
        showDeviceFingerprint: Boolean,
    ): String {
        if (!showDeviceFingerprint) return ""
        return UiText.Settings.deviceFingerprintDesc(
            DeviceFingerprintDialog.sensitiveSummaryLines(context)
        )
    }

    private fun deviceFingerprintToggleBadgeText(showDeviceFingerprint: Boolean): String {
        return if (showDeviceFingerprint) {
            UiText.Settings.DEVICE_FINGERPRINT_HIDE_DEVICE_ID
        } else {
            UiText.Settings.DEVICE_FINGERPRINT_SHOW_DEVICE_ID
        }
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
        onSecondaryActionBadgeCreated: ((TextView) -> Unit)? = null,
    ): View {
        val tokens = UiStyle.tokens(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (padding * 0.4f).toInt(), 0, (padding * 0.4f).toInt())
            item.onClickListener?.let { listener ->
                isClickable = true
                setOnClickListener { listener.onClick(this) }
            }

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = item.title
                    textSize = 14.5f
                    setTextColor(tokens.textPrimary)
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                })
                addActionBadge(
                    context = context,
                    density = density,
                    text = item.actionBadgeText,
                    onClickListener = item.onActionBadgeClick,
                )
                val secondaryBadge = addActionBadge(
                    context = context,
                    density = density,
                    text = item.secondaryActionBadgeText,
                    onClickListener = item.onSecondaryActionBadgeClick,
                )
                if (secondaryBadge != null) {
                    onSecondaryActionBadgeCreated?.invoke(secondaryBadge)
                }
            })

            val descriptionView = TextView(context).apply {
                text = item.description
                visibility = if (item.description.isBlank()) View.GONE else View.VISIBLE
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
            if (item.description.isNotBlank() || onDescriptionCreated != null) {
                addView(descriptionView)
            }
        }
    }

    private fun LinearLayout.addActionBadge(
        context: Context,
        density: Float,
        text: String?,
        onClickListener: View.OnClickListener?,
    ): TextView? {
        if (text == null || onClickListener == null) return null
        val tokens = UiStyle.tokens(context)
        val badge = TextView(context).apply {
            this.text = text
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            UiStyle.paintStatusBadge(this, density, tokens, enabled = true)
            setOnClickListener {
                UiStyle.animateActionPress(this)
                onClickListener.onClick(this)
            }
        }
        addView(
            badge,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins((8 * density).toInt(), 0, 0, 0)
            },
        )
        return badge
    }
}
