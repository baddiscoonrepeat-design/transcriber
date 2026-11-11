package ru.minutes.assistant.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import ru.minutes.assistant.ai.ApiException
import ru.minutes.assistant.ai.ConfigException
import ru.minutes.assistant.data.Status
import ru.minutes.assistant.data.parts
import ru.minutes.assistant.data.segments
import ru.minutes.assistant.data.toJson
import ru.minutes.assistant.graph
import java.io.File
import java.io.IOException

/**
 * Расшифровка по частям + составление протокола.
 * Прогресс по частям сохраняется в БД, поэтому после сбоя сети работа продолжается с места остановки.
 */
class ProcessingWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        if (id < 0) return Result.failure()
        val dao = applicationContext.graph.db.meetings()

        return try {
            run(id)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ConfigException) {
            dao.setStatus(id, Status.ERROR.name, e.message.orEmpty())
            Result.failure()
        } catch (e: ApiException) {
            if (e.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else {
                dao.setStatus(id, Status.ERROR.name, e.message.orEmpty())
                Result.failure()
            }
        } catch (e: IOException) {
            // Нет сети, обрыв соединения и т.п.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
            else {
                dao.setStatus(id, Status.ERROR.name, "Нет связи с сервером: ${e.message.orEmpty()}")
                Result.failure()
            }
        } catch (e: Exception) {
            dao.setStatus(id, Status.ERROR.name, e.message ?: e.javaClass.simpleName)
            Result.failure()
        }
    }

    private suspend fun run(id: Long) {
        val g = applicationContext.graph
        val dao = g.db.meetings()
        val settings = g.settings.current()

        val meeting = dao.get(id) ?: return
        val parts = meeting.parts
        if (parts.isEmpty()) throw ConfigException("Нет аудиофайлов для обработки")
        if (!settings.sttReady) throw ConfigException("Укажите ключ сервиса расшифровки в настройках")
        if (!settings.llmReady) throw ConfigException("Укажите ключ модели для протокола в настройках")

        // 1. Расшифровка (с продолжением с последней сохранённой части)
        val segments = meeting.segments.toMutableList()
        var offset = parts.take(meeting.doneParts).sumOf { it.durationMs }
        for (i in meeting.doneParts until parts.size) {
            dao.setStatus(id, Status.TRANSCRIBING.name, "")
            val file = File(parts[i].path)
            if (!file.exists()) throw ConfigException("Файл записи не найден: ${file.name}")
            val chunk = g.ai.transcribe(file, settings, meeting.language)
            segments += chunk.map { it.copy(startMs = it.startMs + offset) }
            offset += parts[i].durationMs
            dao.setProgress(id, segments.toJson(), i + 1)
        }
        if (segments.none { it.text.isNotBlank() }) {
            throw ConfigException("Речь в записи не распознана")
        }

        // 2. Протокол
        dao.setStatus(id, Status.SUMMARIZING.name, "")
        val fresh = dao.get(id) ?: return
        val protocol = g.ai.buildProtocol(settings, fresh, segments)
        dao.setProtocol(id, protocol.toJson())
        if (!fresh.titleEdited && protocol.title.isNotBlank()) {
            dao.setTitle(id, protocol.title.trim(), false)
        }
        dao.setStatus(id, Status.DONE.name, "")
    }

    companion object {
        const val KEY_ID = "id"
        private const val MAX_ATTEMPTS = 4
    }
}
