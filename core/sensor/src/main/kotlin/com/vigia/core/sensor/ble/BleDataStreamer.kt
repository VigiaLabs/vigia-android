package com.vigia.core.sensor.ble

import com.vigia.core.model.RriScore
import com.vigia.core.model.SpatialLatentVector
import kotlinx.coroutines.flow.Flow

/**
 * Abstracts the physical transport for Blackbox telemetry frames.
 *
 * Current implementation: raw GATT notifications on a single characteristic.
 * If the hardware team switches to an L2CAP Connection-Oriented Channel (CoC)
 * for higher throughput (required for 512-D vectors at high update rates),
 * only the implementation class inside :core:sensor changes.
 * Nothing in :core:network, :core:model, or :feature:copilot is touched.
 */
interface BleDataStreamer {
    /** Emits decoded telemetry frames whenever the Blackbox pushes data. */
    val telemetryFrames: Flow<TelemetryFrame>

    /** Emits FCW (Forward Collision Warning) events from ALERT_CHAR notifications (M11). */
    val fcwEvents: Flow<FcwEvent>

    data class TelemetryFrame(
        val rriScore: RriScore,
        val spatialLatentVector: SpatialLatentVector,
        val receivedAtMs: Long = System.currentTimeMillis(),
    )

    /**
     * Forward Collision Warning decoded from ALERT_CHAR.
     * Wire format: [0x10 | ttc_f32_le(4) | class_id_u8(1)] = 6 bytes.
     */
    data class FcwEvent(
        val ttcSeconds: Float,
        val classId: Int,
        val receivedAtMs: Long = System.currentTimeMillis(),
    ) {
        val targetLabel: String get() = when (classId) {
            1    -> "vehicle"
            2    -> "pedestrian"
            3    -> "cyclist"
            else -> "obstacle"
        }
    }
}
