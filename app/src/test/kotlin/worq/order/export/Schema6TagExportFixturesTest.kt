package worq.order.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.data.MAX_COMPOSED_TASK_TEXT_CODE_POINTS
import worq.order.data.TaskTextComposer

/**
 * Destination-neutral examples shared by the schema-6 Tag export work.
 *
 * These fixtures describe the values that the canonical snapshot must provide. They intentionally
 * do not invoke a destination adapter; CSV, XLSX, and Google parity is verified after the shared
 * snapshot builder consumes these cases.
 */
data class Schema6TagExportFixture(
    val name: String,
    val manualDescription: String,
    val descriptionTags: List<String>,
    val manualExpense: String,
    val expenseTags: List<String>,
    val expectedDescription: String,
    val expectedExpense: String,
) {
    fun composedDescription(): String =
        TaskTextComposer.compose(manualDescription, descriptionTags)

    fun composedExpense(): String =
        TaskTextComposer.compose(manualExpense, expenseTags)
}

object Schema6TagExportFixtures {
    val cases: List<Schema6TagExportFixture> =
        listOf(
            Schema6TagExportFixture(
                name = "blank",
                manualDescription = "",
                descriptionTags = emptyList(),
                manualExpense = "",
                expenseTags = emptyList(),
                expectedDescription = "",
                expectedExpense = "",
            ),
            Schema6TagExportFixture(
                name = "manual text",
                manualDescription = "  Repair router  ",
                descriptionTags = emptyList(),
                manualExpense = "  Laptop purchase. ",
                expenseTags = emptyList(),
                expectedDescription = "Repair router.",
                expectedExpense = "Laptop purchase.",
            ),
            Schema6TagExportFixture(
                name = "tag only",
                manualDescription = "",
                descriptionTags = listOf("Inspect rack"),
                manualExpense = "",
                expenseTags = listOf("Replacement cable"),
                expectedDescription = "Inspect rack.",
                expectedExpense = "Replacement cable.",
            ),
            Schema6TagExportFixture(
                name = "mixed selection order",
                manualDescription = "Replace router",
                descriptionTags = listOf("Confirm VLAN?", "Archive logs!", "Document ports"),
                manualExpense = "Order hardware",
                expenseTags = listOf("Rack screws", "Patch cable"),
                expectedDescription =
                    "Replace router. Confirm VLAN? Archive logs! Document ports.",
                expectedExpense = "Order hardware. Rack screws. Patch cable.",
            ),
            Schema6TagExportFixture(
                name = "unicode and whitespace",
                manualDescription = "  Caf\u00e9\tsetup  ",
                descriptionTags = listOf("\u6771\u4eac router", "\uD83D\uDE80 ready!"),
                manualExpense = "",
                expenseTags = emptyList(),
                expectedDescription = "Caf\u00e9\tsetup. \u6771\u4eac router. \uD83D\uDE80 ready!",
                expectedExpense = "",
            ),
            Schema6TagExportFixture(
                name = "historical task without snapshots",
                manualDescription = "Old task text",
                descriptionTags = emptyList(),
                manualExpense = "Old purchase",
                expenseTags = emptyList(),
                expectedDescription = "Old task text.",
                expectedExpense = "Old purchase.",
            ),
            Schema6TagExportFixture(
                name = "exact composed limit",
                manualDescription = "m".repeat(989),
                descriptionTags = listOf("t".repeat(7)),
                manualExpense = "",
                expenseTags = emptyList(),
                expectedDescription = "m".repeat(989) + ". " + "t".repeat(7) + ".",
                expectedExpense = "",
            ),
        )
}

class Schema6TagExportFixturesTest {
    @Test
    fun fixturesCoverBlankManualTagOnlyMixedOrderPunctuationWhitespaceUnicodeAndHistory() {
        val fixtures = Schema6TagExportFixtures.cases
        val exactLimit = fixtures.single { it.name == "exact composed limit" }.composedDescription()

        assertEquals(7, fixtures.size)
        fixtures.forEach { fixture ->
            assertEquals(fixture.expectedDescription, fixture.composedDescription())
            assertEquals(fixture.expectedExpense, fixture.composedExpense())
        }
        assertEquals(
            MAX_COMPOSED_TASK_TEXT_CODE_POINTS,
            exactLimit.codePointCount(0, exactLimit.length),
        )
        assertEquals(
            "Old task text.",
            fixtures.single { it.name == "historical task without snapshots" }
                .composedDescription(),
        )
    }

    @Test
    fun overLimitFixtureIsRejectedWithoutChangingTheComposedValueRule() {
        val manual = "m".repeat(990)
        val tag = "t".repeat(7)
        val composed = TaskTextComposer.compose(manual, listOf(tag))

        assertEquals(
            MAX_COMPOSED_TASK_TEXT_CODE_POINTS + 1,
            composed.codePointCount(0, composed.length),
        )
        assertTrue(composed.endsWith("."))
        assertFalse(composed.isEmpty())
    }

    @Test
    fun repeatedProjectionOfTheSameFixtureIsDeterministic() {
        Schema6TagExportFixtures.cases.forEach { fixture ->
            assertEquals(fixture.composedDescription(), fixture.composedDescription())
            assertEquals(fixture.composedExpense(), fixture.composedExpense())
        }
    }

    @Test
    fun schemaMarkerAndVisibleHeadersRemainSchema6WithoutTagColumns() {
        assertEquals(6, ExportSchema.VERSION)
        assertEquals(14, ExportSchema.headers.size)
        assertFalse(ExportSchema.headers.any { it.contains("Tag", ignoreCase = true) })
    }
}
