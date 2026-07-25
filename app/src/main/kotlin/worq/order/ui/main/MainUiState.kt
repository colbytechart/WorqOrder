package worq.order.ui.main

import java.time.LocalDate

enum class MainTimerAction {
    START,
    STOP,
}

enum class MainMessage {
    SELECT_A_TASK_FIRST,
    LIVE_TIMING_TODAY_ONLY,
    TIMER_ALREADY_RUNNING,
    STOP_BEFORE_SWITCHING,
    SELECTED_TASK_MISSING,
    NO_ACTIVE_TIMER,
    CLOCK_CHANGED,
    DATA_UNAVAILABLE,
    TASK_NOT_FOUND,
    RUNNING_TASK_LOCKED,
}

data class MainTaskItemUi(
    val id: String,
    val clientName: String,
    val description: String,
    val totalDuration: String,
    val isClientArchived: Boolean,
    val isSelected: Boolean,
    val isRunning: Boolean,
    val canSelect: Boolean,
    val canModify: Boolean = true,
)

data class RunningTaskUi(
    val taskId: String,
    val clientName: String,
    val description: String,
    val workDate: LocalDate,
)

data class MainUiState(
    val displayedDate: LocalDate = LocalDate.of(1970, 1, 1),
    val today: LocalDate = LocalDate.of(1970, 1, 1),
    val isToday: Boolean = true,
    val isLoading: Boolean = true,
    val hasTaskLoadError: Boolean = false,
    val tasks: List<MainTaskItemUi> = emptyList(),
    val timerText: String = ZERO_DURATION,
    val timerAction: MainTimerAction = MainTimerAction.START,
    val canStart: Boolean = false,
    val canStop: Boolean = false,
    val isTimerOperationInProgress: Boolean = false,
    val runningTask: RunningTaskUi? = null,
    val message: MainMessage? = null,
    val isDatePickerVisible: Boolean = false,
    val openTaskMenuTaskId: String? = null,
    val taskPendingDeletion: MainTaskItemUi? = null,
    val canExport: Boolean = false,
) {
    val isTimerRunning: Boolean
        get() = timerAction == MainTimerAction.STOP

    companion object {
        const val ZERO_DURATION = "00:00:00.000"
    }
}

sealed interface MainEvent {
    data class SelectTask(
        val taskId: String,
    ) : MainEvent

    data object StartTimer : MainEvent

    data object StopTimer : MainEvent

    data object PreviousDate : MainEvent

    data object NextDate : MainEvent

    data object OpenDatePicker : MainEvent

    data class PickDate(
        val date: LocalDate,
    ) : MainEvent

    data object DismissDatePicker : MainEvent

    data object ReturnToToday : MainEvent

    data object OpenCreateTask : MainEvent

    data object OpenSettings : MainEvent

    data class OpenTaskMenu(
        val taskId: String,
    ) : MainEvent

    data object CloseTaskMenu : MainEvent

    data class EditTask(
        val taskId: String,
    ) : MainEvent

    data class RequestDeleteTask(
        val taskId: String,
    ) : MainEvent

    data object ConfirmDeleteTask : MainEvent

    data object DismissDeleteTask : MainEvent

    data object DismissMessage : MainEvent

    data object RetryData : MainEvent

    data object LifecycleResumed : MainEvent
}

sealed interface MainEffect {
    data class NavigateToCreateTask(
        val workDate: LocalDate,
    ) : MainEffect

    data object NavigateToSettings : MainEffect

    data class NavigateToEditTask(
        val taskId: String,
    ) : MainEffect
}
