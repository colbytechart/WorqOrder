package worq.order.ui.tasks

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import worq.order.R

@Composable
internal fun TaskTextSupportingText(
    value: String,
    maxCodePoints: Int,
    blankError: Boolean = false,
    tooLongError: Boolean = false,
) {
    val message =
        when {
            blankError -> stringResource(R.string.field_required)
            tooLongError -> stringResource(R.string.character_limit_error, maxCodePoints)
            else ->
                stringResource(
                    R.string.character_count,
                    value.codePointCount(0, value.length),
                    maxCodePoints,
                )
        }
    Text(
        text = message,
        modifier =
            if (blankError || tooLongError) {
                Modifier.semantics {
                    error(message)
                    liveRegion = LiveRegionMode.Assertive
                }
            } else {
                Modifier
            },
    )
}
