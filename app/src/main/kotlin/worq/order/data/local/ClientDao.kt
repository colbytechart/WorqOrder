package worq.order.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ClientDao {
    @Query(
        """
        SELECT *
        FROM clients
        WHERE is_active = 1
        ORDER BY name COLLATE NOCASE ASC, name ASC, id ASC
        """,
    )
    abstract fun observeActiveClients(): Flow<List<ClientEntity>>

    @Query(
        """
        SELECT *
        FROM clients
        ORDER BY is_active DESC, name COLLATE NOCASE ASC, name ASC, id ASC
        """,
    )
    abstract fun observeAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE id = :clientId LIMIT 1")
    abstract suspend fun readClient(clientId: String): ClientEntity?

    @Query(
        """
        SELECT *
        FROM clients
        WHERE active_name_key = :canonicalName
          AND (:excludingClientId IS NULL OR id != :excludingClientId)
        LIMIT 1
        """,
    )
    abstract suspend fun findNormalizedClientNameConflict(
        canonicalName: String,
        excludingClientId: String?,
    ): ClientEntity?

    @Query(
        """
        SELECT *
        FROM clients
        WHERE is_active = 0
          AND canonical_name = :canonicalName
        ORDER BY created_at_epoch_ms ASC, id ASC
        LIMIT 1
        """,
    )
    abstract suspend fun findArchivedClientByNormalizedName(
        canonicalName: String,
    ): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun addClient(client: ClientEntity)

    @Query(
        """
        UPDATE clients
        SET name = :name,
            canonical_name = :canonicalName,
            active_name_key =
                CASE WHEN is_active = 1 THEN :canonicalName ELSE NULL END,
            updated_at_epoch_ms = :updatedAtEpochMs
        WHERE id = :clientId
        """,
    )
    abstract suspend fun renameClient(
        clientId: String,
        name: String,
        canonicalName: String,
        updatedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE clients
        SET is_active = 0,
            active_name_key = NULL,
            archived_at_epoch_ms = :archivedAtEpochMs,
            updated_at_epoch_ms = :archivedAtEpochMs
        WHERE id = :clientId
          AND is_active = 1
        """,
    )
    abstract suspend fun archiveClient(
        clientId: String,
        archivedAtEpochMs: Long,
    ): Int

    @Query(
        """
        UPDATE clients
        SET is_active = 1,
            active_name_key = canonical_name,
            archived_at_epoch_ms = NULL,
            updated_at_epoch_ms = :restoredAtEpochMs
        WHERE id = :clientId
          AND is_active = 0
        """,
    )
    abstract suspend fun restoreClient(
        clientId: String,
        restoredAtEpochMs: Long,
    ): Int
}
