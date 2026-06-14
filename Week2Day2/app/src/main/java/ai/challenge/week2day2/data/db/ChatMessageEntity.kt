package ai.challenge.week2day2.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Сохранённое сообщение диалога. Одна таблица описывает обе сущности рантайма:
 * - ленту UI (все строки → ChatMessage(content, isUser));
 * - контекст для API (строки с inContext == true → Message(role, content)).
 */
@Entity(tableName = "messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val isUser: Boolean,
    /** Входит ли сообщение в контекст, отправляемый модели. */
    val inContext: Boolean,
    /** Момент создания — для стабильного порядка восстановления. */
    val timestamp: Long
)
