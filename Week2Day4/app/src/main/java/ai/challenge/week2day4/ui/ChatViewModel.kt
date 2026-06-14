package ai.challenge.week2day4.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week2day4.data.AskResult
import ai.challenge.week2day4.data.DeepSeekRepository
import ai.challenge.week2day4.data.FactRepository
import ai.challenge.week2day4.data.Message
import ai.challenge.week2day4.data.costUsd
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

    /**
     * История диалога в ролях API (user/assistant), которую агент помнит и отправляет
     * с каждым запросом. Хранится отдельно от UI-ленты, чтобы ошибки и мета-сообщения
     * (например, пересказ) не попадали в контекст модели.
     */
    private val history = mutableListOf<Message>()

    /** Текущее накопленное summary свёрнутой части диалога (null, пока свёрток не было). */
    private var summary: String? = null

    /** Сколько первых сообщений history уже учтено в summary. */
    private var summarizedCount: Int = 0

    /**
     * Что реально уходит в модель: системная инструкция (роль «хранитель фактов»),
     * затем либо вся история (пока свёрток не было), либо summary свёрнутой части +
     * сообщения, ещё не вошедшие в summary.
     */
    private fun buildContext(): List<Message> = buildList {
        add(Message("system", SYSTEM_PROMPT))
        summary?.let { add(Message("system", "Краткое содержание ранее сохранённых сообщений:\n$it")) }
        addAll(if (summary != null) history.drop(summarizedCount) else history)
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onSend() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isLoading) return

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
            // Отправляем не всю историю, а summary свёрнутой части + ещё не свёрнутый хвост.
            var success = false
            val (reply, usage) = runCatching { repository.ask(buildContext()) }.fold(
                onSuccess = { r ->
                    // Ответ модели сохраняем в контексте только при успехе.
                    history += Message(role = "assistant", content = r.content)
                    success = true
                    r.content to r.toTokenUsage()
                },
                onFailure = { e ->
                    ("⚠️ " + (e.message ?: "Не удалось получить ответ.")) to null
                }
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(reply, isUser = false, usage = usage)
                )
            }

            // Каждые SUMMARY_EVERY_EXCHANGES обменов сворачиваем накопившийся хвост в summary.
            val unsummarizedExchanges = (history.size - summarizedCount) / 2
            if (success && unsummarizedExchanges >= SUMMARY_EVERY_EXCHANGES) {
                summarizeContext()
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Рекурсивно сворачивает контекст: прежнее summary + ещё не свёрнутые реплики
     * объединяются в новое summary. Обновляет состояние свёртки и показывает
     * пометку с текстом текущего summary в ленте чата.
     */
    private suspend fun summarizeContext() {
        val request = buildList {
            summary?.let { add(Message("system", "Текущее краткое содержание разговора:\n$it")) }
            addAll(history.drop(summarizedCount))
            add(
                Message(
                    role = "user",
                    content = "Обнови краткое содержание всего разговора выше: кратко и по " +
                            "пунктам, сохрани все важные факты и детали. Верни только сам пересказ."
                )
            )
        }
        // При ошибке свёртки контекст не трогаем — следующий запрос просто уйдёт
        // прежним summary + хвостом (summary/summarizedCount остаются как были).
        runCatching { repository.ask(request) }.onSuccess { r ->
            summary = r.content
            summarizedCount = history.size
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(
                        "📝 Контекст свёрнут. Текущее краткое содержание:\n\n${r.content}",
                        isUser = false,
                        usage = r.toTokenUsage()
                    )
                )
            }
        }
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

    /** Очищает контекст диалога, summary и ленту чата. */
    fun onClearContext() {
        if (_uiState.value.isLoading) return
        history.clear()
        summary = null
        summarizedCount = 0
        _uiState.update { it.copy(messages = emptyList(), input = "") }
    }

    /** Берём только сам вопрос: значимый текст без обрамляющих кавычек. */
    private fun cleanQuestion(raw: String): String =
        raw.trim().removeSurrounding("\"").trim()

    /**
     * Достаёт случайный факт из публичного API (на английском), переводит его на
     * русский через модель и кладёт результат в поле ввода. Контекст диалога не
     * читается и не меняется — служебный перевод в history не попадает.
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
        if (history.isEmpty() || _uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val request = history + Message(
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
     * Просит модель кратко пересказать текущий разговор. Резюме показывается обычным
     * сообщением ассистента; инструкция-запрос и сам пересказ в контекст не добавляются.
     */
    fun onSummarize() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val request = history + Message(
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

    private companion object {
        /** Через сколько обменов (вопрос+ответ) сворачивать историю в summary. */
        const val SUMMARY_EVERY_EXCHANGES = 5

        /**
         * Роль модели: пользователь присылает факты, а не вопросы. Модель должна просто
         * запомнить факт и коротко подтвердить сохранение, не отвечая развёрнуто.
         */
        const val SYSTEM_PROMPT =
            "Пользователь присылает тебе факты — по одному в каждом сообщении. " +
                "Твоя задача — запомнить факт, а не отвечать на него. В ответ дай только " +
                "короткое подтверждение, и можешь дать небольшую информацию по факту"
    }
}
