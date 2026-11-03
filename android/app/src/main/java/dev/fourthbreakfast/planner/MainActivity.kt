package dev.fourthbreakfast.planner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.fourthbreakfast.planner.agenda.AgendaViewModel
import dev.fourthbreakfast.planner.notes.NotesViewModel
import dev.fourthbreakfast.planner.settings.SettingsScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.LocalServices
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
        // a turn of the screen keeps the same intent, and it must not act twice
        if (savedInstanceState == null) route(intent)
        val settings = (application as PlannerApp).settings
        setContent {
            val dark = when (settings.theme) {
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK -> true
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
            }
            // the bars at the top and bottom follow the chosen theme, not only the phone's
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            CompositionLocalProvider(LocalServices provides (application as PlannerApp).services) {
                PlannerTheme(darkTheme = dark) {
                    PlannerScreen(tasksViewModel, agendaViewModel, notesViewModel) { onClose -> SettingsScreen(tasksViewModel, onClose) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    /** What a shortcut, a notification or a share is asking for. */
    private fun route(intent: Intent?) {
        val requests = (application as PlannerApp).requests
        val event = intent?.getStringExtra(OPEN_EVENT)
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
        when {
            event != null -> requests.ask(Launch.OpenEvent(event))
            intent?.action == NEW_TASK -> requests.ask(Launch.NewTask)
            intent?.action == NEW_EVENT -> requests.ask(Launch.NewEvent)
            intent?.action == NEW_NOTE -> requests.ask(Launch.NewNote)
            intent?.action == Intent.ACTION_SEND && !shared.isNullOrBlank() ->
                requests.ask(Launch.Shared(shared, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()))
        }
    }

    override fun onStart() {
        super.onStart()
        (application as PlannerApp).syncController.onInSight()
        (application as PlannerApp).checkForUpdatesIfDue()
    }

    override fun onStop() {
        super.onStop()
        (application as PlannerApp).syncController.onOutOfSight()
        (application as PlannerApp).backUpIfDue()
    }

    companion object {
        const val NEW_TASK = "planner.NEW_TASK"
        const val NEW_EVENT = "planner.NEW_EVENT"
        const val NEW_NOTE = "planner.NEW_NOTE"
        const val OPEN_EVENT = "open_event"
    }
}
