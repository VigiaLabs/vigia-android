package com.vigia.feature.maps.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vigia.core.model.MapLayer

private val BtnBackground = Color(0xCC0A0A0F)
private val BtnBorder     = Color(0x1AFFFFFF)
private val ActiveBorder  = Color(0x4059A9F5)
private val ActiveBg      = Color(0x263B82F6)

private data class LayerButton(
    val layer: MapLayer,
    val icon: ImageVector,
    val label: String,
)

private val buttons = listOf(
    LayerButton(MapLayer.HAZARDS,     Icons.Filled.Warning,    "Hazards"),
    LayerButton(MapLayer.GEOHASH,     Icons.Filled.Layers,     "Geohash"),
    LayerButton(MapLayer.MAINTENANCE, Icons.Filled.Build,      "Maintenance"),
    LayerButton(MapLayer.ECONOMIC,    Icons.AutoMirrored.Filled.TrendingUp, "Economic"),
)

@Composable
fun LayerToggleColumn(
    activeLayers: Set<MapLayer>,
    onToggle: (MapLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        buttons.forEach { btn ->
            val isActive = btn.layer in activeLayers
            val bgColor  by animateColorAsState(if (isActive) ActiveBg else BtnBackground, tween(200), label = "layer_bg")
            val bdColor  by animateColorAsState(if (isActive) ActiveBorder else BtnBorder, tween(200), label = "layer_bd")
            val iconTint by animateColorAsState(
                if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                tween(200), label = "layer_tint",
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor)
                    .border(0.5.dp, bdColor, RoundedCornerShape(10.dp))
                    .clickable { onToggle(btn.layer) },
            ) {
                Icon(btn.icon, contentDescription = btn.label, tint = iconTint, modifier = Modifier.size(18.dp))
            }

            if (btn != buttons.last()) {
                Box(modifier = Modifier.size(width = 1.dp, height = 6.dp).background(BtnBorder))
            }
        }
    }
}
