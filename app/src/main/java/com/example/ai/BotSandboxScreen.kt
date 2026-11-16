package com.example.ai

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.models.AiRole
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusPill
import com.example.ui.theme.glassBarColor
import com.example.ui.theme.glassContainerColor
import com.example.ui.theme.glassTextFieldColors
import com.example.ui.theme.glassTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotSandboxScreen(
    botId: String,
    viewModel: BotBuilderViewModel,
    onEditBot: (String) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isDark = LocalIsDarkTheme.current
    val state by viewModel.sandboxState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(botId) {
        viewModel.openSandbox(botId)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val bot = state.bot

    FuturisticBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = glassTopAppBarColors(accentAlpha = true),
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bot_sandbox_back_button")
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
                            Icon(
                                imageVector = if (bot?.isActive == true) Icons.Outlined.CloudDone else Icons.Outlined.SmartToy,
                                contentDescription = null,
                                tint = if (bot?.isActive == true) SignalEmerald else MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = bot?.name ?: strings.botSandboxTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (bot != null) "@${bot.handle}" else strings.botSandboxTitle,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    },
                    actions = {
                        if (bot != null) {
                            StatusPill(
                                label = if (bot.isActive) strings.botStatusActive else strings.botStatusPaused,
                                dotColor = if (bot.isActive) SignalEmerald else AlertAmber
                            )
                            IconButton(
                                onClick = { onEditBot(bot.localId) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("bot_sandbox_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = strings.botEditAction,
                                    tint = MaterialTheme.colorScheme.primary
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
                if (bot != null && !bot.isActive) {
                    Surface(
                        color = AlertAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, AlertAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.botSandboxInactiveNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertAmber,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Quick slash commands strip
                if (bot != null && bot.commands.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        bot.commands.forEach { cmd ->
                            AssistChip(
                                onClick = { viewModel.sendSandboxMessage(cmd.command) },
                                label = {
                                    Text(
                                        text = cmd.command,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFontFamily)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = glassContainerColor(darkAlpha = 0.28f, lightAlpha = 0.44f),
                                    labelColor = MaterialTheme.colorScheme.secondary
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (state.messages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(modifier = Modifier.widthIn(max = 460.dp)) {
                                Text(
                                    text = strings.botSandboxEmptyTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.botSandboxEmptySubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.messages, key = { it.id }) { msg ->
                                val isUser = msg.role == AiRole.USER
                                val bubbleShape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (isUser) 18.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 18.dp
                                )
                                val bgBrush = when {
                                    msg.isError -> Brush.linearGradient(
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
                                            glassContainerColor(darkAlpha = 0.34f, lightAlpha = 0.54f),
                                            glassBarColor(darkAlpha = 0.26f, lightAlpha = 0.44f)
                                        )
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Surface(
                                        shape = bubbleShape,
                                        color = Color.Transparent,
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                msg.isError -> ErrorCrimson.copy(alpha = 0.55f)
                                                isUser -> Color.White.copy(alpha = 0.28f)
                                                else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f)
                                            }
                                        ),
                                        modifier = Modifier
                                            .widthIn(max = 310.dp)
                                            .clip(bubbleShape)
                                            .background(bgBrush, bubbleShape)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                            if (!msg.matchedCommand.isNullOrBlank()) {
                                                Text(
                                                    text = msg.matchedCommand,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(3.dp))
                                            }
                                            Text(
                                                text = msg.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    msg.isError -> ErrorCrimson
                                                    isUser -> Color.White
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Translucent Composer
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
                            value = state.draftText,
                            onValueChange = viewModel::onSandboxDraftChange,
                            placeholder = { Text(strings.botSandboxInputHint) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.sendSandboxMessage() }),
                            shape = RoundedCornerShape(22.dp),
                            colors = glassTextFieldColors(MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bot_sandbox_input")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (state.draftText.isNotBlank() && !state.isSending) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                            } else {
                                glassContainerColor(darkAlpha = 0.34f, lightAlpha = 0.48f)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("bot_sandbox_send_button")
                                .clickable(enabled = state.draftText.isNotBlank() && !state.isSending) {
                                    viewModel.sendSandboxMessage()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (state.isSending) {
                                    OrbitalPulseLoader(size = 20.dp)
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
}
