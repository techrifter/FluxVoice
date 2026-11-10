# FluxVoice: SDK Examples

Reference code for every integration pattern. Each file is self-contained.

---

## Examples

| #  | File                                                        | Integration Pattern                                                                      | Complexity        |
| -- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------------- | ----------------- |
| 01 | [`01_FluxVoiceScreen.kt`](01_FluxVoiceScreen.kt)           | Full-screen voice UI with built-in animated orb, mode switching & settings nav           | ⭐ Easiest        |
| 02 | [`02_FluxVoiceView_Widget.kt`](02_FluxVoiceView_Widget.kt) | Embedded orb widget inside your own screen (custom size & brand colors)                  | ⭐ Easiest        |
| 03 | [`03_HeadlessEngine.kt`](03_HeadlessEngine.kt)             | Headless engine with your own UI (every config option & callback demonstrated)           | ⭐⭐ Intermediate |
| 04 | [`04_NoTTS.kt`](04_NoTTS.kt)                               | Zero API keys for TTS: use Android's built-in `TextToSpeech` via `onResponse`        | ⭐ Easiest        |
| 05 | [`05_CustomProviders.kt`](05_CustomProviders.kt)           | Implement `SttProvider`, `LlmProvider`, `TtsProvider` from scratch for any service | ⭐⭐⭐ Advanced   |
| 06 | [`06_AdvancedSettings.kt`](06_AdvancedSettings.kt)         | Persistent settings screen with DataStore, navigation, and `onTurnMetrics` telemetry   | ⭐⭐ Intermediate |

---

## Setup

### 1. Add the JitPack repository (required for WebRTC VAD)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }  // required for barge-in VAD
    }
}
```

### 2. Add dependencies to `app/build.gradle.kts`

```kotlin
dependencies {
    // Core + UI (pick the tier you need: compose transitively includes android + core)
    implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.1.0")   // full Compose UI
    // implementation("com.techrifter.fluxvoice:fluxvoice-android:1.1.0") // headless only
    // implementation("com.techrifter.fluxvoice:fluxvoice-core:1.1.0")    // interfaces only

    // Providers: mix and match freely
    implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.1.0")   // STT via Deepgram Nova-3
    implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.1.0")   // LLM via any OpenAI-compatible endpoint
    implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.1.0")   // TTS via Cartesia Sonic

    // Required for Compose integration
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
}
```

### 3. Add permissions to `AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`RECORD_AUDIO` runtime permission is requested automatically on the first tap.

---

## Which Example Do I Start With?

```
I want a complete voice screen with zero extra code
  └─► 01_FluxVoiceScreen.kt

I want to embed a voice orb inside my own existing screen layout
  └─► 02_FluxVoiceView_Widget.kt

I want full control over my own UI (no FluxVoice layout at all)
  └─► 03_HeadlessEngine.kt

I don't want a Cartesia account / want free TTS
  └─► 04_NoTTS.kt

I want to plug in a service FluxVoice doesn't ship a provider for
  └─► 05_CustomProviders.kt

I want persistent user settings (DataStore) and pipeline telemetry
  └─► 06_AdvancedSettings.kt
```

---

## Key APIs at a Glance

### DSL entry point

```kotlin
val controller = rememberFluxVoice(key1, key2, ...) {
    // ── Providers ─────────────────────────────────────────────────────
    stt = DeepgramSttProvider(apiKey = "...", model = "nova-3")
    llm = OpenAiCompatibleLlmProvider(apiKey = "...", modelId = "openai/gpt-oss-120b")
    tts = CartesiaTtsProvider(apiKey = "...", voiceId = CARTESIA_VOICE)

    // ── Persona ────────────────────────────────────────────────────────
    systemPrompt    = "You are Flux, an intelligent voice AI assistant. Deliver high-signal, direct answers tailored for spoken audio without preamble, markdown, or lists."

    // ── Generation ─────────────────────────────────────────────────────
    temperature     = 0.7f       // 0.0 to 2.0
    maxOutputTokens = 1024       // per-turn limit
    maxContextTurns = 10         // sliding window; older turns drop off

    // ── Barge-in ───────────────────────────────────────────────────────
    vadEnabled      = true       // WebRTC VAD for instant interrupt
    vadSensitivity  = 1000       // 200 to 3000 (lower = more sensitive)

    // ── Backchannels ───────────────────────────────────────────────────
    backchannelEnabled  = false  // say "Got it." while LLM thinks
    backchannelDelayMs  = 1500L  // wait time before filler plays

    // ── Speech Output ──────────────────────────────────────────────────
    speechRate = 1.0f            // 0.5 to 2.0
    pitch      = 1.0f            // 0.5 to 2.0

    // ── Callbacks ──────────────────────────────────────────────────────
    onTranscript  { text     -> /* user's words, final */       }
    onResponse    { response -> /* full AI response text */      }
    onError       { error    -> /* handle pipeline errors */     }
    onStateChange { from, to -> /* IDLE/LISTENING/THINKING/... */ }
    onTurnMetrics { metrics  ->
        // Fired after every turn with real measured latencies:
        // metrics.sttLatencyMs       -> speech to final transcript
        // metrics.timeToFirstTokenMs -> TTFT: transcript to 1st LLM token
        // metrics.timeToFirstAudioMs -> TTFA: transcript to audio starts
        // metrics.totalTurnDurationMs -> full end-to-end roundtrip
        // metrics.totalTokens         -> input + output token count
        // metrics.bargeInOccurred     -> true if user interrupted
    }
}
```

### Display options

```kotlin
// Full-screen animated voice UI (recommended)
FluxVoiceScreen(controller = controller)

// Embedded orb widget inside your own layout
FluxVoiceView(
    controller = controller,
    config = FluxVoiceViewConfig(size = 200.dp, showBrandName = false)
)

// Headless: your own UI driven by StateFlow
val state by controller.state.collectAsStateWithLifecycle()
when (state.voiceState) {
    VoiceState.IDLE         -> { /* tap to speak */ }
    VoiceState.LISTENING    -> { /* show transcript: state.partialTranscript */ }
    VoiceState.THINKING     -> { /* spinner */ }
    VoiceState.SPEAKING     -> { /* show response: state.aiResponse */ }
    VoiceState.INTERRUPTING -> { /* stopping... */ }
}
```

### Controller actions

| Method                             | When to call                                                |
| ---------------------------------- | ----------------------------------------------------------- |
| `controller.tap()`               | Primary action: context-sensitive (start / stop / barge-in) |
| `controller.clear()`             | Cancel turn, clear history, return to IDLE                  |
| `controller.dismissError()`      | Dismiss error banner                                        |
| `controller.setSpeechRate(rate)` | Adjust TTS speed live (0.5 to 2.0)                          |
| `controller.setPitch(pitch)`     | Adjust TTS pitch live (0.5 to 2.0)                          |
| `controller.destroy()`           | Release all resources (call from `onCleared`)             |

---

## Supported Providers & Integration

### 1. Built-in Production SDK Modules (Published on Maven Central)

| Type          | Module                     | Provider Class                                                           | Details                                                                              |
| ------------- | -------------------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ |
| **STT** | `fluxvoice-stt-deepgram` | `DeepgramSttProvider(apiKey, model, endpointingMs)`                    | WebSocket streaming, hardware AEC/NS, socket pre-warming (`preConnect`)            |
| **LLM** | `fluxvoice-provider-llm` | `OpenAiCompatibleLlmProvider(apiKey, modelId, baseUrl, customHeaders)` | Universal OpenAI-compatible SSE streaming (Groq, Gemini, OpenAI, OpenRouter, Ollama) |
| **TTS** | `fluxvoice-tts-cartesia` | `CartesiaTtsProvider(apiKey, voiceId, modelId, speechRate, pitch)`     | Sonic 3.6 ultra-low latency PCM streaming via Android `AudioTrack`                 |

### 2. Universal LLM Endpoints with `OpenAiCompatibleLlmProvider`

You don't need new libraries to switch LLMs! Point `OpenAiCompatibleLlmProvider` to any endpoint:

| Provider                      | `baseUrl`                                                                  | Default Model                         | Setup Note                                              |
| ----------------------------- | ---------------------------------------------------------------------------- | ------------------------------------- | ------------------------------------------------------- |
| **Groq** (Default)     | `https://api.groq.com/openai/v1/chat/completions`                          | `openai/gpt-oss-120b`               | Ultra-fast token streaming                              |
| **Google Gemini**       | `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions` | `gemini-2.5-flash`                  | Pass your Google AI Studio API key                      |
| **OpenAI**              | `https://api.openai.com/v1/chat/completions`                               | `gpt-4o-mini`                       | Standard OpenAI API key                                 |
| **OpenRouter**          | `https://openrouter.ai/api/v1/chat/completions`                            | `meta-llama/llama-3.3-70b-instruct` | Pass `customHeaders = mapOf("HTTP-Referer" to "...")` |
| **Local Ollama / vLLM** | `http://localhost:11434/v1/chat/completions`                               | `llama3.2`                          | Pass empty `apiKey = ""` for local keyless servers    |

### 3. Custom STT & TTS Providers

> [!NOTE]
> **Understanding Provider Scope**:
>
> - **LLM (`fluxvoice-provider-llm`)**: Universal BYOK out of the box. Works with any OpenAI-compatible completions endpoint (Groq, Gemini, OpenAI, OpenRouter, Ollama, Together AI) simply by configuring `baseUrl` and `apiKey`.
> - **STT (`fluxvoice-stt-deepgram`)**: Dedicated adapter for Deepgram's live WebSocket protocol.
> - **TTS (`fluxvoice-tts-cartesia`)**: Dedicated adapter for Cartesia's Sonic streaming protocol.
> - **Custom STT/TTS BYOK**: To connect custom speech servers (like `/v1/audio/transcriptions` or `/v1/audio/speech`), implement the lightweight `SttProvider` and `TtsProvider` interfaces from `fluxvoice-core` as shown in [`05_CustomProviders.kt`](05_CustomProviders.kt). You can also refer production-ready implementations directly from the companion [`/app`](../app) directory (`CustomSttEngine.kt` and `CustomTtsEngine.kt`).

Want to use another speech engine? Simply implement the pure Kotlin interfaces in `fluxvoice-core` (demonstrated in [`05_CustomProviders.kt`](05_CustomProviders.kt)):

- **Speech-to-Text**: Implement `SttProvider` (for Groq Whisper, OpenAI Whisper, or Whisper.cpp).
- **Text-to-Speech**: Implement `TtsProvider` (for ElevenLabs, Deepgram Aura, OpenAI TTS, or Android TTS).
- **Playground Workbench**: The companion [`/app`](../app) contains full, ready-to-use implementations of all these engines, letting you test them interactively before exporting clean SDK code!

---

## Tips

- **Recreate engine on key change**: pass the changed value as a key to `rememberFluxVoice(key) { ... }` - the engine and all providers rebuild automatically.
- **Headless in a ViewModel**: use `FluxVoiceEngine(config, viewModelScope)` and call `destroy()` from `onCleared()`.
- **API keys**: never hardcode in source. Use `BuildConfig` fields from `local.properties`, or the app's built-in KeyVault (AES-256 GCM via Android Keystore).
- **VAD sensitivity**: start at `1000`, lower to `600 to 800` in quiet rooms, raise to `1500 to 2000` in noisy environments.
- **Context window**: each turn costs `~input_tokens` (all history) + `output_tokens`. Keep `maxContextTurns` proportional to your budget.
