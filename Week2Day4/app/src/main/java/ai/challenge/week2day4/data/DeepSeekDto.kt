package ai.challenge.week2day4.data

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

/** Ответ DeepSeek. Нас интересует choices[0].message.content и usage. */
@Serializable
data class DeepSeekResponse(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null
)

/** Расход токенов за запрос. DeepSeek отдаёт разбивку кэша — нужна для точной цены. */
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int = 0,
    @SerialName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int = 0
)

@Serializable
data class Choice(
    val message: Message? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)
