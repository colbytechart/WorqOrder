package worq.order.ui.tasks

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import worq.order.model.BillingStatus
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class EditTaskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metadataAndSingularIntervalAreRenderedWithActions() {
        val events = mutableListOf<EditTaskEvent>()
        setContent(
            state =
                readyState().copy(
                    interval = interval(id = "only"),
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
            .onNodeWithText("Do not charge")
            .performScrollTo()
            .assertIsSelected()
        composeRule
            .onNodeWithText("Interval")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Start Time: 09:00 AM").assertCountEquals(1)
        composeRule.onAllNodesWithText("Stop Time: 10:00 AM").assertCountEquals(1)
        composeRule.onAllNodesWithText("Duration: 01:00:00").assertCountEquals(0)
        composeRule.onAllNodesWithText("Add interval").assertCountEquals(0)
        composeRule.onNodeWithText("Edit interval").performScrollTo().performClick()

        assertTrue(events.contains(EditTaskEvent.OpenEditInterval("only")))
    }

    @Test
    fun untimedTaskOffersAddIntervalAndRunningTaskBlocksIntervalMutations() {
        val uiState = mutableStateOf(readyState())
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                EditTaskScreen(uiState = uiState.value, onEvent = {})
            }
        }

        composeRule
            .onNodeWithText("Add interval")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            uiState.value =
                readyState().copy(
                    isRunning = true,
                    interval = interval(id = "running", isRunning = true),
                )
        }

        composeRule.onNodeWithText("Save task changes").assertIsNotEnabled()
        composeRule.onAllNodesWithText("Add interval").assertCountEquals(0)
        composeRule.onAllNodesWithText("Edit interval").assertCountEquals(0)
        composeRule.onAllNodesWithText("Delete interval").assertCountEquals(0)
        composeRule
            .onNodeWithText("Stop this task’s timer before editing or deleting it.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun runningTaskDisablesMetadataAndTaskDeletion() {
        setContent(
            state =
                readyState().copy(
                    isRunning = true,
                    interval = interval(id = "running", isRunning = true),
                ),
        )

        composeRule.onNodeWithText("Save task changes").assertIsNotEnabled()
        composeRule.onNodeWithText("Delete task").assertIsNotEnabled()
        composeRule
            .onNodeWithText("Stop this task’s timer before editing or deleting it.")
            .performScrollTo()
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

    @Test
    fun migratedTaskDoesNotInventBillingStatusSelection() {
        setContent(state = readyState().copy(billingStatus = null))

        composeRule.onNodeWithText("Billable").performScrollTo().assertIsNotSelected()
        composeRule.onNodeWithText("Do not bill").assertIsNotSelected()
        composeRule.onNodeWithText("Do not charge").assertIsNotSelected()
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
            billingStatus = BillingStatus.DO_NOT_CHARGE,
            mileage = "12.5",
            totalDuration = "02:00:00",
            billingMinutes = 120,
        )

    private fun interval(
        id: String,
        isRunning: Boolean = false,
    ) = IntervalItemUi(
        id = id,
        startText = "09:00 AM",
        stopText = "10:00 AM",
        isRunning = isRunning,
    )
}
