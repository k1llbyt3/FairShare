package com.fairshare.android.feature.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LinearProgressIndicator
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.summary.DailySummaryEngine
import com.fairshare.android.core.domain.summary.DailySummaryFacts
import com.fairshare.android.core.model.FinancialSummary
import com.fairshare.android.core.sync.SyncConflictRecord
import com.fairshare.android.core.sync.SyncEngineState
import com.fairshare.android.core.sync.SyncEntityType
import com.fairshare.android.core.sync.SyncStatus
import com.fairshare.android.feature.sync.ConflictResolutionDialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val CHART_PALETTE = listOf(
    Color(0xFF00C896), // FairShare Mint
    Color(0xFF3B82F6), // Blue
    Color(0xFFF59E0B), // Amber
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Violet
    Color(0xFF10B981), // Emerald
    Color(0xFF6366F1), // Indigo
    Color(0xFF14B8A6)  // Teal
)

@Composable
fun HomeScreen(
    currentMember: Member,
    tripName: String,
    summary: FinancialSummary,
    expenses: List<Expense>,
    payments: List<SettlementPayment>,
    budgetPlan: BudgetPlan? = null,
    onAddExpenseClick: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    onViewSettlementClick: () -> Unit,
    onManageMembersClick: () -> Unit,
    onSetBudgetClick: () -> Unit = {},
    onEditBudgetClick: () -> Unit = {},
    onViewBudgetClick: () -> Unit = {},
    onViewDailyBriefClick: () -> Unit = {},
    onViewVaultClick: () -> Unit = {},
    onViewBundlesClick: () -> Unit = {},
    onViewChatClick: () -> Unit = {},
    onViewActivityClick: () -> Unit = {},
    onViewSearchClick: () -> Unit = {},
    onViewExportClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onInviteClick: () -> Unit = {},
    onSwitchGroupClick: () -> Unit = {},
    onExpenseClick: (Expense) -> Unit = {},
    onViewAllExpensesClick: () -> Unit = {},
    syncState: SyncEngineState = SyncEngineState(),
    onSyncClick: () -> Unit = {},
    onRetrySyncClick: () -> Unit = {},
    onResolveConflictKeepLocal: (String, SyncEntityType) -> Unit = { _, _ -> },
    onResolveConflictAcceptRemote: (String, SyncEntityType) -> Unit = { _, _ -> },
    expenseSyncStatuses: Map<String, SyncStatus> = emptyMap(),
    hasGroups: Boolean = true,
    onCreateGroupClick: () -> Unit = {},
    onJoinGroupClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val myBalance = summary.balances.find { it.memberId == currentMember.id }?.netBalance
    val isOwed = (myBalance?.paise ?: 0L) > 0L
    val isDebtor = (myBalance?.paise ?: 0L) < 0L

    val activeExpenses = remember(expenses) { expenses.filter { !it.isDeleted } }
    val categoryTotals = remember(activeExpenses) {
        activeExpenses
            .groupBy { it.category }
            .mapValues { (_, exps) -> exps.sumOf { it.totalAmount.paise } }
            .filter { it.value > 0L }
    }

    // Canonical Budget State and Daily Target calculations
    val budgetState = remember(budgetPlan, activeExpenses) {
        budgetPlan?.let { BudgetEngine.calculateBudgetState(it, activeExpenses) }
    }
    val budgetVelocity = remember(budgetPlan, budgetState) {
        if (budgetPlan != null && budgetState != null) {
            BudgetEngine.calculateSpendingVelocity(budgetPlan, budgetState.totalSpent)
        } else null
    }
    val budgetDailyTarget = remember(budgetState, budgetVelocity) {
        if (budgetState != null && budgetVelocity != null) {
            BudgetEngine.calculateDailyTarget(budgetState, budgetVelocity.remainingDays)
        } else null
    }

    var selectedDateEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val isToday = remember(selectedDateEpochMs) {
        val now = System.currentTimeMillis()
        (selectedDateEpochMs / 86_400_000L) == (now / 86_400_000L)
    }
    val dateLabel = remember(selectedDateEpochMs, isToday) {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        val formatted = sdf.format(java.util.Date(selectedDateEpochMs))
        if (isToday) "Today ($formatted)" else formatted
    }

    // Real Daily Brief facts derived directly from persisted data for selected date
    val selectedDailyFacts = remember(activeExpenses, budgetPlan, selectedDateEpochMs) {
        DailySummaryEngine.calculateDailyFacts(
            plan = budgetPlan,
            expenses = activeExpenses,
            targetDateEpochMs = selectedDateEpochMs
        )
    }

    var activeConflictToResolve by remember { mutableStateOf<SyncConflictRecord?>(null) }
    val realDisplayName = currentMember.name.ifBlank { "User" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // ── 1. Top App Bar / Identity (Single Header with Group Switcher & Notifications) ──
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
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = hasGroups) { onSwitchGroupClick() }
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.fairshare.android.R.drawable.icon),
                    contentDescription = "FairShare Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FAIRSHARE",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    if (hasGroups) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Text(
                                text = tripName.ifBlank { "My Group" },
                                style = FairShareTheme.typography.title,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = " ▾",
                                style = FairShareTheme.typography.title,
                                color = FairShareTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Top-right Notifications icon ONLY
            IconButton(
                onClick = onViewActivityClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications & Activity",
                    tint = FairShareTheme.colors.textPrimary
                )
            }
        }

        // ── Scrollable Body ──────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // No-groups welcome state
            if (!hasGroups) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome to FairShare",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create a group or join with an invite code to start splitting shared expenses.",
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
            }

            // Sync alert banners
            if (syncState.conflictCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FairShareTheme.shapes.button)
                            .background(FairShareTheme.colors.negativeSoft)
                            .clickable { activeConflictToResolve = syncState.activeConflicts.firstOrNull() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${syncState.conflictCount} sync conflict(s) — tap to review",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.negative,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else if (syncState.failedCount > 0) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FairShareTheme.shapes.button)
                            .background(FairShareTheme.colors.negativeSoft)
                            .clickable { onRetrySyncClick() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${syncState.failedCount} change(s) failed to sync — tap to retry",
                            style = FairShareTheme.typography.supporting,
                            color = FairShareTheme.colors.negative,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (hasGroups) {
                // ── 2. Daily Expense Details Box (Clean Date Navigation & Daily Financial Facts) ──
                item {
                    FSCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewDailyBriefClick() },
                        backgroundColor = FairShareTheme.colors.surfaceElevated
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Expense Details",
                                    style = FairShareTheme.typography.title,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Brief Details ›",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Date Navigation: [← Prev] [Date] [Next →]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedDateEpochMs -= 86_400_000L },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("◀", style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                                }
                                Text(
                                    text = dateLabel,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { selectedDateEpochMs += 86_400_000L },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("▶", style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Daily Spent",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedDailyFacts.totalSpentToday.formatted(),
                                        style = FairShareTheme.typography.display,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Transactions",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${selectedDailyFacts.expenseCountToday} expense(s)",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = selectedDailyFacts.headline,
                                style = FairShareTheme.typography.supporting,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Medium
                            )

                            if (selectedDailyFacts.summaryNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedDailyFacts.summaryNotes.first(),
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // ── 2.1 Trip Budget & Runway Section (Prominent Set / Edit Budget CTA) ──
                item {
                    if (budgetPlan == null || budgetState == null) {
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
                                        text = "TRIP BUDGET & RUNWAY",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FSButton(
                                        text = "Set Budget",
                                        onClick = onSetBudgetClick,
                                        variant = FSButtonVariant.Primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No budget configured yet",
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Set an overall trip budget, emergency buffer, and category allocations to monitor spending pace.",
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    } else {
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
                                        text = "TRIP BUDGET & RUNWAY",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FSButton(
                                            text = "Edit Budget",
                                            onClick = onEditBudgetClick,
                                            variant = FSButtonVariant.Secondary
                                        )
                                        Text(
                                            text = "Details ›",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.accent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { onViewBudgetClick() }
                                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = "Overall Budget Spend",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = budgetState.totalSpent.formatted(),
                                                style = FairShareTheme.typography.section,
                                                color = if (budgetState.isBaseBudgetExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = " / ${budgetPlan.totalEnvelope.formatted()}",
                                                style = FairShareTheme.typography.supporting,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (budgetState.isBaseBudgetExceeded) "Buffer Remaining" else "Remaining",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = budgetState.totalEnvelopeRemaining.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = if (budgetState.isBufferExhausted) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val progress = (budgetState.percentageUsed / 100.0).coerceIn(0.0, 1.0).toFloat()
                                val progressColor = when {
                                    budgetState.isBufferExhausted -> FairShareTheme.colors.negative
                                    budgetState.isBaseBudgetExceeded -> FairShareTheme.colors.negative
                                    progress > 0.8f -> Color(0xFFF59E0B)
                                    else -> FairShareTheme.colors.accent
                                }
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = progressColor,
                                    trackColor = FairShareTheme.colors.border
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FairShareTheme.colors.surfaceElevated)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Daily Target",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (budgetDailyTarget != null) "${budgetDailyTarget.formatted()}/day" else "—",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (budgetVelocity != null) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "${budgetVelocity.remainingDays} days remaining",
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val pacingText = if (budgetVelocity.isPacingAhead) "Pacing Ahead" else "On Track"
                                            val pacingColor = if (budgetVelocity.isPacingAhead) Color(0xFFF59E0B) else FairShareTheme.colors.positive
                                            Text(
                                                text = pacingText,
                                                style = FairShareTheme.typography.supporting,
                                                color = pacingColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                if (budgetState.categoryStates.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Category Allocations",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    budgetState.categoryStates.take(3).forEach { catState ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = catState.category.displayName,
                                                style = FairShareTheme.typography.supporting,
                                                color = FairShareTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${catState.spent.formatted()} / ${catState.allocated.formatted()}",
                                                style = FairShareTheme.typography.metadata,
                                                color = if (catState.isExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.textSecondary,
                                                fontWeight = if (catState.isExceeded) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 3. Personal Money Summary Card ────────────────────────────
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            val balanceLabel = when {
                                isOwed -> "You are owed"
                                isDebtor -> "You owe"
                                else -> "All settled up"
                            }
                            val balanceColor = when {
                                isOwed -> FairShareTheme.colors.positive
                                isDebtor -> FairShareTheme.colors.negative
                                else -> FairShareTheme.colors.textSecondary
                            }
                            Text(
                                text = balanceLabel,
                                style = FairShareTheme.typography.supporting,
                                color = balanceColor,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (myBalance != null && myBalance.paise != 0L)
                                    myBalance.formatted() else "₹0.00",
                                style = FairShareTheme.typography.display,
                                color = balanceColor,
                                fontWeight = FontWeight.Bold
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 14.dp),
                                color = FairShareTheme.colors.border
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Group Total Spend",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = summary.totalSpend.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Your Share",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = summary.perPersonShare.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (summary.settlements.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Settle Up  ›",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onViewSettlementClick() }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // ── 4. Primary CTA: Add Expense & Record Payment ──────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FSButton(
                            text = "+ Add Expense",
                            onClick = onAddExpenseClick,
                            modifier = Modifier.weight(1.2f)
                        )
                        FSButton(
                            text = "Record Payment",
                            onClick = onRecordPaymentClick,
                            modifier = Modifier.weight(1f),
                            variant = FSButtonVariant.Secondary
                        )
                    }
                }

                // ── 5. Recent Expenses List ──────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Expenses",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeExpenses.size > 5) {
                            Text(
                                text = "View all (${activeExpenses.size})",
                                style = FairShareTheme.typography.supporting,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable { onViewAllExpensesClick() }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            )
                        }
                    }
                }

                if (activeExpenses.isEmpty()) {
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No expenses recorded yet.",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap '+ Add Expense' to record your first group expense.",
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(activeExpenses.take(5)) { expense ->
                        ExpenseRow(
                            expense = expense,
                            onClick = { onExpenseClick(expense) }
                        )
                    }
                }

                // ── 6. Real Analytics (Category Donut & Spending Over Time) ──
                item {
                    Text(
                        text = "Spending Analytics",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                if (activeExpenses.isEmpty()) {
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surface
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "No spending data available yet",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Category breakdown and trend charts will appear here as expenses are added.",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Category Donut Chart Card
                    item {
                        CategoryDonutChartCard(
                            categoryTotals = categoryTotals,
                            totalSpendPaise = summary.totalSpend.paise,
                            currency = summary.totalSpend.currency
                        )
                    }

                    // Spending-over-time Bar Chart Card
                    item {
                        SpendingOverTimeBarChartCard(
                            expenses = activeExpenses,
                            currency = summary.totalSpend.currency
                        )
                    }
                }
            }
        }
    }

    // Conflict resolution dialog
    val conflictToResolve = activeConflictToResolve
    if (conflictToResolve != null) {
        ConflictResolutionDialog(
            conflict = conflictToResolve,
            onKeepLocal = { id, type ->
                onResolveConflictKeepLocal(id, type)
                activeConflictToResolve = null
            },
            onAcceptRemote = { id, type ->
                onResolveConflictAcceptRemote(id, type)
                activeConflictToResolve = null
            },
            onDismiss = { activeConflictToResolve = null }
        )
    }
}

@Composable
private fun CategoryDonutChartCard(
    categoryTotals: Map<ExpenseCategory, Long>,
    totalSpendPaise: Long,
    currency: Currency
) {
    FSCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = FairShareTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Category Breakdown",
                style = FairShareTheme.typography.section,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            val sortedCategories = remember(categoryTotals) {
                categoryTotals.entries.sortedByDescending { it.value }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Donut Canvas
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 24.dp.toPx()
                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                        var startAngle = -90f
                        sortedCategories.forEachIndexed { index, entry ->
                            val color = CHART_PALETTE[index % CHART_PALETTE.size]
                            val sweepAngle = if (totalSpendPaise > 0L) {
                                (entry.value.toFloat() / totalSpendPaise.toFloat()) * 360f
                            } else 0f

                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                    Text(
                        text = "${sortedCategories.size}\nTypes",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedCategories.take(4).forEachIndexed { index, entry ->
                        val color = CHART_PALETTE[index % CHART_PALETTE.size]
                        val pct = if (totalSpendPaise > 0L) {
                            (entry.value * 100 / totalSpendPaise).toInt()
                        } else 0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.key.displayName,
                                    style = FairShareTheme.typography.supporting,
                                    color = FairShareTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$pct%",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingOverTimeBarChartCard(
    expenses: List<Expense>,
    currency: Currency
) {
    FSCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = FairShareTheme.colors.surface
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Recent Daily Spending",
                style = FairShareTheme.typography.section,
                color = FairShareTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Group expenses by past 5 days
            val calendar = Calendar.getInstance()
            val dayBins = remember(expenses) {
                val list = mutableListOf<Pair<String, Long>>()
                val sdf = SimpleDateFormat("d MMM", Locale.getDefault())
                val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.ROOT)

                for (i in 4 downTo 0) {
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                    val label = sdf.format(cal.time)
                    val key = dayFormat.format(cal.time)

                    val spendForDay = expenses.filter {
                        val expCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        dayFormat.format(expCal.time) == key
                    }.sumOf { it.totalAmount.paise }

                    list.add(label to spendForDay)
                }
                list
            }

            val maxSpend = remember(dayBins) { dayBins.maxOfOrNull { it.second } ?: 1L }.coerceAtLeast(1L)

            // Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dayBins.forEach { (label, paise) ->
                    val fraction = (paise.toFloat() / maxSpend.toFloat()).coerceIn(0.04f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (paise > 0L) {
                            Text(
                                text = "₹${paise / 100}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height((80 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (paise > 0L) FairShareTheme.colors.accent
                                    else FairShareTheme.colors.surfaceElevated
                                )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = label,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit
) {
    val dateStr = remember(expense.timestamp) {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(expense.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = expense.description.ifEmpty { expense.category.displayName },
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${expense.category.displayName} · $dateStr · ${expense.involvedMemberIds.size} people",
                style = FairShareTheme.typography.metadata,
                color = FairShareTheme.colors.textSecondary
            )
        }
        Text(
            text = expense.totalAmount.formatted(),
            style = FairShareTheme.typography.section,
            color = FairShareTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = FairShareTheme.colors.border, thickness = 0.5.dp)
}
