package com.lklock17.omnipos.models

data class StoreConfig(
    var storeName: String = "OmniPOS Store",
    var storeAddress: String = "Jl. Sudirman No. 123, Jakarta",
    var storePhone: String = "0812-3456-7890",
    var footerMessage: String = "Terima Kasih Atas Kunjungan Anda!",
    var defaultSenderName: String = "OmniPOS Official Store",
    var defaultSenderPhone: String = "0812-3456-7890",
    var defaultSenderAddress: String = "Jl. Sudirman No. 123, Jakarta Pusat",
    var isAutoCutEnabled: Boolean = true,
    var feedLinesBeforeCut: Int = 4,
    var isCashDrawerEnabled: Boolean = false,
    var isTaxEnabled: Boolean = false,
    var taxRatePercent: Double = 11.0
)

