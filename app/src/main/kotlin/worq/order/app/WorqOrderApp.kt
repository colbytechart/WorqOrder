package worq.order.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import worq.order.ui.clients.ClientManagementScreen
import worq.order.ui.clients.ClientManagementViewModel
import worq.order.ui.main.MainEffect
import worq.order.ui.main.MainScreen
import worq.order.ui.main.MainViewModel
import worq.order.ui.settings.SettingsScreen
import worq.order.ui.tasks.CreateTaskScreen
import worq.order.ui.tasks.CreateTaskViewModel
import worq.order.ui.tasks.EditTaskScreen

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

            LaunchedEffect(viewModel, navController) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is MainEffect.NavigateToCreateTask ->
                            navController.navigate(AppRoutes.CREATE_TASK)
                        MainEffect.NavigateToSettings ->
                            navController.navigate(AppRoutes.SETTINGS)
                    }
                }
            }
            MainScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
            )
        }
        composable(AppRoutes.CREATE_TASK) {
            val application =
                LocalContext.current.applicationContext as WorqOrderApplication
            val factory =
                remember(application) {
                    CreateTaskViewModel.Factory(
                        clientRepository = application.container.clientRepository,
                    )
                }
            val viewModel: CreateTaskViewModel = viewModel(factory = factory)
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CreateTaskScreen(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                onNavigateBack = navController::popBackStack,
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
            EditTaskScreen(
                taskId =
                    backStackEntry.arguments
                        ?.getString(AppRoutes.EDIT_TASK_ARGUMENT)
                        .orEmpty(),
                onNavigateBack = navController::popBackStack,
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = navController::popBackStack,
                onOpenClientManagement = {
                    navController.navigate(AppRoutes.CLIENT_MANAGEMENT)
                },
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
