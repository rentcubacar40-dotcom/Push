package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.config.AppConfig
import com.example.ui.components.AvatarImage
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.MoodgramIndigo
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramViolet
import com.example.util.MediaUtils
import com.example.viewmodel.ChatViewModel

@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onNavigateToChat: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var userSearchQuery by remember { mutableStateOf("") }
    var targetChatToClear by remember { mutableStateOf<String?>(null) }
    var targetChatToDelete by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onGroupAvatarSelected(context, uri)
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
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra Superior
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chats",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Mensajes y Grupos",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = viewModel::openNewChatDialog) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Nuevo Chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MoodgramViolet)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // SECCIÓN: GRUPO OFICIAL
                    item {
                        Text(
                            text = "Comunidad Oficial",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        val official = state.officialGroup
                        if (official != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(22.dp))
                                    .clickable {
                                        viewModel.openChat(official.id)
                                        onNavigateToChat(official.id)
                                    }
                                    .testTag("official_group_card"),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar con marco oficial
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .border(
                                                2.dp,
                                                Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta, MoodgramIndigo)),
                                                CircleShape
                                            )
                                            .clip(CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (official.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = official.avatarUrl,
                                                contentDescription = "Avatar de Grupo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta))
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Group,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = official.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "Oficial",
                                                tint = MoodgramMagenta,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Text(
                                            text = if (!official.lastMessage.isNullOrBlank()) {
                                                "${official.lastMessageSender ?: ""}: ${official.lastMessage}"
                                            } else {
                                                "${state.allUsers.size} miembros • Grupo general"
                                            },
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (state.currentUser?.isAdmin == true) {
                                        IconButton(
                                            onClick = viewModel::openManageGroupDialog,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Settings,
                                                contentDescription = "Administrar Grupo",
                                                tint = MoodgramMagenta,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // SECCIÓN: CHATS DIRECTOS
                    item {
                        Text(
                            text = "Mensajes Directos",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )
                    }

                    val directList = state.directChats.filter { chat ->
                        chat.members.any { it.equals(state.currentUser?.username, ignoreCase = true) }
                    }

                    if (directList.isEmpty()) {
                        item {
                            EmptyStateView(
                                title = "No hay mensajes directos",
                                subtitle = "Inicia una conversación privada con cualquier miembro de Moodgram.",
                                icon = Icons.Default.Chat,
                                buttonText = "Buscar usuarios",
                                onButtonClick = viewModel::openNewChatDialog,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(directList, key = { it.id }) { chat ->
                            val otherUsername = chat.getOtherParticipant(state.currentUser?.username ?: "")
                            val otherUser = state.allUsers.firstOrNull { it.username.equals(otherUsername, ignoreCase = true) }
                            val isOnline = otherUser?.isOnline == true
                            val avatarUrl = state.resolvedAvatarMap[otherUsername] ?: otherUser?.avatarRef ?: ""

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        viewModel.openChat(chat.id)
                                        onNavigateToChat(chat.id)
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarImage(
                                        avatarUrl = avatarUrl,
                                        displayName = otherUser?.displayName ?: otherUsername,
                                        size = 48.dp,
                                        showOnlineIndicator = true,
                                        isOnline = isOnline
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = otherUser?.displayName ?: otherUsername,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (chat.lastMessageTime != null) {
                                                Text(
                                                    text = MediaUtils.formatRelativeTime(chat.lastMessageTime),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Text(
                                            text = chat.lastMessage ?: "Sin mensajes aún",
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    var showChatRowMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(
                                            onClick = { showChatRowMenu = true },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Opciones",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showChatRowMenu,
                                            onDismissRequest = { showChatRowMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Vaciar mensajes", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showChatRowMenu = false
                                                    targetChatToClear = chat.id
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Eliminar conversación", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showChatRowMenu = false
                                                    targetChatToDelete = chat.id
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }

        // Modal Nuevo Chat / Seleccionar Usuario
        if (state.showNewChatDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeNewChatDialog,
                title = { Text("Iniciar conversación") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = userSearchQuery,
                            onValueChange = { userSearchQuery = it },
                            placeholder = { Text("Buscar por nombre o @usuario...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = CircleShape
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredUsers = state.allUsers.filter { u ->
                            !u.username.equals(state.currentUser?.username, ignoreCase = true) &&
                                    (u.username.contains(userSearchQuery, ignoreCase = true) ||
                                            u.displayName.contains(userSearchQuery, ignoreCase = true))
                        }

                        if (filteredUsers.isEmpty()) {
                            Text(
                                text = "No se encontraron usuarios.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(260.dp)) {
                                items(filteredUsers, key = { it.username }) { user ->
                                    val userAvatar = state.resolvedAvatarMap[user.username] ?: user.avatarRef
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                viewModel.closeNewChatDialog()
                                                viewModel.startDirectChat(user.username) { newChatId ->
                                                    onNavigateToChat(newChatId)
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AvatarImage(
                                            avatarUrl = userAvatar,
                                            displayName = user.displayName,
                                            size = 40.dp,
                                            showOnlineIndicator = true,
                                            isOnline = user.isOnline
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = user.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${user.username} • ${user.getLastSeenText()}",
                                                fontSize = 12.sp,
                                                color = if (user.isOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::closeNewChatDialog) {
                        Text("Cerrar")
                    }
                }
            )
        }

        // Modal Administrar Grupo Oficial (Solo Admin)
        if (state.showManageGroupDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeManageGroupDialog,
                title = { Text("Administrar Grupo Oficial") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Cambiar Foto del Grupo Oficial
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MoodgramMagenta, CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.groupEditAvatarUri != null) {
                                AsyncImage(
                                    model = state.groupEditAvatarUri,
                                    contentDescription = "Foto de Grupo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (!state.officialGroup?.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = state.officialGroup!!.avatarUrl,
                                    contentDescription = "Foto de Grupo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = MoodgramViolet,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Cambiar",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = state.groupEditName,
                            onValueChange = viewModel::onGroupEditNameChange,
                            label = { Text("Nombre del Grupo") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = state.groupEditDescription,
                            onValueChange = viewModel::onGroupEditDescriptionChange,
                            label = { Text("Descripción / Reglas del Grupo") },
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = viewModel::saveOfficialGroupSettings,
                        enabled = !state.isUpdatingGroup
                    ) {
                        if (state.isUpdatingGroup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Guardar Cambios")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::closeManageGroupDialog) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para Vaciar Chat
        targetChatToClear?.let { chatId ->
            AlertDialog(
                onDismissRequest = { targetChatToClear = null },
                title = { Text("Vaciar mensajes del chat") },
                text = { Text("¿Estás seguro de que deseas vaciar todos los mensajes de esta conversación? Esta acción no se puede deshacer.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearChatHistory(chatId)
                            targetChatToClear = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Vaciar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { targetChatToClear = null }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Diálogo para Eliminar Chat
        targetChatToDelete?.let { chatId ->
            AlertDialog(
                onDismissRequest = { targetChatToDelete = null },
                title = { Text("Eliminar conversación") },
                text = { Text("¿Estás seguro de que deseas eliminar este chat? Se borrará completamente de tu lista.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteDirectChat(chatId)
                            targetChatToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { targetChatToDelete = null }) {
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
