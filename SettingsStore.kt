package ru.minutes.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AiSettings(
    // Расшифровка: любой OpenAI-совместимый эндпоинт /audio/transcriptions
    val sttBaseUrl: String = "https://api.openai.com/v1",
    val sttKey: String = "",
    val sttModel: String = "whisper-1",
    // Протокол: "openai" (chat/completions) или "anthropic" (messages)
    val llmProvider: String = PROVIDER_OPENAI,
    val llmBaseUrl: String = "https://api.openai.com/v1",
    val llmKey: String = "",
    val llmModel: String = "gpt-4o-mini",
    // По умолчанию для новых записей
    val language: String = "ru",
    val template: String = "general",
    val autoProcess: Boolean = true,
    // Разделение по говорящим (диаризация)
    val diarize: Boolean = false,
    val diarizeModel: String = "gpt-4o-transcribe-diarize",
) {
    /** Если ключ для протокола не задан, а сервис тот же — используем ключ расшифровки. */
    val effectiveLlmKey: String
        get() = llmKey.ifBlank {
            if (llmProvider == PROVIDER_OPENAI && llmBaseUrl.trimEnd('/') == sttBaseUrl.trimEnd('/')) sttKey else ""
        }

    val sttReady get() = sttKey.isNotBlank() && sttBaseUrl.isNotBlank() && sttModel.isNotBlank()
    val llmReady get() = effectiveLlmKey.isNotBlank() && llmBaseUrl.isNotBlank() && llmModel.isNotBlank()

    companion object {
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_ANTHROPIC = "anthropic"
    }
}

class SettingsStore(private val context: Context) {

    private val prefs: SharedPreferences = openPrefs()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AiSettings> = _state

    fun current(): AiSettings = _state.value

    fun save(s: AiSettings) {
        prefs.edit()
            .putString("sttBaseUrl", s.sttBaseUrl.trim())
            .putString("sttKey", s.sttKey.trim())
            .putString("sttModel", s.sttModel.trim())
            .putString("llmProvider", s.llmProvider)
            .putString("llmBaseUrl", s.llmBaseUrl.trim())
            .putString("llmKey", s.llmKey.trim())
            .putString("llmModel", s.llmModel.trim())
            .putString("language", s.language)
            .putString("template", s.template)
            .putBoolean("autoProcess", s.autoProcess)
            .putBoolean("diarize", s.diarize)
            .putString("diarizeModel", s.diarizeModel.trim())
            .apply()
        _state.value = load()
    }

    private fun load(): AiSettings {
        val d = AiSettings()
        return AiSettings(
            sttBaseUrl = prefs.getString("sttBaseUrl", d.sttBaseUrl) ?: d.sttBaseUrl,
            sttKey = prefs.getString("sttKey", "") ?: "",
            sttModel = prefs.getString("sttModel", d.sttModel) ?: d.sttModel,
            llmProvider = prefs.getString("llmProvider", d.llmProvider) ?: d.llmProvider,
            llmBaseUrl = prefs.getString("llmBaseUrl", d.llmBaseUrl) ?: d.llmBaseUrl,
            llmKey = prefs.getString("llmKey", "") ?: "",
            llmModel = prefs.getString("llmModel", d.llmModel) ?: d.llmModel,
            language = prefs.getString("language", d.language) ?: d.language,
            template = prefs.getString("template", d.template) ?: d.template,
            autoProcess = prefs.getBoolean("autoProcess", d.autoProcess),
            diarize = prefs.getBoolean("diarize", d.diarize),
            diarizeModel = prefs.getString("diarizeModel", d.diarizeModel) ?: d.diarizeModel,
        )
    }

    private fun openPrefs(): SharedPreferences {
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
        return try {
            create()
        } catch (e: Exception) {
            // Хранилище ключей Android может быть повреждено (восстановление с другого устройства и т.п.).
            context.deleteSharedPreferences("secure_settings")
            create()
        }
    }
}
