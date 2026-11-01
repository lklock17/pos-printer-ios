package com.lklock17.omnipos.models

import java.util.UUID

data class SavedRecipient(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var phone: String = "",
    var address: String = "",
    var postalCode: String = "",
    var defaultCourier: String = "J&T Express",
    var notes: String = "",
    var lastUsed: Long = System.currentTimeMillis()
)
