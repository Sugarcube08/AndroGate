package me.capcom.smsgateway.modules.messages.data

import java.util.Date

class SendParams(
    val withDeliveryReport: Boolean = true,
    val skipPhoneValidation: Boolean = false,
    /**
     * Sim number (from 1 to 3)
     */
    val simNumber: Int? = null,
    val validUntil: Date? = null,
    val scheduleAt: Date? = null,
    val priority: Byte? = null,
)
