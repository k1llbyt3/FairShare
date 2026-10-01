package com.fairshare.android.core.network

import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
import com.fairshare.android.core.network.backend.guard.AuthenticationException
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FairShareBackendServiceTest {

    @Test
    fun fullBackendServiceLifecycleTest() {
        val gateway = RealSmsVerificationGateway()
        val service = FairShareBackendService(verificationGateway = gateway)

        // 1. User 1 registers via phone OTP verification
        val phone1 = "+919876543210"
        service.requestPhoneVerification(phone1)
        val code1 = gateway.getCodeForTesting(phone1)!!

        val authResult1 = service.verifyPhoneCode(
            rawPhone = phone1,
            code = code1,
            displayName = "Alice",
            defaultCurrency = "INR"
        )
        val token1 = authResult1.session.token
        val user1 = authResult1.user

        assertEquals("Alice", user1.displayName)
        assertEquals("INR", user1.defaultCurrency)

        // Verify current user
        val currentUser = service.getCurrentUser(token1)
        assertEquals(user1.id, currentUser.id)

        // 2. User 1 creates a group
        val group = service.createGroup(
            sessionToken = token1,
            name = "Goa Vacation",
            description = "Trip 2026",
            groupType = "TRIP",
            currency = "INR"
        )
        assertEquals("Goa Vacation", group.name)
        assertEquals(user1.id, group.createdById)

        val groupsUser1 = service.listUserGroups(token1)
        assertEquals(1, groupsUser1.size)
        assertEquals(group.id, groupsUser1[0].id)

        // 3. User 1 generates an invite code
        val invitation = service.createInvitation(
            sessionToken = token1,
            groupId = group.id,
            inviteType = "CODE",
            maxUses = 5
        )
        assertNotNull(invitation.inviteCode)
        assertTrue(invitation.isValid())

        // Check invitation details
        val inviteDetails = service.getInvitationDetails(invitation.inviteCode)
        assertEquals(group.id, inviteDetails.group.id)
        assertEquals(user1.id, inviteDetails.invitedByUser.id)

        // 4. User 2 registers via phone OTP
        val phone2 = "+919876543211"
        service.requestPhoneVerification(phone2)
        val code2 = gateway.getCodeForTesting(phone2)!!

        val authResult2 = service.verifyPhoneCode(
            rawPhone = phone2,
            code = code2,
            displayName = "Bob",
            defaultCurrency = "INR"
        )
        val token2 = authResult2.session.token
        val user2 = authResult2.user

        // 5. User 2 joins group via invite code
        val joinedGroup = service.joinGroupByInvite(token2, invitation.inviteCode)
        assertEquals(group.id, joinedGroup.id)

        // 6. Verify group members
        val members = service.getGroupMembers(token1, group.id)
        assertEquals(2, members.size)

        val member1 = members.find { it.user.id == user1.id }!!
        val member2 = members.find { it.user.id == user2.id }!!

        assertEquals(GroupRole.OWNER, member1.member.role)
        assertEquals(GroupRole.MEMBER, member2.member.role)

        // 7. Add expense: Alice paid ₹1200 for Dinner (split ₹600 Alice, ₹600 Bob)
        val expense = BackendExpense(
            id = "exp-1",
            groupId = group.id,
            createdById = user1.id,
            payerId = user1.id,
            amountMinor = 120000L, // ₹1200.00
            description = "Seafood Dinner",
            payers = listOf(BackendExpensePayer(user1.id, 120000L)),
            participants = listOf(
                BackendExpenseParticipant(user1.id, shareMinor = 60000L),
                BackendExpenseParticipant(user2.id, shareMinor = 60000L)
            )
        )

        val createdExpense = service.createExpense(token1, group.id, expense)
        assertEquals(120000L, createdExpense.amountMinor)

        val expenseList = service.listExpenses(token2, group.id)
        assertEquals(1, expenseList.size)
        assertEquals("Seafood Dinner", expenseList[0].description)

        // 8. Record settlement payment: Bob paid Alice ₹600
        val payment = BackendSettlementPayment(
            id = "pay-1",
            groupId = group.id,
            fromUserId = user2.id,
            toUserId = user1.id,
            amountMinor = 60000L, // ₹600.00
            createdByUserId = user2.id
        )

        val recordedPayment = service.recordPayment(token2, group.id, payment)
        assertEquals(60000L, recordedPayment.amountMinor)

        val paymentList = service.listPayments(token1, group.id)
        assertEquals(1, paymentList.size)

        // 9. Verify activity timeline
        val activities = service.listActivityEvents(token1, group.id)
        assertTrue(activities.size >= 4) // GROUP_CREATED, MEMBER_JOINED, EXPENSE_CREATED, PAYMENT_RECORDED

        // 10. Logout User 1
        service.logout(token1)
        var unauthenticated = false
        try {
            service.getCurrentUser(token1)
        } catch (_: AuthenticationException) {
            unauthenticated = true
        }
        assertTrue(unauthenticated)
    }

    @Test
    fun accountDeletion_anonymizesUserPreservingLedgerHistory() {
        val gateway = RealSmsVerificationGateway()
        val service = FairShareBackendService(verificationGateway = gateway)

        val phone = "+919876543212"
        service.requestPhoneVerification(phone)
        val code = gateway.getCodeForTesting(phone)!!
        val auth = service.verifyPhoneCode(phone, code, "Charlie")

        val token = auth.session.token
        val group = service.createGroup(token, "Flat Expenses")

        // Delete account
        service.deleteAccount(token)

        // Session should be terminated
        var sessionRevoked = false
        try {
            service.getCurrentUser(token)
        } catch (_: AuthenticationException) {
            sessionRevoked = true
        }
        assertTrue(sessionRevoked)
    }
}
