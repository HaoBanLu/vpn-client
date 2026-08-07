package com.vpn.member.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vpn.member.data.api.RegionItem
import com.vpn.member.ui.displayLabel

/**
 * 地区筛选：横向滚动，避免地区增多后挤在一行导致文字截断或溢出。
 */
@Composable
fun RegionFilterRow(
    regions: List<RegionItem>,
    selectedRegion: String?,
    onRegionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    segmentedShell: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val rowModifier =
        modifier
            .fillMaxWidth()
            .then(
                if (segmentedShell) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(4.dp)
                } else {
                    Modifier
                },
            )
            .horizontalScroll(scrollState)

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(if (segmentedShell) 4.dp else 8.dp),
    ) {
        RegionFilterOption(
            label = "全部",
            selected = selectedRegion == null,
            segmented = segmentedShell,
            onClick = { onRegionSelected(null) },
        )
        regions.forEach { region ->
            RegionFilterOption(
                label = region.displayLabel(),
                selected = selectedRegion.equals(region.code, ignoreCase = true),
                segmented = segmentedShell,
                onClick = { onRegionSelected(region.code) },
            )
        }
    }
}

@Composable
private fun RegionFilterOption(
    label: String,
    selected: Boolean,
    segmented: Boolean,
    onClick: () -> Unit,
) {
    if (segmented) {
        Box(
            modifier =
                Modifier
                    .widthIn(min = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.01f)
                        },
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors =
                FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
        )
    }
}
