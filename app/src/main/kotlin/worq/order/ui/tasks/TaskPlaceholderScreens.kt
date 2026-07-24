package worq.order.ui.tasks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.PlaceholderDestination

@Composable
fun CreateTaskScreen(onNavigateBack: () -> Unit) {
    PlaceholderDestination(
        titleRes = R.string.create_task,
        onNavigateBack = onNavigateBack,
    ) {
        Text(stringResource(R.string.create_task_placeholder))
    }
}

@Composable
fun EditTaskScreen(
    taskId: String,
    onNavigateBack: () -> Unit,
) {
    PlaceholderDestination(
        titleRes = R.string.edit_task,
        onNavigateBack = onNavigateBack,
    ) {
        Text(stringResource(R.string.edit_task_placeholder, taskId))
    }
}
