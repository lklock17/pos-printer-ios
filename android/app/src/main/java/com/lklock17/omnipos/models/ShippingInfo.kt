package com.lklock17.omnipos.models

data class ShippingInfo(
    var courier: String = "J&T Express",
    var service: String = "REGULER",
    var trackingNumber: String = "",
    var senderName: String = "",
    var senderPhone: String = "",
    var senderAddress: String = "",
    var recipientName: String = "",
    var recipientPhone: String = "",
    var recipientAddress: String = "",
    var postalCode: String = "",
    var weightKg: Double = 1.0,
    var isCOD: Boolean = false,
    var codAmount: Double = 0.0,
    var notes: String = ""
)

