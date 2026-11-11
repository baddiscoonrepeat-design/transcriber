package ru.minutes.assistant.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Query(
        "SELECT id, title, createdAt, durationMs, template, status, error, protocolJson " +
            "FROM meetings ORDER BY createdAt DESC"
    )
    fun observeItems(): Flow<List<MeetingItem>>

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observe(id: Long): Flow<Meeting?>

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun get(id: Long): Meeting?

    @Query("SELECT * FROM meetings WHERE status = 'RECORDING'")
    suspend fun recording(): List<Meeting>

    @Insert
    suspend fun insert(m: Meeting): Long

    @Query("DELETE FROM meetings WHERE id = :id")
    suspend fun delete(id: Long)

    // Точечные обновления — чтобы фоновая обработка и действия пользователя не затирали друг друга.

    @Query("UPDATE meetings SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String)

    @Query("UPDATE meetings SET segmentsJson = :json, doneParts = :done WHERE id = :id")
    suspend fun setProgress(id: Long, json: String, done: Int)

    @Query("UPDATE meetings SET segmentsJson = '[]', doneParts = 0, protocolJson = '', speakersJson = '{}' WHERE id = :id")
    suspend fun resetTranscript(id: Long)

    @Query("UPDATE meetings SET protocolJson = :json WHERE id = :id")
    suspend fun setProtocol(id: Long, json: String)

    @Query("UPDATE meetings SET speakersJson = :json WHERE id = :id")
    suspend fun setSpeakers(id: Long, json: String)

    @Query("UPDATE meetings SET segmentsJson = :segments, speakersJson = :speakers WHERE id = :id")
    suspend fun setTranscript(id: Long, segments: String, speakers: String)

    @Query("UPDATE meetings SET title = :title, titleEdited = :edited WHERE id = :id")
    suspend fun setTitle(id: Long, title: String, edited: Boolean)

    @Query("UPDATE meetings SET template = :template WHERE id = :id")
    suspend fun setTemplate(id: Long, template: String)

    @Query(
        "UPDATE meetings SET partsJson = :parts, durationMs = :duration, " +
            "bookmarksJson = :bookmarks WHERE id = :id"
    )
    suspend fun setParts(id: Long, parts: String, duration: Long, bookmarks: String)

    @Query("UPDATE meetings SET bookmarksJson = :bookmarks WHERE id = :id")
    suspend fun setBookmarks(id: Long, bookmarks: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meetings ADD COLUMN speakersJson TEXT NOT NULL DEFAULT '{}'")
    }
}

@Database(entities = [Meeting::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun meetings(): MeetingDao
}
