package org.yarokovisty.vpnis.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the VPNis top-level tab graph.
 *
 * Each destination is a [Serializable] data object, enabling the Navigation 2.8+
 * type-safe API (`composable<Destination> { }`, `navigate(Destination)`,
 * `NavBackStackEntry.hasRoute<Destination>()`).
 *
 * Naming uses the *Destination* suffix to avoid name collision with the
 * `HomeRoute`, `ServersRoute` composable entry-points defined in each feature module.
 */
@Serializable
data object HomeDestination

@Serializable
data object ServersDestination

@Serializable
data object BypassDestination

@Serializable
data object SettingsDestination
