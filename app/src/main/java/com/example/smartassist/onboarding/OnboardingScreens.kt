package com.example.smartassist.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// ========================================================
// 🔤 SIMPLE LOCAL TRANSLATIONS
// ========================================================

private fun t(language: String, key: String): String {
    return when (language) {

        "hi" -> when (key) {
            "choose_language" -> "अपनी भाषा चुनें"
            "continue" -> "जारी रखें"
            "welcome_title" -> "स्मार्ट असिस्ट में आपका स्वागत है"
            "welcome_desc" -> "स्मार्ट असिस्ट आपकी स्क्रीन पर क्या हो रहा है\nयह समझने में मदद करता है।\n\nयह टेक्स्ट पढ़ता है, इमेज समझता है,\nऔर सब कुछ साफ़ तरीके से समझाता है।"
            "next" -> "आगे बढ़ें"
            "permissions" -> "अनुमतियाँ आवश्यक हैं"
            "overlay" -> "ओवरले अनुमति दें"
            "notification" -> "नोटिफिकेशन अनुमति दें"
            "finish" -> "सेटअप पूरा करें"
            "progress" -> "अनुमतियाँ दी गईं"
            else -> key
        }

        "mr" -> when (key) {
            "choose_language" -> "आपली भाषा निवडा"
            "continue" -> "पुढे जा"
            "welcome_title" -> "स्मार्ट असिस्ट मध्ये आपले स्वागत आहे"
            "welcome_desc" -> "स्मार्ट असिस्ट तुम्हाला स्क्रीनवर काय चालू आहे\nहे समजण्यास मदत करते.\n\nहे मजकूर वाचते, प्रतिमा समजते,\nआणि सर्व स्पष्टपणे समजावते."
            "next" -> "पुढे"
            "permissions" -> "परवानग्या आवश्यक"
            "overlay" -> "ओव्हरले परवानगी द्या"
            "notification" -> "सूचना परवानगी द्या"
            "finish" -> "सेटअप पूर्ण करा"
            "progress" -> "परवानग्या दिल्या"
            else -> key
        }

        else -> when (key) {
            "choose_language" -> "Choose your language"
            "continue" -> "Continue"
            "welcome_title" -> "Welcome to Smart Assist"
            "welcome_desc" -> "Smart Assist helps you understand\nwhat is happening on your screen.\n\nIt reads text, understands images,\nand explains everything clearly."
            "next" -> "Next"
            "permissions" -> "Permissions Required"
            "overlay" -> "Grant Overlay Permission"
            "notification" -> "Grant Notification Permission"
            "finish" -> "Finish Setup"
            "progress" -> "permissions granted"
            else -> key
        }
    }
}

// ========================================================
// 1️⃣ LANGUAGE SCREEN
// ========================================================

@Composable
fun LanguageScreen(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit
) {

    val languages = listOf(
        "English" to "en",
        "Hindi" to "hi",
        "Marathi" to "mr"
    )

    var selected by remember { mutableStateOf(selectedLanguage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = t(selected, "choose_language"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        languages.forEach { pair ->
            Row(verticalAlignment = Alignment.CenterVertically) {

                RadioButton(
                    selected = selected == pair.second,
                    onClick = { selected = pair.second }
                )

                Text(text = pair.first)
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = { onLanguageSelected(selected) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t(selected, "continue"))
        }
    }
}

// ========================================================
// 2️⃣ WELCOME SCREEN
// ========================================================

@Composable
fun WelcomeScreen(
    language: String,
    onNext: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = t(language, "welcome_title"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = t(language, "welcome_desc"),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t(language, "next"))
        }
    }
}

// ========================================================
// 3️⃣ PERMISSION SCREEN
// ========================================================

@Composable
fun PermissionScreen(
    language: String,
    onFinish: () -> Unit
) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var overlayGranted by remember { mutableStateOf(false) }
    var notificationGranted by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = Settings.canDrawOverlays(context)

                if (Build.VERSION.SDK_INT >= 33) {
                    notificationGranted =
                        context.checkSelfPermission(
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val totalPermissions =
        if (Build.VERSION.SDK_INT >= 33) 2 else 1

    val grantedCount =
        if (Build.VERSION.SDK_INT >= 33)
            listOf(overlayGranted, notificationGranted).count { it }
        else
            listOf(overlayGranted).count { it }

    val allGranted = grantedCount == totalPermissions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = t(language, "permissions"),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "$grantedCount / $totalPermissions ${t(language, "progress")}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(32.dp))

        PermissionButton(
            text = t(language, "overlay"),
            granted = overlayGranted
        ) {
            requestOverlay(context)
        }

        Spacer(Modifier.height(16.dp))

        if (Build.VERSION.SDK_INT >= 33) {

            PermissionButton(
                text = t(language, "notification"),
                granted = notificationGranted
            ) {
                requestNotification(context)
            }

            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onFinish,
            enabled = allGranted,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(t(language, "finish"))
        }
    }
}

// --------------------------------------------------------

@Composable
private fun PermissionButton(
    text: String,
    granted: Boolean,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text)
            Spacer(Modifier.weight(1f))
            if (granted) Text("✅")
        }
    }
}

// --------------------------------------------------------

private fun requestOverlay(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}

private fun requestNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= 33) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}