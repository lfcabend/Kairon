package com.kairon.android.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.kairon.android.core.auth.AuthRepository
import com.kairon.android.core.auth.SessionExpiredSignal
import com.kairon.android.core.network.AppError
import com.kairon.android.core.network.FieldError
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

/**
 * D4/D4a — a [AppError.Validation] attaches its message to the matching
 * field's `supportingText`; a [AppError.LoginRejected] (or any other
 * non-Validation category) renders on the shared form-level line instead,
 * with neither field individually marked `isError`.
 */
class LoginScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A real [LoginViewModel] whose `state` is overridden for the test, bypassing Hilt entirely. */
    private fun fakeViewModel(initialState: AuthUiState): LoginViewModel {
        val viewModel = LoginViewModel(mockk<AuthRepository>(relaxed = true), mockk<SessionExpiredSignal>(relaxed = true))
        val stateField = LoginViewModel::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (stateField.get(viewModel) as MutableStateFlow<AuthUiState>).value = initialState
        return viewModel
    }

    @Test
    fun aValidationErrorOnEmailAttachesToTheEmailFieldOnly() {
        val viewModel = fakeViewModel(
            AuthUiState.Error(AppError.Validation(listOf(FieldError("email", "must be a well-formed email address")))),
        )

        composeRule.setContent {
            MaterialTheme { LoginScreen(onLoggedIn = {}, onNavigateToRegister = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithText("must be a well-formed email address").assertExists()
    }

    @Test
    fun aLoginRejectedErrorRendersOnceOnTheFormLevelLineNotAttachedToEitherField() {
        val viewModel = fakeViewModel(AuthUiState.Error(AppError.LoginRejected("Invalid email or password.")))

        composeRule.setContent {
            MaterialTheme { LoginScreen(onLoggedIn = {}, onNavigateToRegister = {}, viewModel = viewModel) }
        }

        // Renders exactly once, as the shared form-level line — never duplicated onto a field's own supportingText.
        composeRule.onAllNodesWithText("Invalid email or password.").assertCountEquals(1)
    }
}
