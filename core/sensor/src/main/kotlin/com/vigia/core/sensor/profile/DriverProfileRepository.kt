package com.vigia.core.sensor.profile

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vigia.core.model.DriverProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDs by preferencesDataStore("driver_profile")

@Singleton
class DriverProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringPreferencesKey("profile")

    val profile: Flow<DriverProfile> = context.profileDs.data.map { prefs ->
        prefs[key]?.let { runCatching { DriverProfile.valueOf(it) }.getOrNull() }
            ?: DriverProfile.NEW  // safe default for first-time / unknown users
    }

    suspend fun setProfile(profile: DriverProfile) {
        context.profileDs.edit { it[key] = profile.name }
    }
}
