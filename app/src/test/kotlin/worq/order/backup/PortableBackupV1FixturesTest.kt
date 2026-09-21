package worq.order.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mechanical coverage for the v0.6 Task 50A fixture contract.
 *
 * Terra's production DTO/validator tests consume these same cases in Task 50B. This test does not
 * claim that backup parsing or replacement is implemented; it prevents the approved logical state
 * matrix from being narrowed while the production boundary is built.
 */
class PortableBackupV1FixturesTest {
    @Test
    fun completeFixtureCoversEveryPortableDomainAreaAndPreservesOrder() {
        val fixture = PortableBackupV1Fixtures.complete

        assertEquals(1, fixture.formatVersion)
        assertEquals(7, fixture.dataModelVersion)
        assertEquals(2, fixture.clients.size)
        assertEquals(2, fixture.consultants.size)
        assertEquals(3, fixture.tags.size)
        assertEquals(2, fixture.tasks.size)
        assertEquals("Café Client", fixture.clients.first().name)
        assertEquals("東京 inspection 🚀", fixture.tags[1].text)

        val timed = fixture.tasks.first { it.id == "task-complete" }
        assertEquals(listOf("東京 inspection 🚀", "Confirm access"), timed.descriptionTags.map { it.text })
        assertEquals(listOf(0, 1), timed.descriptionTags.map { it.order })
        assertEquals(listOf("Replacement cable"), timed.expenseTags.map { it.text })
        assertEquals(1, timed.intervals.size)
        assertTrue(timed.intervals.single().stop.isAfter(timed.intervals.single().start))
        assertEquals("client-active", timed.clientId)
        assertEquals("consultant-active", timed.consultantId)
        assertEquals("America/New_York", timed.zoneId)
        assertEquals("BILLABLE", timed.billingStatus)
        assertEquals("Café notes 東京 🚀", timed.notes)

        assertEquals("GOOGLE_SHEETS", fixture.preferences.defaultExportDestination)
        assertEquals("consultant-active", fixture.preferences.selectedConsultantId)
        assertEquals("task-complete", fixture.preferences.selectedTaskId)
        assertEquals(1, fixture.preferences.exportHistory.size)
        assertEquals("SUCCESS", fixture.preferences.exportHistory.single().outcome)
    }

    @Test
    fun emptyFixturePreservesAnEmptyButValidPortableState() {
        val fixture = PortableBackupV1Fixtures.empty

        assertEquals(1, fixture.formatVersion)
        assertTrue(fixture.clients.isEmpty())
        assertTrue(fixture.consultants.isEmpty())
        assertTrue(fixture.tags.isEmpty())
        assertTrue(fixture.tasks.isEmpty())
        assertTrue(fixture.preferences.exportHistory.isEmpty())
        assertEquals("DEVICE", fixture.preferences.timeZoneMode)
        assertEquals("CSV", fixture.preferences.defaultExportDestination)
    }

    @Test
    fun includedCollectionsHaveStableIdentityAndRelationshipCoverage() {
        val fixture = PortableBackupV1Fixtures.complete
        val ids = fixture.clients.map { it.id } + fixture.consultants.map { it.id } +
            fixture.tags.map { it.id } + fixture.tasks.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
        fixture.tasks.forEach { task ->
            assertNotNull(fixture.clients.singleOrNull { it.id == task.clientId })
            task.consultantId?.let { consultantId ->
                assertNotNull(fixture.consultants.singleOrNull { it.id == consultantId })
            }
            task.intervals.forEach { interval ->
                assertEquals(task.id, interval.taskId)
                assertTrue(interval.stop.isAfter(interval.start))
            }
            task.descriptionTags.forEach { snapshot ->
                assertEquals("DESCRIPTION", snapshot.category)
                assertTrue(snapshot.order >= 0)
            }
            task.expenseTags.forEach { snapshot ->
                assertEquals("HARDWARE_SOFTWARE_PURCHASE", snapshot.category)
                assertTrue(snapshot.order >= 0)
            }
        }
    }

    @Test
    fun invalidCaseMatrixCoversEveryApprovedRejectionClass() {
        val cases = PortableBackupV1Fixtures.invalidCases
        val names = cases.map { it.name }.toSet()
        val failureCodes = cases.map { it.expectedFailureCode }.toSet()

        assertEquals(12, cases.size)
        assertEquals(cases.size, names.size)
        assertTrue("duplicate-id" in names)
        assertTrue("broken-reference" in names)
        assertTrue("active-timer" in names)
        assertTrue("open-interval" in names)
        assertTrue("invalid-enum" in names)
        assertTrue("invalid-zone" in names)
        assertTrue("invalid-instant" in names)
        assertTrue("overlength-text" in names)
        assertTrue("overlength-tag" in names)
        assertTrue("unsupported-future" in names)
        assertTrue("legacy-v0" in names)
        assertTrue("DUPLICATE_ID" in failureCodes)
        assertTrue("BROKEN_REFERENCE" in failureCodes)
        assertTrue("ACTIVE_TIMER_PRESENT" in failureCodes)
        assertTrue("OPEN_INTERVAL" in failureCodes)
        assertTrue("INVALID_ENUM" in failureCodes)
        assertTrue("INVALID_ZONE_ID" in failureCodes)
        assertTrue("INVALID_INSTANT" in failureCodes)
        assertTrue("TEXT_LIMIT" in failureCodes)
        assertTrue("TAG_LIMIT" in failureCodes)
        assertTrue("UNSUPPORTED_VERSION" in failureCodes)
    }

    @Test
    fun approvedLengthBoundariesAreRepresentedByFixtureMatrix() {
        val complete = PortableBackupV1Fixtures.complete
        val maxText = "x".repeat(999)
        val maxTag = "t".repeat(400)
        val overText = "x".repeat(1000)
        val overTag = "t".repeat(401)

        assertEquals(999, maxText.codePointCount(0, maxText.length))
        assertEquals(400, maxTag.codePointCount(0, maxTag.length))
        assertEquals(1000, overText.codePointCount(0, overText.length))
        assertEquals(401, overTag.codePointCount(0, overTag.length))
        assertTrue(
            complete.tasks
                .flatMap { it.intervals }
                .all { interval -> interval.stop.isAfter(interval.start) },
        )
    }

    @Test
    fun excludedRuntimeAndCredentialStateIsExplicitlyOutsideThePortableModel() {
        val excluded = PortableBackupV1Fixtures.excludedState

        assertTrue(excluded.activeTimer)
        assertTrue(excluded.openInterval)
        assertTrue(excluded.oauthCredentials)
        assertTrue(excluded.googleConnectionMetadata)
        assertTrue(excluded.notificationPermission)
        assertTrue(excluded.workManagerJob)
        assertTrue(excluded.pendingAutomaticExport)
        assertTrue(excluded.transientUiState)
        assertTrue(excluded.cache)
        assertTrue(excluded.recoveryJournal)
        assertTrue(excluded.rollingRestorePoint)
        assertTrue(excluded.installationExportOrigin)
        assertEquals(100L * 1024L * 1024L, PortableBackupV1Fixtures.maxCompressedBytes)
        assertEquals(500L * 1024L * 1024L, PortableBackupV1Fixtures.maxExpandedBytes)
    }
}
