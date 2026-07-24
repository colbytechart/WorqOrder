package worq.order.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
            .onNodeWithText("Export unavailable in this scaffold")
            .assertIsNotEnabled()
    }

    @Test
    fun settingsIconNavigatesToSettingsPlaceholder() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Theme, time-zone, and export preferences are intentionally inactive in this scaffold.",
            ).assertIsDisplayed()
    }
}
