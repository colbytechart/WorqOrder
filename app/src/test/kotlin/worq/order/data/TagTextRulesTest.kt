package worq.order.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.model.TaskTagSnapshotDraft

class TagTextRulesTest {
    @Test
    fun normalizesWhitespaceAndIgnoresOneTerminalPeriodForDuplicateKey() {
        val withPeriod = TagTextNormalizer.validate("  Install\tmonitor. \n")
        val withoutPeriod = TagTextNormalizer.validate("install monitor")

        assertEquals(
            NormalizedTagText("Install monitor.", "install monitor"),
            (withPeriod as TagTextValidationResult.Valid).text,
        )
        assertEquals(
            "install monitor",
            (withoutPeriod as TagTextValidationResult.Valid).text.normalizedText,
        )
    }

    @Test
    fun usesUnicodeCodePointsForTheFourHundredCharacterLimit() {
        val emoji = "\uD83D\uDEE0"

        assertTrue(
            TagTextNormalizer.validate(emoji.repeat(MAX_TAG_CODE_POINTS)) is
                TagTextValidationResult.Valid,
        )
        assertEquals(
            TagTextValidationError.TOO_LONG,
            (TagTextNormalizer.validate(emoji.repeat(MAX_TAG_CODE_POINTS + 1))
                as TagTextValidationResult.Invalid).error,
        )
    }

    @Test
    fun composesManualTextAndTagsWithExportOnlyPunctuation() {
        assertEquals(
            "Replace router. Confirm VLAN? Archive logs!",
            TaskTextComposer.compose(
                manualText = " Replace router ",
                tagTexts = listOf(" Confirm VLAN? ", "Archive logs!"),
            ),
        )
        assertEquals(
            "Tag-only description.",
            TaskTextComposer.compose("", listOf("Tag-only description")),
        )
        assertEquals(
            "Line 1\r\nLine 2. Saved tag.",
            TaskTextComposer.compose(
                manualText = "  Line 1\r\nLine 2  ",
                tagTexts = listOf("Saved tag"),
            ),
        )
    }

    @Test
    fun validatorAllowsTagOnlyDescriptionAndCountsComposedLength() {
        val valid =
            TaskMetadataValidator.validate(
                description = "",
                hardwareSoftwarePurchases = "",
                descriptionTagSnapshots = listOf(TaskTagSnapshotDraft("Inspect rack")),
            )
        assertTrue(valid is TaskMetadataValidationResult.Valid)

        val tooLong =
            TaskMetadataValidator.validate(
                description = "d".repeat(MAX_COMPOSED_TASK_TEXT_CODE_POINTS - 2),
                hardwareSoftwarePurchases = "",
                descriptionTagSnapshots = listOf(TaskTagSnapshotDraft("tag")),
            ) as TaskMetadataValidationResult.Invalid
        assertTrue(TaskMetadataValidationError.DESCRIPTION_TOO_LONG in tooLong.errors)
    }

    @Test
    fun composedTextAcceptsExactlyNineHundredNinetyNineCodePoints() {
        val exactLimit = "x".repeat(MAX_COMPOSED_TASK_TEXT_CODE_POINTS - 1)
        val composed = TaskTextComposer.compose(exactLimit, emptyList())
        val mixed =
            TaskTextComposer.compose(
                manualText = "m".repeat(989),
                tagTexts = listOf("t".repeat(7)),
            )

        assertEquals(
            MAX_COMPOSED_TASK_TEXT_CODE_POINTS,
            composed.codePointCount(0, composed.length),
        )
        assertEquals(
            MAX_COMPOSED_TASK_TEXT_CODE_POINTS,
            mixed.codePointCount(0, mixed.length),
        )
        assertTrue(
            TaskMetadataValidator.validate(
                description = exactLimit,
                hardwareSoftwarePurchases = "",
            ) is TaskMetadataValidationResult.Valid,
        )
        assertTrue(
            TaskMetadataValidator.validate(
                description = "m".repeat(989),
                hardwareSoftwarePurchases = "",
                descriptionTagSnapshots = listOf(TaskTagSnapshotDraft("t".repeat(7))),
            ) is TaskMetadataValidationResult.Valid,
        )
    }
}
