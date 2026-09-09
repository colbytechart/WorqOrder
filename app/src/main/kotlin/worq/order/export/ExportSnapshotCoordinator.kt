package worq.order.export

import java.time.LocalDate
import kotlinx.coroutines.sync.withLock
import worq.order.data.ActiveTimerRepository
import worq.order.data.TaskRepository
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.NormalizeTimerResult
import worq.order.timer.TimerOperationLock
import worq.order.timer.UtcClock

sealed interface PrepareExportSnapshotResult {
    data class Ready(
        val snapshot: ExportSnapshot,
    ) : PrepareExportSnapshotResult

    data object ClockChanged : PrepareExportSnapshotResult

    data object ActiveTimerChanged : PrepareExportSnapshotResult

    /** A stable export is never built while Room still owns an open interval. */
    data object ActiveTimerRunning : PrepareExportSnapshotResult
}

fun interface ExportSnapshotProvider {
    suspend fun prepare(workDate: LocalDate): PrepareExportSnapshotResult
}

/**
 * Captures the one authoritative, destination-neutral dataset consumed by every exporter.
 *
 * Destination adapters may encode or transport this snapshot, but must not independently select,
 * reorder, or reformat task fields.
 */
class ExportSnapshotCoordinator(
    private val taskRepository: TaskRepository,
    private val activeTimerRepository: ActiveTimerRepository,
    private val activeTimerNormalizer: ActiveTimerNormalizer,
    private val clock: UtcClock,
    private val timerOperationLock: TimerOperationLock,
    private val rowBuilder: ExportRowBuilder = ExportRowBuilder(),
) : ExportSnapshotProvider {
    override suspend fun prepare(workDate: LocalDate): PrepareExportSnapshotResult =
        timerOperationLock.mutex.withLock {
            val exportedAt = clock.now()
            when (activeTimerNormalizer.normalizeWhileLocked(exportedAt)) {
                is NormalizeTimerResult.ClockChanged ->
                    return@withLock PrepareExportSnapshotResult.ClockChanged
                NormalizeTimerResult.ActiveTimerChanged ->
                    return@withLock PrepareExportSnapshotResult.ActiveTimerChanged
                NormalizeTimerResult.NoActiveTimer,
                NormalizeTimerResult.NoChange,
                is NormalizeTimerResult.ClosedAtBoundary,
                is NormalizeTimerResult.Normalized,
                -> Unit
            }
            if (activeTimerRepository.readActiveTimerSnapshot() != null) {
                return@withLock PrepareExportSnapshotResult.ActiveTimerRunning
            }
            val tasks = taskRepository.readTasksWithIntervalsForDate(workDate)
            PrepareExportSnapshotResult.Ready(
                rowBuilder.build(
                    workDate = workDate,
                    exportedAt = exportedAt,
                    tasks = tasks,
                ),
            )
        }
}
