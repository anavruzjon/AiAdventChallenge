package ai.challenge.week2day5.ui

import ai.challenge.week2day5.data.DeepSeekRepository
import ai.challenge.week2day5.data.Message
import ai.challenge.week2day5.data.costUsd
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Прогоняет одну и ту же серию вопросов через все три стратегии управления контекстом
 * и собирает по каждой суммарные метрики (токены, деньги, время, число вызовов API).
 *
 * Работает в полной изоляции от живого чата [ChatViewModel]: держит собственное
 * локальное состояние (история/факты/ветки) на каждый прогон, ничего общего с UI
 * не делит. Логика сборки контекста по стратегиям намеренно зеркалит [ChatViewModel]
 * (см. ссылки в комментариях) — при изменении правил стратегии править оба места.
 */
class StrategyComparer(private val repository: DeepSeekRepository) {

    // Параметры стратегий — те же значения, что в ChatViewModel.
    private val slidingWindowPairs = 5
    private val stickyFactsPairs = 1

    /**
     * Просит модель придумать [count] связанных, нарастающих по контексту вопросов на
     * общую тему (по одному в строке) — чтобы прогон был многоходовым и разница между
     * стратегиями реально проявилась. Этот вызов в метрики стратегий НЕ входит.
     * При ошибке/пустом ответе возвращает запасной фиксированный набор.
     */
    suspend fun generateQuestions(count: Int = 6): List<String> {
        val request = listOf(
            Message(
                role = "user",
                content = "Придумай $count связанных вопросов на одну общую тему, которые " +
                    "образуют единый разговор: каждый следующий опирается на предыдущие и " +
                    "уточняет тему. Верни только сами вопросы, по одному на строку, без " +
                    "нумерации, маркеров и пояснений."
            )
        )
        val parsed = runCatching { repository.ask(request).content }
            .map { raw ->
                raw.lines()
                    .map { it.trim().removePrefix("-").trim().trim('"') }
                    .filter { it.isNotEmpty() }
            }
            .getOrDefault(emptyList())
        return parsed.ifEmpty { fallbackQuestions }.take(count)
    }

    /** Запасной набор связанных вопросов на случай сбоя генерации. */
    private val fallbackQuestions = listOf(
        "Что такое чёрная дыра?",
        "А как она образуется?",
        "Что произойдёт, если в неё что-то упадёт?",
        "Можно ли увидеть чёрную дыру напрямую?",
        "Чем сверхмассивная чёрная дыра отличается от обычной?",
        "Есть ли чёрная дыра в центре нашей галактики?"
    )

    /** Прогоняет серию [questions] через все стратегии и возвращает метрики по каждой. */
    suspend fun run(questions: List<String>): List<StrategyMetrics> =
        ContextStrategy.entries.map { strategy ->
            when (strategy) {
                ContextStrategy.SLIDING_WINDOW -> runSlidingWindow(questions)
                ContextStrategy.STICKY_FACTS -> runStickyFacts(questions)
                ContextStrategy.BRANCHING -> runBranching(questions)
            }
        }

    /**
     * Аккумулятор метрик одной стратегии. [add] суммирует один ответ API
     * (usage может быть null — тогда учитывается только сам факт вызова).
     */
    private class Acc(val strategy: ContextStrategy) {
        private var apiCalls = 0
        private var promptTokens = 0
        private var completionTokens = 0
        private var totalTokens = 0
        private var costUsd = 0.0

        fun add(usage: ai.challenge.week2day5.data.Usage?) {
            apiCalls++
            usage ?: return
            promptTokens += usage.promptTokens
            completionTokens += usage.completionTokens
            totalTokens += usage.totalTokens
            costUsd += usage.costUsd()
        }

        fun toMetrics(elapsedMs: Long) = StrategyMetrics(
            strategy = strategy,
            apiCalls = apiCalls,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            costUsd = costUsd,
            elapsedMs = elapsedMs
        )
    }

    /** Замеряет общее время прогона стратегии (в мс) вокруг [block]. */
    private suspend inline fun timed(acc: Acc, block: (Acc) -> Unit): StrategyMetrics {
        val start = System.nanoTime()
        block(acc)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        return acc.toMetrics(elapsedMs)
    }

    /** Зеркало ChatViewModel.onSend для SLIDING_WINDOW: последние N пар + текущий вопрос. */
    private suspend fun runSlidingWindow(questions: List<String>): StrategyMetrics {
        val history = mutableListOf<Message>()
        return timed(Acc(ContextStrategy.SLIDING_WINDOW)) { acc ->
            for (q in questions) {
                history += Message(role = "user", content = q)
                val request = history.takeLast(slidingWindowPairs * 2 + 1)
                val result = runCatching { repository.ask(request) }.getOrNull()
                acc.add(result?.usage)
                if (result != null) history += Message(role = "assistant", content = result.content)
            }
        }
    }

    /**
     * Зеркало ChatViewModel для STICKY_FACTS: блок фактов + последняя пара + текущий вопрос,
     * после каждого ответа — служебный вызов извлечения фактов (его токены/время тоже в цене).
     */
    private suspend fun runStickyFacts(questions: List<String>): StrategyMetrics {
        val history = mutableListOf<Message>()
        val facts = linkedMapOf<String, String>()
        return timed(Acc(ContextStrategy.STICKY_FACTS)) { acc ->
            for (q in questions) {
                history += Message(role = "user", content = q)
                val recent = history.takeLast(stickyFactsPairs * 2 + 1)
                val request = if (facts.isEmpty()) recent else listOf(factsMessage(facts)) + recent
                val result = runCatching { repository.ask(request) }.getOrNull()
                acc.add(result?.usage)
                if (result == null) continue
                history += Message(role = "assistant", content = result.content)

                // Служебный запрос: извлекаем факты из обмена и мерджим в блок.
                val extract = runCatching { repository.ask(extractFactsRequest(q, result.content)) }
                    .getOrNull()
                acc.add(extract?.usage)
                extract?.let { parseFacts(it.content).forEach { (k, v) -> facts[k] = v } }
            }
        }
    }

    /**
     * Зеркало ChatViewModel.sendBranching: первый вопрос — основная ветка без роутинга,
     * далее служебный вызов-роутер + основной запрос с контекстом целевой ветки.
     */
    private suspend fun runBranching(questions: List<String>): StrategyMetrics {
        val branches = mutableListOf<CmpBranch>()
        var nextId = 0
        var current: CmpBranch? = null
        return timed(Acc(ContextStrategy.BRANCHING)) { acc ->
            for (q in questions) {
                val target: CmpBranch = if (branches.isEmpty()) {
                    CmpBranch(nextId++, branchTitleFrom(q), isMain = true)
                        .also { branches += it; current = it }
                } else {
                    val route = runCatching { repository.ask(routerRequest(branches, q)) }.getOrNull()
                    acc.add(route?.usage)
                    val decision = route?.let { parseBranchDecision(it.content, branches, current, q) }
                        ?: BranchTarget.Existing(current!!.id)
                    when (decision) {
                        is BranchTarget.Existing ->
                            (branches.firstOrNull { it.id == decision.id } ?: current!!)
                                .also { current = it }
                        is BranchTarget.New ->
                            CmpBranch(nextId++, decision.title, isMain = false)
                                .also { branches += it; current = it }
                    }
                }

                target.history += Message(role = "user", content = q)
                val request = if (target.isMain) {
                    target.history.toList()
                } else {
                    branches.first { it.isMain }.history + target.history
                }
                val result = runCatching { repository.ask(request) }.getOrNull()
                acc.add(result?.usage)
                if (result != null) {
                    target.history += Message(role = "assistant", content = result.content)
                }
            }
        }
    }

    /** Локальная ветка для прогона Branching (только контекст, без UI-ленты). */
    private class CmpBranch(
        val id: Int,
        val title: String,
        val isMain: Boolean,
        val history: MutableList<Message> = mutableListOf()
    )

    private sealed interface BranchTarget {
        data class Existing(val id: Int) : BranchTarget
        data class New(val title: String) : BranchTarget
    }

    /** Формирует Markdown-отчёт по результатам прогона. */
    fun buildReport(questions: List<String>, metrics: List<StrategyMetrics>): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.appendLine("# Сравнение стратегий управления контекстом")
        sb.appendLine()
        sb.appendLine("- **Дата:** $date")
        sb.appendLine("- **Модель:** deepseek-chat")
        sb.appendLine("- **Вопросов в серии:** ${questions.size}")
        sb.appendLine()
        sb.appendLine("## Серия вопросов")
        sb.appendLine()
        questions.forEachIndexed { i, q -> sb.appendLine("${i + 1}. $q") }
        sb.appendLine()
        sb.appendLine("## Результаты")
        sb.appendLine()
        sb.appendLine("| Стратегия | Вызовов API | Промпт токены | Ответ токены | Всего токенов | Стоимость, \$ | Время, с |")
        sb.appendLine("|---|---:|---:|---:|---:|---:|---:|")
        metrics.forEach { m ->
            sb.appendLine(
                "| ${m.strategy.label} | ${m.apiCalls} | ${m.promptTokens} | " +
                    "${m.completionTokens} | ${m.totalTokens} | " +
                    String.format(Locale.US, "%.6f", m.costUsd) + " | " +
                    String.format(Locale.US, "%.1f", m.elapsedMs / 1000.0) + " |"
            )
        }
        sb.appendLine()
        sb.appendLine("## Выводы")
        sb.appendLine()
        metrics.minByOrNull { it.totalTokens }?.let {
            sb.appendLine("- **Меньше всего токенов:** ${it.strategy.label} (${it.totalTokens})")
        }
        metrics.minByOrNull { it.costUsd }?.let {
            sb.appendLine(
                "- **Дешевле всего:** ${it.strategy.label} (\$" +
                    String.format(Locale.US, "%.6f", it.costUsd) + ")"
            )
        }
        metrics.minByOrNull { it.elapsedMs }?.let {
            sb.appendLine(
                "- **Быстрее всего:** ${it.strategy.label} (" +
                    String.format(Locale.US, "%.1f", it.elapsedMs / 1000.0) + " с)"
            )
        }
        return sb.toString()
    }

    // --- Переиспользуемая логика стратегий (зеркало ChatViewModel) ---

    /** Системное сообщение с блоком фактов — как ChatViewModel.factsMessage. */
    private fun factsMessage(facts: Map<String, String>): Message = Message(
        role = "system",
        content = "Известные факты о пользователе и диалоге:\n" +
            facts.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
    )

    /** Запрос извлечения фактов — как ChatViewModel.extractFacts. */
    private fun extractFactsRequest(question: String, answer: String) = listOf(
        Message(
            role = "user",
            content = "Извлеки из обмена ниже важные факты, которые стоит запомнить " +
                "о теме разговора. " +
                "Верни их строками в формате «ключ: значение», по одному факту на строку, " +
                "без нумерации, маркеров и пояснений. Если важных фактов нет — верни пустой ответ.\n\n" +
                "Пользователь: $question\nАссистент: $answer"
        )
    )

    /** Разбор фактов «ключ: значение» — как ChatViewModel.parseFacts. */
    private fun parseFacts(raw: String): List<Pair<String, String>> =
        raw.lines().mapNotNull { line ->
            val clean = line.trim().removePrefix("-").trim()
            val idx = clean.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = clean.take(idx).trim()
            val value = clean.substring(idx + 1).trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }

    /** Запрос роутера веток — как ChatViewModel.routeBranch. */
    private fun routerRequest(branches: List<CmpBranch>, prompt: String): List<Message> {
        val branchesText = branches.joinToString("\n\n") { b ->
            val transcript = if (b.history.isEmpty()) {
                "(пока пусто)"
            } else {
                b.history.joinToString("\n") { msg ->
                    val who = if (msg.role == "user") "Пользователь" else "Ассистент"
                    "$who: ${msg.content}"
                }
            }
            "[${b.id}] «${b.title}»\n$transcript"
        }
        return listOf(
            Message(
                role = "user",
                content = "Есть ветки разговора, у каждой свой номер, тема и диалог. Определи, " +
                    "к какой ветке по смыслу относится новый вопрос пользователя (учитывай весь " +
                    "диалог ветки, а не только заголовок), либо это совсем новая тема.\n\n" +
                    "Ветки:\n$branchesText\n\n" +
                    "Новый вопрос пользователя: «$prompt».\n\n" +
                    "Ответь строго одной строкой: либо «BRANCH: <номер существующей ветки>», " +
                    "либо «NEW: <краткое название новой темы из 2–4 слов>». Без пояснений."
            )
        )
    }

    /** Разбор ответа роутера — как ChatViewModel.parseBranchDecision. */
    private fun parseBranchDecision(
        raw: String,
        branches: List<CmpBranch>,
        current: CmpBranch?,
        prompt: String
    ): BranchTarget {
        val clean = raw.trim().removeSurrounding("\"").trim()
        if (clean.startsWith("NEW", ignoreCase = true)) {
            val title = clean.substringAfter(':', "").trim().removeSurrounding("\"").trim()
            return BranchTarget.New(title.ifEmpty { branchTitleFrom(prompt) })
        }
        if (clean.startsWith("BRANCH", ignoreCase = true)) {
            val id = clean.substringAfter(':', "").trim().toIntOrNull()
            val existing = branches.firstOrNull { it.id == id }
            if (existing != null) return BranchTarget.Existing(existing.id)
        }
        return BranchTarget.Existing(current?.id ?: branches.first().id)
    }

    /** Короткое название ветки из текста — как ChatViewModel.branchTitleFrom. */
    private fun branchTitleFrom(prompt: String): String {
        val clean = prompt.trim().removeSurrounding("\"").trim()
        return if (clean.length <= 40) clean else clean.take(40).trimEnd() + "…"
    }
}
