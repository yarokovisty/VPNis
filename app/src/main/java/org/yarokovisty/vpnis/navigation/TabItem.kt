package org.yarokovisty.vpnis.navigation

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

/**
 * Descriptor for a single bottom-navigation tab.
 *
 * @property labelRes String resource id for the tab label.
 * @property icon [ImageVector] rendered when the tab is not selected.
 * @property destinationClass The [KClass] of the type-safe route object,
 *   used with [androidx.navigation.NavBackStackEntry.hasRoute] to determine
 *   which tab is currently active.
 * @property route The route object instance passed to
 *   [androidx.navigation.NavController.navigate].
 */
internal data class TabItem<T : Any>(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val destinationClass: KClass<T>,
    val route: T,
)
