package ai.challenge.week2day2.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {
    /** Все сообщения в порядке создания. */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    /** Полная очистка диалога. */
    @Query("DELETE FROM messages")
    suspend fun clear()
}
