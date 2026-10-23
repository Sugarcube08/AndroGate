package me.capcom.smsgateway.modules.settings

import androidx.preference.PreferenceManager
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.modules.device.DeviceSettings
import me.capcom.smsgateway.modules.encryption.EncryptionSettings
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.logs.LogsSettings
import me.capcom.smsgateway.modules.messages.MessagesSettings
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val settingsModule = module {
    singleOf(::SettingsService)
    factory { PreferenceManager.getDefaultSharedPreferences(get()) }
    factory { SettingsHelper(get()) }

    factory {
        EncryptionSettings(
            PreferencesStorage(get(), "encryption")
        )
    }
    factory {
        GatewaySettings(
            PreferencesStorage(get(), "gateway")
        )
    }
    factory {
        MessagesSettings(
            PreferencesStorage(get(), "messages")
        )
    }
    factory {
        LogsSettings(
            PreferencesStorage(get(), "logs")
        )
    }
    factory {
        DeviceSettings(
            PreferencesStorage(get(), "device")
        )
    }
}
