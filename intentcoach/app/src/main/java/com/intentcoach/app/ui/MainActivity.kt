package com.intentcoach.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.intentcoach.app.data.Settings as CoachSettings // aliased: Icons.Outlined.Settings is also imported

/**
 * The app's main Activity: four tabs (Home, Insights, Focus, Settings) plus a few pushed
 * screens (onboarding, app picker, history, privacy).
 *
 * InterruptActivity is deliberately NOT in this graph. It is launched on top of whatever
 * app the user just opened — a separate surface with its own task, not somewhere you
 * navigate to from here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val onboarded = CoachSettings.isOnboardingDone(this)
        setContent { IntentCoachTheme { IntentCoachNav(startOnboarding = !onboarded) } }
    }
}

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val FOCUS = "focus"
    const val SETTINGS = "settings"
    const val APPS = "apps"
    const val HISTORY = "history"
    const val APP_HISTORY = "history/{pkg}"
    const val PRIVACY = "privacy"

    fun appHistory(pkg: String) = "history/" + Uri.encode(pkg)
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.INSIGHTS, "Insights", Icons.Outlined.Insights),
    Tab(Routes.FOCUS, "Focus", Icons.Outlined.Timer),
    Tab(Routes.SETTINGS, "Settings", Icons.Outlined.Settings)
)

/**
 * Switching tabs pops back to Home and saves/restores each tab's state, so the back
 * button behaves predictably (back from any tab -> Home -> exit) and tabs never stack up.
 */
private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun IntentCoachNav(startOnboarding: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Fixed for this graph's lifetime: changing NavHost's startDestination would rebuild it.
    val start = remember { if (startOnboarding) Routes.ONBOARDING else Routes.HOME }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (TABS.any { it.route == route }) {
                NavigationBar(containerColor = Umber) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Ink,
                                indicatorColor = Lime,
                                selectedTextColor = Cream,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding).background(Ink)
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onStartFocus = { nav.openTab(Routes.FOCUS) },
                    onOpenInsights = { nav.openTab(Routes.INSIGHTS) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) },
                    onManageApps = { nav.navigate(Routes.APPS) },
                    onOpenSettings = { nav.openTab(Routes.SETTINGS) }
                )
            }
            composable(Routes.INSIGHTS) {
                InsightsScreen(
                    onOpenApp = { pkg -> nav.navigate(Routes.appHistory(pkg)) },
                    onOpenHistory = { nav.navigate(Routes.HISTORY) }
                )
            }
            composable(Routes.FOCUS) { FocusScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onManageApps = { nav.navigate(Routes.APPS) },
                    onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                    onDataDeleted = {
                        // Wipe the back stack: after deleting everything the app is a fresh install.
                        nav.navigate(Routes.ONBOARDING) { popUpTo(nav.graph.id) { inclusive = true } }
                    }
                )
            }
            // popBackStack() rather than navigate(): "back" unwinds, it doesn't push a copy.
            composable(Routes.APPS) { AppPickerScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.HISTORY) { HistoryScreen(packageName = null, onBack = { nav.popBackStack() }) }
            composable(
                Routes.APP_HISTORY,
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { backStackEntry ->
                HistoryScreen(
                    packageName = backStackEntry.arguments?.getString("pkg"),
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
