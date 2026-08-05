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
            management = true,
        )

        composeRule.onNodeWithText("Consultant Management").assertIsDisplayed()
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
    fun emptySelectionProvidesGuidance() {
        val events = mutableListOf<ConsultantSettingsEvent>()
        setContent(
            ConsultantSettingsUiState(isLoading = false),
            events::add,
        )

        composeRule
            .onNodeWithText("Add and select a Consultant before creating a task.")
            .assertIsDisplayed()
    }

    @Test
    fun emptyManagementProvidesAddAction() {
        val events = mutableListOf<ConsultantSettingsEvent>()
        setContent(
            ConsultantSettingsUiState(isLoading = false),
            events::add,
            management = true,
        )
        composeRule.onNodeWithText("Add Consultant").performClick()
        composeRule
            .onNodeWithText("Add a Consultant to make them available for new tasks.")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Removed Consultants will appear here and can be restored.")
            .assertIsDisplayed()

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
            management = true,
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
        management: Boolean = false,
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                if (management) {
                    ConsultantManagementScreen(
                        uiState = state,
                        onEvent = onEvent,
                        onNavigateBack = {},
                    )
                } else {
                    ConsultantSelectionSection(
                        uiState = state,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}
