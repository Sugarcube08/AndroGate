package me.capcom.smsgateway.domain

data class SimCard(
    val slotIndex: Int,
    val simNumber: Int,
    val phoneNumber: String? = null,
    val carrierName: String? = null,
    val iccid: String? = null,
)
