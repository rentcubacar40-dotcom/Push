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
import com.example.ui.screens.CreatePostScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.PostDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.AuthViewModel
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
            val darkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = darkTheme) {
                MoodgramApp(repository = repository)
            }
        }
    }
}

@Composable
fun MoodgramApp(repository: MoodgramRepository) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Splash.route

    val currentSession by repository.sessionManager.userSessionFlow.collectAsState(initial = null)
    var isNavBarVisible by remember { mutableStateOf(true) }

    // Determinar si la barra de navegación flotante debe mostrarse
    val shouldShowNavBar = currentSession != null &&
            currentRoute != Screen.Splash.route &&
            currentRoute != Screen.Auth.route &&
            currentRoute != Screen.CreatePost.route &&
            !currentRoute.startsWith("post_detail")

    Box(modifier = Modifier.fillMaxSize()) {
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

            composable(Screen.Feed.route) {
                val feedViewModel = remember { FeedViewModel(repository) }
                FeedScreen(
                    viewModel = feedViewModel,
                    onNavigateToCreatePost = {
                        navController.navigate(Screen.CreatePost.route)
                    },
                    onNavigateToPostDetail = { postId ->
                        navController.navigate(Screen.PostDetail.createRoute(postId))
                    },
                    onNavigateToProfile = { username ->
                        navController.navigate(Screen.Profile.createRoute(username))
                    },
                    onScrollDirectionChanged = { isScrollingUp ->
                        isNavBarVisible = isScrollingUp
                    }
                )
            }

            composable(Screen.CreatePost.route) {
                val createPostViewModel = remember { CreatePostViewModel(repository) }
                CreatePostScreen(
                    viewModel = createPostViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onPostCreated = {
                        navController.navigate(Screen.Feed.route) {
                            popUpTo(Screen.Feed.route) { inclusive = true }
                        }
                    }
                )
            }

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
                    }
                )
            }

            composable(Screen.Admin.route) {
                val adminViewModel = remember { AdminViewModel(repository) }
                AdminScreen(viewModel = adminViewModel)
            }

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
        }

        // Barra de navegación inferior flotante tipo pill
        if (shouldShowNavBar) {
            FloatingNavBar(
                currentRoute = currentRoute,
                isVisible = isNavBarVisible,
                isAdmin = currentSession?.isAdmin == true,
                onNavigate = { route ->
                    if (route == "profile_current") {
                        val currentUsername = currentSession?.username ?: ""
                        navController.navigate(Screen.Profile.createRoute(currentUsername)) {
                            launchSingleTop = true
                        }
                    } else if (route != currentRoute) {
                        navController.navigate(route) {
                            popUpTo(Screen.Feed.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
