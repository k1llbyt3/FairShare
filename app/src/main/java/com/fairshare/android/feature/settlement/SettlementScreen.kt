package com.fairshare.android.feature.settlement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.model.FinancialSummary

@Composable
fun SettlementScreen(
    tripName: String,
    summary: FinancialSummary,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    val settlementText = buildString {
        appendLine("=====================================")
        appendLine("             ${tripName.uppercase()}")
        appendLine("=====================================")
        appendLine("Total Spent: ${summary.totalSpend.formatted()}")
        appendLine("Per Person: ${summary.perPersonShare.formatted()}")
        appendLine("=====================================")
        if (summary.settlements.isEmpty()) {
            appendLine("Everyone is settled up!")
        } else {
            appendLine("Who Pays Whom:")
            for (s in summary.settlements) {
                appendLine("• ${s.fromMemberName} pays ${s.toMemberName} ${s.amount.formatted()}")
            }
        }
        appendLine("=====================================")
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
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FSButton(
                text = "← Back",
                onClick = onBackClick,
                variant = FSButtonVariant.Secondary
            )
            Text(
                text = "Settlement Plan",
                style = FairShareTheme.typography.title,
                color = FairShareTheme.colors.textPrimary
            )
            FSButton(
                text = "Copy",
                onClick = {
                    clipboardManager.setText(AnnotatedString(settlementText))
                },
                variant = FSButtonVariant.Primary
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            item {
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surfaceElevated
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SETTLEMENT SUMMARY",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Expenses",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = summary.totalSpend.formatted(),
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Fair Share Per Person",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
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

            // Who Pays Whom Transfers
            item {
                Text(
                    text = "Recommended Transfers",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
            }

            if (summary.settlements.isEmpty()) {
                item {
                    FSCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "🎉 Everyone is already settled up! No transfers needed.",
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.positive,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                items(summary.settlements) { transfer ->
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
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = transfer.fromMemberName,
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.negative
                                    )
                                    Text(
                                        text = " pays ",
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
                                Text(
                                    text = "Direct settlement transfer",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                            }
                            Text(
                                text = transfer.amount.formatted(),
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.accent
                            )
                        }
                    }
                }
            }

            // Member Balances Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Individual Net Balances",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
            }

            items(summary.balances) { balance ->
                val isPositive = balance.netBalance.paise > 0
                val isNegative = balance.netBalance.paise < 0

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
                        Text(
                            text = balance.memberName,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FSStatusBadge(
                                text = when {
                                    isPositive -> "Gets Back"
                                    isNegative -> "Owes"
                                    else -> "Settled"
                                },
                                textColor = when {
                                    isPositive -> FairShareTheme.colors.positive
                                    isNegative -> FairShareTheme.colors.negative
                                    else -> FairShareTheme.colors.textSecondary
                                },
                                backgroundColor = when {
                                    isPositive -> FairShareTheme.colors.positiveSoft
                                    isNegative -> FairShareTheme.colors.negativeSoft
                                    else -> FairShareTheme.colors.surfaceElevated
                                }
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(
                                text = balance.netBalance.formatted(),
                                style = FairShareTheme.typography.section,
                                color = when {
                                    isPositive -> FairShareTheme.colors.positive
                                    isNegative -> FairShareTheme.colors.negative
                                    else -> FairShareTheme.colors.textSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
