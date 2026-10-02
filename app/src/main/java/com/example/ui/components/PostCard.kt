package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.config.AppConfig
import com.example.data.model.Post
import com.example.data.model.UserSession
import com.example.ui.theme.MoodgramMagenta
import com.example.util.MediaUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostCard(
    post: Post,
    currentUser: UserSession?,
    resolvedMediaUrl: String,
    resolvedAvatarUrl: String,
    onLikeClicked: () -> Unit,
    onReactionSelected: (String) -> Unit = {},
    onCommentClicked: () -> Unit,
    onAuthorClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onShowReactionsDetail: () -> Unit = {},
    isAuthorOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val userReaction = currentUser?.let { post.getUserReaction(it.username) }
    val isLiked = userReaction != null
    val canDelete = currentUser != null && (currentUser.isAdmin || post.authorUsername.equals(currentUser.username, ignoreCase = true))

    var showMenu by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "likeBounce"
    )

    val token = "ddd9b89ebd8115d4a9c1eaae298afde9"
    val rawMediaUrl = resolvedMediaUrl.ifEmpty { post.mediaUrl }
    val finalMediaUrl = remember(rawMediaUrl) {
        var url = rawMediaUrl.replace("\\u003d", "=").trim()
        if (url.contains("/pluginfile.php/1/")) {
            url = url.replace("/pluginfile.php/1/", "/pluginfile.php/${AppConfig.DEFAULT_CONTEXT_ID}/")
        }
        if (url.contains("/pluginfile.php/") && !url.contains("/webservice/pluginfile.php/")) {
            url = url.replace("/pluginfile.php/", "/webservice/pluginfile.php/")
        }
        if (url.startsWith("http") && !url.contains("token=")) {
            val sep = if (url.contains("?")) "&" else "?"
            "$url${sep}token=$token"
        } else {
            url
        }
    }

    val finalAvatarUrl = if (resolvedAvatarUrl.contains("/pluginfile.php/1/")) {
        resolvedAvatarUrl.replace("/pluginfile.php/1/", "/pluginfile.php/${AppConfig.DEFAULT_CONTEXT_ID}/")
    } else {
        resolvedAvatarUrl
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("post_card_${post.id}"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header del Autor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(
                        avatarUrl = finalAvatarUrl,
                        displayName = post.authorDisplayName,
                        size = 44.dp,
                        showRing = true,
                        showOnlineIndicator = true,
                        isOnline = isAuthorOnline,
                        modifier = Modifier.clickable(onClick = onAuthorClicked)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onAuthorClicked)
                    ) {
                        Text(
                            text = post.authorDisplayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = post.authorUsername,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = " • ${MediaUtils.formatRelativeTime(post.createdAt)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (canDelete) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.testTag("post_menu_button_${post.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Opciones",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Eliminar publicación", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClicked()
                                    }
                                )
                            }
                        }
                    }
                }

                // Contenedor Multimedia o Solo Texto con Gradiente
                if (post.isTextOnly) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val gradientColors = remember(post.backgroundColor) {
                        com.example.ui.theme.PostGradients.getGradient(post.backgroundColor)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(gradientColors))
                            .combinedClickable(
                                onDoubleClick = {
                                    if (!isLiked) {
                                        onReactionSelected("❤️")
                                    }
                                    showDoubleTapHeart = true
                                },
                                onClick = onCommentClicked
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 26.sp
                        )

                        DoubleTapHeartAnimation(
                            visible = showDoubleTapHeart,
                            onAnimationEnd = { showDoubleTapHeart = false }
                        )
                    }
                } else {
                    if (post.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = post.text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.22f)
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onDoubleClick = {
                                    if (!isLiked) {
                                        onReactionSelected("❤️")
                                    }
                                    showDoubleTapHeart = true
                                },
                                onClick = onCommentClicked
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (post.isVideo) {
                            VideoPlayerView(
                                videoUrl = finalMediaUrl,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        } else {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(finalMediaUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Imagen de la publicación",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp)),
                                loading = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(rememberShimmerBrush())
                                    )
                                },
                                error = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.BrokenImage,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Error al cargar multimedia",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        DoubleTapHeartAnimation(
                            visible = showDoubleTapHeart,
                            onAnimationEnd = { showDoubleTapHeart = false }
                        )
                    }
                }

                // Chips de Resumen de Reacciones
                val reactionSummary = post.getReactionSummary()
                if (reactionSummary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onShowReactionsDetail),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reactionSummary.forEach { (emoji, count) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.clickable {
                                    onShowReactionsDetail()
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
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Barra de Acciones
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Botón de Reacciones con long press / click
                        Box {
                            IconButton(
                                onClick = {
                                    if (userReaction != null) {
                                        onReactionSelected(userReaction)
                                    } else {
                                        showReactionPicker = !showReactionPicker
                                    }
                                },
                                modifier = Modifier
                                    .scale(likeScale)
                                    .testTag("like_button_${post.id}")
                            ) {
                                if (userReaction != null && userReaction != "❤️") {
                                    Text(text = userReaction, fontSize = 18.sp)
                                } else {
                                    Icon(
                                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = if (isLiked) "Quitar reacción" else "Reaccionar",
                                        tint = if (isLiked) MoodgramMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Botón pequeño para abrir selector de reacciones
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

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = "${post.totalReactionsCount}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLiked) MoodgramMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = onCommentClicked,
                            modifier = Modifier.testTag("comment_button_${post.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Comentar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${post.comments.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (post.fileSize > 0) {
                        Text(
                            text = MediaUtils.formatFileSize(post.fileSize),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                }

                // Vista previa del último comentario si existe
                if (post.comments.isNotEmpty()) {
                    val lastComment = post.comments.last()
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${lastComment.authorDisplayName}: ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = lastComment.text,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Popup Barra de Reacciones flotante (❤️, 🔥, 😂, 😮, 😢, 👏)
        AnimatedVisibility(
            visible = showReactionPicker,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 44.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppConfig.SUPPORTED_REACTIONS.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onReactionSelected(emoji)
                                    showReactionPicker = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}
