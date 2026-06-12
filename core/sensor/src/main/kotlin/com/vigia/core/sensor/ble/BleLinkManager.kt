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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Single source of truth for the BLE link lifecycle.
 *
 * Drives the pipeline:
 *   Idle → Scanning → Connecting → Pairing (LE SC) → Handshaking → Bound
 *
 * Concurrency contract:
 *   [connect] is a suspending function called from the service coroutine scope.
 *   Cancelling that scope (service onDestroy) cancels in-flight GATT operations cleanly.
 *
 * Thread safety:
 *   [_linkState] and [_incomingFrames] are thread-safe MutableState/SharedFlow.
 *   [gattEventBus] is a buffered Channel — GATT callbacks post to it; the connect
 *   coroutine consumes sequentially, so no concurrent readers exist.
 */
@Singleton
@SuppressLint("MissingPermission")  // Permissions are enforced in PermissionManager at UI layer.
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

    // ── Internal GATT event bridge ────────────────────────────────────────────

    private sealed interface GattEvent {
        data class ConnectionChanged(val status: Int, val newState: Int) : GattEvent
        data class ServicesDiscovered(val status: Int) : GattEvent
        data class CharacteristicChanged(val charUuid: UUID, val value: ByteArray) : GattEvent
        data class CharacteristicWritten(val charUuid: UUID, val status: Int) : GattEvent
        data class DescriptorWritten(val descriptorUuid: UUID, val status: Int) : GattEvent
    }

    // Recreated at the start of each connect() call to flush stale events.
    private var gattEventBus = Channel<GattEvent>(Channel.BUFFERED)
    private var activeGatt: BluetoothGatt? = null

    // ── Bluetooth adapter ─────────────────────────────────────────────────────

    private val bluetoothManager by lazy {
        context.getSystemService(BluetoothManager::class.java)
    }
    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    // ── GATT callback — posts all events to gattEventBus ─────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            gattEventBus.trySend(GattEvent.ConnectionChanged(status, newState))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            gattEventBus.trySend(GattEvent.ServicesDiscovered(status))
        }

        // API 33+ preferred override (value delivered directly, no stale characteristic.value read)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == GattConstants.TELEMETRY_CHAR_UUID) {
                // Telemetry frames bypass the event bus and go straight to the shared flow.
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
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Drives the full BLE pipeline. Suspends until [BleLinkState.Bound] is achieved
     * or an unrecoverable error occurs. Cancelling the caller's coroutine scope
     * triggers [disconnect] cleanup via the cancellation handler.
     */
    suspend fun connect(deviceAddress: String) {
        // Reset event bus so stale events from a previous session don't bleed through.
        gattEventBus.cancel()
        gattEventBus = Channel(Channel.BUFFERED)

        keystoreManager.provisionIfAbsent()

        try {
            val device = scanForDevice(deviceAddress)
            val gatt   = connectGatt(device)
            awaitBond(device)
            discoverServices(gatt)
            performHandshake(gatt)
            enableTelemetry(gatt)
            _linkState.value = BleLinkState.Bound
        } catch (e: BleStepException) {
            _linkState.value = BleLinkState.Error(e.error)
            disconnect()
        }
    }

    fun disconnect() {
        activeGatt?.let {
            it.disconnect()
            it.close()
        }
        activeGatt = null
        _linkState.value = BleLinkState.Idle
    }

    // ── Step 1 — LE scan for target device ────────────────────────────────────

    private suspend fun scanForDevice(address: String): BluetoothDevice {
        _linkState.value = BleLinkState.Scanning
        val scanner = adapter?.bluetoothLeScanner
            ?: throw BleStepException(BleLinkError.SCAN_FAILED)

        val filter = ScanFilter.Builder().setDeviceAddress(address).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return suspendCancellableCoroutine { cont ->
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    cont.resumeSafely(result.device)
                }
                override fun onScanFailed(errorCode: Int) {
                    cont.resumeWithException(BleStepException(BleLinkError.SCAN_FAILED))
                }
            }
            scanner.startScan(listOf(filter), settings, callback)
            cont.invokeOnCancellation { scanner.stopScan(callback) }
        }
    }

    // ── Step 2 — GATT connect ─────────────────────────────────────────────────

    private suspend fun connectGatt(device: BluetoothDevice): BluetoothGatt {
        _linkState.value = BleLinkState.Connecting(device.address)
        val gatt = device.connectGatt(context, /*autoConnect=*/false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        activeGatt = gatt

        val event = awaitEvent(timeoutMs = 15_000) {
            it is GattEvent.ConnectionChanged
        } as GattEvent.ConnectionChanged

        if (event.status != BluetoothGatt.GATT_SUCCESS || event.newState != BluetoothProfile.STATE_CONNECTED) {
            throw BleStepException(BleLinkError.CONNECTION_TIMEOUT)
        }
        return gatt
    }

    // ── Step 3 — LE Secure Connections bonding (Numeric Compare / OOB) ────────

    private suspend fun awaitBond(device: BluetoothDevice) {
        _linkState.value = BleLinkState.Pairing

        // If already bonded from a previous session, skip.
        if (device.bondState == BluetoothDevice.BOND_BONDED) return

        val bonded = suspendCancellableCoroutine { cont ->
            val receiver = buildBondReceiver(device, cont)
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            context.registerReceiver(receiver, filter)
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
            device.createBond()     // triggers LE SC Numeric Comparison dialog
        }

        if (!bonded) throw BleStepException(BleLinkError.PAIRING_FAILED)
    }

    private fun buildBondReceiver(
        target: BluetoothDevice,
        cont: CancellableContinuation<Boolean>,
    ) = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (device?.address != target.address) return

            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                BluetoothDevice.BOND_BONDED -> {
                    runCatching { ctx.unregisterReceiver(this) }
                    cont.resumeSafely(true)
                }
                BluetoothDevice.BOND_NONE -> {
                    runCatching { ctx.unregisterReceiver(this) }
                    cont.resumeSafely(false)
                }
            }
        }
    }

    // ── Step 4 — GATT service discovery ──────────────────────────────────────

    private suspend fun discoverServices(gatt: BluetoothGatt) {
        gatt.discoverServices()
        val event = awaitEvent(timeoutMs = 10_000) {
            it is GattEvent.ServicesDiscovered
        } as GattEvent.ServicesDiscovered

        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            throw BleStepException(BleLinkError.GATT_ERROR)
        }
    }

    // ── Step 5 — Application-layer HMAC challenge-response handshake ──────────

    private suspend fun performHandshake(gatt: BluetoothGatt) {
        _linkState.value = BleLinkState.Handshaking

        val service = gatt.getService(GattConstants.VIGIA_SERVICE_UUID)
            ?: throw BleStepException(BleLinkError.GATT_ERROR)
        val handshakeChar = service.getCharacteristic(GattConstants.HANDSHAKE_CHAR_UUID)
            ?: throw BleStepException(BleLinkError.GATT_ERROR)

        // 5a. Enable notifications on handshake characteristic so CHALLENGE arrives.
        enableCharNotifications(gatt, handshakeChar)

        // 5b. Write HELLO to initiate the handshake.
        writeCharacteristic(gatt, handshakeChar, byteArrayOf(GattConstants.Protocol.HELLO))

        // 5c. Await CHALLENGE notification (prefix byte + 32-byte nonce).
        val challengeEvent = awaitCharNotification(
            uuid = GattConstants.HANDSHAKE_CHAR_UUID,
            timeoutMs = 10_000,
        )
        val payload = challengeEvent.value
        if (payload.isEmpty() || payload[0] != GattConstants.Protocol.CHALLENGE) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }
        val nonce = payload.drop(1).toByteArray()
        if (nonce.size != GattConstants.Protocol.NONCE_BYTES) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }

        // 5d. Sign the nonce inside the Keystore hardware — key bytes never leave the TEE.
        val hmac = keystoreManager.sign(nonce)

        // 5e. Write RESPONSE = [0x03 | HMAC-SHA256].
        writeCharacteristic(gatt, handshakeChar, byteArrayOf(GattConstants.Protocol.RESPONSE) + hmac)

        // 5f. Await BOUND or ERR.
        val resultEvent = awaitCharNotification(
            uuid = GattConstants.HANDSHAKE_CHAR_UUID,
            timeoutMs = 10_000,
        )
        if (resultEvent.value.firstOrNull() != GattConstants.Protocol.BOUND) {
            throw BleStepException(BleLinkError.HANDSHAKE_FAILED)
        }
    }

    // ── Step 6 — Enable telemetry characteristic notifications ───────────────

    private suspend fun enableTelemetry(gatt: BluetoothGatt) {
        val service = gatt.getService(GattConstants.VIGIA_SERVICE_UUID) ?: return
        val telChar = service.getCharacteristic(GattConstants.TELEMETRY_CHAR_UUID) ?: return
        enableCharNotifications(gatt, telChar)
    }

    // ── GATT helpers ──────────────────────────────────────────────────────────

    private suspend fun enableCharNotifications(gatt: BluetoothGatt, char: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(GattConstants.CCCD_UUID) ?: return
        // API 33+ write API
        gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        awaitEvent(timeoutMs = 5_000) { it is GattEvent.DescriptorWritten }
    }

    private suspend fun writeCharacteristic(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        // API 33+ write with explicit value; writeType is WRITE_TYPE_DEFAULT (with response).
        gatt.writeCharacteristic(char, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        awaitEvent(timeoutMs = 5_000) {
            it is GattEvent.CharacteristicWritten && it.charUuid == char.uuid
        }
    }

    /**
     * Awaits the next event satisfying [predicate], discarding non-matching events.
     * Uses [withTimeout] to enforce an upper bound and throw [kotlinx.coroutines.TimeoutCancellationException]
     * (caught by the connect() error handler as a pipeline failure).
     */
    private suspend fun awaitEvent(timeoutMs: Long, predicate: (GattEvent) -> Boolean): GattEvent =
        withTimeout(timeoutMs) {
            var event: GattEvent
            do { event = gattEventBus.receive() } while (!predicate(event))
            event
        }

    private suspend fun awaitCharNotification(uuid: UUID, timeoutMs: Long): GattEvent.CharacteristicChanged =
        awaitEvent(timeoutMs) {
            it is GattEvent.CharacteristicChanged && it.charUuid == uuid
        } as GattEvent.CharacteristicChanged

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Safely resumes a [CancellableContinuation] only if it is still active. */
    private fun <T> CancellableContinuation<T>.resumeSafely(value: T) {
        if (isActive) resume(value)
    }

    private class BleStepException(val error: BleLinkError) : Exception(error.name)
}
