package dev.fourthbreakfast.planner

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import dev.fourthbreakfast.planner.data.EventDao
import dev.fourthbreakfast.planner.data.EventEntity
import dev.fourthbreakfast.planner.data.NoteDao
import dev.fourthbreakfast.planner.data.NoteEntity
import dev.fourthbreakfast.planner.data.TaskDao
import dev.fourthbreakfast.planner.data.TaskEntity

@Database(
    entities = [TaskEntity::class, EventEntity::class, NoteEntity::class],
    version = 10,
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
    ],
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun eventDao(): EventDao

    abstract fun noteDao(): NoteDao

    /** Folders were tried and removed. Their tasks stay, at the top level. */
    @DeleteTable(tableName = "folders")
    @DeleteColumn(tableName = "tasks", columnName = "folderId")
    class DropFolders : AutoMigrationSpec
}
