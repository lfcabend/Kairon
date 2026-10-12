package com.kairon.android.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairon.android.about.AboutScreen
import com.kairon.android.account.AccountScreen
import com.kairon.android.assistant.SummariesScreen
import com.kairon.android.auth.LoginScreen
import com.kairon.android.auth.RegisterScreen
import com.kairon.android.journal.JournalDayScreen
import com.kairon.android.journal.JournalEntryScreen
import com.kairon.android.journal.JournalSearchScreen
import com.kairon.android.projects.ProjectDetailScreen
import com.kairon.android.projects.ProjectFormScreen
import com.kairon.android.projects.ProjectListScreen
import com.kairon.android.projects.TaskEditScreen
import com.kairon.android.today.TodayScreen
import com.kairon.android.todo.DayViewScreen
import java.time.LocalDate

private sealed class Route(val path: String) {
    data object Login : Route("login")
    data object Register : Route("register")
    data object Today : Route("today")
    data object DayView : Route("day")
    data object Account : Route("account")
    data object About : Route("about")
    data object Summaries : Route("summaries")
    data object Journal : Route("journal")
    data object JournalEntry : Route("journal/entry/{day}/{entryId}")
    data object JournalSearch : Route("journal/search")
    data object Projects : Route("projects")
    data object ProjectForm : Route("projects/form/{projectId}")
    data object ProjectDetail : Route("projects/{projectId}")
    data object TaskEdit : Route("projects/{projectId}/tasks/{taskId}")
}

// Bottom-nav items, four primary destinations (D3 — Account/About moved to
// KaironBanner's OverflowMenu to keep this list within Material3's 3-5 guidance).
private data class BottomNavItem(val route: Route, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Route.Today, "Today", Icons.Default.DateRange),
    BottomNavItem(Route.DayView, "Todo", Icons.Default.CheckCircle),
    BottomNavItem(Route.Journal, "Journal", Icons.Default.Edit),
    BottomNavItem(Route.Projects, "Projects", Icons.Default.List),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaironApp(sessionViewModel: SessionViewModel = hiltViewModel()) {
    val signedIn by sessionViewModel.signedIn.collectAsState()
    val navController = rememberNavController()

    if (!signedIn) {
        NavHost(navController = navController, startDestination = Route.Login.path) {
            composable(Route.Login.path) {
                LoginScreen(
                    onLoggedIn = { /* signedIn flips via TokenStore; this NavHost is swapped out above */ },
                    onNavigateToRegister = { navController.navigate(Route.Register.path) },
                )
            }
            composable(Route.Register.path) {
                RegisterScreen(
                    onRegistered = { /* signedIn flips via TokenStore */ },
                    onNavigateToLogin = { navController.popBackStack() },
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            KaironBanner(
                onAccount = { navController.navigate(Route.Account.path) },
                onAbout = { navController.navigate(Route.About.path) },
                onSummaries = { navController.navigate(Route.Summaries.path) },
            )
        },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                bottomNavItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route.path } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(item.route.path) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Today.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Today.path) { TodayScreen() }
            composable(Route.DayView.path) { DayViewScreen() }
            composable(Route.Account.path) { AccountScreen(onLoggedOut = {}) }
            composable(Route.About.path) { AboutScreen() }
            composable(Route.Summaries.path) { SummariesScreen(onBack = { navController.popBackStack() }) }

            composable(Route.Journal.path) { backStackEntry ->
                val targetDay = backStackEntry.savedStateHandle.get<String>("targetDay")?.let(LocalDate::parse)
                JournalDayScreen(
                    targetDay = targetDay,
                    onNewEntry = { day -> navController.navigate("journal/entry/$day/new") },
                    onEditEntry = { entryId, day -> navController.navigate("journal/entry/$day/$entryId") },
                    onSearch = { navController.navigate(Route.JournalSearch.path) },
                )
            }
            composable(
                Route.JournalEntry.path,
                arguments = listOf(navArgument("day") { type = NavType.StringType }, navArgument("entryId") { type = NavType.StringType }),
            ) {
                JournalEntryScreen(onBack = { navController.popBackStack() })
            }
            composable(Route.JournalSearch.path) {
                JournalSearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDay = { day ->
                        navController.getBackStackEntry(Route.Journal.path).savedStateHandle["targetDay"] = day.toString()
                        navController.popBackStack(Route.Journal.path, inclusive = false)
                    },
                )
            }

            composable(Route.Projects.path) {
                ProjectListScreen(
                    onOpenProject = { id -> navController.navigate("projects/$id") },
                    onNewProject = { navController.navigate("projects/form/new") },
                )
            }
            composable(
                Route.ProjectForm.path,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                ProjectFormScreen(
                    onBack = { navController.popBackStack() },
                    onSaved = { id ->
                        navController.popBackStack()
                        navController.navigate("projects/$id")
                    },
                )
            }
            composable(
                Route.ProjectDetail.path,
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) {
                ProjectDetailScreen(
                    onBack = { navController.popBackStack() },
                    onEditProject = { id -> navController.navigate("projects/form/$id") },
                    onEditTask = { projectId, taskId -> navController.navigate("projects/$projectId/tasks/$taskId") },
                    onNewTask = { projectId, parentTaskId ->
                        val suffix = parentTaskId?.let { "?parentTaskId=$it" } ?: ""
                        navController.navigate("projects/$projectId/tasks/new$suffix")
                    },
                )
            }
            composable(
                "${Route.TaskEdit.path}?parentTaskId={parentTaskId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("taskId") { type = NavType.StringType },
                    navArgument("parentTaskId") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) {
                TaskEditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
