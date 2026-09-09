package worq.order.timer

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import worq.order.domain.SelectionCoordinator
import worq.order.domain.SelectionReconciliationResult

sealed interface TimerRecoveryResult {
    data class Recovered(
        val normalizedSplitCount: Int,
        val selectionResult: SelectionReconciliationResult,
    ) : TimerRecoveryResult

    data class ClockChanged(
        val intervalStart: Instant,
        val evaluationInstant: Instant,
        val selectionResult: SelectionReconciliationResult,
    ) : TimerRecoveryResult

    data class ActiveTimerChanged(
        val selectionResult: SelectionReconciliationResult,
    ) : TimerRecoveryResult

    data class ClosedAtBoundary(
        val boundary: Instant,
        val selectionResult: SelectionReconciliationResult,
    ) : TimerRecoveryResult
}

/**
 * Application-scoped entry point for startup and foreground timer recovery.
 *
 * Room remains authoritative. This coordinator waits for the effective ZoneId, normalizes the
 * persisted active interval, reconciles selection, and lets [ActiveTimerNormalizer] rebuild the
 * process-local monotonic display anchor when necessary.
 */
class TimerRecoveryCoordinator(
    private val activeTimerNormalizer: ActiveTimerNormalizer,
    private val selectionCoordinator: SelectionCoordinator,
    private val zoneIdProvider: EffectiveZoneIdProvider,
    private val clock: UtcClock,
    private val recoveryMutex: Mutex = Mutex(),
) {
    suspend fun recover(): TimerRecoveryResult =
        recoveryMutex.withLock {
            zoneIdProvider.awaitZoneId()
            val evaluationInstant = clock.now()
            val normalization =
                activeTimerNormalizer.normalize(evaluationInstant)
            val selection = selectionCoordinator.reconcileForToday()

            when (normalization) {
                is NormalizeTimerResult.ClockChanged ->
                    TimerRecoveryResult.ClockChanged(
                        intervalStart = normalization.intervalStart,
                        evaluationInstant = normalization.evaluationInstant,
                        selectionResult = selection,
                    )
                NormalizeTimerResult.ActiveTimerChanged ->
                    TimerRecoveryResult.ActiveTimerChanged(selection)
                NormalizeTimerResult.NoActiveTimer,
                NormalizeTimerResult.NoChange,
                ->
                    TimerRecoveryResult.Recovered(
                        normalizedSplitCount = 0,
                        selectionResult = selection,
                    )
                is NormalizeTimerResult.Normalized ->
                    TimerRecoveryResult.Recovered(
                        normalizedSplitCount = normalization.splitCount,
                        selectionResult = selection,
                    )
                is NormalizeTimerResult.ClosedAtBoundary ->
                    TimerRecoveryResult.ClosedAtBoundary(
                        boundary = normalization.boundary,
                        selectionResult = selection,
                    )
            }
        }
}
