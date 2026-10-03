package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.repository.MoodgramRepository
import com.example.ui.components.FloatingNavBar
import com.example.ui.navigation.Screen
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.CreatePostViewModel
import com.example.viewmodel.FeedViewModel
import com.example.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla principal con NavigationBar M3 y Pager horizontal con 5 destinos:
 * 0: Inicio (Feed)
 * 1: Buscar (Search)
 * 2: Publicar (Create Post)
 * 3: Mensajes (Chat List)
 * 4: Perfil (Profile) - desde el cual se accede a Ajustes y Admin
 */
@Composable
fun MainPagerScreen(
    repository: MoodgramRepository,
    onNavigateToChat: (String) -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val currentSession by repository.sessionManager.userSessionFlow.collectAsState(initial = null)
    val isAdmin = currentSession?.isAdmin == true ||
            currentSession?.username?.equals("@Eliel_21", ignoreCase = true) == true ||
            currentSession?.username?.equals("Eliel_21", ignoreCase = true) == true

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()

    var isNavBarVisible by remember { mutableStateOf(true) }

    // Instancias de ViewModels cacheadas por pantalla en el Pager
    val feedViewModel = remember { FeedViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }
    val createPostViewModel = remember { CreatePostViewModel(repository) }
    val profileViewModel = remember(currentSession?.username) {
        ProfileViewModel(repository, currentSession?.username ?: "profile_current")
    }

    val currentRoute = when (pagerState.currentPage) {
        0 -> Screen.Feed.route
        1 -> "search"
        2 -> Screen.CreatePost.route
        3 -> Screen.Chats.route
        4 -> "profile_current"
        else -> Screen.Feed.route
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> {
                    FeedScreen(
                        viewModel = feedViewModel,
                        onNavigateToCreatePost = {
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onNavigateToPostDetail = onNavigateToPostDetail,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToChats = {
                            coroutineScope.launch { pagerState.animateScrollToPage(3) }
                        },
                        onScrollDirectionChanged = { isScrollingUp ->
                            isNavBarVisible = isScrollingUp
                        }
                    )
                }
                1 -> {
                    SearchScreen(
                        repository = repository,
                        onNavigateToProfile = onNavigateToProfile,
                        onNavigateToPostDetail = onNavigateToPostDetail
                    )
                }
                2 -> {
                    CreatePostScreen(
                        viewModel = createPostViewModel,
                        onNavigateBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onPostCreated = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        }
                    )
                }
                3 -> {
                    ChatListScreen(
                        viewModel = chatViewModel,
                        onNavigateToChat = onNavigateToChat
                    )
                }
                4 -> {
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onNavigateToPostDetail = onNavigateToPostDetail,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToAdmin = onNavigateToAdmin
                    )
                }
            }
        }

        // Barra de navegación Material 3 con etiquetas visibles
        FloatingNavBar(
            currentRoute = currentRoute,
            isVisible = isNavBarVisible,
            isAdmin = isAdmin,
            onNavigate = { route ->
                val targetPage = when (route) {
                    Screen.Feed.route -> 0
                    "search" -> 1
                    Screen.CreatePost.route -> 2
                    Screen.Chats.route -> 3
                    "profile_current", Screen.Profile.route -> 4
                    else -> 0
                }
                coroutineScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
