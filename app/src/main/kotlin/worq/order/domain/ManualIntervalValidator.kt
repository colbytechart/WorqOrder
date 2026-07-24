package worq.order.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import worq.order.model.DailyTask
import worq.order.model.WorkInterval

enum class OverlapOffsetChoice {
    EARLIER_OFFSET,
    LATER_OFFSET,
}

enum class ManualIntervalValidationError {
    START_IN_DST_GAP,
    STOP_IN_DST_GAP,
    START_DST_OVERLAP_REQUIRES_CHOICE,
    STOP_DST_OVERLAP_REQUIRES_CHOICE,
    MANUAL_INTERVAL_MUST_BE_CLOSED,
    RUNNING_INTERVAL_CANNOT_BE_EDITED,
    START_MUST_PRECEDE_STOP,
    OUTSIDE_TASK_DATE,
    OVERLAPS_EXISTING_INTERVAL,
    OVERLAPS_OPEN_INTERVAL,
    WOULD_CREATE_SECOND_OPEN_INTERVAL,
}

sealed interface ManualIntervalValidationResult {
    data class Valid(
        val start: Instant,
        val stop: Instant,
    ) : ManualIntervalValidationResult

    data class Invalid(
        val errors: Set<ManualIntervalValidationError>,
    ) : ManualIntervalValidationResult
}

sealed interface LocalDateTimeResolution {
    data class Resolved(
        val instant: Instant,
        val offset: ZoneOffset,
    ) : LocalDateTimeResolution

    data object Gap : LocalDateTimeResolution

    data object OverlapChoiceRequired : LocalDateTimeResolution
}

object LocalDateTimeResolver {
    fun resolve(
        localDateTime: LocalDateTime,
        zoneId: ZoneId,
        overlapChoice: OverlapOffsetChoice?,
    ): LocalDateTimeResolution {
        val offsets = zoneId.rules.getValidOffsets(localDateTime)
        return when (offsets.size) {
            0 -> LocalDateTimeResolution.Gap
            1 ->
                LocalDateTimeResolution.Resolved(
                    instant = localDateTime.toInstant(offsets.single()),
                    offset = offsets.single(),
                )
            else -> {
                val offset =
                    when (overlapChoice) {
                        OverlapOffsetChoice.EARLIER_OFFSET -> offsets.first()
                        OverlapOffsetChoice.LATER_OFFSET -> offsets.last()
                        null -> return LocalDateTimeResolution.OverlapChoiceRequired
                    }
                LocalDateTimeResolution.Resolved(
                    instant = localDateTime.toInstant(offset),
                    offset = offset,
                )
            }
        }
    }
}

object ManualIntervalValidator {
    fun validateLocal(
        task: DailyTask,
        startLocal: LocalDateTime,
        stopLocal: LocalDateTime?,
        existingIntervals: List<WorkInterval>,
        editingIntervalId: String? = null,
        startOverlapChoice: OverlapOffsetChoice? = null,
        stopOverlapChoice: OverlapOffsetChoice? = null,
    ): ManualIntervalValidationResult {
        val errors = linkedSetOf<ManualIntervalValidationError>()
        if (stopLocal == null) {
            errors += ManualIntervalValidationError.MANUAL_INTERVAL_MUST_BE_CLOSED
            if (existingIntervals.any { it.stop == null && it.id != editingIntervalId }) {
                errors += ManualIntervalValidationError.WOULD_CREATE_SECOND_OPEN_INTERVAL
            }
        }
        if (
            editingIntervalId != null &&
            existingIntervals.any { it.id == editingIntervalId && it.stop == null }
        ) {
            errors += ManualIntervalValidationError.RUNNING_INTERVAL_CANNOT_BE_EDITED
        }

        val startResolution =
            LocalDateTimeResolver.resolve(
                localDateTime = startLocal,
                zoneId = task.zoneId,
                overlapChoice = startOverlapChoice,
            )
        val stopResolution =
            stopLocal?.let {
                LocalDateTimeResolver.resolve(
                    localDateTime = it,
                    zoneId = task.zoneId,
                    overlapChoice = stopOverlapChoice,
                )
            }
        addResolutionError(
            resolution = startResolution,
            gapError = ManualIntervalValidationError.START_IN_DST_GAP,
            overlapError =
                ManualIntervalValidationError.START_DST_OVERLAP_REQUIRES_CHOICE,
            errors = errors,
        )
        stopResolution?.let {
            addResolutionError(
                resolution = it,
                gapError = ManualIntervalValidationError.STOP_IN_DST_GAP,
                overlapError =
                    ManualIntervalValidationError.STOP_DST_OVERLAP_REQUIRES_CHOICE,
                errors = errors,
            )
        }

        val start = (startResolution as? LocalDateTimeResolution.Resolved)?.instant
        val stop = (stopResolution as? LocalDateTimeResolution.Resolved)?.instant
        if (start == null || stop == null) {
            return ManualIntervalValidationResult.Invalid(errors)
        }
        return validateInstants(
            task = task,
            start = start,
            stop = stop,
            existingIntervals = existingIntervals,
            editingIntervalId = editingIntervalId,
            initialErrors = errors,
        )
    }

    fun validateInstants(
        task: DailyTask,
        start: Instant,
        stop: Instant?,
        existingIntervals: List<WorkInterval>,
        editingIntervalId: String? = null,
        initialErrors: Set<ManualIntervalValidationError> = emptySet(),
    ): ManualIntervalValidationResult {
        val errors = LinkedHashSet(initialErrors)
        if (stop == null) {
            errors += ManualIntervalValidationError.MANUAL_INTERVAL_MUST_BE_CLOSED
            if (existingIntervals.any { it.stop == null && it.id != editingIntervalId }) {
                errors += ManualIntervalValidationError.WOULD_CREATE_SECOND_OPEN_INTERVAL
            }
            return ManualIntervalValidationResult.Invalid(errors)
        }
        if (
            editingIntervalId != null &&
            existingIntervals.any { it.id == editingIntervalId && it.stop == null }
        ) {
            errors += ManualIntervalValidationError.RUNNING_INTERVAL_CANNOT_BE_EDITED
        }
        if (!start.isBefore(stop)) {
            errors += ManualIntervalValidationError.START_MUST_PRECEDE_STOP
        }

        val dayStart = task.workDate.atStartOfDay(task.zoneId).toInstant()
        val dayEnd = task.workDate.plusDays(1).atStartOfDay(task.zoneId).toInstant()
        if (start.isBefore(dayStart) || stop.isAfter(dayEnd)) {
            errors += ManualIntervalValidationError.OUTSIDE_TASK_DATE
        }

        if (start.isBefore(stop)) {
            existingIntervals
                .asSequence()
                .filter { it.id != editingIntervalId }
                .filter { existing ->
                    val existingStop = existing.stop
                    if (existingStop == null) {
                        stop.isAfter(existing.start)
                    } else {
                        start.isBefore(existingStop) && existing.start.isBefore(stop)
                    }
                }.forEach { existing ->
                    errors +=
                        if (existing.stop == null) {
                            ManualIntervalValidationError.OVERLAPS_OPEN_INTERVAL
                        } else {
                            ManualIntervalValidationError.OVERLAPS_EXISTING_INTERVAL
                        }
                }
        }

        return if (errors.isEmpty()) {
            ManualIntervalValidationResult.Valid(start = start, stop = stop)
        } else {
            ManualIntervalValidationResult.Invalid(errors)
        }
    }

    private fun addResolutionError(
        resolution: LocalDateTimeResolution,
        gapError: ManualIntervalValidationError,
        overlapError: ManualIntervalValidationError,
        errors: MutableSet<ManualIntervalValidationError>,
    ) {
        when (resolution) {
            LocalDateTimeResolution.Gap -> errors += gapError
            LocalDateTimeResolution.OverlapChoiceRequired -> errors += overlapError
            is LocalDateTimeResolution.Resolved -> Unit
        }
    }
}
