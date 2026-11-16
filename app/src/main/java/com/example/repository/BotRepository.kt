package com.example.repository

import com.example.ai.AiBotReplyResult
import com.example.ai.P2PAiRepository
import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.AppError
import com.example.api.P2PApiService
import com.example.database.ChatDao
import com.example.database.toDomain
import com.example.database.toDtoList
import com.example.database.toEntity
import com.example.models.AiRole
import com.example.models.BotCommandItem
import com.example.models.BotDto
import com.example.models.BotSyncStatus
import com.example.models.BotTestRequest
import com.example.models.CreateBotRequest
import com.example.models.CustomBot
import com.example.models.UpdateBotRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

sealed interface BotServerActionResult {
    data class Success(val bot: CustomBot) : BotServerActionResult
    data class PartialLocalSaved(
        val bot: CustomBot,
        val error: AppError,
        val detail: String? = null
    ) : BotServerActionResult
}

class BotRepository(
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val chatDao: ChatDao,
    private val aiRepository: P2PAiRepository
) {
    val botsFlow: Flow<List<CustomBot>> = chatDao.observeBots().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getBot(localId: String): CustomBot? = chatDao.getBot(localId)?.toDomain()

    suspend fun syncBotsFromServer(): ApiResult<List<CustomBot>> {
        return when (val result = errorHandler.safeCall { api.getBots() }) {
            is ApiResult.Success -> {
                val mapped = result.data.map { dto ->
                    val existing = chatDao.getBotByRemoteId(dto.id)
                    dto.toDomain(localId = existing?.localId ?: "remote_${dto.id}")
                }
                chatDao.upsertBots(mapped.map { it.toEntity() })
                ApiResult.Success(mapped)
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun saveAndSyncBot(
        existingLocalId: String?,
        name: String,
        handle: String,
        description: String,
        systemPrompt: String,
        welcomeMessage: String,
        commands: List<BotCommandItem>,
        aiPowered: Boolean,
        activateOnServer: Boolean
    ): BotServerActionResult {
        val cleanHandle = normalizeHandle(handle)
        val localId = existingLocalId ?: UUID.randomUUID().toString()
        val previous = existingLocalId?.let { chatDao.getBot(it)?.toDomain() }

        val draftBot = CustomBot(
            localId = localId,
            remoteId = previous?.remoteId,
            name = name.trim(),
            handle = cleanHandle,
            description = description.trim(),
            systemPrompt = systemPrompt.trim(),
            welcomeMessage = welcomeMessage.trim(),
            commands = commands,
            aiPowered = aiPowered,
            isActive = previous?.isActive ?: false,
            botToken = previous?.botToken,
            webhookUrl = previous?.webhookUrl,
            syncStatus = BotSyncStatus.PENDING_SERVER_SYNC,
            lastSyncMessage = null,
            updatedAtMillis = System.currentTimeMillis()
        )
        chatDao.upsertBot(draftBot.toEntity())

        val remoteId = draftBot.remoteId
        val apiResult: ApiResult<BotDto> = if (remoteId == null) {
            errorHandler.safeCall {
                api.createBot(
                    CreateBotRequest(
                        name = draftBot.name,
                        handle = draftBot.handle,
                        description = draftBot.description,
                        systemPrompt = draftBot.systemPrompt,
                        welcomeMessage = draftBot.welcomeMessage,
                        commands = draftBot.commands.toDtoList(),
                        aiPowered = draftBot.aiPowered,
                        activateNow = activateOnServer
                    )
                )
            }
        } else {
            val updated = errorHandler.safeCall {
                api.updateBot(
                    botId = remoteId,
                    request = UpdateBotRequest(
                        name = draftBot.name,
                        description = draftBot.description,
                        systemPrompt = draftBot.systemPrompt,
                        welcomeMessage = draftBot.welcomeMessage,
                        commands = draftBot.commands.toDtoList(),
                        aiPowered = draftBot.aiPowered
                    )
                )
            }
            if (updated is ApiResult.Success && activateOnServer && !updated.data.isActive) {
                errorHandler.safeCall { api.activateBot(remoteId) }
            } else {
                updated
            }
        }

        return when (apiResult) {
            is ApiResult.Success -> {
                var synced = apiResult.data.toDomain(localId = localId)
                if (activateOnServer && !synced.isActive && synced.remoteId != null) {
                    val actRes = errorHandler.safeCall { api.activateBot(synced.remoteId!!) }
                    if (actRes is ApiResult.Success) {
                        synced = actRes.data.toDomain(localId = localId)
                    }
                }
                chatDao.upsertBot(synced.toEntity())
                BotServerActionResult.Success(synced)
            }
            is ApiResult.Failure -> {
                val errSummary = apiResult.error.syncSummary()
                val failed = draftBot.copy(
                    syncStatus = BotSyncStatus.SYNC_FAILED,
                    lastSyncMessage = errSummary
                )
                chatDao.upsertBot(failed.toEntity())
                BotServerActionResult.PartialLocalSaved(
                    bot = failed,
                    error = apiResult.error,
                    detail = errSummary
                )
            }
        }
    }

    suspend fun activateBotOnServer(localId: String): BotServerActionResult {
        val current = chatDao.getBot(localId)?.toDomain()
            ?: return BotServerActionResult.PartialLocalSaved(
                bot = CustomBot(
                    localId = localId,
                    name = "",
                    handle = "",
                    description = "",
                    systemPrompt = "",
                    welcomeMessage = "",
                    commands = emptyList()
                ),
                error = AppError.NotFound
            )

        val remoteId = current.remoteId
        val result: ApiResult<BotDto> = if (remoteId == null) {
            errorHandler.safeCall {
                api.createBot(
                    CreateBotRequest(
                        name = current.name,
                        handle = current.handle,
                        description = current.description,
                        systemPrompt = current.systemPrompt,
                        welcomeMessage = current.welcomeMessage,
                        commands = current.commands.toDtoList(),
                        aiPowered = current.aiPowered,
                        activateNow = true
                    )
                )
            }
        } else {
            errorHandler.safeCall { api.activateBot(remoteId) }
        }

        return when (result) {
            is ApiResult.Success -> {
                var synced = result.data.toDomain(localId = localId)
                if (!synced.isActive && synced.remoteId != null) {
                    val act = errorHandler.safeCall { api.activateBot(synced.remoteId!!) }
                    if (act is ApiResult.Success) {
                        synced = act.data.toDomain(localId = localId)
                    }
                }
                chatDao.upsertBot(synced.toEntity())
                BotServerActionResult.Success(synced)
            }
            is ApiResult.Failure -> {
                val errSummary = result.error.syncSummary()
                val failed = current.copy(
                    syncStatus = BotSyncStatus.SYNC_FAILED,
                    lastSyncMessage = errSummary,
                    updatedAtMillis = System.currentTimeMillis()
                )
                chatDao.upsertBot(failed.toEntity())
                BotServerActionResult.PartialLocalSaved(failed, result.error, errSummary)
            }
        }
    }

    suspend fun deactivateBotOnServer(localId: String): BotServerActionResult {
        val current = chatDao.getBot(localId)?.toDomain()
            ?: return BotServerActionResult.PartialLocalSaved(
                bot = CustomBot(
                    localId = localId,
                    name = "",
                    handle = "",
                    description = "",
                    systemPrompt = "",
                    welcomeMessage = "",
                    commands = emptyList()
                ),
                error = AppError.NotFound
            )

        val remoteId = current.remoteId
        if (remoteId == null) {
            val paused = current.copy(
                isActive = false,
                syncStatus = BotSyncStatus.PENDING_SERVER_SYNC,
                updatedAtMillis = System.currentTimeMillis()
            )
            chatDao.upsertBot(paused.toEntity())
            return BotServerActionResult.Success(paused)
        }

        return when (val result = errorHandler.safeCall { api.deactivateBot(remoteId) }) {
            is ApiResult.Success -> {
                val updated = result.data.toDomain(localId = localId)
                chatDao.upsertBot(updated.toEntity())
                BotServerActionResult.Success(updated)
            }
            is ApiResult.Failure -> {
                val errSummary = result.error.syncSummary()
                val failed = current.copy(
                    syncStatus = BotSyncStatus.SYNC_FAILED,
                    lastSyncMessage = errSummary
                )
                chatDao.upsertBot(failed.toEntity())
                BotServerActionResult.PartialLocalSaved(failed, result.error, errSummary)
            }
        }
    }

    suspend fun deleteBot(localId: String): ApiResult<Unit> {
        val current = chatDao.getBot(localId)?.toDomain()
        val remoteId = current?.remoteId
        if (remoteId != null) {
            when (val res = errorHandler.safeCall { api.deleteBot(remoteId) }) {
                is ApiResult.Failure -> {
                    if (res.error != AppError.NotFound) {
                        return res
                    }
                }
                is ApiResult.Success -> Unit
            }
        }
        chatDao.deleteBot(localId)
        return ApiResult.Success(Unit)
    }

    suspend fun testBotMessage(
        bot: CustomBot,
        message: String,
        history: List<Pair<AiRole, String>>
    ): AiBotReplyResult {
        // Use server /test endpoint when synced & active and not AI-only or for deterministic command test
        if (bot.remoteId != null && bot.isActive && !bot.aiPowered) {
            when (val res = errorHandler.safeCall { api.testBot(bot.remoteId, BotTestRequest(message)) }) {
                is ApiResult.Success -> {
                    return AiBotReplyResult.Success(
                        text = res.data.reply,
                        matchedCommand = res.data.matchedCommand
                    )
                }
                is ApiResult.Failure -> {
                    // Fall through to local command/AI engine only if AI is enabled
                    if (!bot.aiPowered) {
                        return AiBotReplyResult.Error(res.error.syncSummary())
                    }
                }
            }
        }
        return aiRepository.replyAsBot(bot, message, history)
    }

    private fun AppError.syncSummary(): String =
        httpCode?.let { "HTTP $it" } ?: (this::class.simpleName ?: "SyncError")

    private fun normalizeHandle(raw: String): String {
        val cleaned = raw.trim()
            .lowercase()
            .removePrefix("@")
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
        val base = cleaned.ifEmpty { "custom_bot" }
        val withSuffix = if (base.endsWith("_bot")) base else "${base}_bot"
        return withSuffix.take(24)
    }

    private fun BotDto.toDomain(localId: String): CustomBot = CustomBot(
        localId = localId,
        remoteId = id,
        name = name,
        handle = handle,
        description = description,
        systemPrompt = systemPrompt,
        welcomeMessage = welcomeMessage,
        commands = commands.map {
            BotCommandItem(
                command = it.command,
                response = it.response,
                description = it.description
            )
        },
        aiPowered = aiPowered,
        isActive = isActive,
        botToken = botToken,
        webhookUrl = webhookUrl,
        syncStatus = if (isActive) BotSyncStatus.ACTIVE_ON_SERVER else BotSyncStatus.PAUSED_ON_SERVER,
        lastSyncMessage = null,
        updatedAtMillis = System.currentTimeMillis()
    )
}
