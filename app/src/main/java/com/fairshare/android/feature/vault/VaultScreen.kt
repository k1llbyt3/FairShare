package com.fairshare.android.feature.vault

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.vault.FairShareVaultEngine
import com.fairshare.android.core.domain.vault.VaultContribution
import com.fairshare.android.core.domain.vault.VaultLedgerState
import java.util.UUID

@Composable
fun VaultScreen(
    groupName: String,
    groupId: String,
    members: List<Member>,
    expenses: List<Expense>,
    currency: Currency = Currency.INR,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contributions = remember { mutableStateListOf<VaultContribution>() }
    var showAddContributionDialog by remember { mutableStateOf(false) }

    val vaultState = remember(contributions.toList(), expenses) {
        FairShareVaultEngine.calculateVaultState(
            groupId = groupId,
            fundName = "$groupName Fund",
            members = members,
            contributions = contributions,
            poolExpenses = expenses,
            currency = currency
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FairShareTheme.colors.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹ Back",
                style = FairShareTheme.typography.body,
                color = FairShareTheme.colors.accent,
                modifier = Modifier.clickable { onBackClick() }
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "FairShare Vault",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Legal & Scope Disclaimer Card
            item {
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surfaceElevated
                ) {
                    Text(
                        text = "VIRTUAL ACCOUNTING LEDGER",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = VaultLedgerState.DISCLAIMER,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Vault Headline Card
            item {
                FSCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = vaultState.fundName.uppercase(),
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = vaultState.remainingPoolBalance.formatted(),
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary,
                        fontSize = 32.sp
                    )
                    Text(
                        text = "remaining virtual pool balance",
                        style = FairShareTheme.typography.supporting,
                        color = FairShareTheme.colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Total Contributed", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                            Text(vaultState.totalContributed.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Spent from Pool", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                            Text(vaultState.totalSpentFromPool.formatted(), style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Member Shares
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEMBER CONTRIBUTIONS & SHARES",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.accent,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "+ Record Contribution",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.accent,
                        modifier = Modifier.clickable { showAddContributionDialog = true }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(vaultState.memberShares) { share ->
                FSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(share.memberName, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                "Contributed: ${share.totalContributed.formatted()} • Spent: ${share.allocatedSpent.formatted()}",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                        }
                        Text(
                            share.remainingShare.formatted(),
                            style = FairShareTheme.typography.body,
                            color = if (share.remainingShare.amountMinor >= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showAddContributionDialog) {
        var amountText by remember { mutableStateOf("") }
        var selectedMemberId by remember { mutableStateOf(members.firstOrNull()?.id ?: "") }
        var noteText by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddContributionDialog = false }) {
            Surface(
                shape = FairShareTheme.shapes.dialog,
                color = FairShareTheme.colors.surfaceElevated,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Record Pool Contribution",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Contribution Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FairShareTheme.colors.accent,
                            unfocusedBorderColor = FairShareTheme.colors.border,
                            focusedTextColor = FairShareTheme.colors.textPrimary,
                            unfocusedTextColor = FairShareTheme.colors.textPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Member", style = FairShareTheme.typography.metadata, color = FairShareTheme.colors.textSecondary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        members.forEach { m ->
                            val isSelected = m.id == selectedMemberId
                            Box(
                                modifier = Modifier
                                    .background(if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.surface)
                                    .clickable { selectedMemberId = m.id }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    m.name,
                                    style = FairShareTheme.typography.metadata,
                                    color = if (isSelected) FairShareTheme.colors.surface else FairShareTheme.colors.textPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FSButton(
                            text = "Cancel",
                            onClick = { showAddContributionDialog = false },
                            variant = FSButtonVariant.Secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FSButton(
                            text = "Record",
                            onClick = {
                                val amt = (amountText.toDoubleOrNull() ?: 0.0) * 100
                                if (amt > 0) {
                                    contributions.add(
                                        VaultContribution(
                                            id = UUID.randomUUID().toString(),
                                            memberId = selectedMemberId,
                                            amount = Money(amt.toLong(), currency),
                                            note = noteText.ifBlank { null }
                                        )
                                    )
                                    showAddContributionDialog = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
