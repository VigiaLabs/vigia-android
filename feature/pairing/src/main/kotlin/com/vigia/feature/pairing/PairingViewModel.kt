package com.vigia.feature.pairing

import android.annotation.SuppressLint
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vigia.core.sensor.pairing.ClaimDeviceRepository
import com.vigia.core.sensor.pairing.ClaimResult
import com.vigia.core.sensor.pairing.PairedConfig
import com.vigia.core.sensor.pairing.PairingRepository
import com.vigia.core.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.regex.Pattern
import javax.inject.Inject

/**
 * Drives the QR → CDM.associate() → DataStore pairing flow (design spec §5).
 *
 * QR wire format: vigia://pair?mac=AA:BB:CC:DD:EE:FF&pk=<base64url P-256 pub>&id=vigia-001&v=1
 *
 * Flow:
 *   1. [QrAnalyzer] detects a vigia:// QR → calls [onQrDetected].
 *   2. ViewModel parses QR, validates pk (must be 65 bytes), calls CDM.associate().
 *   3. CDM callback fires [onDeviceFound] with an IntentSender → emits [PairingState.AwaitingCdmLaunch].
 *   4. PairingScreen launches the IntentSender. User approves → [onAssociationCreated] fires.
 *   5. ViewModel persists [PairedConfig] → emits [PairingState.Success].
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pairingRepository: PairingRepository,
    private val claimDeviceRepository: ClaimDeviceRepository,
    private val walletRepository: WalletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Scanning)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    // Held temporarily between QR detection and CDM result so we can persist after success.
    @Volatile private var pendingMac: String? = null
    @Volatile private var pendingPiPub: ByteArray? = null
    @Volatile private var pendingDeviceId: String? = null

    private val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }

    // ── QR detection ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun onQrDetected(rawQr: String) {
        val uri = runCatching { Uri.parse(rawQr) }.getOrNull() ?: return emitError("Invalid QR format")

        val mac      = uri.getQueryParameter("mac") ?: return emitError("QR missing mac")
        val pkB64    = uri.getQueryParameter("pk")  ?: return emitError("QR missing pk")
        val deviceId = uri.getQueryParameter("id")  ?: ""

        if (!MAC_REGEX.matcher(mac).matches()) return emitError("Invalid MAC in QR: $mac")

        val piPublicKeyBytes = try {
            Base64.decode(pkB64, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return emitError("QR pk field is not valid base64url")
        }

        if (piPublicKeyBytes.size != 65 || piPublicKeyBytes[0] != 0x04.toByte()) {
            return emitError("QR pk must be a 65-byte uncompressed P-256 point (0x04 prefix)")
        }

        pendingMac      = mac
        pendingPiPub    = piPublicKeyBytes
        pendingDeviceId = deviceId

        associateWithCdm(mac, piPublicKeyBytes, deviceId)
    }

    // ── CDM association ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun associateWithCdm(mac: String, piPublicKeyBytes: ByteArray, deviceId: String) {
        val deviceFilter = BluetoothLeDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("VIGIA.*"))
            .build()

        val request = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)   // auto-select if exactly one matching device is found
            .build()

        cdm.associate(request, Executors.newSingleThreadExecutor(), object : CompanionDeviceManager.Callback() {

            override fun onDeviceFound(chooserLauncher: IntentSender) {
                // Emit the intent sender — PairingScreen launches it via ActivityResultLauncher.
                _state.value = PairingState.AwaitingCdmLaunch(
                    intentSender = chooserLauncher,
                    mac = mac,
                    piPublicKeyBytes = piPublicKeyBytes,
                    deviceId = deviceId,
                )
            }

            override fun onAssociationCreated(associationInfo: android.companion.AssociationInfo) {
                // API 33+: association created without explicit user action (device already known).
                persistPairing(associationInfo.id)
            }

            override fun onFailure(error: CharSequence?) {
                _state.value = PairingState.Error(error?.toString() ?: "CDM association failed")
            }
        })
    }

    /** Called by PairingScreen after the ActivityResult returns a successful CDM association. */
    fun onCdmResultReceived(associationId: Int) {
        if (associationId <= 0) {
            _state.value = PairingState.PairingRejected
            return
        }
        persistPairing(associationId)
    }

    fun onCdmResultCancelled() {
        _state.value = PairingState.PairingRejected
    }

    fun retryScanning() {
        pendingMac = null; pendingPiPub = null; pendingDeviceId = null
        _state.value = PairingState.Scanning
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun persistPairing(associationId: Int) {
        val mac      = pendingMac      ?: return emitError("No pending MAC after CDM result")
        val piPub    = pendingPiPub    ?: return emitError("No pending Pi pub key after CDM result")
        val deviceId = pendingDeviceId ?: ""

        viewModelScope.launch {
            // Ensure wallet is provisioned so we have a stable public key to bind.
            walletRepository.ensureProvisioned()
            val walletPubkey = walletRepository.state.value.publicKey
            if (walletPubkey.isBlank()) {
                emitError("Wallet not provisioned — check network and retry")
                return@launch
            }

            // Server enforces 1:1 device-wallet binding before we persist locally.
            when (val result = claimDeviceRepository.claimDevice(deviceId, walletPubkey)) {
                ClaimResult.Ok -> { /* proceed */ }
                ClaimResult.DeviceTaken -> {
                    _state.value = PairingState.DeviceAlreadyClaimed(deviceId, "device_taken")
                    return@launch
                }
                ClaimResult.WalletTaken -> {
                    _state.value = PairingState.DeviceAlreadyClaimed(deviceId, "wallet_taken")
                    return@launch
                }
                is ClaimResult.NetworkError -> {
                    // Network failure — allow pairing to proceed locally so the device
                    // works offline; the claim will be re-attempted on next app launch.
                    android.util.Log.w("PairingViewModel",
                        "claim-device network error: ${result.message} — proceeding locally")
                }
            }

            pairingRepository.savePairedConfig(
                PairedConfig(mac = mac, piPublicKeyBytes = piPub, associationId = associationId, deviceId = deviceId)
            )
            _state.value = PairingState.Success(deviceId)
        }
    }

    private fun emitError(msg: String) { _state.value = PairingState.Error(msg) }

    companion object {
        private val MAC_REGEX = Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}
