package worq.order.app

import android.content.Context
import worq.order.data.ActiveTimerRepository
import worq.order.data.ClientRepository
import worq.order.data.TaskRepository
import worq.order.data.UuidEntityIdGenerator
import worq.order.data.local.RoomActiveTimerRepository
import worq.order.data.local.RoomClientRepository
import worq.order.data.local.RoomTaskRepository
import worq.order.data.local.WorqOrderDatabase
import worq.order.timer.SystemUtcClock

/**
 * Application-scoped dependency boundary.
 */
interface ApplicationContainer {
    val clientRepository: ClientRepository
    val taskRepository: TaskRepository
    val activeTimerRepository: ActiveTimerRepository
}

internal class DefaultApplicationContainer(
    context: Context,
) : ApplicationContainer {
    private val database: WorqOrderDatabase by lazy {
        WorqOrderDatabase.create(context)
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
}
