package ai.challenge.week2day5.ui

/** Расход токенов и стоимость одного ответа модели (для показа в ленте). */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double
)

/** Одно сообщение в ленте чата. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val usage: TokenUsage? = null,
    /** Токены служебного запроса-роутера, выбравшего ветку (только стратегия Branching). */
    val routingUsage: TokenUsage? = null
)

/** Один сохранённый факт «ключ→значение» для стратегии Sticky Facts. */
data class Fact(val key: String, val value: String)

/** Ветка диалога для стратегии Branching (для списка в меню). */
data class BranchUi(val id: Int, val title: String, val isMain: Boolean)

/**
 * Суммарные метрики прогона одной стратегии в режиме сравнения: сколько всего
 * сделано вызовов API (включая служебные — извлечение фактов, роутер), сколько
 * потрачено токенов, денег и времени на всю серию вопросов.
 */
data class StrategyMetrics(
    val strategy: ContextStrategy,
    val apiCalls: Int,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val costUsd: Double,
    val elapsedMs: Long
)

/** Результат сравнения стратегий: готовый Markdown-отчёт и метрики по каждой стратегии. */
data class ComparisonResult(
    val reportMarkdown: String,
    val metrics: List<StrategyMetrics>
)

/**
 * Стратегия управления контекстом диалога. Определяет, какая часть истории
 * отправляется на LLM. При выборе любой стратегии контекст очищается.
 */
enum class ContextStrategy(val label: String) {
    /** Окно: на LLM уходят только последние 5 пар «сообщение—ответ» + текущий вопрос. */
    SLIDING_WINDOW("Скользящее окно (5 пар)"),

    /**
     * Sticky Facts: рядом с диалогом ведётся отдельный блок фактов (ключ→значение),
     * который пополняется после каждого ответа модели. На LLM уходит блок фактов
     * плюс только последняя пара «сообщение—ответ» + текущий вопрос.
     */
    STICKY_FACTS("Sticky Facts / Память"),

    /**
     * Ветки диалога: первые сообщения — основная ветка. Перед каждым сообщением
     * роутер-классификатор решает, продолжает ли вопрос тему текущей ветки. Если тема
     * новая — создаётся ветка, всегда ответвлённая от основной (контекст = история
     * основной + собственные сообщения ветки). Соседние ветки друг про друга не знают.
     */
    BRANCHING("Ветки диалога")
}

/** Состояние экрана чата. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val input: String = "",
    val strategy: ContextStrategy = ContextStrategy.SLIDING_WINDOW,
    /** Блок «sticky facts» для одноимённой стратегии (key→value). */
    val facts: List<Fact> = emptyList(),
    /** Список существующих веток для стратегии Branching (для меню). */
    val branches: List<BranchUi> = emptyList(),
    /** Id активной ветки в стратегии Branching (null, если веток ещё нет). */
    val currentBranchId: Int? = null,
    /** Результат последнего сравнения стратегий (null — диалог сравнения скрыт). */
    val comparison: ComparisonResult? = null
)
