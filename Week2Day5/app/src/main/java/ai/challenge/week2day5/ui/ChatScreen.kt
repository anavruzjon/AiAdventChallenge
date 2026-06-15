package ai.challenge.week2day5.ui

import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    // Диалог просмотра сохранённых фактов (только для стратегии Sticky Facts).
    var showFactsDialog by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    val hasContext = state.messages.isNotEmpty() && !state.isLoading
                    IconButton(
                        onClick = viewModel::onGenerateQuestion,
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Сгенерировать вопрос"
                        )
                    }
                    IconButton(
                        onClick = viewModel::onGenerateContextualQuestion,
                        enabled = hasContext
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = "Вопрос по теме разговора"
                        )
                    }
                    // Overflow-меню: сюда будут добавляться новые действия.
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "Меню"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Сгенерировать факт") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null
                                    )
                                },
                                enabled = !state.isLoading,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onGenerateFact()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Пересказать контекст") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.List,
                                        contentDescription = null
                                    )
                                },
                                enabled = !state.isLoading,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onSummarize()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Очистить контекст") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null
                                    )
                                },
                                enabled = hasContext,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onClearContext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сравнить стратегии") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Analytics,
                                        contentDescription = null
                                    )
                                },
                                enabled = !state.isLoading,
                                onClick = {
                                    menuExpanded = false
                                    viewModel.onCompareStrategies()
                                }
                            )
                            // Просмотр блока фактов — только при стратегии Sticky Facts.
                            if (state.strategy == ContextStrategy.STICKY_FACTS) {
                                DropdownMenuItem(
                                    text = { Text("Показать факты (${state.facts.size})") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Bookmarks,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        showFactsDialog = true
                                    }
                                )
                            }
                            // Список веток — только при стратегии Branching, когда они есть.
                            // Клик переключает активную ветку.
                            if (state.strategy == ContextStrategy.BRANCHING &&
                                state.branches.isNotEmpty()
                            ) {
                                HorizontalDivider()
                                state.branches.forEach { branch ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (branch.isMain) "🌳 ${branch.title}"
                                                else "└─ ${branch.title}"
                                            )
                                        },
                                        trailingIcon = {
                                            if (branch.id == state.currentBranchId) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Активная ветка"
                                                )
                                            }
                                        },
                                        enabled = !state.isLoading,
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.onSelectBranch(branch.id)
                                        }
                                    )
                                }
                            }
                            // Переключатель стратегии управления контекстом.
                            // Выбор любой стратегии очищает контекст.
                            HorizontalDivider()
                            ContextStrategy.entries.forEach { strategy ->
                                DropdownMenuItem(
                                    text = { Text(strategy.label) },
                                    trailingIcon = {
                                        if (state.strategy == strategy) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Выбрано"
                                            )
                                        }
                                    },
                                    enabled = !state.isLoading,
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.onSelectStrategy(strategy)
                                    }
                                )
                            }
                        }
                    }
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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

    if (showFactsDialog) {
        SavedFactsDialog(
            facts = state.facts,
            onClear = viewModel::onClearFacts,
            onDismiss = { showFactsDialog = false }
        )
    }

    // Результат сравнения стратегий: пишем отчёт в README.md в папке приложения и
    // показываем его в диалоге. Путь сохраняем, чтобы показать пользователю.
    state.comparison?.let { comparison ->
        val context = LocalContext.current
        var savedPath by remember(comparison) { mutableStateOf<String?>(null) }
        LaunchedEffect(comparison) {
            savedPath = runCatching {
                File(context.getExternalFilesDir(null), "README.md")
                    .apply { writeText(comparison.reportMarkdown) }
                    .absolutePath
            }.getOrNull()
        }
        ComparisonDialog(
            reportMarkdown = comparison.reportMarkdown,
            savedPath = savedPath,
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, comparison.reportMarkdown)
                }
                context.startActivity(Intent.createChooser(intent, "Поделиться отчётом"))
            },
            onDismiss = viewModel::onDismissComparison
        )
    }
}

@Composable
private fun ComparisonDialog(
    reportMarkdown: String,
    savedPath: String?,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Analytics, contentDescription = null) },
        title = { Text("Сравнение стратегий") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                MarkdownText(
                    markdown = reportMarkdown,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = LocalContentColor.current
                    )
                )
                savedPath?.let { path ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "💾 Сохранено: $path",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = {
            TextButton(onClick = onShare) { Text("Поделиться") }
        }
    )
}

@Composable
private fun SavedFactsDialog(
    facts: List<Fact>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
        title = { Text("Сохранённые факты") },
        text = {
            if (facts.isEmpty()) {
                Text(
                    text = "Пока нет сохранённых фактов. Они появятся после ответов модели.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(facts) { fact ->
                        Column {
                            Text(
                                text = fact.key,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = fact.value,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        dismissButton = {
            TextButton(
                onClick = onClear,
                enabled = facts.isNotEmpty()
            ) { Text("Очистить факты") }
        }
    )
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
            text = "Отвечу с помощью DeepSeek",
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
            modifier = Modifier.widthIn(max = 600.dp)
        ) {
            if (isUser) {
                // Сообщения пользователя — простой текст.
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            } else {
                // Ответы ассистента приходят в Markdown — рендерим красиво,
                // блоки кода моноширинным шрифтом с фоном. Под текстом — расход токенов.
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(
                        markdown = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = LocalContentColor.current
                        ),
                        syntaxHighlightColor = MaterialTheme.colorScheme.surfaceVariant,
                        syntaxHighlightTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    message.usage?.let { usage ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "📊 промпт ${usage.promptTokens} · ответ " +
                                "${usage.completionTokens} · всего ${usage.totalTokens} · ~$" +
                                String.format(Locale.US, "%.6f", usage.costUsd),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    }
                    message.routingUsage?.let { routing ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "🔀 выбор ветки: промпт ${routing.promptTokens} · ответ " +
                                "${routing.completionTokens} · всего ${routing.totalTokens} · ~$" +
                                String.format(Locale.US, "%.6f", routing.costUsd),
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalContentColor.current.copy(alpha = 0.6f)
                        )
                    }
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
