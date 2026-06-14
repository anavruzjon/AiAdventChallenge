package ai.challenge.week2day4.data

import kotlinx.serialization.Serializable

/** Ответ uselessfacts API. Нужен только text. */
@Serializable
data class FactResponse(
    val text: String = ""
)
