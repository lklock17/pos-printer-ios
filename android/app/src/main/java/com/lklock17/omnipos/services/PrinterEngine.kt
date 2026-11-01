package com.lklock17.omnipos.services

import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.OrderType
import com.lklock17.omnipos.models.StoreConfig
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object PrinterEngine {

    // ESC/POS Command Constants
    private val CMD_INIT = byteArrayOf(0x1B, 0x40)
    private val CMD_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val CMD_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val CMD_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val CMD_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val CMD_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val CMD_DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val CMD_DOUBLE_WIDTH_HEIGHT = byteArrayOf(0x1B, 0x21, 0x30)
    private val CMD_NORMAL_SIZE = byteArrayOf(0x1B, 0x21, 0x00)
    
    // Auto-Cut Standard Commands (ESC/POS)
    private val CMD_FEED_CUT = byteArrayOf(0x1B, 0x64, 0x05)
    private val CMD_AUTO_CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val CMD_AUTO_CUT_FULL = byteArrayOf(0x1D, 0x56, 0x41, 0x00)

    // MARK: - 1. Build Receipt ESC/POS (Struk Kasir 58mm / 80mm)
    fun buildReceiptData(order: Order, config: StoreConfig): ByteArray {
        val out = ByteArrayOutputStream()
        val width = 32 // 58mm thermal standard characters width
        val divider = "-".repeat(width) + "\r\n"
        val separator = "=".repeat(width) + "\r\n"

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_DOUBLE_WIDTH_HEIGHT)
        out.write(encodeString(config.storeName + "\r\n"))
        out.write(CMD_NORMAL_SIZE)
        out.write(CMD_BOLD_OFF)
        out.write(encodeString(config.storeAddress + "\r\n"))
        out.write(encodeString("Telp: " + config.storePhone + "\r\n"))
        out.write(encodeString(separator))

        // Info Nota
        out.write(CMD_ALIGN_LEFT)
        out.write(encodeString("No. Nota : " + order.invoiceNumber + "\r\n"))
        out.write(encodeString("Tanggal  : " + order.formattedDate + "\r\n"))
        out.write(encodeString("Tipe     : " + order.orderType.label + "\r\n"))
        out.write(encodeString(divider))

        // Daftar Item
        order.items.forEach { item ->
            out.write(CMD_BOLD_ON)
            out.write(encodeString(item.product.name + "\r\n"))
            out.write(CMD_BOLD_OFF)

            val qtyPrice = "  ${item.quantity}x @${Order.formatRupiah(item.product.price)}"
            val subtotal = Order.formatRupiah(item.subtotal)
            out.write(encodeString(formatRow(qtyPrice, subtotal, width)))

            if (item.itemDiscount > 0) {
                out.write(encodeString(formatRow("  (Diskon)", "-${Order.formatRupiah(item.itemDiscount)}", width)))
            }
            if (item.notes.isNotEmpty()) {
                out.write(encodeString("  * ${item.notes}\r\n"))
            }
        }
        out.write(encodeString(divider))

        // Total
        out.write(encodeString(formatRow("Subtotal:", Order.formatRupiah(order.subtotal), width)))
        if (order.discount > 0) {
            out.write(encodeString(formatRow("Diskon:", "-${Order.formatRupiah(order.discount)}", width)))
        }
        if (order.tax > 0) {
            out.write(encodeString(formatRow("PPN (11%):", Order.formatRupiah(order.tax), width)))
        }
        out.write(CMD_BOLD_ON)
        out.write(encodeString(formatRow("TOTAL:", order.formattedTotal, width)))
        out.write(CMD_BOLD_OFF)

        out.write(encodeString(formatRow("Metode Bayar:", order.paymentMethod.label, width)))
        out.write(encodeString(formatRow("Bayar / Tunai:", Order.formatRupiah(order.paymentAmount), width)))
        if (order.change > 0) {
            out.write(encodeString(formatRow("Kembalian:", Order.formatRupiah(order.change), width)))
        }
        out.write(encodeString(separator))

        // Info Ekspedisi jika ada
        if (order.orderType == OrderType.SHIPPING && order.shippingInfo != null) {
            val ship = order.shippingInfo
            out.write(CMD_ALIGN_CENTER)
            out.write(CMD_BOLD_ON)
            out.write(encodeString("[ INFO PENGIRIMAN PAKET ]\r\n"))
            out.write(CMD_BOLD_OFF)
            out.write(CMD_ALIGN_LEFT)
            out.write(encodeString("Kurir : ${ship.courier} (${ship.service})\r\n"))
            out.write(encodeString("Resi  : ${ship.trackingNumber}\r\n"))
            out.write(encodeString("Kpd   : ${ship.recipientName} (${ship.recipientPhone})\r\n"))
            out.write(encodeString("Alamat: ${ship.recipientAddress}\r\n"))
            if (ship.isCOD) {
                out.write(CMD_BOLD_ON)
                out.write(encodeString("COD   : ${Order.formatRupiah(ship.codAmount)}\r\n"))
                out.write(CMD_BOLD_OFF)
            }
            out.write(encodeString(divider))
        }

        // Footer
        out.write(CMD_ALIGN_CENTER)
        out.write(encodeString(config.footerMessage + "\r\n"))
        out.write(encodeString("Powered by OmniPOS Android\r\n"))

        // Feed Lines
        val feed = maxOf(3, config.feedLinesBeforeCut)
        for (i in 0 until feed) {
            out.write(encodeString("\r\n"))
        }

        // Auto-Cut
        if (config.isAutoCutEnabled) {
            out.write(CMD_FEED_CUT)
            out.write(CMD_AUTO_CUT_PARTIAL)
        }

        return out.toByteArray()
    }

    // MARK: - 2. Build TSPL Label 4x6 Inci (100x150 mm)
    fun buildTSPLLabelData(order: Order, config: StoreConfig): ByteArray {
        val ship = order.shippingInfo ?: return ByteArray(0)
        val sb = StringBuilder()

        sb.append("SIZE 100 mm, 150 mm\r\n")
        sb.append("GAP 3 mm, 0 mm\r\n")
        sb.append("DIRECTION 1\r\n")
        sb.append("CLS\r\n")

        // Border luar
        sb.append("BOX 20,20,780,1180,4\r\n")

        // Header Kurir
        sb.append("BOX 20,20,780,120,3\r\n")
        sb.append("TEXT 40,45,\"4\",0,1,1,\"${ship.courier.uppercase()}\"\r\n")
        sb.append("TEXT 480,45,\"4\",0,1,1,\"[\${ship.service.uppercase()}\]\"\r\n")

        // Barcode Nomor Resi
        val tracking = if (ship.trackingNumber.isEmpty()) order.invoiceNumber else ship.trackingNumber
        sb.append("BARCODE 120,150,\"128\",100,1,0,3,6,\"$tracking\"\r\n")
        sb.append("TEXT 240,265,\"3\",0,1,1,\"NO RESI: $tracking\"\r\n")

        // Garis Pembatas
        sb.append("BOX 20,305,780,310,2\r\n")

        // Box COD
        if (ship.isCOD) {
            sb.append("BOX 480,320,760,400,3\r\n")
            sb.append("TEXT 500,335,\"4\",0,1,1,\"C O D\"\r\n")
            sb.append("TEXT 500,370,\"3\",0,1,1,\"${Order.formatRupiah(ship.codAmount)}\"\r\n")
        } else {
            sb.append("BOX 480,320,760,390,2\r\n")
            sb.append("TEXT 510,345,\"3\",0,1,1,\"NON-COD\"\r\n")
        }

        // Pengirim
        val sName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
        val sPhone = if (ship.senderPhone.isEmpty()) config.defaultSenderPhone else ship.senderPhone
        val sAddr = if (ship.senderAddress.isEmpty()) config.defaultSenderAddress else ship.senderAddress

        sb.append("TEXT 40,325,\"3\",0,1,1,\"PENGIRIM:\"\r\n")
        sb.append("TEXT 40,355,\"2\",0,1,1,\"$sName ($sPhone)\"\r\n")
        sb.append("TEXT 40,380,\"2\",0,1,1,\"${sAddr.take(45)}\"\r\n")

        // Garis Pembatas Penerima
        sb.append("BOX 20,415,780,420,2\r\n")
        sb.append("TEXT 40,435,\"4\",0,1,1,\"PENERIMA / TUJUAN:\"\r\n")
        sb.append("TEXT 40,480,\"3\",0,1,1,\"${ship.recipientName}\"\r\n")
        sb.append("TEXT 40,515,\"3\",0,1,1,\"Telp: ${ship.recipientPhone}\"\r\n")

        // Alamat Penerima
        val addr = ship.recipientAddress
        val line1 = addr.take(40)
        val line2 = if (addr.length > 40) addr.substring(40).take(40) else ""
        val line3 = if (addr.length > 80) addr.substring(80).take(40) else ""

        sb.append("TEXT 40,555,\"2\",0,1,1,\"$line1\"\r\n")
        if (line2.isNotEmpty()) sb.append("TEXT 40,580,\"2\",0,1,1,\"$line2\"\r\n")
        if (line3.isNotEmpty()) sb.append("TEXT 40,605,\"2\",0,1,1,\"$line3\"\r\n")
        if (ship.postalCode.isNotEmpty()) {
            sb.append("TEXT 40,630,\"3\",0,1,1,\"KODE POS: ${ship.postalCode}\"\r\n")
        }

        // Garis Pembatas Barang
        sb.append("BOX 20,670,780,675,2\r\n")
        sb.append("TEXT 40,690,\"3\",0,1,1,\"DETAIL BARANG:\"\r\n")
        sb.append("TEXT 480,690,\"3\",0,1,1,\"BERAT: ${String.format(\"%.1f\", ship.weightKg)} KG\"\r\n")

        var itemY = 725
        order.items.take(4).forEach { item ->
            sb.append("TEXT 50,$itemY,\"2\",0,1,1,\"- ${item.product.name} (x${item.quantity})\"\r\n")
            itemY += 28
        }

        // Instruksi
        sb.append("BOX 20,850,780,855,2\r\n")
        sb.append("TEXT 40,870,\"3\",0,1,1,\"INSTRUKSI: ${ship.notes.uppercase()}\"\r\n")

        // Tanda Tangan
        sb.append("BOX 20,930,780,935,2\r\n")
        sb.append("TEXT 60,950,\"2\",0,1,1,\"Tanda Tangan Kurir\"\r\n")
        sb.append("TEXT 480,950,\"2\",0,1,1,\"Tanda Tangan Penerima\"\r\n")
        sb.append("BOX 50,980,280,1080,1\r\n")
        sb.append("BOX 470,980,700,1080,1\r\n")

        // Footer
        sb.append("TEXT 40,1110,\"1\",0,1,1,\"Dicetak via OmniPOS Android - ${order.formattedDate}\"\r\n")
        sb.append("PRINT 1,1\r\n")

        return sb.toString().toByteArray(Charset.forName("UTF-8"))
    }

    // MARK: - 3. Build TSPL Sticker 58x40 mm (Stiker Thermal Tempel)
    fun buildTSPLSticker58x40Data(order: Order, config: StoreConfig): ByteArray {
        val ship = order.shippingInfo ?: return ByteArray(0)
        val sb = StringBuilder()

        sb.append("SIZE 58 mm, 40 mm\r\n")
        sb.append("GAP 2 mm, 0 mm\r\n")
        sb.append("DIRECTION 1\r\n")
        sb.append("CLS\r\n")

        // Border luar
        sb.append("BOX 10,10,454,310,2\r\n")

        // Header Kurir
        sb.append("TEXT 20,20,\"3\",0,1,1,\"${ship.courier.uppercase()}\"\r\n")
        sb.append("TEXT 280,20,\"3\",0,1,1,\"[\${ship.service.take(8).uppercase()}\]\"\r\n")
        sb.append("BOX 10,48,454,50,1\r\n")

        // Barcode Nomor Resi
        val tracking = if (ship.trackingNumber.isEmpty()) order.invoiceNumber else ship.trackingNumber
        sb.append("BARCODE 25,58,\"128\",40,1,0,2,3,\"$tracking\"\r\n")
        sb.append("TEXT 25,103,\"2\",0,1,1,\"RESI: $tracking\"\r\n")
        sb.append("BOX 10,125,454,127,1\r\n")

        // Penerima
        sb.append("TEXT 20,135,\"3\",0,1,1,\"Kpd: ${ship.recipientName.take(22)}\"\r\n")
        sb.append("TEXT 20,165,\"2\",0,1,1,\"Telp: ${ship.recipientPhone}\"\r\n")

        val rAddr = ship.recipientAddress
        val line1 = rAddr.take(32)
        val line2 = if (rAddr.length > 32) rAddr.substring(32).take(32) else ""

        sb.append("TEXT 20,190,\"2\",0,1,1,\"$line1\"\r\n")
        if (line2.isNotEmpty()) {
            sb.append("TEXT 20,212,\"2\",0,1,1,\"$line2\"\r\n")
        }
        if (ship.postalCode.isNotEmpty()) {
            sb.append("TEXT 20,235,\"2\",0,1,1,\"Pos: ${ship.postalCode}\"\r\n")
        }

        sb.append("BOX 10,260,454,262,1\r\n")

        // Footer COD & Pengirim
        if (ship.isCOD) {
            sb.append("TEXT 20,272,\"3\",0,1,1,\"COD: ${Order.formatRupiah(ship.codAmount)}\"\r\n")
        } else {
            sb.append("TEXT 20,272,\"3\",0,1,1,\"NON-COD\"\r\n")
        }

        val sName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
        sb.append("TEXT 250,275,\"2\",0,1,1,\"Dr: ${sName.take(12)}\"\r\n")

        sb.append("PRINT 1,1\r\n")
        return sb.toString().toByteArray(Charset.forName("UTF-8"))
    }

    // MARK: - 4. Build Slip Ekspedisi ESC/POS 58mm (Kertas Roll Thermal)
    fun buildShippingSlip58Data(order: Order, config: StoreConfig): ByteArray {
        val ship = order.shippingInfo ?: return ByteArray(0)
        val out = ByteArrayOutputStream()
        val width = 32
        val divider = "-".repeat(width) + "\r\n"
        val doubleDivider = "=".repeat(width) + "\r\n"

        out.write(CMD_INIT)
        out.write(CMD_ALIGN_CENTER)
        out.write(CMD_BOLD_ON)
        out.write(CMD_DOUBLE_HEIGHT)
        out.write(encodeString(ship.courier.uppercase() + "\r\n"))
        out.write(CMD_NORMAL_SIZE)
        out.write(encodeString("[${ship.service.uppercase()}]\r\n"))
        out.write(CMD_BOLD_OFF)
        out.write(encodeString(doubleDivider))

        val tracking = if (ship.trackingNumber.isEmpty()) order.invoiceNumber else ship.trackingNumber
        out.write(CMD_BOLD_ON)
        out.write(encodeString("NO RESI:\r\n$tracking\r\n"))
        out.write(CMD_BOLD_OFF)
        out.write(encodeString(divider))

        out.write(CMD_ALIGN_LEFT)
        out.write(CMD_BOLD_ON)
        out.write(encodeString("PENERIMA / TUJUAN:\r\n"))
        out.write(CMD_BOLD_OFF)
        out.write(CMD_BOLD_ON)
        out.write(encodeString(ship.recipientName + "\r\n"))
        out.write(CMD_BOLD_OFF)
        out.write(encodeString("Telp: " + ship.recipientPhone + "\r\n"))
        out.write(encodeString("Alamat:\r\n" + ship.recipientAddress + "\r\n"))
        if (ship.postalCode.isNotEmpty()) {
            out.write(encodeString("Kode Pos: " + ship.postalCode + "\r\n"))
        }
        out.write(encodeString(divider))

        val sName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
        val sPhone = if (ship.senderPhone.isEmpty()) config.defaultSenderPhone else ship.senderPhone
        out.write(encodeString("PENGIRIM: $sName ($sPhone)\r\n"))
        out.write(encodeString(divider))

        if (ship.isCOD) {
            out.write(CMD_ALIGN_CENTER)
            out.write(CMD_BOLD_ON)
            out.write(encodeString("*** COD: ${Order.formatRupiah(ship.codAmount)} ***\r\n"))
            out.write(CMD_BOLD_OFF)
        } else {
            out.write(CMD_ALIGN_CENTER)
            out.write(encodeString("*** NON-COD ***\r\n"))
        }

        out.write(encodeString(divider))
        out.write(encodeString("Dicetak via OmniPOS\r\n"))
        out.write(encodeString("\r\n\r\n\r\n\r\n"))

        if (config.isAutoCutEnabled) {
            out.write(CMD_FEED_CUT)
            out.write(CMD_AUTO_CUT_PARTIAL)
        }

        return out.toByteArray()
    }

    private fun formatRow(left: String, right: String, totalWidth: Int): String {
        val spaces = totalWidth - left.length - right.length
        return if (spaces > 0) {
            left + " ".repeat(spaces) + right + "\r\n"
        } else {
            val truncatedLeft = left.take(maxOf(0, totalWidth - right.length - 1))
            val pad = totalWidth - truncatedLeft.length - right.length
            truncatedLeft + " ".repeat(maxOf(1, pad)) + right + "\r\n"
        }
    }

    private fun encodeString(text: String): ByteArray {
        return try {
            text.toByteArray(Charset.forName("GBK"))
        } catch (e: Exception) {
            text.toByteArray(Charset.forName("ISO-8859-1"))
        }
    }
}
