package com.example.smartassist.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume

class ScreenCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {

    companion object {
        private const val FRAME_COUNT = 3
        private const val FRAME_DELAY_MS = 120L
        private const val WARMUP_DELAY_MS = 250L
    }

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null

    /**
     * Public API used by FloatingService
     */
    suspend fun captureFrames(): List<Bitmap> =
        suspendCancellableCoroutine { continuation ->

            val metrics = DisplayMetrics()
            val windowManager =
                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            imageReader = ImageReader.newInstance(
                width,
                height,
                PixelFormat.RGBA_8888,
                FRAME_COUNT
            )

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    cleanup()
                }
            }

            mediaProjection.registerCallback(
                projectionCallback!!,
                Handler(Looper.getMainLooper())
            )

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SmartAssistCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )

            val handler = Handler(Looper.getMainLooper())
            val capturedBitmaps = mutableListOf<Bitmap>()

            fun captureNext(index: Int) {
                if (index >= FRAME_COUNT) {
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(capturedBitmaps)
                    }
                    return
                }

                handler.postDelayed({

                    val image = imageReader?.acquireLatestImage()

                    if (image != null) {
                        val bitmap = imageToBitmap(image, width, height)
                        if (bitmap != null) {
                            capturedBitmaps.add(bitmap)
                        }
                        image.close()
                    }

                    captureNext(index + 1)

                }, FRAME_DELAY_MS)
            }

            handler.postDelayed(
                { captureNext(0) },
                WARMUP_DELAY_MS
            )
        }

    /**
     * Converts ImageReader Image to Bitmap safely
     */
    private fun imageToBitmap(
        image: android.media.Image,
        width: Int,
        height: Int
    ): Bitmap? {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            projectionCallback?.let {
                mediaProjection.unregisterCallback(it)
            }
        } catch (_: Exception) {
        } finally {
            virtualDisplay = null
            imageReader = null
            projectionCallback = null
        }
    }
}