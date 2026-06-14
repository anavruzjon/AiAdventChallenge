package ai.challenge.week2day2.data.db

/**
 * Тонкая обёртка над [ChatDao], чтобы ViewModel не зависел напрямую от Room.
 */
class ChatHistoryRepository(private val dao: ChatDao) {

    suspend fun load(): List<ChatMessageEntity> = dao.getAll()

    suspend fun add(content: String, isUser: Boolean, inContext: Boolean, timestamp: Long) {
        dao.insert(
            ChatMessageEntity(
                content = content,
                isUser = isUser,
                inContext = inContext,
                timestamp = timestamp
            )
        )
    }

    suspend fun clear() = dao.clear()
}
