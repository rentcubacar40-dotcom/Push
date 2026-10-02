package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.MoodgramRepository
import com.example.ui.components.FloatingNavBar
import com.example.ui.navigation.Screen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatListScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MainPagerScreen
import com.example.ui.screens.PostDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.CreatePostViewModel
import com.example.viewmodel.FeedViewModel
import com.example.viewmodel.PostDetailViewModel
import com.example.viewmodel.ProfileViewModel
import com.example.viewmodel.SettingsViewModel

import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = MoodgramRepository(applicationContext)

        // Configurar Coil globalmente para utilizar el cliente HTTP configurado para Moodle
        val imageLoader = ImageLoader.Builder(applicationContext)
            .okHttpClient(repository.moodleApi.client)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(applicationContext)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve("moodle_images"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            val themeMode by repository.sessionManager.themeModeFlow.collectAsState(initial = "SYSTEM")
            val colorTheme by repository.sessionManager.colorThemeFlow.collectAsState(initial = "TEAL")
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme, colorTheme = colorTheme) {
                MoodgramApp(repository = repository)
            }
        }
    }
}

@Composable
fun MoodgramApp(repository: MoodgramRepository) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                repository = repository,
                sessionManager = repository.sessionManager,
                onNavigateToFeed = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Auth.route) {
            val authViewModel = remember { AuthViewModel(repository) }
            AuthScreen(
                viewModel = authViewModel,
                onAuthSuccess = {
                    navController.navigate(Screen.Feed.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        // Pantalla Principal integrada con ViewPager (Feed, Chats, Crear Post, Perfil, Ajustes)
        composable(Screen.Feed.route) {
            MainPagerScreen(
                repository = repository,
                onNavigateToChat = { chatId ->
                    navController.navigate(Screen.ChatDetail.createRoute(chatId))
                },
                onNavigateToPostDetail = { postId ->
                    navController.navigate(Screen.PostDetail.createRoute(postId))
                },
                onNavigateToProfile = { username ->
                    navController.navigate(Screen.Profile.createRoute(username))
                },
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Detalle de Chat individual o grupal
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val rawChatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val chatId = try { android.net.Uri.decode(rawChatId) } catch (_: Exception) { rawChatId }
            val chatViewModel = remember(chatId) { ChatViewModel(repository) }
            ChatScreen(
                viewModel = chatViewModel,
                chatId = chatId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { username ->
                    navController.navigate(Screen.Profile.createRoute(username))
                }
            )
        }

        // Detalle de Publicación con comentarios y reproductor de video
        composable(
            route = Screen.PostDetail.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            val postDetailViewModel = remember(postId) {
                PostDetailViewModel(repository, postId)
            }
            PostDetailScreen(
                viewModel = postDetailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToProfile = { username ->
                    navController.navigate(Screen.Profile.createRoute(username))
                }
            )
        }

        // Perfil de usuario específico
        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("username") { type = NavType.StringType })
        ) { backStackEntry ->
            val username = backStackEntry.arguments?.getString("username") ?: "profile_current"
            val profileViewModel = remember(username) {
                ProfileViewModel(repository, username)
            }
            ProfileScreen(
                viewModel = profileViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPostDetail = { postId ->
                    navController.navigate(Screen.PostDetail.createRoute(postId))
                },
                onNavigateToChat = { chatId ->
                    navController.navigate(Screen.ChatDetail.createRoute(chatId))
                }
            )
        }

        // Panel de Administración (Admin)
        composable(Screen.Admin.route) {
            val adminViewModel = remember { AdminViewModel(repository) }
            AdminScreen(viewModel = adminViewModel)
        }
    }
}
