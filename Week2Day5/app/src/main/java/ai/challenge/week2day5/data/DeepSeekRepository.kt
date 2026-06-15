package ai.challenge.week2day5.data

import ai.challenge.week2day5.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Текст ответа модели вместе с расходом токенов (usage может отсутствовать). */
data class AskResult(val content: String, val usage: Usage?)

/**
 * Тарифы DeepSeek за 1M токенов (модель deepseek-chat → v4-flash, non-thinking).
 * Источники: cloudzero.com/blog/deepseek-pricing, tokenmix.ai/blog/deepseek-api-pricing.
 * При смене тарифов править только эти константы.
 */
private const val PRICE_INPUT_CACHE_HIT = 0.0028
private const val PRICE_INPUT_CACHE_MISS = 0.14
private const val PRICE_OUTPUT = 0.28

/**
 * Стоимость запроса в долларах. В ответе DeepSeek нет поля цены — считаем сами
 * из токенов: вход с разбивкой на кэш-хит/мисс, выход отдельно. Если разбивки
 * кэша нет (оба поля = 0) — весь промпт считаем как cache miss.
 */
fun Usage.costUsd(): Double {
    val hasCacheBreakdown = promptCacheHitTokens + promptCacheMissTokens > 0
    val hit = if (hasCacheBreakdown) promptCacheHitTokens else 0
    val miss = if (hasCacheBreakdown) promptCacheMissTokens else promptTokens
    return (hit * PRICE_INPUT_CACHE_HIT +
        miss * PRICE_INPUT_CACHE_MISS +
        completionTokens * PRICE_OUTPUT) / 1_000_000.0
}

/**
 * Обёртка над REST API DeepSeek (OpenAI-совместимый).
 * Документация: https://api-docs.deepseek.com
 */
class DeepSeekRepository(
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY
) {
    private val model = "deepseek-chat"
    private val endpoint = "https://api.deepseek.com/chat/completions"

    /**
     * Системная инструкция: ограничивает длину любого ответа модели.
     * Подмешивается в начало каждого запроса в [ask], поэтому ограничение
     * действует на все типы запросов (чат, генерация вопроса, перевод, пересказ).
     */
//    private val systemPrompt = Message(
//        role = "system",
//        content = "Отвечай не слишком большим текстом: не более 20 предложений в ответе. "
//    )



    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            )
        }
        // Логи запросов/ответов выводятся в Logcat с тегом "DeepSeek".
        // JSON в сообщениях форматируется с отступами для читаемости.
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }

    /**
     * Отправляет модели всю историю диалога и возвращает текст ответа.
     * Передаётся полный список сообщений (роли "user"/"assistant"), чтобы модель
     * сохраняла контекст и каждый новый запрос продолжал предыдущий.
     * Бросает Exception с читаемым сообщением.
     */
    suspend fun ask(messages: List<Message>): AskResult {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API-ключ не задан. Добавьте DEEPSEEK_API_KEY в local.properties и пересоберите проект."
            )
        }

        val response: HttpResponse = client.post(endpoint) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                DeepSeekRequest(
                    model = model,
                    messages = messages // + listOf(systemPrompt)
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Ошибка API (${response.status.value}): ${response.bodyAsText()}")
        }

        val body: DeepSeekResponse = response.body()
        val content = body.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw RuntimeException("Пустой ответ от модели.")
        return AskResult(content = content, usage = body.usage)
    }
}
