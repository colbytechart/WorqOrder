package worq.order.app

import android.content.Context
import worq.order.data.ActiveTimerRepository
import worq.order.data.ClientRepository
import worq.order.data.SelectedTaskRepository
import worq.order.data.TaskRepository
import worq.order.data.UuidEntityIdGenerator
import worq.order.data.local.RoomActiveTimerRepository
import worq.order.data.local.RoomClientRepository
import worq.order.data.local.RoomTaskRepository
import worq.order.data.local.WorqOrderDatabase
import worq.order.data.preferences.PreferencesSelectedTaskRepository
import worq.order.data.preferences.worqOrderPreferencesDataStore
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.AndroidMonotonicTimeSource
import worq.order.timer.CurrentDateProvider
import worq.order.timer.DeviceZoneIdProvider
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.SystemUtcClock
import worq.order.timer.TimerCoordinator
import worq.order.timer.TimerOperationLock
import worq.order.timer.UtcClock

/**
 * Application-scoped dependency boundary.
 */
interface ApplicationContainer {
    val clientRepository: ClientRepository
    val taskRepository: TaskRepository
    val activeTimerRepository: ActiveTimerRepository
    val selectedTaskRepository: SelectedTaskRepository
    val utcClock: UtcClock
    val zoneIdProvider: EffectiveZoneIdProvider
    val currentDateProvider: CurrentDateProvider
    val liveTimerSession: LiveTimerSession
    val selectionCoordinator: SelectionCoordinator
    val taskMutationCoordinator: TaskMutationCoordinator
    val timerCoordinator: TimerCoordinator
    val activeTimerNormalizer: ActiveTimerNormalizer
}

internal class DefaultApplicationContainer(
    context: Context,
) : ApplicationContainer {
    private val applicationContext = context.applicationContext

    private val database: WorqOrderDatabase by lazy {
        WorqOrderDatabase.create(applicationContext)
    }

    override val clientRepository: ClientRepository by lazy {
        RoomClientRepository(
            clientDao = database.clientDao(),
            idGenerator = UuidEntityIdGenerator,
            clock = SystemUtcClock,
        )
    }

    override val taskRepository: TaskRepository by lazy {
        RoomTaskRepository(
            taskDao = database.taskDao(),
            workIntervalDao = database.workIntervalDao(),
            idGenerator = UuidEntityIdGenerator,
            clock = SystemUtcClock,
        )
    }

    override val activeTimerRepository: ActiveTimerRepository by lazy {
        RoomActiveTimerRepository(
            activeTimerDao = database.activeTimerDao(),
            idGenerator = UuidEntityIdGenerator,
            clock = SystemUtcClock,
        )
    }

    override val selectedTaskRepository: SelectedTaskRepository by lazy {
        PreferencesSelectedTaskRepository(
            dataStore = applicationContext.worqOrderPreferencesDataStore,
        )
    }

    private val timerOperationLock by lazy {
        TimerOperationLock()
    }

    override val liveTimerSession by lazy {
        LiveTimerSession(AndroidMonotonicTimeSource)
    }

    override val utcClock: UtcClock = SystemUtcClock

    override val zoneIdProvider: EffectiveZoneIdProvider = DeviceZoneIdProvider

    override val currentDateProvider by lazy {
        CurrentDateProvider(
            clock = utcClock,
            zoneIdProvider = zoneIdProvider,
        )
    }

    override val selectionCoordinator: SelectionCoordinator by lazy {
        SelectionCoordinator(
            selectedTaskRepository = selectedTaskRepository,
            taskRepository = taskRepository,
            activeTimerRepository = activeTimerRepository,
            currentDateProvider = currentDateProvider,
            zoneIdProvider = zoneIdProvider,
        )
    }

    override val taskMutationCoordinator: TaskMutationCoordinator by lazy {
        TaskMutationCoordinator(
            taskRepository = taskRepository,
            selectionCoordinator = selectionCoordinator,
            currentDateProvider = currentDateProvider,
            zoneIdProvider = zoneIdProvider,
        )
    }

    override val timerCoordinator: TimerCoordinator by lazy {
        TimerCoordinator(
            activeTimerRepository = activeTimerRepository,
            taskRepository = taskRepository,
            selectedTaskRepository = selectedTaskRepository,
            clock = utcClock,
            zoneIdProvider = zoneIdProvider,
            liveTimerSession = liveTimerSession,
            operationLock = timerOperationLock,
        )
    }

    override val activeTimerNormalizer: ActiveTimerNormalizer by lazy {
        ActiveTimerNormalizer(
            activeTimerRepository = activeTimerRepository,
            taskRepository = taskRepository,
            selectedTaskRepository = selectedTaskRepository,
            clock = utcClock,
            liveTimerSession = liveTimerSession,
            operationLock = timerOperationLock,
        )
    }
}
