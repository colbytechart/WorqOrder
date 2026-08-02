package worq.order.domain

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingMinutesTest {
    @Test
    fun zeroDurationBillsZero() {
        assertEquals(0L, BillingMinutes.fromDuration(Duration.ZERO))
    }

    @Test
    fun positiveDurationRoundsUpToQuarterHour() {
        assertEquals(15L, BillingMinutes.fromDuration(Duration.ofMillis(1)))
        assertEquals(15L, BillingMinutes.fromDuration(Duration.ofMinutes(15)))
        assertEquals(30L, BillingMinutes.fromDuration(Duration.ofMinutes(15).plusMillis(1)))
        assertEquals(
            285L,
            BillingMinutes.fromDuration(
                Duration.ofMinutes(13).plusSeconds(11)
                    .plusHours(3).plusMinutes(3).plusSeconds(45)
                    .plusMinutes(76),
            ),
        )
    }

    @Test
    fun negativeDurationIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingMinutes.fromDuration(Duration.ofMillis(-1))
        }
    }
}
