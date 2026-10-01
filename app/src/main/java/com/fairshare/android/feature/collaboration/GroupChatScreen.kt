package com.fairshare.android.feature.collaboration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.collaboration.GroupMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    groupName: String,
    container: FairShareAppContainer,
    onNavigateBack: () -> Unit,
    onOpenExpense: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = container.collaborationRepository
    val currentUserId = container.sessionStorage.currentUserId ?: ""
    val messagesFlow = remember(groupId) { repo.getMessagesFlow(groupId, currentUserId) }
    val messages by messagesFlow.collectAsState(initial = emptyList())

    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = groupName,
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = "Group Chat & Discussion",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = FairShareTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FairShareTheme.colors.surface
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = {
                        Text(
                            text = "Message group or clarify expense...",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textTertiary
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = messageText.trim()
                        if (text.isNotBlank()) {
                            scope.launch {
                                repo.sendMessage(
                                    groupId = groupId,
                                    senderId = currentUserId,
                                    content = text
                                )
                                messageText = ""
                            }
                        }
                    },
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (messageText.isNotBlank()) FairShareTheme.colors.accent else FairShareTheme.colors.textTertiary
                    )
                }
            }
        },
        containerColor = FairShareTheme.colors.background
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet.\nStart a discussion or clarify an expense with the group.",
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isFromCurrentUser = msg.isFromCurrentUser,
                        onOpenExpense = onOpenExpense
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: GroupMessage,
    isFromCurrentUser: Boolean,
    onOpenExpense: (String) -> Unit
) {
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    ) {
        if (!isFromCurrentUser) {
            Text(
                text = message.senderName,
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .background(
                    color = if (isFromCurrentUser) FairShareTheme.colors.surfaceElevated else FairShareTheme.colors.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Column {
                if (message.referencedEntityId != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FairShareTheme.colors.background, RoundedCornerShape(4.dp))
                            .clickable { onOpenExpense(message.referencedEntityId) }
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "Referenced Expense",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                            if (message.referencedEntitySummary != null) {
                                Text(
                                    text = message.referencedEntitySummary,
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = message.content,
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeStr,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textTertiary
                    )
                }
            }
        }
    }
}
