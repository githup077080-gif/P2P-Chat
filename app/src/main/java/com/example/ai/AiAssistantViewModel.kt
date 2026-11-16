package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.models.AiChatMessage
import com.example.models.AiPersonaMode
import com.example.models.AiTrainingExample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiAssistantUiState(
    val draftText: String = "",
    val streamingReply: String = "",
    val isStreaming: Boolean = false,
    val errorMessage: String? = null
)

class AiAssistantViewModel(
    private val aiRepository: P2PAiRepository
) : ViewModel() {

    val messages: StateFlow<List<AiChatMessage>> = aiRepository.messagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val trainingConfig: StateFlow<AiTrainingConfig> = aiRepository.trainingConfig

    val trainingExamples: StateFlow<List<AiTrainingExample>> = aiRepository.trainingExamplesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(AiAssistantUiState())
    val uiState: StateFlow<AiAssistantUiState> = _uiState.asStateFlow()

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draftText = value, errorMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun sendMessage(overridePrompt: String? = null) {
        val text = (overridePrompt ?: _uiState.value.draftText).trim()
        if (text.isEmpty() || _uiState.value.isStreaming) return

        _uiState.update {
            it.copy(
                draftText = if (overridePrompt == null) "" else it.draftText,
                streamingReply = "",
                isStreaming = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = aiRepository.sendMessageStream(text) { partial ->
                _uiState.update { it.copy(streamingReply = partial) }
            }
            _uiState.update { state ->
                state.copy(
                    isStreaming = false,
                    streamingReply = "",
                    errorMessage = result.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun selectPersona(mode: AiPersonaMode) {
        aiRepository.updatePersonaMode(mode)
    }

    fun saveCustomInstruction(instruction: String) {
        aiRepository.updateCustomInstruction(instruction)
    }

    fun addTrainingExample(prompt: String, idealReply: String) {
        viewModelScope.launch {
            aiRepository.addTrainingExample(prompt, idealReply)
        }
    }

    fun deleteTrainingExample(id: Long) {
        viewModelScope.launch {
            aiRepository.deleteTrainingExample(id)
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            aiRepository.clearConversation()
            _uiState.update { it.copy(streamingReply = "", errorMessage = null) }
        }
    }
}
