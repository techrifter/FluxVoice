<h1 align="center">FluxVoice</h1>

<p align="center">
  Real-time conversational AI for Android
</p>

<p align="center">
  Streaming STT → LLM → TTS with barge-in, interruption handling, and sentence-level voice orchestration.
</p>

<p align="center">
  <a href="https://central.sonatype.com/search?q=com.techrifter.fluxvoice">
    <img src="https://img.shields.io/maven-central/v/com.techrifter.fluxvoice/fluxvoice-core?color=blue" alt="Maven Central"/>
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-Apache%202.0-2563eb" alt="License"/>
  </a>
  <img src="https://img.shields.io/badge/API-24%2B-22c55e" alt="API 24+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin 2.0+"/>
</p>

<br/>

<table align="center">
<tr>
<td align="center" valign="middle">
<img src="app/assets/FluxVoice.webp" width="240" alt="FluxVoice logo"/>
</td>
<td width="40"></td>
<td align="center" valign="middle">
<img src="app/assets/FluxVoice_Demo.webp" width="260" alt="FluxVoice realtime demo"/>
</td>
</tr>
</table>

<p align="center">
  <sub>Tap to talk • Interrupt naturally • Stream responses in real time</sub>
</p>

---

## What is FluxVoice?

Building a real-time voice AI conversation system on Android from scratch means wiring together audio pipelines, streaming inference, interruption handling, and conversational state management before your app can do anything useful:

- `AudioRecord` at the correct PCM format, apply hardware AEC and noise suppression
- Stream raw PCM to convert speech to text
- Separate partial transcripts (live display) from final transcripts (LLM trigger)
- Streaming LLM request with a conversation history
- Splitting the LLM output *as it streams* and send each token to TTS immediately - before the model finishes generating
- Detect voice activity while TTS is playing so the user can interrupt mid-sentence (barge-in)
- Manage STT connection during the AI's turn so the next turn starts in near-zero latency
- Manage state machine (behaviors), retries, and errors

**FluxVoice is all of that.** You write none of it.

Tap once. FluxVoice handles the entire conversation loop - transcribes your speech in real time, streams it through an LLM, and speaks the response before the model has even finished generating. Say something mid-response and it stops, listens, and responds again.

```
Mic → STT → LLM (streaming) → TTS → Speaker
                ↑ barge-in via VAD
```

---

## Which module do I need?

There are three ways to integrate FluxVoice. Pick the one that fits your use case:

| I want to… | Use this | What you write |
|---|---|---|
| Drop a complete voice screen into my app | `fluxvoice-compose` | ~10 lines |
| Build my own screen, just need the voice engine | `fluxvoice-android` | Your own Compose/View UI |
| Build everything myself, just want the interfaces | `fluxvoice-core` | Your own engine + UI |

All three setups use the same provider modules (`fluxvoice-stt-deepgram`, `fluxvoice-provider-llm`, `fluxvoice-tts-cartesia`) which work identically regardless of which path you choose.

---

## Before you start

FluxVoice connects to external services - it doesn't replace them. The default setup uses three services, each with a free tier:

| Provider | Used for | Free tier |
|---|---|---|
| [Deepgram](https://console.deepgram.com) | Speech-to-text | $200 credit |
| [Groq](https://console.groq.com) | LLM (Llama 3) | Free API key |
| [Cartesia](https://cartesia.ai) | Text-to-speech | 20K characters |

You can swap any of them for your own implementation, even skip TTS entirely and use Android's built-in `TextToSpeech`.

---

## Quickstart

The fastest path: a complete, animated voice interaction layer in under 20 lines.

**1. Add dependencies**

```kotlin
// settings.gradle.kts - add JitPack (required for the WebRTC VAD library)
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
```

**2. Add permissions to `AndroidManifest.xml`**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`RECORD_AUDIO` runtime permission is requested automatically - you don't handle it.

**3. Drop the screen**

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
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
        sttProvider  = DeepgramSttProvider(apiKey = "YOUR_DEEPGRAM_KEY")
        llmProvider  = OpenAiCompatibleLlmProvider(apiKey = "YOUR_GROQ_KEY")
        ttsProvider  = CartesiaTtsProvider(apiKey = "YOUR_CARTESIA_KEY")
    }

    FluxVoiceScreen(
        controller      = controller,
        initialMode     = mode,
        onModeChange    = { mode = it },
        onSettingsClick = { /* navigate to your settings screen */ }
    )
}
```

That's it. You get a full screen with an animated orb, mode switching, live transcript bubbles, and error handling.

> **Storing API keys:** Add them to `local.properties` (never commit this file) and read them via `BuildConfig`:
> ```kotlin
> // app/build.gradle.kts
> android {
>     buildFeatures { buildConfig = true }
> }
> buildConfigField("String", "DEEPGRAM_KEY", "\"${properties["DEEPGRAM_KEY"]}\"")
> buildConfigField("String", "GROQ_KEY",     "\"${properties["GROQ_KEY"]}\"")
> buildConfigField("String", "CARTESIA_KEY", "\"${properties["CARTESIA_KEY"]}\"")
> ```
> ```kotlin
> sttProvider = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_KEY)
> ```

---

## Modes

`FluxVoiceMode` controls the mode badge in the header and ships `systemPrompt` for each personality. When the user switches modes in the UI, pass the new mode back to `rememberFluxVoice` as a key - the engine recreates it automatically.

```kotlin
var mode by remember { mutableStateOf(FluxVoiceMode.FAST) }

val controller = rememberFluxVoice(mode) {   // ← mode as key: engine recreates on change
    systemPrompt = mode.defaultPrompt        
    sttProvider  = DeepgramSttProvider(...)
    llmProvider  = OpenAiCompatibleLlmProvider(...)
    ttsProvider  = CartesiaTtsProvider(...)
}

FluxVoiceScreen(
    controller   = controller,
    initialMode  = mode,
    onModeChange = { mode = it }             // ← called when user taps the badge
)
```

| Mode | Emoji | Built-in system prompt behaviour |
|---|---|---|
| `FluxVoiceMode.FAST` | ⚡ | One-sentence answers, no preamble |
| `FluxVoiceMode.THINKING` | 🧠 | Careful reasoning, structured but conversational |
| `FluxVoiceMode.CUSTOM` | ✨ | Custom assistant - override `systemPrompt` with your own |

<table>
  <tr>
    <td align="center"><b>Realtime Mode (ultra-low latency)</b></td>
    <td align="center"><b>Reasoning Mode (backchannel on)</b></td>
  </tr>
  <tr>
    <td><img src="app/assets/FastMode.webp" width="240" alt="Realtime Mode (ultra-low latency)"/></td>
    <td><img src="app/assets/ThinkingMode.webp" width="240" alt="Reasoning Mode (backchannel on)"/></td>
  </tr>
  <tr>
    <td align="center"><b>Modular Pipeline</b></td>
    <td align="center"><b>Voice Interaction Tuning  </b></td>
  </tr>
  <tr>
    <td><img src="app/assets/CustomMode.webp" width="240" alt="Custom mode"/></td>
    <td><img src="app/assets/CustomSettings.webp" width="240" alt="Settings screen"/></td>
  </tr>
</table>

---

## Configuration

All options go in the `rememberFluxVoice { }` block (or `FluxVoiceConfig { }` for non-Compose usage).

```kotlin
val controller = rememberFluxVoice(mode) {
    sttProvider = DeepgramSttProvider(
        apiKey = BuildConfig.DEEPGRAM_KEY,
        model  = "nova-3"
    )
    llmProvider = OpenAiCompatibleLlmProvider(
        apiKey  = BuildConfig.GROQ_KEY,
        modelId = "llama-3.3-70b-versatile"
    )
    ttsProvider = CartesiaTtsProvider(
        apiKey  = BuildConfig.CARTESIA_KEY,
        voiceId = CARTESIA_VOICE,
        modelId = "sonic-3"
    )

    systemPrompt        = "You are a helpful voice assistant. Keep responses concise and conversational."
    maxContextTurns     = 6
    temperature         = 0.7f
    maxOutputTokens     = 1024
    vadEnabled          = true
    vadSensitivity      = 800
    backchannelEnabled  = true
    backchannelDelayMs  = 1500

    onTranscript  { text     -> Log.d("FluxVoice", "User: $text") }
    onResponse    { response -> Log.d("FluxVoice", "AI: $response") }
    onError       { error    -> Crashlytics.recordException(error) }
    onStateChange { from, to -> analytics.track("voice_state", "$from→$to") }
}
```

### Options

| Option | Type | Default | Description |
|---|---|---|---|
| `sttProvider` | `SttProvider?` | `null` | Speech recognition provider |
| `llmProvider` | `LlmProvider?` | `null` | Language model provider |
| `ttsProvider` | `TtsProvider?` | `null` | Text-to-speech provider. Omit to use callbacks only |
| `systemPrompt` | `String` | `"You are a helpful voice assistant..."` | System message prepended to every LLM request |
| `maxContextTurns` | `Int` | `10` | Conversation turns kept in context. Oldest are dropped when full |
| `temperature` | `Float` | `0.7` | LLM sampling temperature (0.0–2.0). Lower = more focused, higher = more creative |
| `maxOutputTokens` | `Int` | `2048` | Maximum tokens the LLM may generate per turn |
| `vadEnabled` | `Boolean` | `true` | Auto-interrupt TTS when the user speaks |
| `vadSensitivity` | `Int` | `1000` | VAD threshold (200–3000). Lower = more sensitive |
| `backchannelEnabled` | `Boolean` | `false` | Speak a short filler ("Got it.", "Sure.") while the LLM warms up |
| `backchannelDelayMs` | `Long` | `1500` | Milliseconds to wait before triggering the backchannel filler |

### Callbacks

| Callback | When it fires |
|---|---|
| `onTranscript { text }` | User's final transcript is ready |
| `onResponse { response }` | Full AI response once the turn completes |
| `onError { error }` | Any pipeline error (network, API, audio) |
| `onStateChange { from, to }` | Every `VoiceState` transition |

---

## `FluxVoiceScreen`

`FluxVoiceScreen` is the complete, ready-to-ship voice experience. It fills the screen and provides:

- Dark gradient background that shifts colour with voice state
- Header row: ⚙ settings icon (optional), mode badge dropdown, 🗑 clear button
- Empty-state feature and a "Configure your AI" card (shown only when `onSettingsClick` is provided)
- Live AI response bubble and user transcript bubble
- Animated orb (280 dp)
- State label ("Listening to you", "Thinking…", etc.)
- Error banner with dismiss

```kotlin
FluxVoiceScreen(
    controller      = controller,          // from rememberFluxVoice { }
    initialMode     = mode,
    onModeChange    = { mode = it },       // called when user switches mode
    onSettingsClick = { navController.navigate("settings") }  // omit to hide the ⚙ icon
)
```

### `FluxVoiceScreen` parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `controller` | `FluxVoiceController` | required | Engine instance from `rememberFluxVoice` |
| `modifier` | `Modifier` | `Modifier` | Applied to the root `Box` |
| `initialMode` | `FluxVoiceMode` | `FluxVoiceMode.FAST` | Starting mode badge |
| `onSettingsClick` | `(() -> Unit)?` | `null` | When provided, shows ⚙ icon in header |
| `onModeChange` | `((FluxVoiceMode) -> Unit)?` | `null` | Called when user switches mode via badge |

---

## Widget - `FluxVoiceView`

`FluxVoiceView` is a self-contained orb widget - use it when you want to embed the voice experience inside your own existing screen layout rather than replacing the full screen.

```kotlin
@Composable
fun MyScreen() {
    val controller = rememberFluxVoice {
        sttProvider = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_KEY)
        llmProvider = OpenAiCompatibleLlmProvider(apiKey = BuildConfig.GROQ_KEY)
        ttsProvider = CartesiaTtsProvider(apiKey = BuildConfig.CARTESIA_KEY)
    }

    Column {
        // ... your own UI above
        FluxVoiceView(
            controller = controller,
            config = FluxVoiceViewConfig(
                size           = 200.dp,
                showTranscript = true,
                showBrandName  = false,
                showStateLabel = true,
                showHintLabel  = true,
                colors = FluxVoiceColors(
                    idle         = Color(0xFF64748B),
                    listening    = Color(0xFF3B82F6),
                    thinking     = Color(0xFF8B5CF6),
                    speaking     = Color(0xFF10B981),
                    interrupting = Color(0xFFEF4444)
                )
            )
        )
        // ... your own UI below
    }
}
```

`FluxVoiceView` has no background of its own - it inherits whatever is behind it, so it works on any coloured or transparent background.

### `FluxVoiceViewConfig`

| Field | Type | Default |
|---|---|---|
| `size` | `Dp` | `240.dp` |
| `showTranscript` | `Boolean` | `true` |
| `showBrandName` | `Boolean` | `true` |
| `showStateLabel` | `Boolean` | `true` |
| `showHintLabel` | `Boolean` | `true` |
| `colors` | `FluxVoiceColors` | see below |

### `FluxVoiceColors` defaults

| State | Color | Hex |
|---|---|---|
| `idle` | Slate 500 | `#64748B` |
| `listening` | Blue 500 | `#3B82F6` |
| `thinking` | Violet 500 | `#8B5CF6` |
| `speaking` | Emerald 500 | `#10B981` |
| `interrupting` | Red 500 | `#EF4444` |

---

## Headless mode

Use `fluxvoice-android` without `fluxvoice-compose` to drive your own UI entirely from the state flow. No Compose dependency pulled in.

```kotlin
// app/build.gradle.kts
implementation("com.techrifter.fluxvoice:fluxvoice-android:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
```

```kotlin
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    val controller = FluxVoiceEngine(
        config = FluxVoiceConfig {
            sttProvider  = DeepgramSttProvider(BuildConfig.DEEPGRAM_KEY)
            llmProvider  = OpenAiCompatibleLlmProvider(BuildConfig.GROQ_KEY)
            ttsProvider  = CartesiaTtsProvider(BuildConfig.CARTESIA_KEY)
            systemPrompt = "You are a concise voice assistant."
        },
        scope = viewModelScope
    )

    override fun onCleared() = controller.destroy()
}

@Composable
fun VoiceScreen(viewModel: VoiceViewModel = viewModel()) {
    val state by viewModel.controller.state.collectAsStateWithLifecycle()

    when (state.voiceState) {
        VoiceState.IDLE         -> IdleButton { viewModel.controller.tap() }
        VoiceState.LISTENING    -> ListeningView(state.partialTranscript)
        VoiceState.THINKING     -> ThinkingView()
        VoiceState.SPEAKING     -> SpeakingView(state.aiResponse)
        VoiceState.INTERRUPTING -> InterruptingView()
    }
    state.errorMessage?.let { msg ->
        ErrorBanner(msg) { viewModel.controller.dismissError() }
    }
}
```

### `FluxVoiceState` fields

| Field | Type | Description |
|---|---|---|
| `voiceState` | `VoiceState` | Current pipeline state |
| `partialTranscript` | `String` | In-progress STT text, updated continuously while listening |
| `transcript` | `String` | Final STT result for the completed turn |
| `aiResponse` | `String` | Accumulated LLM response for the current or last turn |
| `errorMessage` | `String?` | Non-null when a surfaced error is present |

### `FluxVoiceController` methods

| Method | Description |
|---|---|
| `tap()` | Context-sensitive - see state table below |
| `clear()` | Cancel current turn and reset to IDLE |
| `dismissError()` | Clear the error message |
| `destroy()` | Release all resources (called automatically by `rememberFluxVoice`) |

### `tap()` behaviour by state

| State | What `tap()` does |
|---|---|
| `IDLE` | Opens mic, begins listening |
| `LISTENING` | Flushes transcript, sends to LLM immediately |
| `THINKING` | Cancels LLM request, returns to IDLE |
| `SPEAKING` | Stops TTS, cancels stream, reopens mic (barge-in) |
| `INTERRUPTING` | No-op |

The mic times out after **7 seconds** of silence and returns to IDLE automatically.

---

## No TTS - use Android's built-in (Fallback)

If `ttsProvider` is left unset. The pipeline completes the STT → LLM path and delivers the response via `onResponse`. 

```kotlin
val controller = rememberFluxVoice {
    sttProvider = DeepgramSttProvider(apiKey = BuildConfig.DEEPGRAM_KEY)
    llmProvider = OpenAiCompatibleLlmProvider(apiKey = BuildConfig.GROQ_KEY)
    // no ttsProvider
    onResponse { response ->
        tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
```

---

## Providers

### STT - Deepgram

```kotlin
DeepgramSttProvider(
    apiKey = BuildConfig.DEEPGRAM_KEY,
    model  = "nova-3"   // default
)
```

Streams live linear16 PCM audio (16 kHz mono) over a WebSocket. Partial transcripts arrive continuously for live display; a final result fires when Deepgram detects an utterance boundary. The socket pre-warms between turns so the next turn starts with a live connection rather than a new TLS handshake. Hardware AEC and noise suppression are applied to the mic feed before any audio leaves the device.

Get a key at [console.deepgram.com](https://console.deepgram.com).

### LLM - Groq

```kotlin
OpenAiCompatibleLlmProvider(
    apiKey  = BuildConfig.GROQ_KEY,
    modelId = "llama-3.3-70b-versatile"   // default
)
```

| Model | Best for |
|---|---|
| `llama-3.3-70b-versatile` | Best quality, still fast |
| `llama-3.1-8b-instant` | Lowest latency |

`temperature` and `maxOutputTokens` are set via `FluxVoiceConfig` (not the provider constructor). Transient errors retry up to 2 times with 600 ms backoff before surfacing to `onError`.

Get a key at [console.groq.com](https://console.groq.com).

### TTS - Cartesia

```kotlin
CartesiaTtsProvider(
    apiKey  = BuildConfig.CARTESIA_KEY,
    voiceId = CARTESIA_VOICE,   // named constant - a natural conversational voice
    modelId = "sonic-3"         // default
)
```

Each sentence synthesizes as soon as it is extracted from the LLM stream - speech starts before the model finishes generating. `CARTESIA_VOICE` is a constant included in the library. Substitute any voice ID from your [Cartesia dashboard](https://play.cartesia.ai).


Get a key at [cartesia.ai](https://cartesia.ai).

---

## Custom providers

Implement any of the three interfaces from `fluxvoice-core` and pass the instance into the config. Mix and match - use your own LLM with Deepgram STT and Cartesia TTS, or build all three yourself.

```kotlin
// app/build.gradle.kts - interfaces only
implementation("com.techrifter.fluxvoice:fluxvoice-core:1.0.0")
```

### Custom LLM

```kotlin
class MyLlmProvider : LlmProvider {
    override fun streamChat(
        messages: List<Message>,
        config: GenerationConfig
    ): Flow<StreamEvent> = flow {
        emit(StreamEvent.Start)
        try {
            myApiClient.streamCompletion(messages).collect { token ->
                emit(StreamEvent.Token(token))
            }
            emit(StreamEvent.Done)
        } catch (e: Exception) {
            emit(StreamEvent.Error(e))
        }
    }
}
```

### Custom STT

```kotlin
class MySttProvider : SttProvider {

    private val _state = MutableStateFlow<SttState>(SttState.Idle)
    override val state: StateFlow<SttState> = _state.asStateFlow()

    override fun startListening() {
        _state.value = SttState.Listening
        // open your audio stream / WebSocket
    }

    override fun stopListening() {
        // emit partial results via SttState.PartialResult(text) while speaking
        _state.value = SttState.FinalResult("transcribed text")
    }

    override fun destroy() {
        _state.value = SttState.Idle
    }
}
```

### Custom TTS

```kotlin
class MyTtsProvider : TtsProvider {

    private val _state = MutableStateFlow<TtsState>(TtsState.Idle)
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    override fun speak(text: String, utteranceId: String) {
        _state.value = TtsState.Speaking
        // synthesize and play `text`
        // when playback finishes: _state.value = TtsState.Idle
    }

    override fun stop() {
        // stop playback immediately
        _state.value = TtsState.Idle
    }

    override fun shutdown() { /* release all resources */ }
}
```

> The engine calls `speak()` once per sentence as the LLM streams. Your implementation manages its own playback queue. The engine observes `TtsState.Idle` to know when the turn is done and it is safe to open the mic for the next turn.

---

## How it works

FluxVoice is built on asynchronous streaming pipelines. Each stage runs concurrently - TTS plays while the LLM is still generating, and the STT socket pre-warms while the AI is speaking to minimise turn latency. Every stage communicates through `StateFlow`, so the engine reacts to state changes rather than polling.

### 1. Audio capture

The mic opens at **16 kHz, mono, 16-bit PCM** - the format Deepgram's streaming endpoint expects natively. Raw PCM bytes are read from `AudioRecord` in buffered chunks and forwarded to the STT provider over a WebSocket connection. Hardware **acoustic echo cancellation (AEC)** and **noise suppression** are applied at the `AudioRecord` level before any audio leaves the device, which is why barge-in works cleanly even with loud speaker playback.

### 2. Speech-to-text

Deepgram's WebSocket receives the raw PCM stream and returns two types of results:

- **Partial results** - transcribed as you speak, continuously. Used to update the live transcript in the UI.
- **Final result** - emitted when Deepgram detects an utterance boundary (500 ms endpointing by default). This fires the LLM request.

**Socket pre-warming** - as soon as a final transcript arrives, `preConnect()` is called in the background. This opens and authenticates a fresh WebSocket connection while the AI is thinking and speaking, so the *next* turn's `startListening()` connects in near-zero time rather than negotiating a new TLS handshake mid-conversation.

### 3. Language model

The final transcript is appended to the conversation history and dispatched to the LLM as a streaming request (`Flow<StreamEvent>`). Three event types flow through:

- `StreamEvent.Start` - connection established, state moves to THINKING
- `StreamEvent.Token` - a text chunk arrives; accumulated into a rolling buffer and displayed in real time
- `StreamEvent.Done` - generation complete; the full response is saved to conversation history

**Adaptive length hints** - a word-count suffix is appended to the system prompt at call time: queries of ≤ 4 words get `"Respond in 1 sentence."`, ≤ 10 words get `"Respond in 1–2 sentences."`. Longer queries let the model decide. This keeps conversational exchanges snappy without over-constraining complex questions.

**Retries** - transient LLM errors (network drops, 5xx) are retried up to 2 times with 600 ms × attempt backoff before surfacing to `onError`.

**Conversation history** - managed as a sliding window of `maxContextTurns × 2` messages (user + assistant pairs). Oldest turns are dropped when the window is full.

### 4. Sentence-level TTS dispatch

The token buffer is scanned by `SentenceExtractor` on every incoming token. As soon as a sentence boundary is detected - a period, question mark, or exclamation mark followed by whitespace and a capital letter - that sentence is dispatched to the TTS provider immediately, without waiting for the rest of the response. A negative lookbehind prevents numeric sequences like `"1."` from triggering a false split.

This is why speech starts before the LLM finishes: the first sentence is synthesising while tokens 2–N are still being generated. Back-to-back sentences queue and play with no gap between them.

### 5. Voice activity detection (barge-in)

The moment TTS starts playing, a **WebRTC VAD** instance starts reading the same mic feed. WebRTC VAD classifies 10 ms frames as speech or non-speech based on energy and spectral features. When it detects speech above the configured threshold, it fires barge-in:

1. The backchannel job is cancelled
2. The LLM stream job is cancelled
3. TTS is stopped immediately
4. A 300 ms echo-decay window lets the speaker audio dissipate
5. The mic reopens and a new STT turn begins

**VAD threshold** (`vadSensitivity` 200–3000) maps to WebRTC aggressiveness:
- `≤ 600` - Normal (permissive, quick trigger)
- `≤ 1500` - Aggressive (default)
- `> 1500` - Very Aggressive (strict, better for noisy environments)

### 6. Backchannels

When `backchannelEnabled` is true, a coroutine waits `backchannelDelayMs` after the LLM request is sent. If the first token hasn't arrived by then, a random filler ("Got it.", "Sure.", "Mm-hmm.", etc.) is spoken via TTS to mask the latency. The job is cancelled immediately on `StreamEvent.Token`, so fast providers (Groq typically responds in < 500 ms) never trigger an unnecessary filler.

### 7. Turn completion

When `StreamEvent.Done` is received and the TTS provider emits `TtsState.Idle` (playback finished), the engine returns to IDLE, calls `preConnect()` again, and after a 600 ms buffer reopens the mic automatically - creating a continuous hands-free conversation loop.

If TTS is disabled, turn completion fires immediately on `StreamEvent.Done` without waiting for audio playback.

---

## Modules

Pick only what you need - every module is independently published to Maven Central.

| Artifact | What it contains |
|---|---|
| `fluxvoice-core` | `LlmProvider`, `SttProvider`, `TtsProvider` interfaces; `FluxVoiceConfig`, `FluxVoiceController`, `FluxVoiceState`, `VoiceState`, `StreamEvent`, `Message`, `GenerationConfig` |
| `fluxvoice-android` | `FluxVoiceEngine` - the full pipeline orchestrator with VAD, audio capture, sentence extraction, conversation history, retries |
| `fluxvoice-compose` | `FluxVoiceScreen`, `FluxVoiceView`, `rememberFluxVoice`, `FluxVoiceMode`, `FluxVoiceViewConfig`, `FluxVoiceColors` |
| `fluxvoice-stt-deepgram` | `DeepgramSttProvider` - Deepgram real-time transcription via WebSocket |
| `fluxvoice-provider-llm` | `OpenAiCompatibleLlmProvider` - OpenAI-compatible chat completions via SSE streaming (Groq, OpenAI, Ollama, etc.) |
| `fluxvoice-tts-cartesia` | `CartesiaTtsProvider` - Cartesia Sonic synthesis; `CARTESIA_VOICE` constant |

**Dependency chain:** `fluxvoice-compose` → `fluxvoice-android` → `fluxvoice-core`. Adding `fluxvoice-compose` transitively pulls in the other two - you don't need to add them separately. The three provider modules each depend only on `fluxvoice-core` and are independent of each other.

### Common setups

```kotlin
// Full-screen UI with Compose (recommended)
implementation("com.techrifter.fluxvoice:fluxvoice-compose:1.0.0")

// Custom UI - no Compose dependency
implementation("com.techrifter.fluxvoice:fluxvoice-android:1.0.0")

// Interfaces only - bring your own engine and providers
implementation("com.techrifter.fluxvoice:fluxvoice-core:1.0.0")

// Providers - add whichever you need (work with any of the above)
implementation("com.techrifter.fluxvoice:fluxvoice-stt-deepgram:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-provider-llm:1.0.0")
implementation("com.techrifter.fluxvoice:fluxvoice-tts-cartesia:1.0.0")
```

---

## Examples

The [`examples/`](examples/) directory provides standalone integration references for common FluxVoice usage pattern.

---

## Try the FluxVoice app

A fully working demo app is included in the `/app` directory. Clone it, add your API keys to `local.properties`, and run it on a device.

```bash
git clone https://github.com/techrifter/fluxvoice.git
```

```
# local.properties
DEEPGRAM_KEY=your_key_here
GROQ_KEY=your_key_here
CARTESIA_KEY=your_key_here
```

The app demonstrates all three conversation modes and a full settings screen with provider selection.

---

## Requirements

- Android API 24+ (Android 7.0)
- Kotlin 2.0+
- Jetpack Compose (only if using `fluxvoice-compose`)
- JitPack in your `dependencyResolutionManagement` repositories (required by the WebRTC VAD library used internally by `fluxvoice-android`)

---

Apache License, [Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)
