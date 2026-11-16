package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.models.AiChatMessage
import com.example.models.AiRole
import com.example.models.AiTrainingExample
import com.example.models.BotCommandDto
import com.example.models.BotCommandItem
import com.example.models.BotSyncStatus
import com.example.models.ChatMessage
import com.example.models.ConversationSummary
import com.example.models.CustomBot
import com.example.models.UserProfile
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val id: Long,
    val displayName: String,
    val bio: String,
    val photoUrl: String?,
    val isOnline: Boolean,
    val lastSeen: String?
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val peerUsername: String,
    val peerDisplayName: String,
    val peerPhotoUrl: String?,
    val peerOnline: Boolean,
    val peerLastSeen: String?,
    val lastMessage: String,
    val lastMessageTimeIso: String,
    val lastMessageTimestampMillis: Long,
    val isLastMessageOutgoing: Boolean,
    val isLastMessageRead: Boolean,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val draftText: String = "",
    val isLocallyDeleted: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val localKey: String,
    val serverId: Long,
    val peerUsername: String,
    val senderUsername: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val content: String,
    val createdAtIso: String,
    val timestampMillis: Long,
    val readAtIso: String?,
    val isOutgoing: Boolean,
    val isP2PMedia: Boolean = false,
    val mediaPath: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaSizeBytes: Long = 0L,
    val replyToKey: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val isLocallyDeleted: Boolean = false
)

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String,
    val content: String,
    val timestampMillis: Long,
    val isError: Boolean = false
)

@Entity(tableName = "ai_training_examples")
data class AiTrainingExampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val userPrompt: String,
    val idealReply: String,
    val createdAtMillis: Long
)

@Entity(tableName = "bots")
data class BotEntity(
    @PrimaryKey val localId: String,
    val remoteId: Long? = null,
    val name: String,
    val handle: String,
    val description: String,
    val systemPrompt: String,
    val welcomeMessage: String,
    val commandsJson: String,
    val aiPowered: Boolean = true,
    val isActive: Boolean = false,
    val botToken: String? = null,
    val webhookUrl: String? = null,
    val syncStatus: String = BotSyncStatus.PENDING_SERVER_SYNC.name,
    val lastSyncMessage: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

fun UserProfile.toEntity(): UserEntity = UserEntity(
    username = username,
    id = id,
    displayName = displayName,
    bio = bio,
    photoUrl = photoUrl,
    isOnline = isOnline,
    lastSeen = lastSeen
)

fun UserEntity.toDomain(): UserProfile = UserProfile(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    photoUrl = photoUrl,
    isOnline = isOnline,
    lastSeen = lastSeen
)

fun ConversationEntity.toDomain(): ConversationSummary = ConversationSummary(
    peerUsername = peerUsername,
    peerDisplayName = peerDisplayName,
    peerPhotoUrl = peerPhotoUrl,
    peerOnline = peerOnline,
    peerLastSeen = peerLastSeen,
    lastMessage = lastMessage,
    lastMessageTimeIso = lastMessageTimeIso,
    lastMessageTimestampMillis = lastMessageTimestampMillis,
    isLastMessageOutgoing = isLastMessageOutgoing,
    isLastMessageRead = isLastMessageRead,
    unreadCount = unreadCount,
    isPinned = isPinned,
    isMuted = isMuted,
    isArchived = isArchived,
    draftText = draftText
)

fun MessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = serverId,
    localKey = localKey,
    peerUsername = peerUsername,
    senderUsername = senderUsername,
    senderName = senderName,
    senderPhotoUrl = senderPhotoUrl,
    content = content,
    createdAtIso = createdAtIso,
    timestampMillis = timestampMillis,
    readAtIso = readAtIso,
    isOutgoing = isOutgoing,
    isP2PMedia = isP2PMedia,
    mediaPath = mediaPath,
    mediaMimeType = mediaMimeType,
    mediaFileName = mediaFileName,
    mediaSizeBytes = mediaSizeBytes,
    replyToKey = replyToKey,
    replyToSender = replyToSender,
    replyToText = replyToText
)

fun AiMessageEntity.toDomain(): AiChatMessage = AiChatMessage(
    id = id,
    role = if (role == AiRole.USER.name) AiRole.USER else AiRole.MODEL,
    content = content,
    timestampMillis = timestampMillis,
    isError = isError
)

fun AiTrainingExampleEntity.toDomain(): AiTrainingExample = AiTrainingExample(
    id = id,
    userPrompt = userPrompt,
    idealReply = idealReply,
    createdAtMillis = createdAtMillis
)

fun BotEntity.toDomain(): CustomBot = CustomBot(
    localId = localId,
    remoteId = remoteId,
    name = name,
    handle = handle,
    description = description,
    systemPrompt = systemPrompt,
    welcomeMessage = welcomeMessage,
    commands = decodeBotCommands(commandsJson),
    aiPowered = aiPowered,
    isActive = isActive,
    botToken = botToken,
    webhookUrl = webhookUrl,
    syncStatus = runCatching { BotSyncStatus.valueOf(syncStatus) }
        .getOrDefault(BotSyncStatus.PENDING_SERVER_SYNC),
    lastSyncMessage = lastSyncMessage,
    updatedAtMillis = updatedAtMillis
)

fun CustomBot.toEntity(): BotEntity = BotEntity(
    localId = localId,
    remoteId = remoteId,
    name = name,
    handle = handle,
    description = description,
    systemPrompt = systemPrompt,
    welcomeMessage = welcomeMessage,
    commandsJson = encodeBotCommands(commands),
    aiPowered = aiPowered,
    isActive = isActive,
    botToken = botToken,
    webhookUrl = webhookUrl,
    syncStatus = syncStatus.name,
    lastSyncMessage = lastSyncMessage,
    updatedAtMillis = updatedAtMillis
)

fun encodeBotCommands(commands: List<BotCommandItem>): String {
    val array = JSONArray()
    commands.forEach { cmd ->
        val obj = JSONObject()
        obj.put("command", cmd.command)
        obj.put("response", cmd.response)
        obj.put("description", cmd.description)
        array.put(obj)
    }
    return array.toString()
}

fun decodeBotCommands(json: String): List<BotCommandItem> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { idx ->
            val obj = array.optJSONObject(idx) ?: return@mapNotNull null
            val cmd = obj.optString("command").trim()
            if (cmd.isEmpty()) return@mapNotNull null
            BotCommandItem(
                command = cmd,
                response = obj.optString("response"),
                description = obj.optString("description")
            )
        }
    }.getOrDefault(emptyList())
}

fun List<BotCommandItem>.toDtoList(): List<BotCommandDto> = map {
    BotCommandDto(command = it.command, response = it.response, description = it.description)
}
