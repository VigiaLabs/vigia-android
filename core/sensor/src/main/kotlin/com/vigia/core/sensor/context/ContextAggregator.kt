package com.vigia.core.sensor.context

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import com.vigia.core.model.LocationSnapshot
import com.vigia.core.model.RriScore
import com.vigia.core.model.SpatialLatentVector
import com.vigia.core.model.VigiaSearchContext
import com.vigia.core.sensor.ble.BleDataStreamer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fuses real-time location (GPS) with BLE telemetry (RRI + SpatialLatentVector) into a
 * live [VigiaSearchContext] stream ready for the VIGIASearch copilot query.
 *
 * [searchContext] uses [combine] so it emits only when BOTH sources have provided at least one
 * value. [onStart] pre-seeds each source with a safe default so combine fires immediately on
 * first real update from either side — preventing a deadlock when BLE is disconnected.
 *
 * The caller (CopilotViewModel) copies the emitted context with `copy(queryText = userInput)`
 * before passing it to [VigiaSearchClient.search].
 *
 * ACCESS_FINE_LOCATION is declared in :core:sensor's AndroidManifest and granted at runtime
 * by the feature layer before observing [searchContext].
 */
@Singleton
class ContextAggregator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bleDataStreamer: BleDataStreamer,
) {
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val defaultLocation = LocationSnapshot(
        latitudeDeg    = 0.0,
        longitudeDeg   = 0.0,
        accuracyMeters = Float.MAX_VALUE,
        bearingDeg     = 0f,
        velocityMs     = 0f,
        timestampMs    = System.currentTimeMillis(),
    )

    private val defaultFrame = BleDataStreamer.TelemetryFrame(
        rriScore            = RriScore(0f),
        spatialLatentVector = SpatialLatentVector(
            dimensions        = 256,
            data              = FloatArray(256),
            originTimestampMs = 0L,
        ),
    )

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // minSdk=34 — LocationRequest.Builder and FUSED_PROVIDER are both API 31+; no guard needed.
    @SuppressLint("MissingPermission")
    private fun locationFlow(): Flow<LocationSnapshot> = callbackFlow {
        if (!hasLocationPermission()) {
            // Permission not yet granted — emit nothing; combine stays on the default seed.
            awaitClose { }
            return@callbackFlow
        }

        val request = LocationRequest.Builder(LOCATION_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DISTANCE_METERS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()

        val listener = LocationListener { loc: Location ->
            trySend(loc.toSnapshot())
        }

        locationManager.requestLocationUpdates(
            LocationManager.FUSED_PROVIDER,
            request,
            context.mainExecutor,
            listener,
        )

        awaitClose { locationManager.removeUpdates(listener) }
    }

    val searchContext: Flow<VigiaSearchContext> = combine(
        locationFlow().onStart { emit(defaultLocation) },
        bleDataStreamer.telemetryFrames.onStart { emit(defaultFrame) },
    ) { location, frame ->
        VigiaSearchContext(
            queryText           = "",
            timestampMs         = System.currentTimeMillis(),
            location            = location,
            velocityMs          = location.velocityMs,
            rriScore            = frame.rriScore,
            spatialLatentVector = frame.spatialLatentVector,
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun Location.toSnapshot() = LocationSnapshot(
        latitudeDeg    = latitude,
        longitudeDeg   = longitude,
        accuracyMeters = accuracy,
        bearingDeg     = bearing,
        velocityMs     = speed,
        timestampMs    = time,
    )

    companion object {
        private const val LOCATION_INTERVAL_MS   = 1_000L
        private const val MIN_DISTANCE_METERS     = 1f
    }
}
