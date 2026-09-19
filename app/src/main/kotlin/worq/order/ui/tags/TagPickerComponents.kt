package worq.order.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import worq.order.R
import worq.order.data.MAX_TAG_CODE_POINTS
import worq.order.ui.theme.WorqOrderDimens

object TagPickerTestTags {
    const val RESULTS = "tag_picker_results"
}

internal fun String.isTagTextOverLimit(): Boolean =
    codePointCount(0, length) > MAX_TAG_CODE_POINTS

@Composable
internal fun TagTextSupportingText(
    value: String,
    validationMessage: String? = null,
) {
    val currentCodePoints = value.codePointCount(0, value.length)
    val tooLong = currentCodePoints > MAX_TAG_CODE_POINTS
    val message =
        when {
            tooLong ->
                stringResource(
                    R.string.character_limit_error,
                    currentCodePoints,
                    MAX_TAG_CODE_POINTS,
                )
            validationMessage != null -> validationMessage
            else ->
                stringResource(
                    R.string.character_count,
                    currentCodePoints,
                    MAX_TAG_CODE_POINTS,
                )
        }
    Text(
        text = message,
        modifier =
            if (tooLong || validationMessage != null) {
                Modifier.semantics {
                    error(message)
                    liveRegion = LiveRegionMode.Assertive
                }
            } else {
                Modifier
            },
    )
}

/** A selected tag rendered in a task-field chip group. */
data class TagChipUi(
    val id: String,
    val text: String,
)

/** A tag row supplied by the caller of the stateless picker. */
data class TagPickerItemUi(
    val id: String,
    val text: String,
    val isSelected: Boolean,
    val isStale: Boolean = false,
)

/**
 * Compact, single-row presentation of tags associated with one task field.
 *
 * The full tag text remains available through the chip's accessibility description even when
 * the visual label is ellipsized. Selection state and catalog mutations belong to the caller.
 */
@Composable
fun TagChipGroup(
    selectedTags: List<TagChipUi>,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButtonWithTagIcon(
            onClick = onOpenPicker,
            enabled = enabled,
            hasSelection = selectedTags.isNotEmpty(),
        )
        when (selectedTags.size) {
            0 -> Unit
            1 ->
                SelectedTagChip(
                    tag = selectedTags.single(),
                    onClick = onOpenPicker,
                    enabled = enabled,
                    modifier = Modifier.weight(1f, fill = false),
                )
            else ->
                SelectedTagCountChip(
                    count = selectedTags.size,
                    onClick = onOpenPicker,
                    enabled = enabled,
                    modifier = Modifier.weight(1f, fill = false),
                )
        }
    }
}

@Composable
private fun SelectedTagChip(
    tag: TagChipUi,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val openDescription = stringResource(R.string.tag_selected_open_action, tag.text)
    InputChip(
        selected = true,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = tag.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = CircleShape,
        modifier =
            modifier
                .heightIn(min = WorqOrderDimens.ActionButtonHeight)
                .semantics {
                    contentDescription = openDescription
                },
    )
}

@Composable
private fun SelectedTagCountChip(
    count: Int,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    InputChip(
        selected = true,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = stringResource(R.string.tag_selected_count_summary, count),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = CircleShape,
        modifier = modifier.heightIn(min = WorqOrderDimens.ActionButtonHeight),
    )
}

@Composable
private fun OutlinedButtonWithTagIcon(
    onClick: () -> Unit,
    enabled: Boolean,
    hasSelection: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = WorqOrderDimens.ActionButtonHeight),
        contentPadding = PaddingValues(horizontal = WorqOrderDimens.ItemPadding),
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null)
        Text(
            text =
                stringResource(
                    if (hasSelection) R.string.tag_edit_tags else R.string.tag_add_tags,
                ),
            modifier = Modifier.padding(start = WorqOrderDimens.ItemSpacing),
        )
    }
}

/**
 * Full-screen, searchable, multi-select picker. The caller owns selected IDs and applies them
 * only after [onApply] is invoked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagMultiSelectPicker(
    query: String,
    items: List<TagPickerItemUi>,
    onQueryChanged: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleTag: (tagId: String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onCreateInline: (() -> Unit)? = null,
    selectedCount: Int = items.count { it.isSelected },
    title: String? = null,
    errorMessage: String? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(title ?: stringResource(R.string.tag_picker_title))
                },
            )
        },
        bottomBar = {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = WorqOrderDimens.ScreenPadding,
                            vertical = WorqOrderDimens.BottomActionVerticalPadding,
                        ),
                horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).heightIn(min = WorqOrderDimens.ActionButtonHeight),
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).heightIn(min = WorqOrderDimens.ActionButtonHeight),
                ) {
                    Text(stringResource(R.string.tag_apply))
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = WorqOrderDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_tags)) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tag_selected_count, selectedCount.coerceAtLeast(0)),
                )
                onCreateInline?.let { createInline ->
                    TextButton(onClick = createInline) {
                        Text(stringResource(R.string.tag_create_inline))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
            ) {
                TextButton(
                    onClick = onSelectAll,
                    enabled = items.any { item -> !item.isSelected },
                ) {
                    Text(stringResource(R.string.tag_select_all))
                }
                TextButton(
                    onClick = onDeselectAll,
                    enabled = items.any(TagPickerItemUi::isSelected),
                ) {
                    Text(stringResource(R.string.tag_deselect_all))
                }
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { contentDescription = message },
                )
            }
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tag_no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(TagPickerTestTags.RESULTS),
                    contentPadding = PaddingValues(bottom = WorqOrderDimens.ItemSpacing),
                    verticalArrangement = Arrangement.spacedBy(WorqOrderDimens.ItemSpacing),
                ) {
                    items(items = items, key = { it.id }) { item ->
                        TagPickerRow(
                            item = item,
                            onToggle = { onToggleTag(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagPickerRow(
    item: TagPickerItemUi,
    onToggle: () -> Unit,
) {
    val staleDescription =
        if (item.isStale) {
            stringResource(R.string.tag_stale)
        } else {
            null
        }
    val rowDescription =
        listOfNotNull(item.text.takeIf(String::isNotBlank), staleDescription)
            .joinToString(separator = ". ")
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = item.isSelected,
                    role = Role.Checkbox,
                    onClick = onToggle,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                },
        headlineContent = {
            Text(
                text = item.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent =
            if (item.isStale) {
                { Text(stringResource(R.string.tag_stale)) }
            } else {
                null
            },
        trailingContent = {
            Checkbox(checked = item.isSelected, onCheckedChange = null)
        },
    )
}
