package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppConfig
import com.example.data.model.User
import com.example.ui.components.AvatarImage
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramOrange
import com.example.ui.theme.MoodgramTeal
import com.example.ui.theme.MoodgramViolet
import com.example.util.MediaUtils
import com.example.viewmodel.AdminViewModel

@Composable
fun AdminScreen(
    viewModel: AdminViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var userToDelete by remember { mutableStateOf<User?>(null) }
    var postToDeleteId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra superior
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MoodgramMagenta,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Panel de Administración",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::loadAdminData) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recargar")
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoodgramMagenta)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    // Tarjetas de estadísticas superiores
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard(
                                    title = "Usuarios",
                                    value = "${state.users.size}",
                                    subtitle = "${state.activeUsersCount} activos • ${state.bannedUsersCount} susp.",
                                    icon = Icons.Default.Group,
                                    tint = MoodgramViolet,
                                    modifier = Modifier.weight(1f)
                                )
                                AdminStatCard(
                                    title = "Posts",
                                    value = "${state.posts.size}",
                                    subtitle = "${state.totalComments} comentarios",
                                    icon = Icons.Default.PhotoLibrary,
                                    tint = MoodgramMagenta,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AdminStatCard(
                                    title = "Espacio Moodle",
                                    value = MediaUtils.formatFileSize(state.totalStorageBytes),
                                    subtitle = "Evidencias UCF",
                                    icon = Icons.Default.Storage,
                                    tint = MoodgramOrange,
                                    modifier = Modifier.weight(1f)
                                )
                                AdminStatCard(
                                    title = "Estado Servidor",
                                    value = "En línea",
                                    subtitle = "cursos.ucf.edu.cu",
                                    icon = Icons.Default.CloudDone,
                                    tint = MoodgramTeal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Pestañas de Gestión
                    item {
                        TabRow(
                            selectedTabIndex = state.selectedTab,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            indicator = {}
                        ) {
                            listOf("Usuarios", "Publicaciones", "Moodle UCF").forEachIndexed { index, title ->
                                val isSelected = state.selectedTab == index
                                Tab(
                                    selected = isSelected,
                                    onClick = { viewModel.setTab(index) },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                ) {
                                    Text(
                                        text = title,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Contenido según pestaña seleccionada
                    when (state.selectedTab) {
                        0 -> {
                            // Pestaña Usuarios: Buscador
                            item {
                                OutlinedTextField(
                                    value = state.searchQuery,
                                    onValueChange = viewModel::onSearchQueryChange,
                                    placeholder = { Text("Buscar usuario...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = CircleShape,
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val filteredUsers = state.users.filter {
                                it.username.contains(state.searchQuery, ignoreCase = true) ||
                                        it.displayName.contains(state.searchQuery, ignoreCase = true)
                            }

                            items(filteredUsers, key = { it.username }) { user ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            avatarUrl = null,
                                            displayName = user.displayName,
                                            size = 42.dp
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = user.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                if (user.isAdmin) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "ADMIN",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MoodgramMagenta
                                                    )
                                                }
                                            }
                                            Text(
                                                text = user.username,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (user.isBanned) "🚫 Suspendido" else "✅ Activo",
                                                fontSize = 11.sp,
                                                color = if (user.isBanned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        if (!user.username.equals(AppConfig.ADMIN_USERNAME, ignoreCase = true)) {
                                            IconButton(
                                                onClick = { viewModel.toggleBanUser(user) },
                                                modifier = Modifier.testTag("ban_user_${user.username}")
                                            ) {
                                                Icon(
                                                    imageVector = if (user.isBanned) Icons.Default.CheckCircle else Icons.Default.Block,
                                                    contentDescription = if (user.isBanned) "Reactivar" else "Suspender",
                                                    tint = if (user.isBanned) MaterialTheme.colorScheme.primary else MoodgramOrange
                                                )
                                            }

                                            IconButton(
                                                onClick = { userToDelete = user },
                                                modifier = Modifier.testTag("delete_user_${user.username}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // Pestaña Publicaciones
                            if (state.posts.isEmpty()) {
                                item {
                                    Text(
                                        text = "No hay publicaciones en el sistema.",
                                        modifier = Modifier.padding(24.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(state.posts, key = { it.id }) { post ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${post.authorDisplayName} (${post.authorUsername})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = post.text.ifBlank { "[Sin texto]" },
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${post.mediaType.uppercase()} • ${MediaUtils.formatFileSize(post.fileSize)} • ${post.likes.size} likes • ${post.comments.size} coms.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            IconButton(
                                                onClick = { postToDeleteId = post.id },
                                                modifier = Modifier.testTag("admin_delete_post_${post.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar post",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // Pestaña Moodle UCF
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            text = "Infraestructura Moodle UCF",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("• Servidor: ${AppConfig.MOODLE_URL}", fontSize = 13.sp)
                                        Text("• Usuario Moodle: ${AppConfig.MOODLE_USER}", fontSize = 13.sp)
                                        Text("• Servicio: ${AppConfig.MOODLE_SERVICE}", fontSize = 13.sp)
                                        Text("• Área de guardado: Evidencias de Usuario (Private Files)", fontSize = 13.sp)
                                        Text("• Límite por archivo: ${AppConfig.MAX_FILE_MB} MB", fontSize = 13.sp)
                                        Text("• Administrador Moodgram: ${AppConfig.ADMIN_USERNAME}", fontSize = 13.sp)

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = viewModel::loadAdminData,
                                            shape = CircleShape,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Sincronizar todo con Moodle")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo para Confirmar Eliminación de Usuario
        userToDelete?.let { user ->
            AlertDialog(
                onDismissRequest = { userToDelete = null },
                title = { Text("Eliminar usuario") },
                text = { Text("¿Estás seguro de que deseas eliminar permanentemente a ${user.displayName} (${user.username})?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteUser(user)
                            userToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userToDelete = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para Confirmar Eliminación de Publicación
        postToDeleteId?.let { postId ->
            AlertDialog(
                onDismissRequest = { postToDeleteId = null },
                title = { Text("Eliminar publicación") },
                text = { Text("¿Deseas eliminar esta publicación como administrador?") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePost(postId)
                            postToDeleteId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { postToDeleteId = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 75.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun AdminStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
