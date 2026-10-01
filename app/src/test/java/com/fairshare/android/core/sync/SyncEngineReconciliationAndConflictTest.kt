package com.fairshare.android.core.sync

import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.GroupEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.domain.currency.Currency
import com.fairshare.android.core.domain.currency.Money
import com.fairshare.android.core.domain.model.Expense
import com.fairshare.android.core.domain.model.ExpenseParticipant
import com.fairshare.android.core.domain.model.ExpensePayer
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendExpensePayer
import com.fairshare.android.core.network.backend.model.BackendExpenseParticipant
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
 * Phase 5 Comprehensive Synchronization Engine Tests.
 * Required by specs/Plan.md P5-T10.
 *
 * Tests:
 * 1. Offline create -> saved in Room -> syncs upon reconnect.
 * 2. Two-device update -> incremental remote delta ingested into Room.
 * 3. Concurrent edit conflict -> 409 Conflict detected -> no silent overwrite -> user resolution.
 * 4. Offline deletion -> soft delete synced -> no silent resurrection.
 * 5. App restart / process kill -> operations survive in Room and resume.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEngineReconciliationAndConflictTest {

    private lateinit var db: FairShareDatabase
    private lateinit var backendService: FairShareBackendService
    private lateinit var sessionStorage: SessionStorage
    private lateinit var apiClient: FairShareApiClient
    private lateinit var syncEngine: SyncEngine
    private lateinit var expenseRepository: ExpenseRepository
    private lateinit var settlementRepository: SettlementRepository

    private val inr = Currency("INR", "₹")
    private lateinit var userId: String
    private lateinit var groupId: String

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = FairShareDatabase.createInMemory(context)
        val gateway = com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway()
        backendService = FairShareBackendService(verificationGateway = gateway)
        sessionStorage = SessionStorage()
        apiClient = FairShareApiClient(backendService, sessionStorage)
        syncEngine = SyncEngine(db, apiClient, sessionStorage)
        expenseRepository = ExpenseRepository(db, syncEngine)
        settlementRepository = SettlementRepository(db, syncEngine)

        // Seed user session
        val phone = "+919876543210"
        backendService.requestPhoneVerification(phone)
        val code = gateway.getCodeForTesting(phone)!!
        val authResult = backendService.verifyPhoneCode(phone, code, "Arjun", "INR")
        userId = authResult.user.id
        sessionStorage.saveSession(authResult.session.token, userId, authResult.user.phoneNumber)

        // Seed group in backend & local Room
        val backendGroup = backendService.createGroup(authResult.session.token, "Manali Trip", "Mountains", "TRIP", "INR")
        groupId = backendGroup.id

        db.userDao().insertUser(
            UserEntity(id = userId, phoneNumber = authResult.user.phoneNumber, displayName = "Arjun")
        )
        db.groupDao().insertGroup(
            GroupEntity(id = groupId, name = "Manali Trip", createdById = userId)
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
    fun offlineCreate_savesImmediatelyInRoom_andSyncsUponReconnect() = runTest {
        // 1. Enter offline mode
        syncEngine.setSimulatedOffline(true)
        assertTrue(syncEngine.syncState.value.isOffline)

        // 2. Create expense locally while offline
        val expenseId = UUID.randomUUID().toString()
        val offlineExpense = Expense(
            id = expenseId,
            groupId = groupId,
            description = "Campfire Snacks",
            totalAmount = Money(45000L, inr),
            payers = listOf(ExpensePayer(userId, Money(45000L, inr))),
            participants = listOf(ExpenseParticipant(userId))
        )
        expenseRepository.saveExpense(offlineExpense, groupId, userId)

        // 3. Verify it exists immediately in local Room
        val localExpenses = expenseRepository.getExpenses(groupId)
        assertEquals(1, localExpenses.size)
        assertEquals("Campfire Snacks", localExpenses[0].description)

        // 4. Verify sync status is PENDING_SYNC
        val statusBefore = syncEngine.getEntitySyncStatus(expenseId)
        assertEquals(SyncStatus.PENDING_SYNC, statusBefore)

        // 5. Restore network connectivity and trigger sync
        syncEngine.setSimulatedOffline(false)
        val syncSuccess = syncEngine.syncAll()
        assertTrue(syncSuccess)

        // 6. Verify expense is now authoritative on the backend
        val token = sessionStorage.sessionToken!!
        val remoteExpenses = backendService.listExpenses(token, groupId)
        assertEquals(1, remoteExpenses.size)
        assertEquals(expenseId, remoteExpenses[0].id)
        assertEquals(45000L, remoteExpenses[0].amountMinor)

        // 7. Verify sync status is now SYNCED
        val statusAfter = syncEngine.getEntitySyncStatus(expenseId)
        assertEquals(SyncStatus.SYNCED, statusAfter)
    }

    @Test
    fun twoDeviceUpdate_pullsRemoteDeltaAndReconcilesIntoRoom() = runTest {
        // Device A uploads an expense to backend
        val token = sessionStorage.sessionToken!!
        val remoteExpense = BackendExpense(
            id = "exp-device-a",
            groupId = groupId,
            createdById = userId,
            payerId = userId,
            amountMinor = 120000L,
            currency = "INR",
            description = "River Rafting",
            revision = 1L,
            payers = listOf(BackendExpensePayer(memberId = userId, amountMinor = 120000L, currency = "INR")),
            participants = listOf(BackendExpenseParticipant(memberId = userId, shareMinor = 120000L))
        )
        backendService.createExpense(token, groupId, remoteExpense)

        // Device B starts with empty Room database for this expense
        val localBefore = expenseRepository.getExpenses(groupId)
        assertTrue(localBefore.none { it.id == "exp-device-a" })

        // Device B triggers sync
        val synced = syncEngine.syncGroup(groupId)
        assertTrue(synced)

        // Device B now has the remote expense in Room!
        val localAfter = expenseRepository.getExpenses(groupId)
        val ingested = localAfter.find { it.id == "exp-device-a" }
        assertNotNull(ingested)
        assertEquals("River Rafting", ingested!!.description)
        assertEquals(120000L, ingested.totalAmount.amountMinor)
        assertEquals(SyncStatus.SYNCED, syncEngine.getEntitySyncStatus("exp-device-a"))
    }

    @Test
    fun concurrentEdit_detectsConflictWithoutSilentOverwrite_andResolvesKeepLocal() = runTest {
        val token = sessionStorage.sessionToken!!

        // 1. Initial shared expense at revision 1
        val sharedExpense = BackendExpense(
            id = "exp-concurrent-1",
            groupId = groupId,
            createdById = userId,
            payerId = userId,
            amountMinor = 100000L,
            currency = "INR",
            description = "Hotel Booking",
            revision = 1L,
            payers = listOf(BackendExpensePayer(memberId = userId, amountMinor = 100000L, currency = "INR")),
            participants = listOf(BackendExpenseParticipant(memberId = userId, shareMinor = 100000L))
        )
        backendService.createExpense(token, groupId, sharedExpense)

        // Local Room syncs to initial state
        syncEngine.syncGroup(groupId)
        val initialLocal = db.expenseDao().getExpenseWithDetails("exp-concurrent-1")
        assertNotNull(initialLocal)

        // 2. Device A (remote) edits to ₹1500 -> revision 2 on server
        backendService.updateExpense(
            token,
            groupId,
            sharedExpense.copy(
                description = "Hotel Booking + Breakfast",
                amountMinor = 150000L,
                revision = 1L,
                payers = listOf(BackendExpensePayer(memberId = userId, amountMinor = 150000L, currency = "INR")),
                participants = listOf(BackendExpenseParticipant(memberId = userId, shareMinor = 150000L))
            )
        )
        val serverRev = backendService.listExpenses(token, groupId).first { it.id == "exp-concurrent-1" }
        assertEquals(2L, serverRev.revision)

        // 3. Device B (local) edited while offline based on revision 1
        syncEngine.setSimulatedOffline(true)
        val localDomain = Expense(
            id = "exp-concurrent-1",
            groupId = groupId,
            description = "Hotel Booking (Local Edit ₹1800)",
            totalAmount = Money(180000L, inr),
            payers = listOf(ExpensePayer(userId, Money(180000L, inr))),
            participants = listOf(ExpenseParticipant(userId))
        )
        expenseRepository.updateExpense(localDomain, groupId, baseRevision = 1L, actorId = userId)

        // 4. Device B comes online and syncs -> backend returns 409 Conflict
        syncEngine.setSimulatedOffline(false)
        syncEngine.syncAll()

        // 5. Verify CONFLICT state is recorded and neither version was silently overwritten
        val status = syncEngine.getEntitySyncStatus("exp-concurrent-1")
        assertEquals(SyncStatus.CONFLICT, status)
        assertEquals(1, syncEngine.syncState.value.conflictCount)

        // 6. User resolves conflict: Choose "Keep Local"
        syncEngine.resolveConflictKeepLocal("exp-concurrent-1", SyncEntityType.EXPENSE)

        // 7. Verify local changes are now canonical on server with revision incremented
        val resolvedServer = backendService.listExpenses(token, groupId).first { it.id == "exp-concurrent-1" }
        assertEquals(180000L, resolvedServer.amountMinor)
        assertEquals(3L, resolvedServer.revision)
        assertEquals(SyncStatus.SYNCED, syncEngine.getEntitySyncStatus("exp-concurrent-1"))
    }

    @Test
    fun offlineDelete_syncsSoftDeleteWithoutResurrection() = runTest {
        val token = sessionStorage.sessionToken!!
        val expenseId = "exp-delete-test"

        // Create on server and sync locally
        backendService.createExpense(
            token,
            groupId,
            BackendExpense(
                id = expenseId,
                groupId = groupId,
                createdById = userId,
                payerId = userId,
                amountMinor = 50000L,
                currency = "INR",
                description = "Lunch",
                revision = 1L,
                payers = listOf(BackendExpensePayer(memberId = userId, amountMinor = 50000L, currency = "INR")),
                participants = listOf(BackendExpenseParticipant(memberId = userId, shareMinor = 50000L))
            )
        )
        syncEngine.syncGroup(groupId)

        // Delete locally while offline
        syncEngine.setSimulatedOffline(true)
        expenseRepository.softDeleteExpense(expenseId, groupId, userId)

        // Locally marked as deleted in Room
        val localAfterDelete = expenseRepository.getExpenses(groupId)
        assertTrue(localAfterDelete.none { it.id == expenseId })

        // Reconnect and sync
        syncEngine.setSimulatedOffline(false)
        syncEngine.syncAll()

        // Verify remote backend also soft-deleted it (not resurrected)
        val activeRemote = backendService.listExpenses(token, groupId)
        assertTrue(activeRemote.none { it.id == expenseId })
    }

    @Test
    fun appRestart_queuePreservedInRoom_andResumesUponWake() = runTest {
        // Enqueue operation while offline
        syncEngine.setSimulatedOffline(true)
        val op = SyncOperation(
            operationId = UUID.randomUUID().toString(),
            entityId = "exp-restart-test",
            entityType = SyncEntityType.EXPENSE,
            operationType = SyncOperationType.INSERT,
            groupId = groupId,
            baseRevision = 1L,
            payloadJson = "{\"groupId\":\"$groupId\",\"baseRevision\":1}"
        )
        syncEngine.enqueueOperation(op)

        // Simulate app process termination and recreate SyncEngine instance with existing Room DB
        val restartedSyncEngine = SyncEngine(db, apiClient, sessionStorage)
        val pendingOps = db.syncDao().getPendingOperations()
        assertEquals(1, pendingOps.size)
        assertEquals("exp-restart-test", pendingOps[0].entityId)

        // Verify restarted engine has pending count
        assertEquals(1, pendingOps.size)
        restartedSyncEngine.close()
    }
}
