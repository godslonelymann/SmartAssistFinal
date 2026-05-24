package com.example.smartassist.overlay

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*
import androidx.annotation.RequiresApi
import com.example.smartassist.BuildConfig
import com.example.smartassist.capture.ScreenCaptureManager
import com.example.smartassist.llm.GroqVisionClient
import com.example.smartassist.narration.ScreenNarrationBuilder
import com.example.smartassist.narration.VisibleTextFormatter
import com.example.smartassist.ocr.OcrEngine
import com.example.smartassist.settings.ScreenCapturePermissionActivity
import com.example.smartassist.settings.UserPreferences
import com.example.smartassist.translation.OfflineTranslator
import com.example.smartassist.understanding.HybridMerger
import com.example.smartassist.understanding.ScreenKeyElement
import com.example.smartassist.understanding.ScreenUnderstandingResult
import com.example.smartassist.output.TtsPlayer
import java.util.Locale
import kotlinx.coroutines.*
import kotlin.math.abs

import android.content.ContentValues
import com.example.smartassist.R

class FloatingService : Service() {

    companion object {
        private const val TAG = "FloatingService"
    }



    private lateinit var windowManager: WindowManager

    private lateinit var bubbleView: View
    private lateinit var bubbleParams: WindowManager.LayoutParams

    private lateinit var panelOverlay: FrameLayout
    private lateinit var panelView: LinearLayout
    private lateinit var panelText: TextView
    private lateinit var panelContentContainer: LinearLayout
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

    private lateinit var ttsPlayer: TtsPlayer

    private var lastResultText = ""

    private val translator by lazy { OfflineTranslator() }
    private val ocrEngine by lazy { OcrEngine() }
    private val groqClient by lazy {
        GroqVisionClient(BuildConfig.GROQ_API_KEY)
    }





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

        ttsPlayer = TtsPlayer(this)

        // Optional: default tuning
        ttsPlayer.setSpeechRate(1.00f)
        ttsPlayer.setPitch(0.85f)


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



        ttsPlayer.shutdown()
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
                try {
                    // Remove overlays before capture
                    withContext(Dispatchers.Main) {
                        removeAllOverlays()
                    }

                    // Small delay so previous app fully renders
                    delay(200)

                    showLoadingMessage(
                        Labels.t(
                            UserPreferences.getSelectedLanguage(applicationContext),
                            "reading"
                        )
                    )

                    lastResultText = ""
                    disableReadAloudButton()

                    val captureManager =
                        ScreenCaptureManager(applicationContext, mediaProjection)

                    val frames = captureManager.captureFrames()
                    if (frames.isEmpty()) {
                        Log.w(TAG, "Capture returned no frames")
                        showPipelineMessage("Unable to capture the screen. Please try again.")
                        return@launch
                    }

                    val screenshot =
                        frames.drop(1).firstOrNull() ?: frames.first()

                    saveToGallery(screenshot)

                    val ocrBlocks =
                        ocrEngine.recognize(listOf(screenshot))

                    if (ocrBlocks.isEmpty()) {
                        Log.w(TAG, "OCR returned no text")
                    }

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
                        } catch (e: Exception) {
                            Log.e(TAG, "Groq analysis failed", e)
                            null
                        }

                    if (groqResponse.isNullOrBlank()) {
                        Log.w(TAG, "Groq response unavailable; using fallback")
                    }

                    val hybridResult =
                        HybridMerger.merge(
                            ocrBlocks,
                            groqResponse
                        )


                    val targetLang =
                        UserPreferences.getSelectedLanguage(applicationContext)
                    var translationFallbackUsed = false

                    // 🔥 MODEL DOWNLOAD CHECK HERE
                    if (targetLang != "en" &&
                        !UserPreferences.isModelDownloaded(applicationContext, targetLang)
                    ) {
                        val preloaded = translator.preloadLanguage(targetLang)
                        if (preloaded) {
                            UserPreferences.setModelDownloaded(applicationContext, targetLang)
                        } else {
                            translationFallbackUsed = true
                            Log.w(TAG, "Translation model preload failed for $targetLang")
                        }
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

                            } catch (e: Exception) {
                                translationFallbackUsed = true
                                Log.e(TAG, "Summary translation failed", e)
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
                                } catch (e: Exception) {
                                    translationFallbackUsed = true
                                    Log.e(TAG, "Image description translation failed", e)
                                    desc
                                }
                            }
                        }

                    val translatedAssistantAnswer =
                        if (targetLang == "en") {
                            hybridResult.assistantAnswer
                        } else {
                            try {
                                translator.translate(
                                    hybridResult.assistantAnswer,
                                    targetLang
                                )
                            } catch (e: Exception) {
                                translationFallbackUsed = true
                                Log.e(TAG, "Assistant answer translation failed", e)
                                hybridResult.assistantAnswer
                            }
                        }

                    val translatedVisibleTextExplanation =
                        if (targetLang == "en") {
                            hybridResult.visibleTextExplanation
                        } else {
                            try {
                                translator.translate(
                                    hybridResult.visibleTextExplanation,
                                    targetLang
                                )
                            } catch (e: Exception) {
                                translationFallbackUsed = true
                                Log.e(TAG, "Visible text explanation translation failed", e)
                                hybridResult.visibleTextExplanation
                            }
                        }

                    val translatedKeyElements =
                        if (targetLang == "en") {
                            hybridResult.keyElements
                        } else {
                            hybridResult.keyElements.map { item ->
                                ScreenKeyElement(
                                    title = try {
                                        translator.translate(item.title, targetLang)
                                    } catch (e: Exception) {
                                        translationFallbackUsed = true
                                        Log.e(TAG, "Key element title translation failed", e)
                                        item.title
                                    },
                                    description = try {
                                        translator.translate(item.description, targetLang)
                                    } catch (e: Exception) {
                                        translationFallbackUsed = true
                                        Log.e(TAG, "Key element description translation failed", e)
                                        item.description
                                    }
                                )
                            }
                        }

                    val translatedContextInfo =
                        if (targetLang == "en") {
                            hybridResult.contextInfo
                        } else {
                            try {
                                translator.translate(
                                    hybridResult.contextInfo,
                                    targetLang
                                )
                            } catch (e: Exception) {
                                translationFallbackUsed = true
                                Log.e(TAG, "Context translation failed", e)
                                hybridResult.contextInfo
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
                                } catch (e: Exception) {
                                    translationFallbackUsed = true
                                    Log.e(TAG, "Action translation failed", e)
                                    action
                                }
                            }
                        }

                    val translatedResult =
                        hybridResult.copy(
                            summary = translatedSummary,
                            actions = translatedActions,
                            imageDescription = translatedImageDescription,
                            assistantAnswer = translatedAssistantAnswer,
                            visibleTextLines = hybridResult.visibleTextLines,
                            visibleTextExplanation = translatedVisibleTextExplanation,
                            visibleTextGroups = hybridResult.visibleTextGroups,
                            keyElements = translatedKeyElements,
                            contextInfo = translatedContextInfo
                        )

                    val finalFormatted =
                        ScreenNarrationBuilder.build(
                            this@FloatingService,
                            translatedResult
                        )
                    val finalTextBase =
                        finalFormatted.ifBlank {
                            "I could not understand the screen right now. Please try again."
                        }

                    val finalText =
                        if (translationFallbackUsed && targetLang != "en") {
                            finalTextBase + "\n\nTranslation was unavailable, so some text may remain in English."
                        } else {
                            finalTextBase
                        }




                    withContext(Dispatchers.Main) {

                        lastResultText = finalText

                        renderScreenResult(
                            translatedResult,
                            ocrConfidencePercent
                        )

                        if (finalText.isNotBlank()) {
                            enableReadAloudButton()
                        } else {
                            disableReadAloudButton()
                        }
                    }

                } catch (e: CancellationException) {
                    Log.d(TAG, "Capture pipeline cancelled")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Capture pipeline failed", e)
                    showPipelineMessage("Unable to read the screen right now. Please try again.")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        restoreOverlaysAfterCapture()
                    }
                    try { mediaProjection.stop() } catch (_: Exception) {}
                }
            }
        }

        return START_NOT_STICKY
    }




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

        panelContentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        panelText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(38, 40, 44))
            maxWidth = panelWidth - dp(24)
        }

        val scroll = ScrollView(this)
        scroll.addView(panelContentContainer)

        showLoadingMessage(Labels.t(language, "tap_capture"))

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

                val language =
                    UserPreferences.getSelectedLanguage(applicationContext)

                val locale = when (language) {
                    "hi" -> Locale("hi")
                    "mr" -> Locale("mr")
                    else -> Locale.ENGLISH
                }

                ttsPlayer.speak(lastResultText, locale)
            }
        }

        addButton(readAloudButton)

        stopButton = Button(this).apply {
            text = Labels.t(language, "stop")
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                ttsPlayer.stop()
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

    private fun showPipelineMessage(message: String) {
        lastResultText = message
        showLoadingMessage(message)
        if (message.isNotBlank()) {
            enableReadAloudButton()
        } else {
            disableReadAloudButton()
        }
    }

    private fun clearPanelContent() {
        panelContentContainer.removeAllViews()
    }

    private fun showLoadingMessage(message: String) {
        clearPanelContent()
        panelText.text = message
        panelText.setPadding(0, 0, 0, 0)
        panelContentContainer.addView(panelText)
    }

    private fun renderScreenResult(
        result: ScreenUnderstandingResult,
        ocrConfidencePercent: Int
    ) {
        clearPanelContent()

        addCaptionText("OCR Confidence: $ocrConfidencePercent%")

        val assistantAnswer =
            result.assistantAnswer.ifBlank {
                result.summary
            }

        if (assistantAnswer.isNotBlank()) {
            addSectionTitle("Assistant Answer")
            addBodyText(assistantAnswer)
        }

        val visibleTextGroups =
            result.visibleTextGroups.ifEmpty {
                VisibleTextFormatter.groupVisibleText(
                    lines = result.visibleTextLines,
                    screenType = result.screenType,
                    title = result.title
                )
            }

        val visibleTextContent =
            VisibleTextFormatter.formatGroupsForDisplay(visibleTextGroups)

        if (visibleTextContent.isNotBlank()) {
            addSectionTitle("Visible Text")
            addVisibleTextCard(listOf(visibleTextContent))
        }

        if (result.keyElements.isNotEmpty()) {
            addSectionTitle("Key Elements on the Screen")
            result.keyElements.forEach { item ->
                addKeyElementCard(item)
            }
        }

        if (result.contextInfo.isNotBlank()) {
            addSectionTitle("Context")
            addBodyText(result.contextInfo)
        }

        result.imageDescription
            ?.takeIf { it.isNotBlank() }
            ?.let { image ->
                addSectionTitle("Image Description")
                addBodyText(image)
            }

        if (result.actions.isNotEmpty()) {
            addSectionTitle("Actions")
            addActionsList(result.actions)
        }
    }

    private fun addCaptionText(text: String) {
        panelContentContainer.addView(
            TextView(this).apply {
                this.text = text
                textSize = 12f
                setTextColor(Color.rgb(104, 109, 118))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )
    }

    private fun addSectionTitle(title: String) {
        panelContentContainer.addView(
            TextView(this).apply {
                text = title
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(28, 30, 34))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        )
    }

    private fun addBodyText(text: String) {
        panelContentContainer.addView(
            TextView(this).apply {
                this.text = text.trim()
                textSize = 14f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(Color.rgb(52, 56, 63))
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun addVisibleTextCard(lines: List<String>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.rgb(245, 246, 250))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }

        lines.forEachIndexed { index, line ->
            card.addView(
                TextView(this).apply {
                    text = line
                    textSize = 14f
                    setLineSpacing(dp(2).toFloat(), 1f)
                    setTextColor(Color.rgb(50, 54, 61))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = dp(4)
                    }
                }
            )
        }

        panelContentContainer.addView(
            card,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        )
    }

    private fun addKeyElementCard(item: ScreenKeyElement) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(2))
        }

        row.addView(
            TextView(this).apply {
                text = "• "
                textSize = 14f
                setTextColor(Color.rgb(52, 56, 63))
            }
        )

        row.addView(
            TextView(this).apply {
                val content = buildString {
                    append(item.title)
                    if (item.title.isNotBlank() && item.description.isNotBlank()) {
                        append(": ")
                    }
                    append(item.description)
                }

                text =
                    SpannableString(content).apply {
                        if (item.title.isNotBlank()) {
                            setSpan(
                                StyleSpan(Typeface.BOLD),
                                0,
                                item.title.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                textSize = 14f
                setTextColor(Color.rgb(52, 56, 63))
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        panelContentContainer.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun addActionsList(actions: List<String>) {
        actions.forEach { action ->
            panelContentContainer.addView(
                TextView(this).apply {
                    text = "• ${action.trim()}"
                    textSize = 14f
                    setTextColor(Color.rgb(52, 56, 63))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(4)
                }
            )
        }
    }

    private fun formatVisibleTextLines(lines: List<String>): List<String> {
        val formattedLines = mutableListOf<String>()

        lines.forEach { rawLine ->
            val normalizedLine =
                rawLine
                    .trim()
                    .replace(Regex("\\s+"), " ")

            if (normalizedLine.isBlank()) return@forEach

            if (formattedLines.lastOrNull() != normalizedLine) {
                formattedLines.add(normalizedLine)
            }
        }

        return formattedLines
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
