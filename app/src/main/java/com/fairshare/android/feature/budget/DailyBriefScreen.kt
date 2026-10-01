package com.fairshare.android.feature.budget

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.budget.BudgetPlan
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.summary.DailySummaryEngine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun DailyBriefScreen(
    tripName: String,
    budgetPlan: BudgetPlan?,
    allExpenses: List<Expense>,
    payments: List<SettlementPayment> = emptyList(),
    members: List<Member> = emptyList(),
    onBackClick: () -> Unit,
    onExpenseClick: (Expense) -> Unit,
    onOpenBudgetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = budgetPlan?.baseBudget?.currency ?: allExpenses.firstOrNull()?.amount?.currency ?: Currency.INR
    val tz = remember(budgetPlan) {
        try {
            TimeZone.getTimeZone(budgetPlan?.timezone ?: "UTC")
        } catch (_: Exception) {
            TimeZone.getTimeZone("UTC")
        }
    }

    val displayDateFormat = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).apply { timeZone = tz } }

    var selectedDateEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedCategoryFilter by remember { mutableStateOf<ExpenseCategory?>(null) }

    // Calculate day boundaries
    val (dayStart, dayEnd) = remember(selectedDateEpochMs, tz) {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = selectedDateEpochMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis - 1
        Pair(start, end)
    }

    val summary = remember(allExpenses, dayStart, dayEnd, budgetPlan, payments, members) {
        DailySummaryEngine.generateDailySummary(
            allExpenses = allExpenses,
            dayStartEpochMs = dayStart,
            dayEndEpochMs = dayEnd,
            budgetPlan = budgetPlan,
            allPayments = payments,
            members = members,
            currency = currency
        )
    }

    val displayedExpenses = remember(summary.todayExpenses, selectedCategoryFilter) {
        if (selectedCategoryFilter != null) {
            summary.todayExpenses.filter { it.category == selectedCategoryFilter }
        } else {
            summary.todayExpenses
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
            // App Bar
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
                        text = "Daily Brief",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    Text(
                        text = tripName,
                        style = FairShareTheme.typography.metadata,
                        color = FairShareTheme.colors.textSecondary
                    )
                }
                FSButton(
                    text = "Budget",
                    onClick = onOpenBudgetClick,
                    variant = FSButtonVariant.Secondary
                )
            }

            // Date Navigation Row (Previous Day ← Date → Next Day)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FairShareTheme.colors.surfaceElevated)
                    .border(1.dp, FairShareTheme.colors.border)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FSButton(
                    text = "← Prev",
                    onClick = {
                        selectedDateEpochMs -= 86_400_000L
                        selectedCategoryFilter = null
                    },
                    variant = FSButtonVariant.Secondary
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayDateFormat.format(Date(selectedDateEpochMs)),
                        style = FairShareTheme.typography.body,
                        color = FairShareTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    val isToday = (System.currentTimeMillis() in dayStart..dayEnd)
                    if (isToday) {
                        Text(
                            text = "Today",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.accent
                        )
                    }
                }
                FSButton(
                    text = "Next →",
                    onClick = {
                        selectedDateEpochMs += 86_400_000L
                        selectedCategoryFilter = null
                    },
                    variant = FSButtonVariant.Secondary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Headline Card
                item {
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
                                    text = "DAILY SUMMARY",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                if (summary.plannedDailySpending != null) {
                                    FSStatusBadge(
                                        text = if (summary.isOverPace) "Above Pace" else "On Target",
                                        textColor = if (summary.isOverPace) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                                        backgroundColor = if (summary.isOverPace) FairShareTheme.colors.negativeSoft else FairShareTheme.colors.positiveSoft
                                    )
                                } else if (summary.isGroupSettled != null) {
                                    FSStatusBadge(
                                        text = if (summary.isGroupSettled) "Group Settled" else "Debts Active",
                                        textColor = if (summary.isGroupSettled) FairShareTheme.colors.positive else FairShareTheme.colors.accent,
                                        backgroundColor = if (summary.isGroupSettled) FairShareTheme.colors.positiveSoft else FairShareTheme.colors.accentSoft
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = summary.headline,
                                style = FairShareTheme.typography.title,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Spent on this date",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    Text(
                                        text = summary.totalSpentToday.formatted(),
                                        style = FairShareTheme.typography.display,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (summary.plannedDailySpending != null) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Planned Daily Target",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = summary.plannedDailySpending.formatted(),
                                            style = FairShareTheme.typography.section,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Factual Summary Notes
                if (summary.summaryNotes.isNotEmpty()) {
                    item {
                        FSCard(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = FairShareTheme.colors.surface
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "FACTUAL FINANCIAL EXPLANATION",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                summary.summaryNotes.forEach { note ->
                                    Text(
                                        text = "• $note",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }

                // 3. Where Spending Came From (Payer Contribution)
                if (summary.payerNameBreakdown.isNotEmpty()) {
                    item {
                        Text(
                            text = "Where Spending Came From",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }

                    items(summary.payerNameBreakdown.entries.toList()) { (payerName, spent) ->
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = payerName,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = spent.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 4. Pace & Variance Breakdown
                if (summary.differenceFromPlanned != null || summary.suggestedTomorrowLimit != null) {
                    item {
                        FSCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "PACE & RECOMMENDATIONS",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textTertiary
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                if (summary.differenceFromPlanned != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Target Variance:",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        val isOver = summary.differenceFromPlanned.amountMinor > 0L
                                        val sign = if (isOver) "+" else ""
                                        Text(
                                            text = "$sign${summary.differenceFromPlanned.formatted()}",
                                            style = FairShareTheme.typography.body,
                                            color = if (isOver) FairShareTheme.colors.negative else FairShareTheme.colors.positive,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                if (summary.suggestedTomorrowLimit != null && summary.suggestedTomorrowLimit.isPositive) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Suggested limit next day:",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = summary.suggestedTomorrowLimit.formatted() + " / day",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                if (summary.baseBudgetRemaining != null) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Base Budget Remaining:",
                                            style = FairShareTheme.typography.body,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                        Text(
                                            text = summary.baseBudgetRemaining.formatted(),
                                            style = FairShareTheme.typography.body,
                                            color = if (summary.baseBudgetRemaining.isPositive) FairShareTheme.colors.positive else FairShareTheme.colors.negative
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Recorded Payments on Date
                if (summary.paymentsToday.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recorded Payments on Date (${summary.paymentsToday.size})",
                            style = FairShareTheme.typography.section,
                            color = FairShareTheme.colors.textPrimary
                        )
                    }

                    items(summary.paymentsToday) { p ->
                        val fromName = members.firstOrNull { it.id == p.fromMemberId }?.name ?: "Member"
                        val toName = members.firstOrNull { it.id == p.toMemberId }?.name ?: "Member"
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$fromName paid $toName",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary
                                )
                                Text(
                                    text = p.amount.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.positive,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 6. Category Spending Breakdown (Clickable to Filter)
                if (summary.categoryBreakdown.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Spending by Category",
                                style = FairShareTheme.typography.section,
                                color = FairShareTheme.colors.textPrimary
                            )
                            if (selectedCategoryFilter != null) {
                                Text(
                                    text = "Clear filter",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.accent,
                                    modifier = Modifier.clickable { selectedCategoryFilter = null }
                                )
                            }
                        }
                    }

                    items(summary.categoryBreakdown.entries.toList()) { (cat, money) ->
                        val isSelected = selectedCategoryFilter == cat
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = if (isSelected) FairShareTheme.colors.surfaceElevated else FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) FairShareTheme.colors.accent else FairShareTheme.colors.border,
                                    shape = FairShareTheme.shapes.card
                                )
                                .clip(FairShareTheme.shapes.card)
                                .clickable {
                                    selectedCategoryFilter = if (isSelected) null else cat
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = cat.displayName,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Tap to filter source expenses",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }
                                Text(
                                    text = money.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 7. Source Expenses List (Deep Linking)
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (selectedCategoryFilter != null) "${selectedCategoryFilter!!.displayName} Expenses (${displayedExpenses.size})" else "Source Expenses for Date (${displayedExpenses.size})",
                        style = FairShareTheme.typography.section,
                        color = FairShareTheme.colors.textPrimary
                    )
                }

                if (displayedExpenses.isEmpty()) {
                    item {
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                        ) {
                            Text(
                                text = "No expenses recorded on this date.",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(displayedExpenses) { exp ->
                        Surface(
                            shape = FairShareTheme.shapes.card,
                            color = FairShareTheme.colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, FairShareTheme.colors.border, FairShareTheme.shapes.card)
                                .clip(FairShareTheme.shapes.card)
                                .clickable { onExpenseClick(exp) }
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
                                        text = exp.description,
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${exp.category.displayName} • Tap to view detail",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.textTertiary
                                    )
                                }
                                Text(
                                    text = exp.totalAmount.formatted(),
                                    style = FairShareTheme.typography.body,
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
