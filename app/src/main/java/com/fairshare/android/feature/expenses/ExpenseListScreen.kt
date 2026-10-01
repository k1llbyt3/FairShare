package com.fairshare.android.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun getGroupHeaderForTimestamp(timestamp: Long): String {
    val expenseCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowCal = Calendar.getInstance()

    val isSameYear = expenseCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
    val isSameDay = isSameYear && expenseCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

    nowCal.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = isSameYear && expenseCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameDay -> "TODAY"
        isYesterday -> "YESTERDAY"
        isSameYear -> SimpleDateFormat("dd MMM", Locale.ROOT).format(Date(timestamp)).uppercase(Locale.ROOT)
        else -> SimpleDateFormat("dd MMM yyyy", Locale.ROOT).format(Date(timestamp)).uppercase(Locale.ROOT)
    }
}

@Composable
fun ExpenseListScreen(
    tripName: String,
    expenses: List<Expense>,
    members: List<Member>,
    expenseSyncStatuses: Map<String, SyncStatus> = emptyMap(),
    onExpenseClick: (Expense) -> Unit,
    onAddExpenseClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }

    val memberMap = remember(members) { members.associateBy { it.id } }

    val filteredExpenses = remember(expenses, searchQuery, selectedCategoryFilter) {
        val query = searchQuery.trim().lowercase()
        expenses.filter { !it.isDeleted }.filter { expense ->
            val matchesCategory = selectedCategoryFilter == null || expense.category == selectedCategoryFilter
            val matchesQuery = if (query.isBlank()) true else {
                expense.description.lowercase().contains(query) ||
                        expense.category.name.lowercase().contains(query) ||
                        expense.payers.any { p ->
                            memberMap[p.memberId]?.name?.lowercase()?.contains(query) == true
                        }
            }
            matchesCategory && matchesQuery
        }.sortedByDescending { it.timestamp }
    }

    val groupedExpenses = remember(filteredExpenses) {
        filteredExpenses.groupBy { getGroupHeaderForTimestamp(it.timestamp) }
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
                    Column {
                        Text(
                            text = tripName,
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent
                        )
                        Text(
                            text = "Expenses (${expenses.count { !it.isDeleted }})",
                            style = FairShareTheme.typography.title,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }
                }

                FSButton(
                    text = "+ Add",
                    onClick = onAddExpenseClick,
                    variant = FSButtonVariant.Primary
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = if (isWide) 640.dp else 480.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by description, payer, category...", color = FairShareTheme.colors.disabled) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FairShareTheme.colors.accent,
                        unfocusedBorderColor = FairShareTheme.colors.border,
                        focusedTextColor = FairShareTheme.colors.textPrimary,
                        unfocusedTextColor = FairShareTheme.colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isAllSelected = selectedCategoryFilter == null
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isAllSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAllSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                        ),
                        modifier = Modifier.clickable { selectedCategoryFilter = null }
                    ) {
                        Text(
                            text = "All",
                            style = FairShareTheme.typography.metadata,
                            color = if (isAllSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    ExpenseCategory.values().forEach { cat ->
                        val isSelected = selectedCategoryFilter == cat
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) FairShareTheme.colors.accentSoft else FairShareTheme.colors.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border
                            ),
                            modifier = Modifier.clickable {
                                selectedCategoryFilter = if (isSelected) null else cat
                            }
                        ) {
                            Text(
                                text = cat.name.lowercase().replaceFirstChar { it.titlecase() },
                                style = FairShareTheme.typography.metadata,
                                color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredExpenses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (expenses.isEmpty()) "No expenses recorded yet" else "No matching expenses",
                                style = FairShareTheme.typography.title,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (expenses.isEmpty()) "Tap '+ Add' to create your first group expense" else "Try clearing your search or filter",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textTertiary
                            )
                            if (expenses.isEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                FSButton(
                                    text = "+ Add Expense",
                                    onClick = onAddExpenseClick,
                                    variant = FSButtonVariant.Primary
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedExpenses.forEach { (dateHeader, dateExpenses) ->
                            item(key = "header_$dateHeader") {
                                Text(
                                    text = dateHeader,
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                                )
                            }

                            items(dateExpenses, key = { it.id }) { expense ->
                                val payerNames = expense.payers.mapNotNull { memberMap[it.memberId]?.name }
                                val payerText = if (payerNames.isNotEmpty()) "${payerNames.joinToString(", ")} paid" else "Paid"
                                val peopleCount = expense.participants.count { !it.excluded }

                                val timeStr = SimpleDateFormat("hh:mm a", Locale.ROOT).format(Date(expense.timestamp))

                                FSCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onExpenseClick(expense) },
                                    backgroundColor = FairShareTheme.colors.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = expense.description.ifBlank { "Expense" },
                                                style = FairShareTheme.typography.section,
                                                color = FairShareTheme.colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "$payerText · $peopleCount people",
                                                style = FairShareTheme.typography.metadata,
                                                color = FairShareTheme.colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                FSStatusBadge(
                                                    text = expense.mode.name,
                                                    textColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.info else FairShareTheme.colors.accent,
                                                    backgroundColor = if (expense.mode == PaymentMode.ONLINE) FairShareTheme.colors.infoSoft else FairShareTheme.colors.accentSoft
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                val status = expenseSyncStatuses[expense.id] ?: SyncStatus.SYNCED
                                                val (badgeText, badgeColor, badgeBg) = when (status) {
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
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = timeStr,
                                                    style = FairShareTheme.typography.metadata,
                                                    color = FairShareTheme.colors.textTertiary
                                                )
                                            }
                                        }

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
                    }
                }
            }
        }
    }
}
