package ai.challenge.week2day2.ui

/** Одно сообщение в ленте чата. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean
)

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = ""
)
