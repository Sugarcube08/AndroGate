package me.capcom.smsgateway.modules.gateway.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import me.capcom.smsgateway.R
import me.capcom.smsgateway.helpers.SSEManager
import me.capcom.smsgateway.modules.events.ExternalEvent
import me.capcom.smsgateway.modules.events.ExternalEventType
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.workers.PullMessagesWorker
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.logs.db.LogEntry
import me.capcom.smsgateway.modules.notifications.NotificationsService
import me.capcom.smsgateway.modules.orchestrator.EventsRouter
import org.koin.android.ext.android.inject

class GatewayForegroundService : Service() {
    private val settings: GatewaySettings by inject()

    private val eventsRouter by inject<EventsRouter>()

    private val notificationsSvc: NotificationsService by inject()
    private val logsService: LogsService by inject()

    private val wakeLock: PowerManager.WakeLock by lazy {
        (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.javaClass.name)
        }
    }
    private val wifiLock: WifiManager.WifiLock by lazy {
        (getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            this.javaClass.name
        )
    }

    private var sseManager: SSEManager? = null

    override fun onCreate() {
        super.onCreate()

        if (!wakeLock.isHeld) {
            wakeLock.acquire()
        }
        if (!wifiLock.isHeld) {
            wifiLock.acquire()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationsSvc.makeNotification(
            this,
            NotificationsService.NOTIFICATION_ID_REALTIME_EVENTS,
            getString(R.string.listening_to_the_server_events)
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationsService.NOTIFICATION_ID_REALTIME_EVENTS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationsService.NOTIFICATION_ID_REALTIME_EVENTS, notification)
        }

        val token = settings.registrationInfo?.token
        if (token == null) {
            Log.d("GatewayFGService", "Device not registered or token missing; deferring SSE connection")
            return START_STICKY
        }

        if (sseManager == null) {
            sseManager = SSEManager(
                "${settings.serverUrl}/events",
                token
            ).apply {
                onConnected = {
                    Log.d("GatewayFGService", "SSE connected, pulling pending messages")
                    try {
                        PullMessagesWorker.start(this@GatewayForegroundService)
                    } catch (e: Throwable) {
                        e.printStackTrace()
                        logsService.insert(
                            LogEntry.Priority.ERROR,
                            "GatewayFGService",
                            "Failed to start PullMessagesWorker on connect",
                        )
                    }
                }
                onEvent = { event, data ->
                    Log.d("GatewayFGService", "$event: $data")

                    try {
                        processEvent(event, data)
                    } catch (e: Throwable) {
                        e.printStackTrace()

                        logsService.insert(
                            LogEntry.Priority.ERROR,
                            "GatewayFGService",
                            "Failed to process event",
                            mapOf("event" to event, "data" to data)
                        )
                    }
                }
            }
        }

        sseManager?.connect()

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun processEvent(event: String?, data: String) {
        val type = try {
            event?.let { ExternalEventType.valueOf(it) }
                ?: ExternalEventType.MessageEnqueued
        } catch (e: Throwable) {
            throw RuntimeException("Unknown event type: $event", e)
        }

        eventsRouter.route(
            ExternalEvent(
                type = type,
                data = data
            )
        )
    }

    override fun onDestroy() {
        sseManager?.disconnect()
        sseManager = null

        if (wifiLock.isHeld) {
            wifiLock.release()
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GatewayForegroundService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GatewayForegroundService::class.java))
        }
    }
}
