package worq.order.ui.tasks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.clients.ClientItemUi
import worq.order.ui.employees.ConsultantItemUi
import worq.order.model.WorkType
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class EditTaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metadataAndOrderedIntervalsAreRenderedWithActions() {
        val events = mutableListOf<EditTaskEvent>()
        setContent(
            state =
                readyState().copy(
                    intervals =
                        listOf(
                            interval(id = "first", ordinal = 1),
                            interval(id = "second", ordinal = 2),
                        ),
                ),
            onEvent = events::add,
        )

        composeRule.onNodeWithText("Short description").assertIsDisplayed()
        composeRule.onNodeWithText("Hardware / Software Purchases").assertIsDisplayed()
        composeRule
            .onNodeWithText("Task Total: 02:00:00")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Billing Minutes: 120")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Alex Rivera").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("On-Site").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Interval 1")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Start Time: 09:00 AM").assertCountEquals(2)
        composeRule.onAllNodesWithText("Stop Time: 10:00 AM").assertCountEquals(2)
        composeRule.onAllNodesWithText("Duration: 01:00:00").assertCountEquals(0)
        composeRule
            .onNodeWithText("Interval 2")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Add interval")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertTrue(events.contains(EditTaskEvent.OpenAddInterval))
    }

    @Test
    fun runningTaskDisablesMutatingActions() {
        setContent(
            state =
                readyState().copy(
                    isRunning = true,
                    intervals = listOf(interval(id = "running", ordinal = 1)),
                ),
        )

        composeRule.onNodeWithText("Save task changes").assertIsNotEnabled()
        composeRule.onNodeWithText("Add interval").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete task").assertIsNotEnabled()
        composeRule
            .onNodeWithText("Stop this task’s timer before editing or deleting it.")
            .assertIsDisplayed()
    }

    @Test
    fun intervalEditorUsesTheSameStrictTwelveHourClockPresentation() {
        setContent(
            state =
                readyState().copy(
                    intervalEditor =
                        IntervalEditorUiState(
                            startLocal = LocalDateTime.of(2026, 7, 25, 9, 5, 42, 987_000_000),
                            stopLocal = LocalDateTime.of(2026, 7, 25, 13, 30, 15),
                        ),
                ),
        )

        composeRule.onNodeWithText("Start Time: 09:05 AM").assertIsDisplayed()
        composeRule.onNodeWithText("Stop Time: 01:30 PM").assertIsDisplayed()
    }

    private fun setContent(
        state: EditTaskUiState,
        onEvent: (EditTaskEvent) -> Unit = {},
    ) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                EditTaskScreen(uiState = state, onEvent = onEvent)
            }
        }
    }

    private fun readyState() =
        EditTaskUiState(
            taskId = "task",
            isLoading = false,
            workDate = LocalDate.of(2026, 7, 25),
            zoneId = ZoneId.of("America/New_York"),
            originalClientName = "Client",
            activeClients = listOf(ClientItemUi("client", "Client")),
            selectedClientId = "client",
            originalConsultantName = "Alex Rivera",
            activeConsultants = listOf(ConsultantItemUi("employee", "Alex Rivera")),
            selectedConsultantId = "employee",
            description = "Description",
            hardwareSoftwarePurchases = "Laptop",
            workType = WorkType.ON_SITE,
            mileage = "12.5",
            totalDuration = "02:00:00",
            billingMinutes = 120,
        )

    private fun interval(
        id: String,
        ordinal: Int,
    ) = IntervalItemUi(
        id = id,
        ordinal = ordinal,
        startText = "09:00 AM",
        stopText = "10:00 AM",
        isRunning = false,
    )
}
