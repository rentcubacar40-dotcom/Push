package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.example.data.repository.MoodgramRepository
import com.example.ui.components.FloatingNavBar
import com.example.ui.navigation.Screen
import com.example.viewmodel.ChatViewModel
import com.example.viewmodel.CreatePostViewModel
import com.example.viewmodel.FeedViewModel
import com.example.viewmodel.ProfileViewModel
import com.example.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Pantalla principal que integra ViewPager (HorizontalPager) sincronizado con el FloatingNavBar.
 * Permite deslizar suavemente entre Feed, Chats, Crear Post, Perfil y Ajustes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(
    repository: MoodgramRepository,
    onNavigateToChat: (String) -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onLogoutSuccess: () -> Unit
) {
    val currentSession by repository.sessionManager.userSessionFlow.collectAsState(initial = null)
    val isAdmin = currentSession?.isAdmin == true

    val pageCount = 5
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    var isNavBarVisible by remember { mutableStateOf(true) }

    // Instancias de ViewModels cacheadas por pantalla en el Pager
    val feedViewModel = remember { FeedViewModel(repository) }
    val chatViewModel = remember { ChatViewModel(repository) }
    val createPostViewModel = remember { CreatePostViewModel(repository) }
    val profileViewModel = remember(currentSession?.username) {
        ProfileViewModel(repository, currentSession?.username ?: "profile_current")
    }
    val settingsViewModel = remember { SettingsViewModel(repository) }

    val currentRoute = when (pagerState.currentPage) {
        0 -> Screen.Feed.route
        1 -> Screen.Chats.route
        2 -> Screen.CreatePost.route
        3 -> "profile_current"
        4 -> Screen.Settings.route
        else -> Screen.Feed.route
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ViewPager horizontal sincronizado
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
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onScrollDirectionChanged = { isScrollingUp ->
                            isNavBarVisible = isScrollingUp
                        }
                    )
                }
                1 -> {
                    ChatListScreen(
                        viewModel = chatViewModel,
                        onNavigateToChat = onNavigateToChat
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
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onNavigateBack = {
                            coroutineScope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onNavigateToPostDetail = onNavigateToPostDetail
                    )
                }
                4 -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onLogoutSuccess = onLogoutSuccess
                    )
                }
            }
        }

        // Barra de Navegación Flotante Sincronizada con el ViewPager
        FloatingNavBar(
            currentRoute = currentRoute,
            isVisible = isNavBarVisible,
            isAdmin = isAdmin,
            onNavigate = { route ->
                val targetPage = when (route) {
                    Screen.Feed.route -> 0
                    Screen.Chats.route -> 1
                    Screen.CreatePost.route -> 2
                    "profile_current", Screen.Profile.route -> 3
                    Screen.Settings.route, Screen.Admin.route -> 4
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
