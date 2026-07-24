package worq.order.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.model.DailyTask
import worq.order.model.WorkInterval

class ManualIntervalValidatorTest {
    @Test
    fun exactDateBoundariesAndAdjacentIntervalsAreValid() {
        val task = task(LocalDate.of(2026, 7, 24), NEW_YORK)
        val dayStart = task.workDate.atStartOfDay(task.zoneId).toInstant()
        val noon = dayStart.plusSeconds(12 * 60 * 60L)
        val existing = interval("existing", task.id, dayStart, noon)
        val result =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = noon,
                stop = task.workDate.plusDays(1).atStartOfDay(task.zoneId).toInstant(),
                existingIntervals = listOf(existing),
            )

        assertTrue(result is ManualIntervalValidationResult.Valid)
    }

    @Test
    fun reversedAndOutsideDateIntervalsAreRejected() {
        val task = task(LocalDate.of(2026, 7, 24), NEW_YORK)
        val dayStart = task.workDate.atStartOfDay(task.zoneId).toInstant()
        val reversed =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.plusSeconds(60),
                stop = dayStart,
                existingIntervals = emptyList(),
            ) as ManualIntervalValidationResult.Invalid
        val outside =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.minusSeconds(1),
                stop = dayStart.plusSeconds(1),
                existingIntervals = emptyList(),
            ) as ManualIntervalValidationResult.Invalid

        assertTrue(
            ManualIntervalValidationError.START_MUST_PRECEDE_STOP in reversed.errors,
        )
        assertTrue(ManualIntervalValidationError.OUTSIDE_TASK_DATE in outside.errors)
    }

    @Test
    fun completedAndOpenIntervalOverlapAreDistinguished() {
        val task = task(LocalDate.of(2026, 7, 24), NEW_YORK)
        val dayStart = task.workDate.atStartOfDay(task.zoneId).toInstant()
        val completed =
            interval(
                id = "completed",
                taskId = task.id,
                start = dayStart.plusSeconds(60),
                stop = dayStart.plusSeconds(120),
            )
        val open =
            interval(
                id = "open",
                taskId = task.id,
                start = dayStart.plusSeconds(180),
                stop = null,
            )
        val completedOverlap =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.plusSeconds(90),
                stop = dayStart.plusSeconds(150),
                existingIntervals = listOf(completed),
            ) as ManualIntervalValidationResult.Invalid
        val openOverlap =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.plusSeconds(200),
                stop = dayStart.plusSeconds(240),
                existingIntervals = listOf(open),
            ) as ManualIntervalValidationResult.Invalid

        assertTrue(
            ManualIntervalValidationError.OVERLAPS_EXISTING_INTERVAL in
                completedOverlap.errors,
        )
        assertTrue(
            ManualIntervalValidationError.OVERLAPS_OPEN_INTERVAL in openOverlap.errors,
        )
    }

    @Test
    fun openManualProposalAndRunningIntervalEditAreRejected() {
        val task = task(LocalDate.of(2026, 7, 24), NEW_YORK)
        val dayStart = task.workDate.atStartOfDay(task.zoneId).toInstant()
        val running =
            interval(
                id = "running",
                taskId = task.id,
                start = dayStart.plusSeconds(60),
                stop = null,
            )
        val openProposal =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.plusSeconds(180),
                stop = null,
                existingIntervals = listOf(running),
            ) as ManualIntervalValidationResult.Invalid
        val runningEdit =
            ManualIntervalValidator.validateInstants(
                task = task,
                start = dayStart.plusSeconds(60),
                stop = dayStart.plusSeconds(120),
                existingIntervals = listOf(running),
                editingIntervalId = running.id,
            ) as ManualIntervalValidationResult.Invalid

        assertTrue(
            ManualIntervalValidationError.MANUAL_INTERVAL_MUST_BE_CLOSED in
                openProposal.errors,
        )
        assertTrue(
            ManualIntervalValidationError.WOULD_CREATE_SECOND_OPEN_INTERVAL in
                openProposal.errors,
        )
        assertTrue(
            ManualIntervalValidationError.RUNNING_INTERVAL_CANNOT_BE_EDITED in
                runningEdit.errors,
        )
    }

    @Test
    fun springForwardGapIsRejected() {
        val task = task(LocalDate.of(2026, 3, 8), NEW_YORK)

        val result =
            ManualIntervalValidator.validateLocal(
                task = task,
                startLocal = LocalDateTime.of(2026, 3, 8, 2, 30),
                stopLocal = LocalDateTime.of(2026, 3, 8, 3, 30),
                existingIntervals = emptyList(),
            ) as ManualIntervalValidationResult.Invalid

        assertTrue(ManualIntervalValidationError.START_IN_DST_GAP in result.errors)
    }

    @Test
    fun fallBackOverlapRequiresAndHonorsExplicitOffsetChoice() {
        val task = task(LocalDate.of(2026, 11, 1), NEW_YORK)
        val ambiguous =
            ManualIntervalValidator.validateLocal(
                task = task,
                startLocal = LocalDateTime.of(2026, 11, 1, 1, 15),
                stopLocal = LocalDateTime.of(2026, 11, 1, 1, 45),
                existingIntervals = emptyList(),
            ) as ManualIntervalValidationResult.Invalid
        val resolved =
            ManualIntervalValidator.validateLocal(
                task = task,
                startLocal = LocalDateTime.of(2026, 11, 1, 1, 15),
                stopLocal = LocalDateTime.of(2026, 11, 1, 1, 45),
                existingIntervals = emptyList(),
                startOverlapChoice = OverlapOffsetChoice.EARLIER_OFFSET,
                stopOverlapChoice = OverlapOffsetChoice.LATER_OFFSET,
            )

        assertTrue(
            ManualIntervalValidationError.START_DST_OVERLAP_REQUIRES_CHOICE in
                ambiguous.errors,
        )
        assertTrue(
            ManualIntervalValidationError.STOP_DST_OVERLAP_REQUIRES_CHOICE in
                ambiguous.errors,
        )
        assertTrue(resolved is ManualIntervalValidationResult.Valid)
        resolved as ManualIntervalValidationResult.Valid
        assertEquals(90 * 60L, resolved.stop.epochSecond - resolved.start.epochSecond)
        assertFalse(resolved.stop.isBefore(resolved.start))
    }

    private fun task(
        date: LocalDate,
        zoneId: ZoneId,
    ) = DailyTask(
        id = "task-1",
        seriesId = "series-1",
        clientId = "client-1",
        description = "Task",
        workDate = date,
        zoneId = zoneId,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private fun interval(
        id: String,
        taskId: String,
        start: Instant,
        stop: Instant?,
    ) = WorkInterval(
        id = id,
        taskId = taskId,
        ordinal = 1,
        start = start,
        stop = stop,
        wasManuallyEdited = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

    private companion object {
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    }
}
