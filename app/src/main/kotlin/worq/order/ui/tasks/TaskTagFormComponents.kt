package worq.order.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.ui.WorqOrderTextInputDefaults
import worq.order.ui.tags.TagChipGroup
import worq.order.ui.tags.TagMultiSelectPicker
import worq.order.ui.tags.TagTextSupportingText
import worq.order.ui.tags.isTagTextOverLimit
import worq.order.ui.theme.WorqOrderDimens

@Composable
internal fun TaskTagControls(
    selections: List<TaskTagSelectionUi>,
    catalog: List<TaskTagCatalogItemUi>,
    enabled: Boolean,
    onOpenPicker: () -> Unit,
    onUseUpdatedVersion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        TagChipGroup(
            selectedTags = selections.toChipUi(),
            onOpenPicker = onOpenPicker,
            enabled = enabled,
        )
        selections.forEach { selection ->
            val updatedText = selection.updatedCatalogText(catalog)
            when {
                updatedText != null -> {
                    Text(
                        text = stringResource(R.string.tag_updated_version_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onUseUpdatedVersion(selection.id) },
                        enabled = enabled,
                    ) {
                        Text(stringResource(R.string.use_updated_tag_version))
                    }
                }
                selection.sourceTagId != null && catalog.none { it.id == selection.sourceTagId } ->
                    Text(
                        text = stringResource(R.string.tag_removed_from_catalog),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

@Composable
internal fun TaskTagPickerOverlay(
    picker: TaskTagPickerUiState,
    catalog: List<TaskTagCatalogItemUi>,
    onSearchChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onCreateInline: () -> Unit,
) {
    TagMultiSelectPicker(
        query = picker.searchQuery,
        items = pickerItemsFor(picker.draftSelections, catalog, picker.searchQuery),
        onQueryChanged = onSearchChanged,
        onClearQuery = onClearSearch,
        onToggleTag = onToggle,
        onSelectAll = onSelectAll,
        onDeselectAll = onDeselectAll,
        onCancel = onCancel,
        onApply = onApply,
        onCreateInline = onCreateInline,
        selectedCount = picker.draftSelections.size,
        title =
            stringResource(
                if (picker.field == TaskTagField.DESCRIPTION) {
                    R.string.select_description_tags
                } else {
                    R.string.select_purchase_tags
                },
            ),
        errorMessage =
            picker.error?.let { error ->
                stringResource(
                    when (error) {
                        TaskTagPickerError.COMPOSED_TEXT_TOO_LONG ->
                            R.string.tag_picker_composed_text_too_long
                        TaskTagPickerError.DATA_UNAVAILABLE -> R.string.tag_picker_data_unavailable
                    },
                )
            },
    )
}

@Composable
internal fun TaskTagInlineEditorDialog(
    editor: TaskTagInlineEditorUiState,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isOverLimit = editor.text.isTagTextOverLimit()
    val validationMessage =
        editor.error?.let { error ->
            stringResource(
                when (error) {
                    TaskTagInlineEditorError.BLANK -> R.string.tag_error_blank
                    TaskTagInlineEditorError.TOO_LONG -> R.string.tag_error_too_long
                    TaskTagInlineEditorError.DATA_UNAVAILABLE ->
                        R.string.tag_picker_data_unavailable
                },
            )
        }
    AlertDialog(
        onDismissRequest = {
            if (!editor.isSaving) onDismiss()
        },
        title = { Text(stringResource(R.string.task_tag_inline_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing)) {
                OutlinedTextField(
                    value = editor.text,
                    onValueChange = onTextChanged,
                    label = { Text(stringResource(R.string.tag_text)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 5,
                    enabled = !editor.isSaving,
                    keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
                    isError = isOverLimit || editor.error != null,
                    supportingText = {
                        TagTextSupportingText(
                            value = editor.text,
                            validationMessage = validationMessage,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !editor.isSaving && !isOverLimit,
            ) {
                if (editor.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(stringResource(R.string.task_tag_inline_create_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
