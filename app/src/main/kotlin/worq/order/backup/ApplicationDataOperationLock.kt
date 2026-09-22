package worq.order.backup

import kotlinx.coroutines.sync.Mutex

/**
 * Outermost operation gate for a coherent local application-state replacement.
 *
 * The portable-replacement path always obtains this lock before [worq.order.timer.TimerOperationLock].
 * It is intentionally application-scoped rather than tied to a screen so process-start recovery
 * and a later Settings action serialize through the same gate.
 */
class ApplicationDataOperationLock(
    val mutex: Mutex = Mutex(),
)
