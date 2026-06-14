package ai.challenge.week2day2

import android.app.Application
import ai.challenge.week2day2.data.db.ChatDatabase
import ai.challenge.week2day2.data.db.ChatHistoryRepository

/**
 * Держит синглтоны уровня приложения. DI в проекте ручной, поэтому БД и репозиторий
 * истории создаются лениво здесь, а ViewModel получает их через свою фабрику.
 */
class ChatApplication : Application() {
    val historyRepository: ChatHistoryRepository by lazy {
        ChatHistoryRepository(ChatDatabase.getInstance(this).chatDao())
    }
}
