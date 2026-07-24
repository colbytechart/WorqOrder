package worq.order.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import worq.order.ui.clients.ClientManagementScreen
import worq.order.ui.main.MainScreen
import worq.order.ui.main.MainViewModel
import worq.order.ui.settings.SettingsScreen
import worq.order.ui.tasks.CreateTaskScreen
import worq.order.ui.tasks.EditTaskScreen

@Composable
fun WorqOrderApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
    ) {
        composable(AppRoutes.MAIN) {
            val viewModel: MainViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MainScreen(
                uiState = uiState,
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onCreateTask = { navController.navigate(AppRoutes.CREATE_TASK) },
            )
        }
        composable(AppRoutes.CREATE_TASK) {
            CreateTaskScreen(onNavigateBack = navController::popBackStack)
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
            ClientManagementScreen(onNavigateBack = navController::popBackStack)
        }
    }
}
