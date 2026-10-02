package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionsDetailSheet(
    reactions: Map<String, String>, // username -> emoji
    users: List<User>,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilterEmoji by remember { mutableStateOf<String?>(null) }

    val userMap = remember(users) {
        users.associateBy { it.username.lowercase() }
    }

    // Lista de emojis presentes
    val uniqueEmojis = remember(reactions) {
        reactions.values.distinct()
    }

    // Filtrar reacciones
    val filteredReactions = remember(reactions, selectedFilterEmoji) {
        if (selectedFilterEmoji == null) {
            reactions.toList()
        } else {
            reactions.filter { it.value == selectedFilterEmoji }.toList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Reacciones (${reactions.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Chips para filtrar por emoji
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedFilterEmoji == null,
                        onClick = { selectedFilterEmoji = null },
                        label = { Text("Todos (${reactions.size})", fontSize = 13.sp) },
                        shape = CircleShape
                    )
                }

                items(uniqueEmojis) { emoji ->
                    val count = reactions.values.count { it == emoji }
                    FilterChip(
                        selected = selectedFilterEmoji == emoji,
                        onClick = { selectedFilterEmoji = if (selectedFilterEmoji == emoji) null else emoji },
                        label = { Text("$emoji $count", fontSize = 13.sp) },
                        shape = CircleShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            // Lista de usuarios que reaccionaron
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredReactions.isEmpty()) {
                    item {
                        Text(
                            text = "No hay reacciones con este filtro.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }

                items(filteredReactions, key = { it.first }) { (username, emoji) ->
                    val user = userMap[username.lowercase()]
                    val displayName = user?.displayName ?: username
                    val avatarUrl = user?.avatarRef ?: ""

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onNavigateToProfile(username)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarImage(
                            avatarUrl = avatarUrl,
                            displayName = displayName,
                            size = 44.dp,
                            showOnlineIndicator = true,
                            isOnline = user?.isOnline == true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = username,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Badge del emoji de reacción
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
