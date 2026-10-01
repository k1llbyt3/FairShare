package com.fairshare.android.core.network

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.fairshare.android.core.database.FairShareDatabase
import com.fairshare.android.core.design.FairShareTheme
import com.fairshare.android.core.network.backend.FairShareBackendService
import com.fairshare.android.core.network.backend.auth.RealSmsVerificationGateway
import com.fairshare.android.core.network.backend.model.BackendUser
import com.fairshare.android.core.network.client.FairShareApiClient
import com.fairshare.android.core.network.client.SessionStorage
import com.fairshare.android.core.network.repository.AuthRepository
import com.fairshare.android.feature.auth.AuthScreen
import com.fairshare.android.feature.profile.ProfileScreen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase4UiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: FairShareDatabase
    private lateinit var gateway: RealSmsVerificationGateway
    private lateinit var backendService: FairShareBackendService
    private lateinit var sessionStorage: SessionStorage
    private lateinit var apiClient: FairShareApiClient
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = FairShareDatabase.createInMemory(context)
        gateway = RealSmsVerificationGateway()
        backendService = FairShareBackendService(verificationGateway = gateway)
        sessionStorage = SessionStorage()
        apiClient = FairShareApiClient(backendService, sessionStorage)
        authRepository = AuthRepository(apiClient, sessionStorage, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun authScreen_enterPhoneAndSubmit_transitionsToVerificationCodeStep() {
        composeTestRule.setContent {
            FairShareTheme {
                AuthScreen(
                    authRepository = authRepository,
                    onAuthSuccess = {}
                )
            }
        }

        // Verify initial state
        composeTestRule.onNodeWithText("Sign in or Register").assertExists()
        composeTestRule.onNodeWithText("Continue").assertExists()

        // Input name and phone number
        composeTestRule.onNodeWithText("Your Name").performTextInput("Arjun Kumar")
        composeTestRule.onNodeWithText("Mobile Number").performTextInput("9876543210")

        // Click continue
        composeTestRule.onNodeWithText("Continue").performClick()

        // Verification code screen should now be visible
        composeTestRule.onNodeWithText("Verify Mobile Number").assertExists()
        composeTestRule.onNodeWithText("Verify & Sign In").assertExists()
    }

    @Test
    fun profileScreen_showsUserDetailsAndAllowsEditing() {
        val user = BackendUser(
            id = "test-user-id",
            phoneNumber = "+919876543210",
            displayName = "Arjun Kumar",
            defaultCurrency = "INR"
        )
        sessionStorage.saveSession("token-test", user.id, user.phoneNumber)

        var loggedOut = false

        composeTestRule.setContent {
            FairShareTheme {
                ProfileScreen(
                    user = user,
                    authRepository = authRepository,
                    onBackClick = {},
                    onLogoutSuccess = { loggedOut = true }
                )
            }
        }

        // Verify user details are visible
        composeTestRule.onAllNodesWithText("Arjun Kumar").assertCountEquals(2)
        composeTestRule.onNodeWithText("+919876543210").assertExists()
        composeTestRule.onNodeWithText("VERIFIED").assertExists()
        composeTestRule.onNodeWithText("Sign Out").assertExists()

        // Scroll and Tap Sign Out
        composeTestRule.onNodeWithText("Sign Out").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Confirmation dialog appears
        composeTestRule.onNodeWithText("Are you sure", substring = true, useUnmergedTree = true).assertExists()
    }
}
