package dev.fourthbreakfast.planner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.fourthbreakfast.planner.tasks.TasksScreen
import dev.fourthbreakfast.planner.tasks.TasksViewModel
import dev.fourthbreakfast.planner.ui.theme.PlannerTheme

class MainActivity : ComponentActivity() {
    private val tasksViewModel: TasksViewModel by viewModels {
        viewModelFactory {
            initializer { TasksViewModel((application as PlannerApp).tasks) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlannerTheme {
                TasksScreen(tasksViewModel)
            }
        }
    }
}
