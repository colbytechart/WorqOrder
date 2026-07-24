package worq.order.data.local

class ActiveTimerAlreadyExistsException :
    IllegalStateException("A work interval is already active")

class PersistenceInvariantException(
    message: String,
) : IllegalStateException(message)
