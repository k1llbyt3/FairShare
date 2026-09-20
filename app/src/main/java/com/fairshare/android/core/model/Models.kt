package com.fairshare.android.core.model

import java.util.Locale

/**
 * Deterministic Financial Minor-Unit representation.
 * 1 Rupee = 100 Paise.
 * Integer arithmetic guarantees zero floating-point drift.
 */
data class Money(val paise: Long) {
    companion object {
        fun fromRupees(rupees: Double): Money {
            return Money(Math.round(rupees * 100))
        }

        fun fromRupees(rupees: Long): Money {
            return Money(rupees * 100)
        }

        val ZERO = Money(0)
    }

    val inRupees: Double
        get() = paise / 100.0

    fun formatted(): String {
        val absPaise = Math.abs(paise)
        val rupees = absPaise / 100
        val remainder = absPaise % 100
        val sign = if (paise < 0) "-" else ""
        return if (remainder == 0L) {
            String.format(Locale.getDefault(), "%s₹%,d", sign, rupees)
        } else {
            String.format(Locale.getDefault(), "%s₹%,d.%02d", sign, rupees, remainder)
        }
    }
}

data class Member(
    val id: String,
    val name: String,
    val isCurrentUser: Boolean = false
)

enum class PaymentMode {
    CASH,
    ONLINE
}

data class Expense(
    val id: String,
    val payerId: String,
    val amount: Money,
    val involvedMemberIds: List<String>,
    val mode: PaymentMode = PaymentMode.ONLINE,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class Payment(
    val id: String,
    val fromMemberId: String,
    val toMemberId: String,
    val amount: Money,
    val timestamp: Long = System.currentTimeMillis()
)

data class MemberBalance(
    val memberId: String,
    val memberName: String,
    val netBalance: Money
)

data class SettlementTransfer(
    val fromMemberId: String,
    val fromMemberName: String,
    val toMemberId: String,
    val toMemberName: String,
    val amount: Money
)

data class FinancialSummary(
    val totalSpend: Money,
    val perPersonShare: Money,
    val balances: List<MemberBalance>,
    val settlements: List<SettlementTransfer>
)
