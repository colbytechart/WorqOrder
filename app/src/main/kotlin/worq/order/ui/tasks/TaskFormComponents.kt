package worq.order.ui.tasks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import worq.order.R

@Composable
internal fun TaskTextSupportingText(
    value: String,
    maxCodePoints: Int,
    blankError: Boolean = false,
    tooLongError: Boolean = false,
) {
    Text(
        when {
            blankError -> stringResource(R.string.field_required)
            tooLongError -> stringResource(R.string.character_limit_error, maxCodePoints)
            else ->
                stringResource(
                    R.string.character_count,
                    value.codePointCount(0, value.length),
                    maxCodePoints,
                )
        },
    )
}
