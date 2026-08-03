package worq.order.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientCsvFilePolicyTest {
    @Test
    fun requiresCsvExtensionAndSupportedMimeType() {
        assertTrue(ClientCsvFilePolicy.isSupported("clients.CSV", "text/csv"))
        assertTrue(
            ClientCsvFilePolicy.isSupported(
                "clients.csv",
                "text/csv; charset=utf-8",
            ),
        )
        assertFalse(ClientCsvFilePolicy.isSupported("clients.txt", "text/csv"))
        assertFalse(ClientCsvFilePolicy.isSupported("clients.csv", "text/plain"))
        assertFalse(ClientCsvFilePolicy.isSupported(null, "text/csv"))
        assertFalse(ClientCsvFilePolicy.isSupported("clients.csv", null))
    }
}
