package com.vigia.core.sensor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vigia.core.model.BleLinkState
import com.vigia.core.model.DevicePresenceState
import com.vigia.core.sensor.BlackboxConfig
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.sensor.pairing.PairingRepository
import com.vigia.core.wallet.WalletRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service managing the BLE connection lifecycle.
 *
 * Auto-connect flow (design spec §5.3 "AirPods-magic"):
 *  1. [PairingRepository.pairedConfig] → call [CdmPresenceRepository.registerPresenceObserver]
 *     whenever the association ID changes (including first-time pairing).
 *  2. CDM wakes this service when the Pi advertisement is in range.
 *  3. [presenceState] = Present → [connectWithRetry] with the Pi's pinned public key.
 *
 * [foregroundServiceType="connectedDevice"] exempts from the 6-hour background cap (API 34+).
 */
@AndroidEntryPoint
class VigiaForegroundService : Service() {

    @Inject lateinit var cdmRepository: CdmPresenceRepository
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var blackboxConfig: BlackboxConfig
    @Inject lateinit var walletRepository: WalletRepository
    @Inject lateinit var pairingRepository: PairingRepository

    @Volatile private var walletProvisioned = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ServiceState.Idle))
        observePairedConfig()
        observePresence()
        observeLinkState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.launch { bleRepository.disconnect() }
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── CDM registration — keeps auto-connect alive after pairing ─────────────

    private fun observePairedConfig() {
        serviceScope.launch {
            pairingRepository.pairedConfig.collectLatest { config ->
                val id = config?.associationId ?: return@collectLatest
                if (id > 0) cdmRepository.registerPresenceObserver(id)
            }
        }
    }

    // ── Presence observer ─────────────────────────────────────────────────────

    private fun observePresence() {
        serviceScope.launch {
            cdmRepository.presenceState.collectLatest { presence ->
                when (presence) {
                    DevicePresenceState.Present -> {
                        if (!walletProvisioned) {
                            walletProvisioned = true
                            serviceScope.launch { walletRepository.ensureProvisioned() }
                        }
                        connectWithRetry()
                    }
                    DevicePresenceState.Absent,
                    DevicePresenceState.Unknown -> {
                        bleRepository.disconnect()
                        _serviceState.value = ServiceState.AwaitingPresence
                    }
                }
            }
        }
    }

    // ── Link state → notification ─────────────────────────────────────────────

    private fun observeLinkState() {
        serviceScope.launch {
            bleRepository.linkState.collect { link ->
                val state = ServiceState.fromLinkState(blackboxConfig.macAddress, link)
                _serviceState.value = state
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    // ── Retry wrapper ─────────────────────────────────────────────────────────

    private suspend fun connectWithRetry() {
        // Resolve the current paired config — provides MAC override and Pi public key.
        val paired = pairingRepository.pairedConfig.first()
        val mac    = paired?.mac ?: blackboxConfig.macAddress
        val piPub  = paired?.piPublicKeyBytes

        var attempts = 0
        var delayMs  = INITIAL_BACKOFF_MS

        while (attempts < MAX_RETRIES) {
            try {
                bleRepository.startScan(mac, piPub)
                return
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_RETRIES) {
                    _serviceState.value = ServiceState.Error(
                        cause = e.message ?: "GATT_ERROR",
                        retries = attempts,
                    )
                    return
                }
                _serviceState.value = ServiceState.Error(
                    cause = e.message ?: "GATT_ERROR",
                    retries = attempts,
                )
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "VIGIA Copilot", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "Hardware link status"; setShowBadge(false) }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: ServiceState): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VIGIA Copilot")
            .setContentText(state.toNotificationText())
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .build()

    private fun ServiceState.toNotificationText(): String = when (this) {
        ServiceState.Idle             -> "Starting…"
        ServiceState.AwaitingPresence -> "Waiting for Blackbox"
        is ServiceState.Connecting    -> "Connecting to ${deviceAddress.takeLast(5)}"
        is ServiceState.Connected     -> "Blackbox linked · ${deviceAddress.takeLast(5)}"
        is ServiceState.Error         -> "Connection error (attempt $retries)"
    }

    companion object {
        private const val NOTIFICATION_ID    = 1001
        private const val CHANNEL_ID         = "vigia_copilot_channel"
        private const val MAX_RETRIES        = 3
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS     = 30_000L

        fun start(context: Context) =
            context.startForegroundService(Intent(context, VigiaForegroundService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, VigiaForegroundService::class.java))
    }
}
