package com.fairshare.android.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.GroupMember
import com.fairshare.android.core.network.backend.model.GroupRole
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.launch

/**
 * Task C: Group Membership & Identity Screen / Dialog.
 * Supports:
 * - Stable user ID roster with Roles (OWNER, ADMIN, MEMBER)
 * - Search filter for large groups
 * - Historical member preservation badge (Active vs Archived)
 * - Role modifications (Owner only)
 * - Member removal / Leave group
 */
@Composable
fun ManageMembersDialog(
    groupId: String,
    groupName: String,
    currentUserId: String,
    repository: BackendGroupRepository,
    onDismiss: () -> Unit,
    onInviteClick: (() -> Unit)? = null,
    onMemberChanged: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var memberToRemove by remember { mutableStateOf<GroupMember?>(null) }
    var roleMenuMemberId by remember { mutableStateOf<String?>(null) }
    var newParticipantName by remember { mutableStateOf("") }
    var newParticipantPhone by remember { mutableStateOf("") }
    var isAddingParticipant by remember { mutableStateOf(false) }

    fun loadRoster() {
        scope.launch {
            isLoading = true
            when (val res = repository.getGroupMembersWithRoles(groupId)) {
                is NetworkResult.Success -> {
                    members = res.data
                    isLoading = false
                }
                is NetworkResult.Error -> {
                    errorMessage = res.message
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(groupId) {
        loadRoster()
    }

    val currentUserMember = members.find { it.userId == currentUserId }
    val isOwner = currentUserMember?.role == GroupRole.OWNER || currentUserMember == null

    val filteredMembers = members.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.phoneNumber.contains(searchQuery, ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 500.dp)
                .imePadding()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Group Roster",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$groupName (${members.count { !it.isArchived }} active)",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                    if (onInviteClick != null) {
                        FSButton(
                            text = "+ Invite",
                            onClick = onInviteClick,
                            variant = FSButtonVariant.Primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or phone...", color = FairShareTheme.colors.disabled) },
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

                Spacer(modifier = Modifier.height(10.dp))

                // Direct Participant Add (Owner/Admin can add non-account participants)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newParticipantName,
                        onValueChange = {
                            newParticipantName = it
                            errorMessage = null
                        },
                        placeholder = { Text("Add person by name...", color = FairShareTheme.colors.disabled) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary,
                            focusedContainerColor = FairShareTheme.colors.surface,
                            unfocusedContainerColor = FairShareTheme.colors.surface
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = if (isAddingParticipant) "..." else "+ Add",
                        onClick = {
                            if (newParticipantName.isNotBlank() && !isAddingParticipant) {
                                isAddingParticipant = true
                                scope.launch {
                                    when (val res = repository.addNonAccountMember(
                                        groupId = groupId,
                                        name = newParticipantName.trim(),
                                        phoneNumber = newParticipantPhone.ifBlank { null }
                                    )) {
                                        is NetworkResult.Success -> {
                                            newParticipantName = ""
                                            newParticipantPhone = ""
                                            loadRoster()
                                            onMemberChanged()
                                        }
                                        is NetworkResult.Error -> {
                                            errorMessage = res.message
                                        }
                                    }
                                    isAddingParticipant = false
                                }
                            }
                        },
                        enabled = newParticipantName.isNotBlank() && !isAddingParticipant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = FairShareTheme.colors.accent,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (members.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No members found in this group.",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredMembers, key = { it.userId }) { member ->
                            val isSelf = member.userId == currentUserId
                            val initials = member.displayName
                                .split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString("")
                                .ifBlank { "?" }

                            FSCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = if (member.isArchived) FairShareTheme.colors.surface.copy(alpha = 0.6f) else FairShareTheme.colors.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Initials Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(FairShareTheme.shapes.button)
                                            .background(
                                                if (isSelf) FairShareTheme.colors.accentSoft
                                                else FairShareTheme.colors.surfaceElevated
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelf) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                                FairShareTheme.shapes.button
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            style = FairShareTheme.typography.supporting,
                                            color = if (isSelf) FairShareTheme.colors.accent else FairShareTheme.colors.textPrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Name and Phone
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = member.displayName,
                                                style = FairShareTheme.typography.body,
                                                color = if (member.isArchived) FairShareTheme.colors.textTertiary else FairShareTheme.colors.textPrimary
                                            )
                                            if (isSelf) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(You)",
                                                    style = FairShareTheme.typography.metadata,
                                                    color = FairShareTheme.colors.accent
                                                )
                                            }
                                        }
                                        if (member.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = member.phoneNumber,
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textTertiary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // Role / Archived Badge
                                    if (member.isArchived) {
                                        FSStatusBadge(text = "Archived")
                                    } else {
                                        when (member.role) {
                                            GroupRole.OWNER -> FSStatusBadge(text = "Owner")
                                            GroupRole.ADMIN -> FSStatusBadge(text = "Admin")
                                            GroupRole.MEMBER -> {
                                                Text(
                                                    text = "Member",
                                                    style = FairShareTheme.typography.metadata,
                                                    color = FairShareTheme.colors.textTertiary
                                                )
                                            }
                                        }
                                    }

                                    // Action dropdown for Owner / Self
                                    if (!member.isArchived && isOwner && !isSelf) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box {
                                            Text(
                                                text = "•••",
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textSecondary,
                                                modifier = Modifier
                                                    .clickable { roleMenuMemberId = member.userId }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                            )

                                            DropdownMenu(
                                                expanded = roleMenuMemberId == member.userId,
                                                onDismissRequest = { roleMenuMemberId = null },
                                                modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                                            ) {
                                                if (member.role == GroupRole.MEMBER) {
                                                    DropdownMenuItem(
                                                        text = { Text("Make Admin", color = FairShareTheme.colors.textPrimary) },
                                                        onClick = {
                                                            roleMenuMemberId = null
                                                            scope.launch {
                                                                repository.updateMemberRole(groupId, member.userId, GroupRole.ADMIN)
                                                                loadRoster()
                                                                onMemberChanged()
                                                            }
                                                        }
                                                    )
                                                } else if (member.role == GroupRole.ADMIN) {
                                                    DropdownMenuItem(
                                                        text = { Text("Demote to Member", color = FairShareTheme.colors.textPrimary) },
                                                        onClick = {
                                                            roleMenuMemberId = null
                                                            scope.launch {
                                                                repository.updateMemberRole(groupId, member.userId, GroupRole.MEMBER)
                                                                loadRoster()
                                                                onMemberChanged()
                                                            }
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Remove from Group", color = FairShareTheme.colors.negative) },
                                                    onClick = {
                                                        roleMenuMemberId = null
                                                        memberToRemove = member
                                                    }
                                                )
                                            }
                                        }
                                    } else if (!member.isArchived && isSelf && !isOwner) {
                                        // Self leave action
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Leave",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.negative,
                                            modifier = Modifier
                                                .clickable { memberToRemove = member }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage!!,
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                FSButton(
                    text = "Done",
                    onClick = onDismiss,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Confirmation Dialog for Member Removal / Leave (Highlighting Historical Preservation)
    if (memberToRemove != null) {
        val target = memberToRemove!!
        val isSelf = target.userId == currentUserId

        Dialog(onDismissRequest = { memberToRemove = null }) {
            Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (isSelf) "Leave Group?" else "Remove ${target.displayName}?",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSelf) {
                            "You will lose access to new expenses in $groupName. Your past splits and payments will be safely preserved in the ledger."
                        } else {
                            "This user will no longer be able to log expenses. If they participated in past splits, their record will be safely archived to preserve ledger balance integrity."
                        },
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "Cancel",
                            onClick = { memberToRemove = null },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        FSButton(
                            text = if (isSelf) "Leave" else "Remove",
                            onClick = {
                                val userIdToRemove = target.userId
                                memberToRemove = null
                                scope.launch {
                                    when (val res = repository.removeMember(groupId, userIdToRemove)) {
                                        is NetworkResult.Success -> {
                                            loadRoster()
                                            onMemberChanged()
                                            if (isSelf) onDismiss()
                                        }
                                        is NetworkResult.Error -> {
                                            errorMessage = res.message
                                        }
                                    }
                                }
                            },
                            variant = FSButtonVariant.Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
