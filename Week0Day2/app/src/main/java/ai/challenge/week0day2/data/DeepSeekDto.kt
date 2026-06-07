package ai.challenge.week0day2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Тело запроса к DeepSeek (OpenAI-совместимый формат chat/completions).
 *
 * Поля контроля ответа опциональны (default = null). У kotlinx.serialization
 * `encodeDefaults` по умолчанию false, поэтому для «голого» запроса они не
 * попадают в JSON — отправляются только в «контролируемом» варианте.
 */
@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    /** Жёсткий лимит длины ответа на стороне API. */
    @SerialName("max_tokens") val maxTokens: Int? = null,
    /** Stop sequence: генерация обрывается на любой из этих строк. */
    val stop: List<String>? = null,
    val temperature: Double? = null
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

/** Ответ DeepSeek. Нас интересует choices[0].message.content и usage. */
@Serializable
data class DeepSeekResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: Message? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

/** Расход токенов. Нас интересует число токенов в ответе модели. */
@Serializable
data class Usage(
    @SerialName("completion_tokens") val completionTokens: Int? = null
)
