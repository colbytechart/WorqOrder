package worq.order.timer

import java.time.Instant

fun interface UtcClock {
    fun now(): Instant
}

object SystemUtcClock : UtcClock {
    override fun now(): Instant = Instant.now()
}
