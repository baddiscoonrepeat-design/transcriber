package ru.minutes.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.minutes.assistant.ai.AiClient
import ru.minutes.assistant.audio.RecorderService
import ru.minutes.assistant.data.AppDb
import ru.minutes.assistant.data.MIGRATION_1_2
import ru.minutes.assistant.data.Repository
import ru.minutes.assistant.data.SettingsStore

class ProtokolistApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)

        val channel = NotificationChannel(
            RecorderService.CHANNEL_ID,
            "Запись встречи",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "Показывается, пока идёт запись" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // Запись, оборванная гибелью процесса, не должна пропасть.
        CoroutineScope(Dispatchers.IO).launch { graph.repo.recoverInterrupted() }
    }
}

class AppGraph(app: Application) {
    val db: AppDb by lazy { Room.databaseBuilder(app, AppDb::class.java, "protokolist.db").addMigrations(MIGRATION_1_2).build() }
    val repo: Repository by lazy { Repository(app, db.meetings()) }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val ai: AiClient by lazy { AiClient() }
}

val Context.graph: AppGraph
    get() = (applicationContext as ProtokolistApp).graph
