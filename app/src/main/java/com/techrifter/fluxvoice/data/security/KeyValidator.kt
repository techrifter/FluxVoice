package com.techrifter.fluxvoice.data.security

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KeyValidator"

sealed class KeyValidationStatus {
    data object Idle : KeyValidationStatus()
    data object Checking : KeyValidationStatus()
    data class Valid(val message: String = "Active & verified") : KeyValidationStatus()
    data class Invalid(val reason: String) : KeyValidationStatus()
}

@Singleton
class KeyValidator @Inject constructor(
    private val httpClient: HttpClient
) {

    suspend fun validate(providerId: String, apiKey: String): KeyValidationStatus {
        val trimmed = apiKey.trim()
        if (trimmed.isBlank()) {
            return KeyValidationStatus.Invalid("API key cannot be empty")
        }

        return try {
            when (providerId.lowercase()) {
                "groq" -> validateGroq(trimmed)
                "openrouter" -> validateOpenRouter(trimmed)
                "openai" -> validateOpenAi(trimmed)
                "deepgram" -> validateDeepgram(trimmed)
                "cartesia" -> validateCartesia(trimmed)
                "elevenlabs" -> validateElevenLabs(trimmed)
                "gemini" -> validateGemini(trimmed)
                else -> KeyValidationStatus.Valid("Format recognized")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Validation check network error for $providerId: ${e.message}")
            KeyValidationStatus.Invalid("Connection error: ${e.message ?: "network unreachable"}")
        }
    }

    private suspend fun validateGroq(key: String): KeyValidationStatus {
        val response = httpClient.get("https://api.groq.com/openai/v1/models") {
            bearerAuth(key)
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("Groq cloud ready")
        } else {
            KeyValidationStatus.Invalid("Invalid Groq key (${response.status.value})")
        }
    }

    private suspend fun validateOpenRouter(key: String): KeyValidationStatus {
        val response = httpClient.get("https://openrouter.ai/api/v1/auth/key") {
            bearerAuth(key)
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("OpenRouter account verified")
        } else {
            KeyValidationStatus.Invalid("Invalid OpenRouter key (${response.status.value})")
        }
    }

    private suspend fun validateOpenAi(key: String): KeyValidationStatus {
        val response = httpClient.get("https://api.openai.com/v1/models") {
            bearerAuth(key)
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("OpenAI platform ready")
        } else {
            KeyValidationStatus.Invalid("Invalid OpenAI key (${response.status.value})")
        }
    }

    private suspend fun validateDeepgram(key: String): KeyValidationStatus {
        val response = httpClient.get("https://api.deepgram.com/v1/projects") {
            header("Authorization", "Token $key")
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("Deepgram project active")
        } else {
            KeyValidationStatus.Invalid("Invalid Deepgram token (${response.status.value})")
        }
    }

    private suspend fun validateCartesia(key: String): KeyValidationStatus {
        val response = httpClient.get("https://api.cartesia.ai/voices") {
            header("X-API-Key", key)
            header("Cartesia-Version", "2024-06-10")
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("Cartesia Sonic voice ready")
        } else {
            KeyValidationStatus.Invalid("Invalid Cartesia key (${response.status.value})")
        }
    }

    private suspend fun validateElevenLabs(key: String): KeyValidationStatus {
        val response = httpClient.get("https://api.elevenlabs.io/v1/voices") {
            header("xi-api-key", key)
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("ElevenLabs voices ready")
        } else {
            KeyValidationStatus.Invalid("Invalid ElevenLabs key (${response.status.value})")
        }
    }

    private suspend fun validateGemini(key: String): KeyValidationStatus {
        val response = httpClient.get("https://generativelanguage.googleapis.com/v1beta/models") {
            header("x-goog-api-key", key)
        }
        return if (response.status.isSuccess()) {
            KeyValidationStatus.Valid("Gemini API access confirmed")
        } else {
            KeyValidationStatus.Invalid("Invalid Gemini key (${response.status.value})")
        }
    }
}
