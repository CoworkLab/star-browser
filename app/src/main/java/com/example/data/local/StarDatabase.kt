package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val isDesktopMode: Boolean = false,
    val isActive: Boolean = false,
    val blockedCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systemDownloadId: Long = -1L,
    val fileName: String,
    val url: String,
    val mimeType: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_conversations")
data class AgentConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_messages")
data class AgentMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String, // "user", "assistant", "step"
    val content: String,
    val reasoning: String? = null,
    val toolName: String? = null,
    val toolSuccess: Boolean? = null,
    val toolCallId: String? = null,
    val toolArgs: String? = null,
    val toolStatus: String? = null, // "running", "awaiting_approval", "completed", "failed", "skipped", "cancelled"
    val toolDurationMs: Long? = null,
    val citationsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface BrowserTabDao {
    @Query("SELECT * FROM browser_tabs ORDER BY createdAt ASC")
    fun observeTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: String): BrowserTabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTab(tab: BrowserTabEntity)

    @Query("UPDATE browser_tabs SET isActive = (id = :activeId)")
    suspend fun setActiveTab(activeId: String)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteTab(id: String)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT 120")
    fun observeHistory(): Flow<List<HistoryEntryEntity>>

    @Insert
    suspend fun insertHistory(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries")
    suspend fun clearHistory()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT 120")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert
    suspend fun insertDownload(download: DownloadEntity): Long

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Long)

    @Query("DELETE FROM downloads")
    suspend fun clearDownloads()
}

@Dao
interface AgentChatDao {
    @Query("SELECT * FROM agent_conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<AgentConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: AgentConversationEntity)

    @Query("UPDATE agent_conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConversationMeta(id: String, title: String, updatedAt: Long)

    @Query("DELETE FROM agent_conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(conversationId: String): Flow<List<AgentMessageEntity>>

    @Query("SELECT * FROM agent_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(conversationId: String): List<AgentMessageEntity>

    @Insert
    suspend fun insertMessage(message: AgentMessageEntity): Long

    @Query("UPDATE agent_messages SET toolStatus = :status, content = :content, toolSuccess = :success, toolDurationMs = :durationMs WHERE id = :id")
    suspend fun updateStepMessage(id: Long, status: String, content: String, success: Boolean?, durationMs: Long?)

    @Query("UPDATE agent_messages SET toolStatus = 'cancelled', toolSuccess = 0, content = CASE WHEN content = '' THEN 'Interrupted before completion.' ELSE content END WHERE toolStatus IN ('running', 'awaiting_approval')")
    suspend fun recoverInterruptedSteps()

    @Query("DELETE FROM agent_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Database(
    entities = [
        BrowserTabEntity::class,
        BookmarkEntity::class,
        HistoryEntryEntity::class,
        DownloadEntity::class,
        AgentConversationEntity::class,
        AgentMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class StarDatabase : RoomDatabase() {
    abstract fun tabDao(): BrowserTabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun agentChatDao(): AgentChatDao

    companion object {
        @Volatile
        private var INSTANCE: StarDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE agent_messages ADD COLUMN toolCallId TEXT")
                db.execSQL("ALTER TABLE agent_messages ADD COLUMN toolArgs TEXT")
                db.execSQL("ALTER TABLE agent_messages ADD COLUMN toolStatus TEXT")
                db.execSQL("ALTER TABLE agent_messages ADD COLUMN toolDurationMs INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE browser_tabs ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE browser_tabs SET createdAt = updatedAt")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS downloads (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        systemDownloadId INTEGER NOT NULL,
                        fileName TEXT NOT NULL,
                        url TEXT NOT NULL,
                        mimeType TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): StarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StarDatabase::class.java,
                    "star_browser.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
    }
}

class StarRepository(private val db: StarDatabase) {
    val tabs: Flow<List<BrowserTabEntity>> = db.tabDao().observeTabs()
    val bookmarks: Flow<List<BookmarkEntity>> = db.bookmarkDao().observeBookmarks()
    val history: Flow<List<HistoryEntryEntity>> = db.historyDao().observeHistory()
    val downloads: Flow<List<DownloadEntity>> = db.downloadDao().observeDownloads()
    val conversations: Flow<List<AgentConversationEntity>> = db.agentChatDao().observeConversations()

    fun messages(conversationId: String): Flow<List<AgentMessageEntity>> =
        db.agentChatDao().observeMessages(conversationId)

    suspend fun getMessagesOnce(conversationId: String): List<AgentMessageEntity> =
        db.agentChatDao().getMessagesOnce(conversationId)

    suspend fun getTabById(id: String): BrowserTabEntity? = db.tabDao().getTabById(id)
    suspend fun upsertTab(tab: BrowserTabEntity) = db.tabDao().upsertTab(tab)
    suspend fun setActiveTab(activeId: String) = db.tabDao().setActiveTab(activeId)
    suspend fun deleteTab(id: String) = db.tabDao().deleteTab(id)

    suspend fun toggleBookmark(url: String, title: String, currentlyBookmarked: Boolean) {
        if (currentlyBookmarked) {
            db.bookmarkDao().deleteByUrl(url)
        } else {
            db.bookmarkDao().insertBookmark(BookmarkEntity(url = url, title = title.ifBlank { url }))
        }
    }

    suspend fun recordHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        db.historyDao().insertHistory(HistoryEntryEntity(url = url, title = title.ifBlank { url }))
    }

    suspend fun clearHistory() = db.historyDao().clearHistory()

    suspend fun recordDownload(
        systemDownloadId: Long,
        fileName: String,
        url: String,
        mimeType: String?
    ): Long = db.downloadDao().insertDownload(
        DownloadEntity(
            systemDownloadId = systemDownloadId,
            fileName = fileName,
            url = url,
            mimeType = mimeType
        )
    )

    suspend fun deleteDownload(id: Long) = db.downloadDao().deleteDownload(id)
    suspend fun clearDownloads() = db.downloadDao().clearDownloads()

    suspend fun upsertConversation(conversation: AgentConversationEntity) =
        db.agentChatDao().upsertConversation(conversation)

    suspend fun updateConversationMeta(id: String, title: String) =
        db.agentChatDao().updateConversationMeta(id, title, System.currentTimeMillis())

    suspend fun deleteConversation(id: String) {
        db.agentChatDao().deleteMessagesForConversation(id)
        db.agentChatDao().deleteConversation(id)
    }

    suspend fun insertMessage(message: AgentMessageEntity): Long =
        db.agentChatDao().insertMessage(message)

    suspend fun updateStepMessage(
        id: Long,
        status: String,
        content: String,
        success: Boolean?,
        durationMs: Long?
    ) = db.agentChatDao().updateStepMessage(id, status, content, success, durationMs)

    suspend fun recoverInterruptedSteps() = db.agentChatDao().recoverInterruptedSteps()
}
