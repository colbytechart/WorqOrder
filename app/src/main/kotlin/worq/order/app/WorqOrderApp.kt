package worq.order.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import worq.order.data.ThemeMode
import worq.order.export.google.GoogleConnectionFailure
import worq.order.export.google.GoogleConnectionOperationResult
import worq.order.ui.settings.ApplicationSettingsViewModel
import worq.order.ui.settings.SettingsViewModel
import worq.order.ui.clients.ClientManagementScreen
import worq.order.ui.clients.ClientManagementViewModel
import worq.order.ui.main.MainEffect
import worq.order.ui.main.MainScreen
import worq.order.ui.main.MainViewModel
import worq.order.ui.settings.SettingsScreen
import worq.order.ui.settings.SettingsEffect
import worq.order.ui.tasks.CreateTaskScreen
import worq.order.ui.tasks.CreateTaskViewModel
import worq.order.ui.tasks.CreateTaskEffect
import worq.order.ui.tasks.EditTaskScreen
import worq.order.ui.tasks.EditTaskEffect
import worq.order.ui.tasks.EditTaskViewModel
import worq.order.ui.theme.WorqOrderTheme
import worq.order.export.CsvExportCoordinator

@Composable
fun WorqOrderRoot() {
    val application =
        LocalContext.current.applicationContext as WorqOrderApplication
    val factory =
        remember(application) {
            ApplicationSettingsViewModel.Factory(
                application.container.settingsRepository,
            )
        }
    val viewModel: ApplicationSettingsViewModel = viewModel(factory = factory)
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())

    WorqOrderTheme(darkTheme = darkTheme) {
        WorqOrderApp()
    }
}

internal fun resolveDarkTheme(
    themeMode: ThemeMode,
    systemInDarkTheme: Boolean,
): Boolean =
    when (themeMode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

@Composable
fun WorqOrderApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
    ) {
        composable(AppRoutes.MAIN) {
            val application =
                LocalContext.current.applicationContext as WorqOrderApplication
            val factory =
                remember(application) {
                    MainViewModel.Factory(application.container)
                }
            val viewModel: MainViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val csvDocumentLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(
                        CsvExportCoordinator.MIME_TYPE,
                    ),
                ) { documentUri ->
                    viewModel.onEvent(
                        worq.order.ui.main.MainEvent.CsvDocumentSelected(
                            documentUri?.toString(),
                        ),
                    )
                }

            LaunchedEffect(viewModel, navController) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is MainEffect.NavigateToCreateTask ->
                            navController.navigate(AppRoutes.createTask(effect.workDate))
                        is MainEffect.NavigateToEditTask ->
                            navController.navigate(AppRoutes.editTask(effect.taskId))
                        MainEffect.NavigateToSettings ->
                            navController.navigate(AppRoutes.SETTINGS)
                        MainEffect.NavigateToGoogleSheetsSettings ->
                            navController.navigate(AppRoutes.SETTINGS_GOOGLE_SETUP)
                        is MainEffect.LaunchCsvDocument ->
                            csvDocumentLauncher.launch(effect.suggestedFileName)
                    }
                }
            }
            MainScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
            )
        }
        composable(
            route = AppRoutes.CREATE_TASK,
            arguments =
                listOf(
                    navArgument(AppRoutes.CREATE_TASK_DATE_ARGUMENT) {
                        type = NavType.LongType
                    },
                ),
        ) { backStackEntry ->
            val application =
                LocalContext.current.applicationContext as WorqOrderApplication
            val workDate =
                LocalDate.ofEpochDay(
                    backStackEntry.arguments
                        ?.getLong(AppRoutes.CREATE_TASK_DATE_ARGUMENT)
                        ?: 0L,
                )
            val factory =
                remember(application, workDate) {
                    CreateTaskViewModel.Factory(
                        clientRepository = application.container.clientRepository,
                        taskMutationCoordinator =
                            application.container.taskMutationCoordinator,
                        workDate = workDate,
                    )
                }
            val viewModel: CreateTaskViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel, navController) {
                viewModel.effects.collect { effect ->
                    if (effect == CreateTaskEffect.NavigateBack) {
                        navController.popBackStack()
                    }
                }
            }
            CreateTaskScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
            )
        }
        composable(
            route = AppRoutes.EDIT_TASK,
            arguments =
                listOf(
                    navArgument(AppRoutes.EDIT_TASK_ARGUMENT) {
                        type = NavType.StringType
                    },
                ),
        ) { backStackEntry ->
            val application =
                LocalContext.current.applicationContext as WorqOrderApplication
            val taskId =
                backStackEntry.arguments
                    ?.getString(AppRoutes.EDIT_TASK_ARGUMENT)
                    .orEmpty()
            val factory =
                remember(application, taskId) {
                    EditTaskViewModel.Factory(
                        taskId = taskId,
                        taskRepository = application.container.taskRepository,
                        clientRepository = application.container.clientRepository,
                        activeTimerRepository =
                            application.container.activeTimerRepository,
                        taskMutationCoordinator =
                            application.container.taskMutationCoordinator,
                    )
                }
            val viewModel: EditTaskViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel, navController) {
                viewModel.effects.collect { effect ->
                    if (effect == EditTaskEffect.NavigateBack) {
                        navController.popBackStack()
                    }
                }
            }
            EditTaskScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsDestination(navController = navController)
        }
        composable(AppRoutes.SETTINGS_GOOGLE_SETUP) {
            SettingsDestination(
                navController = navController,
                showGoogleSetupRequired = true,
            )
        }
        composable(AppRoutes.CLIENT_MANAGEMENT) {
            val application =
                LocalContext.current.applicationContext as WorqOrderApplication
            val factory =
                remember(application) {
                    ClientManagementViewModel.Factory(
                        clientRepository = application.container.clientRepository,
                    )
                }
            val viewModel: ClientManagementViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            ClientManagementScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack,
            )
        }
    }
}

@Composable
private fun SettingsDestination(
    navController: NavHostController,
    showGoogleSetupRequired: Boolean = false,
) {
    val activity =
        requireNotNull(LocalActivity.current as? ComponentActivity) {
            "Google settings require a ComponentActivity host"
        }
    val application =
        LocalContext.current.applicationContext as WorqOrderApplication
    val factory =
        remember(application) {
            SettingsViewModel.Factory(
                settingsRepository = application.container.settingsRepository,
                activeTimerRepository =
                    application.container.activeTimerRepository,
                zoneIdProvider = application.container.zoneIdProvider,
                googleConnectionRepository =
                    application.container.googleConnectionRepository,
            )
        }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleConnectionCoordinator =
        remember(application, activity) {
            application.container.createGoogleConnectionCoordinator(activity)
        }
    LaunchedEffect(
        viewModel,
        googleConnectionCoordinator,
    ) {
        viewModel.effects.collect { effect ->
            val result =
                try {
                    when (effect) {
                        SettingsEffect.SignInToGoogle ->
                            googleConnectionCoordinator.signIn()
                        is SettingsEffect.ValidateAndConnectSpreadsheet ->
                            googleConnectionCoordinator.validateAndConnect(
                                effect.spreadsheetInput,
                            )
                        SettingsEffect.DisconnectSpreadsheet ->
                            googleConnectionCoordinator.disconnectSpreadsheet()
                        SettingsEffect.SignOutOfGoogle ->
                            googleConnectionCoordinator.signOut()
                    }
                } catch (cancellation: CancellationException) {
                    viewModel.onGoogleOperationInterrupted()
                    throw cancellation
                } catch (_: Exception) {
                    GoogleConnectionOperationResult.Failed(
                        GoogleConnectionFailure.LOCAL_STORAGE,
                    )
                }
            viewModel.onGoogleOperationResult(result)
        }
    }
    SettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = navController::popBackStack,
        onOpenClientManagement = {
            navController.navigate(AppRoutes.CLIENT_MANAGEMENT)
        },
        showGoogleSetupRequired = showGoogleSetupRequired,
    )
}
