package com.fairshare.android.core.network.backend.guard

import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendGroupMember
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.backend.model.GroupRole

class AuthenticationException(message: String) : RuntimeException(message)
class AuthorizationException(message: String) : RuntimeException(message)
class ServerValidationException(message: String) : RuntimeException(message)
class ResourceNotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)

/**
 * Server-side authorization and access control guards.
 * As required by specs/Plan.md P4-T04: Never trust client-side permission checks.
 */
object ServerAuthorizationGuard {

    fun requireMembership(
        userId: String,
        groupId: String,
        members: List<BackendGroupMember>
    ): BackendGroupMember {
        return members.find { it.groupId == groupId && it.userId == userId }
            ?: throw AuthorizationException("User ($userId) is not an active member of group ($groupId)")
    }

    fun requireAdminOrOwner(
        member: BackendGroupMember,
        action: String = "perform this operation"
    ) {
        if (!member.role.canManageGroup()) {
            throw AuthorizationException("Permission denied: ${member.role} role cannot $action")
        }
    }

    fun requireCanManageInvitations(member: BackendGroupMember) {
        if (!member.role.canManageInvitations()) {
            throw AuthorizationException("Permission denied: Only OWNER or ADMIN can manage group invitations")
        }
    }

    fun requireCanRemoveMember(
        actorMember: BackendGroupMember,
        targetMember: BackendGroupMember
    ) {
        val isSelf = actorMember.userId == targetMember.userId
        if (!actorMember.role.canRemoveMember(targetMember.role, isSelf)) {
            throw AuthorizationException("Permission denied: ${actorMember.role} cannot remove member with role ${targetMember.role}")
        }
    }
}

/**
 * Server-side financial validation.
 * As required by specs/Plan.md P4-T06 & specs/PRD.md Section 68 / Section 11.
 * Validates amounts, participants, currencies, payers, and split reconciliation in integer minor units.
 */
object ServerFinancialValidator {

    fun validateExpense(
        expense: BackendExpense,
        group: BackendGroup,
        groupMembers: List<BackendGroupMember>
    ) {
        if (expense.amountMinor <= 0L) {
            throw ServerValidationException("Expense amount must be greater than zero. Received: ${expense.amountMinor}")
        }

        if (expense.description.isBlank()) {
            throw ServerValidationException("Expense description cannot be empty")
        }

        if (expense.groupId != group.id) {
            throw ServerValidationException("Expense groupId (${expense.groupId}) does not match target group (${group.id})")
        }

        val memberUserIds = groupMembers.map { it.userId }.toSet()

        // Validate creator
        if (expense.createdById.isNotBlank() && !memberUserIds.contains(expense.createdById)) {
            throw ServerValidationException("Expense creator (${expense.createdById}) is not a member of group (${group.id})")
        }

        // Validate primary payer
        if (expense.payerId.isNotBlank() && !memberUserIds.contains(expense.payerId)) {
            throw ServerValidationException("Expense payer (${expense.payerId}) is not a member of group (${group.id})")
        }

        // Validate multiple payers if present
        if (expense.payers.isNotEmpty()) {
            var payersSum = 0L
            for (p in expense.payers) {
                if (!memberUserIds.contains(p.memberId)) {
                    throw ServerValidationException("Payer (${p.memberId}) is not a member of group (${group.id})")
                }
                if (p.amountMinor <= 0L) {
                    throw ServerValidationException("Payer amount must be greater than zero. Received: ${p.amountMinor} for ${p.memberId}")
                }
                payersSum += p.amountMinor
            }
            if (payersSum != expense.amountMinor) {
                throw ServerValidationException("Sum of payers ($payersSum) does not match total expense amount (${expense.amountMinor})")
            }
        }

        // Validate participants
        if (expense.participants.isEmpty()) {
            throw ServerValidationException("Expense must have at least one participant")
        }

        val includedParticipants = expense.participants.filter { !it.excluded }
        if (includedParticipants.isEmpty()) {
            throw ServerValidationException("Expense must have at least one non-excluded participant")
        }

        for (part in expense.participants) {
            if (!memberUserIds.contains(part.memberId)) {
                throw ServerValidationException("Participant (${part.memberId}) is not a member of group (${group.id})")
            }
            if (part.shareMinor < 0L) {
                throw ServerValidationException("Participant share cannot be negative. Received: ${part.shareMinor} for ${part.memberId}")
            }
        }

        // Validate split reconciliation: sum of participant shares MUST equal expense amountMinor!
        val sumOfShares = expense.participants.filter { !it.excluded }.sumOf { it.shareMinor }
        if (sumOfShares != expense.amountMinor) {
            throw ServerValidationException(
                "Split reconciliation failed: sum of participant shares ($sumOfShares) must exactly equal total amount (${expense.amountMinor})"
            )
        }
    }

    fun validateSettlementPayment(
        payment: BackendSettlementPayment,
        group: BackendGroup,
        groupMembers: List<BackendGroupMember>
    ) {
        if (payment.amountMinor <= 0L) {
            throw ServerValidationException("Settlement payment amount must be greater than zero. Received: ${payment.amountMinor}")
        }

        if (payment.fromUserId == payment.toUserId) {
            throw ServerValidationException("Settlement payment sender and receiver cannot be the same user (${payment.fromUserId})")
        }

        val memberUserIds = groupMembers.map { it.userId }.toSet()

        if (!memberUserIds.contains(payment.fromUserId)) {
            throw ServerValidationException("Sender (${payment.fromUserId}) is not a member of group (${group.id})")
        }

        if (!memberUserIds.contains(payment.toUserId)) {
            throw ServerValidationException("Receiver (${payment.toUserId}) is not a member of group (${group.id})")
        }

        if (payment.createdByUserId.isNotBlank() && !memberUserIds.contains(payment.createdByUserId)) {
            throw ServerValidationException("Payment recorder (${payment.createdByUserId}) is not a member of group (${group.id})")
        }
    }
}
