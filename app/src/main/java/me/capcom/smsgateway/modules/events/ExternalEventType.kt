package me.capcom.smsgateway.modules.events

import com.google.gson.annotations.SerializedName

enum class ExternalEventType {
    @SerializedName("MessageEnqueued")
    MessageEnqueued,

    @SerializedName("SettingsUpdated")
    SettingsUpdated,

    @SerializedName("MessageCancelled")
    MessageCancelled,
}
