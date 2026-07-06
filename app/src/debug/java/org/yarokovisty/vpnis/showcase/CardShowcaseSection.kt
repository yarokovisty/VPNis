package org.yarokovisty.vpnis.showcase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.uikit.card.VPNisCard
import org.yarokovisty.vpnis.design.uikit.card.VPNisElevatedCard
import org.yarokovisty.vpnis.design.uikit.card.VPNisOutlinedCard

/**
 * Showcase section for the card family (`:design:uikit` `card/`).
 *
 * Renders all three card variants ([VPNisCard], [VPNisElevatedCard],
 * [VPNisOutlinedCard]) in their non-clickable and clickable forms, each
 * with a representative title and body so token fidelity is visible on-device.
 */
@Composable
fun CardShowcaseSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // VPNisCard — filled
        Text("Card (filled)", style = MaterialTheme.typography.labelMedium)
        VPNisCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Amsterdam, Netherlands — non-clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
        VPNisCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Server Location",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Frankfurt, Germany — clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }

        // VPNisElevatedCard
        Text("ElevatedCard", style = MaterialTheme.typography.labelMedium)
        VPNisElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Premium — unlimited bandwidth — non-clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
        VPNisElevatedCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Connection Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Free — 1 GB/month — clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }

        // VPNisOutlinedCard
        Text("OutlinedCard", style = MaterialTheme.typography.labelMedium)
        VPNisOutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "Xray — VLESS + XTLS-Reality — non-clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
        VPNisOutlinedCard(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Protocol",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                text = "WireGuard — UDP — clickable",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}
