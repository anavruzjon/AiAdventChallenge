package ai.challenge.week0day4.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week0day4.data.DeepSeekRepository
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

    fun onTemperatureChange(value: Float) {
        _uiState.update { it.copy(temperature = value) }
    }

    fun onToggleSettings() {
        _uiState.update { it.copy(isSettingsOpen = !it.isSettingsOpen) }
    }

    fun onSend() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isLoading) return

        val temperature = _uiState.value.temperature.toDouble()

        // Добавляем сообщение пользователя, очищаем поле, включаем загрузку.
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(prompt, isUser = true),
                input = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            val reply = try {
                repository.ask(prompt, temperature)
            } catch (e: Exception) {
                "⚠️ " + (e.message ?: "Не удалось получить ответ.")
            }
            _uiState.update {
                it.copy(
                    messages = it.messages + ChatMessage(reply, isUser = false),
                    isLoading = false
                )
            }
        }
    }
}
