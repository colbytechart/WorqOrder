package worq.order.data

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

/**
 * Persistent timing-selection hints.
 *
 * [selectedOnDate] records the effective local date on which the selection was made. It lets the
 * rollover coordinator distinguish a task carried through a real date change from a historical
 * task the user intentionally selected while browsing today.
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
