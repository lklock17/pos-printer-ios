package com.lklock17.omnipos.services

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.OrderType
import com.lklock17.omnipos.models.Product
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
        val serviceUpper = ship.service.uppercase()
        sb.append("TEXT 480,45,\"4\",0,1,1,\"[$serviceUpper]\"\r\n")

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
        val weightStr = String.format(java.util.Locale.US, "%.1f", ship.weightKg)
        sb.append("TEXT 480,690,\"3\",0,1,1,\"BERAT: $weightStr KG\"\r\n")

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

    // MARK: - 3a. Build ESC/POS Raster Sticker 55x30 mm (Stiker Thermal Mini Kassen MT200A)
    // Menggunakan ESC/POS Raster murni (GS v 0) dengan step pitch 264 dots (33mm) & 284 dots tear-off feed
    fun buildTSPLSticker55x30Data(order: Order, config: StoreConfig): ByteArray {
        val bitmap = renderShippingSticker55x30Bitmap(order, config)
        val labelRaster = bitmapToEscPosRaster(bitmap)
        val out = ByteArrayOutputStream()
        out.write(CMD_INIT)
        out.write(labelRaster)
        out.write(createBlankEscPosRaster(384, 284)) // 1 stiker penutup untuk pisau sobek
        return out.toByteArray()
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
        val serviceShort = ship.service.take(8).uppercase()
        sb.append("TEXT 280,20,\"3\",0,1,1,\"[$serviceShort]\"\r\n")
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

    // MARK: - 5. Build Barcode Tag Stiker Produk 55x30 mm (ESC/POS Raster Murni Kassen MT200A)
    // Kalibrasi Presisi: 264 dots per label (33.0 mm) + 284 dots sobekan penutup, Zero Drift!
    private val CODE128_PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    )

    fun renderProductBarcodeTag55x30Bitmap(
        storeName: String,
        productName: String,
        barcode: String,
        price: Double
    ): Bitmap {
        val width = 384
        val height = 264 // 264 dots = 33.0 mm (30mm stiker + 3mm gap)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }

        // 1. Nama Toko (y=16)
        paintText.textSize = 18f
        val storeUpper = storeName.ifEmpty { "OMNIPOS STORE" }.uppercase()
        val textBounds = Rect()
        paintText.getTextBounds(storeUpper, 0, storeUpper.length, textBounds)
        val storeX = (width - textBounds.width()) / 2f
        canvas.drawText(storeUpper, storeX, 26f, paintText)
        canvas.drawLine(10f, 34f, (width - 11).toFloat(), 34f, paintLine)

        // 2. Nama Produk (y=38)
        paintText.textSize = 22f
        val dispName = productName.take(24)
        paintText.getTextBounds(dispName, 0, dispName.length, textBounds)
        val prodX = (width - textBounds.width()) / 2f
        canvas.drawText(dispName, prodX, 56f, paintText)

        // 3. Barcode Code 128 (y=64, h=44)
        val cleanBarcode = barcode.filter { it.code in 32..126 }.ifEmpty { "00000000" }
        val codes = mutableListOf<Int>(104) // Start B
        var csum = 104
        cleanBarcode.forEachIndexed { i, ch ->
            val v = ch.code - 32
            codes.add(v)
            csum += v * (i + 1)
        }
        codes.add(csum % 103)
        codes.add(106) // Stop

        val pattern = codes.joinToString("") { CODE128_PATTERNS[it] }
        var moduleW = 2
        var barTotalW = pattern.sumOf { it.digitToInt() } * moduleW
        if (barTotalW > width - 24) {
            moduleW = 1
            barTotalW = pattern.sumOf { it.digitToInt() } * moduleW
        }
        val startX = maxOf(10, (width - barTotalW) / 2)
        var curX = startX
        val barY = 64
        val barH = 44
        val paintBar = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        pattern.forEachIndexed { _, ch ->
            val w = ch.digitToInt() * moduleW
            if (ch.code % 2 == 0) { // Alternating bars in Code 128 pattern string
                // Wait: pattern is '212222', even indices (0, 2, 4) are bars, odd (1, 3, 5) are spaces!
            }
        }
        for (i in pattern.indices) {
            val w = pattern[i].digitToInt() * moduleW
            if (i % 2 == 0) { // Genap = garis hitam, ganjil = spasi putih
                canvas.drawRect(curX.toFloat(), barY.toFloat(), (curX + w).toFloat(), (barY + barH).toFloat(), paintBar)
            }
            curX += w
        }

        // 4. Angka Barcode (y=112)
        paintText.textSize = 18f
        paintText.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        paintText.getTextBounds(cleanBarcode, 0, cleanBarcode.length, textBounds)
        val codeX = (width - textBounds.width()) / 2f
        canvas.drawText(cleanBarcode, codeX, 126f, paintText)
        canvas.drawLine(10f, 132f, (width - 11).toFloat(), 132f, paintLine)

        // 5. Kotak Harga Mencolok (y=138 s/d 204)
        val paintBox = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(14f, 138f, (width - 15).toFloat(), 204f, paintBox)

        paintText.textSize = 32f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val priceStr = Order.formatRupiah(price)
        paintText.getTextBounds(priceStr, 0, priceStr.length, textBounds)
        val priceX = (width - textBounds.width()) / 2f
        val priceY = 138f + (66f + textBounds.height()) / 2f - 2f
        canvas.drawText(priceStr, priceX, priceY, paintText)

        // y=205..264 adalah area margin bawah stiker dan gap fisik 3mm
        return bitmap
    }

    fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val bytesPerRow = (w + 7) / 8
        val out = ByteArrayOutputStream()

        val xL = (bytesPerRow and 0xFF).toByte()
        val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
        val yL = (h and 0xFF).toByte()
        val yH = ((h shr 8) and 0xFF).toByte()
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in 0 until h) {
            for (byteIdx in 0 until bytesPerRow) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = byteIdx * 8 + bit
                    if (x < w) {
                        val color = pixels[y * w + x]
                        val red = (color shr 16) and 0xFF
                        val green = (color shr 8) and 0xFF
                        val blue = color and 0xFF
                        val gray = (red * 299 + green * 587 + blue * 114) / 1000
                        if (gray < 128) {
                            b = b or (1 shl (7 - bit))
                        }
                    }
                }
                out.write(b)
            }
        }
        return out.toByteArray()
    }

    fun createBlankEscPosRaster(width: Int = 384, height: Int = 284): ByteArray {
        val bytesPerRow = (width + 7) / 8
        val out = ByteArrayOutputStream()
        val xL = (bytesPerRow and 0xFF).toByte()
        val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()
        out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))
        val blankRow = ByteArray(bytesPerRow)
        for (y in 0 until height) {
            out.write(blankRow)
        }
        return out.toByteArray()
    }

    // MARK: - 3b. Render Shipping Sticker 55x30 Bitmap (Pure Graphical ESC/POS Raster)
    fun renderShippingSticker55x30Bitmap(order: Order, config: StoreConfig): Bitmap {
        val ship = order.shippingInfo ?: ShippingInfo(recipientName = "Customer", recipientAddress = "-", recipientPhone = "-")
        val width = 384
        val height = 264 // 264 dots = 33.0 mm
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val paintLine = Paint().apply {
            color = Color.BLACK
            strokeWidth = 1f
        }

        // 1. Header: Kurir & Layanan
        paintText.textSize = 20f
        val courierHeader = "${ship.courier.uppercase()} [${ship.service.take(8).uppercase()}]"
        canvas.drawText(courierHeader, 12f, 26f, paintText)
        if (ship.isCOD) {
            paintText.textSize = 18f
            canvas.drawText("COD", 310f, 26f, paintText)
        }
        canvas.drawLine(10f, 34f, (width - 11).toFloat(), 34f, paintLine)

        // 2. Barcode Resi (y=40..84)
        val tracking = if (ship.trackingNumber.isEmpty()) order.invoiceNumber else ship.trackingNumber
        val cleanTracking = tracking.filter { it.code in 32..126 }.ifEmpty { "00000000" }
        val codes = mutableListOf<Int>(104)
        var csum = 104
        cleanTracking.forEachIndexed { i, ch ->
            val v = ch.code - 32
            codes.add(v)
            csum += v * (i + 1)
        }
        codes.add(csum % 103)
        codes.add(106)
        val pattern = codes.joinToString("") { CODE128_PATTERNS[it] }
        var moduleW = 2
        var barTotalW = pattern.sumOf { it.digitToInt() } * moduleW
        if (barTotalW > width - 24) {
            moduleW = 1
            barTotalW = pattern.sumOf { it.digitToInt() } * moduleW
        }
        val startX = maxOf(10, (width - barTotalW) / 2)
        var curX = startX
        val barY = 40
        val barH = 44
        val paintBar = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        for (i in pattern.indices) {
            val w = pattern[i].digitToInt() * moduleW
            if (i % 2 == 0) {
                canvas.drawRect(curX.toFloat(), barY.toFloat(), (curX + w).toFloat(), (barY + barH).toFloat(), paintBar)
            }
            curX += w
        }

        // 3. Nomor Resi (y=98)
        paintText.textSize = 17f
        paintText.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        val resiText = "RESI: $cleanTracking"
        val textBounds = Rect()
        paintText.getTextBounds(resiText, 0, resiText.length, textBounds)
        canvas.drawText(resiText, (width - textBounds.width()) / 2f, 100f, paintText)
        canvas.drawLine(10f, 108f, (width - 11).toFloat(), 108f, paintLine)

        // 4. Penerima (y=126)
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paintText.textSize = 15f
        canvas.drawText("Kpd: ${ship.recipientName.take(24)} (${ship.recipientPhone})", 12f, 126f, paintText)

        paintText.textSize = 13f
        paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val rAddr = ship.recipientAddress
        val line1 = rAddr.take(34)
        val line2 = if (rAddr.length > 34) rAddr.substring(34).take(34) else ""
        canvas.drawText(line1, 12f, 144f, paintText)
        if (line2.isNotEmpty()) {
            canvas.drawText(line2, 12f, 160f, paintText)
        }

        // 5. Footer: Pengirim & COD
        canvas.drawLine(10f, 172f, (width - 11).toFloat(), 172f, paintLine)
        paintText.textSize = 13f
        val sName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
        canvas.drawText("Dr: ${sName.take(18)}", 12f, 192f, paintText)
        if (ship.isCOD) {
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            val codText = "COD: ${Order.formatRupiah(ship.codAmount)}"
            paintText.getTextBounds(codText, 0, codText.length, textBounds)
            canvas.drawText(codText, (width - 12f - textBounds.width()), 192f, paintText)
        }

        return bitmap
    }

    fun buildSingleProductBarcodeTag55x30Raster(
        product: Product,
        storeName: String
    ): ByteArray {
        val bitmap = renderProductBarcodeTag55x30Bitmap(
            storeName = storeName,
            productName = product.name,
            barcode = if (product.barcode.isNotEmpty()) product.barcode else product.sku,
            price = product.price
        )
        return bitmapToEscPosRaster(bitmap)
    }

    fun buildProductBarcodeTag55x30Raster(
        product: Product,
        storeName: String,
        quantity: Int = 1
    ): ByteArray {
        val safeQty = quantity.coerceIn(1, 10)
        val out = ByteArrayOutputStream()
        // Reset printer 1x di awal
        out.write(CMD_INIT)

        val bitmap = renderProductBarcodeTag55x30Bitmap(
            storeName = storeName,
            productName = product.name,
            barcode = if (product.barcode.isNotEmpty()) product.barcode else product.sku,
            price = product.price
        )
        val labelRaster = bitmapToEscPosRaster(bitmap)

        for (i in 0 until safeQty) {
            out.write(labelRaster)
        }

        // 1 stiker penutup kosong (284 dots / 35.5 mm) untuk pendorongan ke pisau sobek
        out.write(createBlankEscPosRaster(384, 284))

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

