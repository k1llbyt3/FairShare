package com.fairshare.android.core.model

// Re-export domain models for backward compatibility across existing Phase 0/1 UI
typealias Currency = com.fairshare.android.core.domain.currency.Currency
typealias Money = com.fairshare.android.core.domain.currency.Money
typealias Member = com.fairshare.android.core.domain.model.Member
typealias PaymentMode = com.fairshare.android.core.domain.model.PaymentMode
typealias ExpenseCategory = com.fairshare.android.core.domain.model.ExpenseCategory
typealias SplitMethod = com.fairshare.android.core.domain.model.SplitMethod
typealias AdjustmentType = com.fairshare.android.core.domain.model.AdjustmentType
typealias Expense = com.fairshare.android.core.domain.model.Expense
typealias ExpensePayer = com.fairshare.android.core.domain.model.ExpensePayer
typealias ExpenseParticipant = com.fairshare.android.core.domain.model.ExpenseParticipant
typealias ExpenseItem = com.fairshare.android.core.domain.model.ExpenseItem
typealias Payment = com.fairshare.android.core.domain.model.SettlementPayment
typealias SettlementPayment = com.fairshare.android.core.domain.model.SettlementPayment
typealias Adjustment = com.fairshare.android.core.domain.model.Adjustment
typealias MemberBalance = com.fairshare.android.core.domain.balance.MemberBalance
typealias SettlementTransfer = com.fairshare.android.core.domain.settlement.SettlementTransfer

data class FinancialSummary(
    val totalSpend: Money,
    val perPersonShare: Money,
    val balances: List<MemberBalance>,
    val settlements: List<SettlementTransfer>
)
