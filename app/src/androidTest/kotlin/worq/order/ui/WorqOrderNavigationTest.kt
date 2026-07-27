package worq.order.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.app.MainActivity
import worq.order.ui.main.MainScreenTestTags

@RunWith(AndroidJUnit4::class)
class WorqOrderNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScaffoldShowsRequiredRegions() {
        composeRule.onNodeWithText("WorqOrder").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MainScreenTestTags.TIMER)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Tasks").assertIsDisplayed()
        composeRule
            .onNodeWithTag(MainScreenTestTags.EXPORT_ACTION)
            .assertIsEnabled()
        composeRule
            .onNodeWithTag(MainScreenTestTags.ADD_TASK_ACTION)
            .assertIsEnabled()
    }

    @Test
    fun bottomActionsDoNotOverlap() {
        val exportAction =
            composeRule
                .onNodeWithTag(MainScreenTestTags.EXPORT_ACTION)
                .assertIsDisplayed()
                .assertIsEnabled()
        val addTaskAction =
            composeRule
                .onNodeWithTag(MainScreenTestTags.ADD_TASK_ACTION)
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
        composeRule.onNodeWithText("Create Task").assertIsDisplayed()
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
        composeRule.onNodeWithText("Client Management").performClick()
        composeRule.onNodeWithText("Active Clients").assertIsDisplayed()
        composeRule.onNodeWithText("Add client").assertIsDisplayed()
    }
}
