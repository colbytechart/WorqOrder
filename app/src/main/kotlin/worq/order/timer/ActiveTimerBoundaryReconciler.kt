package worq.order.timer

import java.time.Instant

/** Testable boundary-normalization entry point used by lifecycle and scheduled work. */
fun interface ActiveTimerBoundaryReconciler {
    suspend fun normalize(evaluationInstant: Instant): NormalizeTimerResult
}
