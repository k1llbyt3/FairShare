package com.fairshare.android.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.launch

@Composable
fun JoinGroupDialog(
    repository: BackendGroupRepository,
    onDismiss: () -> Unit,
    onGroupJoined: (GroupEntity) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Join with Invite Code",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enter the 8-character invitation code shared by a group admin.",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = {
                        inviteCode = it.uppercase()
                        errorMessage = null
                    },
                    label = { Text("Invite Code", color = FairShareTheme.colors.textTertiary) },
                    placeholder = { Text("e.g. FAIR-9X2K", color = FairShareTheme.colors.disabled) },
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

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = errorMessage!!,
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FSButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    )
                    FSButton(
                        text = if (isLoading) "Joining..." else "Join Group",
                        onClick = {
                            if (inviteCode.isNotBlank()) {
                                isLoading = true
                                errorMessage = null
                                scope.launch {
                                    when (val res = repository.joinGroupByInvite(inviteCode.trim())) {
                                        is NetworkResult.Success -> {
                                            isLoading = false
                                            onGroupJoined(res.data)
                                        }
                                        is NetworkResult.Error -> {
                                            isLoading = false
                                            errorMessage = res.message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = inviteCode.isNotBlank() && !isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun InviteCodeDialog(
    groupName: String,
    inviteCode: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Invite to $groupName",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Share this code with friends so they can join the shared ledger.",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, FairShareTheme.colors.accent.copy(alpha = 0.6f), FairShareTheme.shapes.card)
                        .background(FairShareTheme.colors.surface, FairShareTheme.shapes.card)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inviteCode,
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Valid for 7 days • Group Member role",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textTertiary
                )

                Spacer(modifier = Modifier.height(24.dp))

                FSButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
