package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.models.AiRole
import com.example.models.BotCommandItem
import com.example.models.CustomBot
import com.example.models.UiLanguage
import com.example.repository.BotRepository
import com.example.repository.BotServerActionResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BotEditorState(
    val editingLocalId: String? = null,
    val aiGoalPrompt: String = "",
    val isGeneratingWithAi: Boolean = false,
    val name: String = "",
    val handle: String = "",
    val description: String = "",
    val welcomeMessage: String = "",
    val systemPrompt: String = "",
    val aiPowered: Boolean = true,
    val activateOnServer: Boolean = true,
    val commands: List<BotCommandItem> = listOf(
        BotCommandItem("/start", "Welcome! How can I assist you today?", "Start bot session"),
        BotCommandItem("/help", "Send any question or pick a command.", "Show available commands")
    ),
    val botToken: String? = null,
    val webhookUrl: String? = null,
    val isSaving: Boolean = false,
    val errorBanner: String? = null,
    val validationNameEmpty: Boolean = false
)

data class BotSandboxMessage(
    val id: String,
    val role: AiRole,
    val text: String,
    val matchedCommand: String? = null,
    val isError: Boolean = false
)

data class BotSandboxState(
    val bot: CustomBot? = null,
    val messages: List<BotSandboxMessage> = emptyList(),
    val draftText: String = "",
    val isSending: Boolean = false
)

sealed interface BotHubEvent {
    data class Toast(val messageKey: BotToastType, val detail: String? = null) : BotHubEvent
    data class Error(val error: AppError, val detail: String? = null) : BotHubEvent
}

enum class BotToastType {
    ACTIVATED,
    PAUSED,
    PARTIAL_SYNC,
    SAVED
}

class BotBuilderViewModel(
    private val botRepository: BotRepository,
    private val aiRepository: P2PAiRepository
) : ViewModel() {

    val bots: StateFlow<List<CustomBot>> = botRepository.botsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _editorState = MutableStateFlow(BotEditorState())
    val editorState: StateFlow<BotEditorState> = _editorState.asStateFlow()

    private val _sandboxState = MutableStateFlow(BotSandboxState())
    val sandboxState: StateFlow<BotSandboxState> = _sandboxState.asStateFlow()

    private val _events = MutableSharedFlow<BotHubEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BotHubEvent> = _events.asSharedFlow()

    init {
        syncFromServer()
    }

    fun syncFromServer() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            when (val res = botRepository.syncBotsFromServer()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    // Silent if no bots exist yet or emit softly if user triggered
                }
            }
            _isSyncing.value = false
        }
    }

    fun manualSyncFromServer() {
        if (_isSyncing.value) return
        _isSyncing.value = true
        viewModelScope.launch {
            when (val res = botRepository.syncBotsFromServer()) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    _events.emit(BotHubEvent.Error(res.error))
                }
            }
            _isSyncing.value = false
        }
    }

    fun activateBot(localId: String) {
        viewModelScope.launch {
            when (val res = botRepository.activateBotOnServer(localId)) {
                is BotServerActionResult.Success -> {
                    _events.emit(BotHubEvent.Toast(BotToastType.ACTIVATED))
                }
                is BotServerActionResult.PartialLocalSaved -> {
                    _events.emit(BotHubEvent.Error(res.error, res.detail))
                }
            }
        }
    }

    fun deactivateBot(localId: String) {
        viewModelScope.launch {
            when (val res = botRepository.deactivateBotOnServer(localId)) {
                is BotServerActionResult.Success -> {
                    _events.emit(BotHubEvent.Toast(BotToastType.PAUSED))
                }
                is BotServerActionResult.PartialLocalSaved -> {
                    _events.emit(BotHubEvent.Error(res.error, res.detail))
                }
            }
        }
    }

    fun deleteBot(localId: String) {
        viewModelScope.launch {
            when (val res = botRepository.deleteBot(localId)) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> _events.emit(BotHubEvent.Error(res.error))
            }
        }
    }

    // --- Editor methods ---

    fun loadForEditor(botId: String?, language: UiLanguage) {
        if (botId == null) {
            val defaultWelcome = if (language == UiLanguage.PERSIAN) {
                "سلام! من ربات هوشمند شما هستم. برای دیدن دستورها /help را بفرستید."
            } else {
                "Hello! I'm your smart bot. Send /help to see what I can do."
            }
            val defaultHelp = if (language == UiLanguage.PERSIAN) {
                "پیام دلخواهی بفرستید یا از دستور /start برای شروع دوباره استفاده کنید."
            } else {
                "Send any message or use /start to begin."
            }
            _editorState.value = BotEditorState(
                commands = listOf(
                    BotCommandItem("/start", defaultWelcome, if (language == UiLanguage.PERSIAN) "شروع ربات" else "Start session"),
                    BotCommandItem("/help", defaultHelp, if (language == UiLanguage.PERSIAN) "راهنما" else "Show help")
                )
            )
            return
        }

        viewModelScope.launch {
            val bot = botRepository.getBot(botId)
            if (bot != null) {
                _editorState.value = BotEditorState(
                    editingLocalId = bot.localId,
                    name = bot.name,
                    handle = bot.handle,
                    description = bot.description,
                    welcomeMessage = bot.welcomeMessage,
                    systemPrompt = bot.systemPrompt,
                    aiPowered = bot.aiPowered,
                    activateOnServer = bot.isActive || bot.remoteId == null,
                    commands = bot.commands.ifEmpty {
                        listOf(BotCommandItem("/start", bot.welcomeMessage, "Start"))
                    },
                    botToken = bot.botToken,
                    webhookUrl = bot.webhookUrl
                )
            }
        }
    }

    fun onAiGoalChange(goal: String) {
        _editorState.update { it.copy(aiGoalPrompt = goal, errorBanner = null) }
    }

    fun onNameChange(value: String) {
        _editorState.update { state ->
            val autoHandle = if (state.editingLocalId == null && state.handle.isBlank()) {
                val cleaned = value.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
                if (cleaned.length >= 3) "${cleaned}_bot".take(24) else ""
            } else {
                state.handle
            }
            state.copy(
                name = value,
                handle = autoHandle,
                validationNameEmpty = false,
                errorBanner = null
            )
        }
    }

    fun onHandleChange(value: String) {
        _editorState.update { it.copy(handle = value, errorBanner = null) }
    }

    fun onDescriptionChange(value: String) {
        _editorState.update { it.copy(description = value) }
    }

    fun onWelcomeChange(value: String) {
        _editorState.update { it.copy(welcomeMessage = value) }
    }

    fun onSystemPromptChange(value: String) {
        _editorState.update { it.copy(systemPrompt = value) }
    }

    fun onAiPoweredToggle(enabled: Boolean) {
        _editorState.update { it.copy(aiPowered = enabled) }
    }

    fun onActivateOnServerToggle(enabled: Boolean) {
        _editorState.update { it.copy(activateOnServer = enabled) }
    }

    fun addEmptyCommand() {
        _editorState.update {
            it.copy(commands = it.commands + BotCommandItem("/cmd", "", ""))
        }
    }

    fun updateCommand(index: Int, updated: BotCommandItem) {
        _editorState.update { state ->
            val list = state.commands.toMutableList()
            if (index in list.indices) {
                list[index] = updated
            }
            state.copy(commands = list)
        }
    }

    fun removeCommand(index: Int) {
        _editorState.update { state ->
            val list = state.commands.toMutableList()
            if (index in list.indices && list.size > 1) {
                list.removeAt(index)
            }
            state.copy(commands = list)
        }
    }

    fun generateBotWithAi(language: UiLanguage) {
        val goal = _editorState.value.aiGoalPrompt.trim()
        if (goal.isEmpty() || _editorState.value.isGeneratingWithAi) return

        _editorState.update { it.copy(isGeneratingWithAi = true, errorBanner = null) }
        viewModelScope.launch {
            val result = aiRepository.generateBotDraft(goal, language)
            result.onSuccess { draft ->
                _editorState.update { state ->
                    state.copy(
                        isGeneratingWithAi = false,
                        name = draft.name,
                        handle = draft.handle,
                        description = draft.description,
                        welcomeMessage = draft.welcomeMessage,
                        systemPrompt = draft.systemPrompt,
                        commands = draft.commands.ifEmpty { state.commands },
                        aiPowered = true,
                        validationNameEmpty = false
                    )
                }
            }.onFailure { err ->
                _editorState.update {
                    it.copy(
                        isGeneratingWithAi = false,
                        errorBanner = err.localizedMessage ?: "Failed to generate bot blueprint."
                    )
                }
            }
        }
    }

    fun saveAndSyncBot(onSuccess: () -> Unit) {
        val state = _editorState.value
        if (state.name.trim().isEmpty()) {
            _editorState.update { it.copy(validationNameEmpty = true) }
            return
        }
        if (state.isSaving) return

        val sanitizedCommands = state.commands.mapNotNull { cmd ->
            val cleanCmd = cmd.command.trim()
            if (cleanCmd.isEmpty()) null
            else cmd.copy(command = if (cleanCmd.startsWith("/")) cleanCmd else "/$cleanCmd")
        }

        _editorState.update { it.copy(isSaving = true, errorBanner = null) }
        viewModelScope.launch {
            when (
                val res = botRepository.saveAndSyncBot(
                    existingLocalId = state.editingLocalId,
                    name = state.name,
                    handle = state.handle.ifBlank { state.name },
                    description = state.description,
                    systemPrompt = state.systemPrompt,
                    welcomeMessage = state.welcomeMessage,
                    commands = sanitizedCommands,
                    aiPowered = state.aiPowered,
                    activateOnServer = state.activateOnServer
                )
            ) {
                is BotServerActionResult.Success -> {
                    _editorState.update {
                        it.copy(
                            editingLocalId = res.bot.localId,
                            botToken = res.bot.botToken,
                            webhookUrl = res.bot.webhookUrl,
                            isSaving = false
                        )
                    }
                    _events.emit(
                        BotHubEvent.Toast(
                            if (res.bot.isActive) BotToastType.ACTIVATED else BotToastType.SAVED
                        )
                    )
                    onSuccess()
                }
                is BotServerActionResult.PartialLocalSaved -> {
                    _editorState.update {
                        it.copy(
                            editingLocalId = res.bot.localId,
                            isSaving = false,
                            errorBanner = res.detail
                        )
                    }
                    _events.emit(BotHubEvent.Toast(BotToastType.PARTIAL_SYNC, res.detail))
                    onSuccess()
                }
            }
        }
    }

    // --- Sandbox Live Chat methods ---

    fun openSandbox(botId: String) {
        viewModelScope.launch {
            val bot = botRepository.getBot(botId)
            val initialMsgs = if (bot != null && bot.welcomeMessage.isNotBlank()) {
                listOf(
                    BotSandboxMessage(
                        id = "welcome_${bot.localId}",
                        role = AiRole.MODEL,
                        text = bot.welcomeMessage,
                        matchedCommand = "/start"
                    )
                )
            } else {
                emptyList()
            }
            _sandboxState.value = BotSandboxState(
                bot = bot,
                messages = initialMsgs,
                draftText = "",
                isSending = false
            )
        }
    }

    fun onSandboxDraftChange(text: String) {
        _sandboxState.update { it.copy(draftText = text) }
    }

    fun sendSandboxMessage(overrideText: String? = null) {
        val current = _sandboxState.value
        val bot = current.bot ?: return
        val text = (overrideText ?: current.draftText).trim()
        if (text.isEmpty() || current.isSending) return

        val userMsg = BotSandboxMessage(
            id = "u_${System.currentTimeMillis()}",
            role = AiRole.USER,
            text = text
        )
        val updatedMessages = current.messages + userMsg
        _sandboxState.update {
            it.copy(
                messages = updatedMessages,
                draftText = if (overrideText == null) "" else it.draftText,
                isSending = true
            )
        }

        val history = updatedMessages.map { it.role to it.text }
        viewModelScope.launch {
            val botMsg = when (val res = botRepository.testBotMessage(bot, text, history.dropLast(1))) {
                is AiBotReplyResult.Success -> BotSandboxMessage(
                    id = "b_${System.currentTimeMillis()}",
                    role = AiRole.MODEL,
                    text = res.text,
                    matchedCommand = res.matchedCommand
                )
                is AiBotReplyResult.Error -> BotSandboxMessage(
                    id = "e_${System.currentTimeMillis()}",
                    role = AiRole.MODEL,
                    text = res.message,
                    isError = true
                )
            }
            _sandboxState.update {
                it.copy(
                    messages = it.messages + botMsg,
                    isSending = false
                )
            }
        }
    }
}
