package com.example.smartassist.overlay

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import com.example.smartassist.BuildConfig
import com.example.smartassist.capture.ScreenCaptureManager
import com.example.smartassist.llm.GroqVisionClient
import com.example.smartassist.narration.ScreenNarrationBuilder
import com.example.smartassist.ocr.OcrEngine
import com.example.smartassist.output.SarvamTtsClient
import com.example.smartassist.settings.ScreenCapturePermissionActivity
import com.example.smartassist.settings.UserPreferences
import com.example.smartassist.translation.OfflineTranslator
import com.example.smartassist.understanding.HybridMerger
import kotlinx.coroutines.*
import kotlin.math.abs
import java.io.File
import android.content.ContentValues
import com.example.smartassist.R

class FloatingService : Service() {



    private lateinit var windowManager: WindowManager

    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private lateinit var panelOverlay: FrameLayout
    private lateinit var panelView: LinearLayout
    private lateinit var panelText: TextView
    private lateinit var readAloudButton: Button

    private lateinit var captureButton: Button

    private lateinit var stopButton: Button
    private lateinit var panelParams: WindowManager.LayoutParams

    private var screenWidth = 0
    private var screenHeight = 0

    private var startX = 0
    private var startY = 0
    private var touchX = 0f
    private var touchY = 0f

    private val bubbleSize by lazy { dp(56) }
    private val panelWidth by lazy { dp(300) }
    private val panelMaxHeight by lazy { dp(320) }

    private var lastResultText = ""

    private val translator by lazy { OfflineTranslator() }
    private val ocrEngine by lazy { OcrEngine() }
    private val groqClient by lazy {
        GroqVisionClient(BuildConfig.GROQ_API_KEY)
    }

    private val sarvamTtsClient by lazy {
        SarvamTtsClient(BuildConfig.SARVAM_API_KEY)
    }

    private var mediaPlayer: MediaPlayer? = null

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ---------------------------------------------------------
    // Language Broadcast Receiver
    // ---------------------------------------------------------



    // =========================================================
    // Lifecycle
    // =========================================================

    override fun onCreate() {
        super.onCreate()

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }



        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        val insets =
            metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )

        screenWidth = bounds.width()
        screenHeight = bounds.height() - insets.top - insets.bottom

        createBubble()
        createPanel()



        bubbleParams = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(bubbleView, bubbleParams)
        setBubblePosition(screenWidth - bubbleSize, screenHeight / 3)
    }

    override fun onDestroy() {


        mediaPlayer?.release()
        mediaPlayer = null

        removeAllOverlays()
        translator.close()
        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =========================================================
    // Capture Pipeline
    // =========================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")

        if (resultCode == Activity.RESULT_OK && resultData != null) {

            // ✅ 1️⃣ MUST start foreground FIRST (Android 14+ requirement)
            startForegroundServiceInternal()

            // ✅ 2️⃣ Create MediaProjection IMMEDIATELY (not inside coroutine)
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                        as MediaProjectionManager

            val mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    resultData
                ) ?: return START_NOT_STICKY

            serviceScope.coroutineContext.cancelChildren()

            serviceScope.launch {

                // Remove overlays before capture
                withContext(Dispatchers.Main) {
                    removeAllOverlays()
                }

                // Small delay so previous app fully renders
                delay(200)

                panelText.text =
                    Labels.t(
                        UserPreferences.getSelectedLanguage(applicationContext),
                        "reading"
                    )

                disableReadAloudButton()

                try {

                    val captureManager =
                        ScreenCaptureManager(applicationContext, mediaProjection)

                    val frames = captureManager.captureFrames()
                    if (frames.isEmpty()) return@launch

                    val screenshot =
                        frames.drop(1).firstOrNull() ?: frames.first()

                    saveToGallery(screenshot)

                    val ocrBlocks =
                        ocrEngine.recognize(listOf(screenshot))

                    val ocrText =
                        ocrBlocks.joinToString("\n") { it.text }

                    val ocrConfidence =
                        if (ocrBlocks.isNotEmpty()) {
                            ocrBlocks.map { it.confidence }.average().toFloat()
                        } else {
                            0f
                        }

                    val ocrConfidencePercent = (ocrConfidence * 100).toInt()



                    val groqResponse =
                        try {
                            groqClient.analyzeScreen(
                                screenshot,
                                ocrText
                            )
                        } catch (_: Exception) {
                            null
                        }

                    val hybridResult =
                        HybridMerger.merge(
                            ocrBlocks,
                            groqResponse
                        )


                    val targetLang =
                        UserPreferences.getSelectedLanguage(applicationContext)

                    // 🔥 MODEL DOWNLOAD CHECK HERE
                    if (targetLang != "en" &&
                        !UserPreferences.isModelDownloaded(applicationContext, targetLang)
                    ) {
                        translator.preloadLanguage(targetLang)
                        UserPreferences.setModelDownloaded(applicationContext, targetLang)
                    }

                    // 🔹 Translate ONLY semantic fields, not formatted text

                    val translatedSummary =
                        if (targetLang == "en") {
                            hybridResult.summary
                        } else {
                            try {



                                translator.translate(
                                    hybridResult.summary,
                                    targetLang
                                )

                            } catch (_: Exception) {
                                hybridResult.summary
                            }
                        }

                    val translatedImageDescription =
                        if (targetLang == "en") {
                            hybridResult.imageDescription
                        } else {
                            hybridResult.imageDescription?.let { desc ->
                                try {

                                    translator.translate(desc, targetLang)
                                } catch (_: Exception) {
                                    desc
                                }
                            }
                        }

                    // 🔹 Translate actions list
                    val translatedActions =
                        if (targetLang == "en") {
                            hybridResult.actions
                        } else {
                            hybridResult.actions.map { action ->
                                try {

                                    translator.translate(action, targetLang)
                                } catch (_: Exception) {
                                    action
                                }
                            }
                        }

                    val translatedResult =
                        hybridResult.copy(
                            summary = translatedSummary,
                            actions = translatedActions,
                            imageDescription = translatedImageDescription
                        )

                    val finalFormatted =
                        ScreenNarrationBuilder.build(
                            this@FloatingService,
                            translatedResult
                        )
                    val finalText =
                        if (ocrText.isNotBlank()) {
                            finalFormatted +
                                    "\n\nText Detected:\n" +
                                    ocrText
                        } else {
                            finalFormatted
                        }



                    withContext(Dispatchers.Main) {

                        lastResultText = finalText

                        val confidenceHeader =
                            "OCR Confidence: $ocrConfidencePercent%\n\n"

                        panelText.text = confidenceHeader + finalText

                        enableReadAloudButton()
                        restoreOverlaysAfterCapture()
                    }

                } finally {
                    try { mediaProjection.stop() } catch (_: Exception) {}
                }
            }
        }

        return START_NOT_STICKY
    }

    // =========================================================
    // Sarvam TTS
    // =========================================================

    private fun speakWithSarvam(text: String) {

        if (text.isBlank()) return

        serviceScope.launch {

            disableReadAloudButton()

            val language =
                UserPreferences.getSelectedLanguage(applicationContext)

            val audioBytes =
                sarvamTtsClient.synthesize(text, language)

            if (audioBytes != null) {

                val tempFile = File.createTempFile(
                    "sarvam_tts",
                    ".mp3",
                    cacheDir
                )

                tempFile.writeBytes(audioBytes)

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                    start()

                    setOnCompletionListener {
                        release()
                        tempFile.delete()
                        mediaPlayer = null
                        enableReadAloudButton()
                    }
                }
            } else {
                enableReadAloudButton()
            }
        }
    }

    // =========================================================
    // Language Refresh
    // =========================================================



    // =========================================================
    // Overlay Helpers
    // =========================================================

    private fun removeAllOverlays() {
        try {
            if (bubbleView.parent != null) windowManager.removeView(bubbleView)
        } catch (_: Exception) {}

        try {
            if (panelOverlay.parent != null) windowManager.removeView(panelOverlay)
        } catch (_: Exception) {}
    }

    private fun restoreOverlaysAfterCapture() {
        try {
            if (bubbleView.parent == null)
                windowManager.addView(bubbleView, bubbleParams)

            if (panelOverlay.parent == null) {
                windowManager.addView(panelOverlay, panelParams)
                updatePanelPosition()
            }
        } catch (_: Exception) {}
    }

    // =========================================================
    // UI
    // =========================================================

    private fun createBubble() {

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_smart_assist)
            setColorFilter(0xFFFFFFFF.toInt()) // white icon
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        bubbleView = FrameLayout(this).apply {

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL

                // Subtle purple gradient (Apple-style depth)
                colors = intArrayOf(
                    0xFF7C4DFF.toInt(),  // top lighter purple
                    0xFF5E35B1.toInt()   // bottom deeper purple
                )
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
            }

            elevation = dp(14).toFloat() // soft floating shadow
            alpha = 0.96f

            addView(icon)

            setOnTouchListener(bubbleTouchListener)
        }
    }

    private fun createPanel() {

        val language =
            UserPreferences.getSelectedLanguage(applicationContext)

        panelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xFFFFFFFF.toInt())
            }
            elevation = dp(10).toFloat()
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        panelText = TextView(this).apply {
            text = Labels.t(language, "tap_capture")
            textSize = 14f
            maxWidth = panelWidth - dp(24)
        }

        val scroll = ScrollView(this)
        scroll.addView(panelText)

        panelView.addView(
            scroll,
            LinearLayout.LayoutParams(
                panelWidth - dp(24),
                panelMaxHeight - dp(56)
            )
        )

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        fun addButton(button: Button) {
            buttons.addView(
                button,
                LinearLayout.LayoutParams(0, dp(32), 1f)
            )
        }

        captureButton = Button(this).apply {
            text = Labels.t(language, "capture")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                startActivity(
                    Intent(
                        this@FloatingService,
                        ScreenCapturePermissionActivity::class.java
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        addButton(captureButton)

        readAloudButton = Button(this).apply {
            text = Labels.t(language, "read")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isEnabled = false
            alpha = 0.4f
            setOnClickListener {
                speakWithSarvam(lastResultText)
            }
        }

        addButton(readAloudButton)

        stopButton = Button(this).apply {
            text = Labels.t(language, "stop")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                mediaPlayer?.release()
                mediaPlayer = null
                stopSelf()
            }
        }
        addButton(stopButton)

        panelView.addView(buttons)

        panelOverlay = FrameLayout(this).apply {
            addView(panelView)
        }

        panelParams = WindowManager.LayoutParams(
            panelWidth,
            panelMaxHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private val bubbleTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = bubbleParams.x
                startY = bubbleParams.y
                touchX = event.rawX
                touchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                setBubblePosition(
                    startX + (event.rawX - touchX).toInt(),
                    startY + (event.rawY - touchY).toInt()
                )
                true
            }
            MotionEvent.ACTION_UP -> {
                val moved =
                    abs(event.rawX - touchX) > dp(6) ||
                            abs(event.rawY - touchY) > dp(6)

                if (!moved) togglePanel()
                else snapToEdge()
                true
            }
            else -> false
        }
    }

    private fun togglePanel() {
        if (panelOverlay.parent == null) {
            windowManager.addView(panelOverlay, panelParams)
            updatePanelPosition()
        } else {
            windowManager.removeView(panelOverlay)
        }
    }

    private fun snapToEdge() {
        setBubblePosition(
            if (bubbleParams.x + bubbleSize / 2 > screenWidth / 2)
                screenWidth - bubbleSize else 0,
            bubbleParams.y
        )
    }

    private fun setBubblePosition(x: Int, y: Int) {
        bubbleParams.x = x.coerceIn(0, screenWidth - bubbleSize)
        bubbleParams.y = y.coerceIn(0, screenHeight - bubbleSize)
        windowManager.updateViewLayout(bubbleView, bubbleParams)
        updatePanelPosition()
    }

    private fun updatePanelPosition() {
        if (panelOverlay.parent == null) return

        val bubbleCenterX = bubbleParams.x + bubbleSize / 2

        panelParams.x =
            if (bubbleCenterX > screenWidth / 2)
                (bubbleParams.x - panelWidth).coerceAtLeast(0)
            else
                (bubbleParams.x + bubbleSize)
                    .coerceAtMost(screenWidth - panelWidth)

        panelParams.y =
            bubbleParams.y.coerceIn(0, screenHeight - panelMaxHeight)

        windowManager.updateViewLayout(panelOverlay, panelParams)
    }

    private fun enableReadAloudButton() {
        readAloudButton.isEnabled = true
        readAloudButton.alpha = 1f
    }

    private fun disableReadAloudButton() {
        readAloudButton.isEnabled = false
        readAloudButton.alpha = 0.4f
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundServiceInternal() {

        val channelId = "smart_assist_overlay"

        val channel = NotificationChannel(
            channelId,
            "Smart Assist",
            NotificationManager.IMPORTANCE_MIN
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Smart Assist")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(
            1001,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = "SmartAssist_${System.currentTimeMillis()}.png"
        val resolver = contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartAssist")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val imageUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )

        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private object Labels {

        fun t(language: String, key: String): String {

            return when (language) {

                "hi" -> when (key) {
                    "tap_capture" -> "कैप्चर दबाएँ"
                    "capture" -> "कैप्चर"
                    "read" -> "पढ़ें"
                    "stop" -> "बंद करें"
                    "reading" -> "स्क्रीन पढ़ी जा रही है..."
                    else -> key
                }

                "mr" -> when (key) {
                    "tap_capture" -> "कॅप्चर दाबा"
                    "capture" -> "कॅप्चर"
                    "read" -> "वाचा"
                    "stop" -> "थांबवा"
                    "reading" -> "स्क्रीन वाचली जात आहे..."
                    else -> key
                }

                else -> when (key) {
                    "tap_capture" -> "Tap capture to start"
                    "capture" -> "Capture"
                    "read" -> "Read"
                    "stop" -> "Stop"
                    "reading" -> "Reading screen..."
                    else -> key
                }
            }
        }
    }
}