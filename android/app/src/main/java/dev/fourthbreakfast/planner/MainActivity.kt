package dev.fourthbreakfast.planner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
    private val app: PlannerApp get() = application as PlannerApp

    private val tasksViewModel: TasksViewModel by viewModels {
        viewModelFactory {
            initializer { TasksViewModel(app.data.tasks) }
        }
    }

    private val agendaViewModel: AgendaViewModel by viewModels {
        viewModelFactory {
            initializer { AgendaViewModel(app.data.events) }
        }
    }

    private val notesViewModel: NotesViewModel by viewModels {
        viewModelFactory {
            initializer { NotesViewModel(app.data.notes, app.services.prefs) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // a turn of the screen keeps the same intent, and it must not act twice
        if (savedInstanceState == null) route(intent)
        val settings = app.settings
        setContent {
            val dark = settings.theme.isDark()
            // the bars at the top and bottom follow the chosen theme, not only the phone's
            LaunchedEffect(dark) {
                val bars = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            CompositionLocalProvider(LocalServices provides app.services) {
                PlannerTheme(darkTheme = dark) {
                    PlannerScreen(tasksViewModel, agendaViewModel, notesViewModel) { onClose -> SettingsScreen(onClose) }
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
        val requests = app.requests
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
        app.syncController.onInSight()
        app.checkForUpdatesIfDue()
    }

    override fun onStop() {
        super.onStop()
        app.syncController.onOutOfSight()
        app.backUpIfDue()
    }

    companion object {
        const val NEW_TASK = "planner.NEW_TASK"
        const val NEW_EVENT = "planner.NEW_EVENT"
        const val NEW_NOTE = "planner.NEW_NOTE"
        const val OPEN_EVENT = "open_event"
    }
}
