package com.fairshare.android.feature.groups

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.launch

@Composable
fun GroupsScreen(
    currentUser: BackendUser,
    activeGroupId: String?,
    repository: BackendGroupRepository,
    expenseRepository: ExpenseRepository,
    settlementRepository: SettlementRepository,
    onSelectGroup: (GroupEntity) -> Unit,
    onOpenGroupOverview: (GroupEntity) -> Unit,
    onCreateGroupClick: () -> Unit,
    onJoinGroupClick: () -> Unit,
    onBackClick: () -> Unit,
    initialGroups: List<GroupEntity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var groups by remember(initialGroups) { mutableStateOf(initialGroups) }
    var groupBalances by remember { mutableStateOf<Map<String, Money>>(emptyMap()) }
    var groupMemberCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(initialGroups.isEmpty()) }

    fun refreshGroups() {
        scope.launch {
            if (groups.isEmpty()) {
                isLoading = true
            }
            val activeRes = repository.fetchUserGroups(includeArchived = false)
            if (activeRes is NetworkResult.Success) {
                // Merge with existing groups so newly created groups are never wiped
                val merged = (groups + activeRes.data).distinctBy { it.id }
                groups = merged
            }

            val balances = mutableMapOf<String, Money>()
            val counts = mutableMapOf<String, Int>()

            groups.forEach { group ->
                val membersRes = repository.getGroupMembersWithRoles(group.id)
                val members = if (membersRes is NetworkResult.Success) membersRes.data else emptyList()
                counts[group.id] = members.count { !it.isArchived }
                val expenses = expenseRepository.getExpenses(group.id)
                val payments = settlementRepository.getPayments(group.id)
                val domainMembers = members.map { Member(it.userId, it.displayName, it.userId == currentUser.id) }
                if (domainMembers.isNotEmpty()) {
                    val summary = FinancialEngine.calculateSummary(domainMembers, expenses, payments)
                    val myBal = summary.balances.find { it.memberId == currentUser.id }?.netBalance
                    if (myBal != null) balances[group.id] = myBal
                }
            }

            groupBalances = balances
            groupMemberCounts = counts
            isLoading = false
        }
    }

    LaunchedEffect(currentUser.id, initialGroups) {
        refreshGroups()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // ── Top Bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Groups",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FSButton(
                    text = "Join",
                    onClick = onJoinGroupClick,
                    variant = FSButtonVariant.Secondary
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FairShareTheme.colors.accent)
                        .clickable { onCreateGroupClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Group",
                        tint = FairShareTheme.colors.background,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        when {
            isLoading && groups.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = FairShareTheme.colors.accent,
                        strokeWidth = 2.dp
                    )
                }
            }
            groups.isEmpty() -> {
                // Empty state only when genuinely no groups exist
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No groups yet",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Create a group or join with an invite code.",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    FSButton(
                        text = "Create a Group",
                        onClick = onCreateGroupClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FSButton(
                        text = "Join with Invite Code",
                        onClick = onJoinGroupClick,
                        modifier = Modifier.fillMaxWidth(),
                        variant = FSButtonVariant.Secondary
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupListRow(
                            group = group,
                            isActive = group.id == activeGroupId,
                            memberCount = groupMemberCounts[group.id] ?: 1,
                            netBalance = groupBalances[group.id],
                            onTap = { onOpenGroupOverview(group) }
                        )
                        HorizontalDivider(
                            color = FairShareTheme.colors.border,
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(start = 72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupListRow(
    group: GroupEntity,
    isActive: Boolean,
    memberCount: Int,
    netBalance: Money?,
    onTap: () -> Unit
) {
    val initials = group.name
        .split(" ", "-")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifBlank { "G" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap() }
            .background(if (isActive) FairShareTheme.colors.accentSoft.copy(alpha = 0.2f) else FairShareTheme.colors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FairShareTheme.colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = FairShareTheme.typography.supporting,
                color = if (isActive) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Group info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    style = FairShareTheme.typography.body,
                    color = FairShareTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(FairShareTheme.shapes.badge)
                            .background(FairShareTheme.colors.accentSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Active",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent
                        )
                    }
                }
            }
            Text(
                text = "$memberCount members · ${group.groupType} · ${group.currency}",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
        }

        // Balance
        Spacer(modifier = Modifier.width(8.dp))
        if (netBalance != null) {
            val paise = netBalance.paise
            when {
                paise > 0L -> Text(
                    text = "+${netBalance.formatted()}",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.positive,
                    fontWeight = FontWeight.SemiBold
                )
                paise < 0L -> Text(
                    text = netBalance.formatted(),
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.negative,
                    fontWeight = FontWeight.SemiBold
                )
                else -> Text(
                    text = "Settled",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
            }
        }
    }
}
