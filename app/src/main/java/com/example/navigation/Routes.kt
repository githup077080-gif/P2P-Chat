package com.example.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Splash : AppRoute

    @Serializable
    data object Welcome : AppRoute

    @Serializable
    data object Login : AppRoute

    @Serializable
    data object Register : AppRoute

    @Serializable
    data object MainHome : AppRoute

    @Serializable
    data class PrivateChat(val peerUsername: String) : AppRoute

    @Serializable
    data class PeerProfile(val username: String) : AppRoute

    @Serializable
    data object EditProfile : AppRoute

    @Serializable
    data object PrivacyDashboard : AppRoute

    @Serializable
    data object NotificationPreferences : AppRoute

    @Serializable
    data object StorageManagement : AppRoute

    @Serializable
    data object About : AppRoute

    @Serializable
    data object AiAssistant : AppRoute

    @Serializable
    data class BotBuilder(val botId: String? = null) : AppRoute

    @Serializable
    data class BotSandbox(val botId: String) : AppRoute
}

enum class HomeTab {
    CHATS,
    AI_BOTS,
    SEARCH,
    PROFILE,
    SETTINGS
}
