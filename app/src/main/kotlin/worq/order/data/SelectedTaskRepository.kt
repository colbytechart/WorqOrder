package worq.order.data

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Persistent timing-selection hints.
 *
 * [selectedOnDate] and [selectedInZone] record the context in which the selection was made. They
 * are never eligibility authority: reconciliation and Start validate the selected Room task's
 * own work date and ZoneId, and stale context is cleared without creating another task.
 */
data class SelectedTaskState(
    val taskId: String,
    val seriesId: String,
    val selectedOnDate: LocalDate,
    val selectedInZone: ZoneId,
)

interface SelectedTaskRepository {
    fun observeSelection(): Flow<SelectedTaskState?>

    suspend fun readSelection(): SelectedTaskState?

    suspend fun select(selection: SelectedTaskState)

    suspend fun clear()
}
