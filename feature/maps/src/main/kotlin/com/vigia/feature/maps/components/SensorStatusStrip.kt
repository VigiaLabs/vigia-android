package com.vigia.feature.maps.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigia.feature.maps.SensorStripState
import kotlin.math.roundToInt

private val BleConnected    = Color(0xFF4ADE80)
private val BleDisconnected = Color(0xFF6B7280)
private val StripBackground = Color(0xCC0A0A0F)
private val StripBorder     = Color(0x33FFFFFF)

@Composable
fun SensorStatusStrip(
    state: SensorStripState,
    modifier: Modifier = Modifier,
) {
    val bleDotColor by animateColorAsState(
        targetValue   = if (state.bleConnected) BleConnected else BleDisconnected,
        animationSpec = tween(600),
        label         = "ble_dot_color",
    )

    val inf     = rememberInfiniteTransition(label = "ble_pulse")
    val dotAlpha by inf.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dot_alpha",
    )

    val rriColor = when {
        state.rriScore > 0.8f -> BleConnected
        state.rriScore > 0.5f -> Color(0xFFEAB308)
        else                  -> MaterialTheme.colorScheme.error
    }

    Row(
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(StripBackground)
            .border(width = 0.5.dp, color = StripBorder)
            .padding(horizontal = 16.dp),
    ) {
        // BLE status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(bleDotColor)
                .graphicsLayer { alpha = if (state.bleConnected) dotAlpha else 1f },
        )

        StripLabel("BLE · Pi5")
        StripDivider()

        StripLabel("RRI")
        StripValue(
            text  = "%.2f".format(state.rriScore),
            color = rriColor,
        )
        StripDivider()

        StripLabel(state.confidenceLabel)
        Spacer(Modifier.weight(1f))

        StripValue(
            text  = "${(state.velocityMs * 3.6f).roundToInt()} km/h",
            color = Color.White.copy(alpha = 0.6f),
        )
        StripDivider()

        StripLabel("±${state.accuracyMeters.roundToInt()}m")
    }
}

@Composable
private fun StripLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = Color.White.copy(alpha = 0.5f),
        fontSize = 10.sp,
    )
}

@Composable
private fun StripValue(text: String, color: Color) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color      = color,
        fontSize   = 10.sp,
    )
}

@Composable
private fun StripDivider() {
    Text(text = "│", color = Color.White.copy(alpha = 0.15f), fontSize = 10.sp)
}
