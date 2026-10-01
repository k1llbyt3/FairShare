package com.fairshare.android.feature.settlement

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine
import com.fairshare.android.core.domain.settlement.SettlementTransfer
import com.fairshare.android.feature.expenses.RecordPaymentDialog

@Composable
fun SettlementScreen(
    tripName: String,
    members: List<Member>,
    expenses: List<Expense>,
    payments: List<SettlementPayment>,
    groupId: String = "",
    currentUserMemberId: String? = null,
    onBackClick: () -> Unit,
    onSavePayment: suspend (SettlementPayment) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currency = expenses.firstOrNull()?.amount?.currency ?: Currency.INR

    // Recalculate deterministic balances and suggested transfers from canonical ledger
    val memberBalances = remember(members, expenses, payments) {
        BalanceEngine.calculateGroupBalances(members, expenses, payments, emptyList(), currency)
    }

    val settlementPlan = remember(memberBalances) {
        SettlementEngine.calculateSettlement(memberBalances, currency)
    }

    // Determine current user
    val effectiveCurrentUserMemberId = currentUserMemberId
        ?: members.firstOrNull { it.isCurrentUser }?.id
        ?: members.firstOrNull()?.id
        ?: ""

    val userBalance = memberBalances.firstOrNull { it.memberId == effectiveCurrentUserMemberId }

    // Dialog States
    var selectedTransferForDetail by remember { mutableStateOf<SettlementTransfer?>(null) }
    var showRecordPaymentDialog by remember { mutableStateOf(false) }
    var showSimulationDialog by remember { mutableStateOf(false) }

    // Preselected values for recording / simulating payment
    var paymentPreselectedFrom by remember { mutableStateOf<String?>(null) }
    var paymentPreselectedTo by remember { mutableStateOf<String?>(null) }
    var paymentPreselectedAmount by remember { mutableStateOf<Money?>(null) }

    // Formatted plain-text settlement for Share and Copy
    val shareableSettlementText = remember(tripName, settlementPlan, memberBalances) {
        buildString {
            appendLine("=== $tripName Settlement Plan ===")
            appendLine("Status: ${settlementPlan.algorithmLabel}")
            appendLine("Total Outstanding Transfers: ${settlementPlan.totalTransferred.formatted()}")
            appendLine()
            if (settlementPlan.isSettled || settlementPlan.transfers.isEmpty()) {
                appendLine("Everyone is settled.")
            } else {
                appendLine("Suggested Transfers:")
                settlementPlan.transfers.forEach { t ->
                    appendLine("• ${t.fromMemberName} pays ${t.toMemberName}: ${t.amount.formatted()}")
                }
            }
            appendLine()
            appendLine("Member Balances:")
            memberBalances.forEach { b ->
                val net = b.netBalance
                val sign = if (net.amountMinor > 0) "+" else ""
                appendLine("• ${b.memberName}: $sign${net.formatted()} (Paid: ${b.paid.formatted()}, Consumed: ${b.consumed.formatted()})")
            }
        }
    }

    BackHandler {
        when {
            showSimulationDialog -> showSimulationDialog = false
            showRecordPaymentDialog -> showRecordPaymentDialog = false
            selectedTransferForDetail != null -> selectedTransferForDetail = null
            else -> onBackClick()
        }
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
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .border(1.dp, FairShareTheme.colors.border)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FSButton(
                    text = "← Back",
                    onClick = onBackClick,
                    variant = FSButtonVariant.Secondary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Settlement",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = tripName,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FSButton(
                        text = "Copy",
                        onClick = {
                            clipboardManager.setText(AnnotatedString(shareableSettlementText))
                        },
                        variant = FSButtonVariant.Secondary
                    )
                    FSButton(
                        text = "Share",
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareableSettlementText)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Settlement Plan")
                            context.startActivity(shareIntent)
                        },
                        variant = FSButtonVariant.Primary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. User's Own Position Card (Hero card at top, user-first)
                if (userBalance != null) {
                    item {
                        val netMinor = userBalance.netBalance.amountMinor
                        val isPositive = netMinor > 0L
                        val isNegative = netMinor < 0L
                        val isNeutral = netMinor == 0L

                        val positionTitle = when {
                            isPositive -> "You are owed ${userBalance.netBalance.formatted()}"
                            isNegative -> "You owe ${Money(-netMinor, currency).formatted()}"
                            else -> "You are all settled up"
                        }

                        val positionColor = when {
                            isPositive -> FairShareTheme.colors.positive
                            isNegative -> FairShareTheme.colors.negative
                            else -> FairShareTheme.colors.accent
                        }

                        val positionBg = when {
                            isPositive -> FairShareTheme.colors.positiveSoft
                            isNegative -> FairShareTheme.colors.negativeSoft
                            else -> FairShareTheme.colors.accentSoft
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
                                        text = "YOUR BALANCE POSITION",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                    FSStatusBadge(
                                        text = if (isPositive) "Credit" else if (isNegative) "Debt" else "Settled",
                                        textColor = positionColor,
                                        backgroundColor = positionBg
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = positionTitle,
                                    style = FairShareTheme.typography.display,
                                    color = positionColor,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Total Paid by You",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = userBalance.paid.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Your Consumed Share",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = userBalance.consumed.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Group Settlement Overview Card
                item {
                    val totalGroupSpend = remember(expenses) {
                        Money(expenses.sumOf { it.amount.amountMinor }, currency)
                    }

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
                                    text = "GROUP SETTLEMENT OVERVIEW",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Text(
                                    text = settlementPlan.algorithmLabel,
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Total Group Spending",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = totalGroupSpend.formatted(),
                                        style = FairShareTheme.typography.title,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Outstanding Transfers",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = settlementPlan.totalTransferred.formatted(),
                                        style = FairShareTheme.typography.title,
                                        color = if (settlementPlan.totalTransferred.amountMinor > 0L) FairShareTheme.colors.accent else FairShareTheme.colors.positive
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Action Buttons: Record Payment & What-If Simulation
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FSButton(
                            text = "Record Payment",
                            onClick = {
                                paymentPreselectedFrom = null
                                paymentPreselectedTo = null
                                paymentPreselectedAmount = null
                                showRecordPaymentDialog = true
                            },
                            variant = FSButtonVariant.Primary,
                            modifier = Modifier.weight(1f)
                        )
                        FSButton(
                            text = "What-If Simulation",
                            onClick = {
                                paymentPreselectedFrom = null
                                paymentPreselectedTo = null
                                paymentPreselectedAmount = null
                                showSimulationDialog = true
                            },
                            variant = FSButtonVariant.Secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 4. Suggested Transfers Header
                item {
                    Text(
                        text = "Suggested Transfers (${settlementPlan.transfers.size})",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                }

                // 5. Suggested Transfers List or "Everyone is settled." Empty State
                if (settlementPlan.isSettled || settlementPlan.transfers.isEmpty()) {
                    item {
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Everyone is settled.",
                                    style = FairShareTheme.typography.title,
                                    color = FairShareTheme.colors.positive,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "All member net obligations are balanced. No further transfers are required.",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(settlementPlan.transfers) { transfer ->
                        val isUserDebtor = transfer.fromMemberId == effectiveCurrentUserMemberId
                        val isUserCreditor = transfer.toMemberId == effectiveCurrentUserMemberId
                        val isUserInvolved = isUserDebtor || isUserCreditor

                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = if (isUserInvolved) FairShareTheme.colors.surfaceElevated else FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isUserInvolved) 1.5.dp else 1.dp,
                                    color = if (isUserInvolved) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = FairShareTheme.shapes.card
                                )
                                .clip(FairShareTheme.shapes.card)
                                .clickable { selectedTransferForDetail = transfer }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = transfer.fromMemberName,
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.negative
                                        )
                                        Text(
                                            text = "pays",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = transfer.toMemberName,
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.positive
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Tap to inspect audit trace",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textTertiary
                                        )
                                        if (isUserDebtor) {
                                            FSStatusBadge(
                                                text = "You owe",
                                                textColor = FairShareTheme.colors.negative,
                                                backgroundColor = FairShareTheme.colors.negativeSoft
                                            )
                                        } else if (isUserCreditor) {
                                            FSStatusBadge(
                                                text = "You receive",
                                                textColor = FairShareTheme.colors.positive,
                                                backgroundColor = FairShareTheme.colors.positiveSoft
                                            )
                                        }
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = transfer.amount.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    FSButton(
                                        text = if (isUserDebtor) "Pay" else "Record",
                                        onClick = {
                                            paymentPreselectedFrom = transfer.fromMemberId
                                            paymentPreselectedTo = transfer.toMemberId
                                            paymentPreselectedAmount = transfer.amount
                                            showRecordPaymentDialog = true
                                        },
                                        variant = if (isUserDebtor) FSButtonVariant.Primary else FSButtonVariant.Secondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. Detailed Member Balance Breakdown
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Member Net Positions",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                }

                items(memberBalances) { b ->
                    val isCurrent = b.memberId == effectiveCurrentUserMemberId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FairShareTheme.colors.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = b.memberName + if (isCurrent) " (You)" else "",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = "Paid: ${b.paid.formatted()} • Share: ${b.consumed.formatted()}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                        }
                        Text(
                            text = (if (b.netBalance.amountMinor > 0) "+" else "") + b.netBalance.formatted(),
                            style = FairShareTheme.typography.body,
                            color = if (b.netBalance.amountMinor >= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Modal: Settlement Explanation (TransferDetailDialog)
    val activeTransfer = selectedTransferForDetail
    if (activeTransfer != null) {
        TransferDetailDialog(
            transfer = activeTransfer,
            members = members,
            expenses = expenses,
            payments = payments,
            onDismiss = { selectedTransferForDetail = null },
            onRecordPaymentClick = { fromId, toId, amt ->
                paymentPreselectedFrom = fromId
                paymentPreselectedTo = toId
                paymentPreselectedAmount = amt
                showRecordPaymentDialog = true
            },
            onSimulatePaymentClick = { fromId, toId, amt ->
                paymentPreselectedFrom = fromId
                paymentPreselectedTo = toId
                paymentPreselectedAmount = amt
                showSimulationDialog = true
            }
        )
    }

    // Modal: Record Payment Dialog
    if (showRecordPaymentDialog) {
        RecordPaymentDialog(
            members = members,
            groupId = groupId,
            initialFromMemberId = paymentPreselectedFrom,
            initialToMemberId = paymentPreselectedTo,
            suggestedAmount = paymentPreselectedAmount,
            onDismiss = { showRecordPaymentDialog = false },
            onSavePayment = { newPayment ->
                onSavePayment(newPayment)
            }
        )
    }

    // Modal: What-If Simulation Dialog
    if (showSimulationDialog) {
        SettlementSimulationDialog(
            members = members,
            expenses = expenses,
            payments = payments,
            initialFromMemberId = paymentPreselectedFrom,
            initialToMemberId = paymentPreselectedTo,
            initialAmount = paymentPreselectedAmount,
            onDismiss = { showSimulationDialog = false },
            onApplyAsRealPayment = { fromId, toId, amt ->
                paymentPreselectedFrom = fromId
                paymentPreselectedTo = toId
                paymentPreselectedAmount = amt
                showRecordPaymentDialog = true
            }
        )
    }
}
