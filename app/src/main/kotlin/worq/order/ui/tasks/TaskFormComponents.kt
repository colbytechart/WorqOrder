package worq.order.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import worq.order.R
import worq.order.data.TaskMetadataValidationError
import worq.order.model.WorkType
import worq.order.model.BillingStatus
import worq.order.ui.theme.WorqOrderDimens

@Composable
internal fun TaskWorkTypeSelector(
    selected: WorkType,
    enabled: Boolean,
    onSelect: (WorkType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(
            text = stringResource(R.string.work_type),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            WorkType.entries
                .filter { it != WorkType.UNSPECIFIED }
                .forEach { workType ->
                    val isSelected = selected == workType
                    Row(
                        modifier =
                            Modifier
                                .weight(1f)
                                .selectable(
                                    selected = isSelected,
                                    enabled = enabled,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(workType) },
                                ),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            enabled = enabled,
                            onClick = null,
                        )
                        Text(
                            text =
                                stringResource(
                                    if (workType == WorkType.ON_SITE) {
                                        R.string.work_type_on_site
                                    } else {
                                        R.string.work_type_in_office
                                    },
                                ),
                        )
                    }
                }
        }
    }
}

@Composable
internal fun TaskBillingStatusSelector(
    selected: BillingStatus?,
    enabled: Boolean,
    onSelect: (BillingStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(
            text = stringResource(R.string.billing_status),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            BillingStatus.entries.forEach { billingStatus ->
                val isSelected = selected == billingStatus
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .selectable(
                                selected = isSelected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(billingStatus) },
                            ),
                ) {
                    RadioButton(
                        selected = isSelected,
                        enabled = enabled,
                        onClick = null,
                    )
                    Text(
                        text =
                            stringResource(
                                when (billingStatus) {
                                    BillingStatus.BILLABLE -> R.string.billing_status_billable
                                    BillingStatus.DO_NOT_BILL -> R.string.billing_status_do_not_bill
                                    BillingStatus.DO_NOT_CHARGE ->
                                        R.string.billing_status_do_not_charge
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskMileageField(
    value: String,
    validationErrors: Set<TaskMetadataValidationError>,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val errorMessage = mileageErrorMessage(validationErrors)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.mileage)) },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = errorMessage != null,
        supportingText = errorMessage?.let { message ->
            {
                Text(
                    text = message,
                    modifier =
                        Modifier.semantics {
                            error(message)
                            liveRegion = LiveRegionMode.Assertive
                        },
                )
            }
        },
    )
}

@Composable
private fun mileageErrorMessage(
    errors: Set<TaskMetadataValidationError>,
): String? =
    when {
        TaskMetadataValidationError.MILEAGE_MALFORMED in errors ->
            stringResource(R.string.mileage_malformed)
        TaskMetadataValidationError.MILEAGE_TOO_LARGE in errors ->
            stringResource(R.string.mileage_too_large)
        TaskMetadataValidationError.MILEAGE_TOO_PRECISE in errors ->
            stringResource(R.string.mileage_too_precise)
        else -> null
    }

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
