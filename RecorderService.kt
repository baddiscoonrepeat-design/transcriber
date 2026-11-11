package ru.minutes.assistant.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.minutes.assistant.MainActivity
import ru.minutes.assistant.R
import ru.minutes.assistant.data.AudioPart
import ru.minutes.assistant.defaultTitle
import ru.minutes.assistant.fmtDuration
import ru.minutes.assistant.graph
import java.io.File
import kotlin.math.sqrt

/**
 * Запись встречи в foreground-сервисе: не прерывается при сворачивании приложения и выключении экрана.
 * Запись режется на части по 25 минут (лимит загрузки в API расшифровки) и пишется в OGG/Opus —
 * контейнер, который остаётся читаемым даже при внезапном завершении процесса.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder: MediaRecorder? = null
    private var tickJob: Job? = null
    private var createJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var dir: File? = null
    @Volatile private var meetingId = 0L
    private val parts = mutableListOf<AudioPart>()
    private val bookmarks = mutableListOf<Long>()
    private val levels = ArrayDeque<Float>()
    private var currentFile: File? = null
    private var segStart = 0L
    private var elapsed = 0L
    private var paused = false
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent)
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_BOOKMARK -> addBookmark()
            ACTION_STOP -> stop()
            else -> if (!RecorderState.state.value.active) stopSelf()
        }
        return START_NOT_STICKY
    }

    // ---------- Управление ----------

    private fun start(intent: Intent) {
        // Сервис запущен через startForegroundService — обязаны сразу показать уведомление.
        postForeground()
        if (RecorderState.state.value.active || stopping) return

        val g = applicationContext.graph
        val startedAt = System.currentTimeMillis()
        val d = File(filesDir, "recordings/$startedAt").apply { mkdirs() }
        dir = d
        parts.clear(); bookmarks.clear(); levels.clear()
        elapsed = 0; segStart = 0; paused = false

        try {
            beginPart()
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось начать запись: ${e.message}", Toast.LENGTH_LONG).show()
            d.deleteRecursively()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        acquireWakeLock()

        val settings = g.settings.current()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { defaultTitle(startedAt) }
        val template = intent.getStringExtra(EXTRA_TEMPLATE) ?: settings.template
        val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: settings.language
        val notes = intent.getStringExtra(EXTRA_CONTEXT).orEmpty()

        createJob = scope.launch(Dispatchers.IO) {
            meetingId = g.repo.createRecording(title, startedAt, d, template, language, notes)
        }

        RecorderState.state.value = RecorderState.State(active = true)
        startTicker()
        postForeground()
    }

    private fun setPaused(pause: Boolean) {
        if (!RecorderState.state.value.active || paused == pause || stopping) return
        try {
            if (pause) recorder?.pause() else recorder?.resume()
        } catch (e: Exception) {
            return
        }
        paused = pause
        postForeground()
        publish()
    }

    private fun addBookmark() {
        if (!RecorderState.state.value.active || stopping) return
        bookmarks.add(elapsed)
        persist()
        publish()
    }

    private fun stop() {
        if (!RecorderState.state.value.active || stopping) {
            if (!stopping) stopSelf()
            return
        }
        stopping = true
        tickJob?.cancel()
        endPart()

        val finalParts = parts.toList()
        val finalMarks = bookmarks.toList()
        val d = dir

        scope.launch {
            val g = applicationContext.graph
            val id = withContext(Dispatchers.IO) {
                createJob?.join()
                val newId = meetingId
                if (finalParts.isEmpty()) {
                    // Ничего не записалось — не оставляем пустую встречу.
                    g.repo.delete(newId)
                    d?.deleteRecursively()
                    0L
                } else {
                    g.repo.finishRecording(newId, finalParts, finalMarks)
                    val s = g.settings.current()
                    if (s.autoProcess && s.sttReady && s.llmReady) g.repo.process(newId)
                    newId
                }
            }
            releaseWakeLock()
            RecorderState.state.value = RecorderState.State()
            RecorderState.finished.tryEmit(id)
            stopping = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---------- Тик: время, уровень звука, ротация частей ----------

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(80)
                val now = SystemClock.elapsedRealtime()
                if (!paused) {
                    elapsed += now - last
                    val amp = try { recorder?.maxAmplitude ?: 0 } catch (e: Exception) { 0 }
                    levels.addLast(sqrt(amp / 32767f).coerceIn(0f, 1f))
                    while (levels.size > LEVEL_COUNT) levels.removeFirst()
                    if (elapsed - segStart >= SEGMENT_MS) rotate()
                }
                last = now
                publish()
            }
        }
    }

    private fun rotate() {
        endPart()
        try {
            beginPart()
        } catch (e: Exception) {
            Toast.makeText(this, "Запись остановлена: ${e.message}", Toast.LENGTH_LONG).show()
            stop()
            return
        }
        persist()
    }

    private fun publish() {
        RecorderState.state.value = RecorderState.State(
            active = true,
            paused = paused,
            elapsedMs = elapsed,
            levels = levels.toList(),
            bookmarks = bookmarks.toList(),
        )
    }

    private fun persist() {
        val p = parts.toList()
        val b = bookmarks.toList()
        val g = applicationContext.graph
        scope.launch(Dispatchers.IO) {
            createJob?.join()
            g.repo.saveParts(meetingId, p, b)
        }
    }

    // ---------- Запись частей ----------

    private fun beginPart() {
        val n = parts.size
        val ogg = File(dir, "part_$n.ogg")
        val m4a = File(dir, "part_$n.m4a")
        val (rec, file) = try {
            prepare(ogg, true) to ogg
        } catch (e: Exception) {
            // На части устройств нет OGG/Opus-кодека — падаем обратно на AAC в MP4.
            ogg.delete()
            prepare(m4a, false) to m4a
        }
        recorder = rec
        currentFile = file
        segStart = elapsed
    }

    private fun prepare(file: File, ogg: Boolean): MediaRecorder {
        val r = newRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (ogg) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                r.setAudioEncodingBitRate(24_000)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioEncodingBitRate(32_000)
            }
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            return r
        } catch (e: Exception) {
            runCatching { r.release() }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()

    private fun endPart() {
        val rec = recorder ?: return
        recorder = null
        runCatching { rec.stop() } // бросает исключение, если данных не записано
        runCatching { rec.release() }
        val f = currentFile
        if (f != null && f.exists() && f.length() > 0) {
            parts += AudioPart(f.absolutePath, (elapsed - segStart).coerceAtLeast(0))
        }
        currentFile = null
    }

    // ---------- Уведомление и wake lock ----------

    private fun postForeground() {
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(if (paused) "Запись на паузе" else "Идёт запись встречи")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (paused) {
            b.setContentText(fmtDuration(elapsed))
        } else {
            b.setUsesChronometer(true).setWhen(System.currentTimeMillis() - elapsed)
        }
        b.addAction(0, if (paused) "Продолжить" else "Пауза", actionIntent(if (paused) ACTION_RESUME else ACTION_PAUSE))
        b.addAction(0, "Закладка", actionIntent(ACTION_BOOKMARK))
        b.addAction(0, "Стоп", actionIntent(ACTION_STOP))
        return b.build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this, action.hashCode(),
        Intent(this, RecorderService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "protokolist:recording")
            .apply { acquire(8 * 60 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        tickJob?.cancel()
        recorder?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        recorder = null
        releaseWakeLock()
        if (!stopping) RecorderState.state.value = RecorderState.State()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "recording"
        private const val NOTIF_ID = 1001
        private const val SEGMENT_MS = 25 * 60_000L
        private const val LEVEL_COUNT = 56

        const val ACTION_START = "ru.minutes.assistant.START"
        const val ACTION_PAUSE = "ru.minutes.assistant.PAUSE"
        const val ACTION_RESUME = "ru.minutes.assistant.RESUME"
        const val ACTION_BOOKMARK = "ru.minutes.assistant.BOOKMARK"
        const val ACTION_STOP = "ru.minutes.assistant.STOP"

        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEMPLATE = "template"
        private const val EXTRA_LANGUAGE = "language"
        private const val EXTRA_CONTEXT = "context"

        fun start(ctx: Context, title: String, template: String, language: String, notes: String) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, RecorderService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEMPLATE, template)
                    .putExtra(EXTRA_LANGUAGE, language)
                    .putExtra(EXTRA_CONTEXT, notes),
            )
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, RecorderService::class.java).setAction(action))
        }
    }
}
