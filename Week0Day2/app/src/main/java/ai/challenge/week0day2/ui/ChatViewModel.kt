package ai.challenge.week0day2.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week0day2.data.AskResult
import ai.challenge.week0day2.data.DeepSeekRepository
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

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun onToggleSettings() {
        _uiState.update { it.copy(settingsOpen = !it.settingsOpen) }
    }

    fun onMaxTokensChange(value: Int) {
        _uiState.update { it.copy(maxTokens = value) }
    }

    fun onFormatPromptChange(value: String) {
        _uiState.update { it.copy(formatPrompt = value) }
    }

    fun onStopSequenceChange(value: String) {
        _uiState.update { it.copy(stopSequence = value) }
    }

    /** Сбрасывает параметры ограничений к значениям по умолчанию. */
    fun onResetSettings() {
        _uiState.update {
            it.copy(
                maxTokens = DEFAULT_MAX_TOKENS,
                formatPrompt = DEFAULT_FORMAT_PROMPT,
                stopSequence = DEFAULT_STOP_SEQUENCE
            )
        }
    }

    /**
     * Отправляет один и тот же вопрос двумя способами и кладёт оба ответа в ленту:
     *  1) без ограничений (как в задании №1);
     *  2) с ограничениями — формат + длина + условие завершения (задание №2).
     */
    fun onSend() {
        val state = _uiState.value
        val prompt = state.input.trim()
        if (prompt.isEmpty() || state.isLoading) return

        // Снимок параметров ограничений на момент отправки.
        val format = state.formatPrompt.trim().ifBlank { null }
        val maxTokens = state.maxTokens.takeIf { it > 0 }
        val stop = state.stopSequence.trim().ifBlank { null }?.let { listOf(it) }

        // Добавляем сообщение пользователя, очищаем поле, включаем загрузку.
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(prompt, isUser = true),
                input = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            // 1. Без ограничений — голый запрос.
            val raw = runCatching { repository.ask(prompt) }
            appendAnswer(LABEL_RAW, raw)

            // 2. С ограничениями — формат + max_tokens + stop sequence из настроек.
            val controlled = runCatching {
                repository.ask(
                    prompt = prompt,
                    system = format,
                    maxTokens = maxTokens,
                    stop = stop
                )
            }
            appendAnswer(LABEL_CONTROLLED, controlled)

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Добавляет в ленту карточку ответа (успех — с метаданными, ошибка — с ⚠️). */
    private fun appendAnswer(label: String, result: Result<AskResult>) {
        val message = result.fold(
            onSuccess = { r ->
                ChatMessage(
                    text = r.content,
                    isUser = false,
                    label = label,
                    meta = "finish_reason: ${r.finishReason ?: "—"} · ${r.completionTokens ?: "?"} токенов"
                )
            },
            onFailure = { e ->
                ChatMessage(
                    text = "⚠️ " + (e.message ?: "Не удалось получить ответ."),
                    isUser = false,
                    label = label
                )
            }
        )
        _uiState.update { it.copy(messages = it.messages + message) }
    }

    private companion object {
        const val LABEL_RAW = "Без ограничений"
        const val LABEL_CONTROLLED = "С ограничениями"
    }
}
