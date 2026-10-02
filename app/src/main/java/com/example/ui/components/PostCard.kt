package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    onCommentClicked: () -> Unit,
    onAuthorClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiked = currentUser != null && post.likes.contains(currentUser.username)
    val canDelete = currentUser != null && (currentUser.isAdmin || post.authorUsername.equals(currentUser.username, ignoreCase = true))

    var showMenu by remember { mutableStateOf(false) }
    var showDoubleTapHeart by remember { mutableStateOf(false) }

    // Animación de rebote en el botón de like
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "likeBounce"
    )

    Card(
        modifier = modifier
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
                    avatarUrl = resolvedAvatarUrl,
                    displayName = post.authorDisplayName,
                    size = 44.dp,
                    showRing = true,
                    modifier = Modifier.combinedClickable(
                        onClick = onAuthorClicked
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(onClick = onAuthorClicked)
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

            // Texto de la publicación
            if (post.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Contenedor Multimedia (Imagen o Video)
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.22f)
                    .clip(RoundedCornerShape(20.dp))
                    .combinedClickable(
                        onDoubleClick = {
                            if (!isLiked) {
                                onLikeClicked()
                            }
                            showDoubleTapHeart = true
                        },
                        onClick = onCommentClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (post.isVideo) {
                    VideoPlayerView(
                        videoUrl = resolvedMediaUrl.ifEmpty { post.mediaUrl },
                        modifier = Modifier.clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedMediaUrl.ifEmpty { post.mediaUrl })
                            .crossfade(true)
                            .build(),
                        contentDescription = "Imagen de la publicación",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                    )
                }

                // Animación de corazón al hacer doble toque
                DoubleTapHeartAnimation(
                    visible = showDoubleTapHeart,
                    onAnimationEnd = { showDoubleTapHeart = false }
                )
            }

            // Barra de Acciones: Likes y Comentarios
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onLikeClicked,
                        modifier = Modifier
                            .scale(likeScale)
                            .testTag("like_button_${post.id}")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Quitar me gusta" else "Me gusta",
                            tint = if (isLiked) MoodgramMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${post.likes.size}",
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
}
