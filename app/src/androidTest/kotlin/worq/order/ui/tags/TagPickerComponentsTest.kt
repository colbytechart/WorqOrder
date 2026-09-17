package worq.order.ui.tags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.ui.theme.WorqOrderTheme

@RunWith(AndroidJUnit4::class)
class TagPickerComponentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chipsExposeFullRemovalLabelsAndCompactOverflow() {
        val removed = mutableListOf<String>()
        var opened = 0
        setContent {
            TagChipGroup(
                selectedTags =
                    listOf(
                        TagChipUi("one", "A very long description tag"),
                        TagChipUi("two", "Install monitor"),
                        TagChipUi("three", "Configure network"),
                        TagChipUi("four", "Schedule follow-up"),
                        TagChipUi("five", "Document results"),
                    ),
                onRemove = { removed += it },
                onOpenPicker = { opened += 1 },
            )
        }

        composeRule
            .onNodeWithContentDescription("Remove tag A very long description tag")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("+2 more tags").performClick()

        assertEquals(listOf("one"), removed)
        assertEquals(1, opened)
    }

    @Test
    fun pickerFiltersClearsAndTogglesWithSelectedCount() {
        val allItems =
            listOf(
                TagPickerItemUi("one", "Install monitor", isSelected = true),
                TagPickerItemUi("two", "Configure network", isSelected = false),
            )
        var state by mutableStateOf(PickerTestState(items = allItems))
        val toggled = mutableListOf<String>()
        var clearCount = 0
        setContent {
            TagMultiSelectPicker(
                query = state.query,
                items = state.items,
                selectedCount = allItems.count { it.isSelected },
                onQueryChanged = { query ->
                    state =
                        state.copy(
                            query = query,
                            items = allItems.filter { it.text.contains(query, ignoreCase = true) },
                        )
                },
                onClearQuery = {
                    clearCount += 1
                    state = state.copy(query = "", items = allItems)
                },
                onToggleTag = { toggled += it },
                onCancel = {},
                onApply = {},
            )
        }

        composeRule.onNodeWithText("1 tags selected").assertIsDisplayed()
        composeRule.onNodeWithText("Search tags").performTextInput("network")
        composeRule.onNodeWithText("Configure network").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear tag search").performClick()
        composeRule.onNodeWithContentDescription("Install monitor").assertIsSelected()
        composeRule.onNodeWithText("Configure network").performClick()

        assertEquals(1, clearCount)
        assertEquals(listOf("two"), toggled)
    }

    @Test
    fun pickerProvidesCancelApplyAndInlineCreateActions() {
        val actions = mutableListOf<String>()
        setContent {
            TagMultiSelectPicker(
                query = "",
                items = emptyList(),
                onQueryChanged = {},
                onClearQuery = {},
                onToggleTag = {},
                onCancel = { actions += "cancel" },
                onApply = { actions += "apply" },
                onCreateInline = { actions += "create" },
            )
        }

        composeRule.onNodeWithText("No tags match your search.").assertIsDisplayed()
        composeRule.onNodeWithText("Create new tag").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Apply").performClick()

        assertEquals(listOf("create", "cancel", "apply"), actions)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            WorqOrderTheme(darkTheme = true) {
                content()
            }
        }
    }

    private data class PickerTestState(
        val query: String = "",
        val items: List<TagPickerItemUi>,
    )
}
