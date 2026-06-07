package ai.challenge.week0day3.data

import ai.challenge.week0day3.BuildConfig
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
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
import kotlinx.serialization.json.JsonElement

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
        // JSON в теле запроса и ответа форматируется с отступами и переносами строк.
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("DeepSeek", prettifyBody(message))
                }
            }
            level = LogLevel.BODY
        }
    }

    private val prettyJson = Json { prettyPrint = true }

    /**
     * Форматирует JSON-тело внутри лог-сообщения Ktor: добавляет отступы/переносы
     * и превращает экранированные `\n` в настоящие переводы строки. Если тело не
     * является валидным JSON (или маркеры отсутствуют), сообщение возвращается
     * почти без изменений — только с заменой `\n` на перевод строки.
     */
    private fun prettifyBody(message: String): String {
        val startMarker = "BODY START"
        val endMarker = "BODY END"
        val startIdx = message.indexOf(startMarker)
        val endIdx = message.indexOf(endMarker)
        if (startIdx == -1 || endIdx == -1 || endIdx < startIdx) {
            return message.replace("\\n", "\n")
        }
        val contentStart = startIdx + startMarker.length
        val rawBody = message.substring(contentStart, endIdx).trim()
        val prettyBody = try {
            val element = Json.parseToJsonElement(rawBody)
            prettyJson.encodeToString(JsonElement.serializer(), element)
        } catch (e: Exception) {
            rawBody
        }.replace("\\n", "\n")
        return message.substring(0, contentStart) + "\n" + prettyBody + "\n" +
            message.substring(endIdx)
    }

    /**
     * Отправляет вопрос модели и возвращает текст ответа. Бросает Exception с читаемым сообщением.
     * Необязательный [system] добавляет системное сообщение перед сообщением пользователя.
     */
    suspend fun ask(prompt: String, system: String? = null): String {
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
                    messages = messages
                )
            )
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Ошибка API (${response.status.value}): ${response.bodyAsText()}")
        }

        val body: DeepSeekResponse = response.body()
        return body.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw RuntimeException("Пустой ответ от модели.")
    }
}
