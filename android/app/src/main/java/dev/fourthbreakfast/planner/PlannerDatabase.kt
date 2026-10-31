package dev.fourthbreakfast.planner

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import dev.fourthbreakfast.planner.data.EventDao
import dev.fourthbreakfast.planner.data.EventEntity
import dev.fourthbreakfast.planner.data.TaskDao
import dev.fourthbreakfast.planner.data.TaskEntity

@Database(
    entities = [TaskEntity::class, EventEntity::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = PlannerDatabase.DropFolders::class),
        // the agenda: a new table, nothing existing changes
        AutoMigration(from = 6, to = 7),
    ],
)
abstract class PlannerDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    abstract fun eventDao(): EventDao

    /** Folders were tried and removed. Their tasks stay, at the top level. */
    @DeleteTable(tableName = "folders")
    @DeleteColumn(tableName = "tasks", columnName = "folderId")
    class DropFolders : AutoMigrationSpec
}
