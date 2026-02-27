package com.example.smartassist

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.smartassist.onboarding.OnboardingActivity
import com.example.smartassist.onboarding.OnboardingPrefs
import com.example.smartassist.settings.SettingsActivity

class MainLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (OnboardingPrefs.isCompleted(this)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        finish()
    }
}