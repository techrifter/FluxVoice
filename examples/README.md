# FluxVoice Examples

Reference code for every integration pattern. Each file is self-contained.

## Files

| File | What it covers |
|---|---|
| `01_FluxVoiceScreen.kt` | Full-screen UI, mode switching, `FluxVoiceTheme`, all `FluxVoiceScreen` params |
| `02_FluxVoiceView_Widget.kt` | Embedded orb widget, `FluxVoiceViewConfig`, `FluxVoiceColors` |
| `03_HeadlessEngine.kt` | `FluxVoiceEngine` without Compose UI - all config options and callbacks |
| `04_NoTTS.kt` | Skip Cartesia, use Android's `TextToSpeech` via `onResponse` callback (Fallback) |
| `05_CustomProviders.kt` | Implement `SttProvider`, `LlmProvider`, `TtsProvider` from scratch |
| `06_AdvancedSettings.kt` | Settings screen pattern - DataStore persistence, navigation, wired back into engine |

## Setup

**1. Add to `app/build.gradle.kts`:**
```kotlin
implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-android:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-core:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-provider-groq:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
```

**2. Add to `AndroidManifest.xml`:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```