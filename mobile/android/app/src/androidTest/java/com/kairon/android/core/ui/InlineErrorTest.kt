package com.kairon.android.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Covers the D7 (plain) and D10 ("Reload") usages of the one shared component. */
class InlineErrorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTheMessage() {
        composeRule.setContent {
            MaterialTheme { InlineError("Task not found.") }
        }

        composeRule.onNodeWithText("Task not found.").assertIsDisplayed()
    }

    @Test
    fun withBothActionParamsSetRendersATappableButtonThatInvokesTheCallback() {
        var reloaded = false
        composeRule.setContent {
            MaterialTheme {
                InlineError(
                    "This task was modified by another request. Reload and try again.",
                    actionLabel = "Reload",
                    onAction = { reloaded = true },
                )
            }
        }

        composeRule.onNodeWithText("Reload").performClick()

        assert(reloaded)
    }

    @Test
    fun withNoActionLabelRendersNoButton() {
        composeRule.setContent {
            MaterialTheme { InlineError("Something went wrong. Please try again.") }
        }

        composeRule.onNodeWithText("Reload").assertDoesNotExist()
    }

    @Test
    fun withActionLabelButNoOnActionRendersNoButton() {
        composeRule.setContent {
            MaterialTheme { InlineError("Something went wrong.", actionLabel = "Reload", onAction = null) }
        }

        composeRule.onNodeWithText("Reload").assertDoesNotExist()
    }
}
