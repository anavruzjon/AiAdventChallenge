package ai.challenge.week0day3.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.challenge.week0day3.data.DeepSeekRepository
import kotlinx.coroutines.async
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

    fun onSend() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isLoading) return

        // Добавляем сообщение пользователя, очищаем поле, включаем загрузку.
        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(prompt, isUser = true),
                input = "",
                isLoading = true
            )
        }

        viewModelScope.launch {
            // Запускаем все 4 метода параллельно, но добавляем блоки строго по порядку а→б→в→г.
            val directD = async { runCatching { repository.ask(prompt) } }
            val stepD = async { runCatching { repository.ask(stepByStepPrompt(prompt)) } }
            val genD = async { runCatching { generatedPromptFlow(prompt) } }
            val expertD = async { runCatching { repository.ask(prompt, system = EXPERTS_SYSTEM) } }

            appendResult(PromptMethod.DIRECT.title, directD.await())
            appendResult(PromptMethod.STEP_BY_STEP.title, stepD.await())
            appendGenerated(genD.await())
            appendResult(PromptMethod.EXPERTS.title, expertD.await())

            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /** Двухшаговый сценарий (в): сначала генерируем промпт, затем отвечаем по нему. */
    private suspend fun generatedPromptFlow(prompt: String): GeneratedOutcome {
        val generated = repository.ask(metaPrompt(prompt))
        val answer = repository.ask(generated)
        return GeneratedOutcome(generated, answer)
    }

    /** Добавляет блок ответа с подписью метода; при ошибке — блок с ⚠️. */
    private fun appendResult(label: String, result: Result<String>) {
        val text = result.getOrElse { "⚠️ " + (it.message ?: "Не удалось получить ответ.") }
        _uiState.update {
            it.copy(messages = it.messages + ChatMessage(text, isUser = false, label = label))
        }
    }

    /** Добавляет блок метода (в): показывает и сгенерированный промпт, и итоговый ответ. */
    private fun appendGenerated(result: Result<GeneratedOutcome>) {
        val text = result.fold(
            onSuccess = { "📝 Сгенерированный промпт:\n${it.generated}\n\n💡 Ответ:\n${it.answer}" },
            onFailure = { "⚠️ " + (it.message ?: "Не удалось получить ответ.") }
        )
        _uiState.update {
            it.copy(
                messages = it.messages +
                    ChatMessage(text, isUser = false, label = PromptMethod.GENERATED.title)
            )
        }
    }

    private data class GeneratedOutcome(val generated: String, val answer: String)

    private fun stepByStepPrompt(prompt: String) =
        "$prompt\n\nРешай пошагово: разбей решение на последовательные шаги и поясни каждый."

    private fun metaPrompt(prompt: String) =
        "Ты — эксперт по prompt-engineering. Составь один чёткий, подробный и эффективный " +
            "промпт, который поможет языковой модели максимально качественно решить задачу " +
            "пользователя. Верни ТОЛЬКО текст промпта, без пояснений и без кавычек.\n\n" +
            "Задача пользователя:\n$prompt"

    private companion object {
        const val EXPERTS_SYSTEM =
            "Ты ведёшь обсуждение в составе группы из трёх экспертов:\n" +
                "— Аналитик: разбирает задачу, выделяет ключевые факты и ограничения.\n" +
                "— Инженер: предлагает конкретное практическое решение.\n" +
                "— Критик: ищет слабые места, риски и проверяет решение.\n\n" +
                "Ответь от лица каждого эксперта по очереди (с подзаголовками " +
                "«Аналитик:», «Инженер:», «Критик:»), а затем дай краткий общий вывод " +
                "под подзаголовком «Итог:»."
    }
}
