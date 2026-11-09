package com.techrifter.fluxvoice.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware-backed secure storage contract for commercial cloud API keys.
 * All credentials are encrypted on-device via Android Keystore (AES-256 GCM)
 * and never sent to any intermediary server.
 */
interface KeyVault {
    fun getKey(providerId: String): String
    fun setKey(providerId: String, key: String)
    fun hasKey(providerId: String): Boolean
    fun removeKey(providerId: String)
    fun getAllConfiguredProviders(): Set<String>

    fun getGroqKey(): String = getKey("groq")
    fun setGroqKey(key: String) = setKey("groq", key)

    fun getOpenRouterKey(): String = getKey("openrouter")
    fun setOpenRouterKey(key: String) = setKey("openrouter", key)

    fun getOpenAiKey(): String = getKey("openai")
    fun setOpenAiKey(key: String) = setKey("openai", key)

    fun getDeepgramKey(): String = getKey("deepgram")
    fun setDeepgramKey(key: String) = setKey("deepgram", key)

    fun getCartesiaKey(): String = getKey("cartesia")
    fun setCartesiaKey(key: String) = setKey("cartesia", key)

    fun getElevenLabsKey(): String = getKey("elevenlabs")
    fun setElevenLabsKey(key: String) = setKey("elevenlabs", key)

    fun getGeminiKey(): String = getKey("gemini")
    fun setGeminiKey(key: String) = setKey("gemini", key)

    fun getCustomSttUrl(): String = getKey("custom_stt_url")
    fun setCustomSttUrl(url: String) = setKey("custom_stt_url", url)

    fun getCustomSttKey(): String = getKey("custom_stt_key")
    fun setCustomSttKey(key: String) = setKey("custom_stt_key", key)
}

@Singleton
class EncryptedKeyVault @Inject constructor(
    @param:ApplicationContext private val context: Context
) : KeyVault {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "fluxvoice_secure_vault",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun getKey(providerId: String): String {
        return encryptedPrefs.getString("api_key_${providerId.lowercase()}", "") ?: ""
    }

    override fun setKey(providerId: String, key: String) {
        encryptedPrefs.edit()
            .putString("api_key_${providerId.lowercase()}", key.trim())
            .apply()
    }

    override fun hasKey(providerId: String): Boolean {
        return getKey(providerId).isNotBlank()
    }

    override fun removeKey(providerId: String) {
        encryptedPrefs.edit()
            .remove("api_key_${providerId.lowercase()}")
            .apply()
    }

    override fun getAllConfiguredProviders(): Set<String> {
        return encryptedPrefs.all.keys
            .filter { it.startsWith("api_key_") }
            .map { it.removePrefix("api_key_") }
            .filter { hasKey(it) }
            .toSet()
    }
}

