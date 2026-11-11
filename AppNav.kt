package ru.minutes.assistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.minutes.assistant.audio.RecorderState

@Composable
fun AppNav() {
    val nav = rememberNavController()

    // Приложение открыто из уведомления во время записи — сразу показываем экран записи.
    LaunchedEffect(Unit) {
        if (RecorderState.state.value.active) nav.navigate("record")
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpen = { nav.navigate("meeting/$it") },
                onRecord = { nav.navigate("record") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("record") {
            RecordScreen(
                onBack = { nav.popBackStack() },
                onFinished = { id ->
                    nav.popBackStack()
                    if (id > 0) nav.navigate("meeting/$id")
                },
            )
        }
        composable(
            "meeting/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            MeetingScreen(
                id = entry.arguments!!.getLong("id"),
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
