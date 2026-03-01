# SmartAssist

SmartAssist is an **Android-based screen assistant** that combines on-device OCR, an external generative AI model, offline translation and text-to-speech to help users understand what is on their screen and interact with it.

It captures the current screen, extracts visible text, sends the captured image and OCR text to an AI model for high-level understanding, merges the AI and OCR results, optionally translates the output to the user’s language, and finally displays and reads a concise summary with actionable items.

The app is designed with **accessibility in mind** — it can function as a multilingual screen reader, contextual explainer, and smart summarizer for mobile screens.

---

# Table of Contents

1. [Key Features](#key-features)
2. [Architecture](#architecture)
3. [Installation and Setup](#installation-and-setup)
4. [Running the App](#running-the-app)
5. [Modules and Implementation Details](#modules-and-implementation-details)
6. [Customisation](#customisation)
7. [Security & Privacy Considerations](#security--privacy-considerations)
8. [Dependencies](#dependencies)
9. [Contributing](#contributing)
10. [License](#license)

---

# Key Features

## Movable Overlay Assistant

* Floating bubble overlay
* Expandable action panel
* Drag reposition support
* Foreground service architecture
* Light/Dark theme compatible

## Screen Capture Pipeline

* Uses Android **MediaProjection API**
* Captures multiple frames
* Runs in coroutines (non-blocking)
* Low overhead design

## Multilingual OCR

* Google ML Kit Text Recognition
* Supports:
  * Latin (English)
  * Devanagari (Hindi, Marathi)
* De-duplicates text across frames
* Returns bounding boxes + confidence

## AI-Powered Screen Understanding
* Uses **Llama-4 Scout 17B (Groq API)**
* Sends:
  * Screenshot (Base64)
  * OCR extracted text
* Returns structured JSON:
  * Summary (max 7 sentences)
  * Visible actions (buttons/links/menus)
  * Image description
  * Confidence score

## Hybrid Result Merging
* `HybridMerger` combines:
  * AI structured response
  * OCR fallback
* Handles:
  * AI failure
  * JSON parsing issues
  * Partial responses

## Offline Translation

* Google ML Kit on-device translation
* English → Hindi
* English → Marathi
* Models downloaded once, cached locally
* No internet required after model download

## Narration Builder

* Formats:
  * Summary
  * Image description
  * Numbered action list
* Language-aware headings

## Text-To-Speech (TTS)

* Android TextToSpeech engine
* Adjustable:
  * Pitch
  * Speech rate
* Can stop speech anytime

## Onboarding Flow

* Multi-screen Jetpack Compose flow
* Language selection:
  * English
  * हिन्दी
  * मराठी
* Overlay + notification permission request
* Translation model preloading

## Settings Screen

* Start/Stop SmartAssist
* Change language
* Restart service on language change
* Persist preferences

---

# Architecture

SmartAssist follows a modular pipeline-driven architecture.

```
User
  ↓
FloatingService (Overlay)
  ↓
ScreenCaptureManager
  ↓
OcrEngine
  ↓
GroqVisionClient (LLM)
  ↓
HybridMerger
  ↓
OfflineTranslator
  ↓
ScreenNarrationBuilder
  ↓
TtsPlayer
```

---

## Core Components

### 1️⃣ FloatingService
* Foreground service
* Displays overlay bubble
* Handles capture requests
* Orchestrates entire processing pipeline
* Saves screenshots optionally

### 2️⃣ ScreenCaptureManager
* Uses MediaProjection
* Captures N frames
* Returns list of Bitmaps
* Cleans resources safely

### 3️⃣ OcrEngine
* ML Kit Text Recognition
* Latin + Devanagari
* Deduplicates repeated lines
* Returns structured `TextBlock`

### 4️⃣ GroqVisionClient
* Uses OkHttp
* Sends strict JSON request
* Prompt-engineered for:
  * Summary
  * Actions
  * Image description
* Parses JSON response

### 5️⃣ HybridMerger

* Combines:
  * AI output
  * OCR fallback
* Ensures safe default message

### 6️⃣ OfflineTranslator

* ML Kit Translate
* Downloads models
* Caches models
* Translates final narration

### 7️⃣ ScreenNarrationBuilder

* Builds human-friendly text
* Adds headings
* Numbers actions
* Language-aware formatting

### 8️⃣ TtsPlayer

* Wraps Android TTS
* Safe initialization
* Speech control methods

---

# Installation and Setup

## Prerequisites

* Android Studio (AGP 8.13.2+)
* Kotlin 2.0+
* Java 17
* Device/Emulator API 24+

---

## API Keys Setup

Create `local.properties`:

```properties
GROQ_API_KEY=your_groq_key
SARVAM_API_KEY=your_sarvam_key
OCR_SPACE_API_KEY=your_ocr_key
```

These are injected into `BuildConfig` at compile time.

⚠️ Never commit API keys to GitHub.

---

## Build Steps

1. Clone repository
2. Open in Android Studio
3. Sync Gradle
4. Add `local.properties`
5. Run on device

---

# Permissions

Declared in `AndroidManifest.xml`:

* INTERNET
* FOREGROUND_SERVICE
* SYSTEM_ALERT_WINDOW
* RECORD_AUDIO
* POST_NOTIFICATIONS (Android 13+)

Screen capture permission is requested dynamically every time.

---

# Running the App

## First Launch

* Onboarding
* Language selection
* Permission granting
* Translation model preloading

## Start SmartAssist

* Launch overlay bubble
* Persistent notification

## Capture Screen

* Tap Capture
* Grant MediaProjection permission
* OCR + AI processing
* Display summary + actions

## Read

* TTS reads last narration

## Stop

* Stops foreground service
* Removes overlay

---

# Modules Structure

```
capture/
ocr/
llm/
understanding/
translation/
output/
overlay/
onboarding/
settings/
ui/
```

Each package follows single-responsibility design.

---

# Customisation

## Add New Language

1. Extend `OfflineTranslator`
2. Add new ML Kit language pair
3. Add UI translations
4. Update preferences

---

## Modify AI Prompt

Edit `GroqVisionClient`:

* Change prompt
* Adjust model
* Update base URL

Be mindful of token limits.

---

## Change UI Theme

Modify:

```
ui/theme/Color.kt
ui/theme/Theme.kt
ui/theme/Type.kt
```

Material 3 compatible.

---

## Adjust TTS Settings

Modify default:

```kotlin
rate = 1.0
pitch = 0.85
```

Or expose via Settings UI.

---

# Security & Privacy

## Screen Capture Control

* User must tap Capture
* Permission requested every session

## Data Handling

* Frames processed in memory
* Screenshots optionally saved locally

## API Security

* Keys injected at build time
* Not stored in logs

## Offline Translation

* After download, works without internet
* No translation data sent externally

---

# Dependencies

* Jetpack Compose
* Kotlin Coroutines
* ML Kit Text Recognition
* ML Kit Translate
* OkHttp
* JSON.org
* AndroidX Lifecycle
* Material3

All versions managed in:

```
gradle/libs.versions.toml
```
