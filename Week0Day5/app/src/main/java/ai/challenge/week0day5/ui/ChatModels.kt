package ai.challenge.week0day5.ui

import ai.challenge.week0day5.data.LlmModel

/** Статус запроса к одной модели. */
enum class Status { LOADING, DONE, ERROR }

/** Результат запроса к одной модели + замеренные метрики. */
data class ModelResult(
    val model: LlmModel,
    val status: Status = Status.LOADING,
    val answer: String = "",
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val elapsedMs: Long = 0,
    val costUsd: Double = 0.0,
    val error: String? = null
)

/** Состояние экрана сравнения моделей. */
data class CompareUiState(
    val input: String = "",
    val isRunning: Boolean = false,
    val results: List<ModelResult> = emptyList(), // по одной карточке на уровень
    val verdict: String? = null                   // короткий итоговый вывод
)

/** Форматирует оценочную стоимость: очень малые суммы показываем подробнее. */
fun formatCost(cost: Double): String = when {
    cost <= 0.0 -> "$0"
    cost < 0.0001 -> "<$0.0001"
    cost < 0.01 -> String.format("$%.5f", cost)
    else -> String.format("$%.4f", cost)
}
