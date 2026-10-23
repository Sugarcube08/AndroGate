package me.capcom.smsgateway.modules.orchestrator

import android.content.Context
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.device.DeviceService
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.logs.LogsService
import me.capcom.smsgateway.modules.messages.MessagesService

class OrchestratorService(
    private val messagesSvc: MessagesService,
    private val gatewaySvc: GatewayService,
    private val logsSvc: LogsService,
    private val deviceService: DeviceService,
    private val settings: SettingsHelper,
) {
    fun start(context: Context, autostart: Boolean) {
        if (autostart && !settings.autostart) {
            return
        }

        logsSvc.start(context)
        messagesSvc.start(context)
        deviceService.start(context)
        gatewaySvc.start(context)
    }

    fun stop(context: Context) {
        gatewaySvc.stop(context)
        messagesSvc.stop(context)
        logsSvc.stop(context)
        deviceService.stop(context)
    }
}
