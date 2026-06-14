package ai.challenge.week2day4.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Обёртка над публичным API случайных фактов (uselessfacts).
 * Без авторизации, поддерживает языки en/de. Факты могут быть выдуманными.
 * Документация: https://uselessfacts.jsph.pl
 */
class FactRepository {
    private val endpoint = "https://uselessfacts.jsph.pl/api/v2/facts/random?language=en"

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /** Возвращает текст случайного факта. Бросает Exception при ошибке сети/API. */
    suspend fun randomFact(): String {
        val response: HttpResponse = client.get(endpoint)
        if (!response.status.isSuccess()) {
            throw RuntimeException("Ошибка API фактов (${response.status.value})")
        }
        return response.body<FactResponse>().text.trim()
    }
}
