package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBanner
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBannerAction
import org.yarokovisty.vpnis.design.uikit.banner.VPNisBannerVariant

/**
 * Showcase section for the banner family (`:design:uikit` `banner/`).
 *
 * Demonstrates all three variants ([VPNisBannerVariant.Info], [VPNisBannerVariant.Warning],
 * [VPNisBannerVariant.Error]) with icons from `material-icons-core`, dismissible state,
 * and bounded primary/secondary actions. The Info and Warning banners can be dismissed
 * on-device to verify the dismiss callback and that the composition updates correctly.
 */
@Composable
fun BannerShowcaseSection() {
    var showInfoBanner by remember { mutableStateOf(true) }
    var showWarningBanner by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Info", style = MaterialTheme.typography.labelMedium)
        if (showInfoBanner) {
            VPNisBanner(
                text = "Your connection is active. All traffic is encrypted end-to-end.",
                variant = VPNisBannerVariant.Info,
                icon = Icons.Filled.Info,
                primaryAction = VPNisBannerAction(label = "Details") {},
                onDismiss = { showInfoBanner = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Warning", style = MaterialTheme.typography.labelMedium)
        if (showWarningBanner) {
            VPNisBanner(
                text = "Your free trial expires in 3 days. Upgrade to keep unlimited access.",
                variant = VPNisBannerVariant.Warning,
                icon = Icons.Filled.Warning,
                primaryAction = VPNisBannerAction(label = "Upgrade") {},
                secondaryAction = VPNisBannerAction(label = "Later") {},
                onDismiss = { showWarningBanner = false },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text("Error", style = MaterialTheme.typography.labelMedium)
        VPNisBanner(
            text = "Unable to reach VPN server. Check your network and try again.",
            variant = VPNisBannerVariant.Error,
            icon = Icons.Filled.Close,
            primaryAction = VPNisBannerAction(label = "Retry") {},
            secondaryAction = VPNisBannerAction(label = "Dismiss") {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
