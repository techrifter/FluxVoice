package com.techrifter.fluxvoice.data.local.db

object DefaultPipelines {

    const val CUSTOM_PIPELINE_ID = "custom_user_pipeline"
    const val DEFAULT_CUSTOM_NAME = "Personal Custom Stack"
    const val DEFAULT_CUSTOM_DESC = "Tailored conversational voice stack with custom STT, LLM, and TTS providers."

    val ULTRA_FAST = PipelineEntity(
        id = "preset_ultra_fast",
        name = "Ultra-Fast Cloud",
        description = "High-speed conversational cadence with Groq + Deepgram + Cartesia",
        isPreset = true,
        sttProviderId = "deepgram",
        sttModelId = "nova-3",
        sttEndpointingMs = 400,
        llmProviderId = "groq",
        llmModelId = "openai/gpt-oss-120b",
        customEndpointUrl = "",
        temperature = 0.6f,
        maxOutputTokens = 1024,
        systemPrompt = "You are Flux Fast, an ultra-low-latency voice assistant. Deliver immediate, high-signal answers in 1 to 2 punchy sentences. Never use preamble, pleasantries, or filler.",
        ttsProviderId = "cartesia",
        ttsVoiceId = "638efaaa-4d0c-442e-b701-3fae16aad012",
        speechRate = 1.0f,
        pitch = 1.0f,
        vadEnabled = true,
        vadSensitivity = 850,
        backchannelEnabled = false,
        backchannelDelayMs = 1500L,
        createdAt = 1000L
    )

    val REASONING = PipelineEntity(
        id = "preset_reasoning",
        name = "Deep Reasoning",
        description = "Complex thought and structured answers via Google Gemini 2.5 Flash",
        isPreset = true,
        sttProviderId = "deepgram",
        sttModelId = "nova-3",
        sttEndpointingMs = 600,
        llmProviderId = "gemini",
        llmModelId = "gemini-2.5-flash",
        customEndpointUrl = "",
        temperature = 0.5f,
        maxOutputTokens = 2048,
        systemPrompt = "You are Flux Reason, an expert voice advisor for deep reasoning and system architecture. Analyze questions with first-principles depth, then articulate your answer in clear, flowing spoken paragraphs without bullet points or outlines.",
        ttsProviderId = "cartesia",
        ttsVoiceId = "638efaaa-4d0c-442e-b701-3fae16aad012",
        speechRate = 1.0f,
        pitch = 1.0f,
        vadEnabled = true,
        vadSensitivity = 1200,
        backchannelEnabled = true,
        backchannelDelayMs = 1400L,
        createdAt = 2000L
    )

    val NATURAL_VOICE = PipelineEntity(
        id = "preset_natural_voice",
        name = "Natural Voice",
        description = "Hyper-natural speech inflection using OpenAI GPT-4o-mini and ElevenLabs Voice",
        isPreset = true,
        sttProviderId = "deepgram",
        sttModelId = "nova-3",
        sttEndpointingMs = 500,
        llmProviderId = "openai",
        llmModelId = "gpt-4o-mini",
        customEndpointUrl = "",
        temperature = 0.7f,
        maxOutputTokens = 1024,
        systemPrompt = "You are Flux Voice, an expressive, highly natural conversational partner. Speak with engaging vocal cadence, warmth, and human-like flow. Keep responses focused, using natural pauses.",
        ttsProviderId = "elevenlabs",
        ttsVoiceId = "JBFqnCBsd6RMkjVDRZzb",
        speechRate = 1.0f,
        pitch = 1.0f,
        vadEnabled = true,
        vadSensitivity = 1000,
        backchannelEnabled = false,
        backchannelDelayMs = 1500L,
        createdAt = 3000L
    )

    val BUDGET = PipelineEntity(
        id = "preset_budget",
        name = "Budget Free Tier",
        description = "Lowest cost stack. Free Groq Whisper + Qwen 3.8 + Android system voice.",
        isPreset = true,
        sttProviderId = "groq_whisper",
        sttModelId = "whisper-large-v3-turbo",
        sttEndpointingMs = 500,
        llmProviderId = "groq",
        llmModelId = "qwen/qwen3.8-27b",
        customEndpointUrl = "",
        temperature = 0.7f,
        maxOutputTokens = 1024,
        systemPrompt = "You are Flux Direct, a fast, practical voice assistant. Provide direct, helpful answers in clear spoken language without filler or preamble.",
        ttsProviderId = "android",
        ttsVoiceId = "default",
        speechRate = 1.0f,
        pitch = 1.0f,
        vadEnabled = true,
        vadSensitivity = 1000,
        backchannelEnabled = false,
        backchannelDelayMs = 1500L,
        createdAt = 4000L
    )

    val DEFAULT_CUSTOM = PipelineEntity(
        id = CUSTOM_PIPELINE_ID,
        name = DEFAULT_CUSTOM_NAME,
        description = DEFAULT_CUSTOM_DESC,
        isPreset = false,
        sttProviderId = "deepgram",
        sttModelId = "nova-3",
        sttEndpointingMs = 400,
        llmProviderId = "groq",
        llmModelId = "",
        customEndpointUrl = "",
        temperature = 0.7f,
        maxOutputTokens = 1024,
        systemPrompt = "You are Flux, an intelligent voice AI assistant for real-time conversation. Deliver high-signal, direct answers optimized for spoken audio without markdown, lists, or filler.",
        ttsProviderId = "cartesia",
        ttsVoiceId = "",
        speechRate = 1.0f,
        pitch = 1.0f,
        vadEnabled = true,
        vadSensitivity = 850,
        backchannelEnabled = false,
        backchannelDelayMs = 1500L,
        createdAt = 5000L
    )

    val PRESETS = listOf(ULTRA_FAST, REASONING, NATURAL_VOICE, BUDGET)
}
