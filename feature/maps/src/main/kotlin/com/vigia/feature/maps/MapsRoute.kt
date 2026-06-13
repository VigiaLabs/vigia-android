package com.vigia.feature.maps

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val MAPS_ROUTE = "maps"

fun NavGraphBuilder.mapsScreen() {
    composable(MAPS_ROUTE) {
        MapsScreen()
    }
}
