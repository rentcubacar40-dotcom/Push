package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.config.AppConfig
import com.example.ui.components.AvatarImage
import com.example.ui.components.VideoPlayerView
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramViolet
import com.example.util.MediaUtils
import com.example.viewmodel.PostDetailViewModel

@Composable
fun PostDetailScreen(
    viewModel: PostDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    val userReaction = state.currentUser?.let { state.post?.getUserReaction(it.username) }
    val isLiked = userReaction != null

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val submitCommentAction: () -> Unit = {
        if (state.newCommentText.isNotBlank() && !state.isSubmittingComment) {
            keyboardController?.hide()
            focusManager.clearFocus()
            viewModel.addComment()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra Superior
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }

                    Text(
                        text = "Publicación",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    val post = state.post
                    val currentUser = state.currentUser
                    val canDelete = post != null && currentUser != null &&
                            (currentUser.isAdmin || post.authorUsername.equals(currentUser.username, ignoreCase = true))

                    if (canDelete) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Opciones")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar publicación", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.deletePost(onDeleted = onNavigateBack)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoodgramViolet)
                }
            } else if (state.post != null) {
                val post = state.post!!

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Contenido Multimedia completo
                    item {
                        val finalMedia = state.resolvedMediaUrl.ifEmpty { post.mediaUrl }

                        if (post.isTextOnly || post.mediaUrl.isBlank()) {
                            val gradientColors = remember(post.backgroundColor) {
                                com.example.ui.theme.PostGradients.getGradient(post.backgroundColor)
                            }
                            val isWhiteBg = com.example.ui.theme.PostGradients.isLight(post.backgroundColor)
                            val postTextColor = com.example.ui.theme.PostGradients.getTextColor(post.backgroundColor)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .background(androidx.compose.ui.graphics.Brush.linearGradient(gradientColors))
                                    .then(
                                        if (isWhiteBg) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                        } else Modifier
                                    )
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = post.text,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = postTextColor,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 28.sp
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 240.dp, max = 500.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (post.isVideo) {
                                    VideoPlayerView(
                                        videoUrl = finalMedia,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(300.dp),
                                        autoPlay = true,
                                        initiallyMuted = false
                                    )
                                } else {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(finalMedia)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Foto completa",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 240.dp, max = 500.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Datos del autor y fecha
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val authorAvatar = state.resolvedAvatarUrls[post.authorAvatarRef]
                                    ?: state.resolvedAvatarUrls[post.authorUsername]
                                AvatarImage(
                                    avatarUrl = authorAvatar,
                                    displayName = post.authorDisplayName,
                                    size = 46.dp,
                                    showRing = true,
                                    showOnlineIndicator = true,
                                    isOnline = state.userOnlineStatus[post.authorUsername] == true,
                                    modifier = Modifier.clickable { onNavigateToProfile(post.authorUsername) }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.clickable { onNavigateToProfile(post.authorUsername) }) {
                                    Text(
                                        text = post.authorDisplayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${post.authorUsername} • ${MediaUtils.formatRelativeTime(post.createdAt)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                // Reacción actual o me gusta
                                Box {
                                    IconButton(
                                        onClick = {
                                            if (userReaction != null) {
                                                viewModel.setReaction(userReaction)
                                            } else {
                                                showReactionPicker = !showReactionPicker
                                            }
                                        }
                                    ) {
                                        if (userReaction != null && userReaction != "❤️") {
                                            Text(text = userReaction, fontSize = 20.sp)
                                        } else {
                                            Icon(
                                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Reacción",
                                                tint = if (isLiked) MoodgramMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { showReactionPicker = !showReactionPicker }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(text = "➕", fontSize = 10.sp)
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Text(
                                    text = "${post.totalReactionsCount}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLiked) MoodgramMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Barra de Reacciones flotante en detalle
                            if (showReactionPicker) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 6.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppConfig.SUPPORTED_REACTIONS.forEach { emoji ->
                                            val isCurrent = userReaction == emoji
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .border(
                                                    width = if (isCurrent) 1.5.dp else 0.dp,
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                    .clickable {
                                                        viewModel.setReaction(emoji)
                                                        showReactionPicker = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = emoji, fontSize = 20.sp)
                                            }
                                        }

                                        if (userReaction != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                                    .clickable {
                                                        viewModel.setReaction(userReaction)
                                                        showReactionPicker = false
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Quitar reacción",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .clickable { showReactionPicker = false },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Cerrar",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Chips de Reacciones
                            val reactionSummary = post.getReactionSummary()
                            if (reactionSummary.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setShowReactionsDetail(true) },
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    reactionSummary.forEach { (emoji, count) ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.clickable {
                                                viewModel.setShowReactionsDetail(true)
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = emoji, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "$count",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (post.text.isNotBlank() && !post.isTextOnly) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = post.text,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Comentarios (${post.comments.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Lista de Comentarios
                    if (post.comments.isEmpty()) {
                        item {
                            Text(
                                text = "No hay comentarios aún. ¡Sé el primero en comentar!",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                            )
                        }
                    } else {
                        items(post.comments, key = { it.id }) { comment ->
                            val isAuthor = comment.authorUsername.equals(state.currentUser?.username, ignoreCase = true)
                            val isAdmin = state.currentUser?.isAdmin == true
                            val canManageComment = state.currentUser != null && (isAdmin || isAuthor || post.authorUsername.equals(state.currentUser?.username, ignoreCase = true))
                            val canEdit = state.currentUser != null && (isAdmin || isAuthor)
                            val isReply = comment.replyToCommentId != null

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = if (isReply) 40.dp else 16.dp,
                                        end = 16.dp,
                                        top = 6.dp,
                                        bottom = 6.dp
                                    ),
                                verticalAlignment = Alignment.Top
                            ) {
                                AvatarImage(
                                    avatarUrl = state.resolvedAvatarUrls[comment.authorAvatarRef] ?: state.resolvedAvatarUrls[comment.authorUsername],
                                    displayName = comment.authorDisplayName,
                                    size = if (isReply) 30.dp else 36.dp,
                                    showOnlineIndicator = true,
                                    isOnline = state.userOnlineStatus[comment.authorUsername] == true,
                                    modifier = Modifier.clickable { onNavigateToProfile(comment.authorUsername) }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = comment.authorDisplayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.clickable { onNavigateToProfile(comment.authorUsername) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = MediaUtils.formatRelativeTime(comment.createdAt),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (comment.isEdited) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "(editado)",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                            )
                                        }
                                    }

                                    if (comment.replyToUsername != null) {
                                        Text(
                                            text = "Respondiendo a ${comment.replyToUsername}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = comment.text,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    // Botón responder
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Responder",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { viewModel.setReplyingToComment(comment) }
                                                .padding(end = 12.dp)
                                        )
                                    }
                                }

                                if (canEdit) {
                                    IconButton(
                                        onClick = { viewModel.startEditingComment(comment.id, comment.text) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar comentario",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                if (canManageComment) {
                                    IconButton(
                                        onClick = { viewModel.deleteComment(comment.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar comentario",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Barra inferior fija para redactar comentarios con ajuste perfecto a teclado
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Banner de respuesta si está activo
                        state.replyingToComment?.let { rep ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Respondiendo a ${rep.authorDisplayName} (${rep.authorUsername}): \"${rep.text.take(30)}...\"",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.setReplyingToComment(null) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancelar respuesta",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.newCommentText,
                                onValueChange = viewModel::onCommentChange,
                                placeholder = {
                                    val holderText = if (state.replyingToComment != null) {
                                        "Responde a ${state.replyingToComment?.authorDisplayName}..."
                                    } else {
                                        "Escribe un comentario..."
                                    }
                                    Text(holderText, fontSize = 14.sp)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("comment_input_field"),
                                shape = CircleShape,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                ),
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { submitCommentAction() }
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = submitCommentAction,
                                enabled = state.newCommentText.isNotBlank() && !state.isSubmittingComment,
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .testTag("send_comment_button")
                            ) {
                                if (state.isSubmittingComment) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Enviar",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo para editar comentario
        if (state.editingCommentId != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelEditingComment,
                title = { Text("Editar comentario") },
                text = {
                    OutlinedTextField(
                        value = state.editingCommentText,
                        onValueChange = viewModel::onEditingCommentTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(onClick = viewModel::saveEditedComment) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelEditingComment) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Hoja de detalles de reacciones (quién reaccionó a este post)
        val currentPost = state.post
        if (state.showReactionsDetail && currentPost != null) {
            com.example.ui.components.ReactionsDetailSheet(
                reactions = currentPost.allReactions,
                users = state.allUsers,
                onDismiss = { viewModel.setShowReactionsDetail(false) },
                onNavigateToProfile = onNavigateToProfile
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
        )
    }
}
