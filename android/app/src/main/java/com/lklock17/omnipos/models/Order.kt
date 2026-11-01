package com.lklock17.omnipos.models

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class OrderType(val label: String) {
    OFFLINE("Di Tempat (Offline)"),
    SHIPPING("Paket Ekspedisi (4x6 / Stiker)")
}

enum class PaymentMethod(val label: String) {
    CASH("Tunai (Cash)"),
    QRIS("QRIS / E-Wallet"),
    TRANSFER("Transfer Bank"),
    DEBIT_CARD("Kartu Debit")
}

data class OrderItem(
    val id: String = UUID.randomUUID().toString(),
    val product: Product,
    var quantity: Int,
    var itemDiscount: Double = 0.0,
    var notes: String = ""
) {
    val subtotal: Double
        get() = (product.price * quantity) - itemDiscount
}

data class Order(
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String = generateInvoice(),
    val date: Long = System.currentTimeMillis(),
    val items: List<OrderItem>,
    val discount: Double = 0.0,
    val taxRate: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val paymentAmount: Double = 0.0,
    val change: Double = 0.0,
    val orderType: OrderType = OrderType.OFFLINE,
    val shippingInfo: ShippingInfo? = null
) {
    val subtotal: Double
        get() = items.sumOf { it.subtotal }

    val formattedTotal: String
        get() = formatRupiah(total)

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
            return sdf.format(Date(date))
        }

    companion object {
        fun generateInvoice(): String {
            val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
            val dateStr = sdf.format(Date())
            val rand = (1000..9999).random()
            return "INV-$dateStr-$rand"
        }

        fun formatRupiah(amount: Double): String {
            val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            format.maximumFractionDigits = 0
            return format.format(amount)
        }
    }
}

