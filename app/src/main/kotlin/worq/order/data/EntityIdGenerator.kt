package worq.order.data

import java.util.UUID

fun interface EntityIdGenerator {
    fun newId(): String
}

object UuidEntityIdGenerator : EntityIdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
