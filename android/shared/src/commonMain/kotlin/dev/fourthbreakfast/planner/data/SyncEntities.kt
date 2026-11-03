package dev.fourthbreakfast.planner.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.Gone
import dev.fourthbreakfast.planner.core.SyncLedger

/** A task, event or note deleted for good, and when. */
@Entity(tableName = "gone")
data class GoneEntity(@PrimaryKey val id: String, val at: Long)

/** The version of a thing that this device and another last agreed on. */
@Entity(tableName = "agreed")
data class AgreedEntity(@PrimaryKey val id: String, val updatedAt: Long)

@Dao
interface SyncDao {
    @Query("SELECT * FROM gone")
    suspend fun gone(): List<GoneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGone(items: List<GoneEntity>)

    @Query("SELECT * FROM agreed")
    suspend fun agreed(): List<AgreedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setAgreed(items: List<AgreedEntity>)
}

class RoomSyncLedger(database: PlannerDatabase) : SyncLedger {
    private val dao = database.syncDao()

    override suspend fun gone(): List<Gone> = dao.gone().map { Gone(it.id, it.at) }

    override suspend fun addGone(items: List<Gone>) = dao.addGone(items.map { GoneEntity(it.id, it.at) })

    override suspend fun agreed(): Map<String, Long> = dao.agreed().associate { it.id to it.updatedAt }

    override suspend fun setAgreed(items: Map<String, Long>) = dao.setAgreed(items.map { (id, at) -> AgreedEntity(id, at) })
}

/** Every store deletes for good through here, so the mark that keeps it gone on other devices is never left out. */
internal suspend fun PlannerDatabase.forget(ids: List<String>) {
    if (ids.isEmpty()) return
    val now = System.currentTimeMillis()
    syncDao().addGone(ids.map { GoneEntity(it, now) })
}

/** Runs [block] as one write, on a phone or a computer alike. A write inside another one joins it. */
internal suspend fun <T> PlannerDatabase.inTransaction(block: suspend () -> T): T = useWriterConnection { it.immediateTransaction { block() } }
