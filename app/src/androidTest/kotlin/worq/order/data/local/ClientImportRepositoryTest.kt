package worq.order.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.ArrayDeque
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import worq.order.data.ClientImportCandidate
import worq.order.data.EntityIdGenerator
import worq.order.timer.UtcClock

@RunWith(AndroidJUnit4::class)
class ClientImportRepositoryTest {
    private lateinit var database: WorqOrderDatabase
    private lateinit var dao: ClientDao

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room.inMemoryDatabaseBuilder(context, WorqOrderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.clientDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importAtomicallyAddsRestoresSkipsAndLeavesActiveClientsAlphabetical() =
        runBlocking {
            dao.addClient(client("active", "Zulu", "zulu", true))
            dao.addClient(client("archived", "Beta", "beta", false))
            val repository =
                RoomClientImportRepository(
                    clientDao = dao,
                    idGenerator =
                        QueueIdGenerator(
                            "unused-active-match",
                            "unused-archived-match",
                            "new-alpha",
                        ),
                    clock = FixedClock,
                )

            val result =
                repository.applyImport(
                    listOf(
                        ClientImportCandidate(" zulu ignored ", "zulu"),
                        ClientImportCandidate("Beta", "beta"),
                        ClientImportCandidate("Alpha", "alpha"),
                    ),
                )

            assertEquals(1, result.addedCount)
            assertEquals(1, result.restoredCount)
            assertEquals(1, result.skippedActiveCount)
            assertEquals(
                listOf("Alpha", "Beta", "Zulu"),
                dao.observeActiveClients().first().map(ClientEntity::name),
            )
        }

    @Test
    fun failedLaterInsertRollsBackAnEarlierRestore() =
        runBlocking {
            dao.addClient(client("archived", "Beta", "beta", false))

            runCatching {
                dao.applyClientImport(
                    candidates =
                        listOf(
                            ClientImportEntityCandidate("unused", "Beta", "beta"),
                            ClientImportEntityCandidate("archived", "Alpha", "alpha"),
                        ),
                    importedAtEpochMs = FixedClock.now().toEpochMilli(),
                )
            }

            val archived = requireNotNull(dao.readClient("archived"))
            assertFalse(archived.isActive)
            assertTrue(dao.observeActiveClients().first().isEmpty())
        }

    private fun client(
        id: String,
        name: String,
        canonicalName: String,
        active: Boolean,
    ) = ClientEntity(
        id = id,
        name = name,
        canonicalName = canonicalName,
        activeNameKey = canonicalName.takeIf { active },
        isActive = active,
        createdAtEpochMs = FixedClock.now().toEpochMilli(),
        updatedAtEpochMs = FixedClock.now().toEpochMilli(),
        archivedAtEpochMs = FixedClock.now().toEpochMilli().takeUnless { active },
    )

    private class QueueIdGenerator(
        vararg ids: String,
    ) : EntityIdGenerator {
        private val values = ArrayDeque(ids.toList())

        override fun newId(): String = values.removeFirst()
    }

    private data object FixedClock : UtcClock {
        override fun now(): Instant = Instant.parse("2026-08-02T12:00:00Z")
    }
}
