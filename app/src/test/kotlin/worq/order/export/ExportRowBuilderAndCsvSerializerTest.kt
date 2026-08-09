package worq.order.export

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import worq.order.export.csv.CsvSerializer
import worq.order.model.Client
import worq.order.model.BillingStatus
import worq.order.model.DailyTask
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval
import worq.order.model.WorkType

class ExportRowBuilderAndCsvSerializerTest {
    private val builder = ExportRowBuilder()
    private val serializer = CsvSerializer()

    @Test
    fun emptyDateUsesExactHeaderUtf8AndSuggestedFilename() {
        val snapshot = builder.build(WORK_DATE, EXPORTED_AT, emptyList())
        val csv = serializer.serialize(snapshot)

        assertEquals(
            listOf(
                "Start date",
                "End date",
                "Consultant",
                "Client",
                "Description",
                "Expense",
                "Work type",
                "Billing Status",
                "Mileage",
                "Interval number",
                "Start time",
                "Stop time",
                "Interval duration",
                "Time spent",
                "Billing minutes",
            ),
            ExportSchema.headers,
        )
        assertEquals(4, snapshot.schemaVersion)
        assertEquals(
            ExportSchema.headers.joinToString(",") + "\r\n",
            csv,
        )
        assertEquals(
            csv,
            String(csv.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8),
        )
        assertEquals(
            "worqorder_2026-07-24.csv",
            CsvExportCoordinator.suggestedFileName(WORK_DATE),
        )
        assertEquals("text/csv", CsvExportCoordinator.MIME_TYPE)
    }

    @Test
    fun zeroOneAndMultipleIntervalsUseOneRowPerIntervalInStableOrder() {
        val zero =
            detail(
                taskId = "zero",
                description = "Zero",
                createdAt = Instant.parse("2026-07-24T10:00:00Z"),
            )
        val one =
            detail(
                taskId = "one",
                description = "One",
                createdAt = Instant.parse("2026-07-24T11:00:00Z"),
                intervals =
                    listOf(
                        interval(
                            id = "one-interval",
                            taskId = "one",
                            ordinal = 1,
                            start = Instant.parse("2026-07-24T12:00:00Z"),
                            stop = Instant.parse("2026-07-24T12:30:00Z"),
                        ),
                    ),
            )
        val multiple =
            detail(
                taskId = "multiple",
                description = "Multiple",
                createdAt = Instant.parse("2026-07-24T12:00:00Z"),
                intervals =
                    listOf(
                        interval(
                            id = "later",
                            taskId = "multiple",
                            ordinal = 1,
                            start = Instant.parse("2026-07-24T15:00:00Z"),
                            stop = Instant.parse("2026-07-24T16:00:00Z"),
                        ),
                        interval(
                            id = "earlier",
                            taskId = "multiple",
                            ordinal = 2,
                            start = Instant.parse("2026-07-24T13:00:00Z"),
                            stop = Instant.parse("2026-07-24T14:00:00Z"),
                        ),
                    ),
            )

        val first =
            builder.build(
                workDate = WORK_DATE,
                exportedAt = EXPORTED_AT,
                tasks = listOf(multiple, one, zero),
            )
        val second =
            builder.build(
                workDate = WORK_DATE,
                exportedAt = EXPORTED_AT,
                tasks =
                    listOf(
                        zero,
                        one,
                        multiple.copy(intervals = multiple.intervals.reversed()),
                    ),
            )

        assertEquals(4, first.rows.size)
        assertEquals(
            listOf("Zero", "One", "Multiple", "Multiple"),
            first.rows.map { it["Description"] },
        )
        assertEquals(
            listOf("", "08:00 AM", "09:00 AM", "11:00 AM"),
            first.rows.map { it["Start time"] },
        )
        assertEquals("", first.rows.first()["Interval number"])
        assertEquals("", first.rows.first()["Billing Status"])
        assertEquals("", first.rows.first()["Interval duration"])
        assertEquals("00:00:00", first.rows.first()["Time spent"])
        assertEquals("0", first.rows.first()["Billing minutes"])
        assertEquals(
            serializer.serialize(first),
            serializer.serialize(second),
        )
    }

    @Test
    fun serializerEscapesCommaQuoteAndLineBreakAndPreservesUnicode() {
        val clientName = "Acme, International"
        val description = "Line 1\r\n\"quoted\", café 😀 東京"
        val purchases = "Suite,\nPro"
        val detail =
            detail(
                taskId = "escaped",
                clientName = clientName,
                description = description,
                purchases = purchases,
            )

        val csv =
            serializer.serialize(
                builder.build(WORK_DATE, EXPORTED_AT, listOf(detail)),
            )

        assertTrue(csv.contains("\"Acme, International\""))
        assertTrue(csv.contains("\"Line 1\r\n\"\"quoted\"\", café 😀 東京\""))
        assertTrue(csv.contains("\"Suite,\nPro\""))
        assertEquals(
            csv,
            csv.toByteArray(StandardCharsets.UTF_8).toString(StandardCharsets.UTF_8),
        )
        assertFalse(csv.startsWith("\uFEFF"))
    }

    @Test
    fun longDurationDoesNotWrapAndLocalDstValuesUseClockTimeOnly() {
        val zone = ZoneId.of("America/New_York")
        val longTask =
            detail(
                taskId = "long",
                description = "Long",
                workDate = LocalDate.of(2026, 11, 1),
                zoneId = zone,
                intervals =
                    listOf(
                        interval(
                            id = "twenty-five-hours",
                            taskId = "long",
                            ordinal = 1,
                            start = Instant.parse("2026-11-01T04:00:00Z"),
                            stop = Instant.parse("2026-11-02T05:00:00Z"),
                        ),
                    ),
            )
        val repeatedHourTask =
            detail(
                taskId = "repeated-hour",
                description = "Repeated",
                workDate = LocalDate.of(2026, 11, 1),
                zoneId = zone,
                createdAt = Instant.parse("2026-11-01T04:01:00Z"),
                intervals =
                    listOf(
                        interval(
                            id = "fallback",
                            taskId = "repeated-hour",
                            ordinal = 1,
                            start = Instant.parse("2026-11-01T05:30:00Z"),
                            stop = Instant.parse("2026-11-01T06:30:00Z"),
                        ),
                    ),
            )

        val snapshot =
            builder.build(
                workDate = LocalDate.of(2026, 11, 1),
                exportedAt = Instant.parse("2026-11-02T06:00:00Z"),
                tasks = listOf(longTask, repeatedHourTask),
            )
        val longRow = snapshot.rows.first { it["Description"] == "Long" }
        val repeatedHourRow =
            snapshot.rows.first { it["Description"] == "Repeated" }

        assertEquals("25:00:00", longRow["Interval duration"])
        assertEquals("25:00:00", longRow["Time spent"])
        assertEquals("1500", longRow["Billing minutes"])
        assertEquals("01:30 AM", repeatedHourRow["Start time"])
        assertEquals("01:30 AM", repeatedHourRow["Stop time"])
    }

    @Test
    fun runningIntervalUsesOneExportInstantAndLeavesStopBlank() {
        val running =
            detail(
                taskId = "running",
                intervals =
                    listOf(
                        interval(
                            id = "open",
                            taskId = "running",
                            ordinal = 1,
                            start = Instant.parse("2026-07-24T12:00:00Z"),
                            stop = null,
                        ),
                    ),
            )

        val snapshot =
            builder.build(
                workDate = WORK_DATE,
                exportedAt = Instant.parse("2026-07-24T13:30:00Z"),
                tasks = listOf(running),
            )
        val row = snapshot.rows.single()

        assertEquals("", row["Stop time"])
        assertEquals("01:30:00", row["Interval duration"])
        assertEquals("01:30:00", row["Time spent"])
        assertEquals(
            "2026-07-24T13:30:00Z",
            snapshot.exportedAt.toString(),
        )
        assertEquals(4, snapshot.schemaVersion)
    }

    @Test
    fun exportedDurationsDropMillisecondsWithoutRounding() {
        val detail =
            detail(
                taskId = "fractional",
                intervals =
                    listOf(
                        interval(
                            id = "fractional-interval",
                            taskId = "fractional",
                            ordinal = 1,
                            start = Instant.parse("2026-07-24T12:00:00Z"),
                            stop = Instant.parse("2026-07-24T13:00:00.999Z"),
                        ),
                    ),
            )

        val row = builder.build(WORK_DATE, EXPORTED_AT, listOf(detail)).rows.single()

        assertEquals("01:00:00", row["Interval duration"])
        assertEquals("01:00:00", row["Time spent"])
    }

    @Test
    fun consultantWorkTypeBillingStatusMileageAndBillingMinutesUseCanonicalSchemaOnce() {
        val detail =
            detail(
                taskId = "v4",
                employee = "Alex Rivera",
                workType = WorkType.ON_SITE,
                billingStatus = BillingStatus.DO_NOT_CHARGE,
                mileage = "18.5",
                intervals =
                    listOf(
                        interval(
                            id = "v4-interval",
                            taskId = "v4",
                            ordinal = 1,
                            start = Instant.parse("2026-07-24T12:00:00Z"),
                            stop = Instant.parse("2026-07-24T12:12:32Z"),
                        ),
                    ),
            )

        val snapshot = builder.build(WORK_DATE, EXPORTED_AT, listOf(detail))
        val row = snapshot.rows.single()

        assertEquals("07/24/2026", row["Start date"])
        assertEquals("07/24/2026", row["End date"])
        assertEquals("Alex Rivera", row["Consultant"])
        assertEquals("On-Site", row["Work type"])
        assertEquals("Do not charge", row["Billing Status"])
        assertEquals("18.5", row["Mileage"])
        assertEquals("00:12:32", row["Time spent"])
        assertEquals("15", row["Billing minutes"])
        assertEquals(ExportSchema.headers, serializer.serialize(snapshot).lineSequence().first().split(','))
    }

    private operator fun ExportRow.get(column: String): String =
        values[ExportSchema.headers.indexOf(column)]

    private fun detail(
        taskId: String,
        workDate: LocalDate = WORK_DATE,
        zoneId: ZoneId = ZONE,
        clientName: String = "Client",
        description: String = "Description",
        purchases: String = "",
        employee: String = "",
        workType: WorkType = WorkType.UNSPECIFIED,
        billingStatus: BillingStatus? = null,
        mileage: String? = null,
        createdAt: Instant = Instant.parse("2026-07-24T10:00:00Z"),
        intervals: List<WorkInterval> = emptyList(),
    ): TaskWithIntervals {
        val client =
            Client(
                id = "client-$taskId",
                name = clientName,
                canonicalName = clientName.lowercase(),
                isActive = true,
                createdAt = createdAt.minusSeconds(60),
                updatedAt = createdAt,
                archivedAt = null,
            )
        val task =
            DailyTask(
                id = taskId,
                seriesId = "series-$taskId",
                clientId = client.id,
                description = description,
                hardwareSoftwarePurchases = purchases,
                employeeNameSnapshot = employee,
                workType = workType,
                billingStatus = billingStatus,
                mileage = mileage,
                workDate = workDate,
                zoneId = zoneId,
                createdAt = createdAt,
                updatedAt = createdAt.plusSeconds(30),
            )
        return TaskWithIntervals(
            taskWithClient = TaskWithClient(task, client),
            intervals = intervals,
        )
    }

    private fun interval(
        id: String,
        taskId: String,
        ordinal: Int,
        start: Instant,
        stop: Instant?,
    ): WorkInterval =
        WorkInterval(
            id = id,
            taskId = taskId,
            ordinal = ordinal,
            start = start,
            stop = stop,
            wasManuallyEdited = false,
            createdAt = start,
            updatedAt = stop ?: start,
        )

    private companion object {
        val WORK_DATE: LocalDate = LocalDate.of(2026, 7, 24)
        val EXPORTED_AT: Instant = Instant.parse("2026-07-24T20:00:00Z")
        val ZONE: ZoneId = ZoneId.of("America/New_York")
    }
}
