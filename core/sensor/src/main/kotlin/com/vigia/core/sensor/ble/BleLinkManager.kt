package com.vigia.core.sensor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.vigia.core.model.BleLinkError
import com.vigia.core.model.BleLinkState
import com.vigia.core.sensor.keystore.KeystoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Single source of truth for the BLE link lifecycle.
 *
 * Connection pipeline (design spec §3, §4, §7):
 *   Idle → Scanning → Connecting → MTU negotiation → LE SC Pairing → Handshaking → Bound
 *
 * Auth: ECDH P-256 mutual authentication (§4.2a). Replaces the HMAC approach which was
 * cryptographically impossible — Android StrongBox PURPOSE_SIGN keys are non-exportable
 * and therefore can never be shared with the Pi for symmetric verification.
 *
 * CHALLENGE: [0x02 | nonce_pi(32) | Pi_pub_P256(65) | ECDSA_sig_over(nonce_pi||Pi_pub)]
 * RESPONSE:  [0x03 | nonce_phone(32) | Phone_pub_P256(65) | ECDSA_sig_over(nonce_phone||nonce_pi||Phone_pub)]
 * CONFIRM:   [0x04 | HMAC(session_key, "VIGIA-CONFIRM"||nonce_pi||nonce_phone)]
 *
 * Session key: HKDF-SHA256(ECDH(Pi_priv, Phone_pub), salt=nonce_pi||nonce_phone, info="vigia-ble-v1")
 *
 * After Bound: writes REQUEST_256D to CONTROL_CHAR to confirm default stream mode.
 */
@Singleton
@SuppressLint("MissingPermission")
class BleLinkManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) {

    // ── Public state ─────────────────────────────────────────────────────────

    private val _linkState = MutableStateFlow<BleLinkState>(BleLinkState.Idle)
    val linkState: StateFlow<BleLinkState> = _linkState.asStateFlow()

    /** Raw GATT notification bytes from TELEMETRY_CHAR after reaching Bound state. */
    private val _incomingFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<ByteArray> = _incomingFrames.asSharedFlow()

    /** Session key derived from the ECDH handshake. Null until Bound state. */
    @Volatile private var sessionKey: ByteArray? = null

    // ── Internal GATT event bridge ────────────────────────────────────────────

    internal sealed interface GattEvent {
        data class ConnectionChanged(val status: Int, val newState: Int) : GattEvent
        data class MtuChanged(val mtu: Int, val status: Int) : GattEvent
        data class ServicesDiscovered(val status: Int) : GattEvent
        data class CharacteristicChanged(val charUuid: UUID, val value: ByteArray) : GattEvent
        data class CharacteristicWritten(val charUuid: UUID, val status: Int) : GattEvent
        data class DescriptorWritten(val descriptorUuid: UUID, val status: Int) : GattEvent
        data class RssiRead(val rssi: Int, val status: Int) : GattEvent
    }

    internal var gattEventBus = Channel<GattEvent>(Channel.BUFFERED)
    private var activeGatt: BluetoothGatt? = null

    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    // ── GATT callback ─────────────────────────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            gattEventBus.trySend(GattEvent.ConnectionChanged(status, newState))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gattEventBus.trySend(GattEvent.MtuChanged(mtu, status))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gattEventBus.trySend(GattEvent.ServicesDiscovered(status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == GattConstants.TELEMETRY_CHAR_UUID) {
                _incomingFrames.tryEmit(value)
            } else {
                gattEventBus.trySend(GattEvent.CharacteristicChanged(characteristic.uuid, value))
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            gattEventBus.trySend(GattEvent.CharacteristicWritten(characteristic.uuid, status))
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            gattEventBus.trySend(GattEvent.DescriptorWritten(descriptor.uuid, status))
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            gattEventBus.trySend(GattEvent.RssiRead(rssi, status))
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Drives the full BLE pipeline. Suspends until Bound or an unrecoverable error.
     *
     * @param deviceAddress Bluetooth MAC address from [BlackboxConfig] / pairing QR.
     * @param piPublicKeyBytes Pi's pinned P-256 public key (65 bytes, from QR). Null during
     *   development/demo — handshake is skipped if the Pi stub doesn't send a valid CHALLENGE.
     */
    suspend fun connect(deviceAddress: String, piPublicKeyBytes: ByteArray? = null) {
        gattEventBus.cancel()
        gattEventBus = Channel(Channel.BUFFERED)
        sessionKey = null

        keystoreManager.provisionIfAbsent()

        try {
            val device = scanForDevice(deviceAddress)
            val gatt   = connectGatt(device)
            negotiateMtuAndPhy(gatt)
            awaitBond(device)
            discoverServices(gatt)
            performHandshake(gatt, piPublicKeyBytes)
            confirmDims(gatt, GattConstants.Control.REQUEST_256D)
            enableResponseNotifications(gatt)
            enableTelemetry(gatt)
            enableAlertChar(gatt)
            _linkState.value = BleLinkState.Bound
        } catch (e: BleStepException) {
            _linkState.value = BleLinkState.Error(e.error)
            disconnect()
        }
    }

    fun disconnect() {
        activeGatt?.let { it.disconnect(); it.close() }
        activeGatt = null
        sessionKey = null
        _linkState.value = BleLinkState.Idle
    }

    /**
     * Writes a stream-mode request to CONTROL_CHAR (design spec §7.1).
     * Call after [BleLinkState.Bound] to adjust the Pi's output dimensionality.
     *
     * @param dimsCode One of [GattConstants.Control.REQUEST_256D], [REQUEST_512D], [REQUEST_RRI_ONLY].
     */
    suspend fun requestDims(dimsCode: Byte) {
        val gatt = activeGatt ?: return
        val char = gatt.getService(GattConstants.VIGIA_SERVICE_UUID)
            ?.getCharacteristic(GattConstants.CONTROL_CHAR_UUID) ?: return
        writeCharacteristic(gatt, char, byteArrayOf(dimsCode))
    }

    // ── Step 1 — LE scan ──────────────────────────────────────────────────────

    private suspend fun scanForDevice(address: String): BluetoothDevice {
        _linkState.value = BleLinkState.Scanning
        val scanner = adapter?.bluetoothLeScanner ?: throw BleStepException(BleLinkError.SCAN_FAILED)

        val filter   = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        return suspendCancellableCoroutine { cont ->
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    cont.resumeSafely(result.device)
                }
                override fun onScanFailed(errorCode: Int) {
                    cont.resumeWithException(BleStepException(BleLinkError.SCAN_FAILED))
                }
            }
            scanner.startScan(listOf(filter), settings, cb)
            cont.invokeOnCancellation { scanner.stopScan(cb) }
        }
    }

    // ── Step 2 — GATT connect ─────────────────────────────────────────────────

    private suspend fun connectGatt(device: BluetoothDevice): BluetoothGatt {
        _linkState.value = BleLinkState.Connecting(device.address)
        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        activeGatt = gatt

        val event = awaitEvent(15_000) { it is GattEvent.ConnectionChanged } as GattEvent.ConnectionChanged
        if (event.status != BluetoothGatt.GATT_SUCCESS || event.newState != BluetoothProfile.STATE_CONNECTED) {
            throw BleStepException(BleLinkError.CONNECTION_TIMEOUT)
        }
        return gatt
    }

    // ── Step 2.5 — MTU negotiation + 2M PHY (design spec §7) ─────────────────

    private suspend fun negotiateMtuAndPhy(gatt: BluetoothGatt) {
        // Request ATT MTU 517 — handles 512-D frame (2054 bytes) via ATT fragmentation.
        gatt.requestMtu(GattConstants.TARGET_MTU)
        awaitEvent(5_000) { it is GattEvent.MtuChanged }
        // Non-fatal: proceed even if MTU stays at 23 (frame delivery still works, just fragmented).

        // Prefer LE 2M PHY for throughput (~175 kB/s ceiling vs ~125 kB/s on 1M).
        gatt.setPreferredPhy(
            BluetoothDevice.PHY_LE_2M_MASK,
            BluetoothDevice.PHY_LE_2M_MASK,
            BluetoothDevice.PHY_OPTION_NO_PREFERRED,
        )
        // PHY switch is best-effort; no event to await — BlueZ accepts automatically.
    }

    // ── Step 3 — LE Secure Connections bond ──────────────────────────────────

    private suspend fun awaitBond(device: BluetoothDevice) {
        _linkState.value = BleLinkState.Pairing
        if (device.bondState == BluetoothDevice.BOND_BONDED) return

        val bonded = suspendCancellableCoroutine { cont ->
            val receiver = buildBondReceiver(device, cont)
            context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
            device.createBond()
        }
        if (!bonded) throw BleStepException(BleLinkError.PAIRING_FAILED)
    }

    private fun buildBondReceiver(target: BluetoothDevice, cont: CancellableContinuation<Boolean>) =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device?.address != target.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                    BluetoothDevice.BOND_BONDED -> { runCatching { ctx.unregisterReceiver(this) }; cont.resumeSafely(true) }
                    BluetoothDevice.BOND_NONE   -> { runCatching { ctx.unregisterReceiver(this) }; cont.resumeSafely(false) }
                }
            }
        }

    // ── Step 4 — Service discovery ────────────────────────────────────────────

    private suspend fun discoverServices(gatt: BluetoothGatt) {
        gatt.discoverServices()
        val event = awaitEvent(10_000) { it is GattEvent.ServicesDiscovered } as GattEvent.ServicesDiscovered
        if (event.status != BluetoothGatt.GATT_SUCCESS) throw BleStepException(BleLinkError.GATT_ERROR)
    }

    // ── Step 5 — ECDH P-256 mutual authentication (design spec §4.2a) ─────────

    private suspend fun performHandshake(gatt: BluetoothGatt, piPublicKeyBytes: ByteArray?) {
        _linkState.value = BleLinkState.Handshaking

        val service = gatt.getService(GattConstants.VIGIA_SERVICE_UUID)
            ?: throw BleStepException(BleLinkError.GATT_ERROR)
        val hsChar = service.getCharacteristic(GattConstants.HANDSHAKE_CHAR_UUID)
            ?: throw BleStepException(BleLinkError.GATT_ERROR)

        // Enable notifications so CHALLENGE and CONFIRM arrive asynchronously.
        enableCharNotifications(gatt, hsChar)

        // 5a. Send HELLO.
        writeCharacteristic(gatt, hsChar, byteArrayOf(GattConstants.Protocol.HELLO))

        // 5b. Await CHALLENGE: [0x02 | nonce_pi(32) | Pi_pub(65) | ECDSA_sig(var)]
        val challengeEvent = awaitCharNotification(GattConstants.HANDSHAKE_CHAR_UUID, 10_000)
        val challenge = challengeEvent.value

        if (challenge.size < GattConstants.Protocol.CHALLENGE_MIN_BYTES ||
            challenge[0] != GattConstants.Protocol.CHALLENGE
        ) throw BleStepException(BleLinkError.HANDSHAKE_FAILED)

        val noncePi    = challenge.copyOfRange(1, 1 + GattConstants.Protocol.NONCE_BYTES)
        val piPub65    = challenge.copyOfRange(
            1 + GattConstants.Protocol.NONCE_BYTES,
            1 + GattConstants.Protocol.NONCE_BYTES + GattConstants.Protocol.P256_PUB_BYTES,
        )
        val piSigBytes = challenge.copyOfRange(
            1 + GattConstants.Protocol.NONCE_BYTES + GattConstants.Protocol.P256_PUB_BYTES,
            challenge.size,
        )

        // 5c. Verify Pi's identity: pinned pub key must match, and sig must cover nonce_pi || Pi_pub.
        if (piPublicKeyBytes != null) {
            if (!piPub65.contentEquals(piPublicKeyBytes)) throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
            val signed = noncePi + piPub65
            if (!EcdhHandshake.verifyEcdsaP256(piPub65, signed, piSigBytes)) {
                throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
            }
        }
        // If piPublicKeyBytes is null (demo/dev mode), skip identity pinning — still do ECDH.

        // 5d. Generate phone nonce.
        val noncePhone = ByteArray(GattConstants.Protocol.NONCE_BYTES).also { SecureRandom().nextBytes(it) }

        // 5e. ECDH: shared secret + derive session key.
        val sharedSecret = keystoreManager.computeSharedSecret(piPub65)
        val salt = noncePi + noncePhone
        sessionKey = EcdhHandshake.hkdfSha256(sharedSecret, salt)

        // 5f. Sign RESPONSE material: nonce_phone || nonce_pi || Phone_pub (binds both sides).
        val phonePub65 = keystoreManager.getPublicKeyUncompressed()
        val responseSigned = noncePhone + noncePi + phonePub65
        val phoneSig = keystoreManager.signEcdsa(responseSigned)

        // 5g. Send RESPONSE: [0x03 | nonce_phone(32) | Phone_pub(65) | ECDSA_sig(var)]
        val response = byteArrayOf(GattConstants.Protocol.RESPONSE) + noncePhone + phonePub65 + phoneSig
        writeCharacteristic(gatt, hsChar, response)

        // 5h. Await CONFIRM: [0x04 | HMAC(session_key, "VIGIA-CONFIRM" || nonce_pi || nonce_phone)]
        val confirmEvent = awaitCharNotification(GattConstants.HANDSHAKE_CHAR_UUID, 10_000)
        val confirm = confirmEvent.value

        if (confirm.isEmpty() || confirm[0] == GattConstants.Protocol.ERR) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }
        if (confirm[0] != GattConstants.Protocol.BOUND) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }
        if (confirm.size < 1 + GattConstants.Protocol.CONFIRM_HMAC_BYTES) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }

        // 5i. Verify CONFIRM HMAC — proves Pi derived the same session key.
        val sk = sessionKey ?: throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        val expectedHmac = EcdhHandshake.hmacSha256(sk, EcdhHandshake.CONFIRM_LABEL + noncePi + noncePhone)
        val receivedHmac = confirm.copyOfRange(1, 1 + GattConstants.Protocol.CONFIRM_HMAC_BYTES)
        if (!expectedHmac.contentEquals(receivedHmac)) throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
    }

    // ── Step 6 — Confirm stream dims to Pi (design spec §7.1) ────────────────

    private suspend fun confirmDims(gatt: BluetoothGatt, dimsCode: Byte) {
        val service = gatt.getService(GattConstants.VIGIA_SERVICE_UUID) ?: return
        val ctrl    = service.getCharacteristic(GattConstants.CONTROL_CHAR_UUID) ?: return
        // WRITE_TYPE_NO_RESPONSE — CONTROL_CHAR is write-without-response per profile.
        gatt.writeCharacteristic(ctrl, byteArrayOf(dimsCode), BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        // No write confirmation event for no-response writes; proceed immediately.
    }

    // ── Step 7 — Enable telemetry notifications ───────────────────────────────

    private suspend fun enableResponseNotifications(gatt: BluetoothGatt) {
        val service  = gatt.getService(GattConstants.VIGIA_SERVICE_UUID) ?: return
        val respChar = service.getCharacteristic(GattConstants.RESPONSE_CHAR_UUID) ?: return
        enableCharNotifications(gatt, respChar)
    }

    private suspend fun enableTelemetry(gatt: BluetoothGatt) {
        val service = gatt.getService(GattConstants.VIGIA_SERVICE_UUID) ?: return
        val telChar = service.getCharacteristic(GattConstants.TELEMETRY_CHAR_UUID) ?: return
        enableCharNotifications(gatt, telChar)
    }

    private suspend fun enableAlertChar(gatt: BluetoothGatt) {
        val service   = gatt.getService(GattConstants.VIGIA_SERVICE_UUID) ?: return
        val alertChar = service.getCharacteristic(GattConstants.ALERT_CHAR_UUID) ?: return
        enableCharNotifications(gatt, alertChar)
    }

    // ── GATT helpers ──────────────────────────────────────────────────────────

    private suspend fun enableCharNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(GattConstants.CCCD_UUID) ?: return
        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        awaitEvent(5_000) { it is GattEvent.DescriptorWritten }
    }

    private suspend fun writeCharacteristic(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        awaitEvent(5_000) { it is GattEvent.CharacteristicWritten && it.charUuid == char.uuid }
    }

    private suspend fun awaitEvent(timeoutMs: Long, predicate: (GattEvent) -> Boolean): GattEvent =
        withTimeout(timeoutMs) {
            var event: GattEvent
            do { event = gattEventBus.receive() } while (!predicate(event))
            event
        }

    private suspend fun awaitCharNotification(uuid: UUID, timeoutMs: Long): GattEvent.CharacteristicChanged =
        awaitEvent(timeoutMs) { it is GattEvent.CharacteristicChanged && it.charUuid == uuid }
            as GattEvent.CharacteristicChanged

    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        if (isActive) resume(value)
    }

    private class BleStepException(val error: BleLinkError) : Exception(error.name)
}
