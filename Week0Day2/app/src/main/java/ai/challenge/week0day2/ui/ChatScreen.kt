package ai.challenge.week0day2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Автоскролл к последнему сообщению (или индикатору загрузки).
    LaunchedEffect(state.messages.size, state.isLoading) {
        val count = state.messages.size + if (state.isLoading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "🤖  AI Чат",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = viewModel::onToggleSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Параметры ограничений"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            InputBar(
                input = state.input,
                enabled = !state.isLoading,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::onSend,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = state.settingsOpen) {
                SettingsPanel(
                    maxTokens = state.maxTokens,
                    formatPrompt = state.formatPrompt,
                    stopSequence = state.stopSequence,
                    onMaxTokensChange = viewModel::onMaxTokensChange,
                    onFormatPromptChange = viewModel::onFormatPromptChange,
                    onStopSequenceChange = viewModel::onStopSequenceChange,
                    onReset = viewModel::onResetSettings
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                if (state.messages.isEmpty() && !state.isLoading) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.messages) { message ->
                            MessageBubble(message)
                        }
                        if (state.isLoading) {
                            item { LoadingBubble() }
                        }
                    }
                }
            }
        }
    }
}

/** Панель параметров «контролируемого» запроса: длина, формат, stop-последовательность. */
@Composable
private fun SettingsPanel(
    maxTokens: Int,
    formatPrompt: String,
    stopSequence: String,
    onMaxTokensChange: (Int) -> Unit,
    onFormatPromptChange: (String) -> Unit,
    onStopSequenceChange: (String) -> Unit,
    onReset: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Параметры ограничений",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onReset) { Text("Сбросить") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Лимит длины (max_tokens): $maxTokens",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { onMaxTokensChange(it.toInt()) },
                valueRange = 20f..500f,
                steps = 47 // шаг 10 токенов на диапазоне 20..500
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = formatPrompt,
                onValueChange = onFormatPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Формат ответа (system)") },
                minLines = 2,
                maxLines = 5
            )

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = stopSequence,
                onValueChange = onStopSequenceChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Stop-последовательность") },
                singleLine = true,
                supportingText = { Text("Пусто — не отправлять stop") }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "💬", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Задайте первый вопрос",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Покажу два ответа DeepSeek: без ограничений и с ограничениями",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Avatar("🤖")
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            shape = bubbleShape(isUser),
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Подпись карточки сравнения: «Без ограничений» / «С ограничениями».
                message.label?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(text = message.text)
                // Метаданные ответа: finish_reason и число токенов.
                message.meta?.let { meta ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Avatar("🤖")
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = bubbleShape(isUser = false),
            tonalElevation = 1.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Печатает…",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

private fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = if (isUser) 18.dp else 4.dp,
    bottomEnd = if (isUser) 4.dp else 18.dp
)

@Composable
private fun Avatar(emoji: String) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 16.sp)
    }
}

@Composable
private fun InputBar(
    input: String,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Спросите что-нибудь…") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(
                onClick = onSend,
                enabled = enabled && input.isNotBlank(),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить"
                )
            }
        }
    }
}
