package com.fairshare.android.core.domain.vault

import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money

data class VaultContribution(
    val id: String,
    val memberId: String,
    val amount: Money,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String? = null
)

data class MemberVaultShare(
    val memberId: String,
    val memberName: String,
    val totalContributed: Money,
    val allocatedSpent: Money,
    val remainingShare: Money
)

data class VaultLedgerState(
    val groupId: String,
    val fundName: String,
    val totalContributed: Money,
    val totalSpentFromPool: Money,
    val remainingPoolBalance: Money,
    val memberShares: List<MemberVaultShare>,
    val contributions: List<VaultContribution>,
    val currency: Currency = Currency.INR
) {
    companion object {
        const val DISCLAIMER = "Virtual group accounting ledger • Not a bank account • FairShare does not custody, hold, or transfer funds."
    }
}
