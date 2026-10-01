package com.fairshare.android.feature.expenses

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.intelligence.ExpenseBundlingEngine
import com.fairshare.android.core.domain.model.Expense

@Composable
fun ExpenseBundlesDialog(
    expenses: List<Expense>,
    onExpenseClick: (Expense) -> Unit,
    onDismiss: () -> Unit
) {
    val suggestedBundles = remember(expenses) {
        ExpenseBundlingEngine.suggestBundles(expenses)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = FairShareTheme.shapes.dialog,
            color = FairShareTheme.colors.surfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.dialog)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "EXPENSE BUNDLES",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.accent,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Grouped Activities",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bundles provide an organizational view over real expenses. Underlying records remain immutable.",
                    style = FairShareTheme.typography.supporting,
                    color = FairShareTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (suggestedBundles.isEmpty()) {
                    Text(
                        text = "No grouped activities detected yet. Expenses that happen close together in time will appear here as suggested bundles.",
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(suggestedBundles) { bundle ->
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
                                        Text(
                                            text = bundle.title.uppercase(),
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.textPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${bundle.expenseCount} related expenses",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                    Text(
                                        text = bundle.totalAmount.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                // List underlying expenses
                                val included = expenses.filter { bundle.expenseIds.contains(it.id) }
                                included.forEach { exp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onExpenseClick(exp) }
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "• ${exp.description}",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = exp.totalAmount.formatted(),
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                FSButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    variant = FSButtonVariant.Primary
                )
            }
        }
    }
}
