package org.yarokovisty.vpnis.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.yarokovisty.vpnis.design.theme.VPNisTheme
import org.yarokovisty.vpnis.feature.home.components.icons.HomeIcons

/**
 * Row of two equal tiles showing download and upload traffic counters.
 *
 * Each tile uses [MaterialTheme.colorScheme.surfaceContainerLow] background, rounded 18 dp corners,
 * and 14 dp internal padding. Values use tabular-nums feature settings to keep digits stable-width
 * and avoid layout shifts as numbers update.
 *
 * String localisation note: [downloadLabel] and [uploadLabel] are caller-supplied.
 * // TODO(#54): replace preview literals with string resources.
 *
 * @param downloadLabel Label for the download tile, e.g. "Загрузка".
 * @param uploadLabel Label for the upload tile, e.g. "Отдача".
 * @param downloadValue Formatted download value, e.g. "1.2 MB/s". Default "—".
 * @param uploadValue Formatted upload value, e.g. "256 KB/s". Default "—".
 * @param modifier Optional [Modifier].
 */
@Composable
public fun TrafficStats(
    downloadLabel: String,
    uploadLabel: String,
    modifier: Modifier = Modifier,
    downloadValue: String = "—",
    uploadValue: String = "—",
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        TrafficTile(
            label = downloadLabel,
            value = downloadValue,
            isDownload = true,
            modifier = Modifier.weight(1f),
        )
        TrafficTile(
            label = uploadLabel,
            value = uploadValue,
            isDownload = false,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrafficTile(label: String, value: String, isDownload: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .semantics(mergeDescendants = true) { },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isDownload) HomeIcons.ArrowDownward else HomeIcons.ArrowUpward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFeatureSettings = "tnum",
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun TrafficStatsEmptyLightPreview() {
    VPNisTheme(darkTheme = false) {
        TrafficStats(
            downloadLabel = "Загрузка",
            uploadLabel = "Отдача",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrafficStatsActiveLightPreview() {
    VPNisTheme(darkTheme = false) {
        TrafficStats(
            downloadLabel = "Загрузка",
            uploadLabel = "Отдача",
            downloadValue = "1.2 MB/s",
            uploadValue = "256 KB/s",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrafficStatsEmptyDarkPreview() {
    VPNisTheme(darkTheme = true) {
        TrafficStats(
            downloadLabel = "Загрузка",
            uploadLabel = "Отдача",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TrafficStatsActiveDarkPreview() {
    VPNisTheme(darkTheme = true) {
        TrafficStats(
            downloadLabel = "Загрузка",
            uploadLabel = "Отдача",
            downloadValue = "1.2 MB/s",
            uploadValue = "256 KB/s",
        )
    }
}
