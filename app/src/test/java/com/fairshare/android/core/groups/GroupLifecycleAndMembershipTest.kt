package com.fairshare.android.core.groups

import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.domain.model.GroupType
import com.fairshare.android.core.model.Expense
import com.fairshare.android.core.model.Member
import com.fairshare.android.core.model.Money
import com.fairshare.android.core.model.Payment
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.backend.model.GroupRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class GroupLifecycleAndMembershipTest {

    private lateinit var backend: FairShareBackendService
    private lateinit var ownerUser: BackendUser
    private lateinit var memberUser: BackendUser
    private lateinit var thirdUser: BackendUser

    private var ownerToken: String = ""
    private var memberToken: String = ""
    private var thirdToken: String = ""

    @Before
    fun setUp() {
        val gateway = com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway()
        backend = FairShareBackendService(verificationGateway = gateway)

        // Create 3 real test users with phone auth
        backend.requestPhoneVerification("+919876543210")
        val auth1 = backend.verifyPhoneCode("+919876543210", gateway.getCodeForTesting("+919876543210")!!, "Owner User")
        ownerUser = auth1.user
        ownerToken = auth1.session.token

        backend.requestPhoneVerification("+919876543211")
        val auth2 = backend.verifyPhoneCode("+919876543211", gateway.getCodeForTesting("+919876543211")!!, "Member User")
        memberUser = auth2.user
        memberToken = auth2.session.token

        backend.requestPhoneVerification("+919876543212")
        val auth3 = backend.verifyPhoneCode("+919876543212", gateway.getCodeForTesting("+919876543212")!!, "Third User")
        thirdUser = auth3.user
        thirdToken = auth3.session.token
    }

    @Test
    fun testGroupCreationWithAllMetadataAndOwnerRole() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Goa Trip 2026",
            description = "Annual beach holiday",
            groupType = GroupType.TRIP.name,
            currency = "INR",
            startDate = 1790800000000L,
            endDate = 1791400000000L,
            timezone = "Asia/Kolkata"
        )

        assertNotNull(group.id)
        assertEquals("Goa Trip 2026", group.name)
        assertEquals("Annual beach holiday", group.description)
        assertEquals(GroupType.TRIP.name, group.groupType)
        assertEquals("INR", group.currency)
        assertEquals(1790800000000L, group.startDate)
        assertEquals(1791400000000L, group.endDate)
        assertEquals("Asia/Kolkata", group.timezone)
        assertEquals(ownerUser.id, group.createdById)
        assertFalse(group.isArchived)

        // Verify owner is added with OWNER role
        val members = backend.getGroupMembers(ownerToken, group.id)
        assertEquals(1, members.size)
        assertEquals(ownerUser.id, members[0].user.id)
        assertEquals(GroupRole.OWNER, members[0].member.role)
        assertFalse(members[0].member.isArchived)
    }

    @Test
    fun testInvitationLifecycleAndJoining() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Flat 402 Expenses",
            description = "Household bills",
            groupType = GroupType.HOUSEHOLD.name,
            currency = "INR"
        )

        // Create standard invitation
        val invite = backend.createInvitation(
            sessionToken = ownerToken,
            groupId = group.id
        )
        assertNotNull(invite.inviteCode)
        assertTrue(invite.inviteCode.startsWith("FAIR-") || invite.inviteCode.length == 9)
        assertFalse(invite.isRevoked)

        // Member joins with invite code
        val joinedGroup = backend.joinGroupByInvite(
            sessionToken = memberToken,
            inviteCode = invite.inviteCode
        )
        assertEquals(group.id, joinedGroup.id)

        // Verify roster has both users with proper roles
        val members = backend.getGroupMembers(ownerToken, group.id)
        assertEquals(2, members.size)

        val ownerMember = members.find { it.user.id == ownerUser.id }
        val regularMember = members.find { it.user.id == memberUser.id }

        assertNotNull(ownerMember)
        assertEquals(GroupRole.OWNER, ownerMember?.member?.role)

        assertNotNull(regularMember)
        assertEquals(GroupRole.MEMBER, regularMember?.member?.role)
        assertFalse(regularMember!!.member.isArchived)
    }

    @Test
    fun testTargetedPhoneInvitationValidation() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Office Lunch Club",
            description = "Daily team meals",
            groupType = GroupType.EVENT.name,
            currency = "INR"
        )

        // Create invitation targeted exclusively to thirdUser (+919876543212)
        val targetedInvite = backend.createInvitation(
            sessionToken = ownerToken,
            groupId = group.id,
            targetPhoneNumber = "+919876543212"
        )

        // memberUser (+919876543211) attempts to join with targeted invite -> should fail
        var joinFailed = false
        try {
            backend.joinGroupByInvite(
                sessionToken = memberToken,
                inviteCode = targetedInvite.inviteCode
            )
        } catch (e: Exception) {
            joinFailed = true
            assertTrue(e.message?.contains("phone number") == true || e.message?.contains("different") == true)
        }
        assertTrue("Mismatched phone number should be rejected", joinFailed)

        // thirdUser (+919876543212) joins -> should succeed
        val joined = backend.joinGroupByInvite(
            sessionToken = thirdToken,
            inviteCode = targetedInvite.inviteCode
        )
        assertEquals(group.id, joined.id)
    }

    @Test
    fun testRoleManagementPermissions() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Weekend Trek",
            description = "Camping trek",
            groupType = GroupType.TRIP.name,
            currency = "INR"
        )
        val invite = backend.createInvitation(ownerToken, group.id)
        backend.joinGroupByInvite(memberToken, invite.inviteCode)

        // Owner promotes member to ADMIN
        val updated = backend.updateMemberRole(ownerToken, group.id, memberUser.id, GroupRole.ADMIN)
        assertEquals(GroupRole.ADMIN, updated.role)

        // Member cannot demote OWNER
        var memberUnauthorized = false
        try {
            backend.updateMemberRole(memberToken, group.id, ownerUser.id, GroupRole.MEMBER)
        } catch (e: Exception) {
            memberUnauthorized = true
        }
        assertTrue("Non-owner cannot demote owner", memberUnauthorized)
    }

    @Test
    fun testHistoricalMemberPreservationWhenTransactionsExist() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Kashmir Tour",
            description = "Winter trip",
            groupType = GroupType.TRIP.name,
            currency = "INR"
        )
        val invite = backend.createInvitation(ownerToken, group.id)
        backend.joinGroupByInvite(memberToken, invite.inviteCode)

        // Add an expense involving memberUser
        val expenseId = UUID.randomUUID().toString()
        backend.createExpense(
            sessionToken = ownerToken,
            groupId = group.id,
            expense = BackendExpense(
                id = expenseId,
                groupId = group.id,
                createdById = ownerUser.id,
                payerId = ownerUser.id,
                amountMinor = 1000000L, // ₹10,000
                currency = "INR",
                description = "Hotel Stay",
                payers = listOf(BackendExpensePayer(ownerUser.id, 1000000L)),
                participants = listOf(
                    BackendExpenseParticipant(ownerUser.id, 500000L),
                    BackendExpenseParticipant(memberUser.id, 500000L)
                )
            )
        )

        // Remove memberUser
        backend.removeMember(ownerToken, group.id, memberUser.id)

        // Member should NOT be permanently deleted; they must be marked isArchived = true (FR-GRP-05)
        val allMembers = backend.getGroupMembers(ownerToken, group.id, includeArchived = true)
        val preservedMember = allMembers.find { it.user.id == memberUser.id }
        assertNotNull("Member must be preserved for historical ledger integrity", preservedMember)
        assertTrue("Preserved member must be marked isArchived = true", preservedMember!!.member.isArchived)

        // Active-only roster should not list the member
        val activeMembers = backend.getGroupMembers(ownerToken, group.id, includeArchived = false)
        assertNull("Active roster must exclude archived member", activeMembers.find { it.user.id == memberUser.id })

        // Re-joining with invite reactivates the member
        val reInvite = backend.createInvitation(ownerToken, group.id)
        backend.joinGroupByInvite(memberToken, reInvite.inviteCode)

        val reactivatedMembers = backend.getGroupMembers(ownerToken, group.id, includeArchived = false)
        val activeAgain = reactivatedMembers.find { it.user.id == memberUser.id }
        assertNotNull("Re-joining must reactivate the member", activeAgain)
        assertFalse(activeAgain!!.member.isArchived)
    }

    @Test
    fun testGroupArchiveAndUnarchiveLifecycle() {
        val group = backend.createGroup(
            sessionToken = ownerToken,
            name = "Completed Roadtrip",
            description = "Old trip",
            groupType = GroupType.TRIP.name,
            currency = "INR"
        )

        // Active groups list contains group
        val activeBefore = backend.listUserGroups(ownerToken, includeArchived = false)
        assertTrue(activeBefore.any { it.id == group.id })

        // Archive group
        val archived = backend.archiveGroup(ownerToken, group.id)
        assertTrue(archived.isArchived)

        // Active groups list does NOT contain archived group
        val activeAfter = backend.listUserGroups(ownerToken, includeArchived = false)
        assertFalse(activeAfter.any { it.id == group.id })

        // Full list with includeArchived = true contains it
        val allGroups = backend.listUserGroups(ownerToken, includeArchived = true)
        assertTrue(allGroups.any { it.id == group.id && it.isArchived })

        // Unarchive group
        val unarchived = backend.unarchiveGroup(ownerToken, group.id)
        assertFalse(unarchived.isArchived)

        val activeRestored = backend.listUserGroups(ownerToken, includeArchived = false)
        assertTrue(activeRestored.any { it.id == group.id && !it.isArchived })
    }

    @Test
    fun testGroupFinancialSummaryWithMembers() {
        val m1 = Member(id = "u1", name = "Alice", isCurrentUser = true)
        val m2 = Member(id = "u2", name = "Bob", isCurrentUser = false)
        val m3 = Member(id = "u3", name = "Charlie", isCurrentUser = false)
        val members = listOf(m1, m2, m3)

        val expenses = listOf(
            Expense(
                id = "e1",
                payerId = "u1",
                amount = Money.fromRupees(3000L), // ₹3000
                involvedMemberIds = listOf("u1", "u2", "u3"),
                description = "Dinner"
            )
        )

        val payments = listOf(
            Payment(
                id = "p1",
                fromMemberId = "u2",
                toMemberId = "u1",
                amount = Money.fromRupees(1000L) // ₹1000 paid by Bob to Alice
            )
        )

        val summary = FinancialEngine.calculateSummary(members, expenses, payments)

        // Total spend: ₹3000 (300000 paise)
        assertEquals(300000L, summary.totalSpend.paise)

        // Alice: paid ₹3000, share ₹1000, received ₹1000 -> net balance is +₹1000 (owed ₹1000 from Charlie)
        val aliceBal = summary.balances.find { it.memberId == "u1" }?.netBalance
        assertEquals(100000L, aliceBal?.paise)

        // Bob: paid ₹0, share ₹1000, paid settlement ₹1000 -> net balance is 0 (settled)
        val bobBal = summary.balances.find { it.memberId == "u2" }?.netBalance
        assertEquals(0L, bobBal?.paise)

        // Charlie: paid ₹0, share ₹1000, paid settlement ₹0 -> net balance is -₹1000 (owes Alice)
        val charlieBal = summary.balances.find { it.memberId == "u3" }?.netBalance
        assertEquals(-100000L, charlieBal?.paise)
    }
}
