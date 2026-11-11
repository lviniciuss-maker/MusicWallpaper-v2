package com.lucasvinicius.musicwallpaper2

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private var permStatusBadge: TextView? = null
    private var statusText: TextView? = null
    private var previewImageView: ImageView? = null
    private val pickImageRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.hide()
        window.statusBarColor = Color.parseColor("#0D0F14")
        window.navigationBarColor = Color.parseColor("#0D0F14")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
        val savedOffsetPercent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 40)
        // Intensidade do Blur: vai de 60% (mínimo) até 100% (máximo atual)
        val savedBlurPercent = prefs.getInt("blur_strength_percent", 100).coerceIn(60, 100)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0F14"))
            isFillViewport = true
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(36))
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
        // CARTÃO 1: PERMISSÃO DE LEITURA DE MÚSICA
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

        cardPerm.addView(permTitle)
        cardPerm.addView(permDesc)
        cardPerm.addView(permStatusBadge)
        cardPerm.addView(btnPermission)
        rootLayout.addView(cardPerm)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 2: FOTO PADRÃO DA GALERIA
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
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.chooser_title)), pickImageRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_generic), Toast.LENGTH_SHORT).show()
                }
            }
        }

        cardPhoto.addView(photoTitle)
        cardPhoto.addView(photoDesc)
        cardPhoto.addView(photoRow)
        cardPhoto.addView(btnPickImage)
        rootLayout.addView(cardPhoto)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 3: POSIÇÃO VERTICAL DA CAPA + INTENSIDADE DO BLUR (60% A 100%)
        // =========================================================================
        val cardPos = createCardLayout()

        val posTitle = createCardTitle(getString(R.string.card_pos_title))

        // Slider 1: Posição Vertical (0% a 40%)
        val sliderLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(8))
            text = getString(R.string.pos_label_format, savedOffsetPercent)
        }

        val seekBarPos = SeekBar(this).apply {
            max = 40
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

        // Slider 2: Intensidade do Blur (de 60% a 100% -> 40 posições no SeekBar)
        val blurLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#8AB4F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(6), 0, dp(8))
            text = getString(R.string.blur_label_format, savedBlurPercent)
        }

        val seekBarBlur = SeekBar(this).apply {
            // progress 0 = 60% (redução de 40%) | progress 40 = 100% (máximo atual)
            max = 40
            progress = (savedBlurPercent - 60).coerceIn(0, 40)
            progressTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            thumbTintList = ColorStateList.valueOf(Color.parseColor("#8AB4F8"))
            setPadding(dp(4), dp(4), dp(4), dp(16))
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

        val btnResetOffset = createSecondaryButton(getString(R.string.btn_reset_pos)).apply {
            setOnClickListener {
                seekBarPos.progress = 20
                seekBarBlur.progress = 40 // 60 + 40 = 100%
                prefs.edit()
                    .putInt("top_offset_percent", 20)
                    .putInt("blur_strength_percent", 100)
                    .apply()
                notifySettingsChanged()
            }
        }

        cardPos.addView(posTitle)
        cardPos.addView(sliderLabel)
        cardPos.addView(seekBarPos)
        cardPos.addView(blurLabel)
        cardPos.addView(seekBarBlur)
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
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        updatePhotoStatusUI()
        updatePermissionStatusUI()
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
        if (requestCode == pickImageRequest && resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                saveDefaultWallpaperImage(uri)
            }
        }
    }

    private fun saveDefaultWallpaperImage(uri: Uri) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var sampleSize = 1
            val maxDim = max(options.outWidth, options.outHeight)
            while (maxDim / sampleSize > 1920) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }

            if (decodedBitmap != null) {
                val file = File(filesDir, "default_wallpaper.jpg")
                FileOutputStream(file).use { out ->
                    decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                updatePhotoStatusUI()
                Toast.makeText(this, getString(R.string.toast_photo_saved), Toast.LENGTH_SHORT).show()
                sendBroadcast(Intent("com.lucasvinicius.musicwallpaper2.ACTION_DEFAULT_WALLPAPER_UPDATED").apply {
                    setPackage(packageName)
                })
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "${getString(R.string.toast_error_generic)}: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updatePhotoStatusUI() {
        val file = File(filesDir, "default_wallpaper.jpg")
        if (file.exists()) {
            statusText?.text = getString(R.string.status_photo_ok)
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val thumb = BitmapFactory.decodeFile(file.absolutePath, thumbOpts)
            previewImageView?.setImageBitmap(thumb)
        } else {
            statusText?.text = getString(R.string.status_photo_none)
            previewImageView?.setImageDrawable(null)
        }
    }
}