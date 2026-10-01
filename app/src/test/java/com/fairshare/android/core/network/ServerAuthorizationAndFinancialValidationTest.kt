package com.fairshare.android.core.network

import com.fairshare.android.core.domain.model.ExpenseCategory
import com.fairshare.android.core.domain.model.PaymentMode
import com.fairshare.android.core.domain.model.SplitMethod
import com.fairshare.android.core.network.backend.guard.AuthorizationException
import com.fairshare.android.core.network.backend.guard.ServerAuthorizationGuard
import com.fairshare.android.core.network.backend.guard.ServerFinancialValidator
import com.fairshare.android.core.network.backend.guard.ServerValidationException
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendGroupMember
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.GroupRole
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerAuthorizationAndFinancialValidationTest {

    private val testGroup = BackendGroup(
        id = "group-1",
        name = "Goa Trip",
        createdById = "user-owner"
    )

    private val ownerMember = BackendGroupMember("group-1", "user-owner", GroupRole.OWNER)
    private val adminMember = BackendGroupMember("group-1", "user-admin", GroupRole.ADMIN)
    private val regularMember = BackendGroupMember("group-1", "user-regular", GroupRole.MEMBER)
    private val otherMember = BackendGroupMember("group-1", "user-other", GroupRole.MEMBER)

    private val allMembers = listOf(ownerMember, adminMember, regularMember, otherMember)

    // --- Authorization Tests ---

    @Test
    fun requireMembership_passesForActiveMember() {
        val member = ServerAuthorizationGuard.requireMembership("user-regular", "group-1", allMembers)
        assertNotNull(member)
    }

    @Test(expected = AuthorizationException::class)
    fun requireMembership_throwsForNonMember() {
        ServerAuthorizationGuard.requireMembership("user-outsider", "group-1", allMembers)
    }

    @Test
    fun requireAdminOrOwner_passesForOwnerAndAdmin() {
        ServerAuthorizationGuard.requireAdminOrOwner(ownerMember)
        ServerAuthorizationGuard.requireAdminOrOwner(adminMember)
    }

    @Test(expected = AuthorizationException::class)
    fun requireAdminOrOwner_throwsForRegularMember() {
        ServerAuthorizationGuard.requireAdminOrOwner(regularMember)
    }

    @Test
    fun removeMember_ownerCanRemoveAnyone() {
        ServerAuthorizationGuard.requireCanRemoveMember(ownerMember, regularMember)
        ServerAuthorizationGuard.requireCanRemoveMember(ownerMember, adminMember)
    }

    @Test
    fun removeMember_adminCanRemoveRegularMember() {
        ServerAuthorizationGuard.requireCanRemoveMember(adminMember, regularMember)
    }

    @Test(expected = AuthorizationException::class)
    fun removeMember_adminCannotRemoveOwner() {
        ServerAuthorizationGuard.requireCanRemoveMember(adminMember, ownerMember)
    }

    @Test
    fun removeMember_regularMemberCanRemoveThemselves() {
        ServerAuthorizationGuard.requireCanRemoveMember(regularMember, regularMember)
    }

    @Test(expected = AuthorizationException::class)
    fun removeMember_regularMemberCannotRemoveOthers() {
        ServerAuthorizationGuard.requireCanRemoveMember(regularMember, otherMember)
    }

    // --- Financial Validation Tests ---

    @Test
    fun validateExpense_passesForValidReconciledIntegerMinorUnits() {
        val validExpense = BackendExpense(
            id = "exp-1",
            groupId = "group-1",
            createdById = "user-owner",
            payerId = "user-owner",
            amountMinor = 10000L, // ₹100.00
            description = "Dinner",
            payers = listOf(BackendExpensePayer("user-owner", 10000L)),
            participants = listOf(
                BackendExpenseParticipant("user-owner", shareMinor = 5000L),
                BackendExpenseParticipant("user-regular", shareMinor = 5000L)
            )
        )
        ServerFinancialValidator.validateExpense(validExpense, testGroup, allMembers)
    }

    @Test(expected = ServerValidationException::class)
    fun validateExpense_zeroOrNegativeAmount_throws() {
        val invalidExpense = BackendExpense(
            id = "exp-zero",
            groupId = "group-1",
            createdById = "user-owner",
            payerId = "user-owner",
            amountMinor = 0L,
            description = "Free item",
            participants = listOf(BackendExpenseParticipant("user-owner", shareMinor = 0L))
        )
        ServerFinancialValidator.validateExpense(invalidExpense, testGroup, allMembers)
    }

    @Test(expected = ServerValidationException::class)
    fun validateExpense_unreconciledShares_throws() {
        // Total 10000 but sum of shares is 9999 (off by 1 paisa)
        val unreconciled = BackendExpense(
            id = "exp-unrec",
            groupId = "group-1",
            createdById = "user-owner",
            payerId = "user-owner",
            amountMinor = 10000L,
            description = "Split mismatch",
            participants = listOf(
                BackendExpenseParticipant("user-owner", shareMinor = 5000L),
                BackendExpenseParticipant("user-regular", shareMinor = 4999L)
            )
        )
        ServerFinancialValidator.validateExpense(unreconciled, testGroup, allMembers)
    }

    @Test(expected = ServerValidationException::class)
    fun validateExpense_nonMemberParticipant_throws() {
        val invalidParticipant = BackendExpense(
            id = "exp-outsider",
            groupId = "group-1",
            createdById = "user-owner",
            payerId = "user-owner",
            amountMinor = 10000L,
            description = "Dinner",
            participants = listOf(
                BackendExpenseParticipant("user-owner", shareMinor = 5000L),
                BackendExpenseParticipant("user-outsider", shareMinor = 5000L)
            )
        )
        ServerFinancialValidator.validateExpense(invalidParticipant, testGroup, allMembers)
    }

    @Test
    fun validateSettlementPayment_passesForValidTransferBetweenMembers() {
        val validPayment = BackendSettlementPayment(
            id = "pay-1",
            groupId = "group-1",
            fromUserId = "user-regular",
            toUserId = "user-owner",
            amountMinor = 5000L, // ₹50.00
            createdByUserId = "user-regular"
        )
        ServerFinancialValidator.validateSettlementPayment(validPayment, testGroup, allMembers)
    }

    @Test(expected = ServerValidationException::class)
    fun validateSettlementPayment_sameSenderAndReceiver_throws() {
        val selfPayment = BackendSettlementPayment(
            id = "pay-self",
            groupId = "group-1",
            fromUserId = "user-regular",
            toUserId = "user-regular",
            amountMinor = 5000L,
            createdByUserId = "user-regular"
        )
        ServerFinancialValidator.validateSettlementPayment(selfPayment, testGroup, allMembers)
    }
}
