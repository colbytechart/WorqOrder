package worq.order.ui

import androidx.compose.ui.text.input.KeyboardCapitalization
import org.junit.Assert.assertEquals
import org.junit.Test

class WorqOrderTextInputDefaultsTest {
    @Test
    fun freeTextRequestsSentenceCapitalization() {
        assertEquals(
            KeyboardCapitalization.Sentences,
            WorqOrderTextInputDefaults.sentenceCapitalization.capitalization,
        )
    }
}
