package com.fairshare.android.feature.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.budget.BudgetEngine
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.budget.BufferState
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense

@Composable
fun BudgetScreen(
    tripName: String,
    budgetPlan: BudgetPlan?,
    expenses: List<Expense>,
    onBackClick: () -> Unit,
    onSaveBudget: (BudgetPlan) -> Unit,
    onOpenDailyBrief: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    val currency = budgetPlan?.baseBudget?.currency ?: expenses.firstOrNull()?.amount?.currency ?: Currency.INR

    val budgetState = remember(budgetPlan, expenses) {
        budgetPlan?.let { BudgetEngine.calculateBudgetState(it, expenses) }
    }

    val velocity = remember(budgetPlan, budgetState) {
        if (budgetPlan != null && budgetState != null) {
            BudgetEngine.calculateSpendingVelocity(budgetPlan, budgetState.totalSpent)
        } else null
    }

    val projection = remember(budgetPlan, budgetState) {
        if (budgetPlan != null && budgetState != null) {
            BudgetEngine.calculateProjection(budgetPlan, budgetState.totalSpent)
        } else null
    }

    val dailyTarget = remember(budgetState, velocity) {
        if (budgetState != null && velocity != null) {
            BudgetEngine.calculateDailyTarget(budgetState, velocity.remainingDays)
        } else null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isWide) {
                        Modifier
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                    } else {
                        Modifier
                    }
                )
        ) {
            // App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .border(1.dp, FairShareTheme.colors.border)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    FSButton(
                        text = "← Back",
                        onClick = onBackClick,
                        variant = FSButtonVariant.Secondary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Budget & Runway",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tripName,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FSButton(
                    text = if (budgetPlan == null) "Set" else "Edit",
                    onClick = { showEditBudgetDialog = true },
                    variant = FSButtonVariant.Primary
                )
            }

            if (budgetPlan == null || budgetState == null) {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No Budget Configured",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Configure a base budget and optional emergency buffer to monitor trip spending pace and runway.",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    FSButton(
                        text = "Configure Budget",
                        onClick = { showEditBudgetDialog = true },
                        variant = FSButtonVariant.Primary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Hero Overview Card
                    item {
                        val bufferBadgeColor = when (budgetState.bufferState) {
                            BufferState.UNUSED -> FairShareTheme.colors.accent
                            BufferState.ACTIVE -> FairShareTheme.colors.info
                            BufferState.NEAR_LIMIT -> FairShareTheme.colors.negative
                            BufferState.EXHAUSTED -> FairShareTheme.colors.negative
                        }
                        val bufferBadgeBg = when (budgetState.bufferState) {
                            BufferState.UNUSED -> FairShareTheme.colors.accentSoft
                            BufferState.ACTIVE -> FairShareTheme.colors.infoSoft
                            BufferState.NEAR_LIMIT -> FairShareTheme.colors.negativeSoft
                            BufferState.EXHAUSTED -> FairShareTheme.colors.negativeSoft
                        }
                        val bufferBadgeText = when (budgetState.bufferState) {
                            BufferState.UNUSED -> "Buffer Unused"
                            BufferState.ACTIVE -> "Buffer Active"
                            BufferState.NEAR_LIMIT -> "Buffer Near Limit"
                            BufferState.EXHAUSTED -> "Buffer Exhausted"
                        }

                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surfaceElevated
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "BUDGET OVERVIEW",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                    FSStatusBadge(
                                        text = bufferBadgeText,
                                        textColor = bufferBadgeColor,
                                        backgroundColor = bufferBadgeBg
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Spent",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = budgetState.totalSpent.formatted(),
                                            style = FairShareTheme.typography.display,
                                            color = if (budgetState.isBaseBudgetExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Total Envelope",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = budgetPlan.totalEnvelope.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Native Progress bar
                                val progressFraction = (budgetState.percentageUsed / 100.0).coerceIn(0.0, 1.0).toFloat()
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (budgetState.isBaseBudgetExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                                    trackColor = FairShareTheme.colors.border
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.1f", budgetState.percentageUsed)}% used",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = "${budgetState.totalEnvelopeRemaining.formatted()} remaining",
                                        style = FairShareTheme.typography.metadata,
                                        color = if (budgetState.totalEnvelopeRemaining.isPositive) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Base vs Buffer breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Base Budget Remaining",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = budgetState.remainingBaseBudget.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = if (budgetState.remainingBaseBudget.isPositive) FairShareTheme.colors.positive else FairShareTheme.colors.negative
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Buffer Remaining",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = budgetState.remainingBuffer.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = if (budgetState.isBufferExhausted) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Spending Velocity & Daily Target
                    if (velocity != null) {
                        item {
                            FSCard(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "SPENDING PACE & RUNWAY",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = "Planned Daily Target",
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                            Text(
                                                text = velocity.plannedDailyRate.formatted() + " / day",
                                                style = FairShareTheme.typography.section,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Actual Spending Velocity",
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                            Text(
                                                text = velocity.actualDailyRate.formatted() + " / day",
                                                style = FairShareTheme.typography.section,
                                                color = if (velocity.isPacingAhead) FairShareTheme.colors.negative else FairShareTheme.colors.positive
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Days Elapsed: ${velocity.elapsedDays} of ${velocity.totalDays}",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = "Days Remaining: ${velocity.remainingDays}",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    if (dailyTarget != null && dailyTarget.isPositive) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(FairShareTheme.colors.surfaceElevated, RoundedCornerShape(6.dp))
                                                .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(6.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Recommended Daily Limit:",
                                                    style = FairShareTheme.typography.body,
                                                    color = FairShareTheme.colors.textPrimary
                                                )
                                                Text(
                                                    text = dailyTarget.formatted() + " / day",
                                                    style = FairShareTheme.typography.body,
                                                    color = FairShareTheme.colors.accent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Trip-End Projection Card
                    if (projection != null) {
                        item {
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
                                            text = "TRIP-END PROJECTION",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textTertiary
                                        )
                                        Text(
                                            text = "Projection (Estimate)",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.accent
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Projected Total: ${projection.projectedTotalSpending.formatted()}",
                                        style = FairShareTheme.typography.section,
                                        color = if (projection.willExceedEnvelope) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (projection.willExceedBaseBudget) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "At current pace, spending will exceed base budget by ${projection.projectedBaseOverrun.formatted()}.",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.negative
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "At current pace, spending will stay within the base budget envelope.",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.positive
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Category Allocations Section
                    if (budgetState.categoryStates.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Category Envelopes",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }

                        items(budgetState.categoryStates) { cs ->
                            Surface(
                                shape = FairShareTheme.shapes.card,
                                color = FairShareTheme.colors.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = cs.category.displayName,
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${cs.spent.formatted()} / ${cs.allocated.formatted()}",
                                            style = FairShareTheme.typography.metadata,
                                            color = if (cs.isExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.textSecondary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    val catFraction = (cs.percentageUsed / 100.0).coerceIn(0.0, 1.0).toFloat()
                                    LinearProgressIndicator(
                                        progress = { catFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = if (cs.isExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.accent,
                                        trackColor = FairShareTheme.colors.border
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${String.format(java.util.Locale.US, "%.0f", cs.percentageUsed)}% used",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textTertiary
                                        )
                                        Text(
                                            text = if (cs.isExceeded) "Over by ${(cs.spent - cs.allocated).formatted()}" else "${cs.remaining.formatted()} left",
                                            style = FairShareTheme.typography.metadata,
                                            color = if (cs.isExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditBudgetDialog) {
        EditBudgetDialog(
            initialPlan = budgetPlan,
            currency = currency,
            onDismiss = { showEditBudgetDialog = false },
            onSaveBudget = { newPlan ->
                showEditBudgetDialog = false
                onSaveBudget(newPlan)
            }
        )
    }
}
