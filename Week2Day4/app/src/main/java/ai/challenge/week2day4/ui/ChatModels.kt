package ai.challenge.week2day4.ui

/** Расход токенов и стоимость одного ответа модели (для показа в ленте). */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double
)

/** Одно сообщение в ленте чата. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val usage: TokenUsage? = null
)

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = ""
)
