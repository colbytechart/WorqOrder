package worq.order.data

import java.util.Locale

const val MAX_TAG_CODE_POINTS = 400
const val MAX_COMPOSED_TASK_TEXT_CODE_POINTS = 999

data class NormalizedTagText(
    val displayText: String,
    val normalizedText: String,
)

enum class TagTextValidationError {
    BLANK,
    TOO_LONG,
}

sealed interface TagTextValidationResult {
    data class Valid(
        val text: NormalizedTagText,
    ) : TagTextValidationResult

    data class Invalid(
        val error: TagTextValidationError,
    ) : TagTextValidationResult
}

/**
 * Canonicalizes a reusable Tag without altering task history. One optional final period is
 * ignored for duplicate detection so `Install monitor` and `Install monitor.` cannot coexist in
 * one catalog, while the display snapshot still preserves the user's chosen punctuation.
 */
object TagTextNormalizer {
    fun validate(rawText: String): TagTextValidationResult {
        val displayText = collapseWhitespace(rawText)
        if (displayText.isEmpty()) {
            return TagTextValidationResult.Invalid(TagTextValidationError.BLANK)
        }
        if (displayText.codePointCount(0, displayText.length) > MAX_TAG_CODE_POINTS) {
            return TagTextValidationResult.Invalid(TagTextValidationError.TOO_LONG)
        }
        return TagTextValidationResult.Valid(
            NormalizedTagText(
                displayText = displayText,
                normalizedText = canonicalize(displayText),
            ),
        )
    }

    fun canonicalize(text: String): String =
        collapseWhitespace(text)
            .removeSuffix(".")
            .trimEnd()
            .lowercase(Locale.ROOT)

    fun collapseWhitespace(value: String): String =
        buildString(value.length) {
            var pendingSpace = false
            value.forEach { character ->
                if (character.isWhitespace()) {
                    pendingSpace = isNotEmpty()
                } else {
                    if (pendingSpace) append(' ')
                    append(character)
                    pendingSpace = false
                }
            }
        }
}

/**
 * The single export-only composition rule for manually entered task text plus applied Tag
 * snapshots. It never mutates either stored source value.
 */
object TaskTextComposer {
    fun compose(
        manualText: String,
        tagTexts: Iterable<String>,
    ): String =
        buildList {
            add(manualText)
            tagTexts.forEach(::add)
        }.map(TagTextNormalizer::collapseWhitespace)
            .filter(String::isNotEmpty)
            .joinToString(" ") { component ->
                if (component.last() in TERMINAL_PUNCTUATION) component else "$component."
            }

    fun codePointCount(value: String): Int = value.codePointCount(0, value.length)

    private val TERMINAL_PUNCTUATION = setOf('.', '?', '!')
}
