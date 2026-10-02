package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Auth : Screen("auth")
    object Feed : Screen("feed")
    object CreatePost : Screen("create_post")
    object PostDetail : Screen("post_detail/{postId}") {
        fun createRoute(postId: String) = "post_detail/$postId"
    }
    object Profile : Screen("profile/{username}") {
        fun createRoute(username: String) = "profile/$username"
    }
    object Admin : Screen("admin")
    object Settings : Screen("settings")
}
