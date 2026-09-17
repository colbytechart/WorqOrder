package worq.order.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.TaskMetadataValidationError

class TaskTagUiStateTest {
    @Test
    fun composerPreservesSelectionOrderAndAddsTerminalPunctuation() {
        val selections =
            listOf(
                TaskTagSelectionUi("a", "Install monitor"),
                TaskTagSelectionUi("b", "Verify cabling?"),
            )

        assertEquals(
            "Manual note. Install monitor. Verify cabling?",
            composedTaskText("Manual note", selections),
        )
        assertEquals(
            listOf("Install monitor", "Verify cabling?"),
            selections.toSnapshotDrafts().map { it.text },
        )
    }

    @Test
    fun pickerFilteringKeepsSelectedRowsAndOnlyChangesVisibleCatalogRows() {
        val selections = listOf(TaskTagSelectionUi("selected", "Install monitor", "one"))
        val catalog =
            listOf(
                TaskTagCatalogItemUi("one", "Install monitor"),
                TaskTagCatalogItemUi("two", "Configure network"),
            )

        val filtered = pickerItemsFor(selections, catalog, "network")
        assertEquals(listOf("two"), filtered.map { it.id })
        assertTrue(filtered.single().isSelected.not())

        val selectedSearch = pickerItemsFor(selections, catalog, "install")
        assertEquals(listOf("selected"), selectedSearch.map { it.id })
        assertTrue(selectedSearch.single().isSelected)
    }

    @Test
    fun deletedAndRenamedSourcesRemainTaskSelectionsWithExplicitState() {
        val selection = TaskTagSelectionUi("snapshot-1", "Old wording", "source-1")
        val renamedCatalog = listOf(TaskTagCatalogItemUi("source-1", "New wording"))
        val renamed = selection.updatedCatalogText(renamedCatalog)
        assertEquals("New wording", renamed)

        val deletedRows = pickerItemsFor(listOf(selection), emptyList(), "")
        assertEquals(1, deletedRows.size)
        assertTrue(deletedRows.single().isSelected)
        assertTrue(deletedRows.single().isStale)
    }

    @Test
    fun projectedComposedTextUsesCodePointsAndFlagsOnlyTheAffectedField() {
        val errors =
            projectedTagTextErrors(
                description = "x".repeat(998),
                descriptionSelections = listOf(TaskTagSelectionUi("a", "y")),
                purchases = "ok",
                purchaseSelections = emptyList(),
            )

        assertEquals(
            setOf(TaskMetadataValidationError.DESCRIPTION_TOO_LONG),
            errors,
        )
    }
}
