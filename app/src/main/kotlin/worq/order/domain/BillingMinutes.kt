package worq.order.domain

import java.time.Duration

object BillingMinutes {
    private const val BUCKET_MILLIS = 15L * 60L * 1_000L
    private const val MINUTES_PER_BUCKET = 15L

    fun fromDuration(duration: Duration): Long {
        require(!duration.isNegative) { "Billing duration must not be negative" }
        val totalMillis = duration.toMillis()
        if (totalMillis == 0L) return 0L
        val wholeBuckets = totalMillis / BUCKET_MILLIS
        val bucketCount = wholeBuckets + if (totalMillis % BUCKET_MILLIS == 0L) 0L else 1L
        return Math.multiplyExact(bucketCount, MINUTES_PER_BUCKET)
    }
}
