package com.fairshare.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.FinancialSummary
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Payment
import com.fairshare.android.core.model.PaymentMode

@Composable
fun HomeScreen(
    currentMember: Member,
    tripName: String,
    summary: FinancialSummary,
    expenses: List<Expense>,
    payments: List<Payment>,
    onAddExpenseClick: () -> Unit,
    onRecordPaymentClick: () -> Unit,
    onViewSettlementClick: () -> Unit,
    onManageMembersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val myBalance = summary.balances.find { it.memberId == currentMember.id }?.netBalance
    val isOwed = (myBalance?.paise ?: 0L) > 0L
    val isDebtor = (myBalance?.paise ?: 0L) < 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // Top App Bar / Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FAIRSHARE",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.accent
                )
                Text(
                    text = tripName,
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
            }
            FSButton(
                text = "Members",
                onClick = onManageMembersClick,
                variant = FSButtonVariant.Secondary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Your Current Financial Position Card
            item {
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "YOUR POSITION",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        val positionText = when {
                            isOwed -> "You are owed"
                            isDebtor -> "You owe"
                            else -> "You are settled up"
                        }
                        val positionColor = when {
                            isOwed -> FairShareTheme.colors.positive
                            isDebtor -> FairShareTheme.colors.negative
                            else -> FairShareTheme.colors.textSecondary
                        }

                        Text(
                            text = positionText,
                            style = FairShareTheme.typography.supporting,
                            color = positionColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = myBalance?.formatted() ?: "₹0",
                            style = FairShareTheme.typography.display,
                            color = positionColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Total Group Spend",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Text(
                                    text = summary.totalSpend.formatted(),
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Your Fair Share",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Text(
                                    text = summary.perPersonShare.formatted(),
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Primary Mobile Action Buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "+ Add Expense",
                        onClick = onAddExpenseClick,
                        modifier = Modifier.weight(1f),
                        variant = FSButtonVariant.Primary
                    )
                    FSButton(
                        text = "Pay Member",
                        onClick = onRecordPaymentClick,
                        modifier = Modifier.weight(1f),
                        variant = FSButtonVariant.Secondary
                    )
                }
            }

            // Settlement Banner / Quick Access
            item {
                FSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewSettlementClick() },
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Settlement Plan",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                            Text(
                                text = if (summary.settlements.isEmpty()) "All members are currently settled"
                                else "${summary.settlements.size} pending transfers needed",
                                style = FairShareTheme.typography.supporting,
                                color = if (summary.settlements.isEmpty()) FairShareTheme.colors.positive
                                else FairShareTheme.colors.accent
                            )
                        }
                        FSButton(
                            text = "View",
                            onClick = onViewSettlementClick,
                            variant = FSButtonVariant.Secondary
                        )
                    }
                }
            }

            // Recent Expenses Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Expenses",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = "${expenses.size} total",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textTertiary
                    )
                }
            }

            if (expenses.isEmpty()) {
                item {
                    FSCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No expenses recorded yet. Tap '+ Add Expense' above to start.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                items(expenses.take(5)) { expense ->
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = expense.description.ifEmpty { "Expense" },
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FSStatusBadge(
                                        text = expense.mode.name,
                                        textColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.info else FairShareTheme.colors.accent,
                                        backgroundColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.infoSoft else FairShareTheme.colors.accentSoft
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${expense.involvedMemberIds.size} people involved",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                }
                            }
                            Text(
                                text = expense.amount.formatted(),
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
