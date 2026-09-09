package worq.order.export.automatic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The earliest instant at which the captured work date may be exported automatically. */
internal fun automaticGoogleExportBoundary(
    workDate: LocalDate,
    zoneId: ZoneId,
): Instant = workDate.plusDays(1).atStartOfDay(zoneId).toInstant()
