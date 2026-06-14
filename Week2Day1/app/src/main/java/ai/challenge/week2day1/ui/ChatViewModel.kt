package ai.challenge.week2day1.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week2day1.data.DeepSeekRepository
import ai.challenge.week2day1.data.Message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: DeepSeekRepository = DeepSeekRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /**
     * История диалога в ролях API (user/assistant), которую агент помнит и отправляет
     * с каждым запросом. Хранится отдельно от UI-ленты, чтобы ошибки и мета-сообщения
     * (например, пересказ) не попадали в контекст модели.
     */
    private val history = mutableListOf<Message>()

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
            val reply = try {
                // Отправляем всю историю — каждый запрос продолжает предыдущий.
                repository.ask(history.toList())
            } catch (e: Exception) {
                "⚠️ " + (e.message ?: "Не удалось получить ответ.")
            }.also { reply ->
                // Ответ модели сохраняем в контексте только при успехе.
                if (!reply.startsWith("⚠️ ")) {
                    history += Message(role = "assistant", content = reply)
                }
            }
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(reply, isUser = false),
                    isLoading = false
                )
            }
        }
    }

    /** Очищает контекст диалога и ленту чата. */
    fun onClearContext() {
        if (_uiState.value.isLoading) return
        history.clear()
        _uiState.update { it.copy(messages = emptyList(), input = "") }
    }

    /** Берём только сам вопрос: значимый текст без обрамляющих кавычек. */
    private fun cleanQuestion(raw: String): String =
        raw.trim().removeSurrounding("\"").trim()

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
                .onSuccess { q ->
                    _uiState.update { it.copy(input = cleanQuestion(q), isLoading = false) }
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
                .onSuccess { q ->
                    _uiState.update { it.copy(input = cleanQuestion(q), isLoading = false) }
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
            val summary = try {
                repository.ask(request)
            } catch (e: Exception) {
                "⚠️ " + (e.message ?: "Не удалось составить пересказ.")
            }
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(summary, isUser = false),
                    isLoading = false
                )
            }
        }
    }
}
