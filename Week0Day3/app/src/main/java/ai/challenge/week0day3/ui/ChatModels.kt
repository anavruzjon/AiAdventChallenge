package ai.challenge.week0day3.ui

/** Одно сообщение в ленте чата. [label] подписывает блок ответа методом промптинга. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val label: String? = null
)

/** Четыре стратегии промптинга, применяемые к каждому запросу. */
enum class PromptMethod(val title: String) {
    DIRECT("а) Прямой ответ"),
    STEP_BY_STEP("б) Пошагово"),
    GENERATED("в) Сгенерированный промпт"),
    EXPERTS("г) Группа экспертов")
}

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = ""
)
