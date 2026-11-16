package com.example.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.models.AiChatMessage
import com.example.models.AiPersonaMode
import com.example.models.AiRole
import com.example.models.AiTrainingExample
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.StatusPill
import com.example.ui.theme.glassBarColor
import com.example.ui.theme.glassContainerColor
import com.example.ui.theme.glassModalColor
import com.example.ui.theme.glassTextFieldColors
import com.example.ui.theme.glassTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trainingConfig by viewModel.trainingConfig.collectAsStateWithLifecycle()
    val trainingExamples by viewModel.trainingExamples.collectAsStateWithLifecycle()

    var showTrainingStudio by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, uiState.streamingReply.length) {
        val targetIndex = (messages.size + if (uiState.isStreaming) 1 else 0) - 1
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    val personaModes = listOf(
        AiPersonaMode.NOVA_ASSISTANT to strings.aiPersonaNova,
        AiPersonaMode.TRANSLATOR to strings.aiPersonaTranslator,
        AiPersonaMode.REPLY_COACH to strings.aiPersonaCoach,
        AiPersonaMode.BOT_ARCHITECT to strings.aiPersonaArchitect,
        AiPersonaMode.CUSTOM to strings.aiPersonaCustom
    )

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = glassTopAppBarColors(accentAlpha = true),
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("ai_chat_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backAction
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = strings.aiChatTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.isStreaming) strings.aiStreamingLabel else strings.aiChatSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.isStreaming) MaterialTheme.colorScheme.secondary else SignalEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showTrainingStudio = true },
                            modifier = Modifier.testTag("ai_open_training_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.aiTrainButton,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (messages.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.clearConversation() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("ai_clear_chat_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CleaningServices,
                                    contentDescription = strings.aiClearChatAction,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Translucent persona selector bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    personaModes.forEach { (mode, label) ->
                        val selected = trainingConfig.personaMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectPersona(mode) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = glassContainerColor(darkAlpha = 0.22f, lightAlpha = 0.36f),
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                selectedLabelColor = MaterialTheme.colorScheme.secondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                selectedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("ai_persona_chip_${mode.key}")
                        )
                    }
                }

                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    Surface(
                        color = ErrorCrimson.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorCrimson,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearError() }) {
                                Text(strings.cancelAction, color = ErrorCrimson)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty() && !uiState.isStreaming) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(20.dp)
                                .widthIn(max = 580.dp)
                                .align(Alignment.Center),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            GlassCard(
                                modifier = Modifier.fillMaxWidth(),
                                borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = strings.aiWelcomeCardTitle,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = strings.aiWelcomeCardBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            val quickPrompts = listOf(
                                strings.aiQuickPromptSummarize,
                                strings.aiQuickPromptTranslate,
                                strings.aiQuickPromptReply,
                                strings.aiQuickPromptBotIdea
                            )
                            quickPrompts.forEachIndexed { idx, promptText ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = glassContainerColor(darkAlpha = 0.26f, lightAlpha = 0.42f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.sendMessage(promptText) }
                                        .testTag("ai_quick_prompt_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = promptText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AiMessageGlassBubble(
                                    message = msg,
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(msg.content))
                                        scope.launch {
                                            snackbarHostState.showSnackbar(strings.messageCopiedToast)
                                        }
                                    }
                                )
                            }

                            if (uiState.isStreaming) {
                                item(key = "streaming_bubble") {
                                    AiMessageGlassBubble(
                                        message = AiChatMessage(
                                            id = -1L,
                                            role = AiRole.MODEL,
                                            content = uiState.streamingReply.ifBlank { strings.aiStreamingLabel }
                                        ),
                                        isStreaming = true,
                                        onCopy = {}
                                    )
                                }
                            }
                        }
                    }
                }

                // Translucent Glass Input Bar
                Surface(
                    color = glassBarColor(darkAlpha = 0.30f, lightAlpha = 0.48f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.draftText,
                            onValueChange = viewModel::onDraftChange,
                            placeholder = { Text(strings.aiInputHint) },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendMessage() }),
                            shape = RoundedCornerShape(22.dp),
                            colors = glassTextFieldColors(focusedBorderColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_chat_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.draftText.isNotBlank() && !uiState.isStreaming) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                            } else {
                                glassContainerColor(darkAlpha = 0.34f, lightAlpha = 0.48f)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("ai_send_button")
                                .clickable(enabled = uiState.draftText.isNotBlank() && !uiState.isStreaming) {
                                    viewModel.sendMessage()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (uiState.isStreaming) {
                                    OrbitalPulseLoader(size = 22.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = strings.sendMessageDesc,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTrainingStudio) {
        ModalBottomSheet(
            onDismissRequest = { showTrainingStudio = false },
            containerColor = glassModalColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            AiTrainingStudioSheet(
                config = trainingConfig,
                examples = trainingExamples,
                personaModes = personaModes,
                onSelectPersona = viewModel::selectPersona,
                onSaveCustomInstruction = { text ->
                    viewModel.saveCustomInstruction(text)
                    scope.launch {
                        snackbarHostState.showSnackbar(strings.aiInstructionSavedToast)
                    }
                },
                onAddExample = { q, a ->
                    viewModel.addTrainingExample(q, a)
                    scope.launch {
                        snackbarHostState.showSnackbar(strings.aiExampleAddedToast)
                    }
                },
                onDeleteExample = viewModel::deleteTrainingExample
            )
        }
    }
}

@Composable
private fun AiMessageGlassBubble(
    message: AiChatMessage,
    isStreaming: Boolean = false,
    onCopy: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val isUser = message.role == AiRole.USER

    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (isUser) 18.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 18.dp
    )

    val containerBrush = when {
        message.isError -> Brush.linearGradient(
            listOf(ErrorCrimson.copy(alpha = 0.22f), ErrorCrimson.copy(alpha = 0.14f))
        )
        isUser -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.56f else 0.78f),
                MaterialTheme.colorScheme.secondary.copy(alpha = if (isDark) 0.40f else 0.68f)
            )
        )
        else -> Brush.linearGradient(
            listOf(
                glassContainerColor(darkAlpha = 0.34f, lightAlpha = 0.55f),
                glassBarColor(darkAlpha = 0.26f, lightAlpha = 0.44f)
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = shape,
            color = Color.Transparent,
            border = BorderStroke(
                1.dp,
                when {
                    message.isError -> ErrorCrimson.copy(alpha = 0.55f)
                    isUser -> Color.White.copy(alpha = 0.28f)
                    else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f)
                }
            ),
            modifier = Modifier
                .widthIn(max = 324.dp)
                .clip(shape)
                .background(containerBrush, shape)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && !message.isError) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = strings.aiAssistantCardTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (isStreaming) {
                            OrbitalPulseLoader(size = 14.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = strings.copyAction,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(onClick = onCopy)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        message.isError -> ErrorCrimson
                        isUser -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun AiTrainingStudioSheet(
    config: AiTrainingConfig,
    examples: List<AiTrainingExample>,
    personaModes: List<Pair<AiPersonaMode, String>>,
    onSelectPersona: (AiPersonaMode) -> Unit,
    onSaveCustomInstruction: (String) -> Unit,
    onAddExample: (String, String) -> Unit,
    onDeleteExample: (Long) -> Unit
) {
    val strings = LocalAppStrings.current
    var instructionDraft by remember(config.customInstruction) { mutableStateOf(config.customInstruction) }
    var examplePrompt by remember { mutableStateOf("") }
    var exampleReply by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = strings.aiTrainingTitle,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = strings.aiTrainingSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = strings.aiTrainingPersonaLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            personaModes.forEach { (mode, label) ->
                FilterChip(
                    selected = config.personaMode == mode,
                    onClick = { onSelectPersona(mode) },
                    label = { Text(label) }
                )
            }
        }

        OutlinedTextField(
            value = instructionDraft,
            onValueChange = { instructionDraft = it },
            label = { Text(strings.aiCustomInstructionLabel) },
            placeholder = { Text(strings.aiCustomInstructionHint) },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(16.dp),
            colors = glassTextFieldColors(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_training_instruction_input")
        )

        NeonPrimaryButton(
            text = strings.aiSaveInstructionAction,
            onClick = { onSaveCustomInstruction(instructionDraft) },
            modifier = Modifier.fillMaxWidth(),
            testTag = "ai_save_training_button"
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = strings.aiTrainingExamplesTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = examplePrompt,
            onValueChange = { examplePrompt = it },
            label = { Text(strings.aiExamplePromptLabel) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = glassTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_example_prompt_input")
        )
        OutlinedTextField(
            value = exampleReply,
            onValueChange = { exampleReply = it },
            label = { Text(strings.aiExampleReplyLabel) },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(14.dp),
            colors = glassTextFieldColors(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_example_reply_input")
        )
        NeonOutlinedButton(
            text = strings.aiAddExampleAction,
            enabled = examplePrompt.isNotBlank() && exampleReply.isNotBlank(),
            onClick = {
                onAddExample(examplePrompt, exampleReply)
                examplePrompt = ""
                exampleReply = ""
            },
            modifier = Modifier.fillMaxWidth(),
            testTag = "ai_add_example_button"
        )

        if (examples.isEmpty()) {
            Text(
                text = strings.aiNoExamplesHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            examples.forEach { ex ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ex.userPrompt,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = ex.idealReply,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onDeleteExample(ex.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = strings.deleteLocallyAction,
                                tint = ErrorCrimson
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
