package org.yarokovisty.vpnis.design.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
private fun VPNisThemeLightPreview() {
    VPNisTheme(darkTheme = false) {
        ThemePreviewContent()
    }
}

@Preview(showBackground = true)
@Composable
private fun VPNisThemeDarkPreview() {
    VPNisTheme(darkTheme = true) {
        ThemePreviewContent()
    }
}

@Composable
private fun ThemePreviewContent() {
    Surface {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "VPNis Design System",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Secure. Private. Fast.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = {}) {
                Text(text = "Connect")
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    text = "surfaceContainer · medium shape · labelMedium",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
