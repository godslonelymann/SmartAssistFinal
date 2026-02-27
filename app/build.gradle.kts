plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.smartassist"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.smartassist"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val groqApiKey: String =
            project.findProperty("GROQ_API_KEY") as String? ?: ""

        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"$groqApiKey\""
        )

        // 🔥 SARVAM KEY
        val sarvamApiKey: String =
            project.findProperty("SARVAM_API_KEY") as String? ?: ""

        buildConfigField(
            "String",
            "SARVAM_API_KEY",
            "\"$sarvamApiKey\""
        )

        // 🔥 OCR SPACE KEY (ADD THIS)
        val ocrApiKey: String =
            project.findProperty("OCR_SPACE_API_KEY") as String? ?: ""

        buildConfigField(
            "String",
            "OCR_SPACE_API_KEY",
            "\"$ocrApiKey\""
        )


    }



    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        // MUST match Compose BOM version
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {

    // ------------------------------------------------
    // 🔹 Core Android
    // ------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ------------------------------------------------
    // 🔹 Compose (BOM Controlled - DO NOT add versions manually)
    // ------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("com.google.android.material:material:1.11.0")
    // ------------------------------------------------
    // 🔹 AppCompat (Required for ScreenCapturePermissionActivity)
    // ------------------------------------------------
    implementation("androidx.appcompat:appcompat:1.6.1")

    // ------------------------------------------------
    // 🔹 Kotlin Coroutines
    // ------------------------------------------------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ------------------------------------------------
    // 🔹 ML Kit OCR
    // ------------------------------------------------
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.0")

    // ------------------------------------------------
    // 🔹 ML Kit Translation
    // ------------------------------------------------
    implementation("com.google.mlkit:translate:17.0.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // ------------------------------------------------
    // 🔹 Gemini REST (HTTP)
    // ------------------------------------------------
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ------------------------------------------------
    // 🔹 JSON parsing
    // ------------------------------------------------
    implementation("org.json:json:20231013")

    // ------------------------------------------------
    // 🔹 Testing
    // ------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}