package worq.order.timer

import java.time.ZoneId

object GeographicalZoneIds {
    fun isSelectable(zoneId: ZoneId): Boolean =
        GEOGRAPHICAL_PREFIXES.any { prefix ->
            zoneId.id.startsWith("$prefix/")
        }

    fun available(): List<ZoneId> =
        ZoneId
            .getAvailableZoneIds()
            .asSequence()
            .mapNotNull { id -> runCatching { ZoneId.of(id) }.getOrNull() }
            .filter(::isSelectable)
            .sortedBy { it.id }
            .toList()

    private val GEOGRAPHICAL_PREFIXES =
        setOf(
            "Africa",
            "America",
            "Antarctica",
            "Arctic",
            "Asia",
            "Atlantic",
            "Australia",
            "Europe",
            "Indian",
            "Pacific",
        )
}
