package dev.fourthbreakfast.planner

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.AutoMigrationSpec
import dev.fourthbreakfast.planner.data.AgreedEntity
import dev.fourthbreakfast.planner.data.CalendarSubscriptionDao
import dev.fourthbreakfast.planner.data.CalendarSubscriptionEntity
import dev.fourthbreakfast.planner.data.EventDao
import dev.fourthbreakfast.planner.data.EventEntity
import dev.fourthbreakfast.planner.data.GoneEntity
import dev.fourthbreakfast.planner.data.HabitCheckDao
import dev.fourthbreakfast.planner.data.HabitCheckEntity
import dev.fourthbreakfast.planner.data.HabitDao
import dev.fourthbreakfast.planner.data.HabitEntity
import dev.fourthbreakfast.planner.data.NoteDao
import dev.fourthbreakfast.planner.data.NoteEntity
import dev.fourthbreakfast.planner.data.SyncDao
import dev.fourthbreakfast.planner.data.TaskDao
import dev.fourthbreakfast.planner.data.TaskEntity

object PlannerDatabaseVersion {
    const val LATEST = 16
}

@ConstructedBy(PlannerDatabaseConstructor::class)
@Database(
    entities = [
        TaskEntity::class,
        EventEntity::class,
        NoteEntity::class,
        GoneEntity::class,
        AgreedEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        CalendarSubscriptionEntity::class,
    ],
    version = PlannerDatabaseVersion.LATEST,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = PlannerDatabase.DropFolders::class),
        // the agenda: a new table, nothing existing changes
        AutoMigration(from = 6, to = 7),
        // notes: a new table, nothing existing changes
        AutoMigration(from = 7, to = 8),
        // a title for notes: one new column
        AutoMigration(from = 8, to = 9),
        // reminders: one new column on events
        AutoMigration(from = 9, to = 10),
        // pinned notes: one new column
        AutoMigration(from = 10, to = 11),
        // sync: what was deleted for good, and the versions devices agreed on. two new tables
        AutoMigration(from = 11, to = 12),
        // repeating events and events over several days: four new columns on events
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
    ],
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun calendarSubscriptionDao(): CalendarSubscriptionDao

    abstract fun habitDao(): HabitDao

    abstract fun habitCheckDao(): HabitCheckDao

    abstract fun taskDao(): TaskDao

    abstract fun eventDao(): EventDao

    abstract fun noteDao(): NoteDao

    abstract fun syncDao(): SyncDao

    /** Folders were tried and removed. Their tasks stay, at the top level. */
    @DeleteTable(tableName = "folders")
    @DeleteColumn(tableName = "tasks", columnName = "folderId")
    class DropFolders : AutoMigrationSpec
}

/** Room fills this in for each device. */
@Suppress("KotlinNoActualForExpect")
expect object PlannerDatabaseConstructor : RoomDatabaseConstructor<PlannerDatabase> {
    override fun initialize(): PlannerDatabase
}
