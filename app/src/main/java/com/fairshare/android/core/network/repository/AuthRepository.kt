
package com.fairshare.android.core.network.repository

import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.database.entity.UserEntity
import com.fairshare.android.core.network.backend.auth.VerificationTicket
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.NetworkResult
import com.fairshare.android.core.network.client.SessionStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Unauthenticated : AuthState()
    object Loading : AuthState()
    data class Authenticated(val user: BackendUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * Authentication Repository managing identity, verification, sessions,
 * and syncing authenticated profile into Room for local-first access.
 */
class AuthRepository(
    private val apiClient: FairShareApiClient,
    private val sessionStorage: SessionStorage,
    private val database: FairShareDatabase? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkExistingSession()
    }

    fun checkExistingSession() {
        if (!sessionStorage.hasSession) {
            _authState.value = AuthState.Unauthenticated
            return
        }

        coroutineScope.launch {
            val token = sessionStorage.sessionToken
            val localUserId = sessionStorage.currentUserId

            // 1. First check Room local database for fast, offline-first session restoration
            val localUser = if (localUserId != null && database != null) {
                try {
                    database.userDao().getUserById(localUserId)
                } catch (_: Exception) {
                    null
                }
            } else null

            if (localUser != null && token != null) {
                val cachedUser = BackendUser(
                    id = localUser.id,
                    phoneNumber = localUser.phoneNumber,
                    displayName = localUser.displayName,
                    profileImageUrl = localUser.profileImageUrl,
                    defaultCurrency = localUser.defaultCurrency,
                    createdAt = localUser.createdAt,
                    updatedAt = localUser.updatedAt
                )
                // Rehydrate in-memory backend session if process was restarted
                try {
                    apiClient.backendService.restoreSession(token, cachedUser)
                } catch (_: Exception) {}

                _authState.value = AuthState.Authenticated(cachedUser)
            }

            // 2. Validate with backend / refresh profile
            when (val result = apiClient.getCurrentUser()) {
                is NetworkResult.Success -> {
                    val user = result.data
                    persistUserLocally(user)
                    _authState.value = AuthState.Authenticated(user)
                }
                is NetworkResult.Error -> {
                    if (result.code == 401) {
                        // Only clear if local rehydration failed or session is genuinely invalid
                        if (localUser == null) {
                            sessionStorage.clear()
                            _authState.value = AuthState.Unauthenticated
                        }
                    } else if (_authState.value !is AuthState.Authenticated) {
                        // Fallback if localUser wasn't in Room but phone was saved
                        if (localUserId != null) {
                            val phone = sessionStorage.userPhone ?: ""
                            val cachedUser = BackendUser(
                                id = localUserId,
                                phoneNumber = phone,
                                displayName = if (phone.isNotBlank()) "User ${phone.takeLast(4)}" else "User"
                            )
                            if (token != null) {
                                try {
                                    apiClient.backendService.restoreSession(token, cachedUser)
                                } catch (_: Exception) {}
                            }
                            _authState.value = AuthState.Authenticated(cachedUser)
                        } else {
                            _authState.value = AuthState.Unauthenticated
                        }
                    }
                }
            }
        }
    }

    fun requestVerification(phone: String): NetworkResult<VerificationTicket> {
        return apiClient.requestPhoneVerification(phone)
    }

    fun verifyCode(
        phone: String,
        code: String,
        displayName: String? = null,
        currency: String? = null
    ): NetworkResult<BackendUser> {
        _authState.value = AuthState.Loading
        return when (val result = apiClient.verifyPhoneCode(phone, code, displayName, currency)) {
            is NetworkResult.Success -> {
                val user = result.data.user
                persistUserLocally(user)
                _authState.value = AuthState.Authenticated(user)
                NetworkResult.Success(user)
            }
            is NetworkResult.Error -> {
                _authState.value = AuthState.Unauthenticated
                NetworkResult.Error(result.code, result.message, result.cause)
            }
        }
    }

    fun updateProfile(displayName: String, currency: String): NetworkResult<BackendUser> {
        return when (val result = apiClient.updateProfile(displayName, currency)) {
            is NetworkResult.Success -> {
                val user = result.data
                persistUserLocally(user)
                _authState.value = AuthState.Authenticated(user)
                NetworkResult.Success(user)
            }
            is NetworkResult.Error -> {
                NetworkResult.Error(result.code, result.message, result.cause)
            }
        }
    }

    fun logout(): NetworkResult<Unit> {
        val res = apiClient.logout()
        sessionStorage.clear()
        _authState.value = AuthState.Unauthenticated
        return res
    }

    private fun persistUserLocally(user: BackendUser) {
        database?.let { db ->
            coroutineScope.launch {
                try {
                    val entity = UserEntity(
                        id = user.id,
                        phoneNumber = user.phoneNumber,
                        displayName = user.displayName,
                        profileImageUrl = user.profileImageUrl,
                        defaultCurrency = user.defaultCurrency,
                        createdAt = user.createdAt,
                        updatedAt = user.updatedAt
                    )
                    db.userDao().insertUser(entity)
                } catch (_: Exception) {
                    // Ignore local write failure in read-only or in-memory contexts
                }
            }
        }
    }
}
