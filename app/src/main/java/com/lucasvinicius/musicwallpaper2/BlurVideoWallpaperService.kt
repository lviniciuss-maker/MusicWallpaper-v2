package com.lucasvinicius.musicwallpaper2

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.max

class BlurVideoWallpaperService : WallpaperService() {

    private val m3u8Cache = ConcurrentHashMap<String, String>()
    private val staticCoverCache = ConcurrentHashMap<String, Bitmap>()

    override fun onCreateEngine(): Engine {
        return BlurVideoEngine()
    }

    inner class BlurVideoEngine : Engine(), SurfaceTexture.OnFrameAvailableListener {

        private var exoPlayer: ExoPlayer? = null
        private val mainHandler = Handler(Looper.getMainLooper())
        private var keyguardManager: KeyguardManager? = null

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var videoSurfaceTexture: SurfaceTexture? = null
        private var decoderSurface: Surface? = null
        private var oesTextureId: Int = 0
        private var staticTextureId: Int = 0
        private var homeTextureId: Int = 0

        // Dois Framebuffers (Ping-Pong) em resolução 256x340 para Blur Gaussiano Puro
        private var fboPass1Id: Int = 0
        private var texPass1Id: Int = 0
        private var fboPass2Id: Int = 0
        private var texPass2Id: Int = 0
        private val fboWidth = 256
        private val fboHeight = 340

        private var pass1Program: Int = 0
        private var pass2Program: Int = 0
        private var compositeProgram: Int = 0

        private var surfaceWidth = 1080
        private var surfaceHeight = 2400

        // Dimensões separadas para evitar esticamento lateral na troca de m3u8 para imagem estática
        private var currentVideoWidth = 1080
        private var currentVideoHeight = 1440
        private var currentStaticWidth = 1080
        private var currentStaticHeight = 1080

        private var homeMediaWidth = 1080
        private var homeMediaHeight = 2400

        // Preferências dinâmicas do usuário
        private var userTopOffset = 0.20f
        private var userBlurScale = 1.00f
        private var userCoverExpansion = 0.00f
        private var excludedPackages: Set<String> = emptySet()
        private var offlineOnlyMode: Boolean = false
        private var lockscreenOnlyMode: Boolean = false
        private var keepLastCoverOnPause: Boolean = false
        private var homeUseAlbumBlur: Boolean = false
        private var pixelDynamicColorsEnabled: Boolean = false

        // 0 = Foto padrão da galeria (PNG 100% Lossless com Nitidez Nativa na GPU)
        // 1 = Capa estática da música (cortada nas laterais para caber na caixa) com Blur espelhado
        // 2 = Capa animada .m3u8 (3:4 nativa ou 1:1 cortada nas laterais) com Blur espelhado
        private var displayMode = 0
        private var hasStaticTexture = false
        private var hasHomeTexture = false
        private var hasValidBlurTexture = false
        private var wasOnHomeScreen = false

        // Controle de transição suave (Fade 0.0f = Tela de Bloqueio | 1.0f = Tela Inicial)
        private var homeTransitionAlpha = 0f
        private var homeFadeAnimator: ValueAnimator? = null

        private val stMatrix = FloatArray(16)
        private var hasVideoFrame = false
        private var isEngineVisible = false
        private var currentPlayingM3u8Url: String? = null

        private var currentTrackKey: String = ""
        private var searchGeneration = 0
        private var isUsingOnlineCover = false
        private var isSearchInProgress = false
        private var hasUpdatedCoverForCurrentTrack = false

        // Assinaturas para controle de atualização tardia da capa da notificação
        private var lastNotificationBitmapSignature: Int = 0
        private var stalePreviousTrackSignature: Int = 0

        // Controle de Cores Dinâmicas Material You (Exclusivo Pixel 7+ na Tela de Bloqueio com Cooldown de 60s)
        private var latestCalculatedWallpaperColors: WallpaperColors? = null
        private var lastColorNotifyRealtimeMs: Long = 0L
        private val colorCooldownMs = 60_000L
        private var pendingColorNotifyRunnable: Runnable? = null

        private var mediaSessionManager: MediaSessionManager? = null
        private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

        private var isWaitingForPauseDelay = false
        private val noMusicDelayRunnable = Runnable {
            isWaitingForPauseDelay = false
            applyNoMusicStateNow()
        }

        private var secondCheckRunnable: Runnable? = null

        private val activePollRunnable = object : Runnable {
            override fun run() {
                if (isEngineVisible) {
                    checkCurrentMusic()
                    syncLockAndHomeState()
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }

        private val vertexData = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
            1f,  1f, 1f, 1f
        )
        private lateinit var vertexBuffer: FloatBuffer

        private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            setupMediaControllers(controllers)
        }

        private val wallpaperUpdatesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                mainHandler.post {
                    when (intent?.action) {
                        "com.lucasvinicius.musicwallpaper2.ACTION_DEFAULT_WALLPAPER_UPDATED" -> {
                            loadHomeWallpaperToOpenGL()
                            if (displayMode == 0) showDefaultWallpaper()
                            drawFrame(updateTex = false)
                        }
                        "com.lucasvinicius.musicwallpaper2.ACTION_SETTINGS_UPDATED" -> {
                            val prevOffline = offlineOnlyMode
                            loadUserPreferences()
                            loadHomeWallpaperToOpenGL()
                            if (prevOffline != offlineOnlyMode) {
                                currentTrackKey = ""
                            }
                            checkCurrentMusic()
                            syncLockAndHomeState(immediate = true)
                            drawFrame(updateTex = false, forceBlurPass = homeUseAlbumBlur)
                        }
                        Intent.ACTION_USER_PRESENT,
                        Intent.ACTION_SCREEN_OFF,
                        Intent.ACTION_SCREEN_ON -> {
                            syncLockAndHomeState()
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            mainHandler.post {
                try {
                    keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    loadUserPreferences()
                    initOpenGL(holder.surface)
                    initPlayer()
                    registerUpdatesReceiver()
                    loadHomeWallpaperToOpenGL()
                    showDefaultWallpaper()
                    syncLockAndHomeState(immediate = true)
                    startListeningToMusic()
                } catch (e: Throwable) {
                    Log.e("BlurWallpaper", "Erro em onSurfaceCreated", e)
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            mainHandler.post {
                surfaceWidth = width.coerceAtLeast(1)
                surfaceHeight = height.coerceAtLeast(1)
                drawFrame(updateTex = false, forceBlurPass = homeUseAlbumBlur && !hasValidBlurTexture)
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isEngineVisible = visible
            mainHandler.post {
                mainHandler.removeCallbacks(activePollRunnable)
                if (visible) {
                    loadUserPreferences()
                    checkCurrentMusic()
                    syncLockAndHomeState()
                    mainHandler.postDelayed(activePollRunnable, 1000L)
                    drawFrame(updateTex = false, forceBlurPass = homeUseAlbumBlur && !hasValidBlurTexture)
                } else {
                    homeFadeAnimator?.cancel()
                    exoPlayer?.pause()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isEngineVisible = false
            mainHandler.post {
                homeFadeAnimator?.cancel()
                secondCheckRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingColorNotifyRunnable?.let { mainHandler.removeCallbacks(it) }
                mainHandler.removeCallbacks(activePollRunnable)
                mainHandler.removeCallbacks(noMusicDelayRunnable)
                isWaitingForPauseDelay = false
                unregisterUpdatesReceiver()
                stopListeningToMusic()
                releasePlayer()
                releaseOpenGL()
            }
        }

        override fun onComputeColors(): WallpaperColors? {
            if (!pixelDynamicColorsEnabled || !isGooglePixel7OrNewerDevice()) {
                return super.onComputeColors()
            }
            return latestCalculatedWallpaperColors ?: super.onComputeColors()
        }

        private fun isGooglePixel7OrNewerDevice(): Boolean {
            val isGoogle = Build.BRAND.equals("google", ignoreCase = true) ||
                    Build.MANUFACTURER.equals("google", ignoreCase = true)
            if (!isGoogle) return false
            val model = Build.MODEL?.lowercase() ?: ""
            if (!model.contains("pixel")) return false
            val numMatch = Regex("""pixel\s*(\d+)""").find(model)?.groupValues?.get(1)?.toIntOrNull()
            if (numMatch != null) return numMatch >= 7
            return model.contains("fold") || model.contains("tablet")
        }

        private fun updateCoverColorsForPixelLockscreen(sourceBitmap: Bitmap?) {
            if (!pixelDynamicColorsEnabled || !isGooglePixel7OrNewerDevice() || sourceBitmap == null || sourceBitmap.isRecycled) return
            thread {
                try {
                    val tiny = Bitmap.createScaledBitmap(sourceBitmap, 64, 64, true)
                    val computed = WallpaperColors.fromBitmap(tiny)
                    latestCalculatedWallpaperColors = computed
                    mainHandler.post {
                        requestPixelLockscreenColorNotify()
                    }
                } catch (_: Throwable) {}
            }
        }

        private fun requestPixelLockscreenColorNotify() {
            if (!pixelDynamicColorsEnabled || !isGooglePixel7OrNewerDevice()) return
            val isLocked = keyguardManager?.isKeyguardLocked ?: false
            if (!isLocked) return

            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastColorNotifyRealtimeMs

            if (lastColorNotifyRealtimeMs == 0L || elapsed >= colorCooldownMs) {
                lastColorNotifyRealtimeMs = now
                pendingColorNotifyRunnable?.let { mainHandler.removeCallbacks(it) }
                pendingColorNotifyRunnable = null
                try {
                    notifyColorsChanged()
                    Log.d("BlurWallpaper", "Pixel 7+ Material You: Cores da capa aplicadas na Tela de Bloqueio.")
                } catch (_: Throwable) {}
            } else if (pendingColorNotifyRunnable == null) {
                val remaining = (colorCooldownMs - elapsed).coerceAtLeast(1000L)
                val runnable = Runnable {
                    pendingColorNotifyRunnable = null
                    if (pixelDynamicColorsEnabled && (keyguardManager?.isKeyguardLocked == true)) {
                        lastColorNotifyRealtimeMs = SystemClock.elapsedRealtime()
                        try {
                            notifyColorsChanged()
                            Log.d("BlurWallpaper", "Pixel 7+ Material You (Cooldown 60s concluído): Cores aplicadas.")
                        } catch (_: Throwable) {}
                    }
                }
                pendingColorNotifyRunnable = runnable
                mainHandler.postDelayed(runnable, remaining)
            }
        }

        private fun shouldShowHomeStaticOverride(): Boolean {
            if ((!lockscreenOnlyMode && !homeUseAlbumBlur) || isPreview) return false
            val locked = keyguardManager?.isKeyguardLocked ?: false
            return !locked
        }

        private fun syncLockAndHomeState(immediate: Boolean = false) {
            val onHomeNow = shouldShowHomeStaticOverride()
            val targetAlpha = if (onHomeNow) 1f else 0f

            if (immediate) {
                homeFadeAnimator?.cancel()
                wasOnHomeScreen = onHomeNow
                homeTransitionAlpha = targetAlpha
                if (onHomeNow) {
                    if (!homeUseAlbumBlur || hasValidBlurTexture) {
                        exoPlayer?.pause()
                    }
                } else if (displayMode == 2 && isEngineVisible && exoPlayer?.isPlaying == false) {
                    exoPlayer?.play()
                }
                drawFrame(updateTex = false, forceBlurPass = onHomeNow && homeUseAlbumBlur && !hasValidBlurTexture)
                return
            }

            if (onHomeNow != wasOnHomeScreen) {
                wasOnHomeScreen = onHomeNow
                homeFadeAnimator?.cancel()

                if (!onHomeNow && displayMode == 2 && isEngineVisible && exoPlayer?.isPlaying == false) {
                    exoPlayer?.play()
                }

                homeFadeAnimator = ValueAnimator.ofFloat(homeTransitionAlpha, targetAlpha).apply {
                    duration = 260L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { anim ->
                        homeTransitionAlpha = anim.animatedValue as Float
                        if (homeTransitionAlpha >= 0.999f) {
                            homeTransitionAlpha = 1f
                            if (exoPlayer?.isPlaying == true) {
                                exoPlayer?.pause()
                            }
                        } else if (homeTransitionAlpha <= 0.001f) {
                            homeTransitionAlpha = 0f
                        }
                        drawFrame(updateTex = false)
                    }
                    start()
                }
            } else if (homeFadeAnimator?.isRunning != true) {
                homeTransitionAlpha = targetAlpha
                if (onHomeNow && exoPlayer?.isPlaying == true && (!homeUseAlbumBlur || hasValidBlurTexture)) {
                    exoPlayer?.pause()
                } else if (!onHomeNow && displayMode == 2 && isEngineVisible && exoPlayer?.isPlaying == false) {
                    exoPlayer?.play()
                }
            }
        }

        private fun loadUserPreferences() {
            val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
            val percent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 40)
            userTopOffset = percent / 100f

            val blurPercent = prefs.getInt("blur_strength_percent", 100).coerceIn(60, 100)
            userBlurScale = blurPercent / 100f

            val expandPercent = prefs.getInt("cover_expansion_percent", 0).coerceIn(0, 100)
            userCoverExpansion = expandPercent / 100f

            excludedPackages = prefs.getStringSet("excluded_packages", emptySet())?.toSet() ?: emptySet()
            offlineOnlyMode = prefs.getBoolean("offline_only_mode", false)
            lockscreenOnlyMode = prefs.getBoolean("lockscreen_only_mode", false)
            keepLastCoverOnPause = prefs.getBoolean("keep_last_cover_on_pause", false)
            homeUseAlbumBlur = prefs.getBoolean("home_use_album_blur", false)
            pixelDynamicColorsEnabled = prefs.getBoolean("pixel_dynamic_colors", false)
        }

        private fun saveAndBroadcastDebugMetadata(
            pkg: String? = null,
            rawTitle: String? = null,
            rawArtist: String? = null,
            rawAlbum: String? = null,
            parsedArtist: String? = null,
            parsedAlbum: String? = null,
            parsedTitle: String? = null,
            status: String
        ) {
            try {
                val editor = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                if (pkg != null) editor.putString("debug_last_pkg", pkg)
                if (rawTitle != null) editor.putString("debug_raw_title", rawTitle)
                if (rawArtist != null) editor.putString("debug_raw_artist", rawArtist)
                if (rawAlbum != null) editor.putString("debug_raw_album", rawAlbum)
                if (parsedArtist != null) editor.putString("debug_parsed_artist", parsedArtist)
                if (parsedAlbum != null) editor.putString("debug_parsed_album", parsedAlbum)
                if (parsedTitle != null) editor.putString("debug_parsed_title", parsedTitle)
                editor.putString("debug_result_status", status)
                editor.apply()

                sendBroadcast(Intent("com.lucasvinicius.musicwallpaper2.ACTION_DEBUG_METADATA_UPDATED").apply {
                    setPackage(packageName)
                })
            } catch (_: Throwable) {}
        }

        private fun registerUpdatesReceiver() {
            try {
                val filter = IntentFilter().apply {
                    addAction("com.lucasvinicius.musicwallpaper2.ACTION_DEFAULT_WALLPAPER_UPDATED")
                    addAction("com.lucasvinicius.musicwallpaper2.ACTION_SETTINGS_UPDATED")
                    addAction(Intent.ACTION_USER_PRESENT)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                }
                ContextCompat.registerReceiver(
                    this@BlurVideoWallpaperService,
                    wallpaperUpdatesReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (_: Throwable) {}
        }

        private fun unregisterUpdatesReceiver() {
            try {
                unregisterReceiver(wallpaperUpdatesReceiver)
            } catch (_: Throwable) {}
        }

        private fun startListeningToMusic() {
            try {
                val componentName = ComponentName(this@BlurVideoWallpaperService, MusicNotificationListener::class.java)
                mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                mediaSessionManager?.addOnActiveSessionsChangedListener(sessionListener, componentName, mainHandler)
                val controllers = mediaSessionManager?.getActiveSessions(componentName)
                setupMediaControllers(controllers)
            } catch (_: SecurityException) {
                showDefaultWallpaper()
            } catch (e: Throwable) {
                Log.e("BlurWallpaper", "Erro ao iniciar ouvinte de música", e)
            }
        }

        private fun stopListeningToMusic() {
            try {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
            } catch (_: Throwable) {}
            clearControllerCallbacks()
        }

        private fun clearControllerCallbacks() {
            for ((controller, callback) in controllerCallbacks) {
                try {
                    controller.unregisterCallback(callback)
                } catch (_: Throwable) {}
            }
            controllerCallbacks.clear()
        }

        private fun setupMediaControllers(controllers: List<MediaController>?) {
            clearControllerCallbacks()
            if (controllers.isNullOrEmpty()) {
                scheduleNoMusicFallback()
                return
            }

            for (controller in controllers) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        checkCurrentMusic()
                    }
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        checkCurrentMusic()
                    }
                }
                try {
                    controller.registerCallback(callback, mainHandler)
                    controllerCallbacks[controller] = callback
                } catch (_: Throwable) {}
            }
            checkCurrentMusic()
        }

        private fun isActivePlaybackState(state: Int?): Boolean {
            return state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING ||
                    state == PlaybackState.STATE_FAST_FORWARDING ||
                    state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                    state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                    state == PlaybackState.STATE_CONNECTING
        }

        private fun computeBitmapSignature(bmp: Bitmap?): Int {
            if (bmp == null || bmp.isRecycled || bmp.width <= 0 || bmp.height <= 0) return 0
            return try {
                val w = bmp.width
                val h = bmp.height
                var hash = w * 31 + h
                val xs = intArrayOf(w / 5, w / 2, (w * 4) / 5)
                val ys = intArrayOf(h / 5, h / 2, (h * 4) / 5)
                for (x in xs) {
                    for (y in ys) {
                        hash = hash * 31 + bmp.getPixel(x, y)
                    }
                }
                hash
            } catch (_: Throwable) {
                0
            }
        }

        private fun scheduleDelayedSecondVerification(
            trackKey: String,
            artist: String,
            album: String,
            title: String,
            generation: Int
        ) {
            secondCheckRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                if (generation != searchGeneration || currentTrackKey != trackKey) return@Runnable
                checkCurrentMusic()
                if (!offlineOnlyMode && !isUsingOnlineCover && !isSearchInProgress) {
                    Log.d("BlurWallpaper", "Checagem de 4s: Reiniciando busca online para $title (Wi-Fi lento)")
                    fetchCoverForTrack(trackKey, artist, album, title, generation, isRetryAttempt = true)
                } else if (offlineOnlyMode && !hasUpdatedCoverForCurrentTrack) {
                    showDefaultWallpaper()
                }
            }
            secondCheckRunnable = runnable
            mainHandler.postDelayed(runnable, 4000L)
        }

        private fun checkCurrentMusic() {
            try {
                val componentName = ComponentName(this@BlurVideoWallpaperService, MusicNotificationListener::class.java)
                val controllers = mediaSessionManager?.getActiveSessions(componentName) ?: controllerCallbacks.keys.toList()

                val playingController = controllers.firstOrNull {
                    it.packageName !in excludedPackages && isActivePlaybackState(it.playbackState?.state)
                }

                if (playingController == null) {
                    scheduleNoMusicFallback()
                    return
                }

                val metadata = playingController.metadata
                if (metadata == null) {
                    scheduleNoMusicFallback()
                    return
                }

                val rawTitle = (
                        metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                            ?: ""
                        ).trim()

                val rawArtist = (
                        metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_WRITER)
                            ?: ""
                        ).trim()

                val rawAlbum = (
                        metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                            ?: ""
                        ).trim()

                var title = rawTitle
                var artist = rawArtist
                var album = rawAlbum

                if (album.isEmpty() && artist.contains(" - ")) {
                    val parts = artist.split(" - ", limit = 2)
                    artist = parts[0].trim()
                    album = parts[1].trim()
                } else if (album.isEmpty() && artist.contains(" • ")) {
                    val parts = artist.split(" • ", limit = 2)
                    artist = parts[0].trim()
                    album = parts[1].trim()
                }

                if (artist.isEmpty() && title.isEmpty()) {
                    scheduleNoMusicFallback()
                    return
                }

                if (isWaitingForPauseDelay) {
                    mainHandler.removeCallbacks(noMusicDelayRunnable)
                    isWaitingForPauseDelay = false
                }

                val rawBitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                val safeFallbackBitmap = makeGlSafeBitmap(rawBitmap)
                val currentBmpSignature = computeBitmapSignature(safeFallbackBitmap)

                val newKey = "$artist|$album|$title"
                if (newKey != currentTrackKey) {
                    val previousTrackKey = currentTrackKey
                    currentTrackKey = newKey
                    searchGeneration++
                    val currentGen = searchGeneration
                    isUsingOnlineCover = false
                    hasUpdatedCoverForCurrentTrack = false

                    if (previousTrackKey.isNotEmpty() && lastNotificationBitmapSignature != 0) {
                        stalePreviousTrackSignature = lastNotificationBitmapSignature
                    }
                    lastNotificationBitmapSignature = currentBmpSignature

                    val isFreshNotificationBitmap = safeFallbackBitmap != null &&
                            (stalePreviousTrackSignature == 0 || currentBmpSignature != stalePreviousTrackSignature)

                    Log.d("BlurWallpaper", "App: ${playingController.packageName} -> Artista: $artist | Álbum: $album | Música: $title | FreshBmp: $isFreshNotificationBitmap")

                    saveAndBroadcastDebugMetadata(
                        pkg = playingController.packageName,
                        rawTitle = rawTitle,
                        rawArtist = rawArtist,
                        rawAlbum = rawAlbum,
                        parsedArtist = artist,
                        parsedAlbum = album,
                        parsedTitle = title,
                        status = if (offlineOnlyMode) "Offline Mode (Local Notification Cover)" else "Searching online..."
                    )

                    currentPlayingM3u8Url = null

                    if (isFreshNotificationBitmap && safeFallbackBitmap != null) {
                        updateCoverColorsForPixelLockscreen(safeFallbackBitmap)
                    }

                    if (offlineOnlyMode) {
                        if (isFreshNotificationBitmap && safeFallbackBitmap != null) {
                            hasUpdatedCoverForCurrentTrack = true
                            uploadStaticBitmapToOpenGL(safeFallbackBitmap, isDefaultFullWallpaper = false)
                        }
                        scheduleDelayedSecondVerification(newKey, artist, album, title, currentGen)
                        return
                    }

                    val cachedM3u8 = m3u8Cache[newKey]
                    if (cachedM3u8 != null) {
                        isUsingOnlineCover = true
                        hasUpdatedCoverForCurrentTrack = true
                        saveAndBroadcastDebugMetadata(status = "Animated .m3u8 (From Cache)")
                        startVideoPlayback(cachedM3u8)
                        return
                    }

                    val cachedOnlineBitmap = staticCoverCache[newKey]
                    if (cachedOnlineBitmap != null) {
                        isUsingOnlineCover = true
                        hasUpdatedCoverForCurrentTrack = true
                        updateCoverColorsForPixelLockscreen(cachedOnlineBitmap)
                        uploadStaticBitmapToOpenGL(cachedOnlineBitmap, isDefaultFullWallpaper = false)
                    } else if (displayMode != 2 && isFreshNotificationBitmap && safeFallbackBitmap != null) {
                        hasUpdatedCoverForCurrentTrack = true
                        uploadStaticBitmapToOpenGL(safeFallbackBitmap, isDefaultFullWallpaper = false)
                    }

                    fetchCoverForTrack(newKey, artist, album, title, currentGen, isRetryAttempt = false)
                    scheduleDelayedSecondVerification(newKey, artist, album, title, currentGen)
                } else {
                    if (safeFallbackBitmap != null && !isUsingOnlineCover && !isSearchInProgress) {
                        val isNoLongerStale = (stalePreviousTrackSignature == 0 || currentBmpSignature != stalePreviousTrackSignature)
                        if (isNoLongerStale && (!hasUpdatedCoverForCurrentTrack || displayMode == 0 || currentBmpSignature != lastNotificationBitmapSignature)) {
                            lastNotificationBitmapSignature = currentBmpSignature
                            stalePreviousTrackSignature = 0
                            hasUpdatedCoverForCurrentTrack = true
                            Log.d("BlurWallpaper", "Aplicando capa da notificação após confirmação.")
                            updateCoverColorsForPixelLockscreen(safeFallbackBitmap)
                            uploadStaticBitmapToOpenGL(safeFallbackBitmap, isDefaultFullWallpaper = false)
                        }
                    }
                    if (!offlineOnlyMode && displayMode == 2 && isEngineVisible && homeTransitionAlpha < 1f && exoPlayer?.isPlaying == false) {
                        exoPlayer?.play()
                    }
                }
            } catch (e: Throwable) {
                Log.e("BlurWallpaper", "Erro ao checar música", e)
            }
        }

        private fun startVideoPlayback(m3u8Url: String) {
            if (currentPlayingM3u8Url == m3u8Url && displayMode == 2) return
            currentPlayingM3u8Url = m3u8Url
            exoPlayer?.apply {
                setMediaItem(MediaItem.fromUri(m3u8Url))
                prepare()
                playWhenReady = (homeTransitionAlpha < 1f) || homeUseAlbumBlur
            }
        }

        private fun scheduleNoMusicFallback() {
            if (keepLastCoverOnPause && (displayMode == 1 || displayMode == 2)) {
                if (isWaitingForPauseDelay) {
                    mainHandler.removeCallbacks(noMusicDelayRunnable)
                    isWaitingForPauseDelay = false
                }
                return
            }

            if (displayMode == 0 && currentTrackKey.isEmpty()) return
            if (!isWaitingForPauseDelay) {
                isWaitingForPauseDelay = true
                mainHandler.postDelayed(noMusicDelayRunnable, 5000L)
            }
        }

        private fun applyNoMusicStateNow() {
            if (keepLastCoverOnPause && (displayMode == 1 || displayMode == 2)) return

            if (currentTrackKey.isNotEmpty() || displayMode != 0) {
                secondCheckRunnable?.let { mainHandler.removeCallbacks(it) }
                currentTrackKey = ""
                currentPlayingM3u8Url = null
                isUsingOnlineCover = false
                isSearchInProgress = false
                hasUpdatedCoverForCurrentTrack = false
                lastNotificationBitmapSignature = 0
                stalePreviousTrackSignature = 0
                searchGeneration++
                showDefaultWallpaper()
            }
        }

        private fun makeGlSafeBitmap(src: Bitmap?, maxAllowedDim: Int = 1400): Bitmap? {
            if (src == null || src.isRecycled || src.width <= 0 || src.height <= 0) return null
            return try {
                val maxDim = max(src.width, src.height)
                val scale = if (maxDim > maxAllowedDim) maxAllowedDim.toFloat() / maxDim else 1f
                val targetW = (src.width * scale).toInt().coerceAtLeast(1)
                val targetH = (src.height * scale).toInt().coerceAtLeast(1)

                val safeCopy = src.copy(Bitmap.Config.ARGB_8888, false)
                if (safeCopy != null) {
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(safeCopy, targetW, targetH, true)
                    } else {
                        safeCopy
                    }
                } else {
                    val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    canvas.drawBitmap(src, null, android.graphics.Rect(0, 0, targetW, targetH), null)
                    out
                }
            } catch (_: Throwable) {
                null
            }
        }

        private fun loadHomeWallpaperToOpenGL() {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            try {
                val primaryHomeFile = if (homeUseAlbumBlur) {
                    File(filesDir, "home_blur_wallpaper.png")
                } else {
                    File(filesDir, "home_wallpaper.png")
                }
                val lockPngFile = File(filesDir, "default_wallpaper.png")
                val lockJpgFile = File(filesDir, "default_wallpaper.jpg")

                val fileToLoad = when {
                    primaryHomeFile.exists() -> primaryHomeFile
                    lockPngFile.exists() -> lockPngFile
                    lockJpgFile.exists() -> lockJpgFile
                    else -> null
                }

                if (fileToLoad != null && fileToLoad.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    }
                    val raw = BitmapFactory.decodeFile(fileToLoad.absolutePath, options)
                    if (raw != null && EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        homeMediaWidth = raw.width.coerceAtLeast(1)
                        homeMediaHeight = raw.height.coerceAtLeast(1)
                        hasHomeTexture = true
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, homeTextureId)
                        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, raw, 0)
                        return
                    }
                }
            } catch (_: Throwable) {}
            hasHomeTexture = false
        }

        private fun showDefaultWallpaper() {
            try {
                val pngFile = File(filesDir, "default_wallpaper.png")
                val jpgFile = File(filesDir, "default_wallpaper.jpg")
                val file = if (pngFile.exists()) pngFile else jpgFile

                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inScaled = false
                    }
                    val raw = BitmapFactory.decodeFile(file.absolutePath, options)
                    if (raw != null) {
                        uploadStaticBitmapToOpenGL(raw, isDefaultFullWallpaper = true)
                        return
                    }
                }
            } catch (_: Throwable) {}
            exoPlayer?.pause()
            exoPlayer?.clearMediaItems()
            hasVideoFrame = false
            hasValidBlurTexture = false
            currentPlayingM3u8Url = null
            displayMode = 0
            hasStaticTexture = false
            drawInitialBackground()
        }

        private fun uploadStaticBitmapToOpenGL(bitmap: Bitmap, isDefaultFullWallpaper: Boolean) {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            try {
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return

                exoPlayer?.pause()
                exoPlayer?.clearMediaItems()
                hasVideoFrame = false
                currentPlayingM3u8Url = null

                currentStaticWidth = bitmap.width.coerceAtLeast(1)
                currentStaticHeight = bitmap.height.coerceAtLeast(1)

                displayMode = if (isDefaultFullWallpaper) 0 else 1
                hasStaticTexture = true
                if (isDefaultFullWallpaper) hasValidBlurTexture = false

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

                drawFrame(updateTex = false, forceBlurPass = !isDefaultFullWallpaper)
            } catch (e: Throwable) {
                Log.e("BlurWallpaper", "Erro no uploadStaticBitmapToOpenGL", e)
            }
        }

        private fun initPlayer() {
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this@BlurVideoWallpaperService).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            currentVideoWidth = videoSize.width
                            currentVideoHeight = videoSize.height
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        displayMode = 2
                        hasVideoFrame = true
                        hasUpdatedCoverForCurrentTrack = true
                        drawFrame(updateTex = true, forceBlurPass = true)
                        if (homeTransitionAlpha >= 1f) {
                            exoPlayer?.pause()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("BlurWallpaper", "Erro no ExoPlayer: ${error.message}", error)
                    }
                })
                setVideoSurface(decoderSurface)
            }
        }

        private fun isArtistMatch(expectedArtist: String, returnedArtist: String): Boolean {
            val exp = expectedArtist.lowercase().replace(Regex("[^a-z0-9가-힣一-龯ぁ-んァ-ン]"), "")
            val ret = returnedArtist.lowercase().replace(Regex("[^a-z0-9가-힣一-龯ぁ-んァ-ン]"), "")
            if (exp.isEmpty() || ret.isEmpty()) return true
            if (ret.contains(exp) || exp.contains(ret)) return true
            if ((exp.contains("kanyewest") || exp == "ye") && (ret.contains("kanyewest") || ret == "ye")) return true
            return false
        }

        private fun isTextMatch(expected: String, returned: String): Boolean {
            val cleanExp = expected.replace(Regex("""\s*[(\[].*?[)\]]"""), "")
                .lowercase().replace(Regex("[^a-z0-9가-힣一-龯ぁ-んァ-ン]"), "")
            val cleanRet = returned.replace(Regex("""\s*[(\[].*?[)\]]"""), "")
                .lowercase().replace(Regex("[^a-z0-9가-힣一-龯ぁ-んァ-ン]"), "")
            if (cleanExp.isEmpty() || cleanRet.isEmpty()) return false
            return cleanRet.contains(cleanExp) || cleanExp.contains(cleanRet)
        }

        private fun fetchCoverForTrack(
            trackKey: String,
            artist: String,
            album: String,
            title: String,
            generation: Int,
            isRetryAttempt: Boolean = false
        ) {
            isSearchInProgress = true
            thread {
                try {
                    val cleanArtist = artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: artist
                    val cleanTitle = title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim()
                    val cleanAlbum = album.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim()

                    val encArtist = URLEncoder.encode(cleanArtist, "UTF-8")

                    var m3u8Found: String? = null
                    var m3u8SourceLabel = if (isRetryAttempt) "Plan A (Retry 4s)" else "Plan A (3:4 API)"
                    var itunesMatch: Triple<String, String, String>? = null

                    if (cleanAlbum.isNotEmpty()) {
                        val encAlbum = URLEncoder.encode(cleanAlbum, "UTF-8")
                        m3u8Found = requestM3u8FromEndpoint(
                            "https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encAlbum",
                            cleanArtist,
                            cleanAlbum
                        )
                    }

                    if (m3u8Found == null && cleanTitle.isNotEmpty() && !cleanTitle.equals(cleanAlbum, ignoreCase = true)) {
                        val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                        m3u8Found = requestM3u8FromEndpoint(
                            "https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encTitle",
                            cleanArtist,
                            cleanTitle
                        )
                    }

                    if (m3u8Found == null) {
                        itunesMatch = queryItunesCatalog(cleanArtist, cleanAlbum, cleanTitle)
                        val officialAlbumName = itunesMatch?.first ?: ""
                        if (officialAlbumName.isNotEmpty() && !officialAlbumName.equals(cleanAlbum, ignoreCase = true)) {
                            val encItunesAlbum = URLEncoder.encode(officialAlbumName, "UTF-8")
                            m3u8Found = requestM3u8FromEndpoint(
                                "https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encItunesAlbum",
                                cleanArtist,
                                officialAlbumName
                            )
                        }
                    }

                    if (m3u8Found == null) {
                        if (itunesMatch == null) {
                            itunesMatch = queryItunesCatalog(cleanArtist, cleanAlbum, cleanTitle)
                        }
                        val collectionViewUrl = itunesMatch?.second ?: ""
                        if (collectionViewUrl.isNotEmpty()) {
                            m3u8Found = extractM3u8FromAppleMusicPage(collectionViewUrl)
                            if (m3u8Found != null) {
                                m3u8SourceLabel = if (isRetryAttempt) "Plan B (Retry 4s)" else "Plan B (Apple Direct .m3u8)"
                            }
                        }
                    }

                    if (generation != searchGeneration || offlineOnlyMode) return@thread

                    if (m3u8Found != null) {
                        m3u8Cache[trackKey] = m3u8Found
                        val finalUrl = m3u8Found
                        val finalSourceLabel = m3u8SourceLabel
                        mainHandler.post {
                            isSearchInProgress = false
                            if (generation != searchGeneration || offlineOnlyMode) return@post
                            isUsingOnlineCover = true
                            hasUpdatedCoverForCurrentTrack = true
                            saveAndBroadcastDebugMetadata(
                                parsedArtist = cleanArtist,
                                parsedAlbum = cleanAlbum,
                                parsedTitle = cleanTitle,
                                status = "✅ Animated ($finalSourceLabel)"
                            )
                            startVideoPlayback(finalUrl)
                        }
                    } else {
                        if (itunesMatch == null) {
                            itunesMatch = queryItunesCatalog(cleanArtist, cleanAlbum, cleanTitle)
                        }
                        val highResArtUrl = itunesMatch?.third ?: ""
                        val downloadedStatic = if (highResArtUrl.isNotEmpty()) {
                            downloadBitmapFromUrl(highResArtUrl)
                        } else {
                            null
                        }
                        val safeOnlineStatic = makeGlSafeBitmap(downloadedStatic)
                        if (safeOnlineStatic != null) {
                            staticCoverCache[trackKey] = safeOnlineStatic
                        }
                        mainHandler.post {
                            isSearchInProgress = false
                            if (generation != searchGeneration || offlineOnlyMode) return@post
                            if (safeOnlineStatic != null) {
                                isUsingOnlineCover = true
                                hasUpdatedCoverForCurrentTrack = true
                                saveAndBroadcastDebugMetadata(
                                    parsedArtist = cleanArtist,
                                    parsedAlbum = cleanAlbum,
                                    parsedTitle = cleanTitle,
                                    status = if (isRetryAttempt) "🖼️ Static (iTunes 1000x1000 - Retry 4s)" else "🖼️ Static (iTunes 1000x1000)"
                                )
                                updateCoverColorsForPixelLockscreen(safeOnlineStatic)
                                uploadStaticBitmapToOpenGL(safeOnlineStatic, isDefaultFullWallpaper = false)
                            } else {
                                isUsingOnlineCover = false
                                saveAndBroadcastDebugMetadata(
                                    parsedArtist = cleanArtist,
                                    parsedAlbum = cleanAlbum,
                                    parsedTitle = cleanTitle,
                                    status = if (isRetryAttempt) "📱 Static (Notification Fallback - Verified 4s)" else "📱 Static (Notification Fallback - Waiting 4s check)"
                                )
                                checkCurrentMusic()

                                if (isRetryAttempt && !hasUpdatedCoverForCurrentTrack) {
                                    showDefaultWallpaper()
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    mainHandler.post { isSearchInProgress = false }
                    Log.e("BlurWallpaper", "Erro ao buscar capa", e)
                }
            }
        }

        private fun queryItunesCatalog(artist: String, album: String, title: String): Triple<String, String, String>? {
            val primarySearch = fetchFromItunesEndpoint(
                if (album.isNotEmpty()) "$artist $album" else "$artist $title",
                artist,
                album,
                title
            )
            if (primarySearch != null) return primarySearch

            if (album.isNotEmpty() && title.isNotEmpty() && !album.equals(title, ignoreCase = true)) {
                return fetchFromItunesEndpoint("$artist $title", artist, album, title)
            }
            return null
        }

        private fun fetchFromItunesEndpoint(
            queryText: String,
            artist: String,
            album: String,
            title: String
        ): Triple<String, String, String>? {
            return try {
                if (queryText.isBlank()) return null
                val term = URLEncoder.encode(queryText, "UTF-8")
                val url = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=12")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val results = json.optJSONArray("results") ?: return null

                for (i in 0 until results.length()) {
                    val item = results.getJSONObject(i)
                    val foundArtist = item.optString("artistName")
                    val foundTrack = item.optString("trackName")
                    val foundAlbum = item.optString("collectionName")

                    val artistOk = isArtistMatch(artist, foundArtist)
                    val contentOk = (album.isNotEmpty() && isTextMatch(album, foundAlbum)) ||
                            (title.isNotEmpty() && isTextMatch(title, foundTrack)) ||
                            (title.isNotEmpty() && isTextMatch(title, foundAlbum))

                    if (artistOk && contentOk) {
                        val rawCollectionUrl = item.optString("collectionViewUrl", "")
                        val cleanCollectionUrl = rawCollectionUrl.substringBefore("?")
                        val art100 = item.optString("artworkUrl100", "")
                        val highResArt = if (art100.isNotEmpty()) art100.replace("100x100bb", "1000x1000bb") else ""

                        if (foundAlbum.isNotEmpty() || cleanCollectionUrl.isNotEmpty() || highResArt.isNotEmpty()) {
                            return Triple(foundAlbum, cleanCollectionUrl, highResArt)
                        }
                    }
                }
                null
            } catch (_: Throwable) {
                null
            }
        }

        private fun requestM3u8FromEndpoint(urlString: String, expectedArtist: String, expectedAlbumOrTitle: String): String? {
            return try {
                val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code !in 200..299) return null

                val body = conn.inputStream.bufferedReader().readText()

                val artistRegex = Regex(""""(?:artist|artistName|artist_name)"\s*:\s*"([^"]+)"""")
                val returnedArtist = artistRegex.find(body)?.groupValues?.get(1)?.trim()
                if (!returnedArtist.isNullOrEmpty() && !isArtistMatch(expectedArtist, returnedArtist)) return null

                val albumRegex = Regex(""""(?:album|albumName|album_name|collectionName|title)"\s*:\s*"([^"]+)"""")
                val returnedAlbum = albumRegex.find(body)?.groupValues?.get(1)?.trim()
                if (!returnedAlbum.isNullOrEmpty() && expectedAlbumOrTitle.isNotEmpty() && !isTextMatch(expectedAlbumOrTitle, returnedAlbum)) return null

                val tallFieldRegex = Regex(""""(?:url_tall|tall_url|tallUrl|editorialVideoTall)"\s*:\s*"(https:[^"]+\.m3u8)"""")
                val tallMatch = tallFieldRegex.find(body)?.groupValues?.get(1)?.replace("\\/", "/")
                if (!tallMatch.isNullOrEmpty()) return tallMatch

                val regex = Regex("""https://[^\s"'<>\\]+\.m3u8""")
                val matches = regex.findAll(body).map { it.value.replace("\\/", "/") }.toList()

                matches.firstOrNull {
                    it.contains("1080x1440") ||
                            it.contains("768x1024") ||
                            it.contains("2160x2880") ||
                            it.contains("1440x1920") ||
                            it.contains("tall", ignoreCase = true) ||
                            it.contains("3x4", ignoreCase = true)
                } ?: matches.lastOrNull()
            } catch (_: Throwable) {
                null
            }
        }

        private fun extractM3u8FromAppleMusicPage(collectionViewUrl: String): String? {
            return try {
                val appleMusicUrl = collectionViewUrl.replace("itunes.apple.com", "music.apple.com").trim()
                val conn = (URL(appleMusicUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5500
                    readTimeout = 5500
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                }

                if (conn.responseCode !in 200..299) return null

                val html = conn.inputStream.bufferedReader().readText()
                val normalizedHtml = html.replace("\\/", "/").replace("\\u002F", "/").replace("&amp;", "&")

                val regex = Regex("""https://[^\s"'<>\\]+\.m3u8""")
                val matches = regex.findAll(normalizedHtml).map { it.value }.distinct().toList()
                if (matches.isEmpty()) return null

                matches.firstOrNull {
                    it.contains("1080x1440") ||
                            it.contains("2160x2880") ||
                            it.contains("1440x1920") ||
                            it.contains("768x1024") ||
                            it.contains("tall", ignoreCase = true) ||
                            it.contains("3x4", ignoreCase = true)
                } ?: matches.firstOrNull()
            } catch (e: Throwable) {
                Log.e("BlurWallpaper", "Erro no fallback da página da Apple", e)
                null
            }
        }

        private fun downloadBitmapFromUrl(imageUrl: String): Bitmap? {
            return try {
                val imgConn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5500
                    readTimeout = 5500
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(imgConn.inputStream, null, opts)
            } catch (_: Throwable) {
                null
            }
        }

        private fun releasePlayer() {
            exoPlayer?.release()
            exoPlayer = null
            decoderSurface?.release()
            decoderSurface = null
            videoSurfaceTexture?.release()
            videoSurfaceTexture = null
            hasVideoFrame = false
        }

        override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
            hasVideoFrame = true
            hasUpdatedCoverForCurrentTrack = true
            if (displayMode != 2) {
                displayMode = 2
            }
            if (homeTransitionAlpha >= 1f) {
                if (homeUseAlbumBlur) {
                    drawFrame(updateTex = true, forceBlurPass = true)
                }
                exoPlayer?.pause()
                return
            }
            drawFrame(updateTex = true)
        }

        private fun initOpenGL(surface: Surface) {
            vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            vertexBuffer.position(0)

            eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, 1, numConfigs, 0)

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

            val textures = IntArray(5)
            GLES20.glGenTextures(5, textures, 0)

            oesTextureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            staticTextureId = textures[1]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            texPass1Id = textures[2]
            texPass2Id = textures[3]
            homeTextureId = textures[4]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, homeTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val fbos = IntArray(2)
            GLES20.glGenFramebuffers(2, fbos, 0)
            fboPass1Id = fbos[0]
            fboPass2Id = fbos[1]

            setupFboTexture(texPass1Id, fboPass1Id)
            setupFboTexture(texPass2Id, fboPass2Id)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

            videoSurfaceTexture = SurfaceTexture(oesTextureId).apply {
                setOnFrameAvailableListener(this@BlurVideoEngine, mainHandler)
            }
            decoderSurface = Surface(videoSurfaceTexture)

            pass1Program = createShaderProgram(VERTEX_SHADER, PASS1_HORIZONTAL_BLUR_SHADER)
            pass2Program = createShaderProgram(VERTEX_SHADER, PASS2_VERTICAL_BLUR_SHADER)
            compositeProgram = createShaderProgram(VERTEX_SHADER, COMPOSITE_FRAGMENT_SHADER)
        }

        private fun setupFboTexture(texId: Int, fboId: Int) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                fboWidth, fboHeight, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                texId,
                0
            )
        }

        private fun drawInitialBackground() {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0.05f, 0.05f, 0.08f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun bindQuadAttributes(program: Int) {
            val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
            val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(texHandle)
            GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        }

        private fun drawFrame(updateTex: Boolean, forceBlurPass: Boolean = false) {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return

            try {
                if (updateTex) {
                    videoSurfaceTexture?.updateTexImage()
                    videoSurfaceTexture?.getTransformMatrix(stMatrix)
                }
            } catch (_: Throwable) {
                return
            }

            val effectiveHomeAlpha = if ((lockscreenOnlyMode || homeUseAlbumBlur) && !isPreview) homeTransitionAlpha else 0f
            val canRunBlurOnActiveMedia = (displayMode == 1 && hasStaticTexture) || (displayMode == 2 && hasVideoFrame)

            if (canRunBlurOnActiveMedia && (effectiveHomeAlpha < 1f || forceBlurPass || (homeUseAlbumBlur && !hasValidBlurTexture))) {
                GLES20.glViewport(0, 0, fboWidth, fboHeight)

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass1Id)
                GLES20.glUseProgram(pass1Program)
                bindQuadAttributes(pass1Program)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(pass1Program, "uSTMatrix"), 1, false, stMatrix, 0)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(pass1Program, "uMode"), displayMode)
                GLES20.glUniform1f(GLES20.glGetUniformLocation(pass1Program, "uSpread"), 0.009f * userBlurScale)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(pass1Program, "sOesTexture"), 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(pass1Program, "sStaticTexture"), 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass2Id)
                GLES20.glUseProgram(pass2Program)
                bindQuadAttributes(pass2Program)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.0f, 0.008f * userBlurScale)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass1Id)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(pass2Program, "sInputTexture"), 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass1Id)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.016f * userBlurScale, 0.0f)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass2Id)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass2Id)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.0f, 0.014f * userBlurScale)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass1Id)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                hasValidBlurTexture = true
            }

            val canUseAlbumBlurOnHome = homeUseAlbumBlur && hasValidBlurTexture && (displayMode == 1 || displayMode == 2)

            if (effectiveHomeAlpha >= 1f && !canUseAlbumBlurOnHome && !hasHomeTexture && !hasStaticTexture) {
                drawInitialBackground()
                return
            }

            if (effectiveHomeAlpha < 1f) {
                if (displayMode == 0 && !hasStaticTexture) {
                    if (effectiveHomeAlpha > 0f && hasHomeTexture) {
                        // Permite desenhar a transição se tiver foto da tela inicial
                    } else {
                        drawInitialBackground()
                        return
                    }
                }
                if (displayMode == 2 && !hasVideoFrame && effectiveHomeAlpha <= 0f) {
                    return
                }
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0.05f, 0.05f, 0.08f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(compositeProgram)
            bindQuadAttributes(compositeProgram)

            val activeMediaW = if (displayMode == 2) currentVideoWidth.toFloat() else currentStaticWidth.toFloat()
            val activeMediaH = if (displayMode == 2) currentVideoHeight.toFloat() else currentStaticHeight.toFloat()

            val homeTexToBind = if (hasHomeTexture) homeTextureId else staticTextureId
            val homeWToUse = if (hasHomeTexture) homeMediaWidth else currentStaticWidth
            val homeHToUse = if (hasHomeTexture) homeMediaHeight else currentStaticHeight

            GLES20.glUniform2f(GLES20.glGetUniformLocation(compositeProgram, "uResolution"), surfaceWidth.toFloat(), surfaceHeight.toFloat())
            GLES20.glUniform2f(GLES20.glGetUniformLocation(compositeProgram, "uVideoSize"), activeMediaW, activeMediaH)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(compositeProgram, "uHomeSize"), homeWToUse.toFloat(), homeHToUse.toFloat())
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(compositeProgram, "uSTMatrix"), 1, false, stMatrix, 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "uMode"), displayMode)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uTopOffset"), userTopOffset)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uCoverExpansion"), userCoverExpansion)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uHomeAlpha"), effectiveHomeAlpha)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "uHomeUseBlur"), if (canUseAlbumBlurOnHome) 1 else 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sOesTexture"), 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sStaticTexture"), 1)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass2Id)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sBlurTexture"), 2)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, homeTexToBind)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sHomeTexture"), 3)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        }

        private fun releaseOpenGL() {
            try {
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            } catch (_: Throwable) {}
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            hasValidBlurTexture = false
        }

        private fun createShaderProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            return program
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val PASS1_HORIZONTAL_BLUR_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sOesTexture;
            uniform sampler2D sStaticTexture;
            uniform mat4 uSTMatrix;
            uniform int uMode;
            uniform float uSpread;

            vec2 mirrorCoords(vec2 uv) {
                vec2 m = mod(uv, 2.0);
                if (m.x < 0.0) m.x += 2.0;
                if (m.y < 0.0) m.y += 2.0;
                if (m.x > 1.0) m.x = 2.0 - m.x;
                if (m.y > 1.0) m.y = 2.0 - m.y;
                return clamp(m, vec2(0.005), vec2(0.995));
            }

            vec4 readSource(vec2 uv) {
                vec2 c = mirrorCoords(uv);
                if (uMode == 2) {
                    vec2 glUv = vec2(c.x, 1.0 - c.y);
                    vec2 tr = (uSTMatrix * vec4(glUv, 0.0, 1.0)).xy;
                    return texture2D(sOesTexture, tr);
                } else {
                    return texture2D(sStaticTexture, c);
                }
            }

            void main() {
                vec2 d = vec2(uSpread, 0.0);
                vec4 sum = vec4(0.0);
                sum += readSource(vTexCoord - 6.0 * d) * 0.015;
                sum += readSource(vTexCoord - 5.0 * d) * 0.030;
                sum += readSource(vTexCoord - 4.0 * d) * 0.055;
                sum += readSource(vTexCoord - 3.0 * d) * 0.085;
                sum += readSource(vTexCoord - 2.0 * d) * 0.115;
                sum += readSource(vTexCoord - 1.0 * d) * 0.135;
                sum += readSource(vTexCoord)           * 0.130;
                sum += readSource(vTexCoord + 1.0 * d) * 0.135;
                sum += readSource(vTexCoord + 2.0 * d) * 0.115;
                sum += readSource(vTexCoord + 3.0 * d) * 0.085;
                sum += readSource(vTexCoord + 4.0 * d) * 0.055;
                sum += readSource(vTexCoord + 5.0 * d) * 0.030;
                sum += readSource(vTexCoord + 6.0 * d) * 0.015;
                gl_FragColor = sum;
            }
        """

        private const val PASS2_VERTICAL_BLUR_SHADER = """
            precision highp float;
            varying vec2 vTexCoord;
            uniform sampler2D sInputTexture;
            uniform vec2 uDir;

            vec2 mirrorCoords(vec2 uv) {
                vec2 m = mod(uv, 2.0);
                if (m.x < 0.0) m.x += 2.0;
                if (m.y < 0.0) m.y += 2.0;
                if (m.x > 1.0) m.x = 2.0 - m.x;
                if (m.y > 1.0) m.y = 2.0 - m.y;
                return clamp(m, vec2(0.005), vec2(0.995));
            }

            void main() {
                vec4 sum = vec4(0.0);
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 6.0 * uDir)) * 0.015;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 5.0 * uDir)) * 0.030;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 4.0 * uDir)) * 0.055;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 3.0 * uDir)) * 0.085;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 2.0 * uDir)) * 0.115;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord - 1.0 * uDir)) * 0.135;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord))              * 0.130;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 1.0 * uDir)) * 0.135;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 2.0 * uDir)) * 0.115;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 3.0 * uDir)) * 0.085;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 4.0 * uDir)) * 0.055;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 5.0 * uDir)) * 0.030;
                sum += texture2D(sInputTexture, mirrorCoords(vTexCoord + 6.0 * uDir)) * 0.015;
                gl_FragColor = sum;
            }
        """

        private const val COMPOSITE_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sOesTexture;
            uniform sampler2D sStaticTexture;
            uniform sampler2D sBlurTexture;
            uniform sampler2D sHomeTexture;
            uniform vec2 uResolution;
            uniform vec2 uVideoSize;
            uniform vec2 uHomeSize;
            uniform mat4 uSTMatrix;
            uniform int uMode;
            uniform float uTopOffset;
            uniform float uCoverExpansion;
            uniform float uHomeAlpha;
            uniform int uHomeUseBlur;

            const bool STRETCH_1X1_VERTICALLY = false;

            vec2 mirrorCoords(vec2 uv) {
                vec2 m = mod(uv, 2.0);
                if (m.x < 0.0) m.x += 2.0;
                if (m.y < 0.0) m.y += 2.0;
                if (m.x > 1.0) m.x = 2.0 - m.x;
                if (m.y > 1.0) m.y = 2.0 - m.y;
                return clamp(m, vec2(0.004), vec2(0.996));
            }

            vec4 sampleSharpMedia(vec2 uv) {
                vec2 mUv = mirrorCoords(uv);
                if (uMode == 2) {
                    vec2 glUv = vec2(mUv.x, 1.0 - mUv.y);
                    vec2 transformed = (uSTMatrix * vec4(glUv, 0.0, 1.0)).xy;
                    return texture2D(sOesTexture, transformed);
                } else {
                    return texture2D(sStaticTexture, mUv);
                }
            }

            vec4 sampleSilkyBlur(vec2 uv) {
                vec2 s = vec2(0.018, 0.014);
                vec4 sum = texture2D(sBlurTexture, mirrorCoords(uv)) * 0.28;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2( s.x,  0.0))) * 0.11;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2(-s.x,  0.0))) * 0.11;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2( 0.0,  s.y))) * 0.11;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2( 0.0, -s.y))) * 0.11;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2( s.x,  s.y))) * 0.07;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2(-s.x,  s.y))) * 0.07;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2( s.x, -s.y))) * 0.07;
                sum += texture2D(sBlurTexture, mirrorCoords(uv + vec2(-s.x, -s.y))) * 0.07;
                return sum;
            }

            vec4 sampleHomeDiffusion(vec2 uv) {
                vec2 s1 = vec2(0.045, 0.035);
                vec2 s2 = vec2(0.095, 0.075);
                vec4 sum = sampleSilkyBlur(uv) * 0.24;
                sum += sampleSilkyBlur(uv + vec2( s1.x,  0.0)) * 0.10;
                sum += sampleSilkyBlur(uv + vec2(-s1.x,  0.0)) * 0.10;
                sum += sampleSilkyBlur(uv + vec2( 0.0,  s1.y)) * 0.10;
                sum += sampleSilkyBlur(uv + vec2( 0.0, -s1.y)) * 0.10;
                sum += sampleSilkyBlur(uv + vec2( s2.x,  s2.y)) * 0.09;
                sum += sampleSilkyBlur(uv + vec2(-s2.x,  s2.y)) * 0.09;
                sum += sampleSilkyBlur(uv + vec2( s2.x, -s2.y)) * 0.09;
                sum += sampleSilkyBlur(uv + vec2(-s2.x, -s2.y)) * 0.09;
                return vec4(sum.rgb * 0.82, 1.0);
            }

            vec4 sampleSharpenedStatic(sampler2D tex, vec2 texSize, float screenY) {
                float imgAspect = texSize.x / max(texSize.y, 1.0);
                float screenAspect = uResolution.x / max(uResolution.y, 1.0);
                vec2 uv = vec2(vTexCoord.x, screenY);

                if (screenAspect > imgAspect) {
                    float scaleY = imgAspect / screenAspect;
                    uv.y = (uv.y - 0.5) * scaleY + 0.5;
                } else {
                    float scaleX = screenAspect / imgAspect;
                    uv.x = (uv.x - 0.5) * scaleX + 0.5;
                }

                vec2 texel = vec2(1.0 / max(uResolution.x, 1080.0), 1.0 / max(uResolution.y, 2400.0));
                vec4 center = texture2D(tex, uv);
                vec4 top    = texture2D(tex, uv + vec2(0.0, -texel.y));
                vec4 bottom = texture2D(tex, uv + vec2(0.0,  texel.y));
                vec4 left   = texture2D(tex, uv + vec2(-texel.x, 0.0));
                vec4 right  = texture2D(tex, uv + vec2( texel.x, 0.0));

                vec4 neighbors = (top + bottom + left + right) * 0.25;
                vec4 sharpened = center + (center - neighbors) * 0.45;
                return clamp(sharpened, 0.0, 1.0);
            }

            vec4 computeHomeLayerColor(float screenY) {
                if (uHomeUseBlur == 1) {
                    float mediaAspect = uVideoSize.x / max(uVideoSize.y, 1.0);
                    float screenAspect = uResolution.x / max(uResolution.y, 1.0);
                    vec2 homeBlurUv = vec2(vTexCoord.x, screenY);

                    if (screenAspect < mediaAspect) {
                        float scaleX = screenAspect / mediaAspect;
                        homeBlurUv.x = (homeBlurUv.x - 0.5) * scaleX + 0.5;
                    } else {
                        float scaleY = mediaAspect / screenAspect;
                        homeBlurUv.y = (homeBlurUv.y - 0.5) * scaleY + 0.5;
                    }

                    return sampleHomeDiffusion(homeBlurUv);
                } else {
                    return sampleSharpenedStatic(sHomeTexture, uHomeSize, screenY);
                }
            }

            void main() {
                float screenY = 1.0 - vTexCoord.y;

                if (uHomeAlpha >= 0.999) {
                    gl_FragColor = computeHomeLayerColor(screenY);
                    return;
                }

                vec4 lockColor;
                if (uMode == 0) {
                    lockColor = sampleSharpenedStatic(sStaticTexture, uVideoSize, screenY);
                } else {
                    float baseBoxAspect = 3.0 / 4.0;
                    float baseHeightRatio = (uResolution.x / baseBoxAspect) / max(uResolution.y, 1.0);
                    baseHeightRatio = clamp(baseHeightRatio, 0.45, 0.68);

                    float targetHeightRatio = mix(baseHeightRatio, 0.95, uCoverExpansion);
                    float maxAllowedTop = max(0.025, 1.0 - targetHeightRatio - 0.025);
                    float topOffset = mix(min(uTopOffset, maxAllowedTop), 0.025, uCoverExpansion);

                    float currentBoxAspect = uResolution.x / max(uResolution.y * targetHeightRatio, 1.0);

                    float scaleX = 1.0;
                    if (!STRETCH_1X1_VERTICALLY) {
                        float mediaAspect = uVideoSize.x / max(uVideoSize.y, 1.0);
                        if (currentBoxAspect < mediaAspect) {
                            scaleX = currentBoxAspect / mediaAspect;
                        }
                    }

                    float bottomLimit = topOffset + targetHeightRatio;
                    float centeredX = (vTexCoord.x - 0.5) * scaleX + 0.5;
                    float mainV = (screenY - topOffset) / targetHeightRatio;

                    vec2 sharpUv = vec2(centeredX, clamp(mainV, 0.0, 1.0));
                    vec2 mirrorUv = vec2(centeredX, mainV);

                    vec4 sharpColor = sampleSharpMedia(sharpUv);
                    vec4 silkyBlur = sampleSilkyBlur(mirrorUv);

                    float fadeBand = mix(0.11, 0.04, uCoverExpansion);
                    float bottomFadeBand = mix(0.15, 0.05, uCoverExpansion);

                    float topBlurMask = 0.0;
                    if (topOffset > 0.005) {
                        topBlurMask = 1.0 - smoothstep(topOffset - 0.01, topOffset + (targetHeightRatio * fadeBand), screenY);
                    }
                    float bottomBlurMask = smoothstep(bottomLimit - (targetHeightRatio * bottomFadeBand), bottomLimit + 0.01, screenY);

                    float totalBlurFactor = clamp(topBlurMask + bottomBlurMask, 0.0, 1.0);
                    lockColor = mix(sharpColor, silkyBlur, totalBlurFactor);
                }

                if (uHomeAlpha > 0.001) {
                    vec4 homeColor = computeHomeLayerColor(screenY);
                    gl_FragColor = mix(lockColor, homeColor, uHomeAlpha);
                } else {
                    gl_FragColor = lockColor;
                }
            }
        """
    }
}