package ai.challenge.week2day5.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week2day5.data.AskResult
import ai.challenge.week2day5.data.DeepSeekRepository
import ai.challenge.week2day5.data.FactRepository
import ai.challenge.week2day5.data.Message
import ai.challenge.week2day5.data.costUsd
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: DeepSeekRepository = DeepSeekRepository(),
    private val factRepository: FactRepository = FactRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Прогон серии вопросов через все стратегии для пункта меню «Сравнить стратегии». */
    private val comparer = StrategyComparer(repository)

    /**
     * История диалога в ролях API (user/assistant), которую агент помнит и отправляет
     * с каждым запросом. Хранится отдельно от UI-ленты, чтобы ошибки и мета-сообщения
     * (например, пересказ) не попадали в контекст модели.
     */
    private val history = mutableListOf<Message>()

    /** Размер окна для стратегии Sliding Window — сколько последних пар отправляем. */
    private val slidingWindowPairs = 5

    /**
     * Блок «sticky facts» (ключ→значение) для одноимённой стратегии. LinkedHashMap —
     * сохраняет порядок добавления и дедуплицирует по ключу (новое значение перезаписывает старое).
     */
    private val facts = linkedMapOf<String, String>()

    /** Сколько последних пар отправляем в стратегии Sticky Facts (вместе с блоком фактов). */
    private val stickyFactsPairs = 1

    /**
     * Одна ветка диалога для стратегии Branching. [history] — реплики в ролях API
     * (контекст ветки), [feed] — лента для показа (с usage и сообщениями об ошибках).
     * Разделение — по той же причине, что и общий [history]/[messages].
     */
    private class Branch(
        val id: Int,
        var title: String,
        val isMain: Boolean,
        val history: MutableList<Message> = mutableListOf(),
        val feed: MutableList<ChatMessage> = mutableListOf()
    )

    /** Все существующие ветки в стратегии Branching (первая — основная). */
    private val branches = mutableListOf<Branch>()

    /** Активная ветка в стратегии Branching. */
    private var currentBranch: Branch? = null

    /** Счётчик для выдачи уникальных id веткам. */
    private var nextBranchId = 0

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onSend() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isLoading) return

        // Стратегия Branching обрабатывается отдельно: сначала роутинг по веткам.
        if (_uiState.value.strategy == ContextStrategy.BRANCHING) {
            sendBranching(prompt)
            return
        }

        // Запоминаем реплику пользователя в контексте, добавляем в ленту, включаем загрузку.
        history += Message(role = "user", content = prompt)
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(prompt, isUser = true),
                input = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            // Состав запроса зависит от выбранной стратегии управления контекстом.
            // Sliding Window: только последние 5 пар + текущий вопрос.
            // Sticky Facts: блок фактов + только последняя пара + текущий вопрос.
            // Остальные стратегии пока шлют всю историю.
            val request = when (_uiState.value.strategy) {
                ContextStrategy.SLIDING_WINDOW ->
                    history.takeLast(slidingWindowPairs * 2 + 1)

                ContextStrategy.STICKY_FACTS -> {
                    val recent = history.takeLast(stickyFactsPairs * 2 + 1)
                    if (facts.isEmpty()) recent else listOf(factsMessage()) + recent
                }

                else -> history.toList()
            }
            val (reply, usage) = runCatching { repository.ask(request) }.fold(
                onSuccess = { r ->
                    // Ответ модели сохраняем в контексте только при успехе.
                    history += Message(role = "assistant", content = r.content)
                    r.content to r.toTokenUsage()
                },
                onFailure = { e ->
                    ("⚠️ " + (e.message ?: "Не удалось получить ответ.")) to null
                }
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(reply, isUser = false, usage = usage),
                    isLoading = false
                )
            }

            // После успешного ответа в режиме Sticky Facts отдельным запросом
            // извлекаем важные факты из обмена и пополняем блок facts.
            if (usage != null && _uiState.value.strategy == ContextStrategy.STICKY_FACTS) {
                extractFacts(question = prompt, answer = reply)
            }
        }
    }

    /**
     * Отправка в стратегии Branching. Сначала роутер решает, продолжает ли вопрос тему
     * текущей ветки или нужно завести новую (всегда ответвлённую от основной), затем
     * запрос уходит с контекстом целевой ветки.
     */
    private fun sendBranching(prompt: String) {
        _uiState.update { it.copy(input = "", isLoading = true) }

        viewModelScope.launch {
            // Токены служебного запроса-роутера (null для первого сообщения — роутинга нет).
            var routingUsage: TokenUsage? = null
            // Определяем целевую ветку.
            val target: Branch = if (branches.isEmpty()) {
                // Первое сообщение — заводим основную ветку.
                Branch(
                    id = nextBranchId++,
                    title = branchTitleFrom(prompt),
                    isMain = true
                ).also {
                    branches += it
                    currentBranch = it
                }
            } else {
                val route = routeBranch(prompt)
                routingUsage = route.usage
                when (val decision = route.decision) {
                    // Вопрос подходит к существующей ветке — переключаемся на неё.
                    is BranchDecision.Existing ->
                        (branches.firstOrNull { it.id == decision.id } ?: currentBranch!!)
                            .also { currentBranch = it }

                    // Новая тема — заводим ветку, всегда ответвлённую от основной.
                    is BranchDecision.New -> Branch(
                        id = nextBranchId++,
                        title = decision.title,
                        isMain = false
                    ).also {
                        branches += it
                        currentBranch = it
                    }
                }
            }

            // Реплика пользователя — в контекст и ленту целевой ветки.
            target.history += Message(role = "user", content = prompt)
            target.feed += ChatMessage(prompt, isUser = true)
            _uiState.update {
                it.copy(
                    messages = displayFeed(target),
                    branches = renderBranches(),
                    currentBranchId = target.id
                )
            }

            val (reply, usage) = runCatching { repository.ask(target.contextForSend()) }.fold(
                onSuccess = { r ->
                    target.history += Message(role = "assistant", content = r.content)
                    r.content to r.toTokenUsage()
                },
                onFailure = { e ->
                    ("⚠️ " + (e.message ?: "Не удалось получить ответ.")) to null
                }
            )
            target.feed += ChatMessage(
                reply,
                isUser = false,
                usage = usage,
                routingUsage = routingUsage
            )
            _uiState.update {
                it.copy(messages = displayFeed(target), isLoading = false)
            }
        }
    }

    /** Контекст ветки для отправки: основная — своя история; дочерняя — история основной + своя. */
    private fun Branch.contextForSend(): List<Message> =
        if (isMain) history.toList() else mainBranch().history + history

    /**
     * Лента для показа: основная — своя; дочерняя — сначала сообщения основной ветки,
     * затем собственный диалог ветки. Так в дочерней ветке виден её «корень».
     */
    private fun displayFeed(branch: Branch): List<ChatMessage> =
        if (branch.isMain) branch.feed.toList() else mainBranch().feed + branch.feed

    /** Основная ветка (заводится первым сообщением, существует всё время работы стратегии). */
    private fun mainBranch(): Branch = branches.first { it.isMain }

    /** Решение роутера: перейти в существующую ветку (по id) или завести новую с названием темы. */
    private sealed interface BranchDecision {
        data class Existing(val id: Int) : BranchDecision
        data class New(val title: String) : BranchDecision
    }

    /** Результат роутинга: решение + расход токенов служебного запроса (null, если запроса не было). */
    private data class RouteResult(val decision: BranchDecision, val usage: TokenUsage?)

    /**
     * Служебный запрос к LLM: к какой из существующих веток относится новый вопрос по смыслу,
     * либо это новая тема. Модель видит все ветки целиком (заголовок + весь диалог), потому что
     * заголовок может расходиться с формулировкой вопроса, а суть совпадать. Если вопрос подходит
     * к существующей ветке (любой, не только текущей) — переключаемся на неё и не плодим дубли.
     * Модель отвечает строго «BRANCH: <id>» либо «NEW: <название>». Ошибки роутинга трактуем
     * как продолжение текущей ветки.
     */
    private suspend fun routeBranch(prompt: String): RouteResult {
        val current = currentBranch
            ?: return RouteResult(BranchDecision.New(branchTitleFrom(prompt)), null)
        val request = listOf(
            Message(
                role = "user",
                content = "Есть ветки разговора, у каждой свой номер, тема и диалог. Определи, " +
                    "к какой ветке по смыслу относится новый вопрос пользователя (учитывай весь " +
                    "диалог ветки, а не только заголовок), либо это совсем новая тема.\n\n" +
                    "Ветки:\n${branchesForRouter()}\n\n" +
                    "Новый вопрос пользователя: «$prompt».\n\n" +
                    "Ответь строго одной строкой: либо «BRANCH: <номер существующей ветки>», " +
                    "либо «NEW: <краткое название новой темы из 2–4 слов>». Без пояснений."
            )
        )
        return runCatching { repository.ask(request) }
            .map { r -> RouteResult(parseBranchDecision(r.content, prompt), r.toTokenUsage()) }
            .getOrDefault(RouteResult(BranchDecision.Existing(current.id), null))
    }

    /** Список всех веток с их номерами, темами и диалогом — для запроса роутера. */
    private fun branchesForRouter(): String =
        branches.joinToString("\n\n") { b ->
            "[${b.id}] «${b.title}»\n${branchTranscript(b)}"
        }

    /** Текстовая расшифровка диалога ветки для роутера (все её реплики, по ролям). */
    private fun branchTranscript(branch: Branch): String =
        if (branch.history.isEmpty()) {
            "(пока пусто)"
        } else {
            branch.history.joinToString("\n") { msg ->
                val who = if (msg.role == "user") "Пользователь" else "Ассистент"
                "$who: ${msg.content}"
            }
        }

    /** Разбирает ответ роутера в [BranchDecision]. */
    private fun parseBranchDecision(raw: String, prompt: String): BranchDecision {
        val clean = raw.trim().removeSurrounding("\"").trim()
        if (clean.startsWith("NEW", ignoreCase = true)) {
            val title = clean.substringAfter(':', "").trim().removeSurrounding("\"").trim()
            return BranchDecision.New(title.ifEmpty { branchTitleFrom(prompt) })
        }
        if (clean.startsWith("BRANCH", ignoreCase = true)) {
            val id = clean.substringAfter(':', "").trim().toIntOrNull()
            val existing = branches.firstOrNull { it.id == id }
            if (existing != null) return BranchDecision.Existing(existing.id)
        }
        // Ответ не распознан — безопасно остаёмся в текущей ветке.
        return BranchDecision.Existing(currentBranch?.id ?: branches.first().id)
    }

    /** Запасное короткое название ветки из текста вопроса (когда нет названия от роутера). */
    private fun branchTitleFrom(prompt: String): String {
        val clean = prompt.trim().removeSurrounding("\"").trim()
        return if (clean.length <= 40) clean else clean.take(40).trimEnd() + "…"
    }

    /** Текущий список веток в UI-модели. */
    private fun renderBranches(): List<BranchUi> =
        branches.map { BranchUi(it.id, it.title, it.isMain) }

    /**
     * Ручное переключение активной ветки из меню. Лента заменяется на сообщения
     * выбранной ветки, дальнейший диалог продолжается в ней.
     */
    fun onSelectBranch(id: Int) {
        if (_uiState.value.isLoading) return
        val branch = branches.firstOrNull { it.id == id } ?: return
        currentBranch = branch
        _uiState.update {
            it.copy(messages = displayFeed(branch), currentBranchId = branch.id)
        }
    }

    /** Системное сообщение с текущим блоком фактов для подмешивания в запрос Sticky Facts. */
    private fun factsMessage(): Message = Message(
        role = "system",
        content = "Известные факты о пользователе и диалоге:\n" +
            facts.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }
    )

    /**
     * Отдельный служебный запрос к LLM: извлекает из последнего обмена важные факты
     * в формате «ключ: значение» и мерджит их в блок [facts]. Ошибки извлечения
     * глотаются — они не должны ломать основной ответ. Сам запрос и его результат
     * в history не попадают.
     */
    private suspend fun extractFacts(question: String, answer: String) {
        val request = listOf(
            Message(
                role = "user",
                content = "Извлеки из обмена ниже важные факты, которые стоит запомнить " +
                    "о теме разговора. " +
                    "Верни их строками в формате «ключ: значение», по одному факту на строку, " +
                    "без нумерации, маркеров и пояснений. Если важных фактов нет — верни пустой ответ.\n\n" +
                    "Пользователь: $question\nАссистент: $answer"
            )
        )
        runCatching { repository.ask(request).content }
            .onSuccess { raw ->
                val parsed = parseFacts(raw)
                if (parsed.isEmpty()) return
                parsed.forEach { (key, value) -> facts[key] = value }
                _uiState.update {
                    it.copy(facts = facts.map { e -> Fact(e.key, e.value) })
                }
            }
    }

    /** Разбирает ответ модели в пары «ключ → значение» (по строкам формата «ключ: значение»). */
    private fun parseFacts(raw: String): List<Pair<String, String>> =
        raw.lines().mapNotNull { line ->
            val clean = line.trim().removePrefix("-").trim()
            val idx = clean.indexOf(':')
            if (idx <= 0) return@mapNotNull null
            val key = clean.take(idx).trim()
            val value = clean.substring(idx + 1).trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }

    /** Переводит usage из ответа API в UI-модель с подсчитанной стоимостью. */
    private fun AskResult.toTokenUsage(): TokenUsage? =
        usage?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens,
                costUsd = it.costUsd()
            )
        }

    /** Очищает контекст диалога, ленту чата, блок фактов и ветки. */
    fun onClearContext() {
        if (_uiState.value.isLoading) return
        history.clear()
        facts.clear()
        resetBranches()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                input = "",
                facts = emptyList(),
                branches = emptyList(),
                currentBranchId = null
            )
        }
    }

    /** Сбрасывает всё состояние веток стратегии Branching. */
    private fun resetBranches() {
        branches.clear()
        currentBranch = null
        nextBranchId = 0
    }

    /** Очищает только блок sticky-фактов (кнопка в окне просмотра фактов). */
    fun onClearFacts() {
        facts.clear()
        _uiState.update { it.copy(facts = emptyList()) }
    }

    /**
     * Переключает стратегию управления контекстом. По общему правилу выбор любой
     * стратегии очищает контекст и ленту. Для стратегий 2 и 3 этим всё и
     * ограничивается — их логика будет добавлена в следующих итерациях.
     */
    fun onSelectStrategy(strategy: ContextStrategy) {
        if (_uiState.value.isLoading) return
        history.clear()
        facts.clear()
        resetBranches()
        _uiState.update {
            it.copy(
                strategy = strategy,
                messages = emptyList(),
                input = "",
                facts = emptyList(),
                branches = emptyList(),
                currentBranchId = null
            )
        }
    }

    /** Берём только сам вопрос: значимый текст без обрамляющих кавычек. */
    private fun cleanQuestion(raw: String): String =
        raw.trim().removeSurrounding("\"").trim()

    /**
     * Текущий контекст диалога для мета-действий (пересказ, вопрос-продолжение).
     * В стратегии Branching это контекст активной ветки, иначе — общая история.
     */
    private fun currentContext(): List<Message> =
        if (_uiState.value.strategy == ContextStrategy.BRANCHING) {
            currentBranch?.contextForSend() ?: emptyList()
        } else {
            history.toList()
        }

    /**
     * Вариант 1: самостоятельный вопрос «с нуля». Контекст диалога не читается и не
     * меняется — служебный запрос и ответ модели в history не попадают. Результат
     * кладётся в поле ввода, отправку пользователь делает сам.
     */
    fun onGenerateQuestion() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val request = listOf(
                Message(
                    role = "user",
                    content = "Придумай один интересный познавательный вопрос, на который " +
                        "ты можешь дать содержательный ответ. Верни только сам вопрос, " +
                        "одной строкой, без пояснений и без кавычек."
                )
            )
            runCatching { repository.ask(request) }
                .onSuccess { r ->
                    _uiState.update { it.copy(input = cleanQuestion(r.content), isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                "⚠️ " + (e.message ?: "Не удалось сгенерировать вопрос."),
                                isUser = false
                            ),
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * Генерация случайного факта: берём факт из публичного API (на английском),
     * просим модель перевести его на русский и кладём результат в поле ввода.
     * Контекст диалога не читается и не меняется; отправку пользователь делает сам.
     */
    fun onGenerateFact() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            runCatching {
                val fact = factRepository.randomFact()
                val request = listOf(
                    Message(
                        role = "user",
                        content = "Переведи следующий факт на русский язык. Верни только " +
                            "перевод, без пояснений и без кавычек:\n\n$fact"
                    )
                )
                repository.ask(request).content
            }
                .onSuccess { translated ->
                    _uiState.update { it.copy(input = cleanQuestion(translated), isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                "⚠️ " + (e.message ?: "Не удалось получить факт."),
                                isUser = false
                            ),
                            isLoading = false
                        )
                    }
                }
        }
    }

    /**
     * Вариант 2: вопрос-продолжение на основе текущей переписки. История читается,
     * но служебный запрос и ответ в неё НЕ добавляются — контекст агента не меняется.
     * Результат кладётся в поле ввода.
     */
    fun onGenerateContextualQuestion() {
        if (currentContext().isEmpty() || _uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val request = currentContext() + Message(
                role = "user",
                content = "На основе нашего разговора выше предложи один логичный " +
                    "вопрос-продолжение, на который ты можешь дать содержательный ответ. " +
                    "Верни только сам вопрос, одной строкой, без пояснений и без кавычек."
            )
            runCatching { repository.ask(request) }
                .onSuccess { r ->
                    _uiState.update { it.copy(input = cleanQuestion(r.content), isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                "⚠️ " + (e.message ?: "Не удалось сгенерировать вопрос."),
                                isUser = false
                            ),
                            isLoading = false
                        )
                    }
                }
        }
    }



    /**
     * Сравнение стратегий: генерирует серию связанных вопросов и прогоняет её через все
     * три стратегии, собирая токены/деньги/время. Текущий чат и история не затрагиваются —
     * прогон идёт в изолированном состоянии внутри [comparer]. Результат кладётся в
     * [ChatUiState.comparison] (его показывает и пишет в файл экран).
     */
    fun onCompareStrategies() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            runCatching {
                val questions = comparer.generateQuestions()
                val metrics = comparer.run(questions)
                ComparisonResult(comparer.buildReport(questions, metrics), metrics)
            }.fold(
                onSuccess = { result ->
                    _uiState.update { it.copy(comparison = result, isLoading = false) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            messages = it.messages + ChatMessage(
                                "⚠️ " + (e.message ?: "Не удалось выполнить сравнение."),
                                isUser = false
                            ),
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    /** Закрывает диалог сравнения. */
    fun onDismissComparison() {
        _uiState.update { it.copy(comparison = null) }
    }

    /**
     * Просит модель кратко пересказать текущий разговор. Резюме показывается обычным
     * сообщением ассистента; инструкция-запрос и сам пересказ в контекст не добавляются.
     */
    fun onSummarize() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val request = currentContext() + Message(
                role = "user",
                content = "Кратко и по пунктам перескажи суть нашего разговора выше."
            )
            val (summary, usage) = runCatching { repository.ask(request) }.fold(
                onSuccess = { r -> r.content to r.toTokenUsage() },
                onFailure = { e ->
                    ("⚠️ " + (e.message ?: "Не удалось составить пересказ.")) to null
                }
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(summary, isUser = false, usage = usage),
                    isLoading = false
                )
            }
        }
    }
}
