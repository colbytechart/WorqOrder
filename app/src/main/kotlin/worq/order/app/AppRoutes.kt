package worq.order.app

object AppRoutes {
    const val MAIN = "main"
    const val CREATE_TASK = "task/create"
    const val EDIT_TASK_ARGUMENT = "taskId"
    const val EDIT_TASK = "task/{$EDIT_TASK_ARGUMENT}/edit"
    const val SETTINGS = "settings"
    const val CLIENT_MANAGEMENT = "settings/clients"

    val destinationPatterns =
        listOf(
            MAIN,
            CREATE_TASK,
            EDIT_TASK,
            SETTINGS,
            CLIENT_MANAGEMENT,
        )

    fun editTask(taskId: String): String {
        require(taskId.isNotBlank()) { "taskId must not be blank" }
        require('/' !in taskId) { "taskId must not contain a route separator" }
        return "task/$taskId/edit"
    }
}
