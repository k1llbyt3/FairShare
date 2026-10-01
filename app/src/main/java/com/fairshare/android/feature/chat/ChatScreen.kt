package com.fairshare.android.feature.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.FairShareAppContainer
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.collaboration.GroupMessage
import com.fairshare.android.core.network.client.NetworkResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    groups: List<GroupEntity> = emptyList(),
    selectedGroupId: String? = null,
    onSelectGroup: (GroupEntity) -> Unit = {},
    container: FairShareAppContainer,
    currentUserId: String,
    onNavigateBack: (() -> Unit)? = null,
    onOpenExpense: ((String) -> Unit)? = null,
    onMemberAdded: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeGroup = remember(groups, selectedGroupId) {
        groups.firstOrNull { it.id == selectedGroupId } ?: groups.firstOrNull()
    }

    val repo = container.collaborationRepository
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    var messageInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    var showAddPersonDialog by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }
    var newPersonPhone by remember { mutableStateOf("") }
    var isAddingPerson by remember { mutableStateOf(false) }

    val messagesFlow = remember(activeGroup?.id, currentUserId) {
        if (activeGroup != null) {
            repo.getMessagesFlow(activeGroup.id, currentUserId)
        } else {
            null
        }
    }
    val messages by messagesFlow?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val handleSend = {
        val trimmed = messageInput.trim()
        if (trimmed.isNotBlank() && !isSending && activeGroup != null) {
            isSending = true
            scope.launch {
                try {
                    repo.sendMessage(
                        groupId = activeGroup.id,
                        senderId = currentUserId,
                        content = trimmed,
                        messageType = "TEXT"
                    )
                    messageInput = ""
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(
                        message = "Failed to save message locally: ${e.localizedMessage ?: "Database error"}"
                    )
                } finally {
                    isSending = false
                }
            }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = activeGroup?.name ?: "Group Chat",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeGroup != null) {
                            Text(
                                text = "Local Room Storage • ${messages.size} messages",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                },
                actions = {
                    if (activeGroup != null) {
                        IconButton(onClick = { showAddPersonDialog = true }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(FairShareTheme.shapes.button)
                                    .background(FairShareTheme.colors.accentSoft)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "+ Person",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FairShareTheme.colors.surface
                )
            )
        },
        bottomBar = {
            if (activeGroup != null) {
                Surface(
                    color = FairShareTheme.colors.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = {
                                Text(
                                    text = "Type a message or note...",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textTertiary
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FairShareTheme.colors.accent,
                                unfocusedBorderColor = FairShareTheme.colors.border,
                                focusedTextColor = FairShareTheme.colors.textPrimary,
                                unfocusedTextColor = FairShareTheme.colors.textPrimary,
                                focusedContainerColor = FairShareTheme.colors.surfaceElevated,
                                unfocusedContainerColor = FairShareTheme.colors.surfaceElevated
                            ),
                            maxLines = 4,
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { handleSend() })
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { handleSend() },
                            enabled = messageInput.isNotBlank() && !isSending,
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    color = if (messageInput.isNotBlank() && !isSending) FairShareTheme.colors.accent else FairShareTheme.colors.surfaceElevated,
                                    shape = RoundedCornerShape(22.dp)
                                )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    tint = if (messageInput.isNotBlank()) FairShareTheme.colors.background else FairShareTheme.colors.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = FairShareTheme.colors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Group selector chip row if user has multiple groups
            if (groups.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FairShareTheme.colors.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        val isSelected = activeGroup?.id == group.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectGroup(group) },
                            label = {
                                Text(
                                    text = group.name,
                                    style = FairShareTheme.typography.metadata,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FairShareTheme.colors.accentSoft,
                                selectedLabelColor = FairShareTheme.colors.accent,
                                selectedLeadingIconColor = FairShareTheme.colors.accent,
                                containerColor = FairShareTheme.colors.surfaceElevated,
                                labelColor = FairShareTheme.colors.textSecondary,
                                iconColor = FairShareTheme.colors.textSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                            )
                        )
                    }
                }
            }

            if (groups.isEmpty() || activeGroup == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = FairShareTheme.colors.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Groups Available",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create or join a group first to start chatting and discussing expenses.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = FairShareTheme.colors.textTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No messages yet",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start the group conversation or clarify expenses for ${activeGroup.name}.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        LocalChatMessageBubble(
                            message = msg,
                            isFromCurrentUser = msg.isFromCurrentUser,
                            onOpenExpense = onOpenExpense
                        )
                    }
                }
            }
        }
    }

    if (showAddPersonDialog && activeGroup != null) {
        Dialog(onDismissRequest = { if (!isAddingPerson) showAddPersonDialog = false }) {
            Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Add Person to Group",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add someone without a FairShare account. They will be included in splits and balances.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = { Text("Person's Name *", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. Rahul, Priya", color = FairShareTheme.colors.disabled) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary,
                            focusedContainerColor = FairShareTheme.colors.surface,
                            unfocusedContainerColor = FairShareTheme.colors.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPersonPhone,
                        onValueChange = { newPersonPhone = it },
                        label = { Text("Phone Number (Optional)", color = FairShareTheme.colors.textSecondary) },
                        placeholder = { Text("e.g. +91 9876543210", color = FairShareTheme.colors.disabled) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary,
                            focusedContainerColor = FairShareTheme.colors.surface,
                            unfocusedContainerColor = FairShareTheme.colors.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "Cancel",
                            onClick = { showAddPersonDialog = false },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f),
                            enabled = !isAddingPerson
                        )
                        FSButton(
                            text = if (isAddingPerson) "Adding..." else "Add",
                            onClick = {
                                if (newPersonName.isNotBlank() && !isAddingPerson) {
                                    isAddingPerson = true
                                    scope.launch {
                                        val name = newPersonName.trim()
                                        val phone = newPersonPhone.trim().ifBlank { null }
                                        when (val res = container.backendGroupRepository.addNonAccountMember(
                                            groupId = activeGroup.id,
                                            name = name,
                                            phoneNumber = phone
                                        )) {
                                            is NetworkResult.Success -> {
                                                newPersonName = ""
                                                newPersonPhone = ""
                                                showAddPersonDialog = false
                                                onMemberAdded?.invoke()
                                                try {
                                                    repo.sendMessage(
                                                        groupId = activeGroup.id,
                                                        senderId = currentUserId,
                                                        content = "Added $name to the group",
                                                        messageType = "SYSTEM"
                                                    )
                                                } catch (_: Exception) {}
                                            }
                                            is NetworkResult.Error -> {
                                                snackbarHostState.showSnackbar(
                                                    message = "Failed to add person: ${res.message}"
                                                )
                                            }
                                        }
                                        isAddingPerson = false
                                    }
                                }
                            },
                            enabled = newPersonName.isNotBlank() && !isAddingPerson,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalChatMessageBubble(
    message: GroupMessage,
    isFromCurrentUser: Boolean,
    onOpenExpense: ((String) -> Unit)?
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
                fontWeight = FontWeight.SemiBold,
                color = FairShareTheme.colors.accent,
                modifier = Modifier.padding(start = 6.dp, bottom = 3.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isFromCurrentUser) 14.dp else 2.dp,
                        bottomEnd = if (isFromCurrentUser) 2.dp else 14.dp
                    )
                )
                .background(
                    if (isFromCurrentUser) FairShareTheme.colors.surfaceElevated else FairShareTheme.colors.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isFromCurrentUser) FairShareTheme.colors.accentSoft else FairShareTheme.colors.border,
                    shape = RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isFromCurrentUser) 14.dp else 2.dp,
                        bottomEnd = if (isFromCurrentUser) 2.dp else 14.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (message.referencedEntityId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(FairShareTheme.colors.background)
                            .then(
                                if (onOpenExpense != null) {
                                    Modifier.clickable { onOpenExpense(message.referencedEntityId) }
                                } else Modifier
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = FairShareTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Referenced Expense",
                                style = FairShareTheme.typography.metadata,
                                fontWeight = FontWeight.Bold,
                                color = FairShareTheme.colors.accent
                            )
                            if (message.referencedEntitySummary != null) {
                                Text(
                                    text = message.referencedEntitySummary,
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                Text(
                    text = timeStr,
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textTertiary,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
