<h1 align="center">FluxVoice</h1>

<p align="center">
  <b>The Ultra-Low-Latency Voice AI SDK & Interactive Visual Playground for Android</b>
</p>

<p align="center">
  Sub-second voice turnaround (~560ms TTFA) • Streaming STT → LLM → TTS sentence pipelining • Hardware-accelerated auto barge-in • Universal BYOK • Production Jetpack Compose SDK & Headless Engine
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=com.techrifter.fluxvoice">
    <img src="https://img.shields.io/maven-central/v/com.techrifter.fluxvoice/fluxvoice-core?color=blue" alt="Maven Central"/>
  </a>
  <a href="LICENSE.md">
    <img src="https://img.shields.io/badge/license-Apache%202.0-2563eb" alt="License"/>
  </a>
  <img src="https://img.shields.io/badge/API-24%2B-22c55e" alt="API 24+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0+"/>
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Jetpack%20Compose-4285F4?logo=android&logoColor=white" alt="Platform Android"/>
</p>

<br/>

<table align="center">
<tr>
<td align="center" valign="middle">
<img src="app/assets/FluxVoice.webp" width="230" alt="FluxVoice Logo"/>
<br/>
<sub><b>FluxVoice Ecosystem</b></sub>
</td>
<td width="30"></td>
<td align="center" valign="middle">
<img src="app/assets/FluxVoice_Demo.webp" width="250" alt="FluxVoice Realtime Voice Demo"/>
<br/>
<sub><b>Voice AI Studio</b></sub>
</td>
</tr>
</table>

<p align="center">
  <sub>Natural human cadence • Instant interrupt (barge-in) • Sub-second voice synthesis • Multi-stack studio workbench</sub>
</p>

---

## ⚡ What is FluxVoice?

Building natural, human-speed conversational AI on Android has traditionally been an engineering nightmare:

- Raw 16-bit PCM microphone capture and hardware audio routing
- Acoustic echo feedback loops between device speakers and microphone
- Sluggish turn-taking where the user waits seconds for an LLM to generate its complete answer before audio starts
- Fragile interruption handling when the user speaks while the assistant is speaking
- Complex vendor lock-in across incompatible speech APIs

**FluxVoice eliminates these complexities completely.**

FluxVoice is a complete, two-part Voice AI ecosystem designed specifically for the Android platform:

1. **The FluxVoice SDK (Maven Central)**: Six modular, lightweight Kotlin libraries. Drop a complete conversational voice screen into your app in ~10 lines of Jetpack Compose, or use the headless engine with your own custom architecture.
2. **The FluxVoice Playground & Architecture Studio (`/app`)**: An interactive Android workbench to benchmark speech and language models in real-time, inspect millisecond-level telemetry (STT, TTFT, TTFA, Total Latency), configure and persist multiple custom voice stacks, and **export production-ready Kotlin SDK code with one tap**.

```
                           FLUXVOICE STREAMING ARCHITECTURE
                     
 ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐
 │  Streaming STT  │──────►│  Streaming LLM  │──────►│  Streaming TTS  │
 │ (Deepgram /     │       │ (Groq / Gemini /│       │ (Cartesia Sonic/│
 │  Whisper / BYOK)│       │  OpenAI / BYOK) │       │  Eleven / BYOK) │
 └────────┬────────┘       └────────┬────────┘       └────────┬────────┘
          ▲                         │                         │
          │             Sentence Extractor (Punctuation)      │
          │             Dispatches audio as tokens stream     ▼
     [Microphone]                                         [Speaker]
          ▲                                                   │
          │                   Google WebRTC VAD               │
          └────────── Hardware Acoustic Echo Canceler ────────┘
                      Instant Auto Barge-In (< 50ms)
```

---

## 🏆 Real-World Benchmarks & Telemetry

Every conversation turn is automatically benchmarked on-device by the FluxVoice engine. The built-in **Turn Benchmarks HUD** logs exact millisecond telemetry across different voice stacks:

| Run                              | Stack Architecture                                             | Time-to-First-Token (TTFT) | Time-to-First-Audio (TTFA) | Tokens Generated | Key Behavior                                                                       |
| -------------------------------- | -------------------------------------------------------------- | -------------------------- | -------------------------- | ---------------- | ---------------------------------------------------------------------------------- |
| **01 (Ultra-Fast Cloud)**  | Deepgram Nova-3 + Groq GPT-OSS-120B + Cartesia Sonic 3.6       | **564 ms**           | **565 ms**           | ~46 tokens       | **Instant playback**: Audio starts 1 ms after first token arrival            |
| **02 (Custom + Barge-In)** | OpenAI Whisper + Groq GPT-OSS-120B + Android System TTS        | **692 ms**           | **693 ms**           | ~58 tokens       | **Sub-700ms on-device TTS**: Instant acoustic barge-in interruption verified |
| **03 (Deep Reasoning)**    | Deepgram Nova-3 + Google Gemini 2.5 Flash + Cartesia Sonic 3.6 | **3700 ms**          | **1540 ms**          | 125 tokens       | **Complex reasoning**: First-principles multi-sentence spoken response       |

### Why TTFA Matches TTFT (Sub-1ms Audio Dispatch)

Notice how in Run 01 and Run 02, **TTFA begins within 1 ms of TTFT** (565 ms vs 564 ms, and 693 ms vs 692 ms).
Traditional architectures force the user to wait until the LLM completes its entire generation before audio synthesis begins. FluxVoice's sentence-level pipelining synthesizes and streams the first chunk immediately, delivering human-speed conversational responsiveness on Android.

---

## 📱 Visual Tour: Playground Studio & Modes

<table align="center">
  <tr>
    <td align="center"><b>Fast Mode (Instant Responses)</b></td>
    <td align="center"><b>Thinking Mode (Deep Reasoning)</b></td>
  </tr>
  <tr>
    <td align="center"><img src="app/assets/FastMode.webp" width="250" alt="Fast Mode"/></td>
    <td align="center"><img src="app/assets/ThinkingMode.webp" width="250" alt="Thinking Mode"/></td>
  </tr>
  <tr>
    <td align="center"><sub>Low-latency, punchy answers for voice commands</sub></td>
    <td align="center"><sub>Flowing, structured spoken reasoning for architecture</sub></td>
  </tr>
  <tr>
    <td align="center"><b>Pipeline Studio (Multi-Stack Architecture)</b></td>
    <td align="center"><b>Hardware KeyVault & Settings</b></td>
  </tr>
  <tr>
    <td align="center"><img src="app/assets/CustomMode.webp" width="250" alt="Pipeline Studio"/></td>
    <td align="center"><img src="app/assets/CustomSettings.webp" width="250" alt="KeyVault Settings"/></td>
  </tr>
  <tr>
    <td align="center"><sub>Create, customize, compare & export multiple voice stacks</sub></td>
    <td align="center"><sub>Hardware-backed AES-256 GCM credentials in Keystore</sub></td>
  </tr>
</table>

---

## 🚀 Key Capabilities

### 1. ⚡ Sentence-Level Voice Orchestration

FluxVoice streams LLM tokens through a `SentenceExtractor` boundary scanner. As soon as a complete sentence forms, it is instantly dispatched to the TTS synthesizer. The assistant begins speaking while subsequent sentences are still generating, achieving sub-second first-audio turnaround (~565 ms measured on device).

### 2. 🎙️ Hardware-Accelerated Auto Barge-In

Speak naturally at any time - just like talking to a human. Device hardware **Acoustic Echo Cancellation (AEC)** prevents speaker audio from leaking into the microphone, while continuous **Google WebRTC Voice Activity Detection (VAD)** senses user voice energy. When you interrupt:

- Playback stops in < 50ms
- In-flight LLM streaming job is cleanly cancelled
- Echo-decay window absorbs lingering room reflection
- STT mic re-arms immediately

### 3. 🛠️ Visual Pipeline Studio & 1-Click Kotlin SDK Export

The companion Android app is an interactive workbench for AI voice architects:

- **Multiple Custom Voice Stacks**: Create, name, tweak, and persist unlimited custom pipeline configurations alongside presets.
- **Side-by-Side Comparison**: Switch between Deepgram Nova-3, Groq Whisper, OpenAI Whisper, Groq LPUs, Gemini 2.5 Flash, OpenRouter, Cartesia Sonic, ElevenLabs, and on-device Android TTS.
- **1-Click Kotlin Export**: Tap "Export Code" in any stack to copy-paste production-ready Kotlin Compose SDK code directly into your app.

### 4. 📊 Real-Time Telemetry HUD & Turn Benchmarks

Track exact millisecond metrics for every single conversation turn:

- **STT Latency**: Speech end to transcript finalization
- **TTFT**: Time-to-First-Token from LLM
- **TTFA**: Time-to-First-Audio playback
- **Total Turn Duration**: End-to-end conversational roundtrip
- **Token Usage**: Accurate input/output token accounting.
- **Barge-in Indicator**: Tracks whether and when interruption occurred

### 5. 🔐 Privacy-First Hardware Security (KeyVault)

All commercial API keys entered in the workbench are encrypted directly on-device with **AES-256 GCM** keys stored in the **Android Keystore hardware security module**. Zero plaintext storage on disk. Zero audio or transcript recording to remote servers.

---

## 🧩 Which Module Do I Need?

FluxVoice is published as six modular, lightweight Android libraries on Maven Central:

| Module                     | What it provides                                                                                                                                                                           | What you write                           |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| `fluxvoice-compose`      | Complete animated voice UI (`FluxVoiceScreen`), reactive visualizer (`FluxVoiceView`), Material You dynamic theme (`FluxVoiceTheme`), and Compose lifecycle hook (`rememberFluxVoice`) | ~10 lines of Jetpack Compose             |
| `fluxvoice-android`      | `FluxVoiceEngine`, hardware audio capture with AEC, WebRTC VAD, sliding-window conversation memory, sentence streaming                                                                   | Driven by `StateFlow` in ViewModels    |
| `fluxvoice-core`         | Core interfaces (`SttProvider`, `LlmProvider`, `TtsProvider`), models (`TurnMetrics`, `GenerationConfig`, `StreamEvent`), and config DSL (`FluxVoiceConfig`)                 | Pure Kotlin (no Android UI dependencies) |
| `fluxvoice-stt-deepgram` | Deepgram Nova-3 streaming speech-to-text with WebSocket pre-warming (`preConnect`) and automatic reconnect                                                                               | Pass your Deepgram API key               |
| `fluxvoice-provider-llm` | Universal OpenAI-compatible SSE chat completions engine (Groq, Gemini, OpenAI, OpenRouter, Together AI, Ollama, vLLM)                                                                      | Pass API key and model ID                |
| `fluxvoice-tts-cartesia` | Cartesia Sonic 3.6 real-time streaming voice synthesis with sub-200ms audio output and live pitch/rate controls                                                                            | Pass key and voice ID                    |

### Dependency Graph

```
fluxvoice-compose ──► fluxvoice-android ──► fluxvoice-core ◄── fluxvoice-stt-deepgram
                                                           ◄── fluxvoice-provider-llm
                                                           ◄── fluxvoice-tts-cartesia
```

Adding `fluxvoice-compose` automatically brings in `fluxvoice-android` and `fluxvoice-core`.

---

## 📦 Quickstart

Add a complete, animated conversational voice screen to your Android app in 3 simple steps:

### 1. Add Repositories & Dependencies

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Required for WebRTC VAD
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    // Complete Compose UI + Engine
    implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")

    // Speech & Language Providers
    implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
    implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
    implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")

    // Compose lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
}
```

### 2. Add Android Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

> **Note**: `RECORD_AUDIO` runtime permission is requested automatically when the user taps the visualizer.

### 3. Display the Voice Screen

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxVoiceTheme {
                VoiceScreen()
            }
        }
    }
}

@Composable
fun VoiceScreen() {
    var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }

    val controller = rememberFluxVoice(mode) {
        systemPrompt = mode.defaultPrompt
        stt = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_KEY)
        llm = OpenAiCompatibleLlmProvider(apiKey = BuildConfig.GROQ_KEY)
        tts = CartesiaTtsProvider(apiKey = BuildConfig.CARTESIA_KEY)

        // Real-time telemetry callback
        onTurnMetrics { metrics ->
            Log.d("Voice", "TTFT: ${metrics.timeToFirstTokenMs}ms | TTFA: ${metrics.timeToFirstAudioMs}ms")
        }
    }

    FluxVoiceScreen(
        controller   = controller,
        initialMode  = mode,
        onModeChange = { mode = it }
    )
}
```

You now have a production-ready voice conversation screen with a fluid sound-wave visualizer, live transcript bubbles, mode switching, automatic barge-in, and error handling.

---

## 🎨 Three Ways to Integrate

### Option A: Full-Screen Out of the Box (`FluxVoiceScreen`)

The quickest way to add voice. Provides complete UI, mode badges, live speech bubbles, settings navigation, and animated state transitions.

```kotlin
FluxVoiceScreen(
    controller      = controller,
    initialMode     = FluxVoiceMode.FAST,
    onModeChange    = { mode -> /* handle mode change */ },
    onSettingsClick = { navController.navigate("settings") }
)
```

### Option B: Embedded Interactive Widget (`FluxVoiceView`)

Embed the reactive voice visualizer into your own screen layout (e.g. search bars, customer checkout, in-car dashboard).

```kotlin
FluxVoiceView(
    controller = controller,
    config = FluxVoiceViewConfig(
        size           = 220.dp,
        showTranscript = true,
        showBrandName  = false,
        colors = FluxVoiceColors(
            idle         = Color(0xFF64748B),
            listening    = Color(0xFF38BDF8),
            thinking     = Color(0xFFA855F7),
            speaking     = Color(0xFF34D399),
            interrupting = Color(0xFFF43F5E)
        )
    )
)
```

### Option C: Headless Architecture (Custom UI / ViewModels)

Use `fluxvoice-android` without any UI dependencies to drive your own custom designs from reactive `StateFlow`:

```kotlin
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    val controller = FluxVoiceEngine(
        config = FluxVoiceConfig {
            stt = DeepgramSttProvider(BuildConfig.DEEPGRAM_KEY)
            llm = OpenAiCompatibleLlmProvider(BuildConfig.GROQ_KEY)
            tts = CartesiaTtsProvider(BuildConfig.CARTESIA_KEY)
            systemPrompt = "You are Flux, an ultra-fast voice AI assistant. Speak with clarity and natural rhythm, delivering concise, high-signal spoken answers."
        },
        scope = viewModelScope
    )

    override fun onCleared() = controller.destroy()
}

@Composable
fun CustomScreen(viewModel: VoiceViewModel = viewModel()) {
    val state by viewModel.controller.state.collectAsStateWithLifecycle()

    when (state.voiceState) {
        VoiceState.IDLE         -> IdleButton(onClick = viewModel.controller::tap)
        VoiceState.LISTENING    -> LiveTranscriptView(text = state.partialTranscript)
        VoiceState.THINKING     -> PulsingThinkingIndicator()
        VoiceState.SPEAKING     -> SpeakingResponseView(text = state.aiResponse)
        VoiceState.INTERRUPTING -> StoppingIndicator()
    }
}
```

---

## 🌐 Universal BYOK: Connect Any Provider

FluxVoice is 100% vendor-independent. You can route to any AI provider or self-hosted endpoint:

### 1. Universal Language Models (`OpenAiCompatibleLlmProvider`)

One single provider powers any OpenAI-compatible completions endpoint:

```kotlin
// Groq (Ultra-fast LPUs)
llm = OpenAiCompatibleLlmProvider(
    apiKey  = BuildConfig.GROQ_KEY,
    modelId = "openai/gpt-oss-120b"
)

// Google Gemini 2.5 Flash
llm = OpenAiCompatibleLlmProvider(
    apiKey  = BuildConfig.GEMINI_KEY,
    modelId = "gemini-2.5-flash",
    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
)

// OpenAI
llm = OpenAiCompatibleLlmProvider(
    apiKey  = BuildConfig.OPENAI_KEY,
    modelId = "gpt-4o-mini",
    baseUrl = "https://api.openai.com/v1/chat/completions"
)

// OpenRouter (Access 300+ models)
llm = OpenAiCompatibleLlmProvider(
    apiKey  = BuildConfig.OPENROUTER_KEY,
    modelId = "meta-llama/llama-3.3-70b-instruct",
    baseUrl = "https://openrouter.ai/api/v1/chat/completions",
    customHeaders = mapOf("HTTP-Referer" to "https://my-app.com")
)

// Local Ollama / vLLM (Private & Keyless)
llm = OpenAiCompatibleLlmProvider(
    apiKey  = "",
    modelId = "llama3.2",
    baseUrl = "http://192.168.1.100:11434/v1/chat/completions"
)
```

### 2. Implement Pure Kotlin Provider Interfaces

Implement pure Kotlin interfaces from `fluxvoice-core` in ~30 lines of code:

```kotlin
// Custom STT (Whisper, Azure, On-Device Vosk)
class MyCustomSttProvider : SttProvider {
    override val state = MutableStateFlow<SttState>(SttState.Idle)
    override fun startListening() { /* open mic and stream */ }
    override fun stopListening()  { /* emit SttState.FinalResult */ }
    override fun destroy()        { /* cleanup resources */ }
}

// Custom TTS (ElevenLabs, Deepgram Aura, OpenAI TTS)
class MyCustomTtsProvider : TtsProvider {
    override val state = MutableStateFlow<TtsState>(TtsState.Idle)
    override fun speak(text: String, utteranceId: String) {
        _state.value = TtsState.Speaking
        myAudioEngine.play(text) { _state.value = TtsState.Idle }
    }
    override fun stop()     { myAudioEngine.stop(); _state.value = TtsState.Idle }
    override fun shutdown() { myAudioEngine.release() }
}
```

---

## ⚙️ Configuration Reference

```kotlin
val controller = rememberFluxVoice(mode) {
    // ── Providers ──────────────────────────────────────────────────────────
    stt = DeepgramSttProvider(apiKey = "...", model = "nova-3", endpointingMs = 500)
    llm = OpenAiCompatibleLlmProvider(apiKey = "...", modelId = "openai/gpt-oss-120b")
    tts = CartesiaTtsProvider(apiKey = "...", voiceId = CARTESIA_VOICE)

    // ── Pipeline Tuning ────────────────────────────────────────────────────
    systemPrompt       = "You are Flux, an intelligent voice AI assistant. Deliver high-signal, direct answers tailored for spoken audio without pleasantry filler, markdown, or lists."
    temperature        = 0.7f       // 0.0 to 2.0 (creativity)
    maxOutputTokens    = 2048       // Generation ceiling
    maxContextTurns    = 10         // Sliding memory window
    speechRate         = 1.0f       // 0.5 to 2.0 playback speed
    pitch              = 1.0f       // 0.5 to 2.0 playback pitch

    // ── Conversational Dynamics ────────────────────────────────────────────
    vadEnabled         = true       // Enable acoustic barge-in
    vadSensitivity     = 1000       // 200 to 3000 (lower = more sensitive)
    backchannelEnabled = false      // Speak filler phrase during slow queries
    backchannelDelayMs = 1500L      // Delay before filler word fires

    // ── Event Callbacks ────────────────────────────────────────────────────
    onTranscript  { text     -> /* Final user speech */ }
    onResponse    { text     -> /* Complete assistant response */ }
    onError       { error    -> /* Pipeline exception */ }
    onStateChange { from, to -> /* IDLE / LISTENING / THINKING / SPEAKING */ }
    onTurnMetrics { metrics  -> /* Measured millisecond latency & tokens */ }
}
```

---

## 📂 SDK Reference Examples

The [`/examples`](examples/) directory contains complete, standalone sample files:

| Example                                                              | Description                                                                  |
| -------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| [`01_FluxVoiceScreen.kt`](examples/01_FluxVoiceScreen.kt)           | Full-screen UI with mode switching, theme, and settings navigation           |
| [`02_FluxVoiceView_Widget.kt`](examples/02_FluxVoiceView_Widget.kt) | Embedded animated voice visualizer widget with custom dimensions and colors  |
| [`03_HeadlessEngine.kt`](examples/03_HeadlessEngine.kt)             | Headless `FluxVoiceEngine` in a ViewModel with real-time telemetry HUD     |
| [`04_NoTTS.kt`](examples/04_NoTTS.kt)                               | Zero API keys for TTS: on-device Android `TextToSpeech` fallback           |
| [`05_CustomProviders.kt`](examples/05_CustomProviders.kt)           | Custom `SttProvider`, `LlmProvider`, and `TtsProvider` implementations |
| [`06_AdvancedSettings.kt`](examples/06_AdvancedSettings.kt)         | Persistent settings screen with DataStore, navigation, and latency logging   |

---

## 🛠️ Run the Playground App

Clone the repository and run the full **FluxVoice Playground & Workbench** on an Android device:

```bash
git clone https://github.com/techrifter/fluxvoice.git
```

1. Open the project in Android Studio.
2. (Optional) Put your API keys in `local.properties`:

   ```properties
   DEEPGRAM_KEY=your_deepgram_key
   GROQ_KEY=your_groq_key
   CARTESIA_KEY=your_cartesia_key
   OPENAI_KEY=your_openai_key
   GEMINI_KEY=your_gemini_key
   ELEVENLABS_KEY=your_elevenlabs_key
   OPENROUTER_KEY=your_openrouter_key
   ```

   *(Or enter them directly inside the app's encrypted KeyVault settings).*
3. Hit **Run** to test live pipelines, benchmark latency, and export Kotlin SDK code!

---

## 📋 Requirements

- **Android SDK**: Min SDK 24+ (Android 7.0 Nougat) • Target SDK 35 (Android 15)
- **Kotlin**: 2.0+
- **Jetpack Compose**: Compose BOM 2024.10+ (for `fluxvoice-compose`)
- **Java**: JDK 17+

---

## 📄 License

FluxVoice is open-source software licensed under the [Apache License, Version 2.0](LICENSE.md).
