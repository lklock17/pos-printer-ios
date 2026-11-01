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

object BarcodeHelper {
    /**
     * Membuat barcode standar 12 digit acak (EAN-12 format Indonesia diawali 899)
     */
    fun generateEan12(): String {
        val random9 = (1..9).map { (0..9).random() }.joinToString("")
        return "899$random9"
    }

    /**
     * Membuat SKU unik toko (misal: PRD-XXXX)
     */
    fun generateSku(): String {
        val randomNum = (1000..9999).random()
        return "PRD-$randomNum"
    }
}


