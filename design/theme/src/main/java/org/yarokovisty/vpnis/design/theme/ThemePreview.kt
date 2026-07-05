package org.yarokovisty.vpnis.design.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "VPNis Theme Preview")
            Button(onClick = {}) {
                Text(text = "Connect")
            }
        }
    }
}
