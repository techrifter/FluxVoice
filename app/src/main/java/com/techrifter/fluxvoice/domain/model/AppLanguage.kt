package com.techrifter.fluxvoice.domain.model

/**
 * Supported global & regional languages with verified STT, LLM, and TTS support.
 */
enum class AppLanguage(
    val code: String,
    val displayName: String,
    val systemPromptInstruction: String = ""
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        systemPromptInstruction = ""
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi (हिन्दी)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Hindi (हिन्दी) script. Keep answers natural, direct, and concise (1 to 2 sentences)."
    ),
    BENGALI(
        code = "bn",
        displayName = "Bengali (বাংলা)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Bengali (বাংলা) script. Keep answers concise."
    ),
    TELUGU(
        code = "te",
        displayName = "Telugu (తెలుగు)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Telugu (తెలుగు) script. Keep answers concise."
    ),
    MARATHI(
        code = "mr",
        displayName = "Marathi (मराठी)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Marathi (मराठी) script. Keep answers concise."
    ),
    TAMIL(
        code = "ta",
        displayName = "Tamil (தமிழ்)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Tamil (தமிழ்) script. Keep answers concise."
    ),
    URDU(
        code = "ur",
        displayName = "Urdu (اردو)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Urdu (اردو) script. Keep answers concise."
    ),
    GUJARATI(
        code = "gu",
        displayName = "Gujarati (ગુજરાતી)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Gujarati (ગુજરાતી) script. Keep answers concise."
    ),
    FRENCH(
        code = "fr",
        displayName = "French (Français)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in French (Français). Keep answers natural, direct, and concise (1 to 2 sentences)."
    ),
    SPANISH(
        code = "es",
        displayName = "Spanish (Español)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in Spanish (Español). Keep answers natural, direct, and concise (1 to 2 sentences)."
    ),
    GERMAN(
        code = "de",
        displayName = "German (Deutsch)",
        systemPromptInstruction = "\n\nCRITICAL: Always speak and respond in German (Deutsch). Keep answers natural, direct, and concise (1 to 2 sentences)."
    );

    companion object {
        fun fromCode(code: String): AppLanguage = entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH

        /**
         * Returns list of supported languages for the given STT & TTS provider combination.
         */
        fun getSupportedLanguages(sttProviderId: String, ttsProviderId: String = ""): List<AppLanguage> {
            val stt = sttProviderId.lowercase()
            val tts = ttsProviderId.lowercase()
            return entries.filter { lang ->
                val sttSupported = when (stt) {
                    "deepgram" -> lang.code in setOf("en", "hi", "es", "fr", "de")
                    else -> true
                }
                val ttsSupported = when (tts) {
                    "deepgram", "deepgram_aura" -> lang.code == "en"
                    else -> true
                }
                sttSupported && ttsSupported
            }
        }
    }
}
