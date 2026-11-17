package com.example.mbdictate.stt

import android.util.Base64
import com.example.mbdictate.WavChunker
import com.example.mbdictate.storage.DictateConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SttException(message: String) : Exception(message)

object SttClient {
    private const val TAG = "SttClient"

    fun transcribe(config: DictateConfig, wav: ByteArray, apiKey: String?): String {
        val stt = config.stt
        if (stt.endpoint.isBlank() || stt.model.isBlank()) {
            throw SttException("STT endpoint or model is not configured")
        }
        val chunks = WavChunker.chunkWav(wav, stt.maxAudioSeconds)
        val parts = mutableListOf<String>()
        for (chunk in chunks) {
            val text = when (stt.protocol) {
                "dashscope_asr" -> transcribeDashScope(config, chunk, apiKey)
                else -> transcribeOpenAi(config, chunk, apiKey)
            }
            if (text.isNotBlank()) parts.add(text.trim())
        }
        val merged = WavChunker.mergeTranscripts(parts)
        if (merged.isBlank()) throw SttException("No speech detected")
        return merged.trim()
    }

    private fun transcribeOpenAi(
        config: DictateConfig,
        wav: ByteArray,
        apiKey: String?,
    ): String {
        val stt = config.stt
        val boundary = "----MBDictate${System.currentTimeMillis()}"
        val connection = (URL(stt.endpoint).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty(
            "Content-Type",
            "multipart/form-data; boundary=$boundary",
        )
        if (!apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }

        DataOutputStream(connection.outputStream).use { out ->
            fun field(name: String, value: String) {
                out.writeBytes("--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"$name\"\r\n\r\n",
                )
                out.write(value.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }

            field("model", stt.model)
            if (stt.language.isNotBlank()) {
                field("language", stt.language)
            }
            field("response_format", "json")
            field("temperature", "0")
            if (stt.supportsVocabularyHint && config.vocabulary.isNotEmpty()) {
                field(
                    "prompt",
                    "Expected vocabulary: ${config.vocabulary.joinToString(", ")}",
                )
            }
            val extra = stt.extraParams
            for (key in extra.keys()) {
                when (val value = extra.opt(key)) {
                    is String, is Number, is Boolean -> field(key, value.toString())
                }
            }

            out.writeBytes("--$boundary\r\n")
            out.writeBytes(
                "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n",
            )
            out.writeBytes("Content-Type: audio/wav\r\n\r\n")
            out.write(wav)
            out.writeBytes("\r\n--$boundary--\r\n")
        }

        val code = connection.responseCode
        val body = readBody(connection, code)
        connection.disconnect()
        if (code !in 200..299) {
            throw SttException("STT request failed ($code): ${short(body)}")
        }
        val json = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw SttException("STT returned invalid JSON")
        }
        return json.optString("text", "")
    }

    private fun transcribeDashScope(
        config: DictateConfig,
        wav: ByteArray,
        apiKey: String?,
    ): String {
        val stt = config.stt
        val dataUri = "data:audio/wav;base64," +
            Base64.encodeToString(wav, Base64.NO_WRAP)
        val messages = JSONArray()
        if (stt.supportsVocabularyHint && config.vocabulary.isNotEmpty()) {
            messages.put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        JSONArray().put(
                            JSONObject().put(
                                "text",
                                "Expected vocabulary: ${config.vocabulary.joinToString(", ")}",
                            ),
                        ),
                    ),
            )
        }
        messages.put(
            JSONObject()
                .put("role", "user")
                .put(
                    "content",
                    JSONArray().put(
                        JSONObject()
                            .put("type", "input_audio")
                            .put(
                                "input_audio",
                                JSONObject().put("data", dataUri),
                            ),
                    ),
                ),
        )
        val asrOptions = JSONObject().apply {
            if (stt.language.isBlank()) {
                put("enable_lid", true)
            } else {
                put("language", stt.language)
            }
            put("enable_itn", false)
        }
        val body = JSONObject()
            .put("model", stt.model)
            .put("messages", messages)
            .put("stream", false)
            .put("asr_options", asrOptions)
        val extra = stt.extraParams
        for (key in extra.keys()) {
            body.put(key, extra.get(key))
        }

        val connection = (URL(stt.endpoint).openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Content-Type", "application/json")
        if (!apiKey.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = connection.responseCode
        val response = readBody(connection, code)
        connection.disconnect()
        if (code !in 200..299) {
            throw SttException("STT request failed ($code): ${short(response)}")
        }
        val json = try {
            JSONObject(response)
        } catch (e: Exception) {
            throw SttException("STT returned invalid JSON")
        }
        return extractText(json)
            ?: throw SttException("STT response did not contain text")
    }

    private fun extractText(json: JSONObject): String? {
        fun fromMessage(message: JSONObject?): String? {
            if (message == null) return null
            val content = message.opt("content")
            if (content is String && content.isNotBlank()) return content
            if (content is JSONArray) {
                val builder = StringBuilder()
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) builder.append(text)
                }
                if (builder.isNotBlank()) return builder.toString()
            }
            val text = message.optString("text", "")
            return text.takeIf { it.isNotBlank() }
        }

        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val first = choices.optJSONObject(0)
            fromMessage(first?.optJSONObject("message"))?.let { return it }
        }
        val output = json.optJSONObject("output")
        val outputChoices = output?.optJSONArray("choices")
        if (outputChoices != null && outputChoices.length() > 0) {
            val first = outputChoices.optJSONObject(0)
            fromMessage(first?.optJSONObject("message"))?.let { return it }
        }
        return null
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        if (stream == null) return ""
        return BufferedInputStream(stream).use { input ->
            String(itReadAll(input), Charsets.UTF_8)
        }
    }

    private fun itReadAll(stream: BufferedInputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun short(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length <= 300) trimmed else trimmed.take(300) + "…"
    }
}
