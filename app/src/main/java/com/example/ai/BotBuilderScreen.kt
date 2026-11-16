package com.example.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.localization.LocalAppStrings
import com.example.ui.theme.ErrorCrimson
import com.example.ui.theme.FuturisticBackground
import com.example.ui.theme.GlassCard
import com.example.ui.theme.NeonOutlinedButton
import com.example.ui.theme.NeonPrimaryButton
import com.example.ui.theme.OrbitalPulseLoader
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.glassContainerColor
import com.example.ui.theme.glassTextFieldColors
import com.example.ui.theme.glassTopAppBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotBuilderScreen(
    botId: String?,
    viewModel: BotBuilderViewModel,
    onSavedAndNavigateBack: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.editorState.collectAsStateWithLifecycle()

    LaunchedEffect(botId) {
        viewModel.loadForEditor(botId, strings.language)
    }

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
                                .testTag("bot_builder_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = strings.backAction
                            )
                        }
                    },
                    title = {
                        Text(
                            text = if (botId == null) strings.botBuilderTitleNew else strings.botBuilderTitleEdit,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .widthIn(max = 640.dp)
                        .align(Alignment.TopCenter),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // AI Architect Blueprint Generator Glass Card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.50f),
                        containerColor = glassContainerColor(darkAlpha = 0.30f, lightAlpha = 0.48f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = strings.botAiGenerateTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.aiGoalPrompt,
                            onValueChange = viewModel::onAiGoalChange,
                            placeholder = { Text(strings.botAiGenerateHint) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            colors = glassTextFieldColors(MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_ai_goal_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        NeonOutlinedButton(
                            text = if (state.isGeneratingWithAi) strings.botAiGeneratingLabel else strings.botAiGenerateAction,
                            enabled = state.aiGoalPrompt.isNotBlank() && !state.isGeneratingWithAi,
                            onClick = { viewModel.generateBotWithAi(strings.language) },
                            accentColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "bot_ai_generate_button"
                        )
                        if (state.isGeneratingWithAi) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OrbitalPulseLoader(size = 24.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = strings.botAiGeneratingLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.errorBanner != null || state.validationNameEmpty) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ErrorCrimson.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, ErrorCrimson.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (state.validationNameEmpty) {
                                    strings.botValidationNameRequired
                                } else {
                                    state.errorBanner.orEmpty()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorCrimson,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Identity & Persona Card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            label = { Text(strings.botNameLabel) },
                            placeholder = { Text(strings.botNameHint) },
                            leadingIcon = {
                                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = glassTextFieldColors(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_name_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.handle,
                            onValueChange = viewModel::onHandleChange,
                            label = { Text(strings.botHandleLabel) },
                            placeholder = { Text(strings.botHandleHint) },
                            leadingIcon = {
                                Icon(Icons.Outlined.AlternateEmail, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_handle_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = viewModel::onDescriptionChange,
                            label = { Text(strings.botDescriptionLabel) },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            colors = glassTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_description_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.welcomeMessage,
                            onValueChange = viewModel::onWelcomeChange,
                            label = { Text(strings.botWelcomeLabel) },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            colors = glassTextFieldColors(MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_welcome_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.systemPrompt,
                            onValueChange = viewModel::onSystemPromptChange,
                            label = { Text(strings.botSystemPromptLabel) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(14.dp),
                            colors = glassTextFieldColors(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bot_system_prompt_input")
                        )
                    }

                    // AI & API Activation Switches Card
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = SignalEmerald.copy(alpha = 0.42f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.botAiPoweredLabel, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = strings.botAiPoweredDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.aiPowered,
                                onCheckedChange = viewModel::onAiPoweredToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("bot_ai_powered_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = null,
                                tint = SignalEmerald
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(strings.botActivateOnApiLabel, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = strings.botActivateOnApiDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.activateOnServer,
                                onCheckedChange = viewModel::onActivateOnServerToggle,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = SignalEmerald
                                ),
                                modifier = Modifier.testTag("bot_activate_api_switch")
                            )
                        }
                    }

                    // Slash Commands Card
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(strings.botCommandsTitle, style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(
                                onClick = { viewModel.addEmptyCommand() },
                                modifier = Modifier.testTag("bot_add_command_button")
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.botAddCommandAction)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        state.commands.forEachIndexed { index, cmd ->
                            GlassCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                contentPadding = PaddingValues(12.dp),
                                cornerRadius = 16.dp,
                                containerColor = glassContainerColor(darkAlpha = 0.18f, lightAlpha = 0.34f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = cmd.command,
                                        onValueChange = {
                                            viewModel.updateCommand(index, cmd.copy(command = it))
                                        },
                                        label = { Text(strings.botCommandSlashLabel) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = glassTextFieldColors(),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (state.commands.size > 1) {
                                        IconButton(
                                            onClick = { viewModel.removeCommand(index) },
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
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = cmd.description,
                                    onValueChange = {
                                        viewModel.updateCommand(index, cmd.copy(description = it))
                                    },
                                    label = { Text(strings.botCommandDescLabel) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = glassTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = cmd.response,
                                    onValueChange = {
                                        viewModel.updateCommand(index, cmd.copy(response = it))
                                    },
                                    label = { Text(strings.botCommandReplyLabel) },
                                    minLines = 2,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = glassTextFieldColors(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Cloud API Credentials (when token exists)
                    if (!state.botToken.isNullOrBlank()) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Key, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Text(strings.botApiCredentialsTitle, style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = strings.botTokenLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.botToken.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily),
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(state.botToken.orEmpty()))
                                        scope.launch { snackbarHostState.showSnackbar(strings.botTokenCopiedToast) }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = strings.botCopyTokenAction,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (!state.webhookUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = strings.botWebhookLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = state.webhookUrl.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = SpaceGroteskFontFamily),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    NeonPrimaryButton(
                        text = if (state.activateOnServer) strings.botSaveAndSyncAction else strings.botSaveAction,
                        isLoading = state.isSaving,
                        onClick = { viewModel.saveAndSyncBot(onSuccess = onSavedAndNavigateBack) },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "bot_save_activate_button"
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
