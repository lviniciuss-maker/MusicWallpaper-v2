package com.lucasvinicius.musicwallpaper2

import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import android.net.Uri
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
import androidx.core.view.WindowCompat
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private var permStatusBadge: TextView? = null
    private var btnExcludedApps: Button? = null
    private var statusText: TextView? = null
    private var previewImageView: ImageView? = null
    private val pickImageRequest = 1001

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
        val savedOffsetPercent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 40)
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
        // CARTÃO 1: PERMISSÃO + LISTA DE EXCEÇÃO DE APPS
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

        btnExcludedApps = createSecondaryButton("").apply {
            setOnClickListener {
                showExcludedAppsDialog()
            }
        }
        updateExcludedButtonCount()

        cardPerm.addView(permTitle)
        cardPerm.addView(permDesc)
        cardPerm.addView(permStatusBadge)
        cardPerm.addView(btnPermission)
        cardPerm.addView(createSpacer(10))
        cardPerm.addView(btnExcludedApps)
        rootLayout.addView(cardPerm)
        rootLayout.addView(createSpacer(16))

        // =========================================================================
        // CARTÃO 2: FOTO PADRÃO DA GALERIA (COM EDITOR DE CORTE E QUALIDADE PNG 1:1)
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
                seekBarBlur.progress = 40
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

    // =========================================================================
    // EDITOR DE RECORTE E ZOOM COM EXTRAÇÃO DIRETA DA IMAGEM ORIGINAL (LOSSLESS)
    // =========================================================================
    private fun showCropDialogForUri(uri: Uri) {
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
                    setPadding(dp(16), dp(16), dp(16), dp(8))
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

                container.addView(hintTv)
                container.addView(cropView)

                val dialog = AlertDialog.Builder(this)
                    .setTitle(getString(R.string.crop_dialog_title))
                    .setView(container)
                    .setNegativeButton(getString(R.string.btn_cancel)) { d, _ ->
                        d.dismiss()
                    }
                    .setPositiveButton(getString(R.string.btn_save_crop)) { d, _ ->
                        val normRect = cropView.getNormalizedCropRect()
                        saveLosslessCroppedWallpaper(uri, previewBitmap, normRect, screenW, screenH)
                        d.dismiss()
                    }
                    .create()

                dialog.show()
                dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                    setColor(Color.parseColor("#181B24"))
                    cornerRadius = dp(20).toFloat()
                })
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
        screenW: Int,
        screenH: Int
    ) {
        thread {
            try {
                // Carrega a imagem na resolução máxima nativa sem nenhum reescalonamento!
                val masterBitmap = loadHighResBitmapCorrectOrientation(uri, maxAllowedDim = 8192) ?: fallbackPreview

                val srcLeft = (normRect.left * masterBitmap.width).toInt().coerceIn(0, masterBitmap.width - 1)
                val srcTop = (normRect.top * masterBitmap.height).toInt().coerceIn(0, masterBitmap.height - 1)
                val srcWidth = ((normRect.right - normRect.left) * masterBitmap.width).toInt().coerceIn(1, masterBitmap.width - srcLeft)
                val srcHeight = ((normRect.bottom - normRect.top) * masterBitmap.height).toInt().coerceIn(1, masterBitmap.height - srcTop)

                // Mantém os pixels originais 100% intocados (SEM chamar createScaledBitmap que borra a foto!)
                // Apenas limita a 4096px se o recorte passar do limite físico do OpenGL
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

                File(filesDir, "default_wallpaper.jpg").delete()
                val pngFile = File(filesDir, "default_wallpaper.png")
                FileOutputStream(pngFile).use { out ->
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
            setPadding(dp(16), dp(16), dp(16), dp(8))
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

        container.addView(searchBox)
        container.addView(listView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_excluded_title))
            .setView(container)
            .setPositiveButton(getString(R.string.btn_done)) { d, _ ->
                d.dismiss()
            }
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.parseColor("#181B24"))
            cornerRadius = dp(20).toFloat()
        })

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
                showCropDialogForUri(uri)
            }
        }
    }

    private fun updatePhotoStatusUI() {
        val pngFile = File(filesDir, "default_wallpaper.png")
        val jpgFile = File(filesDir, "default_wallpaper.jpg")
        val activeFile = when {
            pngFile.exists() -> pngFile
            jpgFile.exists() -> jpgFile
            else -> null
        }

        if (activeFile != null) {
            statusText?.text = getString(R.string.status_photo_ok)
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
            val thumb = BitmapFactory.decodeFile(activeFile.absolutePath, thumbOpts)
            previewImageView?.setImageBitmap(thumb)
        } else {
            statusText?.text = getString(R.string.status_photo_none)
            previewImageView?.setImageDrawable(null)
        }
    }
}