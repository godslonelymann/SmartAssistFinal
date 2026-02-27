package com.example.smartassist.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.smartassist.overlay.FloatingService

class ScreenCapturePermissionActivity :
    ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    companion object {
        private const val REQUEST_CODE_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager

        // Always request permission fresh
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_CODE_CAPTURE
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_CAPTURE &&
            resultCode == Activity.RESULT_OK &&
            data != null
        ) {
            val serviceIntent =
                Intent(this, FloatingService::class.java).apply {
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("RESULT_DATA", data)
                }

            startService(serviceIntent)
        }

        finish()
        overridePendingTransition(0, 0)
    }
}