package org.yarokovisty.vpnis

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationBar
import org.yarokovisty.vpnis.design.uikit.navigation.VPNisNavigationBarItem
import org.yarokovisty.vpnis.feature.home.HomeRoute
import org.yarokovisty.vpnis.navigation.BypassDestination
import org.yarokovisty.vpnis.navigation.HomeDestination
import org.yarokovisty.vpnis.navigation.NavIcons
import org.yarokovisty.vpnis.navigation.PlaceholderScreen
import org.yarokovisty.vpnis.navigation.ServersDestination
import org.yarokovisty.vpnis.navigation.SettingsDestination
import org.yarokovisty.vpnis.navigation.TabItem

/**
 * Root composable for the VPNis product UI.
 *
 * Owns the [rememberNavController], the [Scaffold] with [VpnisBottomBar], and the
 * [VpnisNavHost] that maps type-safe route destinations to their screen composables.
 *
 * **Inset ownership:** the [Scaffold] sets `contentWindowInsets = WindowInsets(0)` so
 * the reported [innerPadding] carries ONLY the bottom nav-bar height (consumed by
 * [VPNisNavigationBar] itself for the gesture zone). Top / horizontal system insets are
 * owned by each screen composable individually — e.g. [HomeScreen] applies
 * `windowInsetsPadding(safeDrawing.only(Horizontal + Top))` directly, ensuring the
 * inset is consumed exactly once.
 *
 * **Tab state preservation** uses `popUpTo(startDestination) { saveState = true }` +
 * `launchSingleTop = true` + `restoreState = true` so switching tabs does not
 * duplicate back-stack entries and each tab's scroll/VM state is preserved.
 */
@Composable
fun VpnisApp() {
    VPNisTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val tabs = rememberTabs()

        Scaffold(
            bottomBar = { VpnisBottomBar(navController, currentDestination, tabs) },
            contentWindowInsets = WindowInsets(0),
        ) { innerPadding ->
            VpnisNavHost(navController, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun VpnisBottomBar(navController: NavController, currentDestination: NavDestination?, tabs: List<TabItem<*>>) {
    VPNisNavigationBar {
        tabs.forEach { tab ->
            val selected = currentDestination?.hasRoute(tab.destinationClass) == true
            VPNisNavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) },
            )
        }
    }
}

@Composable
private fun VpnisNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HomeDestination,
        modifier = modifier,
    ) {
        composable<HomeDestination> {
            HomeRoute(
                onNavigateToServers = {
                    navController.navigate(ServersDestination) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable<ServersDestination> {
            PlaceholderScreen(title = stringResource(R.string.nav_servers))
        }
        composable<BypassDestination> {
            PlaceholderScreen(title = stringResource(R.string.nav_bypass))
        }
        composable<SettingsDestination> {
            PlaceholderScreen(title = stringResource(R.string.nav_settings))
        }
    }
}

/**
 * Builds the stable ordered list of [TabItem]s that [VpnisBottomBar] iterates over.
 *
 * Returns a plain `listOf` — no `remember` wrapper needed because the list is
 * structurally identical on every recomposition (constant references) and the
 * Compose compiler will skip recomposing [VpnisBottomBar] when its inputs are equal.
 */
@Composable
private fun rememberTabs(): List<TabItem<*>> = listOf(
    TabItem(
        labelRes = R.string.nav_home,
        icon = Icons.Default.Home,
        destinationClass = HomeDestination::class,
        route = HomeDestination,
    ),
    TabItem(
        labelRes = R.string.nav_servers,
        icon = NavIcons.Dns,
        destinationClass = ServersDestination::class,
        route = ServersDestination,
    ),
    TabItem(
        labelRes = R.string.nav_bypass,
        icon = NavIcons.AltRoute,
        destinationClass = BypassDestination::class,
        route = BypassDestination,
    ),
    TabItem(
        labelRes = R.string.nav_settings,
        icon = NavIcons.Tune,
        destinationClass = SettingsDestination::class,
        route = SettingsDestination,
    ),
)
