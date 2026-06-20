package com.vigia.core.sensor.ble

import com.vigia.core.model.RriScore
import com.vigia.core.model.SpatialLatentVector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes raw GATT telemetry frames from [BleLinkManager.incomingFrames] into typed
 * [BleDataStreamer.TelemetryFrame] values.
 *
 * Frame wire format (little-endian):
 *   [0]       version byte  (0x01)
 *   [1..4]    RRI score     (Float32)
 *   [5]       vector dims   (0x00 = 256-D, 0x01 = 512-D)
 *   [6..end]  vector data   (Float32 × dims)
 *
 * If the hardware team changes the transport to L2CAP CoC, only this class changes —
 * the [BleDataStreamer] interface and all consumers remain untouched.
 */
@Singleton
class BleDataStreamerImpl @Inject constructor(
    private val linkManager: BleLinkManager,
) : BleDataStreamer {

    override val telemetryFrames: Flow<BleDataStreamer.TelemetryFrame> =
        linkManager.incomingFrames.mapNotNull { bytes -> bytes.toTelemetryFrame() }

    // FCW payload: [0x10 | ttc_f32_le(4) | class_id_u8(1)] = 6 bytes
    override val fcwEvents: Flow<BleDataStreamer.FcwEvent> =
        linkManager.gattEventBus
            .receiveAsFlow()
            .filterIsInstance<BleLinkManager.GattEvent.CharacteristicChanged>()
            .mapNotNull { event ->
                if (event.charUuid != GattConstants.ALERT_CHAR_UUID) return@mapNotNull null
                event.value.toFcwEvent()
            }

    private fun ByteArray.toFcwEvent(): BleDataStreamer.FcwEvent? {
        if (size < FCW_PAYLOAD_BYTES) return null
        if ((this[0].toInt() and 0xFF) != FCW_ALERT_TYPE) return null
        val ttc = readFloat32(offset = 1)
        if (!ttc.isFinite() || ttc <= 0f) return null
        val classId = this[5].toInt() and 0xFF
        return BleDataStreamer.FcwEvent(ttcSeconds = ttc, classId = classId)
    }

    // ── Wire-format decoder ───────────────────────────────────────────────────

    private fun ByteArray.toTelemetryFrame(): BleDataStreamer.TelemetryFrame? {
        if (size < HEADER_BYTES) return null                  // malformed — discard silently
        if (this[0] != FRAME_VERSION) return null

        val rriRaw = readFloat32(offset = 1)
        if (rriRaw !in 0f..1f) return null                   // invalid RRI — discard

        val dimsCode = this[5].toInt() and 0xFF

        // 0xFF = RRI-only beacon (design spec §7.1 Dynamic Dimensionality Scaling degraded mode).
        // Emit a frame with an empty vector so consumers can detect degraded link state.
        if (dimsCode == 0xFF) {
            return BleDataStreamer.TelemetryFrame(
                rriScore = RriScore(rriRaw),
                spatialLatentVector = SpatialLatentVector(
                    dimensions = 0,
                    data = FloatArray(0),
                    originTimestampMs = System.currentTimeMillis(),
                ),
            )
        }

        val dims = when (dimsCode) {
            0x00 -> 256
            0x01 -> 512
            else -> return null   // unknown dims code — discard
        }
        val expectedBytes = HEADER_BYTES + dims * Float.SIZE_BYTES
        if (size < expectedBytes) return null

        val vectorData = FloatArray(dims) { i -> readFloat32(offset = HEADER_BYTES + i * Float.SIZE_BYTES) }

        return BleDataStreamer.TelemetryFrame(
            rriScore = RriScore(rriRaw),
            spatialLatentVector = SpatialLatentVector(
                dimensions = dims,
                data = vectorData,
                originTimestampMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Reads a little-endian IEEE 754 Float32 from the given byte [offset]. */
    private fun ByteArray.readFloat32(offset: Int): Float {
        val bits = (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
        return java.lang.Float.intBitsToFloat(bits)
    }

    companion object {
        private const val FRAME_VERSION: Byte = 0x01
        private const val HEADER_BYTES = 6       // version(1) + rri(4) + dims(1)
        private const val FCW_ALERT_TYPE = 0x10  // matches kFcwAlert in ble_gatt_constants.hpp
        private const val FCW_PAYLOAD_BYTES = 6  // type(1) + ttc_f32(4) + class_id(1)
    }
}
