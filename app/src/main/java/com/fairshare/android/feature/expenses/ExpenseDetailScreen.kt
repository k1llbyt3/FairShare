package com.fairshare.android.feature.expenses

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.domain.intelligence.AnomalyAlert
import com.fairshare.android.core.domain.intelligence.AnomalyDetectionEngine
import com.fairshare.android.core.domain.intelligence.ExpenseOwnershipEngine
import com.fairshare.android.core.domain.split.ExpenseSplitEngine
import com.fairshare.android.core.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseDetailScreen(
    expense: Expense,
    members: List<Member>,
    allExpenses: List<Expense> = emptyList(),
    comments: List<com.fairshare.android.core.domain.collaboration.ExpenseComment> = emptyList(),
    syncStatus: SyncStatus = SyncStatus.SYNCED,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onReverseExpense: (reason: String) -> Unit,
    onAddComment: ((String) -> Unit)? = null,
    onViewEvidence: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showReverseDialog by remember { mutableStateOf(false) }

    val memberMap = remember(members) { members.associateBy { it.id } }

    val calculatedSplits = remember(expense) {
        try {
            ExpenseSplitEngine.calculateSplits(expense)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    val anomalies = remember(expense, allExpenses) {
        if (allExpenses.isNotEmpty()) AnomalyDetectionEngine.detectAnomalies(expense, allExpenses)
        else emptyList()
    }

    val ownership = remember(expense, members) {
        ExpenseOwnershipEngine.computeExpenseOwnership(expense, members)
    }

    val formattedDate = remember(expense.timestamp) {
        SimpleDateFormat("EEE, dd MMM yyyy · hh:mm a", Locale.ROOT).format(Date(expense.timestamp))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FairShareTheme.colors.background)
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onBackClick() }
                ) {
                    Text(
                        text = "←",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.accent
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Expense Detail",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FSButton(
                        text = "Share",
                        onClick = {
                            val shareText = com.fairshare.android.core.domain.export.ShareEngine.formatExpenseShare(expense, members)
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Expense Breakdown"))
                        },
                        variant = FSButtonVariant.Secondary
                    )
                    FSButton(
                        text = "Edit",
                        onClick = onEditClick,
                        variant = FSButtonVariant.Secondary
                    )
                    FSButton(
                        text = "Reverse",
                        onClick = { showReverseDialog = true },
                        variant = FSButtonVariant.Secondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = if (isWide) 640.dp else 480.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Digital Ledger Card
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = expense.description.ifBlank { "Expense" }.uppercase(Locale.ROOT),
                                        style = FairShareTheme.typography.title,
                                        color = FairShareTheme.colors.textPrimary,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = formattedDate,
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }

                                val (badgeText, badgeColor, badgeBg) = when (syncStatus) {
                                    SyncStatus.SYNCED -> Triple("SYNCED", FairShareTheme.colors.positive, FairShareTheme.colors.positiveSoft)
                                    SyncStatus.PENDING_SYNC, SyncStatus.LOCAL_ONLY -> Triple("LOCAL", FairShareTheme.colors.accent, FairShareTheme.colors.accentSoft)
                                    SyncStatus.SYNCING -> Triple("SYNCING", FairShareTheme.colors.info, FairShareTheme.colors.infoSoft)
                                    SyncStatus.SYNC_FAILED -> Triple("FAILED", FairShareTheme.colors.negative, FairShareTheme.colors.negativeSoft)
                                    SyncStatus.CONFLICT -> Triple("CONFLICT", FairShareTheme.colors.negative, FairShareTheme.colors.negativeSoft)
                                }
                                FSStatusBadge(
                                    text = badgeText,
                                    textColor = badgeColor,
                                    backgroundColor = badgeBg
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = expense.totalAmount.formatted(),
                                style = FairShareTheme.typography.display,
                                color = FairShareTheme.colors.accent,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(14.dp))

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
                                    textColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.info else FairShareTheme.colors.accent,
                                    backgroundColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.infoSoft else FairShareTheme.colors.accentSoft
                                )
                                FSStatusBadge(
                                    text = expense.splitMethod.name,
                                    textColor = FairShareTheme.colors.accent,
                                    backgroundColor = FairShareTheme.colors.accentSoft
                                )
                            }
                        }
                    }
                }

                // Anomaly Banner (if detected)
                if (anomalies.isNotEmpty()) {
                    item {
                        anomalies.forEach { alert ->
                            FSCard(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = FairShareTheme.colors.surfaceElevated
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = alert.title.uppercase(),
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = alert.explanation,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }

                // Ownership & Contribution Breakdown Card
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "CONTRIBUTION VS CONSUMPTION",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ownership.memberOwnerships.forEach { mo ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(mo.memberName, style = FairShareTheme.typography.body, color = FairShareTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Paid: ${mo.paidAmount.formatted()} • Consumed: ${mo.consumedAmount.formatted()}",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                    val prefix = if (mo.netPosition.amountMinor > 0) "+" else ""
                                    Text(
                                        text = "$prefix${mo.netPosition.formatted()}",
                                        style = FairShareTheme.typography.body,
                                        color = if (mo.netPosition.amountMinor >= 0) FairShareTheme.colors.positive else FairShareTheme.colors.negative,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Paid By Breakdown Card
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "PAID BY",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            expense.payers.forEach { payer ->
                                val member = memberMap[payer.memberId]
                                val memberName = member?.name ?: "Member ${payer.memberId.take(4)}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val initials = memberName.take(2).uppercase()
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(FairShareTheme.colors.surfaceElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.accent
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = if (member?.isCurrentUser == true) "$memberName (You)" else memberName,
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                    Text(
                                        text = payer.amount.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Split Breakdown Table
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "SPLIT RESPONSIBILITY",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${calculatedSplits.size} members",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            calculatedSplits.forEach { (memberId, share) ->
                                val member = memberMap[memberId]
                                val memberName = member?.name ?: "Member ${memberId.take(4)}"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (member?.isCurrentUser == true) "$memberName (You)" else memberName,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = share.formatted(),
                                        style = FairShareTheme.typography.section,
                                        color = FairShareTheme.colors.accent
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = FairShareTheme.colors.border, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Split",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = expense.totalAmount.formatted(),
                                    style = FairShareTheme.typography.section,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // If Itemized, show item breakdown
                if (expense.splitMethod == SplitMethod.ITEMIZED && expense.items.isNotEmpty()) {
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surface
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ITEMIZED BREAKDOWN",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                expense.items.forEach { item ->
                                    val itemParticipants = item.participantMemberIds.mapNotNull { memberMap[it]?.name }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.name,
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = item.amount.formatted(),
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                        }
                                        Text(
                                            text = itemParticipants.joinToString(", "),
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textTertiary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Notes Card
                if (!expense.notes.isNullOrBlank()) {
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surface
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "NOTES",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = expense.notes!!,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Evidence & Receipts Card
                item {
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "EVIDENCE & ATTACHMENTS",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            if (expense.receiptAttachmentId != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Receipt attached",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                    FSButton(
                                        text = "View Evidence",
                                        onClick = { onViewEvidence?.invoke(expense.receiptAttachmentId) },
                                        variant = FSButtonVariant.Secondary
                                    )
                                }
                            } else {
                                Text(
                                    text = "No receipt or invoice attached to this expense.",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Discussion & Clarifications Card
                item {
                    var newCommentText by remember { mutableStateOf("") }
                    FSCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = FairShareTheme.colors.surface
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DISCUSSION & CLARIFICATIONS",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textTertiary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            if (comments.isEmpty()) {
                                Text(
                                    text = "No comments yet. Add context or dispute an item.",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                            } else {
                                comments.forEach { c ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(FairShareTheme.colors.surfaceElevated, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = c.authorName,
                                                style = FairShareTheme.typography.metadata,
                                                fontWeight = FontWeight.Bold,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                            if (c.disputeTag != null) {
                                                Text(
                                                    text = c.disputeTag,
                                                    style = FairShareTheme.typography.metadata,
                                                    color = FairShareTheme.colors.accent,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = c.text,
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.OutlinedTextField(
                                    value = newCommentText,
                                    onValueChange = { newCommentText = it },
                                    placeholder = { Text("Add clarification...", style = FairShareTheme.typography.metadata) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = FairShareTheme.colors.accent,
                                        unfocusedBorderColor = FairShareTheme.colors.border,
                                        focusedTextColor = FairShareTheme.colors.textPrimary,
                                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                FSButton(
                                    text = "Post",
                                    onClick = {
                                        val txt = newCommentText.trim()
                                        if (txt.isNotBlank()) {
                                            onAddComment?.invoke(txt)
                                            newCommentText = ""
                                        }
                                    },
                                    enabled = newCommentText.isNotBlank()
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showReverseDialog) {
        ReverseExpenseDialog(
            expense = expense,
            onConfirmReversal = { reason ->
                onReverseExpense(reason)
                onBackClick()
            },
            onDismiss = { showReverseDialog = false }
        )
    }
}
