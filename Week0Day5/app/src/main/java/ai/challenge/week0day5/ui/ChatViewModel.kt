package ai.challenge.week0day5.ui

import ai.challenge.week0day5.data.HuggingFaceRepository
import ai.challenge.week0day5.data.MODEL_CATALOG
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: HuggingFaceRepository = HuggingFaceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    /** Шлёт один и тот же запрос всем моделям параллельно и собирает метрики. */
    fun onRun() {
        val prompt = _uiState.value.input.trim()
        if (prompt.isEmpty() || _uiState.value.isRunning) return

        // Инициализируем по карточке на модель в состоянии загрузки.
        _uiState.update {
            it.copy(
                isRunning = true,
                verdict = null,
                results = MODEL_CATALOG.map { model -> ModelResult(model = model, status = Status.LOADING) }
            )
        }

        viewModelScope.launch {
            // Параллельные запросы — каждый обновляет свою карточку по завершении.
            val jobs = MODEL_CATALOG.map { model ->
                async {
                    val result = try {
                        val m = repository.ask(model, prompt)
                        ModelResult(
                            model = model,
                            status = Status.DONE,
                            answer = m.answer,
                            promptTokens = m.promptTokens,
                            completionTokens = m.completionTokens,
                            totalTokens = m.totalTokens,
                            elapsedMs = m.elapsedMs,
                            costUsd = m.costUsd
                        )
                    } catch (e: Exception) {
                        ModelResult(
                            model = model,
                            status = Status.ERROR,
                            error = e.message ?: "Не удалось получить ответ."
                        )
                    }
                    updateResult(result)
                    result
                }
            }

            val all = jobs.awaitAll()
            _uiState.update { it.copy(isRunning = false, verdict = buildVerdict(all)) }
        }
    }

    private fun updateResult(result: ModelResult) {
        _uiState.update { state ->
            state.copy(
                results = state.results.map { if (it.model.id == result.model.id) result else it }
            )
        }
    }

    /** Короткий вывод: кто быстрее, кто дешевле, у кого самый развёрнутый ответ. */
    private fun buildVerdict(results: List<ModelResult>): String {
        val ok = results.filter { it.status == Status.DONE }
        if (ok.isEmpty()) return "Ни одна модель не ответила — проверьте HF_API_KEY и доступность моделей."

        val fastest = ok.minByOrNull { it.elapsedMs }
        val cheapest = ok.minByOrNull { it.costUsd }
        val longest = ok.maxByOrNull { it.answer.length } // прокси «детальности» ответа

        return buildString {
            fastest?.let { append("⚡ Быстрее всего: ${it.model.displayName} (${it.elapsedMs} мс)\n") }
            cheapest?.let { append("💰 Дешевле всего: ${it.model.displayName} (${formatCost(it.costUsd)})\n") }
            longest?.let { append("🏆 Самый развёрнутый ответ: ${it.model.displayName}") }
        }.trim()
    }
}
