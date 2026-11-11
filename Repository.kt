package ru.minutes.assistant.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import ru.minutes.assistant.work.ProcessingWorker
import java.io.File
import java.util.concurrent.TimeUnit

class Repository(private val ctx: Context, private val dao: MeetingDao) {

    fun observeItems(): Flow<List<MeetingItem>> = dao.observeItems()
    fun observe(id: Long): Flow<Meeting?> = dao.observe(id)
    suspend fun get(id: Long): Meeting? = dao.get(id)

    // ---------- Запись ----------

    suspend fun createRecording(
        title: String,
        startedAt: Long,
        dir: File,
        template: String,
        language: String,
        context: String,
    ): Long = dao.insert(
        Meeting(
            title = title,
            createdAt = startedAt,
            dirPath = dir.absolutePath,
            template = template,
            language = language,
            context = context,
            status = Status.RECORDING.name,
        )
    )

    suspend fun saveParts(id: Long, parts: List<AudioPart>, bookmarks: List<Long>) {
        dao.setParts(id, parts.toJson(), parts.sumOf { it.durationMs }, bookmarks.toJson())
    }

    suspend fun saveBookmarks(id: Long, bookmarks: List<Long>) = dao.setBookmarks(id, bookmarks.toJson())

    suspend fun finishRecording(id: Long, parts: List<AudioPart>, bookmarks: List<Long>) {
        saveParts(id, parts, bookmarks)
        dao.setStatus(id, Status.RECORDED.name, "")
    }

    /** Запись, оборванную гибелью процесса, восстанавливаем из файлов на диске. */
    suspend fun recoverInterrupted() {
        for (m in dao.recording()) {
            val files = File(m.dirPath).listFiles { f -> f.name.startsWith("part_") && f.length() > 0 }
                ?.sortedBy { it.name.substringAfter("part_").substringBefore('.').toIntOrNull() ?: 0 }
                .orEmpty()
            val parts = files.map { AudioPart(it.absolutePath, durationOf(it)) }.filter { it.durationMs > 0 }
            if (parts.isEmpty()) {
                deleteFiles(m)
                dao.delete(m.id)
            } else {
                dao.setParts(m.id, parts.toJson(), parts.sumOf { it.durationMs }, m.bookmarksJson)
                dao.setStatus(m.id, Status.RECORDED.name, "Запись была прервана — сохранено всё, что успело записаться.")
            }
        }
    }

    private fun durationOf(f: File): Long = try {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            r.release()
        }
    } catch (e: Exception) {
        0L
    }

    // ---------- Обработка ----------

    /** Ставит расшифровку и составление протокола в фоновую очередь. Выполняется и без открытого приложения. */
    suspend fun process(id: Long) {
        dao.setStatus(id, Status.QUEUED.name, "")
        enqueue(id)
    }

    suspend fun retranscribe(id: Long) {
        dao.resetTranscript(id)
        process(id)
    }

    suspend fun regenerate(id: Long, template: String) {
        dao.setTemplate(id, template)
        process(id)
    }

    private fun enqueue(id: Long) {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(workDataOf(ProcessingWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork("process_$id", ExistingWorkPolicy.REPLACE, request)
    }

    // ---------- Правки ----------

    suspend fun rename(id: Long, title: String) = dao.setTitle(id, title.trim(), true)

    suspend fun toggleAction(id: Long, index: Int) {
        val m = dao.get(id) ?: return
        val p = m.protocol ?: return
        if (index !in p.actionItems.indices) return
        val items = p.actionItems.toMutableList()
        items[index] = items[index].copy(done = !items[index].done)
        dao.setProtocol(id, p.copy(actionItems = items).toJson())
    }

    /** Сохраняет протокол, отредактированный вручную. */
    suspend fun saveProtocol(id: Long, protocol: Protocol) {
        val m = dao.get(id) ?: return
        dao.setProtocol(id, protocol.toJson())
        if (!m.statusEnum.isBusy && m.statusEnum != Status.DONE) dao.setStatus(id, Status.DONE.name, "")
    }

    suspend fun renameSpeaker(id: Long, raw: String, name: String) {
        val m = dao.get(id) ?: return
        val names = m.speakerNames.toMutableMap()
        if (name.isBlank()) names.remove(raw) else names[raw] = name.trim()
        dao.setSpeakers(id, names.toJson())
    }

    /** Правка реплики: текст, говорящий (существующий или новый с именем). */
    suspend fun editSegment(id: Long, index: Int, text: String, speaker: String, newSpeakerName: String?) {
        val m = dao.get(id) ?: return
        val segs = m.segments.toMutableList()
        if (index !in segs.indices) return
        var raw = speaker
        val names = m.speakerNames.toMutableMap()
        if (!newSpeakerName.isNullOrBlank()) {
            raw = "U" + System.currentTimeMillis()
            names[raw] = newSpeakerName.trim()
        }
        segs[index] = segs[index].copy(text = text.trim(), speaker = raw)
        dao.setTranscript(id, segs.toJson(), names.toJson())
    }

    suspend fun deleteSegment(id: Long, index: Int) {
        val m = dao.get(id) ?: return
        val segs = m.segments.toMutableList()
        if (index !in segs.indices) return
        segs.removeAt(index)
        dao.setTranscript(id, segs.toJson(), m.speakersJson)
    }

    suspend fun delete(id: Long) {
        WorkManager.getInstance(ctx).cancelUniqueWork("process_$id")
        val m = dao.get(id)
        if (m != null) deleteFiles(m)
        dao.delete(id)
    }

    private fun deleteFiles(m: Meeting) {
        if (m.dirPath.isNotBlank()) File(m.dirPath).deleteRecursively()
        else m.parts.forEach { File(it.path).delete() }
    }
}
