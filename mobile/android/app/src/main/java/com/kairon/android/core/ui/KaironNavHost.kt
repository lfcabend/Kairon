package com.kairon.android.core.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kairon.android.about.AboutScreen
import com.kairon.android.account.AccountScreen
import com.kairon.android.auth.LoginScreen
import com.kairon.android.auth.RegisterScreen
import com.kairon.android.today.TodayScreen
import com.kairon.android.todo.DayViewScreen

private sealed class Route(val path: String) {
    data object Login : Route("login")
    data object Register : Route("register")
    data object Today : Route("today")
    data object DayView : Route("day")
    data object Account : Route("account")
    data object About : Route("about")
}

// Bottom-nav items, in the same relative order as the web app's top-nav (M11 §6.6):
// Today first and default, then the day-level Todo view, then Account/About.
private data class BottomNavItem(val route: Route, val label: String, val icon: ImageVector)

private val bottomNavItems = listOf(
    BottomNavItem(Route.Today, "Today", Icons.Default.DateRange),
    BottomNavItem(Route.DayView, "Todo", Icons.Default.CheckCircle),
    BottomNavItem(Route.Account, "Account", Icons.Default.AccountCircle),
    BottomNavItem(Route.About, "About", Icons.Default.Info),
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
        topBar = { KaironBanner() },
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
        }
    }
}
