package worq.order.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableBackupDocumentContractTest {
    @Test
    fun createDocumentUsesZipActionMimeAndSuggestedName() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fileName = "WorqOrder_Backup_2026-09-20_150000.zip"
        val intent =
            ActivityResultContracts
                .CreateDocument(PORTABLE_BACKUP_MIME_TYPE)
                .createIntent(context, fileName)

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals(PORTABLE_BACKUP_MIME_TYPE, intent.type)
        assertEquals(fileName, intent.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun documentOutputAcceptsOnlyScopedContentUris() {
        assertEquals(
            true,
            isPortableBackupDocumentUri(Uri.parse("content://documents/backup.zip")),
        )
        assertEquals(false, isPortableBackupDocumentUri(Uri.parse("file:///sdcard/backup.zip")))
        assertEquals(false, isPortableBackupDocumentUri(Uri.EMPTY))
        assertEquals(false, isPortableBackupDocumentUri(Uri.parse("content:///backup.zip")))
    }
}
