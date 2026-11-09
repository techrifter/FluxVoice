package com.techrifter.fluxvoice.domain.pipeline

import com.techrifter.fluxvoice.domain.model.PipelineConfig

object PipelineExporter {

    /**
     * Generates standard Kotlin code using FluxVoice SDK Compose API
     * matching the exact configuration of the given pipeline.
     */
    fun exportToKotlinSdk(config: PipelineConfig): String {
        val sttConfig = when (config.sttProviderId.lowercase()) {
            "deepgram" -> """
            stt = DeepgramSttProvider(
                apiKey = "YOUR_DEEPGRAM_KEY",
                model = ${kotlinStringLiteral(config.sttModelId.ifBlank { "nova-3" })},
                endpointingMs = ${config.sttEndpointingMs}
            )"""
            "groq", "groq_whisper" -> """
            stt = GroqWhisperSttProvider(
                apiKey = "YOUR_GROQ_KEY",
                model = "whisper-large-v3-turbo"
            )"""
            "custom" -> """
            stt = CustomWhisperSttProvider(
                baseUrl = ${kotlinStringLiteral(config.customEndpointUrl.ifBlank { "https://api.your-stt-provider.com/v1/audio/transcriptions" })},
                apiKey = "YOUR_CUSTOM_STT_KEY",
                model = ${kotlinStringLiteral(config.sttModelId.ifBlank { "whisper-1" })}
            )"""
            else -> """
            stt = WhisperSttProvider(
                apiKey = "YOUR_OPENAI_KEY"
            )"""
        }.trimIndent()

        val llmConfig = when (config.llmProviderId.lowercase()) {
            "gemini" -> """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_GEMINI_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId.ifBlank { "gemini-2.5-flash" })},
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
            )"""
            "openrouter" -> """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_OPENROUTER_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId)},
                baseUrl = "https://openrouter.ai/api/v1/chat/completions"
            )"""
            "groq" -> """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_GROQ_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId)},
                baseUrl = "https://api.groq.com/openai/v1/chat/completions"
            )"""
            "openai" -> """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_OPENAI_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId)},
                baseUrl = "https://api.openai.com/v1/chat/completions"
            )"""
            "custom" -> {
                val customUrl = config.customEndpointUrl.trim().ifBlank {
                    "https://api.your-provider.com/v1/chat/completions"
                }.let { url ->
                    if (!url.contains("/chat/completions")) {
                        if (url.endsWith("/")) "${url}chat/completions" else "$url/chat/completions"
                    } else url
                }
                """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_CUSTOM_API_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId)},
                baseUrl = ${kotlinStringLiteral(customUrl)}
            )"""
            }
            else -> """
            llm = OpenAiCompatibleLlmProvider(
                apiKey = "YOUR_API_KEY",
                modelId = ${kotlinStringLiteral(config.llmModelId)}
            )"""
        }.trimIndent()

        val ttsConfig = when (config.ttsProviderId.lowercase()) {
            "cartesia" -> """
            tts = CartesiaTtsProvider(
                apiKey = "YOUR_CARTESIA_KEY",
                voiceId = ${kotlinStringLiteral(config.ttsVoiceId)},
                speechRate = ${config.speechRate}f,
                pitch = ${config.pitch}f
            )"""
            "elevenlabs" -> """
            tts = ElevenLabsTtsProvider(
                apiKey = "YOUR_ELEVENLABS_KEY",
                voiceId = ${kotlinStringLiteral(config.ttsVoiceId)},
                modelId = "eleven_flash_v2_5"
            )"""
            "openai" -> """
            tts = OpenAiTtsProvider(
                apiKey = "YOUR_OPENAI_KEY",
                voice = ${kotlinStringLiteral(config.ttsVoiceId)},
                model = "tts-1"
            )"""
            "deepgram", "deepgram_aura" -> """
            tts = DeepgramAuraTtsProvider(
                apiKey = "YOUR_DEEPGRAM_KEY",
                model = ${kotlinStringLiteral(config.ttsVoiceId)}
            )"""
            "custom" -> """
            tts = CustomAudioSpeechTtsProvider(
                baseUrl = ${kotlinStringLiteral(config.customEndpointUrl.ifBlank { "https://api.your-tts-provider.com/v1" })},
                apiKey = "YOUR_CUSTOM_API_KEY",
                voice = ${kotlinStringLiteral(config.ttsVoiceId)}
            )"""
            else -> """
            tts = AndroidSystemTtsProvider(
                speechRate = ${config.speechRate}f,
                pitch = ${config.pitch}f
            )"""
        }.trimIndent()

        return """
// Generated by FluxVoice Playground for Android
// Pipeline: ${config.name.replace('\n', ' ').replace('\r', ' ')}

val fluxVoice = rememberFluxVoice {
    $sttConfig
    $llmConfig
    $ttsConfig
    systemPrompt = ${kotlinStringLiteral(config.systemPrompt)}
    temperature = ${config.temperature}f
    speechRate = ${config.speechRate}f
    pitch = ${config.pitch}f
    vadEnabled = ${config.vadEnabled}
    backchannelEnabled = ${config.backchannelEnabled}
    onTurnMetrics = { metrics ->
        Log.d("VoiceMetrics", "Latency: " + metrics.timeToFirstAudioMs + "ms")
    }
}
""".trimIndent()
    }

    private fun kotlinStringLiteral(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> {
                    append('\\')
                    append('$')
                }
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}
