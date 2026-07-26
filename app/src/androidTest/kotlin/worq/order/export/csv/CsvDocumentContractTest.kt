package worq.order.export.csv

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.export.CsvExportCoordinator

@RunWith(AndroidJUnit4::class)
class CsvDocumentContractTest {
    @Test
    fun createDocumentUsesCsvActionMimeAndSuggestedName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "worqorder_2026-07-24.csv"
        val intent =
            ActivityResultContracts
                .CreateDocument(CsvExportCoordinator.MIME_TYPE)
                .createIntent(context, fileName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("text/csv", intent.type)
        assertEquals(fileName, intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
