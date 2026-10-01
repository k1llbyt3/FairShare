package com.fairshare.android.core.network

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.common.FinancialEngine
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.Member
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.client.SessionStorage
import com.fairshare.android.core.network.repository.AuthRepository
import com.fairshare.android.core.network.repository.BackendGroupRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackendAndLocalReconciliationTest {

    private lateinit var db: FairShareDatabase
    private lateinit var gateway: RealSmsVerificationGateway
    private lateinit var backendService: FairShareBackendService
    private lateinit var sessionStorage: SessionStorage
    private lateinit var apiClient: FairShareApiClient
    private lateinit var authRepository: AuthRepository
    private lateinit var backendGroupRepository: BackendGroupRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = FairShareDatabase.createInMemory(context)
        gateway = RealSmsVerificationGateway()
        backendService = FairShareBackendService(verificationGateway = gateway)
        sessionStorage = SessionStorage()
        apiClient = FairShareApiClient(backendService, sessionStorage)
        authRepository = AuthRepository(apiClient, sessionStorage, db)
        backendGroupRepository = BackendGroupRepository(apiClient, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullReconciliation_backendToRoomToFinancialEngine() = runBlocking {
        // 1. User 1 logs in via API
        val phone1 = "+919876543210"
        authRepository.requestVerification(phone1)
        val code1 = gateway.getCodeForTesting(phone1)!!

        val authRes1 = authRepository.verifyCode(phone1, code1, "Rahul", "INR")
        assertTrue(authRes1 is NetworkResult.Success)
        val user1 = (authRes1 as NetworkResult.Success).data

        // Verify User 1 was persisted in Room
        val localUser1 = db.userDao().getUserById(user1.id)
        assertEquals("Rahul", localUser1?.displayName)

        // 2. User 1 creates group via backend repository
        val groupRes = backendGroupRepository.createGroup("Manali Trip 2026", "Himalayan trek", "TRIP", "INR")
        assertTrue(groupRes is NetworkResult.Success)
        val group = (groupRes as NetworkResult.Success).data

        // Verify group was mirrored in Room
        val localGroup = db.groupDao().getGroupById(group.id)
        assertEquals("Manali Trip 2026", localGroup?.name)

        // 3. User 2 logs in and joins via invite code
        val inviteRes = apiClient.createInvitation(group.id)
        assertTrue(inviteRes is NetworkResult.Success)
        val inviteCode = (inviteRes as NetworkResult.Success).data.inviteCode

        // Switch to User 2 session
        val sessionStorage2 = SessionStorage()
        val apiClient2 = FairShareApiClient(backendService, sessionStorage2)
        val authRepo2 = AuthRepository(apiClient2, sessionStorage2, db)
        val groupRepo2 = BackendGroupRepository(apiClient2, db)

        val phone2 = "+919876543222"
        authRepo2.requestVerification(phone2)
        val code2 = gateway.getCodeForTesting(phone2)!!
        val authRes2 = authRepo2.verifyCode(phone2, code2, "Sneha", "INR")
        assertTrue(authRes2 is NetworkResult.Success)
        val user2 = (authRes2 as NetworkResult.Success).data

        // User 2 joins group
        val joinRes = groupRepo2.joinGroupByInvite(inviteCode)
        assertTrue(joinRes is NetworkResult.Success)

        // Refresh members into Room
        val membersRes = backendGroupRepository.refreshGroupMembers(group.id)
        assertTrue(membersRes is NetworkResult.Success)
        val members = (membersRes as NetworkResult.Success).data
        assertEquals(2, members.size)

        // 4. Record expense in Room and calculate deterministic financial summary
        // Rahul paid ₹3,000 for Cabin (split equally: ₹1,500 Rahul, ₹1,500 Sneha)
        val expense = Expense(
            id = "exp-cabin",
            description = "Cabin Booking",
            totalAmount = Money(300000L), // ₹3,000.00
            payers = listOf(ExpensePayer(user1.id, Money(300000L))),
            participants = listOf(
                ExpenseParticipant(user1.id, exactAmount = Money(150000L)),
                ExpenseParticipant(user2.id, exactAmount = Money(150000L))
            ),
            timestamp = System.currentTimeMillis()
        )

        // Run Phase 2 financial engine
        val summary = FinancialEngine.calculateSummary(
            members = members,
            expenses = listOf(expense),
            payments = emptyList()
        )

        assertEquals(Money(300000L), summary.totalSpend)
        assertEquals(Money(150000L), summary.perPersonShare)

        // Rahul should be owed ₹1,500, Sneha should owe ₹1,500
        val rahulBalance = summary.balances.find { it.memberId == user1.id }!!.netBalance
        val snehaBalance = summary.balances.find { it.memberId == user2.id }!!.netBalance

        assertEquals(150000L, rahulBalance.paise)
        assertEquals(-150000L, snehaBalance.paise)

        // Settlement transfer: Sneha pays Rahul ₹1,500
        assertEquals(1, summary.settlements.size)
        val transfer = summary.settlements[0]
        assertEquals(user2.id, transfer.fromMemberId)
        assertEquals(user1.id, transfer.toMemberId)
        assertEquals(Money(150000L), transfer.amount)

        // 5. Record settlement payment of ₹1,500
        val payment = SettlementPayment(
            id = "pay-settle",
            fromMemberId = user2.id,
            toMemberId = user1.id,
            amount = Money(150000L),
            timestamp = System.currentTimeMillis()
        )

        val settledSummary = FinancialEngine.calculateSummary(
            members = members,
            expenses = listOf(expense),
            payments = listOf(payment)
        )

        // Now everyone is settled
        val rahulSettled = settledSummary.balances.find { it.memberId == user1.id }!!.netBalance
        val snehaSettled = settledSummary.balances.find { it.memberId == user2.id }!!.netBalance

        assertEquals(0L, rahulSettled.paise)
        assertEquals(0L, snehaSettled.paise)
        assertTrue(settledSummary.settlements.isEmpty())
    }
}
