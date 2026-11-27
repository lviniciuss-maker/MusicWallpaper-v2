package com.lucasvinicius.musicwallpaper2

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.ExifInterface
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private data class AppPalette(
        val isDark: Boolean,
        val bg: Int,
        val cardBg: Int,
        val cardStroke: Int,
        val subCardBg: Int,
        val subCardStroke: Int,
        val toggleRowBg: Int,
        val secondaryBtnBg: Int,
        val secondaryBtnText: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val mutedText: Int,
        val bodyText: Int,
        val accent: Int,
        val accentOnPrimary: Int,
        val badgeOk: Int,
        val badgeMissing: Int,
        val warnBg: Int,
        val warnStroke: Int,
        val warnText: Int,
        val diagBg: Int,
        val diagStroke: Int,
        val diagText: Int,
        val inputBg: Int,
        val pillUnselectedBg: Int,
        val pillUnselectedStroke: Int,
        val previewPlaceholder: Int,
        val previewFrameBorder: Int,
        val adbBtnBg: Int,
        val adbBtnStroke: Int,
        val dialogStroke: Int
    )

    private lateinit var palette: AppPalette

    private var permStatusBadge: TextView? = null
    private var btnRestrictedHelp: Button? = null
    private var restrictedHelpSpacer: View? = null
    private var updateNotifyCheckBox: CheckBox? = null
    private var btnExcludedApps: Button? = null
    private var btnClearCache: Button? = null
    private var diagBodyTv: TextView? = null
    private var isDiagExpanded: Boolean = false

    private var statusText: TextView? = null
    private var previewImageView: ImageView? = null
    private var lockscreenPreviewView: LockscreenPreviewView? = null

    private var pauseDelayContainer: LinearLayout? = null
    private var staticZoomSpeedContainer: LinearLayout? = null
    private var homePosSliderContainer: LinearLayout? = null

    private var homePhotoContainer: LinearLayout? = null
    private var homeStatusText: TextView? = null
    private var homePreviewImageView: ImageView? = null

    private var homeBlurPhotoContainer: LinearLayout? = null
    private var homeBlurStatusText: TextView? = null
    private var homeBlurPreviewImageView: ImageView? = null

    private val pickLockscreenImageRequest = 1001
    private val pickHomeImageRequest = 1002
    private val pickHomeBlurImageRequest = 1003
    private val exportSettingsRequest = 1004
    private val importSettingsRequest = 1005
    private val requestPostNotificationsCode = 2001

    private val githubOwner = "lviniciuss-maker"
    private val githubRepo = "MusicWallpaper-v2"

    private val paypalDonateUrl = "https://www.paypal.com/donate/?business=6J6ACS6XFSFYE&no_recurring=0&currency_code=BRL"
    private val pixDonateKey = "f73342e6-86cb-412c-aa5f-1ea2f1faf763"

    private val debugUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.lucasvinicius.musicwallpaper2.ACTION_DEBUG_METADATA_UPDATED") {
                updateDiagnosticUI()
                updateCacheSizeUI()
                loadLatestCachedCoverForPreview()
            }
        }
    }

    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    private fun buildPalette(isDark: Boolean): AppPalette {
        return if (isDark) {
            AppPalette(
                isDark = true,
                bg = Color.parseColor("#0D0F14"),
                cardBg = Color.parseColor("#181B24"),
                cardStroke = Color.parseColor("#262A38"),
                subCardBg = Color.parseColor("#141720"),
                subCardStroke = Color.parseColor("#262B3C"),
                toggleRowBg = Color.parseColor("#202431"),
                secondaryBtnBg = Color.parseColor("#262B3C"),
                secondaryBtnText = Color.WHITE,
                primaryText = Color.WHITE,
                secondaryText = Color.parseColor("#9AA0B4"),
                mutedText = Color.parseColor("#7E859B"),
                bodyText = Color.parseColor("#D5D9E6"),
                accent = Color.parseColor("#8AB4F8"),
                accentOnPrimary = Color.parseColor("#0D0F14"),
                badgeOk = Color.parseColor("#6DD58C"),
                badgeMissing = Color.parseColor("#F28B82"),
                warnBg = Color.parseColor("#2A261D"),
                warnStroke = Color.parseColor("#4D4329"),
                warnText = Color.parseColor("#FDD663"),
                diagBg = Color.parseColor("#13161F"),
                diagStroke = Color.parseColor("#252B3D"),
                diagText = Color.parseColor("#C6CCDF"),
                inputBg = Color.parseColor("#252936"),
                pillUnselectedBg = Color.parseColor("#141720"),
                pillUnselectedStroke = Color.parseColor("#2C3245"),
                previewPlaceholder = Color.parseColor("#252936"),
                previewFrameBorder = Color.parseColor("#3B4359"),
                adbBtnBg = Color.parseColor("#1E2433"),
                adbBtnStroke = Color.parseColor("#33415C"),
                dialogStroke = Color.parseColor("#2A3042")
            )
        } else {
            AppPalette(
                isDark = false,
                bg = Color.parseColor("#F4F6FA"),
                cardBg = Color.parseColor("#FFFFFF"),
                cardStroke = Color.parseColor("#DCE1EC"),
                subCardBg = Color.parseColor("#F0F3F8"),
                subCardStroke = Color.parseColor("#D5DCE8"),
                toggleRowBg = Color.parseColor("#EDF1F7"),
                secondaryBtnBg = Color.parseColor("#E5EAF3"),
                secondaryBtnText = Color.parseColor("#111827"),
                primaryText = Color.parseColor("#111827"),
                secondaryText = Color.parseColor("#525B6E"),
                mutedText = Color.parseColor("#6B7280"),
                bodyText = Color.parseColor("#374151"),
                accent = Color.parseColor("#1A73E8"),
                accentOnPrimary = Color.WHITE,
                badgeOk = Color.parseColor("#1E8E3E"),
                badgeMissing = Color.parseColor("#D93025"),
                warnBg = Color.parseColor("#FEF7E0"),
                warnStroke = Color.parseColor("#F5D67B"),
                warnText = Color.parseColor("#8C5E00"),
                diagBg = Color.parseColor("#EEF2F8"),
                diagStroke = Color.parseColor("#D5DCE8"),
                diagText = Color.parseColor("#1F2937"),
                inputBg = Color.parseColor("#E8EDF5"),
                pillUnselectedBg = Color.parseColor("#FFFFFF"),
                pillUnselectedStroke = Color.parseColor("#D0D7E5"),
                previewPlaceholder = Color.parseColor("#DCE2EC"),
                previewFrameBorder = Color.parseColor("#9AA3B5"),
                adbBtnBg = Color.parseColor("#E6EFFD"),
                adbBtnStroke = Color.parseColor("#B8D0FA"),
                dialogStroke = Color.parseColor("#D0D7E5")
            )
        }
    }

    private fun resolveIsDarkTheme(themeMode: Int): Boolean {
        return when (themeMode) {
            1 -> false
            2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()

        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val currentThemeMode = prefs.getInt("app_theme_mode", 0).coerceIn(0, 2)
        val isDark = resolveIsDarkTheme(currentThemeMode)
        palette = buildPalette(isDark)

        window.statusBarColor = palette.bg
        window.navigationBarColor = palette.bg
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        var currentOffsetPercent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 60)
        var currentHomeOffsetPercent = if (prefs.contains("home_top_offset_percent")) {
            prefs.getInt("home_top_offset_percent", currentOffsetPercent).coerceIn(0, 60)
        } else {
            currentOffsetPercent
        }
        var isPreviewingHomePos = false
        var currentLandscapeHorizontalPercent = prefs.getInt("landscape_horizontal_percent", 50).coerceIn(0, 100)
        var currentBlurPercent = prefs.getInt("blur_strength_percent", 100).coerceIn(60, 100)
        var currentBlurSatBoostPercent = prefs.getInt("blur_saturation_boost_percent", 0).coerceIn(0, 60)
        var currentExpansionPercent = prefs.getInt("cover_expansion_percent", 0).coerceIn(0, 100)
        var currentDimPercent = prefs.getInt("cover_dim_percent", 0).coerceIn(0, 40)
        var currentClockGradient = prefs.getBoolean("clock_top_gradient", false)
        var currentBottomGradient = prefs.getBoolean("bottom_footer_gradient", false)
        val savedStaticZoomEnabled = prefs.getBoolean("static_cover_zoom_enabled", false)
        var currentStaticZoomCycleSec = prefs.getInt("static_cover_zoom_cycle_sec", 12).let {
            if (it in listOf(8, 12, 18)) it else 12
        }
        var currentStaticZoomIntensityPercent = prefs.getInt("static_cover_zoom_intensity_percent", 6).let {
            if (it in listOf(6, 8, 10)) it else 6
        }
        val savedHomeBlurBrightness = prefs.getInt("home_blur_brightness_percent", 82).coerceIn(40, 100)
        val savedUpdateNotif = prefs.getBoolean("update_notifications_enabled", false) && hasPostNotificationsPermission()

        // 0 = Online | 1 = Auto (Rede Lenta) | 2 = Offline (100%)
        var currentSearchMode = if (prefs.contains("search_mode")) {
            prefs.getInt("search_mode", 0).coerceIn(0, 2)
        } else if (prefs.getBoolean("offline_only_mode", false)) {
            2
        } else {
            0
        }

        val savedWifiOnlyAnimated = prefs.getBoolean("wifi_only_animated", false)
        val savedPauseOnBatterySaver = prefs.getBoolean("pause_on_battery_saver", true)
        val savedKeepLastCover = prefs.getBoolean("keep_last_cover_on_pause", false)
        var currentPauseDelaySec = prefs.getInt("pause_delay_seconds", 5).let {
            if (it in listOf(5, 15, 30, 60)) it else 5
        }
        val savedHomeUseAlbumBlur = prefs.getBoolean("home_use_album_blur", false)
        var savedLockscreenOnly = prefs.getBoolean("lockscreen_only_mode", false)
        if (savedHomeUseAlbumBlur && savedLockscreenOnly) {
            savedLockscreenOnly = false
            prefs.edit().putBoolean("lockscreen_only_mode", false).apply()
        }
        val savedPixelColors = prefs.getBoolean("pixel_dynamic_colors", false)
        val currentAppVersion = getInstalledVersionName()

        var previewCaptionRef: TextView? = null
        fun refreshLockscreenPreview() {
            val activeOffset = if (isPreviewingHomePos) currentHomeOffsetPercent else currentOffsetPercent
            previewCaptionRef?.text = if (isPreviewingHomePos) {
                getString(R.string.preview_caption_home)
            } else {
                getString(R.string.preview_caption)
            }
            lockscreenPreviewView?.updateParams(
                topOffsetPercent = activeOffset,
                blurPercent = currentBlurPercent,
                blurSatBoostPercent = currentBlurSatBoostPercent,
                expansionPercent = currentExpansionPercent,
                dimPercent = currentDimPercent,
                clockGradient = currentClockGradient,
                bottomGradient = currentBottomGradient,
                showClock = !isPreviewingHomePos
            )
        }

        fun updateHomePosSliderVisibility() {
            val homeBlurOn = prefs.getBoolean("home_use_album_blur", false)
            val lockOnlyOn = prefs.getBoolean("lockscreen_only_mode", false)
            val showHomePos = !homeBlurOn && !lockOnlyOn
            homePosSliderContainer?.visibility = if (showHomePos) View.VISIBLE else View.GONE
            if (!showHomePos && isPreviewingHomePos) {
                isPreviewingHomePos = false
                refreshLockscreenPreview()
            }
        }

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(palette.bg)
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(36))
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val subtitleText = TextView(this).apply {
            text = getString(R.string.app_subtitle)
            setTextColor(palette.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, dp(16))
        }

        // =========================================================================
        // SELETOR COMPACTO DE TEMA (SISTEMA | CLARO | ESCURO)
        // =========================================================================
        val themeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(palette.cardBg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), palette.cardStroke)
            }
        }

        val themeOptions = listOf(
            Pair(0, getString(R.string.theme_system)),
            Pair(1, getString(R.string.theme_light)),
            Pair(2, getString(R.string.theme_dark))
        )

        themeOptions.forEachIndexed { idx, (mode, label) ->
            val isSelected = (mode == currentThemeMode)
            val pill = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setTextColor(if (isSelected) palette.accentOnPrimary else palette.secondaryText)
                typeface = if (isSelected) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(if (isSelected) palette.accent else Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < themeOptions.lastIndex) marginEnd = dp(4)
                }
                setOnClickListener {
                    if (mode != currentThemeMode) {
                        prefs.edit().putInt("app_theme_mode", mode).apply()
                        recreate()
                    }
                }
            }
            themeBar.addView(pill)
        }

        rootLayout.addView(titleText)
        rootLayout.addView(subtitleText)
        rootLayout.addView(themeBar)
        rootLayout.addView(createSpacer(18))

        // =========================================================================
        // CARTÃO 1: PERMISSÃO + AVISO DE ATUALIZAÇÃO + EXCEÇÕES + MODO DE BUSCA + WI-FI + BATERIA + CACHE + DIAGNÓSTICO
        // =========================================================================
        val cardPerm = createCardLayout()

        val permTitle = createCardTitle(getString(R.string.card_perm_title))
        val permDesc = createCardDescription(getString(R.string.card_perm_desc))

        permStatusBadge = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(14))
        }

        val btnPermission = createSecondaryButton(getString(R.string.btn_grant_perm)).apply {
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        restrictedHelpSpacer = createSpacer(8)
        btnRestrictedHelp = Button(this).apply {
            text = getString(R.string.btn_restricted_help)
            isAllCaps = false
            setTextColor(palette.warnText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                setColor(palette.warnBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), palette.warnStroke)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                showRestrictedSettingsHelpDialog()
            }
        }

        val updateNotifyRow = createToggleRow(
            title = getString(R.string.chk_update_notif_title),
            desc = getString(R.string.chk_update_notif_desc),
            initialChecked = savedUpdateNotif
        ) { newState ->
            if (newState) {
                if (hasPostNotificationsPermission()) {
                    prefs.edit().putBoolean("update_notifications_enabled", true).apply()
                    notifySettingsChanged()
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        requestPostNotificationsCode
                    )
                }
            } else {
                prefs.edit().putBoolean("update_notifications_enabled", false).apply()
                notifySettingsChanged()
            }
        }
        updateNotifyCheckBox = updateNotifyRow.getChildAt(1) as? CheckBox

        btnExcludedApps = createSecondaryButton("").apply {
            setOnClickListener {
                showExcludedAppsDialog()
            }
        }
        updateExcludedButtonCount()

        val wifiOnlySpacer = createSpacer(10)
        val wifiOnlyRow = createToggleRow(
            title = getString(R.string.chk_wifi_only_title),
            desc = getString(R.string.chk_wifi_only_desc),
            initialChecked = savedWifiOnlyAnimated
        ) { newState ->
            prefs.edit().putBoolean("wifi_only_animated", newState).apply()
            notifySettingsChanged()
        }

        val searchModeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.toggleRowBg)
                cornerRadius = dp(12).toFloat()
            }
        }

        val searchModeTitleTv = TextView(this).apply {
            text = getString(R.string.search_mode_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

        val searchModePillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val searchModeDescTv = TextView(this).apply {
            setTextColor(palette.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, 0)
        }

        val searchModeOptions = listOf(
            Pair(0, getString(R.string.search_mode_online)),
            Pair(1, getString(R.string.search_mode_auto)),
            Pair(2, getString(R.string.search_mode_offline))
        )
        val searchModeButtons = mutableListOf<Pair<Int, TextView>>()

        fun applySearchModeUI(selectedMode: Int) {
            for ((mode, tv) in searchModeButtons) {
                val isSelected = (mode == selectedMode)
                tv.setTextColor(if (isSelected) palette.accentOnPrimary else palette.bodyText)
                tv.typeface = if (isSelected) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
                tv.background = GradientDrawable().apply {
                    setColor(if (isSelected) palette.accent else palette.pillUnselectedBg)
                    cornerRadius = dp(10).toFloat()
                    if (!isSelected) {
                        setStroke(dp(1), palette.pillUnselectedStroke)
                    }
                }
            }
            searchModeDescTv.text = when (selectedMode) {
                1 -> getString(R.string.search_mode_desc_auto)
                2 -> getString(R.string.search_mode_desc_offline)
                else -> getString(R.string.search_mode_desc_online)
            }
            val wifiVis = if (selectedMode == 2) View.GONE else View.VISIBLE
            wifiOnlySpacer.visibility = wifiVis
            wifiOnlyRow.visibility = wifiVis
        }

        searchModeOptions.forEachIndexed { idx, (mode, label) ->
            val pill = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(8), dp(9), dp(8), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < searchModeOptions.lastIndex) marginEnd = dp(8)
                }
                setOnClickListener {
                    if (currentSearchMode != mode) {
                        currentSearchMode = mode
                        prefs.edit()
                            .putInt("search_mode", mode)
                            .putBoolean("offline_only_mode", mode == 2)
                            .apply()
                        applySearchModeUI(mode)
                        notifySettingsChanged()
                    }
                }
            }
            searchModeButtons.add(Pair(mode, pill))
            searchModePillsRow.addView(pill)
        }
        applySearchModeUI(currentSearchMode)

        searchModeContainer.addView(searchModeTitleTv)
        searchModeContainer.addView(searchModePillsRow)
        searchModeContainer.addView(searchModeDescTv)

        val batterySaverRow = createToggleRow(
            title = getString(R.string.chk_battery_saver_title),
            desc = getString(R.string.chk_battery_saver_desc),
            initialChecked = savedPauseOnBatterySaver
        ) { newState ->
            prefs.edit().putBoolean("pause_on_battery_saver", newState).apply()
            notifySettingsChanged()
        }

        btnClearCache = createSecondaryButton(getString(R.string.btn_clear_cache, "0.0 MB")).apply {
            setOnClickListener {
                thread {
                    try {
                        File(cacheDir, "artwork_cache").deleteRecursively()
                    } catch (_: Throwable) {}
                    runOnUiThread {
                        sendBroadcast(Intent("com.lucasvinicius.musicwallpaper2.ACTION_CLEAR_CACHE").apply {
                            setPackage(packageName)
                        })
                        updateCacheSizeUI()
                        loadLatestCachedCoverForPreview()
                        Toast.makeText(this@MainActivity, getString(R.string.toast_cache_cleared), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val diagBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = GradientDrawable().apply {
                setColor(palette.diagBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), palette.diagStroke)
            }
        }

        val diagHeaderTv = TextView(this).apply {
            text = getString(R.string.diag_header_collapsed)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val diagContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, 0)
        }

        val diagHintTv = TextView(this).apply {
            text = getString(R.string.diag_copy_hint)
            setTextColor(palette.mutedText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, 0, 0, dp(6))
        }

        diagBodyTv = TextView(this).apply {
            setTextColor(palette.diagText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(2).toFloat(), 1.05f)
            setOnClickListener {
                val textToCopy = text?.toString() ?: ""
                if (textToCopy.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MusicWallpaper Metadata", textToCopy))
                    Toast.makeText(this@MainActivity, getString(R.string.toast_diag_copied), Toast.LENGTH_SHORT).show()
                }
            }
        }

        diagContentContainer.addView(diagHintTv)
        diagContentContainer.addView(diagBodyTv)

        diagHeaderTv.setOnClickListener {
            isDiagExpanded = !isDiagExpanded
            diagContentContainer.visibility = if (isDiagExpanded) View.VISIBLE else View.GONE
            diagHeaderTv.text = if (isDiagExpanded) {
                getString(R.string.diag_header_expanded)
            } else {
                getString(R.string.diag_header_collapsed)
            }
        }

        diagBox.addView(diagHeaderTv)
        diagBox.addView(diagContentContainer)

        cardPerm.addView(permTitle)
        cardPerm.addView(permDesc)
        cardPerm.addView(permStatusBadge)
        cardPerm.addView(btnPermission)
        cardPerm.addView(restrictedHelpSpacer)
        cardPerm.addView(btnRestrictedHelp)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(updateNotifyRow)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(btnExcludedApps)
        cardPerm.addView(createSpacer(12))
        cardPerm.addView(searchModeContainer)
        cardPerm.addView(wifiOnlySpacer)
        cardPerm.addView(wifiOnlyRow)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(batterySaverRow)

        if (isGooglePixel7OrNewer()) {
            val pixelColorsRow = createToggleRow(
                title = getString(R.string.chk_pixel_colors_title),
                desc = getString(R.string.chk_pixel_colors_desc),
                initialChecked = savedPixelColors
            ) { newState ->
                prefs.edit().putBoolean("pixel_dynamic_colors", newState).apply()
                notifySettingsChanged()
            }
            cardPerm.addView(createSpacer(10))
            cardPerm.addView(pixelColorsRow)
        }

        cardPerm.addView(createSpacer(12))
        cardPerm.addView(btnClearCache)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(diagBox)
        rootLayout.addView(cardPerm)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 2: FOTOS DE ESPERA + TEMPO DE PAUSA + MANTER CAPA + TELA INICIAL
        // =========================================================================
        val cardPhoto = createCardLayout()

        val photoTitle = createCardTitle(getString(R.string.card_photo_title))
        val photoDesc = createCardDescription(getString(R.string.card_photo_desc))

        val photoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(14))
        }

        previewImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(84)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(palette.previewPlaceholder)
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        statusText = TextView(this).apply {
            setTextColor(palette.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        photoRow.addView(previewImageView)
        photoRow.addView(statusText)

        val btnPickImage = createSecondaryButton(getString(R.string.btn_pick_photo)).apply {
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.chooser_title)), pickLockscreenImageRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        pauseDelayContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.toggleRowBg)
                cornerRadius = dp(12).toFloat()
            }
            visibility = if (savedKeepLastCover) View.GONE else View.VISIBLE
        }

        val pauseDelayTitleTv = TextView(this).apply {
            text = getString(R.string.pause_delay_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

        val pauseDelayOptionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val delayOptions = listOf(
            Pair(5, "5s"),
            Pair(15, "15s"),
            Pair(30, "30s"),
            Pair(60, "1 min")
        )
        val delayButtons = mutableListOf<Pair<Int, TextView>>()

        fun styleDelayButtons(selectedSec: Int) {
            for ((sec, tv) in delayButtons) {
                val isSelected = (sec == selectedSec)
                tv.setTextColor(if (isSelected) palette.accentOnPrimary else palette.bodyText)
                tv.typeface = if (isSelected) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
                tv.background = GradientDrawable().apply {
                    setColor(if (isSelected) palette.accent else palette.pillUnselectedBg)
                    cornerRadius = dp(10).toFloat()
                    if (!isSelected) {
                        setStroke(dp(1), palette.pillUnselectedStroke)
                    }
                }
            }
        }

        delayOptions.forEachIndexed { idx, (seconds, label) ->
            val pill = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(8), dp(9), dp(8), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < delayOptions.lastIndex) marginEnd = dp(8)
                }
                setOnClickListener {
                    currentPauseDelaySec = seconds
                    prefs.edit().putInt("pause_delay_seconds", seconds).apply()
                    styleDelayButtons(seconds)
                    notifySettingsChanged()
                }
            }
            delayButtons.add(Pair(seconds, pill))
            pauseDelayOptionsRow.addView(pill)
        }
        styleDelayButtons(currentPauseDelaySec)

        pauseDelayContainer?.addView(pauseDelayTitleTv)
        pauseDelayContainer?.addView(pauseDelayOptionsRow)

        val keepLastCoverRow = createToggleRow(
            title = getString(R.string.chk_keep_cover_title),
            desc = getString(R.string.chk_keep_cover_desc),
            initialChecked = savedKeepLastCover
        ) { newState ->
            prefs.edit().putBoolean("keep_last_cover_on_pause", newState).apply()
            pauseDelayContainer?.visibility = if (newState) View.GONE else View.VISIBLE
            notifySettingsChanged()
        }

        var lockscreenOnlyRowRef: LinearLayout? = null
        var homeUseBlurRowRef: LinearLayout? = null

        val lockscreenOnlyRow = createToggleRow(
            title = getString(R.string.chk_lockscreen_only_title),
            desc = getString(R.string.chk_lockscreen_only_desc),
            initialChecked = savedLockscreenOnly
        ) { newState ->
            val editor = prefs.edit().putBoolean("lockscreen_only_mode", newState)
            if (newState) {
                editor.putBoolean("home_use_album_blur", false)
                (homeUseBlurRowRef?.getChildAt(1) as? CheckBox)?.isChecked = false
                homeBlurPhotoContainer?.visibility = View.GONE
            }
            editor.apply()
            homePhotoContainer?.visibility = if (newState) View.VISIBLE else View.GONE
            updateHomePosSliderVisibility()
            notifySettingsChanged()
        }
        lockscreenOnlyRowRef = lockscreenOnlyRow

        val homeUseBlurRow = createToggleRow(
            title = getString(R.string.chk_home_blur_title),
            desc = getString(R.string.chk_home_blur_desc),
            initialChecked = savedHomeUseAlbumBlur
        ) { newState ->
            val editor = prefs.edit().putBoolean("home_use_album_blur", newState)
            if (newState) {
                editor.putBoolean("lockscreen_only_mode", false)
                (lockscreenOnlyRowRef?.getChildAt(1) as? CheckBox)?.isChecked = false
                homePhotoContainer?.visibility = View.GONE
            }
            editor.apply()
            homeBlurPhotoContainer?.visibility = if (newState) View.VISIBLE else View.GONE
            updateHomePosSliderVisibility()
            notifySettingsChanged()
        }
        homeUseBlurRowRef = homeUseBlurRow

        homeBlurPhotoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(palette.subCardBg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), palette.subCardStroke)
            }
            visibility = if (savedHomeUseAlbumBlur) View.VISIBLE else View.GONE
        }

        val homeBlurBrightnessLabel = TextView(this).apply {
            text = getString(R.string.home_blur_brightness_format, savedHomeBlurBrightness)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        }

        val seekBarHomeBlurBrightness = SeekBar(this).apply {
            max = 60
            progress = (savedHomeBlurBrightness - 40).coerceIn(0, 60)
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actualPercent = 40 + progress
                    homeBlurBrightnessLabel.text = getString(R.string.home_blur_brightness_format, actualPercent)
                    if (fromUser) {
                        prefs.edit().putInt("home_blur_brightness_percent", actualPercent).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val homeBlurSectionTitle = TextView(this).apply {
            text = getString(R.string.home_blur_photo_section_title)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val homeBlurPhotoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }

        homeBlurPreviewImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(84)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(palette.previewPlaceholder)
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        homeBlurStatusText = TextView(this).apply {
            setTextColor(palette.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        homeBlurPhotoRow.addView(homeBlurPreviewImageView)
        homeBlurPhotoRow.addView(homeBlurStatusText)

        val btnPickHomeBlurImage = createSecondaryButton(getString(R.string.btn_pick_home_blur_photo)).apply {
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.chooser_title)), pickHomeBlurImageRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        homeBlurPhotoContainer?.addView(homeBlurBrightnessLabel)
        homeBlurPhotoContainer?.addView(seekBarHomeBlurBrightness)
        homeBlurPhotoContainer?.addView(homeBlurSectionTitle)
        homeBlurPhotoContainer?.addView(homeBlurPhotoRow)
        homeBlurPhotoContainer?.addView(btnPickHomeBlurImage)

        homePhotoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(palette.subCardBg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), palette.subCardStroke)
            }
            visibility = if (savedLockscreenOnly) View.VISIBLE else View.GONE
        }

        val homeSectionTitle = TextView(this).apply {
            text = getString(R.string.home_photo_section_title)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val homePhotoRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }

        homePreviewImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(84)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                setColor(palette.previewPlaceholder)
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        homeStatusText = TextView(this).apply {
            setTextColor(palette.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        homePhotoRow.addView(homePreviewImageView)
        homePhotoRow.addView(homeStatusText)

        val btnPickHomeImage = createSecondaryButton(getString(R.string.btn_pick_home_photo)).apply {
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.chooser_title)), pickHomeImageRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        homePhotoContainer?.addView(homeSectionTitle)
        homePhotoContainer?.addView(homePhotoRow)
        homePhotoContainer?.addView(btnPickHomeImage)

        cardPhoto.addView(photoTitle)
        cardPhoto.addView(photoDesc)
        cardPhoto.addView(photoRow)
        cardPhoto.addView(btnPickImage)
        cardPhoto.addView(createSpacer(12))
        cardPhoto.addView(keepLastCoverRow)
        cardPhoto.addView(createSpacer(10))
        cardPhoto.addView(pauseDelayContainer)
        cardPhoto.addView(createSpacer(10))
        cardPhoto.addView(homeUseBlurRow)
        cardPhoto.addView(createSpacer(8))
        cardPhoto.addView(homeBlurPhotoContainer)
        cardPhoto.addView(createSpacer(10))
        cardPhoto.addView(lockscreenOnlyRow)
        cardPhoto.addView(createSpacer(8))
        cardPhoto.addView(homePhotoContainer)
        rootLayout.addView(cardPhoto)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 3: PREVIEW (CAPA REAL) + POSIÇÕES + BLUR + SATURAÇÃO + EXPANSÃO + DIMMER + SOMBRAS + ZOOM
        // =========================================================================
        val cardPos = createCardLayout()

        val posTitle = createCardTitle(getString(R.string.card_pos_title))

        val previewWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(14), 0, dp(10))
        }

        val lockscreenPreview = LockscreenPreviewView(
            this,
            getString(R.string.preview_date_sample),
            palette.previewFrameBorder
        ).apply {
            layoutParams = LinearLayout.LayoutParams(dp(128), dp(276))
        }
        lockscreenPreviewView = lockscreenPreview

        val previewCaptionTv = TextView(this).apply {
            text = getString(R.string.preview_caption)
            setTextColor(palette.mutedText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(8), 0, 0)
        }
        previewCaptionRef = previewCaptionTv
        refreshLockscreenPreview()

        previewWrapper.addView(lockscreenPreview)
        previewWrapper.addView(previewCaptionTv)

        val sliderLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(8))
            text = getString(R.string.pos_label_format, currentOffsetPercent)
        }

        val seekBarPos = SeekBar(this).apply {
            max = 60
            progress = currentOffsetPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentOffsetPercent = progress
                    sliderLabel.text = getString(R.string.pos_label_format, progress)
                    if (fromUser) {
                        isPreviewingHomePos = false
                        prefs.edit().putInt("top_offset_percent", progress).apply()
                        notifySettingsChanged()
                    }
                    refreshLockscreenPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isPreviewingHomePos = false
                    refreshLockscreenPreview()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        homePosSliderContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (!savedHomeUseAlbumBlur && !savedLockscreenOnly) View.VISIBLE else View.GONE
        }

        val homeSliderLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.home_pos_label_format, currentHomeOffsetPercent)
        }

        val seekBarHomePos = SeekBar(this).apply {
            max = 60
            progress = currentHomeOffsetPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentHomeOffsetPercent = progress
                    homeSliderLabel.text = getString(R.string.home_pos_label_format, progress)
                    if (fromUser) {
                        isPreviewingHomePos = true
                        prefs.edit().putInt("home_top_offset_percent", progress).apply()
                        notifySettingsChanged()
                    }
                    refreshLockscreenPreview()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isPreviewingHomePos = true
                    refreshLockscreenPreview()
                }
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        homePosSliderContainer?.addView(homeSliderLabel)
        homePosSliderContainer?.addView(seekBarHomePos)

        val landscapePosLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.landscape_pos_label_format, currentLandscapeHorizontalPercent)
        }

        val seekBarLandscapePos = SeekBar(this).apply {
            max = 100
            progress = currentLandscapeHorizontalPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentLandscapeHorizontalPercent = progress
                    landscapePosLabel.text = getString(R.string.landscape_pos_label_format, progress)
                    if (fromUser) {
                        prefs.edit().putInt("landscape_horizontal_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val blurLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.blur_label_format, currentBlurPercent)
        }

        val seekBarBlur = SeekBar(this).apply {
            max = 40
            progress = (currentBlurPercent - 60).coerceIn(0, 40)
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentBlurPercent = 60 + progress
                    blurLabel.text = getString(R.string.blur_label_format, currentBlurPercent)
                    refreshLockscreenPreview()
                    if (fromUser) {
                        prefs.edit().putInt("blur_strength_percent", currentBlurPercent).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val blurSatLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.blur_sat_label_format, currentBlurSatBoostPercent)
        }

        val seekBarBlurSat = SeekBar(this).apply {
            max = 60
            progress = currentBlurSatBoostPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentBlurSatBoostPercent = progress
                    blurSatLabel.text = getString(R.string.blur_sat_label_format, progress)
                    refreshLockscreenPreview()
                    if (fromUser) {
                        prefs.edit().putInt("blur_saturation_boost_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val expandLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.expand_label_format, currentExpansionPercent)
        }

        val seekBarExpand = SeekBar(this).apply {
            max = 100
            progress = currentExpansionPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentExpansionPercent = progress
                    expandLabel.text = getString(R.string.expand_label_format, progress)
                    refreshLockscreenPreview()
                    if (fromUser) {
                        prefs.edit().putInt("cover_expansion_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val dimLabel = TextView(this).apply {
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.dim_label_format, currentDimPercent)
        }

        val seekBarDim = SeekBar(this).apply {
            max = 40
            progress = currentDimPercent
            progressTintList = ColorStateList.valueOf(palette.accent)
            thumbTintList = ColorStateList.valueOf(palette.accent)
            setPadding(dp(14), dp(6), dp(14), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentDimPercent = progress
                    dimLabel.text = getString(R.string.dim_label_format, progress)
                    refreshLockscreenPreview()
                    if (fromUser) {
                        prefs.edit().putInt("cover_dim_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val clockGradientRow = createToggleRow(
            title = getString(R.string.chk_clock_gradient_title),
            desc = getString(R.string.chk_clock_gradient_desc),
            initialChecked = currentClockGradient
        ) { newState ->
            currentClockGradient = newState
            refreshLockscreenPreview()
            prefs.edit().putBoolean("clock_top_gradient", newState).apply()
            notifySettingsChanged()
        }

        val bottomGradientRow = createToggleRow(
            title = getString(R.string.chk_bottom_gradient_title),
            desc = getString(R.string.chk_bottom_gradient_desc),
            initialChecked = currentBottomGradient
        ) { newState ->
            currentBottomGradient = newState
            refreshLockscreenPreview()
            prefs.edit().putBoolean("bottom_footer_gradient", newState).apply()
            notifySettingsChanged()
        }

        staticZoomSpeedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.subCardBg)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), palette.subCardStroke)
            }
            visibility = if (savedStaticZoomEnabled) View.VISIBLE else View.GONE
        }

        val staticZoomSpeedTitleTv = TextView(this).apply {
            text = getString(R.string.static_zoom_speed_title)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

        val staticZoomSpeedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val zoomSpeedOptions = listOf(
            Pair(8, getString(R.string.static_zoom_speed_fast)),
            Pair(12, getString(R.string.static_zoom_speed_medium)),
            Pair(18, getString(R.string.static_zoom_speed_slow))
        )
        val zoomSpeedButtons = mutableListOf<Pair<Int, TextView>>()

        fun styleZoomSpeedButtons(selectedSec: Int) {
            for ((sec, tv) in zoomSpeedButtons) {
                val isSelected = (sec == selectedSec)
                tv.setTextColor(if (isSelected) palette.accentOnPrimary else palette.bodyText)
                tv.typeface = if (isSelected) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
                tv.background = GradientDrawable().apply {
                    setColor(if (isSelected) palette.accent else palette.pillUnselectedBg)
                    cornerRadius = dp(10).toFloat()
                    if (!isSelected) {
                        setStroke(dp(1), palette.pillUnselectedStroke)
                    }
                }
            }
        }

        zoomSpeedOptions.forEachIndexed { idx, (seconds, label) ->
            val pill = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(dp(6), dp(9), dp(6), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < zoomSpeedOptions.lastIndex) marginEnd = dp(8)
                }
                setOnClickListener {
                    currentStaticZoomCycleSec = seconds
                    prefs.edit().putInt("static_cover_zoom_cycle_sec", seconds).apply()
                    styleZoomSpeedButtons(seconds)
                    notifySettingsChanged()
                }
            }
            zoomSpeedButtons.add(Pair(seconds, pill))
            staticZoomSpeedRow.addView(pill)
        }
        styleZoomSpeedButtons(currentStaticZoomCycleSec)

        val staticZoomIntensityTitleTv = TextView(this).apply {
            text = getString(R.string.static_zoom_intensity_title)
            setTextColor(palette.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(10))
        }

        val staticZoomIntensityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val zoomIntensityOptions = listOf(
            Pair(6, "6%"),
            Pair(8, "8%"),
            Pair(10, "10%")
        )
        val zoomIntensityButtons = mutableListOf<Pair<Int, TextView>>()

        fun styleZoomIntensityButtons(selectedPct: Int) {
            for ((pct, tv) in zoomIntensityButtons) {
                val isSelected = (pct == selectedPct)
                tv.setTextColor(if (isSelected) palette.accentOnPrimary else palette.bodyText)
                tv.typeface = if (isSelected) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
                tv.background = GradientDrawable().apply {
                    setColor(if (isSelected) palette.accent else palette.pillUnselectedBg)
                    cornerRadius = dp(10).toFloat()
                    if (!isSelected) {
                        setStroke(dp(1), palette.pillUnselectedStroke)
                    }
                }
            }
        }

        zoomIntensityOptions.forEachIndexed { idx, (pct, label) ->
            val pill = TextView(this).apply {
                text = label
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setPadding(dp(6), dp(9), dp(6), dp(9))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < zoomIntensityOptions.lastIndex) marginEnd = dp(8)
                }
                setOnClickListener {
                    currentStaticZoomIntensityPercent = pct
                    prefs.edit().putInt("static_cover_zoom_intensity_percent", pct).apply()
                    styleZoomIntensityButtons(pct)
                    notifySettingsChanged()
                }
            }
            zoomIntensityButtons.add(Pair(pct, pill))
            staticZoomIntensityRow.addView(pill)
        }
        styleZoomIntensityButtons(currentStaticZoomIntensityPercent)

        staticZoomSpeedContainer?.addView(staticZoomSpeedTitleTv)
        staticZoomSpeedContainer?.addView(staticZoomSpeedRow)
        staticZoomSpeedContainer?.addView(staticZoomIntensityTitleTv)
        staticZoomSpeedContainer?.addView(staticZoomIntensityRow)

        val staticZoomRow = createToggleRow(
            title = getString(R.string.chk_static_zoom_title),
            desc = getString(R.string.chk_static_zoom_desc),
            initialChecked = savedStaticZoomEnabled
        ) { newState ->
            prefs.edit().putBoolean("static_cover_zoom_enabled", newState).apply()
            staticZoomSpeedContainer?.visibility = if (newState) View.VISIBLE else View.GONE
            notifySettingsChanged()
        }

        val btnResetOffset = createSecondaryButton(getString(R.string.btn_reset_pos)).apply {
            setOnClickListener {
                isPreviewingHomePos = false
                currentOffsetPercent = 20
                currentHomeOffsetPercent = 20
                currentLandscapeHorizontalPercent = 50
                currentBlurPercent = 100
                currentBlurSatBoostPercent = 0
                currentExpansionPercent = 0
                currentDimPercent = 0
                currentClockGradient = false
                currentBottomGradient = false
                currentStaticZoomCycleSec = 12
                currentStaticZoomIntensityPercent = 6
                seekBarPos.progress = 20
                seekBarHomePos.progress = 20
                seekBarLandscapePos.progress = 50
                seekBarBlur.progress = 40
                seekBarBlurSat.progress = 0
                seekBarExpand.progress = 0
                seekBarDim.progress = 0
                seekBarHomeBlurBrightness.progress = 42
                (clockGradientRow.getChildAt(1) as? CheckBox)?.isChecked = false
                (bottomGradientRow.getChildAt(1) as? CheckBox)?.isChecked = false
                (staticZoomRow.getChildAt(1) as? CheckBox)?.isChecked = false
                staticZoomSpeedContainer?.visibility = View.GONE
                styleZoomSpeedButtons(12)
                styleZoomIntensityButtons(6)
                refreshLockscreenPreview()
                prefs.edit()
                    .putInt("top_offset_percent", 20)
                    .putInt("home_top_offset_percent", 20)
                    .putInt("landscape_horizontal_percent", 50)
                    .putInt("blur_strength_percent", 100)
                    .putInt("blur_saturation_boost_percent", 0)
                    .putInt("cover_expansion_percent", 0)
                    .putInt("cover_dim_percent", 0)
                    .putInt("home_blur_brightness_percent", 82)
                    .putBoolean("clock_top_gradient", false)
                    .putBoolean("bottom_footer_gradient", false)
                    .putBoolean("static_cover_zoom_enabled", false)
                    .putInt("static_cover_zoom_cycle_sec", 12)
                    .putInt("static_cover_zoom_intensity_percent", 6)
                    .apply()
                notifySettingsChanged()
            }
        }

        cardPos.addView(posTitle)
        cardPos.addView(previewWrapper)
        cardPos.addView(sliderLabel)
        cardPos.addView(seekBarPos)
        cardPos.addView(homePosSliderContainer)
        cardPos.addView(landscapePosLabel)
        cardPos.addView(seekBarLandscapePos)
        cardPos.addView(blurLabel)
        cardPos.addView(seekBarBlur)
        cardPos.addView(blurSatLabel)
        cardPos.addView(seekBarBlurSat)
        cardPos.addView(expandLabel)
        cardPos.addView(seekBarExpand)
        cardPos.addView(dimLabel)
        cardPos.addView(seekBarDim)
        cardPos.addView(clockGradientRow)
        cardPos.addView(createSpacer(10))
        cardPos.addView(bottomGradientRow)
        cardPos.addView(createSpacer(10))
        cardPos.addView(staticZoomRow)
        cardPos.addView(createSpacer(8))
        cardPos.addView(staticZoomSpeedContainer)
        cardPos.addView(createSpacer(14))
        cardPos.addView(btnResetOffset)
        rootLayout.addView(cardPos)
        rootLayout.addView(createSpacer(24))

        // =========================================================================
        // BOTÃO PRINCIPAL: ATIVAR PAPEL DE PAREDE
        // =========================================================================
        val btnWallpaper = Button(this).apply {
            text = getString(R.string.btn_activate_wallpaper)
            isAllCaps = false
            setTextColor(palette.accentOnPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(palette.accent)
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setOnClickListener {
                try {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(
                            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                            ComponentName(this@MainActivity, BlurVideoWallpaperService::class.java)
                        )
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        rootLayout.addView(btnWallpaper)
        rootLayout.addView(createSpacer(12))

        // =========================================================================
        // BACKUP DE CONFIGURAÇÕES: EXPORTAR | IMPORTAR (.JSON)
        // =========================================================================
        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnExport = createSecondaryButton(getString(R.string.btn_export_settings)).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "MusicWallpaper_Backup.json")
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, exportSettingsRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_export_error), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnImport = createSecondaryButton(getString(R.string.btn_import_settings)).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, importSettingsRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_import_error), Toast.LENGTH_SHORT).show()
                }
            }
        }

        backupRow.addView(btnExport)
        backupRow.addView(btnImport)
        rootLayout.addView(backupRow)
        rootLayout.addView(createSpacer(10))

        // =========================================================================
        // RODAPÉ: ATUALIZAÇÕES NO GITHUB + FAQ + APOIAR O PROJETO
        // =========================================================================
        val btnCheckUpdate = createSecondaryButton(getString(R.string.btn_check_updates, currentAppVersion)).apply {
            setOnClickListener {
                checkForGitHubUpdates(isManualCheck = true)
            }
        }
        rootLayout.addView(btnCheckUpdate)
        rootLayout.addView(createSpacer(10))

        val btnFaq = createSecondaryButton(getString(R.string.btn_faq)).apply {
            setOnClickListener {
                showFaqDialog()
            }
        }
        rootLayout.addView(btnFaq)
        rootLayout.addView(createSpacer(10))

        val btnDonate = createSecondaryButton(getString(R.string.btn_donate)).apply {
            setOnClickListener {
                showDonateDialog()
            }
        }
        rootLayout.addView(btnDonate)

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        updatePhotoStatusUI()
        updatePermissionStatusUI()
        syncUpdateNotificationToggle()
        updateDiagnosticUI()
        updateCacheSizeUI()
        loadLatestCachedCoverForPreview()

        checkForGitHubUpdates(isManualCheck = false)
    }

    private fun loadLatestCachedCoverForPreview() {
        thread {
            var sharpBmp: Bitmap? = null
            var blurBmp: Bitmap? = null
            try {
                val coversDir = File(cacheDir, "artwork_cache/covers")
                val latestFile = coversDir.listFiles()
                    ?.filter { it.isFile && (it.name.endsWith(".jpg", true) || it.name.endsWith(".png", true)) }
                    ?.maxByOrNull { it.lastModified() }

                if (latestFile != null) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(latestFile.absolutePath, bounds)
                    var sample = 1
                    val maxDim = max(bounds.outWidth, bounds.outHeight)
                    while (maxDim / sample > 480) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply {
                        inSampleSize = sample
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    sharpBmp = BitmapFactory.decodeFile(latestFile.absolutePath, opts)
                    if (sharpBmp != null) {
                        blurBmp = createFastPreviewBlur(sharpBmp)
                    }
                }
            } catch (_: Throwable) {}

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    lockscreenPreviewView?.setCoverArtwork(sharpBmp, blurBmp)
                }
            }
        }
    }

    private fun createFastPreviewBlur(src: Bitmap): Bitmap {
        val tinyW = 18
        val tinyH = 18
        val tiny = Bitmap.createScaledBitmap(src, tinyW, tinyH, true)
        val pixels = IntArray(tinyW * tinyH)
        tiny.getPixels(pixels, 0, tinyW, 0, 0, tinyW, tinyH)
        val out = IntArray(tinyW * tinyH)

        var cur = pixels
        var dst = out
        repeat(2) {
            for (y in 0 until tinyH) {
                for (x in 0 until tinyW) {
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0
                    for (dy in -1..1) {
                        val ny = (y + dy).coerceIn(0, tinyH - 1)
                        for (dx in -1..1) {
                            val nx = (x + dx).coerceIn(0, tinyW - 1)
                            val c = cur[ny * tinyW + nx]
                            r += Color.red(c)
                            g += Color.green(c)
                            b += Color.blue(c)
                            count++
                        }
                    }
                    dst[y * tinyW + x] = Color.rgb(r / count, g / count, b / count)
                }
            }
            val tmp = cur
            cur = dst
            dst = tmp
        }

        val blurredTiny = Bitmap.createBitmap(tinyW, tinyH, Bitmap.Config.ARGB_8888)
        blurredTiny.setPixels(cur, 0, tinyW, 0, 0, tinyW, tinyH)
        return Bitmap.createScaledBitmap(blurredTiny, 128, 256, true)
    }

    private fun exportSettingsToUri(uri: Uri) {
        thread {
            try {
                val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                val excluded = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
                val excludedArray = JSONArray().apply {
                    excluded.sorted().forEach { put(it) }
                }

                val savedMode = if (prefs.contains("search_mode")) {
                    prefs.getInt("search_mode", 0).coerceIn(0, 2)
                } else if (prefs.getBoolean("offline_only_mode", false)) {
                    2
                } else {
                    0
                }

                val savedTopOffset = prefs.getInt("top_offset_percent", 20)
                val savedHomeTopOffset = if (prefs.contains("home_top_offset_percent")) {
                    prefs.getInt("home_top_offset_percent", savedTopOffset)
                } else {
                    savedTopOffset
                }

                val root = JSONObject().apply {
                    put("app", "MusicWallpaper")
                    put("schema_version", 3)
                    put("app_theme_mode", prefs.getInt("app_theme_mode", 0))
                    put("top_offset_percent", savedTopOffset)
                    put("home_top_offset_percent", savedHomeTopOffset)
                    put("landscape_horizontal_percent", prefs.getInt("landscape_horizontal_percent", 50))
                    put("blur_strength_percent", prefs.getInt("blur_strength_percent", 100))
                    put("blur_saturation_boost_percent", prefs.getInt("blur_saturation_boost_percent", 0))
                    put("cover_expansion_percent", prefs.getInt("cover_expansion_percent", 0))
                    put("cover_dim_percent", prefs.getInt("cover_dim_percent", 0))
                    put("clock_top_gradient", prefs.getBoolean("clock_top_gradient", false))
                    put("bottom_footer_gradient", prefs.getBoolean("bottom_footer_gradient", false))
                    put("static_cover_zoom_enabled", prefs.getBoolean("static_cover_zoom_enabled", false))
                    put("static_cover_zoom_cycle_sec", prefs.getInt("static_cover_zoom_cycle_sec", 12))
                    put("static_cover_zoom_intensity_percent", prefs.getInt("static_cover_zoom_intensity_percent", 6))
                    put("home_blur_brightness_percent", prefs.getInt("home_blur_brightness_percent", 82))
                    put("pause_delay_seconds", prefs.getInt("pause_delay_seconds", 5))
                    put("keep_last_cover_on_pause", prefs.getBoolean("keep_last_cover_on_pause", false))
                    put("lockscreen_only_mode", prefs.getBoolean("lockscreen_only_mode", false))
                    put("home_use_album_blur", prefs.getBoolean("home_use_album_blur", false))
                    put("search_mode", savedMode)
                    put("offline_only_mode", savedMode == 2)
                    put("wifi_only_animated", prefs.getBoolean("wifi_only_animated", false))
                    put("pause_on_battery_saver", prefs.getBoolean("pause_on_battery_saver", true))
                    put("pixel_dynamic_colors", prefs.getBoolean("pixel_dynamic_colors", false))
                    put("update_notifications_enabled", prefs.getBoolean("update_notifications_enabled", false))
                    put("excluded_packages", excludedArray)
                }

                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(root.toString(2).toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("OutputStream null")

                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_export_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun importSettingsFromUri(uri: Uri) {
        thread {
            try {
                val rawText = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalStateException("InputStream null")

                val json = JSONObject(rawText)
                val looksValid = json.optString("app", "") == "MusicWallpaper" ||
                        json.has("top_offset_percent") ||
                        json.has("blur_strength_percent")
                if (!looksValid) {
                    throw IllegalArgumentException("Unrecognized JSON")
                }

                val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                val editor = prefs.edit()

                if (json.has("app_theme_mode")) {
                    editor.putInt("app_theme_mode", json.optInt("app_theme_mode", 0).coerceIn(0, 2))
                }
                if (json.has("top_offset_percent")) {
                    editor.putInt("top_offset_percent", json.optInt("top_offset_percent", 20).coerceIn(0, 60))
                }
                if (json.has("home_top_offset_percent")) {
                    editor.putInt("home_top_offset_percent", json.optInt("home_top_offset_percent", 20).coerceIn(0, 60))
                } else if (json.has("top_offset_percent")) {
                    editor.putInt("home_top_offset_percent", json.optInt("top_offset_percent", 20).coerceIn(0, 60))
                }
                if (json.has("landscape_horizontal_percent")) {
                    editor.putInt("landscape_horizontal_percent", json.optInt("landscape_horizontal_percent", 50).coerceIn(0, 100))
                }
                if (json.has("blur_strength_percent")) {
                    editor.putInt("blur_strength_percent", json.optInt("blur_strength_percent", 100).coerceIn(60, 100))
                }
                if (json.has("blur_saturation_boost_percent")) {
                    editor.putInt("blur_saturation_boost_percent", json.optInt("blur_saturation_boost_percent", 0).coerceIn(0, 60))
                }
                if (json.has("cover_expansion_percent")) {
                    editor.putInt("cover_expansion_percent", json.optInt("cover_expansion_percent", 0).coerceIn(0, 100))
                }
                if (json.has("cover_dim_percent")) {
                    editor.putInt("cover_dim_percent", json.optInt("cover_dim_percent", 0).coerceIn(0, 40))
                }
                if (json.has("clock_top_gradient")) {
                    editor.putBoolean("clock_top_gradient", json.optBoolean("clock_top_gradient", false))
                }
                if (json.has("bottom_footer_gradient")) {
                    editor.putBoolean("bottom_footer_gradient", json.optBoolean("bottom_footer_gradient", false))
                }
                if (json.has("static_cover_zoom_enabled")) {
                    editor.putBoolean("static_cover_zoom_enabled", json.optBoolean("static_cover_zoom_enabled", false))
                }
                if (json.has("static_cover_zoom_cycle_sec")) {
                    val cycle = json.optInt("static_cover_zoom_cycle_sec", 12).let { if (it in listOf(8, 12, 18)) it else 12 }
                    editor.putInt("static_cover_zoom_cycle_sec", cycle)
                }
                if (json.has("static_cover_zoom_intensity_percent")) {
                    val intensity = json.optInt("static_cover_zoom_intensity_percent", 6).let { if (it in listOf(6, 8, 10)) it else 6 }
                    editor.putInt("static_cover_zoom_intensity_percent", intensity)
                }
                if (json.has("home_blur_brightness_percent")) {
                    editor.putInt("home_blur_brightness_percent", json.optInt("home_blur_brightness_percent", 82).coerceIn(40, 100))
                }
                if (json.has("pause_delay_seconds")) {
                    val delay = json.optInt("pause_delay_seconds", 5).let { if (it in listOf(5, 15, 30, 60)) it else 5 }
                    editor.putInt("pause_delay_seconds", delay)
                }
                if (json.has("keep_last_cover_on_pause")) {
                    editor.putBoolean("keep_last_cover_on_pause", json.optBoolean("keep_last_cover_on_pause", false))
                }

                val importedHomeBlur = if (json.has("home_use_album_blur")) {
                    json.optBoolean("home_use_album_blur", false)
                } else {
                    prefs.getBoolean("home_use_album_blur", false)
                }
                var importedLockOnly = if (json.has("lockscreen_only_mode")) {
                    json.optBoolean("lockscreen_only_mode", false)
                } else {
                    prefs.getBoolean("lockscreen_only_mode", false)
                }
                if (importedHomeBlur && importedLockOnly) {
                    importedLockOnly = false
                }
                editor.putBoolean("home_use_album_blur", importedHomeBlur)
                editor.putBoolean("lockscreen_only_mode", importedLockOnly)

                if (json.has("search_mode")) {
                    val importedMode = json.optInt("search_mode", 0).coerceIn(0, 2)
                    editor.putInt("search_mode", importedMode)
                    editor.putBoolean("offline_only_mode", importedMode == 2)
                } else if (json.has("offline_only_mode")) {
                    val isOff = json.optBoolean("offline_only_mode", false)
                    editor.putInt("search_mode", if (isOff) 2 else 0)
                    editor.putBoolean("offline_only_mode", isOff)
                }

                if (json.has("wifi_only_animated")) {
                    editor.putBoolean("wifi_only_animated", json.optBoolean("wifi_only_animated", false))
                }
                if (json.has("pause_on_battery_saver")) {
                    editor.putBoolean("pause_on_battery_saver", json.optBoolean("pause_on_battery_saver", true))
                }
                if (json.has("pixel_dynamic_colors")) {
                    editor.putBoolean("pixel_dynamic_colors", json.optBoolean("pixel_dynamic_colors", false))
                }
                if (json.has("update_notifications_enabled")) {
                    val wantNotif = json.optBoolean("update_notifications_enabled", false) && hasPostNotificationsPermission()
                    editor.putBoolean("update_notifications_enabled", wantNotif)
                }

                val excludedJson = json.optJSONArray("excluded_packages")
                if (excludedJson != null) {
                    val importedSet = HashSet<String>()
                    for (i in 0 until excludedJson.length()) {
                        val pkg = excludedJson.optString(i, "").trim()
                        if (pkg.isNotEmpty()) importedSet.add(pkg)
                    }
                    editor.putStringSet("excluded_packages", importedSet)
                }

                editor.apply()

                runOnUiThread {
                    notifySettingsChanged()
                    Toast.makeText(this, getString(R.string.toast_import_success), Toast.LENGTH_SHORT).show()
                    recreate()
                }
            } catch (_: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_import_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun syncUpdateNotificationToggle() {
        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val enabledInPrefs = prefs.getBoolean("update_notifications_enabled", false)
        if (enabledInPrefs && !hasPostNotificationsPermission()) {
            prefs.edit().putBoolean("update_notifications_enabled", false).apply()
            updateNotifyCheckBox?.isChecked = false
            notifySettingsChanged()
        } else {
            updateNotifyCheckBox?.isChecked = enabledInPrefs
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPostNotificationsCode) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
            if (granted) {
                prefs.edit().putBoolean("update_notifications_enabled", true).apply()
                updateNotifyCheckBox?.isChecked = true
                notifySettingsChanged()
            } else {
                prefs.edit().putBoolean("update_notifications_enabled", false).apply()
                updateNotifyCheckBox?.isChecked = false
                Toast.makeText(this, getString(R.string.toast_notif_perm_denied), Toast.LENGTH_SHORT).show()
                notifySettingsChanged()
            }
        }
    }

    private fun showDonateDialog() {
        val isPortuguese = Locale.getDefault().language.equals("pt", ignoreCase = true)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(8))
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.donate_dialog_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        }

        val msgTv = TextView(this).apply {
            text = getString(R.string.donate_dialog_msg)
            setTextColor(palette.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(0, 0, 0, dp(16))
        }

        val btnPaypal = Button(this).apply {
            text = getString(R.string.btn_donate_paypal)
            isAllCaps = false
            setTextColor(palette.accentOnPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(palette.accent)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paypalDonateUrl)))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        container.addView(titleTv)
        container.addView(msgTv)
        container.addView(btnPaypal)

        if (isPortuguese) {
            val pixCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(palette.diagBg)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), palette.diagStroke)
                }
            }

            val pixTitleTv = TextView(this).apply {
                text = getString(R.string.donate_pix_title)
                setTextColor(palette.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
            }

            val pixKeyTv = TextView(this).apply {
                text = pixDonateKey
                setTextColor(palette.diagText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, 0, 0, dp(10))
            }

            val btnCopyPix = Button(this).apply {
                text = getString(R.string.btn_copy_pix)
                isAllCaps = false
                setTextColor(palette.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(palette.adbBtnBg)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), palette.adbBtnStroke)
                }
                setPadding(dp(12), dp(9), dp(12), dp(9))
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MusicWallpaper Pix", pixDonateKey))
                    Toast.makeText(this@MainActivity, getString(R.string.toast_pix_copied), Toast.LENGTH_SHORT).show()
                }
            }

            pixCard.addView(pixTitleTv)
            pixCard.addView(pixKeyTv)
            pixCard.addView(btnCopyPix)

            container.addView(createSpacer(14))
            container.addView(pixCard)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(getString(R.string.btn_done)) { d, _ -> d.dismiss() }
            .create()

        dialog.show()
        styleThemeDialog(dialog)
    }

    private class LockscreenPreviewView(
        context: Context,
        private val sampleDateText: String,
        frameBorderColor: Int
    ) : View(context) {

        private var topOffsetFrac = 0.20f
        private var blurFrac = 1.00f
        private var blurSatBoostFrac = 0.00f
        private var expansionFrac = 0.00f
        private var dimFrac = 0.00f
        private var clockGradientEnabled = false
        private var bottomGradientEnabled = false
        private var showClockOverlay = true

        private var sharpCoverBitmap: Bitmap? = null
        private var blurredCoverBitmap: Bitmap? = null

        private val clipPath = Path()
        private val frameRect = RectF()
        private val srcRect = Rect()
        private val dstRect = RectF()
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = frameBorderColor
        }
        private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6FFFFFF")
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#99FFFFFF")
        }

        fun setCoverArtwork(sharp: Bitmap?, blurred: Bitmap?) {
            sharpCoverBitmap = sharp
            blurredCoverBitmap = blurred
            invalidate()
        }

        fun updateParams(
            topOffsetPercent: Int,
            blurPercent: Int,
            blurSatBoostPercent: Int,
            expansionPercent: Int,
            dimPercent: Int,
            clockGradient: Boolean,
            bottomGradient: Boolean,
            showClock: Boolean = true
        ) {
            topOffsetFrac = topOffsetPercent.coerceIn(0, 60) / 100f
            blurFrac = blurPercent.coerceIn(60, 100) / 100f
            blurSatBoostFrac = blurSatBoostPercent.coerceIn(0, 60) / 100f
            expansionFrac = expansionPercent.coerceIn(0, 100) / 100f
            dimFrac = dimPercent.coerceIn(0, 40) / 100f
            clockGradientEnabled = clockGradient
            bottomGradientEnabled = bottomGradient
            showClockOverlay = showClock
            invalidate()
        }

        private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        private fun boostColorSaturation(color: Int, boostFrac: Float): Int {
            if (boostFrac <= 0.001f) return color
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[1] = (hsv[1] * (1f + boostFrac * 1.15f)).coerceIn(0f, 1f)
            return Color.HSVToColor(hsv)
        }

        private fun drawBitmapCenterCrop(canvas: Canvas, bmp: Bitmap, target: RectF, paint: Paint) {
            val bw = bmp.width.toFloat()
            val bh = bmp.height.toFloat()
            val tw = target.width()
            val th = target.height()
            if (bw <= 0f || bh <= 0f || tw <= 0f || th <= 0f) return

            val bmpAspect = bw / bh
            val targetAspect = tw / th
            if (bmpAspect > targetAspect) {
                val cropW = (bh * targetAspect).toInt().coerceIn(1, bmp.width)
                val left = (bmp.width - cropW) / 2
                srcRect.set(left, 0, left + cropW, bmp.height)
            } else {
                val cropH = (bw / targetAspect).toInt().coerceIn(1, bmp.height)
                val top = (bmp.height - cropH) / 2
                srcRect.set(0, top, bmp.width, top + cropH)
            }
            canvas.drawBitmap(bmp, srcRect, target, paint)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val density = resources.displayMetrics.density
            val cornerRadius = 22f * density
            frameRect.set(0f, 0f, w, h)

            canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.clipPath(clipPath)

            val baseBoxAspect = 3f / 4f
            val baseHeightRatio = ((w / baseBoxAspect) / h).coerceIn(0.45f, 0.68f)
            val targetHeightRatio = lerp(baseHeightRatio, 0.95f, expansionFrac)
            val topOffset = lerp(topOffsetFrac, 0.025f, expansionFrac)

            val coverTop = topOffset * h
            val coverHeight = targetHeightRatio * h
            val coverBottom = coverTop + coverHeight

            val fadeBand = lerp(0.15f, 0.045f, expansionFrac) * coverHeight
            val bottomFadeBand = lerp(0.18f, 0.06f, expansionFrac) * coverHeight

            val realSharp = sharpCoverBitmap
            val realBlur = blurredCoverBitmap

            if (realSharp != null && !realSharp.isRecycled && realBlur != null && !realBlur.isRecycled) {
                if (blurSatBoostFrac > 0.001f) {
                    val cm = ColorMatrix().apply { setSaturation(1f + blurSatBoostFrac) }
                    bitmapPaint.colorFilter = ColorMatrixColorFilter(cm)
                } else {
                    bitmapPaint.colorFilter = null
                }
                drawBitmapCenterCrop(canvas, realBlur, frameRect, bitmapPaint)
                bitmapPaint.colorFilter = null

                dstRect.set(0f, coverTop, w, coverBottom)
                val layerId = canvas.saveLayer(dstRect, null)
                drawBitmapCenterCrop(canvas, realSharp, dstRect, bitmapPaint)

                val topFadeEndFrac = if (topOffset > 0.005f) (fadeBand / coverHeight).coerceIn(0.01f, 0.45f) else 0f
                val bottomFadeStartFrac = if ((topOffset + targetHeightRatio) < 0.995f) {
                    (1f - (bottomFadeBand / coverHeight)).coerceIn(0.55f, 0.99f)
                } else {
                    1f
                }

                val colors = when {
                    topFadeEndFrac > 0f && bottomFadeStartFrac < 1f ->
                        intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT)
                    topFadeEndFrac > 0f ->
                        intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK)
                    bottomFadeStartFrac < 1f ->
                        intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT)
                    else ->
                        intArrayOf(Color.BLACK, Color.BLACK)
                }
                val positions = when {
                    topFadeEndFrac > 0f && bottomFadeStartFrac < 1f ->
                        floatArrayOf(0f, topFadeEndFrac, bottomFadeStartFrac, 1f)
                    topFadeEndFrac > 0f ->
                        floatArrayOf(0f, topFadeEndFrac, 1f)
                    bottomFadeStartFrac < 1f ->
                        floatArrayOf(0f, bottomFadeStartFrac, 1f)
                    else ->
                        floatArrayOf(0f, 1f)
                }

                maskPaint.shader = LinearGradient(
                    0f, coverTop, 0f, coverBottom,
                    colors, positions, Shader.TileMode.CLAMP
                )
                canvas.drawRect(dstRect, maskPaint)
                maskPaint.shader = null
                canvas.restoreToCount(layerId)
            } else {
                val blurSoftness = ((blurFrac - 0.6f) / 0.4f).coerceIn(0f, 1f)
                val bgTopColor = boostColorSaturation(
                    Color.rgb(
                        (52 + blurSoftness * 18).toInt(),
                        (76 + blurSoftness * 16).toInt(),
                        (118 + blurSoftness * 14).toInt()
                    ),
                    blurSatBoostFrac
                )
                val bgMidColor = boostColorSaturation(
                    Color.rgb(
                        (96 + blurSoftness * 10).toInt(),
                        (64 + blurSoftness * 12).toInt(),
                        (108 + blurSoftness * 10).toInt()
                    ),
                    blurSatBoostFrac
                )
                val bgBotColor = boostColorSaturation(
                    Color.rgb(
                        (38 + blurSoftness * 12).toInt(),
                        (44 + blurSoftness * 14).toInt(),
                        (72 + blurSoftness * 14).toInt()
                    ),
                    blurSatBoostFrac
                )
                fillPaint.shader = LinearGradient(
                    0f, 0f, w, h,
                    intArrayOf(bgTopColor, bgMidColor, bgBotColor),
                    floatArrayOf(0f, 0.52f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(frameRect, fillPaint)
                fillPaint.shader = null

                canvas.save()
                canvas.clipRect(0f, coverTop, w, coverBottom)

                fillPaint.shader = LinearGradient(
                    0f, coverTop, w, coverBottom,
                    intArrayOf(
                        Color.parseColor("#5C7AEA"),
                        Color.parseColor("#A66CFF"),
                        Color.parseColor("#F2789F")
                    ),
                    floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, coverTop, w, coverBottom, fillPaint)
                fillPaint.shader = null

                val cx = w * 0.5f
                val cy = coverTop + coverHeight * 0.5f
                val discRadius = (w * 0.31f)
                fillPaint.color = Color.parseColor("#33FFFFFF")
                canvas.drawCircle(cx, cy, discRadius, fillPaint)
                fillPaint.color = Color.parseColor("#440D0F14")
                canvas.drawCircle(cx, cy, discRadius * 0.42f, fillPaint)
                fillPaint.color = Color.parseColor("#99FFFFFF")
                canvas.drawCircle(cx, cy, discRadius * 0.14f, fillPaint)
                canvas.restore()

                if (topOffset > 0.005f) {
                    fillPaint.shader = LinearGradient(
                        0f, coverTop - 2f, 0f, coverTop + fadeBand,
                        bgTopColor, Color.TRANSPARENT, Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, coverTop - 2f, w, coverTop + fadeBand, fillPaint)
                }
                if ((topOffset + targetHeightRatio) < 0.995f) {
                    fillPaint.shader = LinearGradient(
                        0f, coverBottom - bottomFadeBand, 0f, coverBottom + 2f,
                        Color.TRANSPARENT, bgBotColor, Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, coverBottom - bottomFadeBand, w, coverBottom + 2f, fillPaint)
                }
                fillPaint.shader = null
            }

            if (dimFrac > 0.001f) {
                fillPaint.color = Color.argb((dimFrac * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
                canvas.drawRect(frameRect, fillPaint)
            }

            if (clockGradientEnabled) {
                fillPaint.shader = LinearGradient(
                    0f, 0f, 0f, h * 0.34f,
                    Color.argb((0.42f * 255f).toInt(), 0, 0, 0),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h * 0.34f, fillPaint)
                fillPaint.shader = null
            }

            if (bottomGradientEnabled) {
                fillPaint.shader = LinearGradient(
                    0f, h * 0.85f, 0f, h,
                    Color.TRANSPARENT,
                    Color.argb((0.42f * 255f).toInt(), 0, 0, 0),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, h * 0.85f, w, h, fillPaint)
                fillPaint.shader = null
            }

            val camRadius = 3.2f * density
            fillPaint.color = Color.parseColor("#AA0D0F14")
            canvas.drawCircle(w * 0.5f, 10f * density, camRadius, fillPaint)

            if (showClockOverlay) {
                clockPaint.textSize = 27f * density
                clockPaint.setShadowLayer(4f * density, 0f, 1.5f * density, Color.parseColor("#55000000"))
                canvas.drawText("09:41", w * 0.5f, h * 0.21f, clockPaint)

                datePaint.textSize = 8.5f * density
                datePaint.setShadowLayer(3f * density, 0f, 1f * density, Color.parseColor("#55000000"))
                canvas.drawText(sampleDateText, w * 0.5f, h * 0.255f, datePaint)
            }

            val pillW = w * 0.34f
            val pillH = 2.6f * density
            val pillTop = h - 8f * density
            canvas.drawRoundRect(
                RectF((w - pillW) * 0.5f, pillTop, (w + pillW) * 0.5f, pillTop + pillH),
                pillH, pillH, pillPaint
            )

            canvas.restore()

            framePaint.strokeWidth = 2f * density
            val inset = density
            canvas.drawRoundRect(
                RectF(inset, inset, w - inset, h - inset),
                cornerRadius, cornerRadius, framePaint
            )
        }
    }

    private fun updateCacheSizeUI() {
        thread {
            val totalBytes = calculateDirectorySize(File(cacheDir, "artwork_cache"))
            val mb = totalBytes / (1024.0 * 1024.0)
            val formatted = String.format(Locale.US, "%.1f MB", mb)
            runOnUiThread {
                btnClearCache?.text = getString(R.string.btn_clear_cache, formatted)
            }
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            total += if (f.isDirectory) calculateDirectorySize(f) else f.length()
        }
        return total
    }

    private fun showFaqDialog() {
        val faqItems = listOf(
            Pair(getString(R.string.faq_q1), getString(R.string.faq_a1)),
            Pair(getString(R.string.faq_q2), getString(R.string.faq_a2)),
            Pair(getString(R.string.faq_q3), getString(R.string.faq_a3)),
            Pair(getString(R.string.faq_q4), getString(R.string.faq_a4)),
            Pair(getString(R.string.faq_q5), getString(R.string.faq_a5)),
            Pair(getString(R.string.faq_q6), getString(R.string.faq_a6)),
            Pair(getString(R.string.faq_q7), getString(R.string.faq_a7)),
            Pair(getString(R.string.faq_q8), getString(R.string.faq_a8))
        )

        val outerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(8))
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.faq_dialog_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(2), 0, dp(2), dp(12))
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(440)
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        faqItems.forEachIndexed { index, (question, answer) ->
            val itemCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(palette.diagBg)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), palette.diagStroke)
                }
            }

            val qTv = TextView(this).apply {
                text = question
                setTextColor(palette.accent)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
            }

            val aTv = TextView(this).apply {
                text = answer
                setTextColor(palette.bodyText)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                setLineSpacing(dp(2).toFloat(), 1.12f)
                setTextIsSelectable(true)
            }

            itemCard.addView(qTv)
            itemCard.addView(aTv)

            if (index == 3) {
                val btnCopyAdb = Button(this).apply {
                    text = getString(R.string.btn_copy_adb)
                    isAllCaps = false
                    setTextColor(palette.accent)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    background = GradientDrawable().apply {
                        setColor(palette.adbBtnBg)
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), palette.adbBtnStroke)
                    }
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                    }
                    setOnClickListener {
                        val adbCommands = "adb shell appops set com.lucasvinicius.musicwallpaper2 ACCESS_RESTRICTED_SETTINGS allow\n" +
                                "adb shell cmd notification allow_listener com.lucasvinicius.musicwallpaper2/com.lucasvinicius.musicwallpaper2.MusicNotificationListener"
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("MusicWallpaper ADB", adbCommands))
                        Toast.makeText(this@MainActivity, getString(R.string.toast_adb_copied), Toast.LENGTH_SHORT).show()
                    }
                }
                itemCard.addView(btnCopyAdb)
            }

            listContainer.addView(itemCard)
            if (index < faqItems.lastIndex) {
                listContainer.addView(createSpacer(10))
            }
        }

        scroll.addView(listContainer)
        outerContainer.addView(titleTv)
        outerContainer.addView(scroll)

        val dialog = AlertDialog.Builder(this)
            .setView(outerContainer)
            .setPositiveButton(getString(R.string.btn_done)) { d, _ -> d.dismiss() }
            .create()

        dialog.show()
        styleThemeDialog(dialog)
    }

    private fun isGooglePixel7OrNewer(): Boolean {
        val isGoogle = Build.BRAND.equals("google", ignoreCase = true) ||
                Build.MANUFACTURER.equals("google", ignoreCase = true)
        if (!isGoogle) return false
        val model = Build.MODEL?.lowercase() ?: ""
        if (!model.contains("pixel")) return false
        val numMatch = Regex("""pixel\s*(\d+)""").find(model)?.groupValues?.get(1)?.toIntOrNull()
        if (numMatch != null) return numMatch >= 7
        return model.contains("fold") || model.contains("tablet")
    }

    private fun createToggleRow(
        title: String,
        desc: String,
        initialChecked: Boolean,
        onToggle: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.toggleRowBg)
                cornerRadius = dp(12).toFloat()
            }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            }
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val descTv = TextView(this).apply {
            text = desc
            setTextColor(palette.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(titleTv)
        textCol.addView(descTv)

        val chk = CheckBox(this).apply {
            isChecked = initialChecked
            buttonTintList = ColorStateList.valueOf(palette.accent)
            isClickable = false
            isFocusable = false
        }

        row.addView(textCol)
        row.addView(chk)

        row.setOnClickListener {
            val newState = !chk.isChecked
            chk.isChecked = newState
            onToggle(newState)
        }

        return row
    }

    override fun onStart() {
        super.onStart()
        try {
            val filter = IntentFilter("com.lucasvinicius.musicwallpaper2.ACTION_DEBUG_METADATA_UPDATED")
            ContextCompat.registerReceiver(this, debugUpdateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (_: Throwable) {}
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(debugUpdateReceiver)
        } catch (_: Throwable) {}
    }

    private fun updateDiagnosticUI() {
        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        var pkg = prefs.getString("debug_last_pkg", "") ?: ""
        var rawTitle = prefs.getString("debug_raw_title", "") ?: ""
        var rawArtist = prefs.getString("debug_raw_artist", "") ?: ""
        var rawAlbum = prefs.getString("debug_raw_album", "") ?: ""
        var parsedArtist = prefs.getString("debug_parsed_artist", "") ?: ""
        var parsedAlbum = prefs.getString("debug_parsed_album", "") ?: ""
        var parsedTitle = prefs.getString("debug_parsed_title", "") ?: ""
        var resultStatus = prefs.getString("debug_result_status", "") ?: ""

        if (isNotificationListenerEnabled()) {
            try {
                val excluded = prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()
                val cn = ComponentName(this, MusicNotificationListener::class.java)
                val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val active = msm.getActiveSessions(cn).firstOrNull {
                    it.packageName !in excluded && it.playbackState?.state == PlaybackState.STATE_PLAYING
                }
                if (active != null && active.metadata != null) {
                    val m = active.metadata!!
                    val liveTitle = (m.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "").trim()
                    val liveArtist = (m.getString(MediaMetadata.METADATA_KEY_ARTIST)
                        ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                        ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "").trim()
                    val liveAlbum = (m.getString(MediaMetadata.METADATA_KEY_ALBUM)
                        ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION) ?: "").trim()

                    if (pkg.isEmpty() || (liveTitle.isNotEmpty() && liveTitle != rawTitle)) {
                        pkg = active.packageName
                        rawTitle = liveTitle
                        rawArtist = liveArtist
                        rawAlbum = liveAlbum
                        if (parsedTitle.isEmpty()) parsedTitle = liveTitle
                        if (parsedArtist.isEmpty()) parsedArtist = liveArtist
                        if (parsedAlbum.isEmpty()) parsedAlbum = liveAlbum
                        if (resultStatus.isEmpty()) resultStatus = "Active in MediaSession"
                    }
                }
            } catch (_: Throwable) {}
        }

        if (pkg.isEmpty() && rawTitle.isEmpty() && rawArtist.isEmpty()) {
            diagBodyTv?.text = getString(R.string.diag_box_empty)
            return
        }

        val info = buildString {
            appendLine("App: $pkg")
            appendLine("Raw Title : \"$rawTitle\"")
            appendLine("Raw Artist: \"$rawArtist\"")
            appendLine("Raw Album : \"$rawAlbum\"")
            appendLine("Parsed    : Art=\"$parsedArtist\" | Alb=\"$parsedAlbum\" | Trk=\"$parsedTitle\"")
            if (resultStatus.isNotEmpty()) {
                append("Status    : $resultStatus")
            }
        }.trimEnd()

        diagBodyTv?.text = info
    }

    private fun showRestrictedSettingsHelpDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(8))
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.restricted_help_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }

        val msgTv = TextView(this).apply {
            text = getString(R.string.restricted_help_msg)
            setTextColor(palette.bodyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(dp(2).toFloat(), 1.15f)
        }

        container.addView(titleTv)
        container.addView(msgTv)

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setNegativeButton(getString(R.string.btn_done), null)
            .setPositiveButton(getString(R.string.btn_open_app_info)) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
            .create()

        dialog.show()
        styleThemeDialog(dialog)
    }

    private fun styleThemeDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(palette.cardBg)
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), palette.dialogStroke)
        })
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(palette.accent)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            setTextColor(palette.secondaryText)
        }
    }

    private fun getInstalledVersionName(): String {
        return try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (_: Throwable) {
            "1.0"
        }
    }

    private fun checkForGitHubUpdates(isManualCheck: Boolean) {
        val currentVersion = getInstalledVersionName()
        thread {
            try {
                val url = URL("https://api.github.com/repos/$githubOwner/$githubRepo/releases/latest")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "MusicWallpaper-Android")
                }

                if (conn.responseCode in 200..299) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val latestTag = json.optString("tag_name", "").removePrefix("v").removePrefix("V").trim()
                    val releaseUrl = json.optString("html_url", "https://github.com/$githubOwner/$githubRepo/releases")
                    val releaseNotes = json.optString("body", "").trim()

                    var downloadUrl = releaseUrl
                    val assets = json.optJSONArray("assets")
                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val assetObj = assets.getJSONObject(i)
                            val browserUrl = assetObj.optString("browser_download_url", "")
                            if (browserUrl.endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = browserUrl
                                break
                            }
                        }
                    }

                    if (latestTag.isNotEmpty() && isNewerVersion(currentVersion, latestTag)) {
                        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
                        val lastDismissed = prefs.getString("dismissed_update_tag", "")

                        if (!isManualCheck && lastDismissed == latestTag) {
                            return@thread
                        }

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            val notesPreview = if (releaseNotes.length > 600) {
                                releaseNotes.take(600) + "..."
                            } else {
                                releaseNotes.ifEmpty { "Melhorias de desempenho e estabilidade." }
                            }

                            val dialogContainer = LinearLayout(this).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(22), dp(22), dp(22), dp(8))
                            }

                            val titleTv = TextView(this).apply {
                                text = getString(R.string.update_dialog_title, latestTag)
                                setTextColor(palette.primaryText)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                                setPadding(0, 0, 0, dp(12))
                            }

                            val msgScroll = ScrollView(this).apply {
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                            }

                            val msgTv = TextView(this).apply {
                                text = getString(R.string.update_dialog_msg, notesPreview)
                                setTextColor(palette.bodyText)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                                setLineSpacing(dp(2).toFloat(), 1.1f)
                            }

                            msgScroll.addView(msgTv)
                            dialogContainer.addView(titleTv)
                            dialogContainer.addView(msgScroll)

                            val dialog = AlertDialog.Builder(this)
                                .setView(dialogContainer)
                                .setNegativeButton(getString(R.string.btn_later)) { d, _ ->
                                    prefs.edit().putString("dismissed_update_tag", latestTag).apply()
                                    d.dismiss()
                                }
                                .setPositiveButton(getString(R.string.btn_download_update)) { _, _ ->
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                                    } catch (_: Exception) {
                                        Toast.makeText(this, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .create()

                            dialog.show()
                            styleThemeDialog(dialog)
                        }
                    } else if (isManualCheck) {
                        runOnUiThread {
                            Toast.makeText(this, getString(R.string.toast_up_to_date, currentVersion), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (isManualCheck) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.toast_update_check_error), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Throwable) {
                if (isManualCheck) {
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.toast_update_check_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val cleanCurr = current.replace(Regex("[^0-9.]"), "")
        val cleanLate = latest.replace(Regex("[^0-9.]"), "")
        if (cleanCurr.isEmpty() || cleanLate.isEmpty()) return false

        val currParts = cleanCurr.split(".").map { it.toIntOrNull() ?: 0 }
        val lateParts = cleanLate.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = max(currParts.size, lateParts.size)

        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val l = lateParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun showCropDialogForUri(uri: Uri, targetFileName: String) {
        thread {
            val previewBitmap = loadHighResBitmapCorrectOrientation(uri, maxAllowedDim = 4096)
            if (previewBitmap == null) {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
                return@thread
            }

            runOnUiThread {
                val realMetrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(realMetrics)
                val screenW = realMetrics.widthPixels.coerceAtLeast(1080)
                val screenH = realMetrics.heightPixels.coerceAtLeast(2400)
                val screenAspect = screenW.toFloat() / screenH.toFloat()

                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setBackgroundColor(palette.cardBg)
                    setPadding(dp(16), dp(20), dp(16), dp(8))
                }

                val dialogTitleTv = TextView(this).apply {
                    text = getString(R.string.crop_dialog_title)
                    setTextColor(palette.primaryText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(6))
                }

                val hintTv = TextView(this).apply {
                    text = getString(R.string.crop_dialog_hint)
                    setTextColor(palette.secondaryText)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(12))
                }

                val maxCropHeightPx = (screenH * 0.56f).toInt()
                val maxCropWidthPx = (screenW * 0.78f).toInt()
                var cropViewW = (maxCropHeightPx * screenAspect).toInt()
                var cropViewH = maxCropHeightPx
                if (cropViewW > maxCropWidthPx) {
                    cropViewW = maxCropWidthPx
                    cropViewH = (cropViewW / screenAspect).toInt()
                }

                val cropView = InteractiveCropView(this, previewBitmap, palette.accent).apply {
                    layoutParams = LinearLayout.LayoutParams(cropViewW, cropViewH)
                }

                container.addView(dialogTitleTv)
                container.addView(hintTv)
                container.addView(cropView)

                val dialog = AlertDialog.Builder(this)
                    .setView(container)
                    .setNegativeButton(getString(R.string.btn_cancel)) { d, _ ->
                        d.dismiss()
                    }
                    .setPositiveButton(getString(R.string.btn_save_crop)) { d, _ ->
                        val normRect = cropView.getNormalizedCropRect()
                        saveLosslessCroppedWallpaper(uri, previewBitmap, normRect, targetFileName)
                        d.dismiss()
                    }
                    .create()

                dialog.show()
                styleThemeDialog(dialog)
            }
        }
    }

    private class InteractiveCropView(
        context: Context,
        private val bitmap: Bitmap,
        borderColor: Int
    ) : View(context) {

        private val matrix = Matrix()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = borderColor
            strokeWidth = 4f
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#44FFFFFF")
            strokeWidth = 2f
        }

        private var minScale = 1f
        private var maxScale = 8f
        private var currentScale = 1f
        private var transX = 0f
        private var transY = 0f

        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var activePointerId = MotionEvent.INVALID_POINTER_ID

        private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val prevScale = currentScale
                currentScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                val factor = currentScale / prevScale

                transX = detector.focusX - (detector.focusX - transX) * factor
                transY = detector.focusY - (detector.focusY - transY) * factor

                constrainTranslation()
                invalidate()
                return true
            }
        })

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val scaleX = w.toFloat() / bitmap.width
                val scaleY = h.toFloat() / bitmap.height
                minScale = max(scaleX, scaleY)
                maxScale = minScale * 8f
                currentScale = minScale

                transX = (w - bitmap.width * currentScale) * 0.5f
                transY = (h - bitmap.height * currentScale) * 0.5f
                constrainTranslation()
            }
        }

        private fun constrainTranslation() {
            val scaledW = bitmap.width * currentScale
            val scaledH = bitmap.height * currentScale

            val minX = width - scaledW
            val minY = height - scaledH

            transX = if (scaledW <= width) (width - scaledW) * 0.5f else transX.coerceIn(minX, 0f)
            transY = if (scaledH <= height) (height - scaledH) * 0.5f else transY.coerceIn(minY, 0f)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    activePointerId = event.getPointerId(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!scaleDetector.isInProgress) {
                        val idx = event.findPointerIndex(activePointerId)
                        if (idx != -1) {
                            val dx = event.getX(idx) - lastTouchX
                            val dy = event.getY(idx) - lastTouchY
                            transX += dx
                            transY += dy
                            constrainTranslation()
                            invalidate()
                            lastTouchX = event.getX(idx)
                            lastTouchY = event.getY(idx)
                        }
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId == activePointerId) {
                        val newPointerIndex = if (pointerIndex == 0) 1 else 0
                        lastTouchX = event.getX(newPointerIndex)
                        lastTouchY = event.getY(newPointerIndex)
                        activePointerId = event.getPointerId(newPointerIndex)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.BLACK)

            matrix.reset()
            matrix.postScale(currentScale, currentScale)
            matrix.postTranslate(transX, transY)
            canvas.drawBitmap(bitmap, matrix, paint)

            val thirdW = width / 3f
            val thirdH = height / 3f
            canvas.drawLine(thirdW, 0f, thirdW, height.toFloat(), gridPaint)
            canvas.drawLine(thirdW * 2f, 0f, thirdW * 2f, height.toFloat(), gridPaint)
            canvas.drawLine(0f, thirdH, width.toFloat(), thirdH, gridPaint)
            canvas.drawLine(0f, thirdH * 2f, width.toFloat(), thirdH * 2f, gridPaint)

            canvas.drawRect(2f, 2f, width - 2f, height - 2f, borderPaint)
        }

        fun getNormalizedCropRect(): RectF {
            val scaledW = bitmap.width * currentScale
            val scaledH = bitmap.height * currentScale
            val left = (-transX / scaledW).coerceIn(0f, 1f)
            val top = (-transY / scaledH).coerceIn(0f, 1f)
            val right = ((width - transX) / scaledW).coerceIn(0f, 1f)
            val bottom = ((height - transY) / scaledH).coerceIn(0f, 1f)
            return RectF(left, top, right, bottom)
        }
    }

    private fun loadHighResBitmapCorrectOrientation(uri: Uri, maxAllowedDim: Int = 8192): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            var sampleSize = 1
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            while (maxDim / sampleSize > maxAllowedDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }

            val rawBitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val rotation = contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotation != 0f) {
                val rotMatrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, rotMatrix, true)
            } else {
                rawBitmap
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveLosslessCroppedWallpaper(
        uri: Uri,
        fallbackPreview: Bitmap,
        normRect: RectF,
        targetFileName: String
    ) {
        thread {
            try {
                val masterBitmap = loadHighResBitmapCorrectOrientation(uri, maxAllowedDim = 8192) ?: fallbackPreview

                val srcLeft = (normRect.left * masterBitmap.width).toInt().coerceIn(0, masterBitmap.width - 1)
                val srcTop = (normRect.top * masterBitmap.height).toInt().coerceIn(0, masterBitmap.height - 1)
                val srcWidth = ((normRect.right - normRect.left) * masterBitmap.width).toInt().coerceIn(1, masterBitmap.width - srcLeft)
                val srcHeight = ((normRect.bottom - normRect.top) * masterBitmap.height).toInt().coerceIn(1, masterBitmap.height - srcTop)

                val exactSubBitmap = Bitmap.createBitmap(masterBitmap, srcLeft, srcTop, srcWidth, srcHeight)
                val maxGlDim = max(exactSubBitmap.width, exactSubBitmap.height)
                val finalBitmapToSave = if (maxGlDim > 4096) {
                    val scale = 4096f / maxGlDim
                    Bitmap.createScaledBitmap(
                        exactSubBitmap,
                        (exactSubBitmap.width * scale).toInt().coerceAtLeast(1),
                        (exactSubBitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    exactSubBitmap
                }

                if (targetFileName == "default_wallpaper.png") {
                    File(filesDir, "default_wallpaper.jpg").delete()
                }
                val destFile = File(filesDir, targetFileName)
                FileOutputStream(destFile).use { out ->
                    finalBitmapToSave.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                runOnUiThread {
                    updatePhotoStatusUI()
                    Toast.makeText(this, getString(R.string.toast_photo_saved), Toast.LENGTH_SHORT).show()
                    sendBroadcast(Intent("com.lucasvinicius.musicwallpaper2.ACTION_DEFAULT_WALLPAPER_UPDATED").apply {
                        setPackage(packageName)
                    })
                }
            } catch (e: Throwable) {
                runOnUiThread {
                    Toast.makeText(this, "${getString(R.string.toast_error_generic)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showExcludedAppsDialog() {
        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val excludedSet = (prefs.getStringSet("excluded_packages", emptySet()) ?: emptySet()).toMutableSet()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.cardBg)
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.dialog_excluded_title)
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), dp(12))
        }

        val searchBox = EditText(this).apply {
            hint = getString(R.string.search_apps_hint)
            setHintTextColor(palette.mutedText)
            setTextColor(palette.primaryText)
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(palette.inputBg)
                cornerRadius = dp(12).toFloat()
            }
        }

        val listView = ListView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(380)).apply {
                topMargin = dp(12)
            }
            dividerHeight = 0
        }

        container.addView(titleTv)
        container.addView(searchBox)
        container.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton(getString(R.string.btn_done)) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.show()
        styleThemeDialog(dialog)

        thread {
            val pm = packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val allApps = resolveInfos
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { appInfo ->
                    AppItem(
                        name = pm.getApplicationLabel(appInfo).toString(),
                        packageName = appInfo.packageName,
                        icon = pm.getApplicationIcon(appInfo)
                    )
                }
                .sortedWith(compareByDescending<AppItem> { excludedSet.contains(it.packageName) }.thenBy { it.name.lowercase() })

            val filteredApps = allApps.toMutableList()

            runOnUiThread {
                val adapter = object : ArrayAdapter<AppItem>(this, 0, filteredApps) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val item = filteredApps[position]
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(8), dp(10), dp(8), dp(10))
                        }

                        val iconView = ImageView(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                                marginEnd = dp(12)
                            }
                            setImageDrawable(item.icon)
                        }

                        val textCol = LinearLayout(context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val nameTv = TextView(context).apply {
                            text = item.name
                            setTextColor(palette.primaryText)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        }

                        val pkgTv = TextView(context).apply {
                            text = item.packageName
                            setTextColor(palette.mutedText)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        }

                        textCol.addView(nameTv)
                        textCol.addView(pkgTv)

                        val checkBox = CheckBox(context).apply {
                            isChecked = excludedSet.contains(item.packageName)
                            buttonTintList = ColorStateList.valueOf(palette.accent)
                            isClickable = false
                            isFocusable = false
                        }

                        row.addView(iconView)
                        row.addView(textCol)
                        row.addView(checkBox)

                        row.setOnClickListener {
                            val nowChecked = !checkBox.isChecked
                            checkBox.isChecked = nowChecked
                            if (nowChecked) {
                                excludedSet.add(item.packageName)
                            } else {
                                excludedSet.remove(item.packageName)
                            }
                            prefs.edit().putStringSet("excluded_packages", HashSet(excludedSet)).apply()
                            updateExcludedButtonCount()
                            notifySettingsChanged()
                        }

                        return row
                    }
                }

                listView.adapter = adapter

                searchBox.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val query = s?.toString()?.trim()?.lowercase() ?: ""
                        filteredApps.clear()
                        if (query.isEmpty()) {
                            filteredApps.addAll(allApps)
                        } else {
                            filteredApps.addAll(allApps.filter {
                                it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                            })
                        }
                        adapter.notifyDataSetChanged()
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
        }
    }

    private fun updateExcludedButtonCount() {
        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val count = prefs.getStringSet("excluded_packages", emptySet())?.size ?: 0
        btnExcludedApps?.text = getString(R.string.btn_excluded_apps, count)
    }

    private fun notifySettingsChanged() {
        sendBroadcast(Intent("com.lucasvinicius.musicwallpaper2.ACTION_SETTINGS_UPDATED").apply {
            setPackage(packageName)
        })
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatusUI()
        syncUpdateNotificationToggle()
        updatePhotoStatusUI()
        updateExcludedButtonCount()
        updateDiagnosticUI()
        updateCacheSizeUI()
        loadLatestCachedCoverForPreview()
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val cn = ComponentName(this, MusicNotificationListener::class.java).flattenToString()
        return flat.contains(cn)
    }

    private fun updatePermissionStatusUI() {
        val granted = isNotificationListenerEnabled()
        permStatusBadge?.apply {
            if (granted) {
                text = getString(R.string.perm_status_ok)
                setTextColor(palette.badgeOk)
            } else {
                text = getString(R.string.perm_status_missing)
                setTextColor(palette.badgeMissing)
            }
        }
        val helpVisibility = if (granted) View.GONE else View.VISIBLE
        btnRestrictedHelp?.visibility = helpVisibility
        restrictedHelpSpacer?.visibility = helpVisibility
    }

    private fun createCardLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(palette.cardBg)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), palette.cardStroke)
            }
        }
    }

    private fun createCardTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(palette.primaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    private fun createCardDescription(desc: String): TextView {
        return TextView(this).apply {
            text = desc
            setTextColor(palette.secondaryText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun createSecondaryButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(palette.secondaryBtnText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                setColor(palette.secondaryBtnBg)
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            when (requestCode) {
                pickLockscreenImageRequest -> showCropDialogForUri(uri, "default_wallpaper.png")
                pickHomeImageRequest -> showCropDialogForUri(uri, "home_wallpaper.png")
                pickHomeBlurImageRequest -> showCropDialogForUri(uri, "home_blur_wallpaper.png")
                exportSettingsRequest -> exportSettingsToUri(uri)
                importSettingsRequest -> importSettingsFromUri(uri)
            }
        }
    }

    private fun updatePhotoStatusUI() {
        val pngFile = File(filesDir, "default_wallpaper.png")
        val jpgFile = File(filesDir, "default_wallpaper.jpg")
        val activeLockFile = when {
            pngFile.exists() -> pngFile
            jpgFile.exists() -> jpgFile
            else -> null
        }

        if (activeLockFile != null) {
            statusText?.text = getString(R.string.status_photo_ok)
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val thumb = BitmapFactory.decodeFile(activeLockFile.absolutePath, thumbOpts)
            previewImageView?.setImageBitmap(thumb)
        } else {
            statusText?.text = getString(R.string.status_photo_none)
            previewImageView?.setImageDrawable(null)
        }

        val homeBlurPngFile = File(filesDir, "home_blur_wallpaper.png")
        if (homeBlurPngFile.exists()) {
            homeBlurStatusText?.text = getString(R.string.status_home_blur_photo_ok)
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val thumb = BitmapFactory.decodeFile(homeBlurPngFile.absolutePath, thumbOpts)
            homeBlurPreviewImageView?.setImageBitmap(thumb)
        } else {
            homeBlurStatusText?.text = getString(R.string.status_home_photo_none)
            if (activeLockFile != null) {
                val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val thumb = BitmapFactory.decodeFile(activeLockFile.absolutePath, thumbOpts)
                homeBlurPreviewImageView?.setImageBitmap(thumb)
            } else {
                homeBlurPreviewImageView?.setImageDrawable(null)
            }
        }

        val homePngFile = File(filesDir, "home_wallpaper.png")
        if (homePngFile.exists()) {
            homeStatusText?.text = getString(R.string.status_home_photo_ok)
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val thumb = BitmapFactory.decodeFile(homePngFile.absolutePath, thumbOpts)
            homePreviewImageView?.setImageBitmap(thumb)
        } else {
            homeStatusText?.text = getString(R.string.status_home_photo_none)
            if (activeLockFile != null) {
                val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val thumb = BitmapFactory.decodeFile(activeLockFile.absolutePath, thumbOpts)
                homePreviewImageView?.setImageBitmap(thumb)
            } else {
                homePreviewImageView?.setImageDrawable(null)
            }
        }
    }
}