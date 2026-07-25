package worq.order.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import worq.order.data.ActiveTimerRepository
import worq.order.data.SettingsRepository
import worq.order.data.TimeZoneMode

interface DeviceZoneIdSource {
    fun currentZoneId(): ZoneId

    fun observeZoneId(): Flow<ZoneId>
}

class AndroidDeviceZoneIdSource(
    context: Context,
) : DeviceZoneIdSource {
    private val applicationContext = context.applicationContext

    override fun currentZoneId(): ZoneId = ZoneId.systemDefault()

    override fun observeZoneId(): Flow<ZoneId> =
        callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        trySend(currentZoneId())
                    }
                }
            trySend(currentZoneId())
            ContextCompat.registerReceiver(
                applicationContext,
                receiver,
                IntentFilter(Intent.ACTION_TIMEZONE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            awaitClose {
                applicationContext.unregisterReceiver(receiver)
            }
        }.distinctUntilChanged()
}

class SettingsEffectiveZoneIdProvider(
    settingsRepository: SettingsRepository,
    deviceZoneIdSource: DeviceZoneIdSource,
    activeTimerRepository: ActiveTimerRepository,
    applicationScope: CoroutineScope,
) : EffectiveZoneIdProvider {
    private val effectiveZoneId =
        MutableStateFlow(deviceZoneIdSource.currentZoneId())
    private val initialZoneId = CompletableDeferred<ZoneId>()

    init {
        combine(
            settingsRepository.observeSettings(),
            deviceZoneIdSource.observeZoneId(),
            activeTimerRepository.observeActiveTimer(),
        ) { settings, deviceZoneId, activeTimer ->
            activeTimer?.boundaryZoneId
                ?: if (
                    settings.timeZoneMode == TimeZoneMode.MANUAL &&
                    settings.manualZoneId != null
                ) {
                    settings.manualZoneId
                } else {
                    deviceZoneId
                }
        }.distinctUntilChanged()
            .onEach {
                effectiveZoneId.value = it
                initialZoneId.complete(it)
            }
            .launchIn(applicationScope)
    }

    override fun zoneId(): ZoneId = effectiveZoneId.value

    override fun observeZoneId(): Flow<ZoneId> = effectiveZoneId.asStateFlow()

    override suspend fun awaitZoneId(): ZoneId = initialZoneId.await()
}
