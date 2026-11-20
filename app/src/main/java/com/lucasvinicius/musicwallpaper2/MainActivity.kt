package com.lucasvinicius.musicwallpaper2

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
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private var permStatusBadge: TextView? = null
    private var btnRestrictedHelp: Button? = null
    private var restrictedHelpSpacer: View? = null
    private var btnExcludedApps: Button? = null
    private var diagBodyTv: TextView? = null

    private var statusText: TextView? = null
    private var previewImageView: ImageView? = null

    private var homePhotoContainer: LinearLayout? = null
    private var homeStatusText: TextView? = null
    private var homePreviewImageView: ImageView? = null

    private var homeBlurPhotoContainer: LinearLayout? = null
    private var homeBlurStatusText: TextView? = null
    private var homeBlurPreviewImageView: ImageView? = null

    private val pickLockscreenImageRequest = 1001
    private val pickHomeImageRequest = 1002
    private val pickHomeBlurImageRequest = 1003

    private val githubOwner = "lviniciuss-maker"
    private val githubRepo = "MusicWallpaper-v2"

    private val debugUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.lucasvinicius.musicwallpaper2.ACTION_DEBUG_METADATA_UPDATED") {
                updateDiagnosticUI()
            }
        }
    }

    data class AppItem(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#0D0F14")
        window.navigationBarColor = Color.parseColor("#0D0F14")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val savedOffsetPercent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 60)
        val savedBlurPercent = prefs.getInt("blur_strength_percent", 100).coerceIn(60, 100)
        val savedExpansionPercent = prefs.getInt("cover_expansion_percent", 0).coerceIn(0, 100)
        val savedOfflineOnly = prefs.getBoolean("offline_only_mode", false)
        val savedKeepLastCover = prefs.getBoolean("keep_last_cover_on_pause", false)
        val savedHomeUseAlbumBlur = prefs.getBoolean("home_use_album_blur", false)
        var savedLockscreenOnly = prefs.getBoolean("lockscreen_only_mode", false)
        if (savedHomeUseAlbumBlur && savedLockscreenOnly) {
            savedLockscreenOnly = false
            prefs.edit().putBoolean("lockscreen_only_mode", false).apply()
        }
        val savedPixelColors = prefs.getBoolean("pixel_dynamic_colors", false)
        val currentAppVersion = getInstalledVersionName()

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0F14"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(56), dp(20), dp(36))
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val subtitleText = TextView(this).apply {
            text = getString(R.string.app_subtitle)
            setTextColor(Color.parseColor("#9AA0B4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, dp(4), 0, dp(24))
        }

        rootLayout.addView(titleText)
        rootLayout.addView(subtitleText)

        // =========================================================================
        // CARTÃO 1: PERMISSÃO + EXCEÇÕES + MODO OFFLINE + PIXEL 7+ + DIAGNÓSTICO
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
            setTextColor(Color.parseColor("#FDD663"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A261D"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#4D4329"))
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnClickListener {
                showRestrictedSettingsHelpDialog()
            }
        }

        btnExcludedApps = createSecondaryButton("").apply {
            setOnClickListener {
                showExcludedAppsDialog()
            }
        }
        updateExcludedButtonCount()

        val localOnlyRow = createToggleRow(
            title = getString(R.string.chk_local_only_title),
            desc = getString(R.string.chk_local_only_desc),
            initialChecked = savedOfflineOnly
        ) { newState ->
            prefs.edit().putBoolean("offline_only_mode", newState).apply()
            notifySettingsChanged()
        }

        val diagBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#13161F"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#252B3D"))
            }
            setOnClickListener {
                val textToCopy = diagBodyTv?.text?.toString() ?: ""
                if (textToCopy.isNotEmpty()) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MusicWallpaper Metadata", textToCopy))
                    Toast.makeText(this@MainActivity, getString(R.string.toast_diag_copied), Toast.LENGTH_SHORT).show()
                }
            }
        }

        val diagTitleTv = TextView(this).apply {
            text = getString(R.string.diag_box_title)
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(4))
        }

        diagBodyTv = TextView(this).apply {
            setTextColor(Color.parseColor("#C6CCDF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(2).toFloat(), 1.05f)
        }

        diagBox.addView(diagTitleTv)
        diagBox.addView(diagBodyTv)

        cardPerm.addView(permTitle)
        cardPerm.addView(permDesc)
        cardPerm.addView(permStatusBadge)
        cardPerm.addView(btnPermission)
        cardPerm.addView(restrictedHelpSpacer)
        cardPerm.addView(btnRestrictedHelp)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(btnExcludedApps)
        cardPerm.addView(createSpacer(12))
        cardPerm.addView(localOnlyRow)

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
        cardPerm.addView(diagBox)
        rootLayout.addView(cardPerm)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 2: FOTOS DE ESPERA + MANTER CAPA AO PAUSAR + OPÇÕES DA TELA INICIAL
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
                setColor(Color.parseColor("#252936"))
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        statusText = TextView(this).apply {
            setTextColor(Color.parseColor("#D5D9E6"))
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

        val keepLastCoverRow = createToggleRow(
            title = getString(R.string.chk_keep_cover_title),
            desc = getString(R.string.chk_keep_cover_desc),
            initialChecked = savedKeepLastCover
        ) { newState ->
            prefs.edit().putBoolean("keep_last_cover_on_pause", newState).apply()
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
            notifySettingsChanged()
        }
        homeUseBlurRowRef = homeUseBlurRow

        homeBlurPhotoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141720"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#262B3C"))
            }
            visibility = if (savedHomeUseAlbumBlur) View.VISIBLE else View.GONE
        }

        val homeBlurSectionTitle = TextView(this).apply {
            text = getString(R.string.home_blur_photo_section_title)
            setTextColor(Color.parseColor("#8AB4F8"))
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
                setColor(Color.parseColor("#252936"))
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        homeBlurStatusText = TextView(this).apply {
            setTextColor(Color.parseColor("#D5D9E6"))
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

        homeBlurPhotoContainer?.addView(homeBlurSectionTitle)
        homeBlurPhotoContainer?.addView(homeBlurPhotoRow)
        homeBlurPhotoContainer?.addView(btnPickHomeBlurImage)

        homePhotoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141720"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#262B3C"))
            }
            visibility = if (savedLockscreenOnly) View.VISIBLE else View.GONE
        }

        val homeSectionTitle = TextView(this).apply {
            text = getString(R.string.home_photo_section_title)
            setTextColor(Color.parseColor("#8AB4F8"))
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
                setColor(Color.parseColor("#252936"))
                cornerRadius = dp(10).toFloat()
            }
            clipToOutline = true
        }

        homeStatusText = TextView(this).apply {
            setTextColor(Color.parseColor("#D5D9E6"))
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
        // CARTÃO 3: POSIÇÃO VERTICAL (ATÉ 60%) + INTENSIDADE DO BLUR + EXPANSÃO (95%)
        // =========================================================================
        val cardPos = createCardLayout()

        val posTitle = createCardTitle(getString(R.string.card_pos_title))

        val sliderLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(8))
            text = getString(R.string.pos_label_format, savedOffsetPercent)
        }

        val seekBarPos = SeekBar(this).apply {
            max = 60
            progress = savedOffsetPercent
            progressTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            setPadding(dp(4), dp(4), dp(4), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    sliderLabel.text = getString(R.string.pos_label_format, progress)
                    if (fromUser) {
                        prefs.edit().putInt("top_offset_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val blurLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.blur_label_format, savedBlurPercent)
        }

        val seekBarBlur = SeekBar(this).apply {
            max = 40
            progress = (savedBlurPercent - 60).coerceIn(0, 40)
            progressTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            setPadding(dp(4), dp(4), dp(4), dp(14))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actualPercent = 60 + progress
                    blurLabel.text = getString(R.string.blur_label_format, actualPercent)
                    if (fromUser) {
                        prefs.edit().putInt("blur_strength_percent", actualPercent).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val expandLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.expand_label_format, savedExpansionPercent)
        }

        val seekBarExpand = SeekBar(this).apply {
            max = 100
            progress = savedExpansionPercent
            progressTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            setPadding(dp(4), dp(4), dp(4), dp(16))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    expandLabel.text = getString(R.string.expand_label_format, progress)
                    if (fromUser) {
                        prefs.edit().putInt("cover_expansion_percent", progress).apply()
                        notifySettingsChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val btnResetOffset = createSecondaryButton(getString(R.string.btn_reset_pos)).apply {
            setOnClickListener {
                seekBarPos.progress = 20
                seekBarBlur.progress = 40
                seekBarExpand.progress = 0
                prefs.edit()
                    .putInt("top_offset_percent", 20)
                    .putInt("blur_strength_percent", 100)
                    .putInt("cover_expansion_percent", 0)
                    .apply()
                notifySettingsChanged()
            }
        }

        cardPos.addView(posTitle)
        cardPos.addView(sliderLabel)
        cardPos.addView(seekBarPos)
        cardPos.addView(blurLabel)
        cardPos.addView(seekBarBlur)
        cardPos.addView(expandLabel)
        cardPos.addView(seekBarExpand)
        cardPos.addView(btnResetOffset)
        rootLayout.addView(cardPos)
        rootLayout.addView(createSpacer(24))

        // =========================================================================
        // BOTÃO PRINCIPAL: ATIVAR PAPEL DE PAREDE
        // =========================================================================
        val btnWallpaper = Button(this).apply {
            text = getString(R.string.btn_activate_wallpaper)
            isAllCaps = false
            setTextColor(Color.parseColor("#0D0F14"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#8AB4F8"))
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
        // BOTÃO DE VERIFICAR ATUALIZAÇÕES NO GITHUB + BOTÃO FAQ
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

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        updatePhotoStatusUI()
        updatePermissionStatusUI()
        updateDiagnosticUI()

        checkForGitHubUpdates(isManualCheck = false)
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
            setTextColor(Color.WHITE)
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
                    setColor(Color.parseColor("#13161F"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#252B3D"))
                }
            }

            val qTv = TextView(this).apply {
                text = question
                setTextColor(Color.parseColor("#8AB4F8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                setPadding(0, 0, 0, dp(6))
            }

            val aTv = TextView(this).apply {
                text = answer
                setTextColor(Color.parseColor("#D5D9E6"))
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
                    setTextColor(Color.parseColor("#8AB4F8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E2433"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), Color.parseColor("#33415C"))
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
        styleDarkDialog(dialog)
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
                setColor(Color.parseColor("#202431"))
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
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val descTv = TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#9AA0B4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }

        textCol.addView(titleTv)
        textCol.addView(descTv)

        val chk = CheckBox(this).apply {
            isChecked = initialChecked
            buttonTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
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
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }

        val msgTv = TextView(this).apply {
            text = getString(R.string.restricted_help_msg)
            setTextColor(Color.parseColor("#E2E6F3"))
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
        styleDarkDialog(dialog)
    }

    private fun styleDarkDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.parseColor("#181B24"))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.parseColor("#2A3042"))
        })
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.parseColor("#9AA0B4"))
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
                                setTextColor(Color.WHITE)
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
                                setTextColor(Color.parseColor("#E2E6F3"))
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
                            styleDarkDialog(dialog)
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
                    setBackgroundColor(Color.parseColor("#181B24"))
                    setPadding(dp(16), dp(20), dp(16), dp(8))
                }

                val dialogTitleTv = TextView(this).apply {
                    text = getString(R.string.crop_dialog_title)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, dp(6))
                }

                val hintTv = TextView(this).apply {
                    text = getString(R.string.crop_dialog_hint)
                    setTextColor(Color.parseColor("#9AA0B4"))
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

                val cropView = InteractiveCropView(this, previewBitmap).apply {
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
                styleDarkDialog(dialog)
            }
        }
    }

    private class InteractiveCropView(
        context: Context,
        private val bitmap: Bitmap
    ) : View(context) {

        private val matrix = Matrix()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#8AB4F8")
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
            setBackgroundColor(Color.parseColor("#181B24"))
            setPadding(dp(16), dp(20), dp(16), dp(8))
        }

        val titleTv = TextView(this).apply {
            text = getString(R.string.dialog_excluded_title)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(4), 0, dp(4), dp(12))
        }

        val searchBox = EditText(this).apply {
            hint = getString(R.string.search_apps_hint)
            setHintTextColor(Color.parseColor("#7E859B"))
            setTextColor(Color.WHITE)
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#252936"))
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
        styleDarkDialog(dialog)

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
                            setTextColor(Color.WHITE)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        }

                        val pkgTv = TextView(context).apply {
                            text = item.packageName
                            setTextColor(Color.parseColor("#7E859B"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        }

                        textCol.addView(nameTv)
                        textCol.addView(pkgTv)

                        val checkBox = CheckBox(context).apply {
                            isChecked = excludedSet.contains(item.packageName)
                            buttonTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
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
        updatePhotoStatusUI()
        updateExcludedButtonCount()
        updateDiagnosticUI()
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
                setTextColor(Color.parseColor("#6DD58C"))
            } else {
                text = getString(R.string.perm_status_missing)
                setTextColor(Color.parseColor("#F28B82"))
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
                setColor(Color.parseColor("#181B24"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#262A38"))
            }
        }
    }

    private fun createCardTitle(title: String): TextView {
        return TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    private fun createCardDescription(desc: String): TextView {
        return TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#9AA0B4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, 0)
        }
    }

    private fun createSecondaryButton(label: String): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#262B3C"))
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