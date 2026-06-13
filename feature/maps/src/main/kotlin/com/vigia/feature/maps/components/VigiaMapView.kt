package com.vigia.feature.maps.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.vigia.core.model.LatLng
import com.vigia.core.model.LocationSnapshot
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Wraps the OSMDroid [MapView] in a Compose [AndroidView].
 *
 * All domain overlays (hazards, route, geohash) are drawn via Compose Canvas siblings
 * layered on top of this view in [MapsScreen] — not via OSMDroid overlays — so that
 * animation logic stays in Kotlin/Compose and avoids bridging costs.
 */
@Composable
fun VigiaMapView(
    center: LatLng,
    zoom: Double,
    userLocation: LocationSnapshot?,
    modifier: Modifier = Modifier,
) {
    val mapView = remember { null as MapView? }   // created inside AndroidView factory

    AndroidView(
        factory = { context ->
            Configuration.getInstance().userAgentValue = context.packageName
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(zoom)
                controller.setCenter(GeoPoint(center.lat, center.lng))

                // Dark-ish filter via colour matrix — full custom tile style would require
                // a self-hosted tile server; this gives an acceptable near-dark result.
                overlayManager.tilesOverlay.setColorFilter(darkMapFilter())

                // Blue dot for user position
                val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                myLocationOverlay.enableMyLocation()
                overlays.add(myLocationOverlay)
            }
        },
        update = { view ->
            view.controller.animateTo(GeoPoint(center.lat, center.lng))
            view.controller.setZoom(zoom)
        },
        onRelease = { view -> view.onDetach() },
        modifier = modifier,
    )
}

/** Colour matrix that desaturates and darkens the standard OSM tiles to approximate a dark map style. */
private fun darkMapFilter(): android.graphics.ColorMatrixColorFilter {
    val matrix = android.graphics.ColorMatrix().apply {
        // Desaturate
        setSaturation(0.4f)
    }
    // Darken: multiply each channel by 0.55
    val darken = android.graphics.ColorMatrix(floatArrayOf(
        0.55f, 0f, 0f, 0f, 0f,
        0f, 0.55f, 0f, 0f, 0f,
        0f, 0f, 0.55f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))
    matrix.postConcat(darken)
    return android.graphics.ColorMatrixColorFilter(matrix)
}
