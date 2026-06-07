package ai.challenge.week0day5.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тело запроса (OpenAI-совместимый формат chat/completions — годится и для DeepSeek, и для HF Router). */
@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

/** Ответ. Нас интересует choices[0].message.content и usage (счётчики токенов). */
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

/** Счётчики токенов из ответа (OpenAI-совместимое поле usage). */
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)
