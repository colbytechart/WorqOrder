package worq.order.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.app.MainActivity

@RunWith(AndroidJUnit4::class)
class WorqOrderNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScaffoldShowsRequiredRegions() {
        composeRule.onNodeWithText("WorqOrder").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:00.000").assertIsDisplayed()
        composeRule.onNodeWithText("Tasks").assertIsDisplayed()
        composeRule
            .onNodeWithText("Export", substring = true)
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Add task").assertIsEnabled()
    }

    @Test
    fun bottomActionsDoNotOverlap() {
        val exportAction =
            composeRule
                .onNodeWithText("Export", substring = true)
                .assertIsDisplayed()
                .assertIsNotEnabled()
        val addTaskAction =
            composeRule
                .onNodeWithText("Add task")
                .assertIsDisplayed()
                .assertIsEnabled()

        val exportBounds = exportAction.fetchSemanticsNode().boundsInRoot
        val addTaskBounds = addTaskAction.fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Export must end before Add task begins: $exportBounds vs $addTaskBounds",
            exportBounds.right <= addTaskBounds.left,
        )
    }

    @Test
    fun addTaskActionNavigatesToCreateTaskPlaceholder() {
        composeRule.onNodeWithText("Add task").performClick()
        composeRule.onNodeWithText("Create task").assertIsDisplayed()
    }

    @Test
    fun settingsIconNavigatesToClientManagement() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(
                hasText(
                    "Add, rename, remove, or restore clients without changing historical tasks.",
                ),
            )
        composeRule
            .onNodeWithText(
                "Add, rename, remove, or restore clients without changing historical tasks.",
            ).assertIsDisplayed()
        composeRule.onNodeWithText("Client management").performClick()
        composeRule.onNodeWithText("Active clients").assertIsDisplayed()
        composeRule.onNodeWithText("Add client").assertIsDisplayed()
    }
}
