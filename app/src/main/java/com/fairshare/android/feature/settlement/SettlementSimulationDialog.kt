package com.fairshare.android.feature.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.simulation.SimulationEngine
import java.util.UUID

@Composable
fun SettlementSimulationDialog(
    members: List<Member>,
    expenses: List<com.fairshare.android.core.domain.model.Expense>,
    payments: List<SettlementPayment>,
    initialFromMemberId: String? = null,
    initialToMemberId: String? = null,
    initialAmount: Money? = null,
    onDismiss: () -> Unit,
    onApplyAsRealPayment: (fromMemberId: String, toMemberId: String, amount: Money) -> Unit
) {
    val currency = initialAmount?.currency ?: Currency.INR

    var fromMemberId by remember {
        mutableStateOf(initialFromMemberId ?: members.firstOrNull()?.id ?: "")
    }
    var toMemberId by remember {
        mutableStateOf(initialToMemberId ?: members.getOrNull(1)?.id ?: members.firstOrNull()?.id ?: "")
    }

    val initialAmountText = initialAmount?.let {
        String.format(java.util.Locale.US, "%.2f", it.amountMinor / 100.0)
    } ?: ""

    var amountText by remember { mutableStateOf(initialAmountText) }
    var expandedFromMenu by remember { mutableStateOf(false) }
    var expandedToMenu by remember { mutableStateOf(false) }

    val fromMember = members.firstOrNull { it.id == fromMemberId }
    val toMember = members.firstOrNull { it.id == toMemberId }

    val parsedRupees = amountText.toDoubleOrNull() ?: 0.0
    val parsedMinor = (parsedRupees * 100).toLong()
    val candidateMoney = Money(parsedMinor, currency)

    // Compute simulation purely in memory — zero persistence or side-effects
    val simulationResult = remember(fromMemberId, toMemberId, parsedMinor, members, expenses, payments) {
        if (parsedMinor > 0L && fromMemberId.isNotBlank() && toMemberId.isNotBlank() && fromMemberId != toMemberId) {
            val candidatePayment = SettlementPayment(
                id = "sim-${UUID.randomUUID()}",
                fromMemberId = fromMemberId,
                toMemberId = toMemberId,
                amount = candidateMoney,
                groupId = "simulation"
            )
            SimulationEngine.simulatePayment(
                members = members,
                currentExpenses = expenses,
                currentPayments = payments,
                candidatePayment = candidatePayment,
                currency = currency
            )
        } else {
            null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header & Simulation Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "What-If Simulation",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    FSStatusBadge(
                        text = "Hypothetical",
                        textColor = FairShareTheme.colors.info,
                        backgroundColor = FairShareTheme.colors.infoSoft
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Isolation safeguard notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                        .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(6.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "SIMULATION ONLY: Changes are isolated in memory and will never write to Room, sync to the server, or alter real group ledger history unless explicitly applied.",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Payer (From)
                Text(
                    text = "Hypothetical Payer",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .clickable { expandedFromMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = fromMember?.name ?: "Select payer",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "▼",
                            fontSize = 10.sp,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedFromMenu,
                        onDismissRequest = { expandedFromMenu = false },
                        modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m.name + if (m.isCurrentUser) " (You)" else "",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    fromMemberId = m.id
                                    expandedFromMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recipient (To)
                Text(
                    text = "Hypothetical Recipient",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, FairShareTheme.colors.border, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(FairShareTheme.colors.surface)
                            .clickable { expandedToMenu = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = toMember?.name ?: "Select recipient",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "▼",
                            fontSize = 10.sp,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedToMenu,
                        onDismissRequest = { expandedToMenu = false },
                        modifier = Modifier.background(FairShareTheme.colors.surfaceElevated)
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m.name + if (m.isCurrentUser) " (You)" else "",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                },
                                onClick = {
                                    toMemberId = m.id
                                    expandedToMenu = false
                                }
                            )
                        }
                    }
                }

                if (fromMemberId == toMemberId) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Payer and recipient cannot be the same member",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.negative
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                Text(
                    text = "Hypothetical Payment Amount (₹)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) {
                            amountText = input
                        }
                    },
                    placeholder = { Text("0.00", color = FairShareTheme.colors.disabled) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Simulation Results Display
                if (simulationResult != null) {
                    Text(
                        text = "Simulation Outcome",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Transfer count impact
                    val prevCount = simulationResult.previousPlan.transfers.size
                    val newCount = simulationResult.simulatedPlan.transfers.size
                    val countDelta = simulationResult.transferCountDelta

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Remaining Transfers:",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Text(
                            text = "$prevCount → $newCount (${if (countDelta <= 0) "$countDelta" else "+$countDelta"})",
                            style = FairShareTheme.typography.body,
                            color = if (countDelta <= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Before vs After Member Balances
                    Text(
                        text = "Projected Balance Shifts",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    simulationResult.simulatedBalances.forEach { sb ->
                        val prevBal = simulationResult.previousBalances.firstOrNull { it.memberId == sb.memberId }?.netBalance
                            ?: Money.zero(currency)
                        val delta = simulationResult.balanceDeltas[sb.memberId] ?: Money.zero(currency)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = sb.memberName,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Was ${prevBal.formatted()}",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = sb.netBalance.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = if (sb.netBalance.amountMinor >= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                                    fontWeight = FontWeight.Bold
                                )
                                if (delta.amountMinor != 0L) {
                                    val sign = if (delta.amountMinor > 0) "+" else ""
                                    Text(
                                        text = "$sign${delta.formatted()}",
                                        style = FairShareTheme.typography.metadata,
                                        color = if (delta.amountMinor > 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Projected Transfers
                    Text(
                        text = "Projected Suggested Transfers",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (simulationResult.simulatedPlan.transfers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Everyone would be fully settled with this payment.",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.positive
                            )
                        }
                    } else {
                        simulationResult.simulatedPlan.transfers.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(FairShareTheme.colors.surface, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${t.fromMemberName} → ${t.toMemberName}",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = t.amount.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Exit",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Apply as Real",
                        onClick = {
                            if (parsedMinor > 0L && fromMemberId != toMemberId) {
                                onDismiss()
                                onApplyAsRealPayment(fromMemberId, toMemberId, candidateMoney)
                            }
                        },
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = parsedMinor > 0L && fromMemberId.isNotBlank() && toMemberId.isNotBlank() && fromMemberId != toMemberId
                    )
                }
            }
        }
    }
}
