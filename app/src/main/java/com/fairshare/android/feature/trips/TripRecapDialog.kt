package com.fairshare.android.feature.trips

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fairshare.android.core.database.entity.TripEntity
import com.fairshare.android.core.database.entity.TripItineraryItemEntity
import com.fairshare.android.core.database.entity.TripSegmentEntity
import com.fairshare.android.core.design.FSButton
import com.fairshare.android.core.design.FSButtonVariant
import com.fairshare.android.core.design.FSCard
import com.fairshare.android.core.design.FSStatusBadge
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.domain.balance.BalanceEngine
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.domain.settlement.SettlementEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun TripRecapDialog(
    trip: TripEntity,
    itineraryItems: List<TripItineraryItemEntity>,
    segments: List<TripSegmentEntity>,
    expenses: List<Expense>,
    members: List<Member>,
    payments: List<SettlementPayment>,
    onDismiss: () -> Unit
) {
    val currency = expenses.firstOrNull()?.amount?.currency ?: Currency.INR
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    // 1. Duration calculation
    val durationDays = remember(trip.startDate, trip.endDate) {
        val diffMs = max(0L, trip.endDate - trip.startDate)
        val days = (diffMs / 86_400_000L).toInt() + 1
        days
    }

    // 2. Itinerary completion
    val completedCount = remember(itineraryItems) {
        itineraryItems.count { it.status == "COMPLETED" }
    }
    val skippedCount = remember(itineraryItems) {
        itineraryItems.count { it.status == "SKIPPED" }
    }
    val totalItems = itineraryItems.size

    // 3. Planned vs Covered Distance & Travel Time
    val totalPlannedDistKm = remember(segments) {
        segments.sumOf { it.plannedDistanceKm }
    }
    val totalCoveredDistKm = remember(segments) {
        segments.sumOf { it.coveredDistanceKm }
    }
    val totalTravelDurationMinutes = remember(segments) {
        segments.sumOf { it.travelDurationMinutes }
    }

    // 4. Financial Spending (if linked group has expenses)
    val totalSpendingMinor = remember(expenses) {
        expenses.filter { !it.isDeleted }.sumOf { it.totalAmount.amountMinor }
    }
    val totalSpending = Money(totalSpendingMinor, currency)

    // Category breakdown
    val categoryTotals = remember(expenses) {
        val active = expenses.filter { !it.isDeleted }
        val map = HashMap<ExpenseCategory, Long>()
        for (e in active) {
            val cur = map[e.category] ?: 0L
            map[e.category] = cur + e.totalAmount.amountMinor
        }
        map.entries.sortedByDescending { it.value }.take(4)
    }

    // Settlement status
    val settlementPlan = remember(members, expenses, payments) {
        if (members.isNotEmpty()) {
            val balances = BalanceEngine.calculateGroupBalances(members, expenses.filter { !it.isDeleted }, payments, emptyList(), currency)
            SettlementEngine.calculateSettlement(balances, currency)
        } else null
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trip Recap",
                        style = FairShareTheme.typography.title,
                        color = FairShareTheme.colors.textPrimary
                    )
                    FSStatusBadge(
                        text = "Verified Recap",
                        textColor = FairShareTheme.colors.accent,
                        backgroundColor = FairShareTheme.colors.accentSoft
                    )
                }
                Text(
                    text = trip.name,
                    style = FairShareTheme.typography.section,
                    color = FairShareTheme.colors.accent
                )
                Text(
                    text = "${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))} ($durationDays days)",
                    style = FairShareTheme.typography.metadata,
                    color = FairShareTheme.colors.textSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: Itinerary & Travel Execution
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ITINERARY & ROUTE PROGRESS",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Itinerary Items Completed:",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = "$completedCount of $totalItems" + if (skippedCount > 0) " ($skippedCount skipped)" else "",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Planned Distance:",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = "%.1f km".format(totalPlannedDistKm),
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Actual Covered Distance:",
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Text(
                                text = "%.1f km".format(totalCoveredDistKm),
                                style = FairShareTheme.typography.body,
                                color = FairShareTheme.colors.positive,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (totalTravelDurationMinutes > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val hours = totalTravelDurationMinutes / 60
                            val mins = totalTravelDurationMinutes % 60
                            val durationText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Recorded Travel Time:",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = durationText,
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary
                                )
                            }
                        }

                        if (segments.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = FairShareTheme.colors.border)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Daily Travel Totals:",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            for (day in 1..durationDays) {
                                val dayItems = itineraryItems.filter { it.dayIndex == day }
                                val dayItemIds = dayItems.map { it.id }.toSet()
                                val daySegments = segments.filter { seg ->
                                    seg.fromItineraryItemId in dayItemIds || seg.toItineraryItemId in dayItemIds || (day == 1 && seg.fromItineraryItemId == null && seg.toItineraryItemId == null)
                                }
                                val dayPlanned = daySegments.sumOf { it.plannedDistanceKm }
                                val dayCovered = daySegments.sumOf { it.coveredDistanceKm }
                                if (dayPlanned > 0.0 || dayCovered > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Day $day",
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = "%.1f km covered / %.1f km planned".format(dayCovered, dayPlanned),
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Financial Spending & Settlement (if group linked or expenses exist)
                FSCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FairShareTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "FINANCIAL LEDGER & SETTLEMENT",
                            style = FairShareTheme.typography.metadata,
                            color = FairShareTheme.colors.textTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (trip.groupId == null && expenses.isEmpty()) {
                            Text(
                                text = "No expense group linked to this trip. Financial recap will populate when linked to a group.",
                                style = FairShareTheme.typography.metadata,
                                color = FairShareTheme.colors.textSecondary
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Trip Spending:",
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Text(
                                    text = totalSpending.formatted(),
                                    style = FairShareTheme.typography.body,
                                    color = FairShareTheme.colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (categoryTotals.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Major Spending Categories:",
                                    style = FairShareTheme.typography.metadata,
                                    color = FairShareTheme.colors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                categoryTotals.forEach { (cat, amountMinor) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = cat.displayName,
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = Money(amountMinor, currency).formatted(),
                                            style = FairShareTheme.typography.metadata,
                                            color = FairShareTheme.colors.textSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (settlementPlan != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = FairShareTheme.colors.border)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Settlement Status:",
                                        style = FairShareTheme.typography.body,
                                        color = FairShareTheme.colors.textSecondary
                                    )
                                    if (settlementPlan.isSettled || settlementPlan.transfers.isEmpty()) {
                                        FSStatusBadge(
                                            text = "All Settled",
                                            textColor = FairShareTheme.colors.positive,
                                            backgroundColor = FairShareTheme.colors.positiveSoft
                                        )
                                    } else {
                                        FSStatusBadge(
                                            text = "${settlementPlan.totalTransferred.formatted()} Pending",
                                            textColor = FairShareTheme.colors.accent,
                                            backgroundColor = FairShareTheme.colors.accentSoft
                                        )
                                    }
                                }

                                if (settlementPlan.transfers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "WHO OWES WHOM",
                                        style = FairShareTheme.typography.metadata,
                                        color = FairShareTheme.colors.accent,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    settlementPlan.transfers.forEach { tr ->
                                        val fromName = members.find { it.id == tr.fromMemberId }?.name ?: tr.fromMemberId
                                        val toName = members.find { it.id == tr.toMemberId }?.name ?: tr.toMemberId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$fromName → $toName",
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.textPrimary
                                            )
                                            Text(
                                                text = tr.amount.formatted(),
                                                style = FairShareTheme.typography.body,
                                                color = FairShareTheme.colors.negative,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val context = LocalContext.current
                val shareSummaryText = remember(trip, totalCoveredDistKm, totalPlannedDistKm, totalSpending, settlementPlan) {
                    buildString {
                        appendLine("🏖️ FairShare Trip Recap: ${trip.name}")
                        appendLine("📅 Duration: ${dateFormat.format(Date(trip.startDate))} - ${dateFormat.format(Date(trip.endDate))} ($durationDays days)")
                        appendLine("📍 Itinerary: $completedCount/$totalItems activities completed")
                        val places = itineraryItems.mapNotNull { it.destination.takeIf { d -> d.isNotBlank() } }.distinct()
                        if (places.isNotEmpty()) {
                            appendLine("🗺️ Places: ${places.joinToString(", ")}")
                        }
                        appendLine("🚗 Distance Covered: %.1f km (Planned: %.1f km)".format(totalCoveredDistKm, totalPlannedDistDistSafe(totalPlannedDistKm)))
                        if (totalSpendingMinor > 0L) {
                            appendLine("💰 Total Group Spending: ${totalSpending.formatted()}")
                        }
                        if (settlementPlan != null && settlementPlan.transfers.isNotEmpty()) {
                            appendLine("\n🤝 Who Owes Whom:")
                            settlementPlan.transfers.forEach { tr ->
                                val fromName = members.find { it.id == tr.fromMemberId }?.name ?: tr.fromMemberId
                                val toName = members.find { it.id == tr.toMemberId }?.name ?: tr.toMemberId
                                appendLine(" • $fromName owes $toName: ${tr.amount.formatted()}")
                            }
                        } else if (settlementPlan != null) {
                            appendLine("✨ Group is completely settled!")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FSButton(
                        text = "Share Recap",
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Trip Recap: ${trip.name}")
                                putExtra(Intent.EXTRA_TEXT, shareSummaryText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Trip Recap"))
                        },
                        variant = FSButtonVariant.Secondary,
                        modifier = Modifier.weight(1f)
                    )

                    FSButton(
                        text = "Close",
                        onClick = onDismiss,
                        variant = FSButtonVariant.Primary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun totalPlannedDistDistSafe(d: Double): Double = if (d > 0.0) d else 0.0
