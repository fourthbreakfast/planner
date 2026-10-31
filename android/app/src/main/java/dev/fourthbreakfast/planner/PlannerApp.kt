package dev.fourthbreakfast.planner

import android.app.Application
import androidx.room.Room
import dev.fourthbreakfast.planner.core.Tasks
import dev.fourthbreakfast.planner.data.RoomTaskStore

class PlannerApp : Application() {
    private val database: PlannerDatabase by lazy {
        Room.databaseBuilder(this, PlannerDatabase::class.java, "planner.db").build()
    }

    val tasks: Tasks by lazy { Tasks(RoomTaskStore(database)) }
}
