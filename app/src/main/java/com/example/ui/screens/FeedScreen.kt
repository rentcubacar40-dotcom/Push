package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.EmptyStateView
import com.example.ui.components.PostCard
import com.example.ui.components.PostCardSkeleton
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramViolet
import com.example.viewmodel.FeedViewModel

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }
    return remember(this) {
        derivedStateOf {
            if (previousIndex != firstVisibleItemIndex) {
                previousIndex > firstVisibleItemIndex
            } else {
                previousScrollOffset >= firstVisibleItemScrollOffset
            }.also {
                previousIndex = firstVisibleItemIndex
                previousScrollOffset = firstVisibleItemScrollOffset
            }
        }
    }.value
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onScrollDirectionChanged: (Boolean) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val isScrollingUp = listState.isScrollingUp()
    LaunchedEffect(isScrollingUp) {
        onScrollDirectionChanged(isScrollingUp)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 70.dp, bottom = 100.dp)
        ) {
            if (state.isLoading && state.posts.isEmpty()) {
                items(3) {
                    PostCardSkeleton()
                }
            } else if (state.posts.isEmpty()) {
                item {
                    EmptyStateView(
                        title = "No hay publicaciones aún",
                        subtitle = "Sé el primero en compartir una foto o un video desde Moodgram con almacenamiento en Moodle.",
                        buttonText = "Crear publicación",
                        onButtonClick = onNavigateToCreatePost,
                        modifier = Modifier.padding(top = 40.dp)
                    )
                }
            } else {
                items(
                    items = state.posts,
                    key = { it.id }
                ) { post ->
                    val resolvedMedia = state.resolvedMediaUrls[post.id] ?: post.mediaUrl
                    val resolvedAvatar = state.resolvedAvatarUrls[post.authorAvatarRef] ?: ""

                    PostCard(
                        post = post,
                        currentUser = state.currentUser,
                        resolvedMediaUrl = resolvedMedia,
                        resolvedAvatarUrl = resolvedAvatar,
                        onLikeClicked = { viewModel.toggleLike(post) },
                        onCommentClicked = { onNavigateToPostDetail(post.id) },
                        onAuthorClicked = { onNavigateToProfile(post.authorUsername) },
                        onDeleteClicked = { viewModel.deletePost(post.id) }
                    )
                }
            }
        }

        // Top App Bar translúcida con efecto de vidrio
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .shadow(elevation = 3.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MoodgramViolet,
                    modifier = Modifier.size(28.dp)
                )

                Text(
                    text = "Moodgram",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 10.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MoodgramMagenta
                    )
                } else {
                    IconButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.testTag("feed_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar feed",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
        )
    }
}
