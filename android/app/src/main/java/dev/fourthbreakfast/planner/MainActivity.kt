package dev.fourthbreakfast.planner

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.tasks.TasksScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    private val tasksViewModel: TasksViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as PlannerApp
                TasksViewModel(app.tasks, app.backups)
            }
        }
    }

    private val agendaViewModel: AgendaViewModel by viewModels {
        viewModelFactory {
            initializer { AgendaViewModel((application as PlannerApp).events) }
        }
    }

    private val notesViewModel: NotesViewModel by viewModels {
        viewModelFactory {
            initializer { NotesViewModel((application as PlannerApp).notes) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = (application as PlannerApp).settings
        setContent {
            val dark = when (settings.theme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            // the bars at the top and bottom follow the chosen theme, not only the phone's
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            PlannerTheme(darkTheme = dark) {
                TasksScreen(tasksViewModel, agendaViewModel, notesViewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as PlannerApp).backUpIfDue()
    }
}
