package ai.challenge.week0day2.ui

/** Значения параметров ограничений по умолчанию. */
const val DEFAULT_MAX_TOKENS = 120
const val DEFAULT_STOP_SEQUENCE = "<<КОНЕЦ>>"
const val DEFAULT_FORMAT_PROMPT =
    "Отвечай строго маркированным списком ровно из 3 пунктов. " +
    "Каждый пункт — не более 12 слов. Без вступления и заключения. " +
    "Заверши ответ строкой <<КОНЕЦ>>."

/** Одно сообщение в ленте чата. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    /** Заголовок карточки ответа: «Без ограничений» / «С ограничениями». */
    val label: String? = null,
    /** Метаданные ответа: finish_reason и число токенов. */
    val meta: String? = null
)

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = "",
    /** Открыта ли панель параметров ограничений. */
    val settingsOpen: Boolean = false,
    /** Лимит длины «контролируемого» ответа (max_tokens). */
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** Описание формата ответа (system-промпт). */
    val formatPrompt: String = DEFAULT_FORMAT_PROMPT,
    /** Stop-последовательность (пусто = не отправлять). */
    val stopSequence: String = DEFAULT_STOP_SEQUENCE
)
