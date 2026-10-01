package com.fairshare.android.core.domain.currency

/**
 * Currency representation with ISO code, display symbol, and minor unit decimals.
 * Default is INR with 2 decimal places (paise).
 */
data class Currency(
    val code: String,
    val symbol: String,
    val minorUnitDecimals: Int = 2
) {
    companion object {
        val INR = Currency(code = "INR", symbol = "₹", minorUnitDecimals = 2)
        val USD = Currency(code = "USD", symbol = "$", minorUnitDecimals = 2)
        val EUR = Currency(code = "EUR", symbol = "€", minorUnitDecimals = 2)
        val GBP = Currency(code = "GBP", symbol = "£", minorUnitDecimals = 2)
        val JPY = Currency(code = "JPY", symbol = "¥", minorUnitDecimals = 0)
    }
}
