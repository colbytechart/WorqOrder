package worq.order.app

import android.content.Context
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import worq.order.data.ActiveTimerRepository
import worq.order.data.ClientImportRepository
import worq.order.data.ClientRepository
import worq.order.data.EmployeeRepository
import worq.order.data.GoogleConnectionRepository
import worq.order.data.SelectedTaskRepository
import worq.order.data.SettingsRepository
import worq.order.data.TaskRepository
import worq.order.data.UuidEntityIdGenerator
import worq.order.data.document.AndroidClientCsvDocumentSource
import worq.order.data.local.RoomActiveTimerRepository
import worq.order.data.local.RoomClientImportRepository
import worq.order.data.local.RoomClientRepository
import worq.order.data.local.RoomEmployeeRepository
import worq.order.data.local.RoomTaskRepository
import worq.order.data.local.WorqOrderDatabase
import worq.order.data.preferences.PreferencesGoogleConnectionRepository
import worq.order.data.preferences.PreferencesSelectedTaskRepository
import worq.order.data.preferences.PreferencesSettingsRepository
import worq.order.data.preferences.worqOrderPreferencesDataStore
import worq.order.domain.ClientCsvImportCoordinator
import worq.order.domain.ClientCsvParser
import worq.order.domain.SelectionCoordinator
import worq.order.domain.TaskMutationCoordinator
import worq.order.export.CsvExportCoordinator
import worq.order.export.ExportSnapshotCoordinator
import worq.order.export.XlsxExportCoordinator
import worq.order.export.csv.AndroidDocumentOutputDestination
import worq.order.export.csv.DocumentOutputDestination
import worq.order.export.google.AndroidGoogleAccountAuthorizer
import worq.order.export.google.GoogleConnectionCoordinator
import worq.order.export.google.GoogleSheetsExportCoordinator
import worq.order.export.google.RestGoogleSheetsGateway
import worq.order.export.xlsx.AndroidBinaryDocumentOutputDestination
import worq.order.export.xlsx.BinaryDocumentOutputDestination
import worq.order.timer.ActiveTimerNormalizer
import worq.order.timer.AndroidDeviceZoneIdSource
import worq.order.timer.AndroidMonotonicTimeSource
import worq.order.timer.CurrentDateProvider
import worq.order.timer.DeviceZoneIdSource
import worq.order.timer.EffectiveZoneIdProvider
import worq.order.timer.LiveTimerSession
import worq.order.timer.SystemUtcClock
import worq.order.timer.SettingsEffectiveZoneIdProvider
import worq.order.timer.TimerCoordinator
import worq.order.timer.TimerOperationLock
import worq.order.timer.TimerRecoveryCoordinator
import worq.order.timer.UtcClock

/**
 * Application-scoped dependency boundary.
 */
interface ApplicationContainer {
    val clientRepository: ClientRepository
    val clientCsvImportCoordinator: ClientCsvImportCoordinator
    val employeeRepository: EmployeeRepository
    val taskRepository: TaskRepository
    val activeTimerRepository: ActiveTimerRepository
    val selectedTaskRepository: SelectedTaskRepository
    val settingsRepository: SettingsRepository
    val googleConnectionRepository: GoogleConnectionRepository
    val utcClock: UtcClock
    val zoneIdProvider: EffectiveZoneIdProvider
    val currentDateProvider: CurrentDateProvider
    val liveTimerSession: LiveTimerSession
    val selectionCoordinator: SelectionCoordinator
    val taskMutationCoordinator: TaskMutationCoordinator
    val timerCoordinator: TimerCoordinator
    val activeTimerNormalizer: ActiveTimerNormalizer
    val timerRecoveryCoordinator: TimerRecoveryCoordinator
    val exportSnapshotCoordinator: ExportSnapshotCoordinator
    val csvExportCoordinator: CsvExportCoordinator
    val xlsxExportCoordinator: XlsxExportCoordinator
    val documentOutputDestination: DocumentOutputDestination
    val binaryDocumentOutputDestination: BinaryDocumentOutputDestination

    fun createGoogleConnectionCoordinator(
        activity: ComponentActivity,
    ): GoogleConnectionCoordinator

    fun createGoogleSheetsExportCoordinator(
        activity: ComponentActivity,
    ): GoogleSheetsExportCoordinator
}

internal class DefaultApplicationContainer(
    context: Context,
) : ApplicationContainer {
    private val applicationContext = context.applicationContext
    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    private val clientImportRepository: ClientImportRepository by lazy {
        RoomClientImportRepository(
            clientDao = database.clientDao(),
            idGenerator = UuidEntityIdGenerator,
            clock = SystemUtcClock,
        )
    }

    override val clientCsvImportCoordinator: ClientCsvImportCoordinator by lazy {
        ClientCsvImportCoordinator(
            documentSource = AndroidClientCsvDocumentSource(applicationContext.contentResolver),
            parser = ClientCsvParser(),
            repository = clientImportRepository,
        )
    }

    override val employeeRepository: EmployeeRepository by lazy {
        RoomEmployeeRepository(
            dao = database.employeeDao(),
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

    override val settingsRepository: SettingsRepository by lazy {
        PreferencesSettingsRepository(
            dataStore = applicationContext.worqOrderPreferencesDataStore,
            activeTimerRepository = activeTimerRepository,
            timerOperationLock = timerOperationLock,
        )
    }

    override val googleConnectionRepository: GoogleConnectionRepository by lazy {
        PreferencesGoogleConnectionRepository(
            dataStore = applicationContext.worqOrderPreferencesDataStore,
        )
    }

    private val deviceZoneIdSource: DeviceZoneIdSource by lazy {
        AndroidDeviceZoneIdSource(applicationContext)
    }

    override val liveTimerSession by lazy {
        LiveTimerSession(AndroidMonotonicTimeSource)
    }

    override val utcClock: UtcClock = SystemUtcClock

    override val zoneIdProvider: EffectiveZoneIdProvider by lazy {
        SettingsEffectiveZoneIdProvider(
            settingsRepository = settingsRepository,
            deviceZoneIdSource = deviceZoneIdSource,
            activeTimerRepository = activeTimerRepository,
            applicationScope = applicationScope,
        )
    }

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

    override val timerRecoveryCoordinator: TimerRecoveryCoordinator by lazy {
        TimerRecoveryCoordinator(
            activeTimerNormalizer = activeTimerNormalizer,
            selectionCoordinator = selectionCoordinator,
            zoneIdProvider = zoneIdProvider,
            clock = utcClock,
        )
    }

    override val exportSnapshotCoordinator: ExportSnapshotCoordinator by lazy {
        ExportSnapshotCoordinator(
            taskRepository = taskRepository,
            activeTimerNormalizer = activeTimerNormalizer,
            clock = utcClock,
            timerOperationLock = timerOperationLock,
        )
    }

    override val csvExportCoordinator: CsvExportCoordinator by lazy {
        CsvExportCoordinator(exportSnapshotCoordinator)
    }

    override val xlsxExportCoordinator: XlsxExportCoordinator by lazy {
        XlsxExportCoordinator(exportSnapshotCoordinator)
    }

    override val documentOutputDestination: DocumentOutputDestination by lazy {
        AndroidDocumentOutputDestination(applicationContext.contentResolver)
    }

    override val binaryDocumentOutputDestination: BinaryDocumentOutputDestination by lazy {
        AndroidBinaryDocumentOutputDestination(applicationContext.contentResolver)
    }

    private val googleSheetsGateway: RestGoogleSheetsGateway by lazy {
        RestGoogleSheetsGateway()
    }

    override fun createGoogleConnectionCoordinator(
        activity: ComponentActivity,
    ): GoogleConnectionCoordinator =
        GoogleConnectionCoordinator(
            authorizer =
                AndroidGoogleAccountAuthorizer(
                    activity = activity,
                    webClientId = worq.order.BuildConfig.GOOGLE_WEB_CLIENT_ID,
                ),
            sheetsGateway = googleSheetsGateway,
            connectionRepository = googleConnectionRepository,
            now = utcClock::now,
        )

    override fun createGoogleSheetsExportCoordinator(
        activity: ComponentActivity,
    ): GoogleSheetsExportCoordinator =
        GoogleSheetsExportCoordinator(
            authorizer =
                AndroidGoogleAccountAuthorizer(
                    activity = activity,
                    webClientId = worq.order.BuildConfig.GOOGLE_WEB_CLIENT_ID,
                ),
            gateway = googleSheetsGateway,
            connectionRepository = googleConnectionRepository,
            snapshotProvider = exportSnapshotCoordinator,
        )
}
