package worq.order.backup

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.sync.withLock
import worq.order.timer.TimerOperationLock
import worq.order.timer.UtcClock

sealed interface PreparePortableBackupResult {
    data class Ready(
        val backup: PreparedPortableBackup,
    ) : PreparePortableBackupResult

    data object TimerRunning : PreparePortableBackupResult

    data object InvalidLocalState : PreparePortableBackupResult
}

/**
 * Captures a logically validated state while the shared timer-operation lock prevents Start/Stop
 * from racing the snapshot. The later SAF write is deliberately separate and never mutates data.
 */
class PortableBackupCoordinator(
    private val snapshotReader: PortableBackupSnapshotReader,
    private val timerOperationLock: TimerOperationLock,
    private val clock: UtcClock,
    private val producer: PortableBackupProducer,
) {
    suspend fun prepare(): PreparePortableBackupResult =
        timerOperationLock.mutex.withLock {
            when (val result = snapshotReader.read()) {
                is PortableBackupSnapshotReadResult.Ready -> {
                    val createdAt = clock.now()
                    PreparePortableBackupResult.Ready(
                        PreparedPortableBackup(
                            data = result.data,
                            createdAt = createdAt,
                            producer = producer,
                            suggestedFileName = suggestedFileName(createdAt),
                        ),
                    )
                }
                PortableBackupSnapshotReadResult.TimerRunning ->
                    PreparePortableBackupResult.TimerRunning
                PortableBackupSnapshotReadResult.InvalidLocalState ->
                    PreparePortableBackupResult.InvalidLocalState
            }
        }

    companion object {
        private val FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss").withZone(ZoneOffset.UTC)

        fun suggestedFileName(createdAt: Instant): String =
            "WorqOrder_Backup_${FILE_TIME_FORMAT.format(createdAt)}.zip"
    }
}
