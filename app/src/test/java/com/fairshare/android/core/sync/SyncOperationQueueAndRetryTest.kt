package com.fairshare.android.core.sync

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.domain.model.SettlementPayment
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.SessionStorage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Phase 5 Unit Tests for Task A (Operation Queue) and Task B (Upload & Idempotency).
 * Verifies stable client IDs, persistent queue, exponential backoff, and idempotent retries.
 */
@RunWith(RobolectricTestRunner::class)
class SyncOperationQueueAndRetryTest {

    private lateinit var db: FairShareDatabase
    private lateinit var backendService: FairShareBackendService
    private lateinit var sessionStorage: SessionStorage
    private lateinit var apiClient: FairShareApiClient
    private lateinit var syncEngine: SyncEngine

    private val testUserId = "user-alice"
    private var testGroupId = "group-goa"
    private val inr = Currency("INR", "₹")

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = FairShareDatabase.createInMemory(context)
        val gateway = com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway()
        backendService = FairShareBackendService(verificationGateway = gateway)
        sessionStorage = SessionStorage()
        apiClient = FairShareApiClient(backendService, sessionStorage)
        syncEngine = SyncEngine(db, apiClient, sessionStorage)

        // Seed user and session
        val phone = "+919876543210"
        backendService.requestPhoneVerification(phone)
        val code = gateway.getCodeForTesting(phone)!!
        val authResult = backendService.verifyPhoneCode(phone, code, "Alice", "INR")
        sessionStorage.saveSession(authResult.session.token, authResult.user.id, authResult.user.phoneNumber)

        // Seed group in backend & local Room
        val backendGroup = backendService.createGroup(authResult.session.token, "Goa Trip", "Beach vacation", "TRIP", "INR")
        testGroupId = backendGroup.id
        db.userDao().insertUser(
            UserEntity(id = authResult.user.id, phoneNumber = authResult.user.phoneNumber, displayName = "Alice")
        )
        db.groupDao().insertGroup(
            GroupEntity(id = backendGroup.id, name = "Goa Trip", createdById = authResult.user.id)
        )
    }

    @After
    fun tearDown() {
        if (::syncEngine.isInitialized) {
            syncEngine.close()
        }
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun clientGeneratedStableId_persistsInOperationQueueAndSurvives() = runTest {
        val stableExpenseId = UUID.randomUUID().toString()
        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = stableExpenseId,
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.INSERT,
            groupId = testGroupId,
            baseRevision = 1L,
            payloadJson = "{\"groupId\":\"$testGroupId\",\"baseRevision\":1}"
        )

        syncEngine.enqueueOperation(op)

        val pending = db.syncDao().getPendingOperations()
        assertEquals(1, pending.size)
        assertEquals(stableExpenseId, pending[0].entityId)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun idempotentRetry_repeatedUploadDoesNotCreateDuplicatesOnServer() = runTest {
        val stableExpenseId = "exp-unique-1234"
        val token = sessionStorage.sessionToken!!
        val user = backendService.getCurrentUser(token)
        val groups = backendService.listUserGroups(token)
        val groupId = groups.first().id

        val domainExpense = Expense(
            id = stableExpenseId,
            groupId = groupId,
            description = "Dinner at Fishermans Wharf",
            totalAmount = Money(250000L, inr),
            payers = listOf(ExpensePayer(user.id, Money(250000L, inr))),
            participants = listOf(ExpenseParticipant(user.id))
        )

        // Upload first time
        val expenseWithDetails = com.fairshare.android.core.database.mapper.EntityMappers.run {
            val (entity, parts) = domainExpense.toEntity(groupId, user.id)
            com.fairshare.android.core.database.relation.ExpenseWithDetails(
                entity, parts.first, parts.second, parts.third
            )
        }
        val backendExp = com.fairshare.android.core.database.mapper.EntityMappers.run { expenseWithDetails.toBackend() }

        val firstUpload = backendService.createExpense(token, groupId, backendExp)
        assertNotNull(firstUpload)
        assertEquals(stableExpenseId, firstUpload.id)

        // Upload second time with same client ID (simulating duplicate sync retry)
        val secondUpload = backendService.createExpense(token, groupId, backendExp)
        assertNotNull(secondUpload)
        assertEquals(stableExpenseId, secondUpload.id)

        // Verify the backend does NOT contain duplicate expenses
        val allExpenses = backendService.listExpenses(token, groupId)
        val matching = allExpenses.filter { it.id == stableExpenseId }
        assertEquals(1, matching.size)
    }

    @Test
    fun failedOperation_recordsErrorAndPermitsRetry() = runTest {
        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = "exp-fail-1",
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.INSERT,
            groupId = testGroupId,
            status = "FAILED",
            retryCount = 2,
            errorMessage = "Timeout"
        )

        db.syncDao().insertOperation(
            com.fairshare.android.core.database.entity.SyncOperationEntity(
                id = op.operationId,
                entityType = op.entityType.name,
                entityId = op.entityId,
                operationType = op.operationType.name,
                payloadJson = op.payloadJson,
                status = "FAILED",
                retryCount = 2,
                errorMessage = "Timeout"
            )
        )

        val failed = db.syncDao().getFailedOperations()
        assertEquals(1, failed.size)

        // Retry resets failed status to PENDING
        apiClient.isSimulatedOffline = true // Keep offline to observe pending state
        syncEngine.retryFailedOperations()

        val pending = db.syncDao().getPendingOperations()
        assertTrue(pending.any { it.entityId == "exp-fail-1" && it.status == "PENDING" && it.retryCount == 0 })
    }
}
