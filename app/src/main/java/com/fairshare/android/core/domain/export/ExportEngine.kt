package com.fairshare.android.core.domain.export

import com.fairshare.android.core.domain.balance.MemberBalance
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.Group
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportEngine {

    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun generateExport(
        groupName: String,
        scope: ExportScope,
        format: ExportFormat,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        members: List<Member>,
        balances: List<MemberBalance> = emptyList()
    ): ExportData {
        val memberMap = members.associateBy { it.id }
        val safeGroupName = groupName.replace(Regex("""[^a-zA-Z0-9_\-]"""), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

        return when (format) {
            ExportFormat.CSV -> {
                val csvContent = buildCsv(scope, expenses, payments, memberMap)
                val count = when (scope) {
                    ExportScope.FULL_LEDGER -> expenses.size + payments.size
                    ExportScope.EXPENSES_ONLY -> expenses.size
                    ExportScope.SETTLEMENTS_ONLY -> payments.size
                }
                ExportData(
                    fileName = "${safeGroupName}_export_$timestamp.csv",
                    mimeType = format.mimeType,
                    content = csvContent,
                    recordCount = count
                )
            }
            ExportFormat.TEXT_SUMMARY -> {
                val textContent = buildTextSummary(groupName, scope, expenses, payments, members, balances)
                val count = when (scope) {
                    ExportScope.FULL_LEDGER -> expenses.size + payments.size
                    ExportScope.EXPENSES_ONLY -> expenses.size
                    ExportScope.SETTLEMENTS_ONLY -> payments.size
                }
                ExportData(
                    fileName = "${safeGroupName}_summary_$timestamp.txt",
                    mimeType = format.mimeType,
                    content = textContent,
                    recordCount = count
                )
            }
        }
    }

    private fun buildCsv(
        scope: ExportScope,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        memberMap: Map<String, Member>
    ): String {
        val sb = StringBuilder()

        if (scope == ExportScope.FULL_LEDGER || scope == ExportScope.EXPENSES_ONLY) {
            sb.appendLine("Type,Date,Description,Category,Paid By,Amount,Currency,Split Method,Notes")
            for (exp in expenses.sortedByDescending { it.timestamp }) {
                val dateStr = DATE_FORMAT.format(Date(exp.timestamp))
                val payers = exp.payers.mapNotNull { memberMap[it.memberId]?.name }.joinToString(";")
                val notes = (exp.notes ?: "").replace("\"", "\"\"")
                val desc = exp.description.replace("\"", "\"\"")
                val amountFormatted = String.format(Locale.US, "%.2f", exp.totalAmount.amountMinor / 100.0)

                sb.appendLine("Expense,\"$dateStr\",\"$desc\",${exp.category.name},\"$payers\",$amountFormatted,${exp.totalAmount.currency.code},${exp.splitMethod.name},\"$notes\"")
            }
        }

        if (scope == ExportScope.FULL_LEDGER && payments.isNotEmpty()) {
            sb.appendLine()
        }

        if (scope == ExportScope.FULL_LEDGER || scope == ExportScope.SETTLEMENTS_ONLY) {
            sb.appendLine("Type,Date,From,To,Amount,Currency,Payment Mode,Notes")
            for (p in payments.sortedByDescending { it.timestamp }) {
                val dateStr = DATE_FORMAT.format(Date(p.timestamp))
                val fromName = memberMap[p.fromMemberId]?.name ?: "Member"
                val toName = memberMap[p.toMemberId]?.name ?: "Member"
                val amountFormatted = String.format(Locale.US, "%.2f", p.amount.amountMinor / 100.0)
                val note = (p.note ?: "").replace("\"", "\"\"")

                sb.appendLine("Settlement,\"$dateStr\",\"$fromName\",\"$toName\",$amountFormatted,${p.amount.currency.code},${p.mode.name},\"$note\"")
            }
        }

        return sb.toString()
    }

    private fun buildTextSummary(
        groupName: String,
        scope: ExportScope,
        expenses: List<Expense>,
        payments: List<SettlementPayment>,
        members: List<Member>,
        balances: List<MemberBalance>
    ): String {
        val memberMap = members.associateBy { it.id }
        val sb = StringBuilder()
        sb.appendLine("=========================================")
        sb.appendLine("FairShare Group Summary: $groupName")
        sb.appendLine("Generated: ${DATE_FORMAT.format(Date())}")
        sb.appendLine("=========================================")
        sb.appendLine()

        if (balances.isNotEmpty()) {
            sb.appendLine("--- CURRENT BALANCES ---")
            for (b in balances) {
                val name = memberMap[b.memberId]?.name ?: b.memberId
                val status = if (b.netBalance.amountMinor > 0) "+${b.netBalance.formatted()} (Gets back)"
                else if (b.netBalance.amountMinor < 0) "${b.netBalance.formatted()} (Owes)"
                else "Settled up (₹0)"
                sb.appendLine("$name: $status")
            }
            sb.appendLine()
        }

        if (scope == ExportScope.FULL_LEDGER || scope == ExportScope.EXPENSES_ONLY) {
            val totalExpenseMinor = expenses.sumOf { it.totalAmount.amountMinor }
            val currency = expenses.firstOrNull()?.totalAmount?.currency
            val formattedTotal = if (currency != null) com.fairshare.android.core.domain.currency.Money(totalExpenseMinor, currency).formatted() else "₹${totalExpenseMinor / 100}"

            sb.appendLine("--- EXPENSES (${expenses.size} total: $formattedTotal) ---")
            for (exp in expenses.sortedByDescending { it.timestamp }) {
                val dateStr = DATE_FORMAT.format(Date(exp.timestamp))
                val payers = exp.payers.mapNotNull { memberMap[it.memberId]?.name }.joinToString(", ")
                sb.appendLine("• $dateStr | ${exp.description} | ${exp.totalAmount.formatted()} | Paid by $payers (${exp.category.displayName})")
            }
            sb.appendLine()
        }

        if (scope == ExportScope.FULL_LEDGER || scope == ExportScope.SETTLEMENTS_ONLY) {
            sb.appendLine("--- SETTLEMENT PAYMENTS (${payments.size} total) ---")
            for (p in payments.sortedByDescending { it.timestamp }) {
                val dateStr = DATE_FORMAT.format(Date(p.timestamp))
                val fromName = memberMap[p.fromMemberId]?.name ?: "Member"
                val toName = memberMap[p.toMemberId]?.name ?: "Member"
                sb.appendLine("• $dateStr | $fromName paid $toName | ${p.amount.formatted()}")
            }
            sb.appendLine()
        }

        sb.appendLine("Exported cleanly via FairShare.")
        return sb.toString()
    }
}
