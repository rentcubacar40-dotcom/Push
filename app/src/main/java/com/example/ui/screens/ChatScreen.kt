package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.config.AppConfig
import com.example.data.model.ChatMessage
import com.example.ui.components.AvatarImage
import com.example.ui.theme.MoodgramIndigo
import com.example.ui.theme.MoodgramMagenta
import com.example.ui.theme.MoodgramViolet
import com.example.util.MediaUtils
import com.example.viewmodel.ChatViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    chatId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var selectedMessageForOptions by remember { mutableStateOf<ChatMessage?>(null) }
    var previewImageFullscreen by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onImageSelected(context, uri)
    }

    val groupPhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.onGroupAvatarSelected(context, uri)
    }

    LaunchedEffect(chatId) {
        viewModel.openChat(chatId)
    }

    val activeChat = state.activeChat
    val messages = activeChat?.messages ?: emptyList()

    // Auto scroll al último mensaje
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    BackHandler {
        viewModel.closeActiveChat()
        onNavigateBack()
    }

    val isOfficialGroup = activeChat?.isOfficialGroup == true
    val otherUsername = if (!isOfficialGroup && activeChat != null) {
        activeChat.getOtherParticipant(state.currentUser?.username ?: "")
    } else ""
    val otherUser = state.allUsers.firstOrNull { it.username.equals(otherUsername, ignoreCase = true) }
    val isOtherOnline = otherUser?.isOnline == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra Superior del Chat
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        viewModel.closeActiveChat()
                        onNavigateBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }

                    if (isOfficialGroup) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(MoodgramViolet, MoodgramMagenta))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (activeChat?.avatarUrl?.isNotEmpty() == true) {
                                AsyncImage(
                                    model = state.activeChatResolvedAvatar.ifEmpty { activeChat.avatarUrl },
                                    contentDescription = "Foto de Grupo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        val directAvatar = state.resolvedAvatarMap[otherUsername] ?: otherUser?.avatarRef ?: ""
                        AvatarImage(
                            avatarUrl = directAvatar,
                            displayName = otherUser?.displayName ?: otherUsername,
                            size = 40.dp,
                            showOnlineIndicator = true,
                            isOnline = isOtherOnline,
                            modifier = Modifier.clickable { onNavigateToProfile(otherUsername) }
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (!isOfficialGroup && otherUsername.isNotEmpty()) {
                                    onNavigateToProfile(otherUsername)
                                }
                            }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isOfficialGroup) (activeChat?.name ?: "Grupo Oficial") else (otherUser?.displayName ?: otherUsername),
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isOfficialGroup) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Oficial",
                                    tint = MoodgramMagenta,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isOfficialGroup) {
                                "${state.allUsers.size} miembros"
                            } else {
                                otherUser?.getLastSeenText() ?: "Desconectado"
                            },
                            fontSize = 11.sp,
                            color = if (!isOfficialGroup && isOtherOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (!isOfficialGroup && isOtherOnline) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    if (isOfficialGroup && state.currentUser?.isAdmin == true) {
                        IconButton(onClick = viewModel::openManageGroupDialog) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurar Grupo",
                                tint = MoodgramMagenta
                            )
                        }
                    }
                }
            }

            // Lista de Mensajes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isOfficialGroup && messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "¡Bienvenidos al Grupo Oficial de Moodgram!\nSé el primero en enviar un mensaje a la comunidad.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    val isMe = message.senderUsername.equals(state.currentUser?.username, ignoreCase = true)
                    val senderAvatar = state.resolvedAvatarMap[message.senderUsername] ?: message.senderAvatarRef
                    val senderIsOnline = state.userOnlineMap[message.senderUsername] == true

                    MessageBubbleItem(
                        message = message,
                        isMe = isMe,
                        senderAvatarUrl = senderAvatar,
                        senderIsOnline = senderIsOnline,
                        onMessageClick = { selectedMessageForOptions = message },
                        onMessageLongClick = { selectedMessageForOptions = message },
                        onAvatarClick = { onNavigateToProfile(message.senderUsername) },
                        onImageClick = { previewImageFullscreen = message.mediaUrl }
                    )
                }
            }

            // Vista previa de imagen adjunta antes de enviar
            AnimatedVisibility(visible = state.selectedImageUri != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                        ) {
                            AsyncImage(
                                model = state.selectedImageUri,
                                contentDescription = "Imagen seleccionada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Imagen lista para enviar",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(onClick = viewModel::removeSelectedImage) {
                            Icon(Icons.Default.Close, contentDescription = "Quitar")
                        }
                    }
                }
            }

            // Barra inferior para escribir mensajes ajustada perfectamente al teclado
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Adjuntar Foto",
                            tint = MoodgramViolet
                        )
                    }

                    OutlinedTextField(
                        value = state.messageInputText,
                        onValueChange = viewModel::onMessageInputChange,
                        placeholder = { Text("Escribe un mensaje...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        ),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (state.messageInputText.isNotBlank() || state.selectedImageUri != null) {
                                    viewModel.sendMessage()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    val canSend = (state.messageInputText.isNotBlank() || state.selectedImageUri != null) && !state.isSending

                    IconButton(
                        onClick = {
                            if (canSend) {
                                viewModel.sendMessage()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("send_chat_message_button")
                    ) {
                        if (state.isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Enviar",
                                tint = if (canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Modal Opciones de Mensaje (Reacciones, Editar, Eliminar)
        if (selectedMessageForOptions != null) {
            val msg = selectedMessageForOptions!!
            val isSender = msg.senderUsername.equals(state.currentUser?.username, ignoreCase = true)
            val isAdmin = state.currentUser?.isAdmin == true
            val canManageMsg = isSender || isAdmin

            ModalBottomSheet(
                onDismissRequest = { selectedMessageForOptions = null },
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Reaccionar al mensaje",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selector de Emojis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        AppConfig.SUPPORTED_REACTIONS.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .clickable {
                                        viewModel.reactToMessage(msg.id, emoji)
                                        selectedMessageForOptions = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }

                    if (canManageMsg) {
                        Spacer(modifier = Modifier.height(16.dp))

                        if (msg.text.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val id = msg.id
                                        val txt = msg.text
                                        selectedMessageForOptions = null
                                        viewModel.startEditingMessage(id, txt)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Editar mensaje", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val id = msg.id
                                    selectedMessageForOptions = null
                                    viewModel.deleteMessage(id)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text("Eliminar mensaje", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }

        // Diálogo para Editar Mensaje
        if (state.editingMessageId != null) {
            AlertDialog(
                onDismissRequest = viewModel::cancelEditingMessage,
                title = { Text("Editar mensaje") },
                text = {
                    OutlinedTextField(
                        value = state.editingMessageText,
                        onValueChange = viewModel::onEditingMessageTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                },
                confirmButton = {
                    Button(onClick = viewModel::saveEditedMessage) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelEditingMessage) {
                        Text("Cancelar")
                    }
                }
            )
        }

        // Visor de Imagen en Pantalla Completa
        if (previewImageFullscreen != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { previewImageFullscreen = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = previewImageFullscreen,
                    contentDescription = "Imagen completa",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { previewImageFullscreen = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }

        // Modal Administrar Grupo Oficial (Admin)
        if (state.showManageGroupDialog) {
            AlertDialog(
                onDismissRequest = viewModel::closeManageGroupDialog,
                title = { Text("Administrar Grupo Oficial") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, MoodgramMagenta, CircleShape)
                                .clickable {
                                    groupPhotoPickerLauncher.launch(
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isMe: Boolean,
    senderAvatarUrl: String,
    senderIsOnline: Boolean,
    onMessageClick: () -> Unit,
    onMessageLongClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onImageClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            AvatarImage(
                avatarUrl = senderAvatarUrl,
                displayName = message.senderDisplayName,
                size = 32.dp,
                showOnlineIndicator = true,
                isOnline = senderIsOnline,
                modifier = Modifier
                    .clickable(onClick = onAvatarClick)
                    .padding(end = 6.dp)
            )
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            if (!isMe) {
                Text(
                    text = message.senderDisplayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MoodgramViolet,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isMe) 18.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 18.dp
                ),
                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (isMe) 18.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 18.dp
                        )
                    )
                    .combinedClickable(
                        onClick = onMessageClick,
                        onLongClick = onMessageLongClick
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (message.mediaUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onImageClick)
                        ) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Imagen de chat",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "editado • ",
                                fontSize = 10.sp,
                                color = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            text = MediaUtils.formatRelativeTime(message.createdAt),
                            fontSize = 10.sp,
                            color = if (isMe) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Reacciones al mensaje
            if (message.reactions.isNotEmpty()) {
                val summary = mutableMapOf<String, Int>()
                message.reactions.values.forEach { e -> summary[e] = (summary[e] ?: 0) + 1 }
                Row(
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    summary.forEach { (emoji, count) ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 3.dp,
                            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = emoji, fontSize = 11.sp)
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(text = "$count", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
