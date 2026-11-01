package com.lklock17.omnipos.models

import java.util.UUID

data class Product(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var sku: String = "",
    var barcode: String = "",
    var price: Double,
    var costPrice: Double = 0.0,
    var stock: Int = 0,
    var minStockAlert: Int = 5,
    var category: String = "Semua",
    var unit: String = "pcs",
    var imageBase64: String? = null,
    var isFavorite: Boolean = false
) {
    val isLowStock: Boolean
        get() = stock <= minStockAlert
}
