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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.split.ExpenseSplitEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseReviewDialog(
    expense: Expense,
    members: List<Member>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val memberMap = remember(members) { members.associateBy { it.id } }

    val calculatedSplits = remember(expense) {
        try {
            ExpenseSplitEngine.calculateSplits(expense)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val formattedDate = remember(expense.timestamp) {
        SimpleDateFormat("EEE, dd MMM yyyy · hh:mm a", Locale.ROOT).format(Date(expense.timestamp))
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
                Text(
                    text = "Review Expense",
                    style = FairShareTheme.typography.title,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Primary card
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = expense.description.ifBlank { "Expense" },
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = expense.totalAmount.formatted(),
                            style = FairShareTheme.typography.display,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FSStatusBadge(
                                text = expense.category.name,
                                textColor = FairShareTheme.colors.textSecondary,
                                backgroundColor = FairShareTheme.colors.surfaceElevated
                            )
                            FSStatusBadge(
                                text = expense.mode.name,
                                textColor = FairShareTheme.colors.info,
                                backgroundColor = FairShareTheme.colors.infoSoft
                            )
                            FSStatusBadge(
                                text = expense.splitMethod.name,
                                textColor = FairShareTheme.colors.accent,
                                backgroundColor = FairShareTheme.colors.accentSoft
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formattedDate,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textTertiary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Payer Breakdown
                Text(
                    text = "Paid By",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                expense.payers.forEach { payer ->
                    val memberName = memberMap[payer.memberId]?.name ?: "Member ${payer.memberId.take(4)}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = memberName,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = payer.amount.formatted(),
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Split Breakdown
                Text(
                    text = "Split Breakdown (${expense.splitMethod.name})",
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                calculatedSplits.forEach { (memberId, share) ->
                    val memberName = memberMap[memberId]?.name ?: "Member ${memberId.take(4)}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = memberName,
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.textPrimary
                        )
                        Text(
                            text = share.formatted(),
                            style = FairShareTheme.typography.body,
                            color = FairShareTheme.colors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (!expense.notes.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Notes",
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = expense.notes!!,
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Edit",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    FSButton(
                        text = "Confirm & Save",
                        onClick = onConfirm,
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
