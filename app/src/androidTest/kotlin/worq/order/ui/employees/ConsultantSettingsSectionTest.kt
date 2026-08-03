package worq.order.ui.employees

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class ConsultantSettingsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun directoryExposesSelectionAndExpectedManagementActions() {
        val events = mutableListOf<ConsultantSettingsEvent>()
        setContent(
            ConsultantSettingsUiState(
                isLoading = false,
                activeConsultants =
                    listOf(
                        ConsultantItemUi("alpha", "Alpha"),
                        ConsultantItemUi("zulu", "Zulu"),
                    ),
                archivedConsultants = listOf(ConsultantItemUi("morgan", "Morgan")),
                selectedConsultantId = "alpha",
            ),
            events::add,
        )

        composeRule.onNodeWithText("Consultant").assertIsDisplayed()
        composeRule.onNodeWithText("Selected for new tasks").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rename Alpha").performClick()
        composeRule
            .onNodeWithContentDescription("Remove Zulu from the Consultant list")
            .performClick()
        composeRule.onNodeWithContentDescription("Restore Morgan").performClick()

        assertTrue(ConsultantSettingsEvent.OpenRenameConsultant("alpha") in events)
        assertTrue(ConsultantSettingsEvent.RequestArchive("zulu") in events)
        assertTrue(ConsultantSettingsEvent.RestoreConsultant("morgan") in events)
    }

    @Test
    fun emptyDirectoryProvidesAddAndSelectionGuidance() {
        val events = mutableListOf<ConsultantSettingsEvent>()
        setContent(
            ConsultantSettingsUiState(isLoading = false),
            events::add,
        )

        composeRule
            .onNodeWithText("Add and select a Consultant before creating a task.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add Consultant").performClick()

        assertEquals(listOf(ConsultantSettingsEvent.OpenAddConsultant), events)
    }

    @Test
    fun editorAndArchiveConfirmationExplainValidationAndHistory() {
        val events = mutableListOf<ConsultantSettingsEvent>()
        setContent(
            ConsultantSettingsUiState(
                isLoading = false,
                activeConsultants = listOf(ConsultantItemUi("alpha", "Alpha")),
                selectedConsultantId = "alpha",
                editor =
                    ConsultantEditorUiState(
                        mode = ConsultantEditorMode.RENAME,
                        consultantId = "alpha",
                        name = "Alpha",
                        fieldError = ConsultantNameFieldError.DUPLICATE_ACTIVE,
                    ),
                archiveConfirmation =
                    ArchiveConsultantConfirmation(
                        consultantId = "alpha",
                        consultantName = "Alpha",
                        wasSelected = true,
                    ),
            ),
            events::add,
        )

        composeRule
            .onNodeWithText("An active Consultant already uses this name.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Earlier tasks and exports keep their captured Consultant name. " +
                    "This Consultant will be removed from new-task choices and the current " +
                    "Consultant selection will be cleared.",
            ).assertIsDisplayed()
    }

    private fun setContent(
        state: ConsultantSettingsUiState,
        onEvent: (ConsultantSettingsEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                ConsultantSettingsSection(uiState = state, onEvent = onEvent)
            }
        }
    }
}
