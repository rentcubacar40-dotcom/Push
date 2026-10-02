package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Auth : Screen("auth")
    object Feed : Screen("feed")
    object Chats : Screen("chats")
    object ChatDetail : Screen("chat_detail/{chatId}") {
        fun createRoute(chatId: String) = "chat_detail/${android.net.Uri.encode(chatId)}"
    }
    object CreatePost : Screen("create_post")
    object PostDetail : Screen("post_detail/{postId}") {
        fun createRoute(postId: String) = "post_detail/${android.net.Uri.encode(postId)}"
    }
    object Profile : Screen("profile/{username}") {
        fun createRoute(username: String) = "profile/${android.net.Uri.encode(username)}"
    }
    object Admin : Screen("admin")
    object Settings : Screen("settings")
}
