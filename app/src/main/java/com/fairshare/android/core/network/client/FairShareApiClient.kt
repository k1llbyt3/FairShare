package com.fairshare.android.core.network.client

import com.fairshare.android.core.network.backend.AuthResult
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.InvitationDetails
import com.fairshare.android.core.network.backend.auth.VerificationTicket
import com.fairshare.android.core.network.backend.guard.AuthenticationException
import com.fairshare.android.core.network.backend.guard.AuthorizationException
import com.fairshare.android.core.network.backend.guard.ConflictException
import com.fairshare.android.core.network.backend.guard.ResourceNotFoundException
import com.fairshare.android.core.network.backend.guard.ServerValidationException
import com.fairshare.android.core.network.backend.model.BackendActivityEvent
import com.fairshare.android.core.network.backend.model.BackendExpense
import com.fairshare.android.core.network.backend.model.BackendGroup
import com.fairshare.android.core.network.backend.model.BackendGroupDelta
import com.fairshare.android.core.network.backend.model.BackendGroupMember
import com.fairshare.android.core.network.backend.model.BackendGroupMemberWithUser
import com.fairshare.android.core.network.backend.model.BackendInvitation
import com.fairshare.android.core.network.backend.model.BackendSettlementPayment
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.backend.model.GroupRole

sealed class NetworkResult<out T> {
    data class Success<out T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int, val message: String, val cause: Throwable? = null) : NetworkResult<Nothing>()
}

class ApiException(val statusCode: Int, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Secure API Client connecting the Android client to the authoritative FairShare Backend.
 * Automatically injects authorization session tokens on protected requests.
 */
class FairShareApiClient(
    val backendService: FairShareBackendService,
    val sessionStorage: SessionStorage
) {
    var isSimulatedOffline: Boolean = false

    private fun <T> executeRequest(block: () -> T): NetworkResult<T> {
        if (isSimulatedOffline) {
            return NetworkResult.Error(
                code = 0,
                message = "No network connection. Please check your internet connectivity.",
                cause = RuntimeException("Offline")
            )
        }
        return try {
            val result = block()
            NetworkResult.Success(result)
        } catch (e: AuthenticationException) {
            sessionStorage.clear()
            NetworkResult.Error(code = 401, message = e.message ?: "Unauthorized", cause = e)
        } catch (e: AuthorizationException) {
            NetworkResult.Error(code = 403, message = e.message ?: "Forbidden", cause = e)
        } catch (e: ResourceNotFoundException) {
            NetworkResult.Error(code = 404, message = e.message ?: "Not Found", cause = e)
        } catch (e: ConflictException) {
            NetworkResult.Error(code = 409, message = e.message ?: "Conflict", cause = e)
        } catch (e: ServerValidationException) {
            NetworkResult.Error(code = 400, message = e.message ?: "Bad Request", cause = e)
        } catch (e: IllegalStateException) {
            NetworkResult.Error(code = 429, message = e.message ?: "Rate limit or conflict", cause = e)
        } catch (e: IllegalArgumentException) {
            NetworkResult.Error(code = 400, message = e.message ?: "Invalid request", cause = e)
        } catch (e: Exception) {
            NetworkResult.Error(code = 500, message = e.message ?: "Internal error", cause = e)
        }
    }

    private fun requireToken(): String {
        return sessionStorage.sessionToken
            ?: throw AuthenticationException("No active session found. Please authenticate.")
    }

    // Auth endpoints
    fun requestPhoneVerification(phone: String): NetworkResult<VerificationTicket> =
        executeRequest { backendService.requestPhoneVerification(phone) }

    fun verifyPhoneCode(phone: String, code: String, displayName: String?, currency: String?): NetworkResult<AuthResult> =
        executeRequest {
            val result = backendService.verifyPhoneCode(phone, code, displayName, currency)
            sessionStorage.saveSession(
                token = result.session.token,
                userId = result.user.id,
                phone = result.user.phoneNumber
            )
            result
        }

    fun getCurrentUser(): NetworkResult<BackendUser> =
        executeRequest { backendService.getCurrentUser(requireToken()) }

    fun updateProfile(displayName: String, defaultCurrency: String): NetworkResult<BackendUser> =
        executeRequest { backendService.updateProfile(requireToken(), displayName, defaultCurrency) }

    fun logout(): NetworkResult<Unit> =
        executeRequest {
            sessionStorage.sessionToken?.let { backendService.logout(it) }
            sessionStorage.clear()
        }

    fun deleteAccount(): NetworkResult<Unit> =
        executeRequest {
            backendService.deleteAccount(requireToken())
            sessionStorage.clear()
        }

    // Group endpoints
    fun createGroup(
        name: String,
        description: String = "",
        groupType: String = "TRIP",
        currency: String = "INR",
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String = "Asia/Kolkata"
    ): NetworkResult<BackendGroup> =
        executeRequest { backendService.createGroup(requireToken(), name, description, groupType, currency, startDate, endDate, timezone) }

    fun getGroup(groupId: String): NetworkResult<BackendGroup> =
        executeRequest { backendService.getGroup(requireToken(), groupId) }

    fun listUserGroups(includeArchived: Boolean = false): NetworkResult<List<BackendGroup>> =
        executeRequest { backendService.listUserGroups(requireToken(), includeArchived) }

    fun updateGroup(
        groupId: String,
        name: String,
        description: String,
        currency: String,
        startDate: Long? = null,
        endDate: Long? = null,
        timezone: String? = null
    ): NetworkResult<BackendGroup> =
        executeRequest { backendService.updateGroup(requireToken(), groupId, name, description, currency, startDate, endDate, timezone) }

    fun archiveGroup(groupId: String): NetworkResult<BackendGroup> =
        executeRequest { backendService.archiveGroup(requireToken(), groupId) }

    fun unarchiveGroup(groupId: String): NetworkResult<BackendGroup> =
        executeRequest { backendService.unarchiveGroup(requireToken(), groupId) }

    fun getGroupMembers(groupId: String, includeArchived: Boolean = true): NetworkResult<List<BackendGroupMemberWithUser>> =
        executeRequest { backendService.getGroupMembers(requireToken(), groupId, includeArchived) }

    fun addMember(groupId: String, targetUserId: String, role: GroupRole = GroupRole.MEMBER): NetworkResult<Unit> =
        executeRequest { backendService.addMember(requireToken(), groupId, targetUserId, role) }

    fun updateMemberRole(groupId: String, targetUserId: String, newRole: GroupRole): NetworkResult<Unit> =
        executeRequest { backendService.updateMemberRole(requireToken(), groupId, targetUserId, newRole) }

    fun removeMember(groupId: String, targetUserId: String): NetworkResult<Unit> =
        executeRequest { backendService.removeMember(requireToken(), groupId, targetUserId) }

    fun addNonAccountMember(groupId: String, name: String, phoneNumber: String? = null): NetworkResult<BackendUser> =
        executeRequest { backendService.addNonAccountMember(requireToken(), groupId, name, phoneNumber) }

    // Invitation endpoints
    fun createInvitation(
        groupId: String,
        inviteType: String = "CODE",
        maxUses: Int = 0,
        targetPhoneNumber: String? = null,
        expiresInMs: Long = 7 * 24 * 60 * 60 * 1000L
    ): NetworkResult<BackendInvitation> =
        executeRequest { backendService.createInvitation(requireToken(), groupId, inviteType, maxUses, targetPhoneNumber, expiresInMs) }

    fun getInvitationDetails(inviteCode: String): NetworkResult<InvitationDetails> =
        executeRequest { backendService.getInvitationDetails(inviteCode) }

    fun joinGroupByInvite(inviteCode: String): NetworkResult<BackendGroup> =
        executeRequest { backendService.joinGroupByInvite(requireToken(), inviteCode) }

    fun revokeInvitation(groupId: String, invitationId: String): NetworkResult<Unit> =
        executeRequest { backendService.revokeInvitation(requireToken(), groupId, invitationId) }

    // Expense endpoints
    fun createExpense(groupId: String, expense: BackendExpense): NetworkResult<BackendExpense> =
        executeRequest { backendService.createExpense(requireToken(), groupId, expense) }

    fun updateExpense(groupId: String, expense: BackendExpense): NetworkResult<BackendExpense> =
        executeRequest { backendService.updateExpense(requireToken(), groupId, expense) }

    fun deleteExpense(groupId: String, expenseId: String): NetworkResult<Unit> =
        executeRequest { backendService.deleteExpense(requireToken(), groupId, expenseId) }

    fun listExpenses(groupId: String): NetworkResult<List<BackendExpense>> =
        executeRequest { backendService.listExpenses(requireToken(), groupId) }

    // Payment endpoints
    fun recordPayment(groupId: String, payment: BackendSettlementPayment): NetworkResult<BackendSettlementPayment> =
        executeRequest { backendService.recordPayment(requireToken(), groupId, payment) }

    fun deletePayment(groupId: String, paymentId: String): NetworkResult<Unit> =
        executeRequest { backendService.deletePayment(requireToken(), groupId, paymentId) }

    fun listPayments(groupId: String): NetworkResult<List<BackendSettlementPayment>> =
        executeRequest { backendService.listPayments(requireToken(), groupId) }

    // Activity endpoints
    fun listActivityEvents(groupId: String): NetworkResult<List<BackendActivityEvent>> =
        executeRequest { backendService.listActivityEvents(requireToken(), groupId) }

    // Delta Synchronization endpoint (Phase 5)
    fun getGroupDelta(groupId: String, sinceTimestamp: Long): NetworkResult<BackendGroupDelta> =
        executeRequest { backendService.getGroupDelta(requireToken(), groupId, sinceTimestamp) }

    fun ensureGroupHydrated(
        group: BackendGroup,
        members: List<BackendGroupMember>,
        users: List<BackendUser>
    ) {
        backendService.hydrateGroupFromLocal(group, members, users)
    }
}
