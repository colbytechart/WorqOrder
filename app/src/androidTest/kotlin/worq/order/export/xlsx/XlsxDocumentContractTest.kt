package worq.order.export.xlsx

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.export.XlsxExportCoordinator

@RunWith(AndroidJUnit4::class)
class XlsxDocumentContractTest {
    @Test
    fun createDocumentUsesXlsxActionMimeAndSuggestedName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "worqorder_2026-07-24.xlsx"
        val intent =
            ActivityResultContracts
                .CreateDocument(XlsxExportCoordinator.MIME_TYPE)
                .createIntent(context, fileName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            intent.type,
        )
        assertEquals(fileName, intent.getStringExtra(Intent.EXTRA_TITLE))
    }
}
