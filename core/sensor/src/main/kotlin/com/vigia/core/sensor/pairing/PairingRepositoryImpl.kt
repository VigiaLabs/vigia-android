package com.vigia.core.sensor.pairing

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore("vigia_pairing")

@Singleton
class PairingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PairingRepository {

    private val store = context.pairingDataStore

    override val pairedConfig: Flow<PairedConfig?> = store.data.map { prefs ->
        val mac   = prefs[KEY_MAC]    ?: return@map null
        val pkB64 = prefs[KEY_PUB_KEY] ?: return@map null
        val assocId = prefs[KEY_ASSOCIATION_ID] ?: return@map null
        val deviceId = prefs[KEY_DEVICE_ID] ?: ""

        val piPublicKeyBytes = try {
            Base64.decode(pkB64, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return@map null
        }

        if (piPublicKeyBytes.size != 65) return@map null

        PairedConfig(
            mac = mac,
            piPublicKeyBytes = piPublicKeyBytes,
            associationId = assocId,
            deviceId = deviceId,
        )
    }

    override suspend fun savePairedConfig(config: PairedConfig) {
        store.edit { prefs ->
            prefs[KEY_MAC]            = config.mac
            prefs[KEY_PUB_KEY]        = Base64.encodeToString(config.piPublicKeyBytes, Base64.NO_WRAP)
            prefs[KEY_ASSOCIATION_ID] = config.associationId
            prefs[KEY_DEVICE_ID]      = config.deviceId
        }
    }

    override suspend fun clearPairedConfig() {
        store.edit { it.clear() }
    }

    companion object {
        private val KEY_MAC            = stringPreferencesKey("paired_mac")
        private val KEY_PUB_KEY        = stringPreferencesKey("paired_pi_pub_key_b64")
        private val KEY_ASSOCIATION_ID = intPreferencesKey("paired_association_id")
        private val KEY_DEVICE_ID      = stringPreferencesKey("paired_device_id")
    }
}
