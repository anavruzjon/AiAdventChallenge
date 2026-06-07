package ai.challenge.week0day5.data

import ai.challenge.week0day5.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
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

/** Замеренный результат одного запроса к модели. */
data class AskMetrics(
    val answer: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val elapsedMs: Long,
    val costUsd: Double
)

/**
 * Обёртка над HuggingFace Router (OpenAI-совместимый chat/completions).
 * Один токен (HF_API_KEY) на все модели — меняется только поле `model`.
 * Документация: https://huggingface.co/docs/inference-providers
 */
class HuggingFaceRepository(
    private val apiKey: String = BuildConfig.HF_API_KEY
) {
    private val endpoint = "https://router.huggingface.co/v1/chat/completions"

    // Верхний предел длины ответа. Высокий — чтобы модели договаривали до конца
    // (тогда число выходных токенов отражает реальную «многословность»), но со
    // страховкой от бесконечной генерации. Если упираются в потолок — поднимите.
    private val maxTokens = 4196

    private val client = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000_000
            connectTimeoutMillis = 50000_000
            socketTimeoutMillis = 15000_000
        }
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            )
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.ALL
        }
    }

    /**
     * Отправляет [prompt] модели [model], замеряет время и токены, считает оценочную стоимость.
     * Бросает Exception с читаемым сообщением при ошибке.
     */
    suspend fun ask(model: LlmModel, prompt: String): AskMetrics {
        if (apiKey.isBlank()) {
            throw IllegalStateException(
                "API-ключ не задан. Добавьте HF_API_KEY в app.properties и пересоберите проект."
            )
        }

        val startNs = System.nanoTime()
        val response: HttpResponse = client.post(endpoint) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(
                DeepSeekRequest(
                    model = model.id,
                    messages = listOf(Message(role = "user", content = prompt)),
                    maxTokens = maxTokens
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Ошибка API (${response.status.value}): ${response.bodyAsText()}")
        }

        val body: DeepSeekResponse = response.body()
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000

        val answer = body.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw RuntimeException("Пустой ответ от модели.")

        val usage = body.usage ?: Usage()
        val cost = usage.promptTokens / 1_000_000.0 * model.inputPricePerM +
                usage.completionTokens / 1_000_000.0 * model.outputPricePerM

        return AskMetrics(
            answer = answer,
            promptTokens = usage.promptTokens,
            completionTokens = usage.completionTokens,
            totalTokens = usage.totalTokens,
            elapsedMs = elapsedMs,
            costUsd = cost
        )
    }
}
