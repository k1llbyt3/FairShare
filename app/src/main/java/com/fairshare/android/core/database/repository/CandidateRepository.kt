package com.fairshare.android.core.database.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.TransactionCandidateEntity
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.transactions.CandidateSource
import com.fairshare.android.core.domain.transactions.CandidateStatus
import com.fairshare.android.core.domain.transactions.TransactionCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CandidateRepository(
    private val database: FairShareDatabase
) {

    suspend fun getPendingCandidates(): List<TransactionCandidate> = withContext(Dispatchers.IO) {
        val entities = database.candidateAndAttachmentDao().getCandidatesByStatus("PENDING")
        entities.map { it.toDomain() }
    }

    suspend fun saveCandidate(candidate: TransactionCandidate): Unit = withContext(Dispatchers.IO) {
        val entity = TransactionCandidateEntity(
            id = candidate.id,
            groupId = candidate.groupId,
            rawSource = candidate.rawSource.name,
            rawText = candidate.rawText,
            detectedAmountMinor = candidate.amount.amountMinor,
            currency = candidate.amount.currency.code,
            detectedMerchant = candidate.detectedMerchant,
            detectedTimestamp = candidate.detectedTimestamp,
            status = candidate.status.name,
            createdAt = candidate.createdAt
        )
        database.candidateAndAttachmentDao().insertCandidate(entity)
    }

    suspend fun updateCandidateStatus(candidateId: String, status: CandidateStatus): Unit = withContext(Dispatchers.IO) {
        val candidates = database.candidateAndAttachmentDao().getCandidatesByStatus("PENDING")
        val match = candidates.firstOrNull { it.id == candidateId }
        if (match != null) {
            val updated = match.copy(status = status.name)
            database.candidateAndAttachmentDao().insertCandidate(updated)
        }
    }

    /**
     * Checks if a candidate appears to be a duplicate of an existing expense in the group,
     * or of another candidate detected within a 2-hour window.
     */
    suspend fun checkDuplicate(candidate: TransactionCandidate, existingExpenses: List<Expense>): Pair<Boolean, String?> = withContext(Dispatchers.Default) {
        val candidateAmount = candidate.amount.amountMinor
        val candidateMerchant = candidate.detectedMerchant?.trim()?.lowercase() ?: ""

        // Check against recorded expenses
        for (exp in existingExpenses) {
            val sameAmount = exp.totalAmount.amountMinor == candidateAmount
            val desc = exp.description.trim().lowercase()
            val sameMerchant = candidateMerchant.isNotEmpty() && (desc.contains(candidateMerchant) || candidateMerchant.contains(desc))
            val timeDiffMillis = kotlin.math.abs(exp.timestamp - candidate.detectedTimestamp)
            val isRecent = timeDiffMillis < (4 * 3600 * 1000L) // within 4 hours

            if (sameAmount && sameMerchant) {
                return@withContext true to "Matches existing expense '${exp.description}' (${exp.totalAmount.formatted()})"
            } else if (sameAmount && isRecent) {
                return@withContext true to "Similar amount recorded recently for '${exp.description}'"
            }
        }

        // Check against pending candidates
        val pending = database.candidateAndAttachmentDao().getCandidatesByStatus("PENDING")
        for (other in pending) {
            if (other.id != candidate.id && other.detectedAmountMinor == candidateAmount) {
                val otherMerchant = other.detectedMerchant?.trim()?.lowercase() ?: ""
                if (candidateMerchant.isNotEmpty() && candidateMerchant == otherMerchant) {
                    return@withContext true to "Identical candidate already awaiting review"
                }
            }
        }

        false to null
    }

    private fun TransactionCandidateEntity.toDomain(): TransactionCandidate {
        val curr = if (currency == "USD") Currency.USD else Currency.INR
        val src = try { CandidateSource.valueOf(rawSource) } catch (_: Exception) { CandidateSource.NOTIFICATION }
        val st = try { CandidateStatus.valueOf(status) } catch (_: Exception) { CandidateStatus.PENDING }
        return TransactionCandidate(
            id = id,
            groupId = groupId,
            rawSource = src,
            rawText = rawText,
            amount = Money(detectedAmountMinor, curr),
            detectedMerchant = detectedMerchant,
            detectedTimestamp = detectedTimestamp,
            status = st,
            createdAt = createdAt
        )
    }
}
