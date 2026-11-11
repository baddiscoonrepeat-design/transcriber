package ru.minutes.assistant.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.minutes.assistant.fmtDuration

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    encodeDefaults = true
}

inline fun <reified T> T.toJson(): String = AppJson.encodeToString(this)

enum class Status(val label: String) {
    RECORDING("Идёт запись"),
    RECORDED("Записано"),
    QUEUED("В очереди"),
    TRANSCRIBING("Расшифровка…"),
    SUMMARIZING("Составляю протокол…"),
    DONE("Готово"),
    ERROR("Ошибка");

    val isBusy get() = this == QUEUED || this == TRANSCRIBING || this == SUMMARIZING
}

@Serializable
data class AudioPart(val path: String, val durationMs: Long)

@Serializable
data class Segment(
    val startMs: Long,
    val text: String,
    /** Метка говорящего от сервиса расшифровки («A», «B»…); пусто — говорящий неизвестен. */
    val speaker: String = "",
)

/** Имя говорящего: заданное пользователем или автоматическая метка. */
fun speakerLabel(raw: String, names: Map<String, String>): String =
    names[raw]?.takeIf { it.isNotBlank() } ?: "Спикер $raw"

fun Segment.formatLine(names: Map<String, String>): String {
    val who = if (speaker.isBlank()) "" else speakerLabel(speaker, names) + ": "
    return "[${fmtDuration(startMs)}] $who$text"
}

@Serializable
data class KeyPoint(
    val topic: String = "",
    val details: String = "",
)

@Serializable
data class ActionItem(
    val task: String = "",
    val owner: String = "",
    val due: String = "",
    val priority: String = "",
    val done: Boolean = false,
)

@Serializable
data class Protocol(
    val title: String = "",
    val summary: String = "",
    val participants: List<String> = emptyList(),
    val agenda: List<String> = emptyList(),
    @SerialName("key_points") val keyPoints: List<KeyPoint> = emptyList(),
    val decisions: List<String> = emptyList(),
    @SerialName("action_items") val actionItems: List<ActionItem> = emptyList(),
    @SerialName("open_questions") val openQuestions: List<String> = emptyList(),
    @SerialName("next_meeting") val nextMeeting: String = "",
)

@Entity(tableName = "meetings")
data class Meeting(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val titleEdited: Boolean = false,
    val createdAt: Long,
    val durationMs: Long = 0,
    val dirPath: String = "",
    val partsJson: String = "[]",
    val bookmarksJson: String = "[]",
    val template: String = "general",
    val language: String = "ru",
    val context: String = "",
    val status: String = Status.RECORDING.name,
    val error: String = "",
    val segmentsJson: String = "[]",
    val doneParts: Int = 0,
    val protocolJson: String = "",
    /** Имена говорящих: метка → имя, JSON-объект. */
    @ColumnInfo(defaultValue = "{}") val speakersJson: String = "{}",
)

/** Облегчённая проекция для списка — без тяжёлой расшифровки. */
data class MeetingItem(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val template: String,
    val status: String,
    val error: String,
    val protocolJson: String,
)

val Meeting.statusEnum: Status
    get() = runCatching { Status.valueOf(status) }.getOrDefault(Status.ERROR)

val MeetingItem.statusEnum: Status
    get() = runCatching { Status.valueOf(status) }.getOrDefault(Status.ERROR)

val Meeting.parts: List<AudioPart>
    get() = runCatching { AppJson.decodeFromString<List<AudioPart>>(partsJson) }.getOrDefault(emptyList())

val Meeting.bookmarks: List<Long>
    get() = runCatching { AppJson.decodeFromString<List<Long>>(bookmarksJson) }.getOrDefault(emptyList())

val Meeting.segments: List<Segment>
    get() = runCatching { AppJson.decodeFromString<List<Segment>>(segmentsJson) }.getOrDefault(emptyList())

val Meeting.speakerNames: Map<String, String>
    get() = runCatching { AppJson.decodeFromString<Map<String, String>>(speakersJson) }.getOrDefault(emptyMap())

val Meeting.protocol: Protocol?
    get() = parseProtocolOrNull(protocolJson)

fun parseProtocolOrNull(json: String): Protocol? =
    if (json.isBlank()) null else runCatching { AppJson.decodeFromString<Protocol>(json) }.getOrNull()
