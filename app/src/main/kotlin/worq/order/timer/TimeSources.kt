package worq.order.timer

import android.os.SystemClock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

fun interface MonotonicTimeSource {
    fun elapsedRealtimeNanos(): Long
}

object AndroidMonotonicTimeSource : MonotonicTimeSource {
    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()
}

fun interface EffectiveZoneIdProvider {
    fun zoneId(): ZoneId

    fun observeZoneId(): Flow<ZoneId> = flowOf(zoneId())

    suspend fun awaitZoneId(): ZoneId = zoneId()
}

object DeviceZoneIdProvider : EffectiveZoneIdProvider {
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

class CurrentDateProvider(
    private val clock: UtcClock,
    private val zoneIdProvider: EffectiveZoneIdProvider,
) {
    fun today(): LocalDate = clock.now().atZone(zoneIdProvider.zoneId()).toLocalDate()
}
