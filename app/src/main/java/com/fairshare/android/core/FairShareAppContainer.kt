package com.fairshare.android.core

import android.content.Context
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.repository.ExpenseRepository
import com.fairshare.android.core.database.repository.GroupRepository
import com.fairshare.android.core.database.repository.SettlementRepository
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.SessionStorage
import com.fairshare.android.core.network.repository.AuthRepository
import com.fairshare.android.core.network.repository.BackendGroupRepository

/**
 * Dependency container for FairShare Android Application.
 * Provides singleton access to Room database, backend client, and repositories.
 */
class FairShareAppContainer(context: Context) {
    val database: FairShareDatabase = FairShareDatabase.getInstance(context)
    val backendService: FairShareBackendService = sharedBackendService
    val sessionStorage: SessionStorage = SessionStorage(context)
    val apiClient: FairShareApiClient = FairShareApiClient(backendService, sessionStorage)

    val authRepository: AuthRepository = AuthRepository(apiClient, sessionStorage, database)
    val backendGroupRepository: BackendGroupRepository = BackendGroupRepository(apiClient, database)

    val syncEngine: com.fairshare.android.core.sync.SyncEngine = com.fairshare.android.core.sync.SyncEngine(database, apiClient, sessionStorage)

    val groupRepository: GroupRepository = GroupRepository(database)
    val expenseRepository: ExpenseRepository = ExpenseRepository(database, syncEngine)
    val settlementRepository: SettlementRepository = SettlementRepository(database, syncEngine)
    val budgetRepository: com.fairshare.android.core.database.repository.BudgetRepository = com.fairshare.android.core.database.repository.BudgetRepository(database, syncEngine)
    val candidateRepository: com.fairshare.android.core.database.repository.CandidateRepository = com.fairshare.android.core.database.repository.CandidateRepository(database)
    val receiptOcrEngine: com.fairshare.android.core.domain.receipt.ReceiptOcrEngine = com.fairshare.android.core.domain.receipt.LocalReceiptOcrEngine()
    val collaborationRepository: com.fairshare.android.core.database.repository.CollaborationRepository = com.fairshare.android.core.database.repository.CollaborationRepository(database, syncEngine, apiClient)
    val attachmentRepository: com.fairshare.android.core.database.repository.AttachmentRepository = com.fairshare.android.core.database.repository.AttachmentRepository(database)
    val tripRepository: com.fairshare.android.core.database.repository.TripRepository = com.fairshare.android.core.database.repository.TripRepository(database)

    companion object {
        // Shared backend service simulates canonical cloud backend instance
        val sharedBackendService: FairShareBackendService by lazy {
            FairShareBackendService()
        }

        @Volatile
        private var instance: FairShareAppContainer? = null

        fun getInstance(context: Context): FairShareAppContainer {
            return instance ?: synchronized(this) {
                instance ?: FairShareAppContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
