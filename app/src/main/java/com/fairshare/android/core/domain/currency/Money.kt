package com.fairshare.android.core.domain.currency

import java.util.Locale

/**
 * Deterministic Financial Minor-Unit representation.
 * All financial storage and computation must use integer minor units (e.g. Paise for INR, Cents for USD).
 * Never uses Double or floating-point values as authoritative storage.
 */
data class Money(
    val amountMinor: Long,
    val currency: Currency = Currency.INR
) : Comparable<Money> {

    /**
     * Backward compatibility property for existing codebase (INR paise).
     */
    val paise: Long
        get() = amountMinor

    val isPositive: Boolean
        get() = amountMinor > 0L

    val isNegative: Boolean
        get() = amountMinor < 0L

    val isZero: Boolean
        get() = amountMinor == 0L

    fun abs(): Money = Money(kotlin.math.abs(amountMinor), currency)

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor + other.amountMinor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(amountMinor - other.amountMinor, currency)
    }

    operator fun times(factor: Long): Money {
        return Money(amountMinor * factor, currency)
    }

    operator fun times(factor: Int): Money {
        return Money(amountMinor * factor.toLong(), currency)
    }

    operator fun unaryMinus(): Money = Money(-amountMinor, currency)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency.code == other.currency.code) {
            "Currency mismatch: cannot operate between ${currency.code} and ${other.currency.code}"
        }
    }

    /**
     * Converts to major unit (e.g. Rupees) strictly for display/presentation.
     */
    val inMajorUnits: Double
        get() = if (currency.minorUnitDecimals == 0) {
            amountMinor.toDouble()
        } else {
            val divisor = powerOfTen(currency.minorUnitDecimals)
            amountMinor.toDouble() / divisor.toDouble()
        }

    /**
     * Backward compatibility for existing UI code.
     */
    val inRupees: Double
        get() = inMajorUnits

    /**
     * Deterministic, reproducible string formatting.
     */
    fun formatted(): String {
        val absMinor = kotlin.math.abs(amountMinor)
        val sign = if (amountMinor < 0) "-" else ""

        if (currency.minorUnitDecimals == 0) {
            return String.format(Locale.ROOT, "%s%s%,d", sign, currency.symbol, absMinor)
        }

        val divisor = powerOfTen(currency.minorUnitDecimals)
        val major = absMinor / divisor
        val remainder = absMinor % divisor

        return if (remainder == 0L) {
            String.format(Locale.ROOT, "%s%s%,d", sign, currency.symbol, major)
        } else {
            val formatSpec = "%s%s%,d.%0${currency.minorUnitDecimals}d"
            String.format(Locale.ROOT, formatSpec, sign, currency.symbol, major, remainder)
        }
    }

    override fun toString(): String = formatted()

    companion object {
        val ZERO = Money(0L, Currency.INR)

        fun zero(currency: Currency = Currency.INR): Money = Money(0L, currency)

        fun fromMinor(amountMinor: Long, currency: Currency = Currency.INR): Money =
            Money(amountMinor, currency)

        fun fromRupees(rupees: Long): Money =
            Money(rupees * 100L, Currency.INR)

        fun fromRupees(rupees: Double): Money {
            val paise = Math.round(rupees * 100.0)
            return Money(paise, Currency.INR)
        }

        fun fromMajor(major: Long, currency: Currency = Currency.INR): Money {
            val factor = powerOfTen(currency.minorUnitDecimals)
            return Money(major * factor, currency)
        }

        fun fromMajor(major: Double, currency: Currency = Currency.INR): Money {
            val factor = powerOfTen(currency.minorUnitDecimals)
            val minor = Math.round(major * factor.toDouble())
            return Money(minor, currency)
        }

        private fun powerOfTen(n: Int): Long {
            var result = 1L
            for (i in 1..n) {
                result *= 10L
            }
            return result
        }
    }
}
