package org.yarokovisty.vpnis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.showcase.BannerShowcaseSection
import org.yarokovisty.vpnis.showcase.ButtonShowcaseSection
import org.yarokovisty.vpnis.showcase.CardShowcaseSection
import org.yarokovisty.vpnis.showcase.SelectionShowcaseSection

/**
 * Debug entry point: the VPNis design-system showcase gallery.
 *
 * A scrollable catalog of every `:design:uikit` component in its states, used
 * for visual acceptance (`manual-tester`) against the "VPNis Material 3"
 * reference. Present in `debug` builds only — see [MainActivity].
 *
 * The top bar carries a light/dark toggle so both colour schemes can be checked
 * on-device without changing the system setting. Each design-system family adds
 * one [ShowcaseSection] here (see the family issues under epic #24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var darkTheme by remember { mutableStateOf(false) }
    VPNisTheme(darkTheme = darkTheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("VPNis UI Kit — debug showcase") },
                    actions = {
                        Text("Dark", style = MaterialTheme.typography.labelLarge)
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = { darkTheme = it },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Each design-system family registers its section here.
                item { ShowcaseSection(title = "Buttons") { ButtonShowcaseSection() } }
                item { ShowcaseSection(title = "Selection controls") { SelectionShowcaseSection() } }
                item { ShowcaseSection(title = "Cards") { CardShowcaseSection() } }
                item { ShowcaseSection(title = "Banners") { BannerShowcaseSection() } }
            }
        }
    }
}

/**
 * A titled block in the showcase gallery. Families wrap their demo composables
 * in this for a consistent header + separator.
 */
@Composable
fun ShowcaseSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
        content()
    }
}
