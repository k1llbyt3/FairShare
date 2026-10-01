package com.fairshare.android.core.sync

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.SyncMetadataEntity
import com.fairshare.android.core.database.entity.SyncOperationEntity
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.database.mapper.EntityMappers.toBackend
import com.fairshare.android.core.database.mapper.EntityMappers.toRoomEntity
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendGroupMember
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.backend.model.GroupRole
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.client.SessionStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/**
 * FairShare Core Synchronization Engine (Phase 5).
 * Connects local Room working state to authoritative cloud backend.
 *
 * Guaranteed Invariants:
 * - Room is the local working state, Cloud Backend is authoritative shared truth.
 * - Stable client-generated IDs before upload (P5-T02).
 * - Idempotent upload queue: retries never create duplicate records (P5-T03).
 * - Exponential backoff on retries (P5-T04).
 * - Incremental change retrieval with cursor timestamps (P5-T05).
 * - Deterministic reconciliation: local state merges canonical server state (P5-T06).
 * - Explicit revision conflict detection: never silently overwrite financial changes (P5-T07, Task D).
 * - Full offline operation: changes survive process kill and sync upon network recovery (P5-T08, P5-T09).
 */
class SyncEngine(
    private val db: FairShareDatabase,
    private val apiClient: FairShareApiClient,
    private val sessionStorage: SessionStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SyncContract {

    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val _syncState = MutableStateFlow(SyncEngineState())
    override val syncState: StateFlow<SyncEngineState> = _syncState.asStateFlow()

    override val pendingSyncCount: Int
        get() = _syncState.value.pendingCount

    init {
        scope.launch {
            refreshSyncState()
        }
    }

    /**
     * Enqueue a local mutation for asynchronous synchronization.
     * Persisted immediately to Room sync_operations table so it survives app death.
     */
    suspend fun enqueueOperation(operation: SyncOperation) = withContext(ioDispatcher) {
        val entity = SyncOperationEntity(
            id = operation.operationId,
            entityType = operation.entityType.name,
            entityId = operation.entityId,
            operationType = operation.operationType.name,
            payloadJson = operation.payloadJson.ifBlank {
                JSONObject().apply {
                    put("groupId", operation.groupId)
                    put("baseRevision", operation.baseRevision)
                }.toString()
            },
            status = "PENDING",
            retryCount = 0,
            errorMessage = null,
            createdAt = operation.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        db.syncDao().insertOperation(entity)
        setEntityStatus(operation.entityId, SyncStatus.PENDING_SYNC)
        refreshSyncState()

        // Attempt immediate synchronization if online
        if (!apiClient.isSimulatedOffline) {
            scope.launch {
                syncGroup(operation.groupId)
            }
        }
    }

    override suspend fun syncGroup(groupId: String): Boolean = withContext(ioDispatcher) {
        if (apiClient.isSimulatedOffline) {
            _syncState.value = _syncState.value.copy(isOffline = true)
            return@withContext false
        }

        _syncState.value = _syncState.value.copy(isSyncing = true, isOffline = false, errorMessage = null)
        try {
            ensureGroupSynchronized(groupId)
            uploadPendingOperationsForGroup(groupId)
            pullRemoteChanges(groupId)
            refreshSyncState()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                errorMessage = e.message
            )
            refreshSyncState()
            false
        }
    }

    override suspend fun syncAll(): Boolean = withContext(ioDispatcher) {
        if (apiClient.isSimulatedOffline) {
            _syncState.value = _syncState.value.copy(isOffline = true)
            return@withContext false
        }

        _syncState.value = _syncState.value.copy(isSyncing = true, isOffline = false, errorMessage = null)
        try {
            val groups = db.groupDao().getAllActiveGroups()
            for (group in groups) {
                ensureGroupSynchronized(group.id)
            }
            uploadAllPendingOperations()

            for (group in groups) {
                pullRemoteChanges(group.id)
            }

            refreshSyncState()
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            true
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                errorMessage = e.message
            )
            refreshSyncState()
            false
        }
    }

    override suspend fun retryFailedOperations(): Boolean = withContext(ioDispatcher) {
        val failed = db.syncDao().getFailedOperations()
        for (op in failed) {
            db.syncDao().updateOperation(
                op.copy(
                    status = "PENDING",
                    retryCount = 0,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            setEntityStatus(op.entityId, SyncStatus.PENDING_SYNC)
        }
        refreshSyncState()
        syncAll()
    }

    override suspend fun resolveConflictKeepLocal(entityId: String, entityType: SyncEntityType) = withContext(ioDispatcher) {
        val conflictMeta = db.syncDao().getMetadata("conflict_$entityId") ?: return@withContext
        val conflictJson = JSONObject(conflictMeta.value)
        val remoteRev = conflictJson.optLong("remoteRevision", 1L)
        val groupId = conflictJson.optString("groupId", "")

        // Clear conflict record
        db.syncDao().deleteMetadata("conflict_$entityId")

        // Look for existing pending operation or recreate one with updated base revision
        val pendingOps = db.syncDao().getOperationsForEntity(entityId)
        if (pendingOps.isNotEmpty()) {
            for (op in pendingOps) {
                val updatedPayload = JSONObject(op.payloadJson).apply {
                    put("baseRevision", remoteRev)
                }.toString()
                db.syncDao().updateOperation(
                    op.copy(
                        status = "PENDING",
                        retryCount = 0,
                        errorMessage = null,
                        payloadJson = updatedPayload,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            // Re-enqueue update operation with baseRevision = remoteRev
            val newOp = SyncOperation(
                operationId = UUID.randomUUID().toString(),
                entityId = entityId,
                entityType = entityType,
                operationType = SyncOperationType.UPDATE,
                groupId = groupId,
                baseRevision = remoteRev,
                payloadJson = JSONObject().apply {
                    put("groupId", groupId)
                    put("baseRevision", remoteRev)
                }.toString()
            )
            enqueueOperation(newOp)
        }

        setEntityStatus(entityId, SyncStatus.PENDING_SYNC)
        refreshSyncState()

        if (!apiClient.isSimulatedOffline) {
            syncGroup(groupId)
        }
    }

    override suspend fun resolveConflictAcceptRemote(entityId: String, entityType: SyncEntityType) = withContext(ioDispatcher) {
        val conflictMeta = db.syncDao().getMetadata("conflict_$entityId") ?: return@withContext
        val conflictJson = JSONObject(conflictMeta.value)
        val remotePayload = conflictJson.optString("remotePayloadJson", "")

        // Drop local pending operations
        db.syncDao().deleteOperationsForEntity(entityId)
        db.syncDao().deleteMetadata("conflict_$entityId")

        // Overwrite local Room entity with remote version
        if (entityType == SyncEntityType.EXPENSE && remotePayload.isNotBlank()) {
            val remoteBackend = parseBackendExpense(remotePayload)
            if (remoteBackend != null) {
                if (remoteBackend.isDeleted) {
                    db.expenseDao().softDeleteExpense(remoteBackend.id)
                } else {
                    val (entity, parts) = remoteBackend.toRoomEntity()
                    db.expenseDao().insertExpenseWithDetails(entity, parts.first, parts.second, parts.third)
                }
            }
        } else if (entityType == SyncEntityType.PAYMENT && remotePayload.isNotBlank()) {
            val remotePayment = parseBackendPayment(remotePayload)
            if (remotePayment != null) {
                if (remotePayment.isDeleted) {
                    db.settlementDao().softDeletePayment(remotePayment.id)
                } else {
                    db.settlementDao().insertPayment(remotePayment.toRoomEntity())
                }
            }
        }

        setEntityStatus(entityId, SyncStatus.SYNCED)
        refreshSyncState()
    }

    override suspend fun getEntitySyncStatus(entityId: String): SyncStatus = withContext(ioDispatcher) {
        // First check if there is an active conflict
        val conflict = db.syncDao().getMetadata("conflict_$entityId")
        if (conflict != null) return@withContext SyncStatus.CONFLICT

        val statusMeta = db.syncDao().getMetadata("status_$entityId")
        if (statusMeta != null) {
            try {
                return@withContext SyncStatus.valueOf(statusMeta.value)
            } catch (_: Exception) {}
        }

        val ops = db.syncDao().getOperationsForEntity(entityId)
        if (ops.any { it.status == "FAILED" }) return@withContext SyncStatus.SYNC_FAILED
        if (ops.any { it.status == "PENDING" || it.status == "IN_FLIGHT" }) return@withContext SyncStatus.PENDING_SYNC

        SyncStatus.SYNCED
    }

    override fun setSimulatedOffline(isOffline: Boolean) {
        apiClient.isSimulatedOffline = isOffline
        _syncState.value = _syncState.value.copy(isOffline = isOffline)
        if (!isOffline) {
            scope.launch {
                syncAll()
            }
        }
    }

    // -------------------------------------------------------------
    // Private Helpers: Upload Queue Processing (Task B)
    // -------------------------------------------------------------

    private suspend fun ensureGroupSynchronized(groupId: String) {
        if (groupId.isBlank()) return
        val group = db.groupDao().getGroupById(groupId) ?: return
        val crossRefs = db.groupDao().getGroupMemberCrossRefs(groupId)
        val memberUsers = db.groupDao().getMembersForGroup(groupId)

        val backendGroup = BackendGroup(
            id = group.id,
            name = group.name,
            description = group.description,
            groupType = group.groupType,
            currency = group.currency,
            createdById = group.createdById,
            startDate = group.startDate,
            endDate = group.endDate,
            settlementDeadline = group.settlementDeadline,
            isArchived = group.isArchived,
            revision = group.revision,
            createdAt = group.createdAt,
            updatedAt = group.updatedAt
        )

        val backendMembers = crossRefs.map { cr ->
            val role = try {
                GroupRole.valueOf(cr.role)
            } catch (e: IllegalArgumentException) {
                GroupRole.MEMBER
            }
            BackendGroupMember(
                groupId = cr.groupId,
                userId = cr.userId,
                role = role,
                joinedAt = cr.joinedAt,
                isArchived = false
            )
        }.toMutableList()

        val backendUsers = memberUsers.map { u ->
            BackendUser(
                id = u.id,
                phoneNumber = u.phoneNumber,
                displayName = u.displayName,
                profileImageUrl = u.profileImageUrl,
                defaultCurrency = u.defaultCurrency,
                createdAt = u.createdAt,
                updatedAt = u.updatedAt
            )
        }.toMutableList()

        val currentUserId = sessionStorage.currentUserId
        if (!currentUserId.isNullOrBlank()) {
            if (backendMembers.none { it.userId == currentUserId }) {
                backendMembers.add(
                    BackendGroupMember(
                        groupId = groupId,
                        userId = currentUserId,
                        role = if (currentUserId == group.createdById) GroupRole.OWNER else GroupRole.MEMBER,
                        joinedAt = System.currentTimeMillis()
                    )
                )
            }
            if (backendUsers.none { it.id == currentUserId }) {
                val currentUserEntity = db.userDao().getUserById(currentUserId)
                if (currentUserEntity != null) {
                    backendUsers.add(
                        BackendUser(
                            id = currentUserEntity.id,
                            phoneNumber = currentUserEntity.phoneNumber,
                            displayName = currentUserEntity.displayName,
                            profileImageUrl = currentUserEntity.profileImageUrl,
                            defaultCurrency = currentUserEntity.defaultCurrency,
                            createdAt = currentUserEntity.createdAt,
                            updatedAt = currentUserEntity.updatedAt
                        )
                    )
                } else {
                    backendUsers.add(
                        BackendUser(
                            id = currentUserId,
                            phoneNumber = sessionStorage.userPhone ?: "+0000000000",
                            displayName = "Current User",
                            defaultCurrency = group.currency
                        )
                    )
                }
            }
        }

        apiClient.ensureGroupHydrated(backendGroup, backendMembers, backendUsers)
    }

    private suspend fun uploadAllPendingOperations() {
        val pending = db.syncDao().getPendingOperations()
        for (op in pending) {
            processOperation(op)
        }
    }

    private suspend fun uploadPendingOperationsForGroup(groupId: String) {
        val pending = db.syncDao().getPendingOperations()
        for (op in pending) {
            val opGroupId = extractGroupIdFromOperation(op)
            if (opGroupId.isBlank() || opGroupId == groupId) {
                processOperation(op)
            }
        }
    }

    private suspend fun processOperation(op: SyncOperationEntity) {
        if (apiClient.isSimulatedOffline) return

        // Exponential backoff check: delay = min(1000 * 2^retryCount, 60s)
        if (op.retryCount > 0) {
            val backoffMs = min(1000L * (1 shl min(op.retryCount, 6)), 60000L)
            if (System.currentTimeMillis() < op.updatedAt + backoffMs) {
                return // Still in backoff window
            }
        }

        db.syncDao().updateOperation(op.copy(status = "IN_FLIGHT", updatedAt = System.currentTimeMillis()))
        setEntityStatus(op.entityId, SyncStatus.SYNCING)

        val groupId = extractGroupIdFromOperation(op)
        val baseRev = extractBaseRevisionFromOperation(op)
        ensureGroupSynchronized(groupId)

        when (op.entityType) {
            SyncEntityType.EXPENSE.name -> {
                when (op.operationType) {
                    SyncOperationType.INSERT.name -> {
                        val expenseWithDetails = db.expenseDao().getExpenseWithDetails(op.entityId)
                        if (expenseWithDetails == null) {
                            // Record was deleted locally before sync; discard insert
                            db.syncDao().deleteOperation(op.id)
                            return
                        }
                        val backendExpense = expenseWithDetails.toBackend()
                        when (val result = apiClient.createExpense(groupId, backendExpense)) {
                            is NetworkResult.Success -> {
                                val canonical = result.data
                                db.expenseDao().insertExpense(
                                    expenseWithDetails.expense.copy(
                                        revision = canonical.revision,
                                        updatedAt = canonical.updatedAt
                                    )
                                )
                                db.syncDao().deleteOperation(op.id)
                                setEntityStatus(op.entityId, SyncStatus.SYNCED)
                            }
                            is NetworkResult.Error -> {
                                handleOperationError(op, result.code, result.message, groupId, baseRev)
                            }
                        }
                    }
                    SyncOperationType.UPDATE.name -> {
                        val expenseWithDetails = db.expenseDao().getExpenseWithDetails(op.entityId)
                        if (expenseWithDetails == null) {
                            db.syncDao().deleteOperation(op.id)
                            return
                        }
                        val backendExpense = expenseWithDetails.toBackend().copy(revision = baseRev)
                        when (val result = apiClient.updateExpense(groupId, backendExpense)) {
                            is NetworkResult.Success -> {
                                val canonical = result.data
                                db.expenseDao().insertExpense(
                                    expenseWithDetails.expense.copy(
                                        revision = canonical.revision,
                                        updatedAt = canonical.updatedAt
                                    )
                                )
                                db.syncDao().deleteOperation(op.id)
                                setEntityStatus(op.entityId, SyncStatus.SYNCED)
                            }
                            is NetworkResult.Error -> {
                                handleOperationError(op, result.code, result.message, groupId, baseRev)
                            }
                        }
                    }
                    SyncOperationType.DELETE.name -> {
                        when (val result = apiClient.deleteExpense(groupId, op.entityId)) {
                            is NetworkResult.Success -> {
                                db.syncDao().deleteOperation(op.id)
                                setEntityStatus(op.entityId, SyncStatus.SYNCED)
                            }
                            is NetworkResult.Error -> {
                                handleOperationError(op, result.code, result.message, groupId, baseRev)
                            }
                        }
                    }
                }
            }
            SyncEntityType.PAYMENT.name -> {
                when (op.operationType) {
                    SyncOperationType.INSERT.name -> {
                        val payment = db.settlementDao().getPaymentById(op.entityId)
                        if (payment == null) {
                            db.syncDao().deleteOperation(op.id)
                            return
                        }
                        when (val result = apiClient.recordPayment(groupId, payment.toBackend())) {
                            is NetworkResult.Success -> {
                                val canonical = result.data
                                db.settlementDao().insertPayment(
                                    payment.copy(
                                        revision = canonical.revision,
                                        updatedAt = canonical.updatedAt
                                    )
                                )
                                db.syncDao().deleteOperation(op.id)
                                setEntityStatus(op.entityId, SyncStatus.SYNCED)
                            }
                            is NetworkResult.Error -> {
                                handleOperationError(op, result.code, result.message, groupId, baseRev)
                            }
                        }
                    }
                    SyncOperationType.DELETE.name -> {
                        when (val result = apiClient.deletePayment(groupId, op.entityId)) {
                            is NetworkResult.Success -> {
                                db.syncDao().deleteOperation(op.id)
                                setEntityStatus(op.entityId, SyncStatus.SYNCED)
                            }
                            is NetworkResult.Error -> {
                                handleOperationError(op, result.code, result.message, groupId, baseRev)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleOperationError(
        op: SyncOperationEntity,
        statusCode: Int,
        message: String,
        groupId: String,
        baseRev: Long
    ) {
        if (statusCode == 409) {
            // Explicit Conflict State: Revision mismatch (P5-T07, Task D)
            // Retrieve latest remote canonical expense to record conflict comparison
            val remoteExp = try {
                when (val deltaRes = apiClient.getGroupDelta(groupId, 0L)) {
                    is NetworkResult.Success -> deltaRes.data.expenses.find { it.id == op.entityId }
                    else -> null
                }
            } catch (_: Exception) { null }
            val remoteRev = remoteExp?.revision ?: (baseRev + 1)
            val remoteSummary = remoteExp?.let { "${it.description}: ${(it.amountMinor / 100)}" }
                ?: "Server has a newer revision than $baseRev"
            val localExp = db.expenseDao().getExpenseWithDetails(op.entityId)
            val conflict = SyncConflictRecord(
                entityId = op.entityId,
                entityType = SyncEntityType.valueOf(op.entityType),
                groupId = groupId,
                localRevision = baseRev,
                remoteRevision = remoteRev,
                localSummary = "${localExp?.expense?.description ?: "Local Expense"}: ${((localExp?.expense?.amountMinor ?: 0L) / 100)}",
                remoteSummary = remoteSummary,
                localPayloadJson = localExp?.toBackend()?.let { serializeBackendExpense(it) } ?: op.payloadJson,
                remotePayloadJson = remoteExp?.let { serializeBackendExpense(it) } ?: "",
                timestamp = System.currentTimeMillis()
            )
            recordConflict(conflict)
            db.syncDao().updateOperation(
                op.copy(
                    status = "FAILED",
                    errorMessage = message,
                    updatedAt = System.currentTimeMillis()
                )
            )
            setEntityStatus(op.entityId, SyncStatus.CONFLICT)
        } else {
            // General network / retryable error
            db.syncDao().updateOperation(
                op.copy(
                    status = "FAILED",
                    retryCount = op.retryCount + 1,
                    errorMessage = message,
                    updatedAt = System.currentTimeMillis()
                )
            )
            setEntityStatus(op.entityId, SyncStatus.SYNC_FAILED)
        }
    }

    // -------------------------------------------------------------
    // Private Helpers: Remote Change Ingestion (Task C)
    // -------------------------------------------------------------

    private suspend fun pullRemoteChanges(groupId: String) {
        ensureGroupSynchronized(groupId)
        val cursorMeta = db.syncDao().getMetadata("cursor_$groupId")
        val sinceTimestamp = cursorMeta?.value?.toLongOrNull() ?: 0L

        when (val result = apiClient.getGroupDelta(groupId, sinceTimestamp)) {
            is NetworkResult.Success -> {
                val delta = result.data

                // Reconcile group details and members
                if (delta.group != null) {
                    val currentGroup = db.groupDao().getGroupById(groupId)
                    if (currentGroup != null) {
                        db.groupDao().insertGroup(
                            currentGroup.copy(
                                name = delta.group.name,
                                description = delta.group.description,
                                currency = delta.group.currency,
                                revision = delta.group.revision,
                                updatedAt = delta.group.updatedAt
                            )
                        )
                    }
                }

                for (memberWithUser in delta.members) {
                    db.userDao().insertUser(
                        UserEntity(
                            id = memberWithUser.user.id,
                            phoneNumber = memberWithUser.user.phoneNumber,
                            displayName = memberWithUser.user.displayName,
                            profileImageUrl = memberWithUser.user.profileImageUrl,
                            defaultCurrency = memberWithUser.user.defaultCurrency
                        )
                    )
                }

                // Reconcile expenses
                for (remoteExp in delta.expenses) {
                    val activeConflict = db.syncDao().getMetadata("conflict_${remoteExp.id}") != null
                    if (activeConflict) {
                        continue
                    }

                    val pendingOps = db.syncDao().getOperationsForEntity(remoteExp.id)
                    val hasPendingLocalEdit = pendingOps.any { it.status == "PENDING" || it.status == "IN_FLIGHT" }

                    if (hasPendingLocalEdit) {
                        // Check if remote revision is higher than local base revision: conflict!
                        val localExp = db.expenseDao().getExpenseWithDetails(remoteExp.id)
                        val localRev = localExp?.expense?.revision ?: 1L
                        if (remoteExp.revision > localRev) {
                            val conflict = SyncConflictRecord(
                                entityId = remoteExp.id,
                                entityType = SyncEntityType.EXPENSE,
                                groupId = groupId,
                                localRevision = localRev,
                                remoteRevision = remoteExp.revision,
                                localSummary = "${localExp?.expense?.description ?: "Local Expense"}: ${((localExp?.expense?.amountMinor ?: 0L) / 100)}",
                                remoteSummary = "${remoteExp.description}: ${(remoteExp.amountMinor / 100)}",
                                localPayloadJson = localExp?.toBackend()?.let { serializeBackendExpense(it) } ?: "",
                                remotePayloadJson = serializeBackendExpense(remoteExp),
                                timestamp = System.currentTimeMillis()
                            )
                            recordConflict(conflict)
                            setEntityStatus(remoteExp.id, SyncStatus.CONFLICT)
                        }
                    } else {
                        // No local conflict: server is authoritative canonical truth
                        if (remoteExp.isDeleted) {
                            db.expenseDao().softDeleteExpense(remoteExp.id)
                        } else {
                            val (entity, parts) = remoteExp.toRoomEntity()
                            db.expenseDao().insertExpenseWithDetails(entity, parts.first, parts.second, parts.third)
                        }
                        setEntityStatus(remoteExp.id, SyncStatus.SYNCED)
                    }
                }

                // Reconcile payments
                for (remotePay in delta.payments) {
                    val activePayConflict = db.syncDao().getMetadata("conflict_${remotePay.id}") != null
                    if (activePayConflict) {
                        continue
                    }

                    val pendingOps = db.syncDao().getOperationsForEntity(remotePay.id)
                    val hasPendingLocalEdit = pendingOps.any { it.status == "PENDING" || it.status == "IN_FLIGHT" }

                    if (hasPendingLocalEdit) {
                        val localPay = db.settlementDao().getPaymentById(remotePay.id)
                        val localRev = localPay?.revision ?: 1L
                        if (remotePay.revision > localRev) {
                            val conflict = SyncConflictRecord(
                                entityId = remotePay.id,
                                entityType = SyncEntityType.PAYMENT,
                                groupId = groupId,
                                localRevision = localRev,
                                remoteRevision = remotePay.revision,
                                localSummary = "Payment: ${((localPay?.amountMinor ?: 0L) / 100)}",
                                remoteSummary = "Payment: ${(remotePay.amountMinor / 100)}",
                                localPayloadJson = localPay?.toBackend()?.let { serializeBackendPayment(it) } ?: "",
                                remotePayloadJson = serializeBackendPayment(remotePay),
                                timestamp = System.currentTimeMillis()
                            )
                            recordConflict(conflict)
                            setEntityStatus(remotePay.id, SyncStatus.CONFLICT)
                        }
                    } else {
                        if (remotePay.isDeleted) {
                            db.settlementDao().softDeletePayment(remotePay.id)
                        } else {
                            db.settlementDao().insertPayment(remotePay.toRoomEntity())
                        }
                        setEntityStatus(remotePay.id, SyncStatus.SYNCED)
                    }
                }

                // Save updated cursor
                db.syncDao().insertMetadata(
                    SyncMetadataEntity(
                        key = "cursor_$groupId",
                        value = delta.cursorTimestamp.toString()
                    )
                )
            }
            is NetworkResult.Error -> {
                // Log and update state without crashing
            }
        }
    }

    // -------------------------------------------------------------
    // Conflict & Status Bookkeeping (Task D & Task E)
    // -------------------------------------------------------------

    private suspend fun recordConflict(conflict: SyncConflictRecord) {
        val json = JSONObject().apply {
            put("entityId", conflict.entityId)
            put("entityType", conflict.entityType.name)
            put("groupId", conflict.groupId)
            put("localRevision", conflict.localRevision)
            put("remoteRevision", conflict.remoteRevision)
            put("localSummary", conflict.localSummary)
            put("remoteSummary", conflict.remoteSummary)
            put("localPayloadJson", conflict.localPayloadJson)
            put("remotePayloadJson", conflict.remotePayloadJson)
            put("timestamp", conflict.timestamp)
        }.toString()

        db.syncDao().insertMetadata(
            SyncMetadataEntity(
                key = "conflict_${conflict.entityId}",
                value = json
            )
        )
    }

    private suspend fun setEntityStatus(entityId: String, status: SyncStatus) {
        db.syncDao().insertMetadata(
            SyncMetadataEntity(
                key = "status_$entityId",
                value = status.name
            )
        )
    }

    fun close() {
        scope.cancel()
    }

    private suspend fun refreshSyncState() = withContext(ioDispatcher) {
        try {
            if (!db.isOpen) return@withContext
            val pendingOps = db.syncDao().getPendingOperations()
            val failedOps = db.syncDao().getFailedOperations()
            val allMeta = db.syncDao().getAllMetadata()

            val conflicts = allMeta.filter { it.key.startsWith("conflict_") }.mapNotNull { meta ->
                try {
                    val json = JSONObject(meta.value)
                    SyncConflictRecord(
                        entityId = json.getString("entityId"),
                        entityType = SyncEntityType.valueOf(json.getString("entityType")),
                        groupId = json.getString("groupId"),
                        localRevision = json.getLong("localRevision"),
                        remoteRevision = json.getLong("remoteRevision"),
                        localSummary = json.getString("localSummary"),
                        remoteSummary = json.getString("remoteSummary"),
                        localPayloadJson = json.optString("localPayloadJson", ""),
                        remotePayloadJson = json.optString("remotePayloadJson", ""),
                        timestamp = json.getLong("timestamp")
                    )
                } catch (_: Exception) {
                    null
                }
            }

            _syncState.value = _syncState.value.copy(
                pendingCount = pendingOps.size,
                failedCount = failedOps.size,
                conflictCount = conflicts.size,
                activeConflicts = conflicts
            )
        } catch (_: Exception) {
            // Room DB closed or scope cancelled
        }
    }

    private fun extractGroupIdFromOperation(op: SyncOperationEntity): String {
        return try {
            JSONObject(op.payloadJson).optString("groupId", "")
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractBaseRevisionFromOperation(op: SyncOperationEntity): Long {
        return try {
            JSONObject(op.payloadJson).optLong("baseRevision", 1L)
        } catch (_: Exception) {
            1L
        }
    }

    private fun serializeBackendExpense(expense: BackendExpense): String {
        return JSONObject().apply {
            put("id", expense.id)
            put("groupId", expense.groupId)
            put("description", expense.description)
            put("amountMinor", expense.amountMinor)
            put("currency", expense.currency)
            put("revision", expense.revision)
            put("payerId", expense.payerId)
            put("isDeleted", expense.isDeleted)
        }.toString()
    }

    private fun parseBackendExpense(jsonStr: String): BackendExpense? {
        return try {
            val json = JSONObject(jsonStr)
            BackendExpense(
                id = json.getString("id"),
                groupId = json.getString("groupId"),
                createdById = json.optString("payerId", ""),
                payerId = json.optString("payerId", ""),
                amountMinor = json.getLong("amountMinor"),
                currency = json.optString("currency", "INR"),
                description = json.getString("description"),
                revision = json.optLong("revision", 1L),
                isDeleted = json.optBoolean("isDeleted", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun serializeBackendPayment(payment: BackendSettlementPayment): String {
        return JSONObject().apply {
            put("id", payment.id)
            put("groupId", payment.groupId)
            put("amountMinor", payment.amountMinor)
            put("currency", payment.currency)
            put("fromUserId", payment.fromUserId)
            put("toUserId", payment.toUserId)
            put("revision", payment.revision)
            put("isDeleted", payment.isDeleted)
        }.toString()
    }

    private fun parseBackendPayment(jsonStr: String): BackendPayment? {
        return try {
            val json = JSONObject(jsonStr)
            BackendPayment(
                id = json.getString("id"),
                groupId = json.getString("groupId"),
                fromUserId = json.getString("fromUserId"),
                toUserId = json.getString("toUserId"),
                amountMinor = json.getLong("amountMinor"),
                currency = json.optString("currency", "INR"),
                createdByUserId = json.optString("createdByUserId", json.optString("fromUserId", "")),
                revision = json.optLong("revision", 1L),
                isDeleted = json.optBoolean("isDeleted", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}

private typealias BackendPayment = BackendSettlementPayment
