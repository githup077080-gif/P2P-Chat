package com.example.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.models.AiPersonaMode
import com.example.models.BotSyncStatus
import com.example.models.CustomBot
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StatusPill
import com.example.ui.theme.glassBorderStroke
import com.example.ui.theme.glassContainerColor
import com.example.ui.theme.glassModalColor

@Composable
fun AiBotsHubScreen(
    aiViewModel: AiAssistantViewModel,
    botViewModel: BotBuilderViewModel,
    onOpenAiAssistant: () -> Unit,
    onCreateBot: () -> Unit,
    onEditBot: (String) -> Unit,
    onTestBot: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val bots by botViewModel.bots.collectAsStateWithLifecycle()
    val isSyncing by botViewModel.isSyncing.collectAsStateWithLifecycle()
    val trainingConfig by aiViewModel.trainingConfig.collectAsStateWithLifecycle()
    val examples by aiViewModel.trainingExamples.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteBot by remember { mutableStateOf<CustomBot?>(null) }

    LaunchedEffect(Unit) {
        botViewModel.events.collect { event ->
            when (event) {
                is BotHubEvent.Toast -> {
                    val msg = when (event.messageKey) {
                        BotToastType.ACTIVATED -> strings.botActivatedToast
                        BotToastType.PAUSED -> strings.botPausedToast
                        BotToastType.PARTIAL_SYNC -> strings.botPartialSyncNotice
                        BotToastType.SAVED -> strings.profileSaved
                    }
                    snackbarHostState.showSnackbar(msg)
                }
                is BotHubEvent.Error -> {
                    snackbarHostState.showSnackbar(event.detail ?: event.error.message(strings))
                }
            }
        }
    }

    val personaLabel = when (trainingConfig.personaMode) {
        AiPersonaMode.NOVA_ASSISTANT -> strings.aiPersonaNova
        AiPersonaMode.TRANSLATOR -> strings.aiPersonaTranslator
        AiPersonaMode.REPLY_COACH -> strings.aiPersonaCoach
        AiPersonaMode.BOT_ARCHITECT -> strings.aiPersonaArchitect
        AiPersonaMode.CUSTOM -> strings.aiPersonaCustom
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 700.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "hub_header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.aiHubTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.aiHubSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pre-trained Built-In Nova AI Hero Glass Card
            item(key = "nova_ai_hero") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_nova_ai_card"),
                    borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
                    containerColor = glassContainerColor(darkAlpha = 0.30f, lightAlpha = 0.48f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.78f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = strings.aiAssistantCardTitle,
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = strings.aiAssistantCardTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                StatusPill(
                                    label = strings.aiAssistantBadge,
                                    dotColor = SignalEmerald
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = strings.aiAssistantCardSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = personaLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${examples.size} ${strings.aiTrainedExamplesCountLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    NeonPrimaryButton(
                        text = strings.aiAssistantOpenAction,
                        onClick = onOpenAiAssistant,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_nova_ai_button"
                    )
                }
            }

            // Bot Builder section header
            item(key = "bots_section_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = strings.botBuilderSectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { botViewModel.manualSyncFromServer() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("bots_sync_button")
                        ) {
                            if (isSyncing) {
                                OrbitalPulseLoader(size = 20.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = strings.botSyncServerAction,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        TextButton(
                            onClick = onCreateBot,
                            modifier = Modifier.testTag("create_bot_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.botBuilderCreateAction,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (bots.isEmpty()) {
                item(key = "bots_empty_state") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SmartToy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = strings.botEmptyTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = strings.botEmptySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            NeonPrimaryButton(
                                text = strings.botBuilderCreateAction,
                                onClick = onCreateBot,
                                modifier = Modifier.fillMaxWidth(),
                                testTag = "empty_create_bot_button"
                            )
                        }
                    }
                }
            } else {
                items(bots, key = { it.localId }) { bot ->
                    BotOverviewCard(
                        bot = bot,
                        onToggleActivation = {
                            if (bot.isActive) {
                                botViewModel.deactivateBot(bot.localId)
                            } else {
                                botViewModel.activateBot(bot.localId)
                            }
                        },
                        onTest = { onTestBot(bot.localId) },
                        onEdit = { onEditBot(bot.localId) },
                        onDelete = { pendingDeleteBot = bot }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateBot,
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("create_bot_fab")
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = strings.botBuilderCreateAction
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    pendingDeleteBot?.let { bot ->
        AlertDialog(
            onDismissRequest = { pendingDeleteBot = null },
            containerColor = glassModalColor(),
            title = { Text(strings.botDeleteConfirmTitle) },
            text = {
                Text(
                    text = strings.botDeleteConfirmBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        botViewModel.deleteBot(bot.localId)
                        pendingDeleteBot = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorCrimson),
                    modifier = Modifier.testTag("confirm_delete_bot_button")
                ) {
                    Text(strings.confirmAction)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBot = null }) {
                    Text(strings.cancelAction, color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }
}

@Composable
private fun BotOverviewCard(
    bot: CustomBot,
    onToggleActivation: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalAppStrings.current
    val (statusLabel, statusColor) = when {
        bot.isActive && bot.syncStatus == BotSyncStatus.ACTIVE_ON_SERVER ->
            strings.botStatusActive to SignalEmerald
        bot.syncStatus == BotSyncStatus.SYNC_FAILED ->
            strings.botStatusFailed to ErrorCrimson
        bot.syncStatus == BotSyncStatus.PENDING_SERVER_SYNC ->
            strings.botStatusPending to AlertAmber
        else -> strings.botStatusPaused to MaterialTheme.colorScheme.onSurfaceVariant
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bot_card_${bot.handle}"),
        borderColor = statusColor.copy(alpha = 0.42f),
        containerColor = glassContainerColor(darkAlpha = 0.28f, lightAlpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                    border = glassBorderStroke(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (bot.isActive) Icons.Outlined.CloudDone else Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = if (bot.isActive) SignalEmerald else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bot.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "@${bot.handle}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(label = statusLabel, dotColor = statusColor)
        }

        if (bot.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = bot.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (bot.commands.isNotEmpty() || !bot.botToken.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bot.commands.take(3).forEach { cmd ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = cmd.command,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (!bot.botToken.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "API •••${bot.botToken.takeLast(4)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = !bot.lastSyncMessage.isNullOrBlank() && bot.syncStatus == BotSyncStatus.SYNC_FAILED) {
            Text(
                text = bot.lastSyncMessage.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = ErrorCrimson,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeonOutlinedButton(
                text = if (bot.isActive) strings.botDeactivateAction else strings.botActivateAction,
                onClick = onToggleActivation,
                accentColor = if (bot.isActive) AlertAmber else SignalEmerald,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                testTag = "toggle_bot_api_${bot.handle}"
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onTest)
                    .testTag("test_bot_${bot.handle}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = strings.botTestAction,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = strings.botTestAction,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_bot_${bot.handle}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = strings.botEditAction,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_bot_${bot.handle}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = strings.botDeleteAction,
                    tint = ErrorCrimson
                )
            }
        }
    }
}
