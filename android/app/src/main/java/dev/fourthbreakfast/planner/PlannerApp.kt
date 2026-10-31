package dev.fourthbreakfast.planner

import android.app.Application
import androidx.room.Room
import dev.fourthbreakfast.planner.core.Backups
import dev.fourthbreakfast.planner.core.Events
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.data.RoomEventStore
import dev.fourthbreakfast.planner.data.RoomTaskStore

class PlannerApp : Application() {
    private val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db").build()
    }

    private val taskStore by lazy { RoomTaskStore(database) }
    private val eventStore by lazy { RoomEventStore(database) }

    val tasks: Tasks by lazy { Tasks(taskStore) }
    val events: Events by lazy { Events(eventStore) }
    val backups: Backups by lazy { Backups(taskStore, eventStore) }
}
