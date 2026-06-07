package ai.challenge.week0day2.data

import ai.challenge.week0day2.BuildConfig
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

/** Результат запроса: текст ответа + метаданные для сравнения. */
data class AskResult(
    val content: String,
    val finishReason: String?,
    val completionTokens: Int?
)

/**
 * Обёртка над REST API DeepSeek (OpenAI-совместимый).
 * Документация: https://api-docs.deepseek.com
 */
class DeepSeekRepository(
    private val apiKey: String = BuildConfig.DEEPSEEK_API_KEY
) {
    private val model = "deepseek-chat"
    private val endpoint = "https://api.deepseek.com/chat/completions"

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
     * Отправляет вопрос модели и возвращает ответ с метаданными.
     * Параметры контроля опциональны: без них запрос «голый» (как в задании №1),
     * с ними — «контролируемый» (формат + длина + условие завершения, задание №2).
     *
     * @param system системный промпт с описанием формата ответа (роль "system").
     * @param maxTokens жёсткий лимит длины ответа.
     * @param stop stop-последовательности, на которых генерация обрывается.
     *
     * Бросает Exception с читаемым сообщением.
     */
    suspend fun ask(
        prompt: String,
        system: String? = null,
        maxTokens: Int? = null,
        stop: List<String>? = null
    ): AskResult {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API-ключ не задан. Добавьте DEEPSEEK_API_KEY в local.properties и пересоберите проект."
            )
        }

        val messages = buildList {
            if (system != null) add(Message(role = "system", content = system))
            add(Message(role = "user", content = prompt))
        }

        val response: HttpResponse = client.post(endpoint) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                DeepSeekRequest(
                    model = model,
                    messages = messages,
                    maxTokens = maxTokens,
                    stop = stop
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Ошибка API (${response.status.value}): ${response.bodyAsText()}")
        }

        val body: DeepSeekResponse = response.body()
        val choice = body.choices.firstOrNull()
        val content = choice?.message?.content?.trim()
            ?: throw RuntimeException("Пустой ответ от модели.")

        return AskResult(
            content = content,
            finishReason = choice.finishReason,
            completionTokens = body.usage?.completionTokens
        )
    }
}
