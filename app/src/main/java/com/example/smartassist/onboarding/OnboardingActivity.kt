package com.example.smartassist.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import com.example.smartassist.settings.SettingsActivity
import com.example.smartassist.settings.UserPreferences

class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            var step by remember { mutableStateOf(0) }

            // ✅ Language state (default English)
            var selectedLanguage by remember {
                mutableStateOf(
                    UserPreferences.getSelectedLanguage(this@OnboardingActivity)
                )
            }

            Surface(color = MaterialTheme.colorScheme.background) {

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "OnboardingTransition"
                ) { currentStep: Int ->

                    when (currentStep) {

                        // ------------------------------------------------
                        // STEP 1 — LANGUAGE
                        // ------------------------------------------------
                        0 -> LanguageScreen(
                            selectedLanguage = selectedLanguage,
                            onLanguageSelected = { selectedCode ->

                                // Update local state
                                selectedLanguage = selectedCode

                                // Persist selection
                                UserPreferences.setSelectedLanguage(
                                    this@OnboardingActivity,
                                    selectedCode
                                )

                                step = 1
                            }
                        )

                        // ------------------------------------------------
                        // STEP 2 — WELCOME
                        // ------------------------------------------------
                        1 -> WelcomeScreen(
                            language = selectedLanguage,
                            onNext = {
                                step = 2
                            }
                        )

                        // ------------------------------------------------
                        // STEP 3 — PERMISSIONS
                        // ------------------------------------------------
                        2 -> PermissionScreen(
                            language = selectedLanguage,
                            onFinish = {

                                OnboardingPrefs.markCompleted(
                                    this@OnboardingActivity
                                )

                                startActivity(
                                    Intent(
                                        this@OnboardingActivity,
                                        SettingsActivity::class.java
                                    )
                                )

                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}