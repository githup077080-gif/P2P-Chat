package com.example.ai

import android.content.Context
import android.util.Log
import com.example.database.AiMessageEntity
import com.example.database.AiTrainingExampleEntity
import com.example.database.ChatDao
import com.example.database.toDomain
import com.example.models.AiChatMessage
import com.example.models.AiPersonaMode
import com.example.models.AiRole
import com.example.models.AiTrainingExample
import com.example.models.BotCommandItem
import com.example.models.CustomBot
import com.example.models.UiLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "P2PAiRepository"
private const val AI_MODEL_NAME = "gemini-3.8-flash"
private const val PREFS_NAME = "p2p_ai_training_prefs"
private const val KEY_PERSONA_MODE = "persona_mode"
private const val KEY_CUSTOM_INSTRUCTION = "custom_instruction"
private const val AI_TIMEOUT_MS = 60_000L

data class AiTrainingConfig(
    val personaMode: AiPersonaMode = AiPersonaMode.NOVA_ASSISTANT,
    val customInstruction: String = ""
)

data class GeneratedBotDraft(
    val name: String,
    val handle: String,
    val description: String,
    val welcomeMessage: String,
    val systemPrompt: String,
    val commands: List<BotCommandItem>
)

sealed interface AiBotReplyResult {
    data class Success(val text: String, val matchedCommand: String? = null) : AiBotReplyResult
    data class Error(val message: String) : AiBotReplyResult
}

class P2PAiRepository(
    context: Context,
    private val chatDao: ChatDao
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _trainingConfig = MutableStateFlow(
        AiTrainingConfig(
            personaMode = AiPersonaMode.fromKey(prefs.getString(KEY_PERSONA_MODE, null)),
            customInstruction = prefs.getString(KEY_CUSTOM_INSTRUCTION, "").orEmpty()
        )
    )
    val trainingConfig: StateFlow<AiTrainingConfig> = _trainingConfig.asStateFlow()

    val messagesFlow: Flow<List<AiChatMessage>> = chatDao.observeAiMessages().map { list ->
        list.map { it.toDomain() }
    }

    val trainingExamplesFlow: Flow<List<AiTrainingExample>> =
        chatDao.observeTrainingExamples().map { list -> list.map { it.toDomain() } }

    private val botArchitectModel by lazy {
        val botCommandSchema = Schema.obj(
            properties = mapOf(
                "command" to Schema.string(description = "Slash command starting with /, e.g. /start or /status"),
                "description" to Schema.string(description = "Short description of what the command does"),
                "response" to Schema.string(description = "Automated reply returned when the command is invoked")
            )
        )
        val botSchema = Schema.obj(
            properties = mapOf(
                "name" to Schema.string(description = "Display name of the bot"),
                "handle" to Schema.string(description = "Lowercase alphanumeric/underscore handle ending in _bot, 4-24 chars"),
                "description" to Schema.string(description = "One-sentence summary of the bot"),
                "welcomeMessage" to Schema.string(description = "Greeting sent on /start"),
                "systemPrompt" to Schema.string(description = "Detailed persona and behavior rules for the bot"),
                "commands" to Schema.array(
                    items = botCommandSchema,
                    description = "3 to 5 useful slash commands including /start and /help"
                )
            )
        )
        Firebase.ai.generativeModel(
            modelName = AI_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = botSchema
                temperature = 0.6f
            },
            systemInstruction = content {
                text(
                    "You are a senior conversational bot architect for P2P CHAT. " +
                        "Given a user goal, design a practical, polite, well-structured chat bot. " +
                        "Match the language of the user's request (Persian or English) for name, description, welcomeMessage, systemPrompt, and command replies. " +
                        "Ensure handle uses only lowercase English letters, digits, and underscores, and ends with _bot."
                )
            }
        )
    }

    fun updatePersonaMode(mode: AiPersonaMode) {
        prefs.edit().putString(KEY_PERSONA_MODE, mode.key).apply()
        _trainingConfig.value = _trainingConfig.value.copy(personaMode = mode)
    }

    fun updateCustomInstruction(instruction: String) {
        val clean = instruction.trim()
        prefs.edit().putString(KEY_CUSTOM_INSTRUCTION, clean).apply()
        _trainingConfig.value = _trainingConfig.value.copy(customInstruction = clean)
    }

    suspend fun addTrainingExample(userPrompt: String, idealReply: String) {
        val cleanPrompt = userPrompt.trim()
        val cleanReply = idealReply.trim()
        if (cleanPrompt.isEmpty() || cleanReply.isEmpty()) return
        chatDao.insertTrainingExample(
            AiTrainingExampleEntity(
                userPrompt = cleanPrompt,
                idealReply = cleanReply,
                createdAtMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteTrainingExample(id: Long) {
        chatDao.deleteTrainingExample(id)
    }

    suspend fun clearConversation() {
        chatDao.clearAiMessages()
    }

    suspend fun sendMessageStream(
        prompt: String,
        onStreamingUpdate: (String) -> Unit
    ): Result<String> {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        val existingHistory = chatDao.getAiMessages()
        val userExamples = chatDao.getTrainingExamples().map { it.toDomain() }
        val config = _trainingConfig.value

        chatDao.insertAiMessage(
            AiMessageEntity(
                role = AiRole.USER.name,
                content = cleanPrompt,
                timestampMillis = System.currentTimeMillis()
            )
        )

        val accumulated = StringBuilder()
        return try {
            val model = Firebase.ai.generativeModel(
                modelName = AI_MODEL_NAME,
                generationConfig = generationConfig {
                    temperature = 0.7f
                },
                systemInstruction = content {
                    text(buildTrainedSystemPrompt(config, userExamples))
                }
            )

            val fullHistory = buildTrainedHistory(
                mode = config.personaMode,
                userExamples = userExamples,
                persistedMessages = existingHistory
            )
            val chat = model.startChat(history = fullHistory)

            withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessageStream(cleanPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        onStreamingUpdate(accumulated.toString())
                    }
                }
            }

            val finalReply = accumulated.toString().trim()
            if (finalReply.isEmpty()) {
                val emptyErr = "AI returned an empty response."
                chatDao.insertAiMessage(
                    AiMessageEntity(
                        role = AiRole.MODEL.name,
                        content = emptyErr,
                        timestampMillis = System.currentTimeMillis(),
                        isError = true
                    )
                )
                Result.failure(IllegalStateException(emptyErr))
            } else {
                chatDao.insertAiMessage(
                    AiMessageEntity(
                        role = AiRole.MODEL.name,
                        content = finalReply,
                        timestampMillis = System.currentTimeMillis(),
                        isError = false
                    )
                )
                Result.success(finalReply)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI generation timed out", e)
            val msg = "Request timed out after 60 seconds. Please try again."
            chatDao.insertAiMessage(
                AiMessageEntity(
                    role = AiRole.MODEL.name,
                    content = msg,
                    timestampMillis = System.currentTimeMillis(),
                    isError = true
                )
            )
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            val msg = e.localizedMessage ?: "AI generation failed."
            chatDao.insertAiMessage(
                AiMessageEntity(
                    role = AiRole.MODEL.name,
                    content = msg,
                    timestampMillis = System.currentTimeMillis(),
                    isError = true
                )
            )
            Result.failure(e)
        }
    }

    suspend fun generateBotDraft(goal: String, language: UiLanguage): Result<GeneratedBotDraft> {
        val cleanGoal = goal.trim()
        if (cleanGoal.isEmpty()) {
            return Result.failure(IllegalArgumentException("Goal description cannot be empty."))
        }
        return try {
            val langHint = if (language == UiLanguage.PERSIAN) "Persian (fa)" else "English (en)"
            val prompt = "Design a P2P CHAT bot for this purpose (prefer $langHint unless the goal specifies another language): $cleanGoal"
            val response = withTimeout(AI_TIMEOUT_MS) {
                botArchitectModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned an empty response.")
            val json = JSONObject(rawJson)
            val rawHandle = json.optString("handle", "assistant_bot")
                .lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
                .ifEmpty { "custom_bot" }
            val normalizedHandle = if (rawHandle.endsWith("_bot")) rawHandle else "${rawHandle}_bot"
            val cmdsJson = json.optJSONArray("commands")
            val commands = if (cmdsJson != null) {
                (0 until cmdsJson.length()).mapNotNull { i ->
                    val item = cmdsJson.optJSONObject(i) ?: return@mapNotNull null
                    val cmdText = item.optString("command").trim()
                    if (cmdText.isEmpty()) return@mapNotNull null
                    BotCommandItem(
                        command = if (cmdText.startsWith("/")) cmdText else "/$cmdText",
                        description = item.optString("description"),
                        response = item.optString("response")
                    )
                }
            } else {
                emptyList()
            }
            Result.success(
                GeneratedBotDraft(
                    name = json.optString("name", "Nova Bot"),
                    handle = normalizedHandle.take(24),
                    description = json.optString("description"),
                    welcomeMessage = json.optString("welcomeMessage"),
                    systemPrompt = json.optString("systemPrompt"),
                    commands = commands
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bot generation timed out", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bot generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun replyAsBot(
        bot: CustomBot,
        userMessage: String,
        history: List<Pair<AiRole, String>> = emptyList()
    ): AiBotReplyResult {
        val trimmed = userMessage.trim()
        if (trimmed.isEmpty()) {
            return AiBotReplyResult.Error("Message cannot be empty.")
        }

        // 1. Exact slash command handling
        val token = trimmed.split(Regex("\\s+")).firstOrNull()?.lowercase().orEmpty()
        if (token == "/start" && bot.welcomeMessage.isNotBlank()) {
            return AiBotReplyResult.Success(bot.welcomeMessage, matchedCommand = "/start")
        }
        val matched = bot.commands.firstOrNull { it.command.equals(token, ignoreCase = true) }
        if (matched != null && matched.response.isNotBlank()) {
            return AiBotReplyResult.Success(matched.response, matchedCommand = matched.command)
        }
        if (token == "/help" && bot.commands.isNotEmpty()) {
            val helpText = buildString {
                append(bot.name)
                if (bot.description.isNotBlank()) append(" — ${bot.description}")
                append("\n\n")
                bot.commands.forEach { cmd ->
                    append("${cmd.command}")
                    if (cmd.description.isNotBlank()) append(": ${cmd.description}")
                    append("\n")
                }
            }.trim()
            return AiBotReplyResult.Success(helpText, matchedCommand = "/help")
        }

        if (!bot.aiPowered) {
            val fallback = bot.welcomeMessage.ifBlank {
                "Command not recognized. Send /help to see available commands."
            }
            return AiBotReplyResult.Success(fallback)
        }

        // 2. AI-powered bot response via Firebase AI Logic
        return try {
            val botInstruction = buildString {
                append("You are ${bot.name} (@${bot.handle}). ")
                if (bot.description.isNotBlank()) append("Role: ${bot.description}. ")
                if (bot.systemPrompt.isNotBlank()) append("\nBehavior rules:\n${bot.systemPrompt}\n")
                if (bot.commands.isNotEmpty()) {
                    append("\nSupported commands users can run: ")
                    append(bot.commands.joinToString(", ") { it.command })
                }
                append("\nReply clearly, concisely, and match the user's language (Persian or English).")
            }
            val model = Firebase.ai.generativeModel(
                modelName = AI_MODEL_NAME,
                systemInstruction = content { text(botInstruction) }
            )
            val chatHistory = history.takeLast(12).map { (role, text) ->
                content(if (role == AiRole.USER) "user" else "model") { text(text) }
            }
            val chat = model.startChat(history = chatHistory)
            val response = withTimeout(AI_TIMEOUT_MS) {
                chat.sendMessage(trimmed)
            }
            val reply = response.text?.trim()
            if (reply.isNullOrBlank()) {
                AiBotReplyResult.Error("Bot AI returned an empty response.")
            } else {
                AiBotReplyResult.Success(reply)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Bot AI reply timed out", e)
            AiBotReplyResult.Error("Bot response timed out after 60 seconds.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Bot AI reply failed", e)
            AiBotReplyResult.Error(e.localizedMessage ?: "Failed to generate bot response.")
        }
    }

    private fun buildTrainedSystemPrompt(
        config: AiTrainingConfig,
        examples: List<AiTrainingExample>
    ): String = buildString {
        append(
            "You are Nova AI, the built-in trained intelligent assistant inside P2P CHAT. " +
                "You are fluent in both Persian (Farsi, natural & polite) and English. " +
                "Always reply in the same language the user writes unless they ask for translation. " +
                "Keep answers clear, structured, practical, and friendly.\n\n"
        )
        when (config.personaMode) {
            AiPersonaMode.NOVA_ASSISTANT -> append(
                "Active Specialty: Smart Messaging & Productivity Assistant. " +
                    "Help users write better messages, summarize chats, answer technical or everyday questions, and organize ideas."
            )
            AiPersonaMode.TRANSLATOR -> append(
                "Active Specialty: Bilingual Persian <-> English Translator. " +
                    "Provide natural, context-aware translations with optional tone variations (formal/casual)."
            )
            AiPersonaMode.REPLY_COACH -> append(
                "Active Specialty: Communication & Reply Coach. " +
                    "Draft 2-3 concise, high-impact reply options (friendly, professional, brief) ready to copy and send."
            )
            AiPersonaMode.BOT_ARCHITECT -> append(
                "Active Specialty: Chat Bot Architect. " +
                    "Help users design bot personas, slash commands, welcome flows, and automated replies."
            )
            AiPersonaMode.CUSTOM -> append(
                "Active Specialty: User-Trained Custom Persona."
            )
        }
        if (config.customInstruction.isNotBlank()) {
            append("\n\nUser Custom Training Instructions:\n")
            append(config.customInstruction)
        }
        if (examples.isNotEmpty()) {
            append("\n\nFollow the style, formatting, and domain facts shown in the user's training examples.")
        }
    }

    private fun buildTrainedHistory(
        mode: AiPersonaMode,
        userExamples: List<AiTrainingExample>,
        persistedMessages: List<AiMessageEntity>
    ): List<Content> {
        val history = mutableListOf<Content>()

        // Pre-trained foundational few-shot examples
        history += content("user") {
            text("سلام نوا! تو توی P2P CHAT چه کارهایی انجام میدی؟")
        }
        history += content("model") {
            text(
                "سلام! من نوا هستم، دستیار هوش مصنوعی آموزش‌دیده داخل P2P CHAT. " +
                    "می‌تونم پیام‌هات رو خلاصه کنم، متن‌ها رو بین فارسی و انگلیسی ترجمه کنم، " +
                    "پاسخ‌های حرفه‌ای برات بنویسم و حتی در ساخت و آموزش ربات‌های اختصاصی کمکت کنم."
            )
        }

        if (mode == AiPersonaMode.BOT_ARCHITECT) {
            history += content("user") {
                text("یک ساختار خوب برای ربات پشتیبانی چت پیشنهاد بده.")
            }
            history += content("model") {
                text(
                    "برای ربات پشتیبانی، دستورهای /start (خوش‌آمدگویی)، /help (لیست خدمات)، " +
                        "/status (پیگیری وضعیت) و /agent (اتصال به اپراتور) بهترین شروع هستند و کنارش یک پرامپت دقیق با لحن محترمانه تعریف می‌کنیم."
                )
            }
        }

        // User-supplied fine-tuning Q&A pairs
        userExamples.takeLast(12).forEach { example ->
            history += content("user") { text(example.userPrompt) }
            history += content("model") { text(example.idealReply) }
        }

        // Recent persisted conversation turns (excluding error bubbles)
        persistedMessages
            .filter { !it.isError }
            .takeLast(16)
            .forEach { msg ->
                val roleName = if (msg.role == AiRole.USER.name) "user" else "model"
                history += content(roleName) { text(msg.content) }
            }

        return history
    }
}
