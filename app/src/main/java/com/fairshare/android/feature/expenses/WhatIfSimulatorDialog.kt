package com.fairshare.android.feature.expenses

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.simulation.SimulationEngine
import java.util.UUID

@Composable
fun WhatIfSimulatorDialog(
    members: List<Member>,
    currentExpenses: List<Expense>,
    currentPayments: List<SettlementPayment>,
    budgetPlan: BudgetPlan?,
    currency: Currency = Currency.INR,
    onDismiss: () -> Unit
) {
    var amountInput by remember { mutableStateOf("5000") }
    var descriptionInput by remember { mutableStateOf("Hotel booking") }
    var selectedPayerId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }

    val amountMinor = ((amountInput.toDoubleOrNull() ?: 0.0) * 100).toLong()
    val candidateMoney = Money(amountMinor, currency)

    val candidateExpense = remember(amountMinor, descriptionInput, selectedPayerId) {
        Expense(
            id = UUID.randomUUID().toString(),
            description = descriptionInput.ifBlank { "Simulated Expense" },
            totalAmount = candidateMoney,
            payers = listOf(ExpensePayer(memberId = selectedPayerId, amount = candidateMoney)),
            participants = members.map { ExpenseParticipant(memberId = it.id) },
            splitMethod = SplitMethod.EQUAL,
            mode = PaymentMode.ONLINE,
            category = ExpenseCategory.ACCOMMODATION,
            timestamp = System.currentTimeMillis()
        )
    }

    val outcome = remember(candidateExpense) {
        SimulationEngine.simulateWhatIfExpense(
            members = members,
            currentExpenses = currentExpenses,
            currentPayments = currentPayments,
            candidateExpense = candidateExpense,
            budgetPlan = budgetPlan,
            currency = currency
        )
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WHAT-IF SIMULATOR",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(FairShareTheme.colors.surface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NO MUTATION",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Projected Financial Impact",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Inputs
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Simulated Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = descriptionInput,
                    onValueChange = { descriptionInput = it },
                    label = { Text("Simulated Description") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Budget impact if plan exists
                outcome.budgetResult?.let { b ->
                    FSCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "BUDGET IMPACT",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base Remaining", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(
                                "${b.previousRemainingBase?.formatted()} → ${b.simulatedRemainingBase?.formatted()}",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Buffer Remaining", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                            Text(
                                "${b.previousRemainingBuffer?.formatted()} → ${b.simulatedRemainingBuffer?.formatted()}",
                                style = FairShareTheme.typography.body,
                                color = if (b.bufferExceeded) FairShareTheme.colors.negative else FairShareTheme.colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        b.simulatedDailyRunway?.let { runway ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("New Daily Allowance", style = FairShareTheme.typography.supporting, color = FairShareTheme.colors.textSecondary)
                                Text("${runway.formatted()} / day", style = FairShareTheme.typography.body, color = FairShareTheme.colors.accent)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Balance deltas
                FSCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "PROJECTED MEMBER BALANCES",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    members.forEach { m ->
                        val delta = outcome.balanceResult.balanceDeltas[m.id] ?: Money.zero(currency)
                        val simBal = outcome.balanceResult.simulatedBalances.find { it.memberId == m.id }?.netBalance ?: Money.zero(currency)
                        val deltaPrefix = if (delta.amountMinor > 0) "+" else ""

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(m.name, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(simBal.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                                Text(
                                    "$deltaPrefix${delta.formatted()}",
                                    style = FairShareTheme.typography.metadata,
                                    color = if (delta.amountMinor >= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                FSButton(
                    text = "Close Simulation",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    variant = FSButtonVariant.Primary
                )
            }
        }
    }
}
