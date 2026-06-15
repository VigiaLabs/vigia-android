package com.vigia.core.sensor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vigia.core.model.BleLinkError
import com.vigia.core.model.BleLinkState
import com.vigia.core.model.DevicePresenceState
import com.vigia.core.sensor.BlackboxConfig
import com.vigia.core.sensor.ble.BleRepository
import com.vigia.core.sensor.cdm.CdmPresenceRepository
import com.vigia.core.wallet.WalletRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [foregroundServiceType="connectedDevice"] keeps this service alive indefinitely
 * alongside an active CDM association — the 6-hour [dataSync] cap does not apply.
 *
 * Lifecycle contract:
 *   onCreate   → startForeground (mandatory before returning) → observe presence
 *   Present    → launch BLE connect coroutine (cancelled on Absent / onDestroy)
 *   Absent     → disconnect + update notification
 *   onDestroy  → cancel serviceScope (cancels any in-flight GATT coroutine cleanly)
 *
 * Retry policy:
 *   Up to [MAX_RETRIES] attempts with exponential backoff are made per presence window.
 *   Each retry increments [_serviceState] retryCount so the UI can reflect it.
 */
@AndroidEntryPoint
class VigiaForegroundService : Service() {

    @Inject lateinit var cdmRepository: CdmPresenceRepository
    @Inject lateinit var bleRepository: BleRepository
    @Inject lateinit var blackboxConfig: BlackboxConfig
    @Inject lateinit var walletRepository: WalletRepository

    // Prevent repeated provisioning across presence flaps in a single service lifetime.
    @Volatile private var walletProvisioned = false

    // Service-scoped coroutine scope; cancelled in onDestroy.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Active BLE connection job — replaced on every new Presence=true event.
    private var bleJob: Job? = null

    private val notificationManager: NotificationManager by lazy {
        getSystemService(NotificationManager::class.java)
    }

    // ── Hilt-injectable service state ─────────────────────────────────────────

    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Idle)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(ServiceState.Idle))
        observePresence()
        observeLinkState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bleRepository.let {
            serviceScope.launch { it.disconnect() }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Presence observer ─────────────────────────────────────────────────────

    /**
     * [collectLatest] cancels the previous block whenever a new value is emitted —
     * if presence toggles rapidly (device leaves/returns), any in-flight BLE coroutine
     * is cancelled before the next attempt starts.
     */
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
        var attempts = 0
        var delayMs = INITIAL_BACKOFF_MS

        while (attempts < MAX_RETRIES) {
            try {
                bleRepository.startScan(blackboxConfig.macAddress)
                return  // connected — exit retry loop
            } catch (e: Exception) {
                attempts++
                if (attempts >= MAX_RETRIES) {
                    _serviceState.value = ServiceState.Error(
                        cause   = e.message ?: BleLinkError.GATT_ERROR.name,
                        retries = attempts,
                    )
                    return
                }
                _serviceState.value = ServiceState.Error(
                    cause   = e.message ?: BleLinkError.GATT_ERROR.name,
                    retries = attempts,
                )
                kotlinx.coroutines.delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VIGIA Copilot",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Hardware link status"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: ServiceState): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VIGIA Copilot")
            .setContentText(state.toNotificationText())
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun ServiceState.toNotificationText(): String = when (this) {
        ServiceState.Idle                 -> "Starting…"
        ServiceState.AwaitingPresence     -> "Waiting for Blackbox"
        is ServiceState.Connecting        -> "Connecting to ${deviceAddress.takeLast(5)}"
        is ServiceState.Connected         -> "Blackbox linked · ${deviceAddress.takeLast(5)}"
        is ServiceState.Error             -> "Connection error (attempt $retries)"
    }

    companion object {
        private const val NOTIFICATION_ID     = 1001
        private const val CHANNEL_ID          = "vigia_copilot_channel"
        private const val MAX_RETRIES         = 3
        private const val INITIAL_BACKOFF_MS  = 2_000L
        private const val MAX_BACKOFF_MS      = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VigiaForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VigiaForegroundService::class.java))
        }
    }
}
