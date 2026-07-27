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
import worq.order.model.DailyTask
import worq.order.model.TaskWithClient
import worq.order.model.TaskWithIntervals
import worq.order.model.WorkInterval

class ExportRowBuilderAndCsvSerializerTest {
    private val builder = ExportRowBuilder()
    private val serializer = CsvSerializer()

    @Test
    fun emptyDateUsesExactHeaderUtf8AndSuggestedFilename() {
        val snapshot = builder.build(WORK_DATE, EXPORTED_AT, emptyList())
        val csv = serializer.serialize(snapshot)

        assertEquals(
            listOf(
                "Work Date",
                "Client Name",
                "Description",
                "Hardware / Software Purchases",
                "Interval Number",
                "Start Local",
                "Stop Local",
                "Interval Duration Formatted",
                "Task Total Duration Formatted",
            ),
            ExportSchema.headers,
        )
        assertEquals(2, snapshot.schemaVersion)
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
            listOf("", "08:00", "09:00", "11:00"),
            first.rows.map { it["Start Local"] },
        )
        assertEquals("", first.rows.first()["Interval Number"])
        assertEquals("", first.rows.first()["Interval Duration Formatted"])
        assertEquals("00:00:00", first.rows.first()["Task Total Duration Formatted"])
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

        assertEquals("25:00:00", longRow["Interval Duration Formatted"])
        assertEquals("25:00:00", longRow["Task Total Duration Formatted"])
        assertEquals("01:30", repeatedHourRow["Start Local"])
        assertEquals("01:30", repeatedHourRow["Stop Local"])
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

        assertEquals("", row["Stop Local"])
        assertEquals("01:30:00", row["Interval Duration Formatted"])
        assertEquals("01:30:00", row["Task Total Duration Formatted"])
        assertEquals(
            "2026-07-24T13:30:00Z",
            snapshot.exportedAt.toString(),
        )
        assertEquals(2, snapshot.schemaVersion)
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

        assertEquals("01:00:00", row["Interval Duration Formatted"])
        assertEquals("01:00:00", row["Task Total Duration Formatted"])
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
