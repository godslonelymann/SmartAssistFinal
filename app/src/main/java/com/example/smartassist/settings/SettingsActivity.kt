package com.example.smartassist.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartassist.overlay.FloatingService

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {

    val context = LocalContext.current

    var selectedLanguageTag by remember {
        mutableStateOf(
            UserPreferences.getSelectedLanguage(context)
        )
    }

    val language = selectedLanguageTag

    val languages = listOf(
        "English" to "en",
        "Hindi" to "hi",
        "Marathi" to "mr"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Labels.t(language, "settings_title")) }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text(
                text = Labels.t(language, "output_language"),
                style = MaterialTheme.typography.titleMedium
            )

            LanguageDropdown(
                languages = languages,
                selectedTag = selectedLanguageTag,
                onSelected = { newTag ->
                    if (newTag != selectedLanguageTag) {

                        selectedLanguageTag = newTag

                        // Save preference
                        UserPreferences.setSelectedLanguage(
                            context,
                            newTag
                        )

                        // 🔥 Restart FloatingService immediately
                        context.stopService(
                            Intent(context, FloatingService::class.java)
                        )

                        context.startService(
                            Intent(context, FloatingService::class.java)
                        )
                        toast(
                            context,
                            Labels.t(newTag, "language_updated")
                        )
                    }
                }
            )

            HorizontalDivider()

            Button(
                onClick = {
                    startSmartAssist(context)
                    toast(context, Labels.t(language, "started"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(Labels.t(language, "start"))
            }

            Button(
                onClick = {
                    stopSmartAssist(context)
                    toast(context, Labels.t(language, "stopped"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(Labels.t(language, "stop"))
            }
        }
    }
}

@Composable
private fun LanguageDropdown(
    languages: List<Pair<String, String>>,
    selectedTag: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel =
        languages.firstOrNull { it.second == selectedTag }?.first
            ?: languages.first().first

    Box {

        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { (label, tag) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(tag)
                    }
                )
            }
        }
    }
}

/* --------------------------------------------------
   Service helpers
-------------------------------------------------- */

private fun startSmartAssist(context: Context) {
    context.startService(
        Intent(context, FloatingService::class.java)
    )
}

private fun stopSmartAssist(context: Context) {
    context.stopService(
        Intent(context, FloatingService::class.java)
    )
}

private fun toast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

/* --------------------------------------------------
   🔥 Labels System (LOCALIZED HERE ONLY)
-------------------------------------------------- */

private object Labels {

    fun t(language: String, key: String): String {

        return when (language) {

            "hi" -> when (key) {
                "settings_title" -> "स्मार्ट असिस्ट सेटिंग्स"
                "output_language" -> "आउटपुट भाषा"
                "start" -> "स्मार्ट असिस्ट शुरू करें"
                "stop" -> "स्मार्ट असिस्ट बंद करें"
                "started" -> "स्मार्ट असिस्ट शुरू हुआ"
                "stopped" -> "स्मार्ट असिस्ट बंद हुआ"
                "language_updated" -> "भाषा अपडेट की गई"
                else -> key
            }

            "mr" -> when (key) {
                "settings_title" -> "स्मार्ट असिस्ट सेटिंग्स"
                "output_language" -> "आउटपुट भाषा"
                "start" -> "स्मार्ट असिस्ट सुरू करा"
                "stop" -> "स्मार्ट असिस्ट थांबवा"
                "started" -> "स्मार्ट असिस्ट सुरू झाला"
                "stopped" -> "स्मार्ट असिस्ट थांबवला"
                "language_updated" -> "भाषा अपडेट झाली"
                else -> key
            }

            else -> when (key) {
                "settings_title" -> "Smart Assist Settings"
                "output_language" -> "Output Language"
                "start" -> "Start Smart Assist"
                "stop" -> "Stop Smart Assist"
                "started" -> "Smart Assist Started"
                "stopped" -> "Smart Assist Stopped"
                "language_updated" -> "Language Updated"
                else -> key
            }
        }
    }
}