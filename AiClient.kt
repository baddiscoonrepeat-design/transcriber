package ru.minutes.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ru.minutes.assistant.data.AiSettings
import ru.minutes.assistant.data.AppJson
import ru.minutes.assistant.data.Meeting
import ru.minutes.assistant.data.Protocol
import ru.minutes.assistant.data.Segment
import ru.minutes.assistant.data.bookmarks
import ru.minutes.assistant.data.formatLine
import ru.minutes.assistant.data.speakerNames
import ru.minutes.assistant.fmtDateFull
import ru.minutes.assistant.fmtDuration
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int, val retryable: Boolean) : IOException(message)

/** Ошибка настройки или данных — повторять бессмысленно. */
class ConfigException(message: String) : Exception(message)

class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()

    // ---------- Расшифровка ----------

    /** Расшифровывает один аудиофайл. Метки времени — относительно начала файла. */
    suspend fun transcribe(file: File, s: AiSettings, language: String): List<Segment> =
        withContext(Dispatchers.IO) {
            // Модели gpt-4o-*-transcribe не отдают сегменты; whisper-совместимые — отдают.
            val diarize = s.diarize && s.diarizeModel.isNotBlank()
            val model = if (diarize) s.diarizeModel else s.sttModel
            val verbose = !diarize && !model.startsWith("gpt-4o")
            val format = when {
                diarize -> "diarized_json"
                verbose -> "verbose_json"
                else -> "json"
            }
            val mime = if (file.extension.equals("ogg", true)) "audio/ogg" else "audio/mp4"

            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                .addFormDataPart("model", model)
                .addFormDataPart("response_format", format)
                .apply { if (diarize) addFormDataPart("chunking_strategy", "auto") }
                .apply { if (language.isNotBlank()) addFormDataPart("language", language) }
                .build()

            val request = Request.Builder()
                .url(s.sttBaseUrl.trimEnd('/') + "/audio/transcriptions")
                .header("Authorization", "Bearer ${s.sttKey}")
                .post(body)
                .build()

            val json = JSONObject(execute(request))
            val segs = json.optJSONArray("segments")
            if (segs != null && segs.length() > 0) {
                val list = buildList {
                    for (i in 0 until segs.length()) {
                        val o = segs.getJSONObject(i)
                        val text = o.optString("text").trim()
                        val speaker = if (o.isNull("speaker")) "" else o.optString("speaker").trim()
                        if (text.isNotEmpty()) {
                            add(Segment((o.optDouble("start", 0.0) * 1000).toLong(), text, speaker))
                        }
                    }
                }
                if (diarize) mergeTurns(list) else list
            } else {
                val text = json.optString("text").trim()
                if (text.isEmpty()) emptyList() else listOf(Segment(0, text))
            }
        }

    /** Склеивает подряд идущие короткие реплики одного говорящего в одну — так расшифровка читается как диалог. */
    private fun mergeTurns(list: List<Segment>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (s in list) {
            val last = out.lastOrNull()
            if (last != null && s.speaker.isNotEmpty() && last.speaker == s.speaker && last.text.length < 500) {
                out[out.lastIndex] = last.copy(text = last.text + " " + s.text)
            } else {
                out += s
            }
        }
        return out
    }

    // ---------- Протокол ----------

    suspend fun buildProtocol(s: AiSettings, m: Meeting, segments: List<Segment>): Protocol {
        val names = m.speakerNames
        val transcript = segments.joinToString("\n") { it.formatLine(names) }

        val header = buildString {
            appendLine("Дата встречи: ${fmtDateFull(m.createdAt)}")
            appendLine("Рабочее название: ${m.title}")
            if (m.context.isNotBlank()) appendLine("Заметки пользователя (участники, контекст): ${m.context}")
            val marks = m.bookmarks
            if (marks.isNotEmpty()) {
                appendLine("Закладки пользователя на моментах: " + marks.joinToString(", ") { fmtDuration(it) })
            }
            if (segments.any { it.speaker.isNotBlank() }) {
                appendLine(
                    "В расшифровке перед репликами указаны говорящие. Имена без слова «Спикер» заданы пользователем и " +
                        "достоверны — используй их для участников и ответственных. Метки вида «Спикер A» присвоены " +
                        "автоматически: не принимай их за имена, пока настоящее имя не прозвучало в разговоре. " +
                        "Распределение по говорящим может содержать ошибки."
                )
            }
            appendLine()
        }

        val body = if (transcript.length <= SINGLE_PASS_LIMIT) {
            "Расшифровка:\n$transcript"
        } else {
            // Очень длинная встреча: сначала конспекты фрагментов, затем итоговый протокол.
            val chunks = splitLines(transcript, CHUNK_SIZE)
            val notes = chunks.mapIndexed { i, chunk ->
                chat(s, Prompts.notesSystem(i + 1, chunks.size), chunk, json = false)
            }
            "Конспекты последовательных фрагментов расшифровки:\n\n" + notes.joinToString("\n\n---\n\n")
        }

        val raw = chat(
            s,
            Prompts.protocolSystem(MeetingTemplate.byId(m.template), m.language),
            header + body,
            json = true,
        )
        return parseProtocol(raw)
    }

    private fun parseProtocol(raw: String): Protocol {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) throw ApiException("Модель вернула ответ не в формате JSON", 0, true)
        return try {
            AppJson.decodeFromString<Protocol>(raw.substring(start, end + 1))
        } catch (e: Exception) {
            throw ApiException("Не удалось разобрать ответ модели", 0, true)
        }
    }

    private fun splitLines(text: String, size: Int): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        for (line in text.lineSequence()) {
            if (sb.length + line.length + 1 > size && sb.isNotEmpty()) {
                result += sb.toString()
                sb.clear()
            }
            sb.appendLine(line)
        }
        if (sb.isNotEmpty()) result += sb.toString()
        return result
    }

    // ---------- Чат-запрос к выбранному провайдеру ----------

    private suspend fun chat(s: AiSettings, system: String, user: String, json: Boolean): String =
        withContext(Dispatchers.IO) {
            val base = s.llmBaseUrl.trimEnd('/')
            if (s.llmProvider == AiSettings.PROVIDER_ANTHROPIC) {
                val payload = JSONObject()
                    .put("model", s.llmModel)
                    .put("max_tokens", 8000)
                    .put("system", system)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                val request = Request.Builder()
                    .url("$base/v1/messages")
                    .header("x-api-key", s.effectiveLlmKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val content = JSONObject(execute(request)).getJSONArray("content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.getJSONObject(i)
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
            } else {
                val payload = JSONObject()
                    .put("model", s.llmModel)
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    )
                if (json && base.contains("api.openai.com")) {
                    payload.put("response_format", JSONObject().put("type", "json_object"))
                }
                val request = Request.Builder()
                    .url("$base/chat/completions")
                    .header("Authorization", "Bearer ${s.effectiveLlmKey}")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                JSONObject(execute(request))
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content")
            }
        }

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = runCatching {
                    val o = JSONObject(text)
                    o.optJSONObject("error")?.optString("message").orEmpty().ifBlank { o.optString("message") }
                }.getOrNull().orEmpty().ifBlank { text.take(300) }
                throw ApiException(
                    "HTTP ${resp.code}: $detail",
                    resp.code,
                    retryable = resp.code == 429 || resp.code >= 500,
                )
            }
            return text
        }
    }

    private companion object {
        const val SINGLE_PASS_LIMIT = 120_000
        const val CHUNK_SIZE = 60_000
    }
}
