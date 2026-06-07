package ai.challenge.week0day5.data

/** Уровень «силы» модели для сравнения. */
enum class Tier(val label: String, val emoji: String) {
    WEAK("Слабая", "🐢"),
    MEDIUM("Средняя", "🚶"),
    STRONG("Сильная", "🚀")
}

/**
 * Описание одной модели HuggingFace Router.
 * Цены — приблизительные ($ за 1M токенов) из каталога Inference Providers,
 * нужны лишь для оценочного расчёта стоимости (HF Router не возвращает цену в ответе).
 */
data class LlmModel(
    val tier: Tier,
    val id: String,             // model id для HF Router (например "meta-llama/Llama-3.2-1B-Instruct")
    val displayName: String,
    val inputPricePerM: Double, // $/1M входных токенов (прибл.)
    val outputPricePerM: Double // $/1M выходных токенов (прибл.)
)

/**
 * Три модели «из начала / середины / конца» каталога — слабая, средняя, сильная.
 *
 * Доступность конкретных id у Inference Providers иногда меняется. Если какая-то
 * модель не обслуживается — замените id здесь (например на Qwen/Qwen2.5-*-Instruct
 * или deepseek-ai/DeepSeek-V3-0324). Это единственное место с конфигом моделей.
 */
val MODEL_CATALOG: List<LlmModel> = listOf(
    LlmModel(
        tier = Tier.WEAK,
        id = "Qwen/Qwen3-4B-Instruct-2507",
        displayName = "Qwen3 · 4B",
        inputPricePerM = 0.02,
        outputPricePerM = 0.05
    ),
    LlmModel(
        tier = Tier.MEDIUM,
        id = "meta-llama/Llama-3.1-8B-Instruct",
        displayName = "Llama 3.1 · 8B",
        inputPricePerM = 0.03,
        outputPricePerM = 0.05
    ),
    LlmModel(
        tier = Tier.STRONG,
        id = "meta-llama/Llama-3.3-70B-Instruct",
        displayName = "Llama 3.3 · 70B",
        inputPricePerM = 0.30,
        outputPricePerM = 0.40
    )
)
