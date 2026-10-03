package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.MoodgramRepository
import com.example.ui.navigation.Screen
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.MainPagerScreen
import com.example.ui.screens.PostDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.PostDetailViewModel
import com.example.viewmodel.ProfileViewModel
import com.example.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    private var pendingChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Obtener el repositorio singleton administrado a nivel de Application
        val app = application as MoodgramApplication
        val repository = app.repository

        // 2. Extraer destino de notificación si se abrió mediante un PendingIntent
        pendingChatId = intent.getStringExtra("extra_chat_id")

        setContent {
            val themeMode by repository.sessionManager.themeModeFlow.collectAsState(initial = "SYSTEM")
            val colorTheme by repository.sessionManager.colorThemeFlow.collectAsState(initial = "TEAL")
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme, colorTheme = colorTheme) {
                MoodgramApp(
                    repository = repository,
                    pendingChatId = pendingChatId,
                    onClearPendingChat = { pendingChatId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("extra_chat_id")?.let { chatId ->
            pendingChatId = chatId
        }
    }
}

@Composable
fun MoodgramApp(
    repository: MoodgramRepository,
    pendingChatId: String? = null,
    onClearPendingChat: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val currentSession by repository.sessionManager.userSessionFlow.collectAsState(initial = null)
    val usersDb by repository.usersFlow.collectAsState()

    // 1. Solicitud automática del permiso de notificaciones en Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { /* Gestionado por el usuario */ }

        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 2. Si el usuario logueado es bloqueado o eliminado por el administrador, expulsar de inmediato
    LaunchedEffect(currentSession?.username, usersDb.users) {
        val session = currentSession ?: return@LaunchedEffect
        if (usersDb.users.isNotEmpty()) {
            val userInDb = usersDb.users.firstOrNull { it.username.equals(session.username, ignoreCase = true) }
            if (userInDb == null || userInDb.isBanned) {
                repository.logout()
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Manejo de Deep Link hacia un chat desde una notificación
    LaunchedEffect(pendingChatId) {
        pendingChatId?.let { chatId ->
            if (chatId.isNotBlank()) {
                navController.navigate(Screen.ChatDetail.createRoute(chatId)) {
                    launchSingleTop = true
                }
                onClearPendingChat()
            }
        }
    }

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

        // Pantalla Principal con NavigationBar Material 3
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
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
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

        // Detalle de Publicación con comentarios
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
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
                }
            )
        }

        // Pantalla de Ajustes
        composable(Screen.Settings.route) {
            val settingsViewModel = remember { SettingsViewModel(repository) }
            SettingsScreen(
                viewModel = settingsViewModel,
                onLogoutSuccess = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
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
