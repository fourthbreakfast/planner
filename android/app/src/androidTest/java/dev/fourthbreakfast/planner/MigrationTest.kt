package dev.fourthbreakfast.planner

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val NAME = "migration-test"

/** What people have saved must survive every update. Run with: gradlew connectedDebugAndroidTest -Pdemo */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val latest = PlannerDatabaseVersion.LATEST

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PlannerDatabase::class.java,
        listOf(PlannerDatabase.DropFolders()),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private fun upgrade() = helper.runMigrationsAndValidate(NAME, latest, true)

    @Test
    fun shortlistAndTaskSchedulesSurviveLaterUpgrades() {
        helper.createDatabase(NAME, 14).use {
            it.execSQL(
                "INSERT INTO tasks (id, title, done, createdAt, updatedAt, parentId, priority, deletedAt, position, starred) " +
                    "VALUES ('t', 'planned task', 0, 1, 2, NULL, 0, NULL, 0, 1)",
            )
            it.execSQL(
                "INSERT INTO events (id, title, day, minute, location, notes, createdAt, updatedAt, repeat, skipDays, taskId) " +
                    "VALUES ('e', 'planned task', 20000, 600, '', '', 1, 2, 'NONE', '', 't')",
            )
        }
        upgrade().use {
            assertEquals("1", it.single("SELECT starred FROM tasks WHERE id = 't'"))
            assertEquals("t", it.single("SELECT taskId FROM events WHERE id = 'e'"))
            assertEquals("0", it.single("SELECT count(*) FROM habits"))
            assertEquals("0", it.single("SELECT count(*) FROM habit_checks"))
            assertEquals("0", it.single("SELECT count(*) FROM calendar_subscriptions"))
            assertEquals("", it.single("SELECT ifnull(calendarSourceId, '') FROM events WHERE id = 'e'"))
            assertEquals("", it.single("SELECT ifnull(calendarName, '') FROM events WHERE id = 'e'"))
            assertEquals("0", it.single("SELECT calendarGeneration FROM events WHERE id = 'e'"))
            assertEquals("", it.single("SELECT ifnull(calendarStartInstant, '') FROM events WHERE id = 'e'"))
            assertEquals("", it.single("SELECT ifnull(calendarEndInstant, '') FROM events WHERE id = 'e'"))
        }
    }

    private fun SupportSQLiteDatabase.single(sql: String): String = query(sql).use {
        assertEquals(1, it.count)
        it.moveToFirst()
        it.getString(0)
    }

    @Test
    fun everyVersionUpgradesToTheLatestSchema() {
        for (version in 1 until latest) {
            helper.createDatabase("$NAME-$version", version).close()
            helper.runMigrationsAndValidate("$NAME-$version", latest, true).close()
        }
    }

    @Test
    fun aTaskFromTheVeryFirstVersionIsKept() {
        helper.createDatabase(NAME, 1).use {
            it.execSQL("INSERT INTO tasks (id, title, done, createdAt, updatedAt) VALUES ('t', 'old task', 1, 5, 6)")
        }
        upgrade().use {
            assertEquals("old task|1|5|6", it.single("SELECT title || '|' || done || '|' || createdAt || '|' || updatedAt FROM tasks"))
            assertEquals("", it.single("SELECT ifnull(parentId, '') FROM tasks"))
            assertEquals("", it.single("SELECT ifnull(deletedAt, '') FROM tasks"))
        }
    }

    @Test
    fun anEventFromBeforeRemindersIsKeptWithNoReminder() {
        helper.createDatabase(NAME, 9).use {
            it.execSQL(
                "INSERT INTO events (id, title, day, minute, endMinute, location, notes, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('e', 'dentist', 20000, 570, 600, 'clinic', 'card', 1, 2, NULL)",
            )
        }
        upgrade().use {
            assertEquals(
                "dentist|20000|570|600|clinic|card",
                it.single("SELECT title || '|' || day || '|' || minute || '|' || endMinute || '|' || location || '|' || notes FROM events"),
            )
            assertEquals("", it.single("SELECT ifnull(reminder, '') FROM events"))
        }
    }

    @Test
    fun aNoteFromBeforePinningIsKeptAndNotPinned() {
        helper.createDatabase(NAME, 10).use {
            it.execSQL(
                "INSERT INTO notes (id, title, body, position, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('n', 'list', 'p0n milk', 1.0, 1, 2, NULL)",
            )
        }
        upgrade().use {
            assertEquals("list|p0n milk|0", it.single("SELECT title || '|' || body || '|' || pinned FROM notes"))
        }
    }

    @Test
    fun aNoteFromBeforeTitlesGetsAnEmptyTitle() {
        helper.createDatabase(NAME, 8).use {
            it.execSQL("INSERT INTO notes (id, body, position, createdAt, updatedAt, deletedAt) VALUES ('n', 'p0n milk', 1.0, 1, 2, NULL)")
        }
        upgrade().use {
            assertEquals("|p0n milk", it.single("SELECT title || '|' || body FROM notes"))
        }
    }

    @Test
    fun anEventFromBeforeRepeatingStaysASingleDay() {
        helper.createDatabase(NAME, 12).use {
            it.execSQL(
                "INSERT INTO events (id, title, day, minute, endMinute, reminder, location, notes, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('e', 'dentist', 20000, 570, 600, 10, 'clinic', 'card', 1, 2, NULL)",
            )
        }
        upgrade().use {
            assertEquals("dentist|20000|570|10", it.single("SELECT title || '|' || day || '|' || minute || '|' || reminder FROM events"))
            assertEquals("", it.single("SELECT ifnull(endDay, '') FROM events"))
            assertEquals("NONE", it.single("SELECT repeat FROM events"))
            assertEquals("", it.single("SELECT ifnull(repeatUntil, '') FROM events"))
            assertEquals("", it.single("SELECT skipDays FROM events"))
        }
    }
}
