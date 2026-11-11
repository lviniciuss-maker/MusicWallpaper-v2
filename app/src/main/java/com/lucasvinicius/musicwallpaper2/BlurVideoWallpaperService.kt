package com.lucasvinicius.musicwallpaper2

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
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
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

        private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
        private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

        private var videoSurfaceTexture: SurfaceTexture? = null
        private var decoderSurface: Surface? = null
        private var oesTextureId: Int = 0
        private var staticTextureId: Int = 0

        // Dois Framebuffers (Ping-Pong) em resolução 256x340 para Blur Gaussiano Puro (Zero quadriculado)
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
        private var mediaWidth = 1080
        private var mediaHeight = 1440

        // Preferências dinâmicas do usuário (Posição Vertical e Intensidade do Blur)
        private var userTopOffset = 0.20f
        private var userBlurScale = 1.00f

        // 0 = Foto padrão da galeria
        // 1 = Capa estática da música (cortada nas laterais para caber na caixa 3:4) com Blur espelhado
        // 2 = Capa animada .m3u8 (3:4 nativa ou 1:1 cortada nas laterais para caixa 3:4) com Blur espelhado
        private var displayMode = 0
        private var hasStaticTexture = false

        private val stMatrix = FloatArray(16)
        private var hasVideoFrame = false
        private var isEngineVisible = false
        private var currentPlayingM3u8Url: String? = null

        private var currentTrackKey: String = ""
        private var searchGeneration = 0
        private var mediaSessionManager: MediaSessionManager? = null
        private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

        private var isWaitingForPauseDelay = false
        private val noMusicDelayRunnable = Runnable {
            isWaitingForPauseDelay = false
            applyNoMusicStateNow()
        }

        private val activePollRunnable = object : Runnable {
            override fun run() {
                if (isEngineVisible) {
                    checkCurrentMusic()
                    mainHandler.postDelayed(this, 1500L)
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
                            if (displayMode == 0) showDefaultWallpaper()
                        }
                        "com.lucasvinicius.musicwallpaper2.ACTION_SETTINGS_UPDATED" -> {
                            loadUserPreferences()
                            drawFrame(updateTex = false)
                        }
                    }
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            mainHandler.post {
                try {
                    loadUserPreferences()
                    initOpenGL(holder.surface)
                    initPlayer()
                    registerUpdatesReceiver()
                    showDefaultWallpaper()
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
                drawFrame(updateTex = false)
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
                    mainHandler.postDelayed(activePollRunnable, 1500L)
                    if (displayMode == 2) {
                        exoPlayer?.play()
                    }
                    drawFrame(updateTex = false)
                } else {
                    exoPlayer?.pause()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isEngineVisible = false
            mainHandler.post {
                mainHandler.removeCallbacks(activePollRunnable)
                mainHandler.removeCallbacks(noMusicDelayRunnable)
                isWaitingForPauseDelay = false
                unregisterUpdatesReceiver()
                stopListeningToMusic()
                releasePlayer()
                releaseOpenGL()
            }
        }

        private fun loadUserPreferences() {
            val prefs = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE)
            val percent = prefs.getInt("top_offset_percent", 20).coerceIn(0, 40)
            userTopOffset = percent / 100f

            val blurPercent = prefs.getInt("blur_strength_percent", 100).coerceIn(60, 100)
            userBlurScale = blurPercent / 100f
        }

        private fun registerUpdatesReceiver() {
            try {
                val filter = IntentFilter().apply {
                    addAction("com.lucasvinicius.musicwallpaper2.ACTION_DEFAULT_WALLPAPER_UPDATED")
                    addAction("com.lucasvinicius.musicwallpaper2.ACTION_SETTINGS_UPDATED")
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

        // Verifica se a sessão pertence a um dos apps de streaming de música suportados
        // (incluindo Bitchord e Cider!)
        private fun isSupportedMusicApp(packageName: String): Boolean {
            return packageName in SUPPORTED_MUSIC_PACKAGES ||
                    packageName.contains("apple.android.music", ignoreCase = true) ||
                    packageName.contains("spotify", ignoreCase = true) ||
                    packageName.contains("youtube.music", ignoreCase = true) ||
                    packageName.contains("tidal", ignoreCase = true) ||
                    packageName.contains("amazon.mp3", ignoreCase = true) ||
                    packageName.contains("bitchord", ignoreCase = true) ||
                    packageName.contains("cider", ignoreCase = true)
        }

        private fun isActivePlaybackState(state: Int?): Boolean {
            return state == PlaybackState.STATE_PLAYING ||
                    state == PlaybackState.STATE_BUFFERING ||
                    state == PlaybackState.STATE_FAST_FORWARDING ||
                    state == PlaybackState.STATE_SKIPPING_TO_NEXT ||
                    state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
                    state == PlaybackState.STATE_CONNECTING
        }

        private fun checkCurrentMusic() {
            try {
                val componentName = ComponentName(this@BlurVideoWallpaperService, MusicNotificationListener::class.java)
                val controllers = mediaSessionManager?.getActiveSessions(componentName) ?: controllerCallbacks.keys.toList()

                // 1º Procura nos apps suportados (Spotify, Apple Music, YT Music, Tidal, Amazon, Bitchord e Cider)
                val playingController = controllers.firstOrNull {
                    isSupportedMusicApp(it.packageName) && isActivePlaybackState(it.playbackState?.state)
                } ?: controllers.firstOrNull {
                    // Reserva caso o usuário use uma variante/clone de algum player de música
                    isActivePlaybackState(it.playbackState?.state)
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

                // Leitura compatível com todos os players (inclusive Bitchord e Cider, que podem usar DISPLAY_TITLE / SUBTITLE)
                val title = (
                        metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                            ?: ""
                        ).trim()

                var artist = (
                        metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_WRITER)
                            ?: ""
                        ).trim()

                var album = (
                        metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
                            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION)
                            ?: ""
                        ).trim()

                // Alguns clientes como Cider/Bitchord às vezes enviam "Artista - Álbum" juntos no subtítulo
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

                val newKey = "$artist|$album|$title"
                if (newKey != currentTrackKey) {
                    currentTrackKey = newKey
                    searchGeneration++
                    val currentGen = searchGeneration
                    Log.d("BlurWallpaper", "App: ${playingController.packageName} -> Artista: $artist | Álbum: $album | Música: $title")

                    exoPlayer?.pause()
                    hasVideoFrame = false
                    currentPlayingM3u8Url = null

                    val cachedBitmap = staticCoverCache[newKey] ?: safeFallbackBitmap
                    if (cachedBitmap != null) {
                        uploadStaticBitmapToOpenGL(cachedBitmap, isDefaultFullWallpaper = false)
                    }

                    val cachedM3u8 = m3u8Cache[newKey]
                    if (cachedM3u8 != null) {
                        startVideoPlayback(cachedM3u8)
                    } else {
                        fetchCoverForTrack(newKey, artist, album, title, safeFallbackBitmap, currentGen)
                    }
                } else {
                    if (displayMode == 0 && safeFallbackBitmap != null) {
                        uploadStaticBitmapToOpenGL(safeFallbackBitmap, isDefaultFullWallpaper = false)
                    }
                    if (displayMode == 2 && isEngineVisible && exoPlayer?.isPlaying == false) {
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
                playWhenReady = true
            }
        }

        private fun scheduleNoMusicFallback() {
            if (displayMode == 0 && currentTrackKey.isEmpty()) return
            if (!isWaitingForPauseDelay) {
                isWaitingForPauseDelay = true
                mainHandler.postDelayed(noMusicDelayRunnable, 5000L)
            }
        }

        private fun applyNoMusicStateNow() {
            if (currentTrackKey.isNotEmpty() || displayMode != 0) {
                currentTrackKey = ""
                currentPlayingM3u8Url = null
                searchGeneration++
                exoPlayer?.pause()
                exoPlayer?.clearMediaItems()
                hasVideoFrame = false
                showDefaultWallpaper()
            }
        }

        private fun makeGlSafeBitmap(src: Bitmap?): Bitmap? {
            if (src == null || src.isRecycled || src.width <= 0 || src.height <= 0) return null
            return try {
                val maxDim = max(src.width, src.height)
                val scale = if (maxDim > 1400) 1400f / maxDim else 1f
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

        private fun showDefaultWallpaper() {
            try {
                val file = File(filesDir, "default_wallpaper.jpg")
                if (file.exists()) {
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val raw = BitmapFactory.decodeFile(file.absolutePath, options)
                    val safeBitmap = makeGlSafeBitmap(raw)
                    if (safeBitmap != null) {
                        uploadStaticBitmapToOpenGL(safeBitmap, isDefaultFullWallpaper = true)
                        return
                    }
                }
            } catch (_: Throwable) {}
            displayMode = 0
            hasStaticTexture = false
            drawInitialBackground()
        }

        private fun uploadStaticBitmapToOpenGL(bitmap: Bitmap, isDefaultFullWallpaper: Boolean) {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) return
            try {
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return

                mediaWidth = bitmap.width.coerceAtLeast(1)
                mediaHeight = bitmap.height.coerceAtLeast(1)
                displayMode = if (isDefaultFullWallpaper) 0 else 1
                hasStaticTexture = true

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                drawFrame(updateTex = false)
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
                            mediaWidth = videoSize.width
                            mediaHeight = videoSize.height
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        displayMode = 2
                        hasVideoFrame = true
                        drawFrame(updateTex = true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("BlurWallpaper", "Erro no ExoPlayer: ${error.message}", error)
                    }
                })
                setVideoSurface(decoderSurface)
            }
        }

        private fun fetchCoverForTrack(
            trackKey: String,
            artist: String,
            album: String,
            title: String,
            fallbackBitmap: Bitmap?,
            generation: Int
        ) {
            thread {
                try {
                    val cleanArtist = artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: artist
                    val cleanTitle = title.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                    val cleanAlbum = album.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()

                    val encArtist = URLEncoder.encode(cleanArtist, "UTF-8")

                    var m3u8Found: String? = null

                    if (cleanAlbum.isNotEmpty()) {
                        val encAlbum = URLEncoder.encode(cleanAlbum, "UTF-8")
                        m3u8Found = requestM3u8FromEndpoint("https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encAlbum")
                    }

                    if (m3u8Found == null && cleanTitle.isNotEmpty() && !cleanTitle.equals(cleanAlbum, ignoreCase = true)) {
                        val encTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                        m3u8Found = requestM3u8FromEndpoint("https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encTitle")
                    }

                    if (m3u8Found == null) {
                        val itunesAlbum = findOfficialAlbumOnItunes(cleanArtist, cleanTitle.ifEmpty { cleanAlbum })
                        if (itunesAlbum != null && !itunesAlbum.equals(cleanAlbum, ignoreCase = true)) {
                            val encItunesAlbum = URLEncoder.encode(itunesAlbum, "UTF-8")
                            m3u8Found = requestM3u8FromEndpoint("https://artwork.m8tec.top/api/v1/artwork/search?artist=$encArtist&album=$encItunesAlbum")
                        }
                    }

                    if (generation != searchGeneration) return@thread

                    if (m3u8Found != null) {
                        m3u8Cache[trackKey] = m3u8Found
                        val finalUrl = m3u8Found
                        mainHandler.post {
                            if (generation != searchGeneration) return@post
                            startVideoPlayback(finalUrl)
                        }
                    } else {
                        val downloadedStatic = fetchHighResStaticCover(cleanArtist, cleanAlbum, cleanTitle)
                        val safeStatic = makeGlSafeBitmap(downloadedStatic) ?: fallbackBitmap
                        if (safeStatic != null) {
                            staticCoverCache[trackKey] = safeStatic
                        }
                        mainHandler.post {
                            if (generation != searchGeneration) return@post
                            exoPlayer?.pause()
                            exoPlayer?.clearMediaItems()
                            hasVideoFrame = false
                            if (safeStatic != null) {
                                uploadStaticBitmapToOpenGL(safeStatic, isDefaultFullWallpaper = false)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.e("BlurWallpaper", "Erro ao buscar capa na API", e)
                }
            }
        }

        private fun findOfficialAlbumOnItunes(artist: String, trackOrAlbum: String): String? {
            return try {
                val term = URLEncoder.encode("$artist $trackOrAlbum", "UTF-8")
                val url = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=1")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    results.getJSONObject(0).optString("collectionName").takeIf { it.isNotEmpty() }
                } else null
            } catch (_: Throwable) {
                null
            }
        }

        private fun fetchHighResStaticCover(artist: String, album: String, title: String): Bitmap? {
            return try {
                val queryText = if (album.isNotEmpty()) "$artist $album" else "$artist $title"
                val term = URLEncoder.encode(queryText, "UTF-8")
                val url = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=2")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val results = json.optJSONArray("results") ?: return null
                if (results.length() == 0) return null

                val artworkUrl100 = results.getJSONObject(0).optString("artworkUrl100")
                if (artworkUrl100.isEmpty()) return null

                val highResUrl = artworkUrl100.replace("100x100bb", "1000x1000bb")
                val imgConn = (URL(highResUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(imgConn.inputStream, null, opts)
            } catch (_: Throwable) {
                null
            }
        }

        private fun requestM3u8FromEndpoint(urlString: String): String? {
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

                val tallFieldRegex = Regex(""""(?:url_tall|tall_url|tallUrl|editorialVideoTall)"\s*:\s*"(https:[^"]+\.m3u8)"""")
                val tallMatch = tallFieldRegex.find(body)?.groupValues?.get(1)?.replace("\\/", "/")
                if (!tallMatch.isNullOrEmpty()) {
                    return tallMatch
                }

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
            if (displayMode != 2) {
                displayMode = 2
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

            val textures = IntArray(4)
            GLES20.glGenTextures(4, textures, 0)

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

        private fun drawFrame(updateTex: Boolean) {
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

            if (displayMode == 0 && !hasStaticTexture) {
                drawInitialBackground()
                return
            }

            if (displayMode == 2 && !hasVideoFrame) {
                return
            }

            // Motor Ping-Pong 4 Estágios (256x340) com intensidade controlada pelo Slider (userBlurScale)
            if (displayMode == 1 || displayMode == 2) {
                GLES20.glViewport(0, 0, fboWidth, fboHeight)

                // Estágio 1: Blur Horizontal inicial
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

                // Estágio 2: Blur Vertical inicial
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass2Id)
                GLES20.glUseProgram(pass2Program)
                bindQuadAttributes(pass2Program)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.0f, 0.008f * userBlurScale)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass1Id)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(pass2Program, "sInputTexture"), 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Estágio 3: 2ª Passada Horizontal
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass1Id)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.016f * userBlurScale, 0.0f)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass2Id)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Estágio 4: 2ª Passada Vertical
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboPass2Id)
                GLES20.glUniform2f(GLES20.glGetUniformLocation(pass2Program, "uDir"), 0.0f, 0.014f * userBlurScale)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass1Id)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }

            // Composição Final na Tela de Bloqueio
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
            GLES20.glClearColor(0.05f, 0.05f, 0.08f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(compositeProgram)
            bindQuadAttributes(compositeProgram)

            GLES20.glUniform2f(GLES20.glGetUniformLocation(compositeProgram, "uResolution"), surfaceWidth.toFloat(), surfaceHeight.toFloat())
            GLES20.glUniform2f(GLES20.glGetUniformLocation(compositeProgram, "uVideoSize"), mediaWidth.toFloat(), mediaHeight.toFloat())
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(compositeProgram, "uSTMatrix"), 1, false, stMatrix, 0)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "uMode"), displayMode)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(compositeProgram, "uTopOffset"), userTopOffset)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sOesTexture"), 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, staticTextureId)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sStaticTexture"), 1)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texPass2Id)
            GLES20.glUniform1i(GLES20.glGetUniformLocation(compositeProgram, "sBlurTexture"), 2)

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
        // Lista oficial de pacotes dos players suportados (incluindo Bitchord e Cider)
        private val SUPPORTED_MUSIC_PACKAGES = setOf(
            "com.apple.android.music",               // Apple Music
            "com.spotify.music",                     // Spotify
            "com.google.android.apps.youtube.music", // YouTube Music
            "com.aspiro.tidal",                      // Tidal
            "com.amazon.mp3",                        // Amazon Music
            "com.music.bitchord",                    // Bitchord
            "com.cidercollective.cider"              // Cider
        )

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

        // Composição Final com Caixa 3:4 Padronizada e Corte Lateral (Center-Crop) sem deformar!
        private const val COMPOSITE_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sOesTexture;
            uniform sampler2D sStaticTexture;
            uniform sampler2D sBlurTexture;
            uniform vec2 uResolution;
            uniform vec2 uVideoSize;
            uniform mat4 uSTMatrix;
            uniform int uMode;
            uniform float uTopOffset;

            // false = Mantém a proporção original da arte e corta as laterais (Center-Crop) para encaixar em 3:4!
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

            void main() {
                float screenY = 1.0 - vTexCoord.y;

                // Modo 0: Papel de parede da galeria em tela cheia (quando sem música)
                if (uMode == 0) {
                    float imgAspect = uVideoSize.x / max(uVideoSize.y, 1.0);
                    float screenAspect = uResolution.x / max(uResolution.y, 1.0);
                    vec2 uv = vec2(vTexCoord.x, screenY);

                    if (screenAspect > imgAspect) {
                        float scaleY = imgAspect / screenAspect;
                        uv.y = (uv.y - 0.5) * scaleY + 0.5;
                    } else {
                        float scaleX = screenAspect / imgAspect;
                        uv.x = (uv.x - 0.5) * scaleX + 0.5;
                    }
                    gl_FragColor = texture2D(sStaticTexture, clamp(uv, vec2(0.002), vec2(0.998)));
                    return;
                }

                float topOffset = uTopOffset;
                
                // Caixa 3:4 fixa na tela:
                float fixedBoxAspect = 3.0 / 4.0;
                float targetHeightRatio = (uResolution.x / fixedBoxAspect) / max(uResolution.y, 1.0);
                targetHeightRatio = clamp(targetHeightRatio, 0.45, 0.68);

                float scaleX = 1.0;
                if (!STRETCH_1X1_VERTICALLY) {
                    float mediaAspect = uVideoSize.x / max(uVideoSize.y, 1.0);
                    if (fixedBoxAspect < mediaAspect) {
                        // Corta as laterais proporcionalmente (Center-Crop) para formar 3:4 sem deformar!
                        scaleX = fixedBoxAspect / mediaAspect;
                    }
                }

                float bottomLimit = topOffset + targetHeightRatio;
                float centeredX = (vTexCoord.x - 0.5) * scaleX + 0.5;
                float mainV = (screenY - topOffset) / targetHeightRatio;

                vec2 sharpUv = vec2(centeredX, clamp(mainV, 0.0, 1.0));
                vec2 mirrorUv = vec2(centeredX, mainV);

                vec4 sharpColor = sampleSharpMedia(sharpUv);
                vec4 silkyBlur = sampleSilkyBlur(mirrorUv);

                float topBlurMask = 0.0;
                if (topOffset > 0.01) {
                    topBlurMask = 1.0 - smoothstep(topOffset - 0.01, topOffset + (targetHeightRatio * 0.11), screenY);
                }
                float bottomBlurMask = smoothstep(bottomLimit - (targetHeightRatio * 0.15), bottomLimit + 0.01, screenY);

                float totalBlurFactor = clamp(topBlurMask + bottomBlurMask, 0.0, 1.0);

                gl_FragColor = mix(sharpColor, silkyBlur, totalBlurFactor);
            }
        """
    }
}