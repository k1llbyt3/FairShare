package com.fairshare.android.core.domain.export

enum class ExportFormat(val displayName: String, val extension: String, val mimeType: String) {
    CSV("CSV Spreadsheet", "csv", "text/csv"),
    TEXT_SUMMARY("Text Summary", "txt", "text/plain")
}

enum class ExportScope(val displayName: String) {
    FULL_LEDGER("Full Group Ledger (Expenses + Settlements)"),
    EXPENSES_ONLY("Expenses Only"),
    SETTLEMENTS_ONLY("Settlement Payments Only")
}

data class ExportData(
    val fileName: String,
    val mimeType: String,
    val content: String,
    val recordCount: Int
)

data class SharePayload(
    val title: String,
    val text: String
)
