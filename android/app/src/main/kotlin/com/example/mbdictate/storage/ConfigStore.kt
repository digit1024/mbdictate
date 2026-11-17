package com.example.mbdictate.storage

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SttRuntimeConfig(
    val protocol: String,
    val endpoint: String,
    val model: String,
    val language: String,
    val maxAudioSeconds: Int,
    val supportsVocabularyHint: Boolean,
    val extraParams: JSONObject,
)

data class DictateConfig(
    val providerId: String,
    val providerLabel: String,
    val modelId: String,
    val stt: SttRuntimeConfig,
    val inputMode: String,
    val vocabulary: List<String>,
    val insertionMode: String,
)

object ConfigStore {
    private const val PREFS = "mbdictate"
    private const val KEY_CONFIG = "config_json"

    private fun defaultJson(): JSONObject {
        return JSONObject().apply {
            put("providerId", "groq")
            put("providerLabel", "Groq")
            put("modelId", "whisper-large-v3")
            put(
                "stt",
                JSONObject().apply {
                    put("protocol", "openai_transcription")
                    put(
                        "endpoint",
                        "https://api.groq.com/openai/v1/audio/transcriptions",
                    )
                    put("model", "whisper-large-v3")
                    put("language", "")
                    put("maxAudioSeconds", 300)
                    put("supportsVocabularyHint", true)
                    put("extraParams", JSONObject())
                },
            )
            put("inputMode", "hold")
            put("vocabulary", JSONArray())
            put("insertionMode", "auto")
        }
    }

    fun loadRaw(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_CONFIG, null) ?: defaultJson().toString()
    }

    fun saveRaw(context: Context, raw: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONFIG, raw)
            .apply()
    }

    fun save(context: Context, config: JSONObject) {
        saveRaw(context, config.toString())
    }

    fun load(context: Context): DictateConfig {
        val json = try {
            JSONObject(loadRaw(context))
        } catch (_: Exception) {
            defaultJson()
        }
        val stt = json.optJSONObject("stt") ?: defaultJson().getJSONObject("stt")
        val vocabularyJson = json.optJSONArray("vocabulary") ?: JSONArray()
        val vocabulary = buildList {
            for (i in 0 until vocabularyJson.length()) {
                val term = vocabularyJson.optString(i).trim()
                if (term.isNotEmpty()) add(term)
            }
        }
        return DictateConfig(
            providerId = json.optString("providerId", "groq"),
            providerLabel = json.optString("providerLabel", "Groq"),
            modelId = json.optString("modelId", "whisper-large-v3"),
            stt = SttRuntimeConfig(
                protocol = stt.optString("protocol", "openai_transcription"),
                endpoint = stt.optString("endpoint", ""),
                model = stt.optString("model", ""),
                language = stt.optString("language", "")
                    .takeUnless { it.equals("auto", ignoreCase = true) } ?: "",
                maxAudioSeconds = stt.optInt("maxAudioSeconds", 300).coerceAtLeast(10),
                supportsVocabularyHint = stt.optBoolean(
                    "supportsVocabularyHint",
                    false,
                ),
                extraParams = stt.optJSONObject("extraParams") ?: JSONObject(),
            ),
            inputMode = json.optString("inputMode", "hold"),
            vocabulary = vocabulary,
            insertionMode = json.optString("insertionMode", "auto"),
        )
    }
}
