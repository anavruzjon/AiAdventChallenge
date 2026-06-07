package ai.challenge.week0day3.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Тело запроса к DeepSeek (OpenAI-совместимый формат chat/completions). */
@Serializable
data class DeepSeekRequest(
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

/** Ответ DeepSeek. Нас интересует choices[0].message.content. */
@Serializable
data class DeepSeekResponse(
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val message: Message? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)
