package com.fairshare.android.feature.groups

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.GroupMember
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatMillisToDate(millis: Long?): String? {
    if (millis == null) return null
    return SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))
}

private fun String.toGroupTypeLabel(): String = when (this.uppercase()) {
    "TRIP" -> "Trip"
    "HOUSEHOLD" -> "Home"
    "HOME" -> "Home"
    "EVENT" -> "Event"
    "OTHER" -> "Other"
    else -> this.replace("_", " ").lowercase()
        .replaceFirstChar { it.uppercase() }
}

@Composable
fun GroupOverviewScreen(
    group: GroupEntity,
    currentUser: BackendUser,
    repository: BackendGroupRepository,
    expenseRepository: ExpenseRepository,
    settlementRepository: SettlementRepository,
    onBackClick: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onSettleUpClick: () -> Unit,
    onManageMembersClick: () -> Unit,
    onInviteClick: () -> Unit,
    onGroupUpdated: (GroupEntity) -> Unit,
    onChatClick: () -> Unit = {},
    onActivityClick: () -> Unit = {},
    onTripPlannerClick: () -> Unit = {},
    onBudgetClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var members by remember { mutableStateOf<List<GroupMember>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var payments by remember { mutableStateOf<List<SettlementPayment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentGroupState by remember { mutableStateOf(group) }
    var showEditDialog by remember { mutableStateOf(false) }

    fun refreshGroupData() {
        scope.launch {
            isLoading = true
            when (val res = repository.getGroupMembersWithRoles(currentGroupState.id)) {
                is NetworkResult.Success -> {
                    members = res.data
                }
                is NetworkResult.Error -> {}
            }
            expenses = expenseRepository.getExpenses(currentGroupState.id)
            payments = settlementRepository.getPayments(currentGroupState.id)
            isLoading = false
        }
    }

    LaunchedEffect(currentGroupState.id) {
        refreshGroupData()
    }

    val domainMembers = members.map {
        Member(it.userId, it.displayName, it.userId == currentUser.id)
    }

    val summary = remember(domainMembers, expenses, payments) {
        if (domainMembers.isNotEmpty()) {
            FinancialEngine.calculateSummary(domainMembers, expenses, payments)
        } else null
    }

    val myBalance = summary?.balances?.find { it.memberId == currentUser.id }?.netBalance

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // ── Top App Bar with back, group name, Chat & Activity discoverable ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = FairShareTheme.colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = currentGroupState.name,
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentGroupState.groupType.toGroupTypeLabel()} · ${currentGroupState.currency}",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
            }

            // Chat, Activity & Trip discoverable actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onTripPlannerClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = "Open Trip",
                        tint = FairShareTheme.colors.accent
                    )
                }
                IconButton(
                    onClick = onChatClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "Group Chat",
                        tint = FairShareTheme.colors.accent
                    )
                }
                IconButton(
                    onClick = onActivityClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Group Activity",
                        tint = FairShareTheme.colors.textSecondary
                    )
                }
            }
        }

        // ── Scrollable Body ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Financial Balance Overview Card
            FSCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = FairShareTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "GROUP FINANCIAL POSITION",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Group Spend",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = summary?.totalSpend?.formatted() ?: "₹0.00",
                                style = FairShareTheme.typography.title,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Your Net Balance",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val paise = myBalance?.paise ?: 0L
                            val balanceColor = when {
                                paise > 0L -> FairShareTheme.colors.positive
                                paise < 0L -> FairShareTheme.colors.negative
                                else -> FairShareTheme.colors.textSecondary
                            }
                            val balanceText = when {
                                paise > 0L -> "+${myBalance?.formatted()}"
                                paise < 0L -> myBalance?.formatted() ?: "₹0.00"
                                else -> "Settled Up"
                            }
                            Text(
                                text = balanceText,
                                style = FairShareTheme.typography.title,
                                color = balanceColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Focused Actions: Add Expense & Settle Up
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "+ Add Expense",
                            onClick = onAddExpenseClick,
                            variant = FSButtonVariant.Primary,
                            modifier = Modifier.weight(1.2f)
                        )
                        FSButton(
                            text = "Settle Up",
                            onClick = onSettleUpClick,
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick secondary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FSButton(
                    text = "Budget",
                    onClick = onBudgetClick,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                FSButton(
                    text = "Invite",
                    onClick = onInviteClick,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
                FSButton(
                    text = "Roster",
                    onClick = onManageMembersClick,
                    variant = FSButtonVariant.Secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Recent Expenses Section
            FSCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = FairShareTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "RECENT EXPENSES (${expenses.size})",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (expenses.isEmpty()) {
                        Text(
                            text = "No expenses recorded in this group yet.",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        expenses.take(4).forEach { exp ->
                            val dateStr = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(exp.timestamp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = exp.description.ifEmpty { exp.category.displayName },
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${exp.category.displayName} · $dateStr",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                }
                                Text(
                                    text = exp.totalAmount.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            HorizontalDivider(color = FairShareTheme.colors.border.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Member Balances Roster
            FSCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = FairShareTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MEMBERS (${members.count { !it.isArchived }})",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage →",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            modifier = Modifier.clickable { onManageMembersClick() }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = FairShareTheme.colors.accent,
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        members.filter { !it.isArchived }.forEach { member ->
                            val memberBal = summary?.balances?.find { it.memberId == member.userId }?.netBalance
                            val isSelf = member.userId == currentUser.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = member.displayName,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = if (isSelf) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (isSelf) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(You)",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (memberBal != null) {
                                    val bPaise = memberBal.paise
                                    val color = when {
                                        bPaise > 0L -> FairShareTheme.colors.positive
                                        bPaise < 0L -> FairShareTheme.colors.negative
                                        else -> FairShareTheme.colors.textSecondary
                                    }
                                    val formatted = when {
                                        bPaise > 0L -> "+${memberBal.formatted()}"
                                        bPaise < 0L -> memberBal.formatted()
                                        else -> "Settled"
                                    }
                                    Text(
                                        text = formatted,
                                        style = FairShareTheme.typography.supporting,
                                        color = color,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            HorizontalDivider(color = FairShareTheme.colors.border.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            // Group Info & Lifecycle Controls
            FSCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = FairShareTheme.colors.surface
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GROUP SETTINGS",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (currentGroupState.description.isNotBlank()) {
                        Text(
                            text = currentGroupState.description,
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (currentGroupState.startDate != null || currentGroupState.endDate != null) {
                        val sStr = formatMillisToDate(currentGroupState.startDate) ?: "Start"
                        val eStr = formatMillisToDate(currentGroupState.endDate) ?: "Ongoing"
                        Text(
                            text = "Timeline: $sStr → $eStr",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "Edit Group",
                            onClick = { showEditDialog = true },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        if (!currentGroupState.isArchived) {
                            FSButton(
                                text = "Archive",
                                onClick = {
                                    scope.launch {
                                        when (val res = repository.archiveGroup(currentGroupState.id)) {
                                            is NetworkResult.Success -> {
                                                currentGroupState = res.data
                                                onGroupUpdated(currentGroupState)
                                            }
                                            is NetworkResult.Error -> {}
                                        }
                                    }
                                },
                                variant = FSButtonVariant.Secondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Edit Group Dialog
    if (showEditDialog) {
        var editName by remember { mutableStateOf(currentGroupState.name) }
        var editDescription by remember { mutableStateOf(currentGroupState.description) }
        var isSaving by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isSaving) showEditDialog = false }) {
            Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .clip(FairShareTheme.shapes.dialog)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Edit Group Details",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Group Name", color = FairShareTheme.colors.textSecondary) },
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

                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text("Description", color = FairShareTheme.colors.textSecondary) },
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "Cancel",
                            onClick = { showEditDialog = false },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                        FSButton(
                            text = if (isSaving) "Saving..." else "Save",
                            onClick = {
                                if (editName.isNotBlank()) {
                                    isSaving = true
                                    scope.launch {
                                        when (val res = repository.updateGroup(
                                            groupId = currentGroupState.id,
                                            name = editName.trim(),
                                            description = editDescription.trim(),
                                            currency = currentGroupState.currency,
                                            startDate = currentGroupState.startDate,
                                            endDate = currentGroupState.endDate
                                        )) {
                                            is NetworkResult.Success -> {
                                                isSaving = false
                                                currentGroupState = res.data
                                                onGroupUpdated(currentGroupState)
                                                showEditDialog = false
                                            }
                                            is NetworkResult.Error -> {
                                                isSaving = false
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = editName.isNotBlank() && !isSaving,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
