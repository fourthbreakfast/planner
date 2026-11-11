package dev.fourthbreakfast.planner.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import dev.fourthbreakfast.planner.PlannerDatabase
import dev.fourthbreakfast.planner.core.CalendarFailure
import dev.fourthbreakfast.planner.core.CalendarSubscription
import dev.fourthbreakfast.planner.core.Store
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "calendar_subscriptions")
data class CalendarSubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val createdAt: Long,
    val lastAttempt: Long?,
    val lastSuccess: Long?,
    val etag: String?,
    val lastModified: String?,
    val failure: String?,
    val fromDay: Long?,
    val throughDay: Long?,
    val cachedFeed: String?,
)

fun CalendarSubscriptionEntity.toSubscription() = CalendarSubscription(
    id, name, url, createdAt, lastAttempt, lastSuccess, etag, lastModified,
    CalendarFailure.entries.firstOrNull { it.name == failure }, fromDay, throughDay, cachedFeed,
)

fun CalendarSubscription.toEntity() = CalendarSubscriptionEntity(
    id, name, url, createdAt, lastAttempt, lastSuccess, etag, lastModified, failure?.name, fromDay, throughDay, cachedFeed,
)

@Dao
interface CalendarSubscriptionDao : EntityDao<CalendarSubscriptionEntity> {
    @Query("SELECT * FROM calendar_subscriptions ORDER BY name, id")
    override fun observeAll(): Flow<List<CalendarSubscriptionEntity>>

    @Query("SELECT * FROM calendar_subscriptions ORDER BY name, id")
    override suspend fun getAll(): List<CalendarSubscriptionEntity>
}

/** Local metadata deletion must not create a new source tombstone; the calendar service explicitly owns that operation. */
internal class RoomCalendarStore(private val database: PlannerDatabase) : Store<CalendarSubscription> {
    private val dao get() = database.calendarSubscriptionDao()
    override fun observeAll(): Flow<List<CalendarSubscription>> = dao.observeAll().map { rows -> rows.map(CalendarSubscriptionEntity::toSubscription) }
    override suspend fun all(): List<CalendarSubscription> = dao.getAll().map(CalendarSubscriptionEntity::toSubscription)
    override suspend fun insert(items: List<CalendarSubscription>) = dao.insertAll(items.map(CalendarSubscription::toEntity))
    override suspend fun update(items: List<CalendarSubscription>) = dao.updateAll(items.map(CalendarSubscription::toEntity))
    override suspend fun delete(items: List<CalendarSubscription>) = dao.deleteAll(items.map(CalendarSubscription::toEntity))
    override suspend fun <R> transaction(block: suspend () -> R): R = roomTransactions(database).transaction(block)
}
