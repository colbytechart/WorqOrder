package worq.order.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import worq.order.R
import worq.order.domain.TagCsvImportFailure
import worq.order.model.TagCategory
import worq.order.ui.WorqOrderTextInputDefaults
import worq.order.ui.theme.WorqOrderDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    uiState: TagManagementUiState,
    onEvent: (TagManagementEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tag_management)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            when {
                uiState.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.hasLoadError ->
                    TagLoadError(
                        onRetry = { onEvent(TagManagementEvent.Retry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = WorqOrderDimens.ScreenPadding,
                                vertical = WorqOrderDimens.SectionSpacing,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(WorqOrderDimens.SectionSpacing),
                    ) {
                        item(key = "description-tags") {
                            TagCatalogNavigationRow(
                                title = stringResource(R.string.description_tags),
                                supportingText =
                                    stringResource(
                                        R.string.description_tags_summary,
                                        uiState.descriptionTagCount,
                                    ),
                                onClick = {
                                    onEvent(TagManagementEvent.OpenDescriptionTags)
                                },
                            )
                        }
                        item(key = "purchase-tags") {
                            TagCatalogNavigationRow(
                                title = stringResource(R.string.hardware_software_purchase_tags),
                                supportingText =
                                    stringResource(
                                        R.string.hardware_software_purchase_tags_summary,
                                        uiState.purchaseTagCount,
                                    ),
                                onClick = {
                                    onEvent(
                                        TagManagementEvent.OpenHardwareSoftwarePurchaseTags,
                                    )
                                },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun TagCatalogNavigationRow(
    title: String,
    supportingText: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { role = Role.Button }
                .clickable(onClick = onClick),
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = null,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(supportingText) },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagCategoryManagementScreen(
    uiState: TagCategoryManagementUiState,
    onEvent: (TagCategoryManagementEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onImportCsv: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.importSummary, uiState.importFailure) {
        listState.scrollToItem(0)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(uiState.category.titleResource())) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
        ) {
            when {
                uiState.isLoading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.hasLoadError ->
                    TagLoadError(
                        onRetry = { onEvent(TagCategoryManagementEvent.Retry) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                else ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding =
                            androidx.compose.foundation.layout.PaddingValues(
                                horizontal = WorqOrderDimens.ScreenPadding,
                                vertical = WorqOrderDimens.ItemSpacing,
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                    ) {
                        uiState.message?.let { message ->
                            item(key = "tag-message") {
                                TagMessage(
                                    text = stringResource(message.messageResource()),
                                    onDismiss = {
                                        onEvent(TagCategoryManagementEvent.DismissMessage)
                                    },
                                )
                            }
                        }
                        item(key = "tag-search") {
                            TagSearchField(
                                query = uiState.searchQuery,
                                onQueryChanged = {
                                    onEvent(TagCategoryManagementEvent.EditSearch(it))
                                },
                                onClear = {
                                    onEvent(TagCategoryManagementEvent.ClearSearch)
                                },
                            )
                        }
                        item(key = "tag-add") {
                            FilledTonalButton(
                                onClick = { onEvent(TagCategoryManagementEvent.OpenAddTag) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isImporting,
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                Text(
                                    text = stringResource(R.string.add_tag),
                                    modifier = Modifier.padding(start = WorqOrderDimens.ItemSpacing),
                                )
                            }
                        }
                        item(key = "tag-import") {
                            OutlinedButton(
                                onClick = {
                                    onEvent(TagCategoryManagementEvent.ImportCsv)
                                    onImportCsv()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isImporting,
                            ) {
                                if (uiState.isImporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(WorqOrderDimens.InlineProgressSize),
                                        strokeWidth = WorqOrderDimens.InlineProgressStrokeWidth,
                                    )
                                }
                                Text(
                                    text =
                                        stringResource(
                                            if (uiState.isImporting) {
                                                R.string.importing_tags
                                            } else {
                                                R.string.import_tags_from_csv
                                            },
                                        ),
                                    modifier = Modifier.padding(start = WorqOrderDimens.ItemSpacing),
                                )
                            }
                        }
                        uiState.importSummary?.let { summary ->
                            item(key = "tag-import-summary") {
                                TagImportStatus(
                                    text =
                                        stringResource(
                                            R.string.tag_import_success,
                                            summary.addedCount,
                                            summary.skippedCount,
                                        ),
                                    isError = false,
                                    onDismiss = {
                                        onEvent(TagCategoryManagementEvent.DismissImportStatus)
                                    },
                                )
                            }
                        }
                        uiState.importFailure?.let { failure ->
                            item(key = "tag-import-failure") {
                                val reason = stringResource(failure.failure.messageResource())
                                TagImportStatus(
                                    text =
                                        if (
                                            failure.recordNumber != null &&
                                                failure.columnNumber != null
                                        ) {
                                            stringResource(
                                                R.string.tag_import_failure_at_location,
                                                reason,
                                                failure.recordNumber,
                                                failure.columnNumber,
                                            )
                                        } else {
                                            reason
                                        },
                                    isError = true,
                                    onDismiss = {
                                        onEvent(TagCategoryManagementEvent.DismissImportStatus)
                                    },
                                )
                            }
                        }
                        item(key = "tag-list-heading") {
                            Text(
                                text = stringResource(uiState.category.listTitleResource()),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = WorqOrderDimens.ItemSpacing),
                            )
                        }
                        if (uiState.tags.isEmpty()) {
                            item(key = "tag-empty") {
                                EmptyTagSection(category = uiState.category)
                            }
                        } else {
                            items(
                                items = uiState.tags,
                                key = TagItemUi::id,
                            ) { tag ->
                                TagRow(
                                    tag = tag,
                                    enabled = !uiState.isImporting,
                                    onEdit = {
                                        onEvent(TagCategoryManagementEvent.OpenEditTag(tag.id))
                                    },
                                    onDelete = {
                                        onEvent(TagCategoryManagementEvent.RequestDeleteTag(tag.id))
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }
    }

    uiState.editor?.let { editor ->
        TagEditorDialog(
            editor = editor,
            onTextChanged = { onEvent(TagCategoryManagementEvent.EditTagText(it)) },
            onConfirm = { onEvent(TagCategoryManagementEvent.ConfirmEditor) },
            onDismiss = { onEvent(TagCategoryManagementEvent.DismissEditor) },
        )
    }
    uiState.deleteConfirmation?.let { confirmation ->
        DeleteTagDialog(
            confirmation = confirmation,
            onConfirm = { onEvent(TagCategoryManagementEvent.ConfirmDeleteTag) },
            onDismiss = { onEvent(TagCategoryManagementEvent.DismissDeleteTag) },
        )
    }
}

@Composable
private fun TagSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.search_tags)) },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.clear_search),
                    )
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun TagRow(
    tag: TagItemUi,
    enabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(tag.text) },
        trailingContent = {
            Row {
                IconButton(
                    onClick = onEdit,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_tag_action, tag.text),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_tag_action, tag.text),
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyTagSection(category: TagCategory) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(WorqOrderDimens.ItemPadding),
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.TaskTextSpacing),
    ) {
        Text(
            text = stringResource(category.emptyTitleResource()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.no_tags_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagEditorDialog(
    editor: TagEditorUiState,
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldError = editor.fieldError
    val supportingText =
        fieldError?.let { stringResource(it.messageResource()) }
            ?: stringResource(R.string.tag_text_limit)
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (editor.mode == TagEditorMode.ADD) {
                        R.string.add_tag_title
                    } else {
                        R.string.edit_tag_title
                    },
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = editor.text,
                onValueChange = onTextChanged,
                enabled = !editor.isSaving,
                label = { Text(stringResource(R.string.tag_text)) },
                keyboardOptions = WorqOrderTextInputDefaults.sentenceCapitalization,
                supportingText = {
                    Text(
                        text = supportingText,
                        modifier =
                            if (fieldError != null) {
                                Modifier.semantics {
                                    error(supportingText)
                                    liveRegion = LiveRegionMode.Assertive
                                }
                            } else {
                                Modifier
                            },
                    )
                },
                isError = editor.fieldError != null,
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(WorqOrderDimens.InlineProgressSize),
                        strokeWidth = WorqOrderDimens.InlineProgressStrokeWidth,
                    )
                } else {
                    Text(stringResource(R.string.save))
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

@Composable
private fun TagImportStatus(
    text: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.semantics {
                liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        headlineContent = {
            Text(
                text = text,
                color =
                    if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        trailingContent = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                )
            }
        },
    )
}

@Composable
private fun DeleteTagDialog(
    confirmation: DeleteTagConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!confirmation.isDeleting) onDismiss() },
        title = { Text(stringResource(R.string.delete_tag_title)) },
        text = {
            Text(stringResource(R.string.delete_tag_message, confirmation.tagText))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !confirmation.isDeleting) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !confirmation.isDeleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun TagMessage(
    text: String,
    onDismiss: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.semantics {
                liveRegion = LiveRegionMode.Assertive
            },
        headlineContent = {
            Text(text = text, color = MaterialTheme.colorScheme.error)
        },
        trailingContent = {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                )
            }
        },
    )
}

@Composable
private fun TagLoadError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(WorqOrderDimens.ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
    ) {
        Text(stringResource(R.string.tags_load_failed))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

private fun TagCategory.titleResource(): Int =
    when (this) {
        TagCategory.DESCRIPTION -> R.string.description_tags
        TagCategory.HARDWARE_SOFTWARE_PURCHASE -> R.string.hardware_software_purchase_tags
    }

private fun TagCategory.listTitleResource(): Int =
    when (this) {
        TagCategory.DESCRIPTION -> R.string.description_tags
        TagCategory.HARDWARE_SOFTWARE_PURCHASE -> R.string.hardware_software_purchase_tags
    }

private fun TagCategory.emptyTitleResource(): Int =
    when (this) {
        TagCategory.DESCRIPTION -> R.string.no_description_tags
        TagCategory.HARDWARE_SOFTWARE_PURCHASE -> R.string.no_hardware_software_purchase_tags
    }

private fun TagFieldError.messageResource(): Int =
    when (this) {
        TagFieldError.BLANK -> R.string.tag_error_blank
        TagFieldError.TOO_LONG -> R.string.tag_error_too_long
        TagFieldError.DUPLICATE -> R.string.tag_error_duplicate
    }

private fun TagManagementMessage.messageResource(): Int =
    when (this) {
        TagManagementMessage.DATA_UNAVAILABLE -> R.string.tag_data_unavailable
        TagManagementMessage.TAG_NOT_FOUND -> R.string.tag_not_found
    }

private fun TagCsvImportFailure.messageResource(): Int =
    when (this) {
        TagCsvImportFailure.UNSUPPORTED_FILE -> R.string.tag_import_unsupported_file
        TagCsvImportFailure.FILE_TOO_LARGE -> R.string.tag_import_file_too_large
        TagCsvImportFailure.READ_FAILED -> R.string.tag_import_read_failed
        TagCsvImportFailure.INVALID_UTF8 -> R.string.tag_import_invalid_utf8
        TagCsvImportFailure.MALFORMED_CSV -> R.string.tag_import_malformed_csv
        TagCsvImportFailure.TOO_MANY_TAGS -> R.string.tag_import_too_many_tags
        TagCsvImportFailure.TAG_TOO_LONG -> R.string.tag_import_too_long
        TagCsvImportFailure.STORAGE_FAILED -> R.string.tag_import_storage_failed
    }
