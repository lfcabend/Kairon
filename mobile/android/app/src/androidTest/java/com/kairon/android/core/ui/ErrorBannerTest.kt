package com.kairon.android.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ErrorBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTheMessage() {
        composeRule.setContent {
            MaterialTheme { ErrorBanner(message = "Couldn't connect to the server.", onDismiss = {}) }
        }

        composeRule.onNodeWithText("Couldn't connect to the server.").assertIsDisplayed()
    }

    @Test
    fun retryInvokesTheCallbackWhenPresent() {
        var retried = false
        composeRule.setContent {
            MaterialTheme { ErrorBanner(message = "Sync failed.", onDismiss = {}, onRetry = { retried = true }) }
        }

        composeRule.onNodeWithText("Retry").performClick()

        assert(retried)
    }

    @Test
    fun noRetryButtonWhenOnRetryIsNull() {
        composeRule.setContent {
            MaterialTheme { ErrorBanner(message = "Sync failed.", onDismiss = {}, onRetry = null) }
        }

        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun dismissRemovesTheBannerFromComposition() {
        var dismissed = false
        composeRule.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) {
                    ErrorBanner(
                        message = "Sync failed.",
                        onDismiss = {
                            dismissed = true
                            visible = false
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("Dismiss").performClick()

        assert(dismissed)
        composeRule.onNodeWithText("Sync failed.").assertDoesNotExist()
    }
}
