import Foundation
import UIKit
import SwiftUI

public class PrinterEngine {
    // ESC/POS Command Constants
    public static let ESC: [UInt8] = [0x1B]
    public static let GS: [UInt8]  = [0x1D]

    public static let cmdInit: [UInt8]         = [0x1B, 0x40]            // Inisialisasi printer
    public static let cmdAlignLeft: [UInt8]    = [0x1B, 0x61, 0x00]      // Rata kiri
    public static let cmdAlignCenter: [UInt8]  = [0x1B, 0x61, 0x01]      // Rata tengah
    public static let cmdAlignRight: [UInt8]   = [0x1B, 0x61, 0x02]      // Rata kanan
    public static let cmdBoldOn: [UInt8]       = [0x1B, 0x45, 0x01]      // Bold ON
    public static let cmdBoldOff: [UInt8]      = [0x1B, 0x45, 0x00]      // Bold OFF
    public static let cmdDoubleHeight: [UInt8] = [0x1B, 0x21, 0x10]      // Double height
    public static let cmdNormalSize: [UInt8]   = [0x1B, 0x21, 0x00]      // Ukuran normal
    public static let cmdFeed5: [UInt8]        = [0x1B, 0x64, 0x05]      // Feed 5 baris
    public static let cmdAutoCut: [UInt8]      = [0x1D, 0x56, 0x42, 0x00] // GS V 66 0 (Cut with feed)
    public static let cmdAutoCutFull: [UInt8]  = [0x1D, 0x56, 0x00]      // GS V 0 (Full Cut)

    // MARK: - Format 2 Kolom (Rata Kiri & Kanan)
    public static func formatRow(left: String, right: String, width: Int = 32) -> String {
        let l = String(left)
        let r = String(right)
        let spaceCount = width - l.count - r.count
        if spaceCount < 1 {
            let allowed = max(1, width - r.count - 1)
            let trimmedLeft = String(l.prefix(allowed))
            return trimmedLeft + " " + r + "\r\n"
        }
        return l + String(repeating: " ", count: spaceCount) + r + "\r\n"
    }

    // MARK: - 1. Build Data Struk Kasir Thermal (58mm / 80mm)
    public static func buildReceiptData(order: Order, config: StoreConfig) -> Data {
        var data = Data()
        let width = config.paperWidth // 32 untuk 58mm, 48 untuk 80mm
        let separator = String(repeating: "=", count: width) + "\r\n"
        let divider = String(repeating: "-", count: width) + "\r\n"

        // 1. Reset
        data.append(contentsOf: cmdInit)

        // 2. Header Toko
        data.append(contentsOf: cmdAlignCenter)
        data.append(contentsOf: cmdBoldOn)
        data.append(contentsOf: cmdDoubleHeight)
        data.append(encodeString(config.storeName + "\r\n"))
        data.append(contentsOf: cmdNormalSize)
        data.append(contentsOf: cmdBoldOff)

        if !config.storeTagline.isEmpty {
            data.append(encodeString(config.storeTagline + "\r\n"))
        }
        if !config.storeAddress.isEmpty {
            data.append(encodeString(config.storeAddress + "\r\n"))
        }
        if !config.storePhone.isEmpty {
            data.append(encodeString("Telp/WA: " + config.storePhone + "\r\n"))
        }
        data.append(encodeString(separator))

        // 3. Info Transaksi
        data.append(contentsOf: cmdAlignLeft)
        data.append(encodeString(formatRow(left: "No. Struk:", right: order.invoiceNumber, width: width)))
        data.append(encodeString(formatRow(left: "Waktu:", right: order.formattedDate, width: width)))
        data.append(encodeString(formatRow(left: "Tipe:", right: order.orderType.rawValue, width: width)))
        data.append(encodeString(divider))

        // 4. Daftar Produk
        for item in order.items {
            let itemTitle = item.product.name
            let qtyPrice = "\(item.quantity)x @\(CurrencyHelper.formatPlain(item.product.price))"
            let subtotalStr = CurrencyHelper.formatPlain(item.subtotal)

            data.append(contentsOf: cmdBoldOn)
            data.append(encodeString(itemTitle + "\r\n"))
            data.append(contentsOf: cmdBoldOff)
            data.append(encodeString(formatRow(left: "  " + qtyPrice, right: subtotalStr, width: width)))
            if item.itemDiscount > 0 {
                data.append(encodeString(formatRow(left: "  (Diskon)", right: "-\(CurrencyHelper.formatPlain(item.itemDiscount))", width: width)))
            }
            if !item.notes.isEmpty {
                data.append(encodeString("  * \(item.notes)\r\n"))
            }
        }
        data.append(encodeString(divider))

        // 5. Total & Pembayaran
        data.append(encodeString(formatRow(left: "Subtotal:", right: CurrencyHelper.formatPlain(order.subtotal), width: width)))
        if order.discount > 0 {
            data.append(encodeString(formatRow(left: "Diskon:", right: "-\(CurrencyHelper.formatPlain(order.discount))", width: width)))
        }
        if order.tax > 0 {
            data.append(encodeString(formatRow(left: "PPN (11%):", right: CurrencyHelper.formatPlain(order.tax), width: width)))
        }
        data.append(contentsOf: cmdBoldOn)
        data.append(encodeString(formatRow(left: "TOTAL AKHIR:", right: order.formattedTotal, width: width)))
        data.append(contentsOf: cmdBoldOff)

        data.append(encodeString(formatRow(left: "Metode Bayar:", right: order.paymentMethod.rawValue, width: width)))
        data.append(encodeString(formatRow(left: "Bayar / Tunai:", right: CurrencyHelper.format(order.paymentAmount), width: width)))
        if order.change > 0 {
            data.append(encodeString(formatRow(left: "Kembalian:", right: CurrencyHelper.format(order.change), width: width)))
        }
        data.append(encodeString(separator))

        // 6. Jika Pesanan Pengiriman, tampilkan info ekspedisi di bawah
        if order.orderType == .shipping, let ship = order.shippingInfo {
            data.append(contentsOf: cmdAlignCenter)
            data.append(contentsOf: cmdBoldOn)
            data.append(encodeString("[ INFO PENGIRIMAN PAKET ]\r\n"))
            data.append(contentsOf: cmdBoldOff)
            data.append(contentsOf: cmdAlignLeft)
            data.append(encodeString("Kurir : \(ship.courier) (\(ship.service))\r\n"))
            data.append(encodeString("Resi  : \(ship.trackingNumber)\r\n"))
            data.append(encodeString("Kpd   : \(ship.recipientName) (\(ship.recipientPhone))\r\n"))
            data.append(encodeString("Alamat: \(ship.recipientAddress)\r\n"))
            if ship.isCOD {
                data.append(contentsOf: cmdBoldOn)
                data.append(encodeString("Status: COD (\(CurrencyHelper.format(ship.codAmount)))\r\n"))
                data.append(contentsOf: cmdBoldOff)
            }
            data.append(encodeString(divider))
        }

        // 7. Footer
        data.append(contentsOf: cmdAlignCenter)
        data.append(encodeString(config.footerMessage + "\r\n"))
        data.append(encodeString("Powered by OmniPOS Universal\r\n"))

        // 8. Feed Kertas
        let feedCount = max(3, config.feedLinesBeforeCut)
        for _ in 0..<feedCount {
            data.append(encodeString("\r\n"))
        }

        // 9. Auto-Cut
        if config.isAutoCutEnabled {
            data.append(contentsOf: cmdAutoCut)
            data.append(contentsOf: cmdAutoCutFull)
        }

        return data
    }

    // MARK: - 2. Build Data Label Pengiriman 4x6 Inci (TSPL Command untuk Printer Label)
    public static func buildTSPLLabelData(order: Order, config: StoreConfig) -> Data {
        guard let ship = order.shippingInfo else { return Data() }

        var tspl = ""
        // Ukuran 4x6 inci (100mm x 150mm), 203 DPI, gap 3mm
        tspl += "SIZE 100 mm, 150 mm\r\n"
        tspl += "GAP 3 mm, 0 mm\r\n"
        tspl += "DIRECTION 1\r\n"
        tspl += "CLS\r\n"

        // Border luar label
        tspl += "BOX 20,20,780,1180,4\r\n"

        // Header Kurir & Service
        tspl += "BOX 20,20,780,120,3\r\n"
        tspl += "TEXT 40,45,\"4\",0,1,1,\"\(ship.courier.uppercased())\"\r\n"
        tspl += "TEXT 480,45,\"4\",0,1,1,\"[\(ship.service.uppercased())]\"\r\n"

        // Barcode Nomor Resi (Code 128)
        let tracking = ship.trackingNumber.isEmpty ? order.invoiceNumber : ship.trackingNumber
        tspl += "BARCODE 120,150,\"128\",100,1,0,3,6,\"\(tracking)\"\r\n"
        tspl += "TEXT 240,265,\"3\",0,1,1,\"NO RESI: \(tracking)\"\r\n"

        // Garis Pembatas Header Resi
        tspl += "BOX 20,305,780,310,2\r\n"

        // Box COD / NON-COD
        if ship.isCOD {
            tspl += "BOX 480,320,760,400,3\r\n"
            tspl += "TEXT 500,335,\"4\",0,1,1,\"C O D\"\r\n"
            tspl += "TEXT 500,370,\"3\",0,1,1,\"\(CurrencyHelper.format(ship.codAmount))\"\r\n"
        } else {
            tspl += "BOX 480,320,760,390,2\r\n"
            tspl += "TEXT 510,345,\"3\",0,1,1,\"NON-COD\"\r\n"
        }

        // Informasi Pengirim (Kiri)
        let sName = ship.senderName.isEmpty ? config.defaultSenderName : ship.senderName
        let sPhone = ship.senderPhone.isEmpty ? config.defaultSenderPhone : ship.senderPhone
        let sAddr = ship.senderAddress.isEmpty ? config.defaultSenderAddress : ship.senderAddress

        tspl += "TEXT 40,325,\"3\",0,1,1,\"PENGIRIM:\"\r\n"
        tspl += "TEXT 40,355,\"2\",0,1,1,\"\(sName) (\(sPhone))\"\r\n"
        tspl += "TEXT 40,380,\"2\",0,1,1,\"\(String(sAddr.prefix(45)))\"\r\n"

        // Garis Pembatas Pengirim - Penerima
        tspl += "BOX 20,415,780,420,2\r\n"

        // Informasi Penerima (Sangat Menonjol / Besar)
        tspl += "TEXT 40,435,\"4\",0,1,1,\"PENERIMA / TUJUAN:\"\r\n"
        tspl += "TEXT 40,480,\"3\",0,1,1,\"\(ship.recipientName)\"\r\n"
        tspl += "TEXT 40,515,\"3\",0,1,1,\"Telp: \(ship.recipientPhone)\"\r\n"
        
        // Alamat Penerima (Maksimal 3 baris)
        let rAddr = ship.recipientAddress
        let line1 = String(rAddr.prefix(40))
        let remaining = rAddr.count > 40 ? String(rAddr.dropFirst(40)) : ""
        let line2 = String(remaining.prefix(40))
        let line3 = remaining.count > 40 ? String(remaining.dropFirst(40).prefix(40)) : ""

        tspl += "TEXT 40,555,\"2\",0,1,1,\"\(line1)\"\r\n"
        if !line2.isEmpty { tspl += "TEXT 40,580,\"2\",0,1,1,\"\(line2)\"\r\n" }
        if !line3.isEmpty { tspl += "TEXT 40,605,\"2\",0,1,1,\"\(line3)\"\r\n" }
        if !ship.postalCode.isEmpty {
            tspl += "TEXT 40,630,\"3\",0,1,1,\"KODE POS: \(ship.postalCode)\"\r\n"
        }

        // Garis Pembatas Isi Paket
        tspl += "BOX 20,670,780,675,2\r\n"
        tspl += "TEXT 40,690,\"3\",0,1,1,\"DETAIL BARANG:\"\r\n"
        tspl += "TEXT 480,690,\"3\",0,1,1,\"BERAT: \(String(format: "%.1f", ship.weightKg)) KG\"\r\n"

        var itemY = 725
        for item in order.items.prefix(4) {
            tspl += "TEXT 50,\(itemY),\"2\",0,1,1,\"- \(item.product.name) (x\(item.quantity))\"\r\n"
            itemY += 28
        }

        // Catatan / Instruksi Khusus
        tspl += "BOX 20,850,780,855,2\r\n"
        tspl += "TEXT 40,870,\"3\",0,1,1,\"INSTRUKSI: \(ship.notes.uppercased())\"\r\n"

        // Tanda Tangan / Serah Terima Kurir
        tspl += "BOX 20,930,780,935,2\r\n"
        tspl += "TEXT 60,950,\"2\",0,1,1,\"Tanda Tangan Kurir\"\r\n"
        tspl += "TEXT 480,950,\"2\",0,1,1,\"Tanda Tangan Penerima\"\r\n"
        tspl += "BOX 50,980,280,1080,1\r\n"
        tspl += "BOX 470,980,700,1080,1\r\n"

        // Footer Resi
        tspl += "TEXT 40,1110,\"1\",0,1,1,\"Dicetak via OmniPOS Universal System - \(order.formattedDate)\"\r\n"
        tspl += "PRINT 1,1\r\n"

        return tspl.data(using: .utf8) ?? Data()
    }

    // MARK: - 3a. Build Data Stiker Thermal 55x30 mm (TSPL Command untuk Printer Kassen MT200A)
    public static func buildTSPLSticker55x30Data(order: Order, config: StoreConfig) -> Data {
        guard let ship = order.shippingInfo else { return Data() }

        var tspl = ""
        // Ukuran stiker 55mm x 30mm, 203 DPI (8 dots/mm) -> 440 x 240 dots, gap 3mm
        tspl += "SIZE 55 mm, 30 mm\r\n"
        tspl += "GAP 3 mm, 0 mm\r\n"
        tspl += "DIRECTION 1\r\n"
        tspl += "CLS\r\n"

        // Border luar stiker (440x240 dots)
        tspl += "BOX 6,6,434,234,2\r\n"

        // Header: Kurir & Layanan & COD
        tspl += "TEXT 12,10,\"3\",0,1,1,\"\(ship.courier.uppercased())\"\r\n"
        let srvShort = ship.service.prefix(7).uppercased()
        tspl += "TEXT 230,12,\"2\",0,1,1,\"[\(srvShort)]\"\r\n"
        if ship.isCOD {
            tspl += "TEXT 330,10,\"3\",0,1,1,\"COD\"\r\n"
        } else {
            tspl += "TEXT 320,12,\"2\",0,1,1,\"NON-COD\"\r\n"
        }
        tspl += "BOX 6,32,434,34,1\r\n"

        // Barcode Nomor Resi (Code 128)
        let tracking = ship.trackingNumber.isEmpty ? order.invoiceNumber : ship.trackingNumber
        tspl += "BARCODE 12,38,\"128\",32,1,0,2,2,\"\(tracking)\"\r\n"
        tspl += "TEXT 12,73,\"2\",0,1,1,\"RESI: \(tracking)\"\r\n"
        tspl += "BOX 6,92,434,94,1\r\n"

        // Penerima (Tujuan Utama Pengiriman)
        tspl += "TEXT 12,98,\"3\",0,1,1,\"Kpd: \(String(ship.recipientName.prefix(20)))\"\r\n"
        tspl += "TEXT 12,124,\"2\",0,1,1,\"Telp: \(ship.recipientPhone)\"\r\n"

        // Alamat ringkas
        let rAddr = ship.recipientAddress
        let line1 = String(rAddr.prefix(32))
        let remaining = rAddr.count > 32 ? String(rAddr.dropFirst(32)) : ""
        let line2 = String(remaining.prefix(32))

        tspl += "TEXT 12,144,\"2\",0,1,1,\"\(line1)\"\r\n"
        if !line2.isEmpty {
            tspl += "TEXT 12,164,\"2\",0,1,1,\"\(line2)\"\r\n"
        }
        tspl += "BOX 6,186,434,188,1\r\n"

        // Footer COD & Pengirim
        let sName = ship.senderName.isEmpty ? config.defaultSenderName : ship.senderName
        if ship.isCOD {
            tspl += "TEXT 12,192,\"2\",0,1,1,\"COD: \(CurrencyHelper.format(ship.codAmount))\"\r\n"
            tspl += "TEXT 12,212,\"1\",0,1,1,\"Dr: \(String(sName.prefix(16))) | \(ship.postalCode)\"\r\n"
        } else {
            tspl += "TEXT 12,192,\"2\",0,1,1,\"Dr: \(String(sName.prefix(16)))\"\r\n"
            tspl += "TEXT 12,212,\"1\",0,1,1,\"Pos: \(ship.postalCode) | OmniPOS\"\r\n"
        }

        tspl += "PRINT 1,1\r\n"
        return tspl.data(using: .utf8) ?? Data()
    }

    // MARK: - 3. Build Data Stiker Thermal 58x40 mm (TSPL Command untuk Printer Stiker Kecil / Tempel)
    public static func buildTSPLSticker58x40Data(order: Order, config: StoreConfig) -> Data {
        guard let ship = order.shippingInfo else { return Data() }

        var tspl = ""
        // Ukuran stiker 58mm x 40mm, 203 DPI (8 dots/mm) -> 464 x 320 dots, gap 2mm
        tspl += "SIZE 58 mm, 40 mm\r\n"
        tspl += "GAP 2 mm, 0 mm\r\n"
        tspl += "DIRECTION 1\r\n"
        tspl += "CLS\r\n"

        // Border luar stiker
        tspl += "BOX 10,10,454,310,2\r\n"

        // Header: Kurir & Layanan
        tspl += "TEXT 20,20,\"3\",0,1,1,\"\(ship.courier.uppercased())\"\r\n"
        tspl += "TEXT 280,20,\"3\",0,1,1,\"[\(ship.service.prefix(8).uppercased())]\"\r\n"
        tspl += "BOX 10,48,454,50,1\r\n"

        // Barcode Nomor Resi (Code 128)
        let tracking = ship.trackingNumber.isEmpty ? order.invoiceNumber : ship.trackingNumber
        tspl += "BARCODE 25,58,\"128\",40,1,0,2,3,\"\(tracking)\"\r\n"
        tspl += "TEXT 25,103,\"2\",0,1,1,\"RESI: \(tracking)\"\r\n"
        tspl += "BOX 10,125,454,127,1\r\n"

        // Penerima (Tujuan Utama Pengiriman)
        tspl += "TEXT 20,135,\"3\",0,1,1,\"Kpd: \(String(ship.recipientName.prefix(22)))\"\r\n"
        tspl += "TEXT 20,165,\"2\",0,1,1,\"Telp: \(ship.recipientPhone)\"\r\n"

        // Potong alamat jadi 2 baris pendek agar pas di stiker 58x40
        let rAddr = ship.recipientAddress
        let line1 = String(rAddr.prefix(32))
        let remaining = rAddr.count > 32 ? String(rAddr.dropFirst(32)) : ""
        let line2 = String(remaining.prefix(32))

        tspl += "TEXT 20,190,\"2\",0,1,1,\"\(line1)\"\r\n"
        if !line2.isEmpty {
            tspl += "TEXT 20,212,\"2\",0,1,1,\"\(line2)\"\r\n"
        }
        if !ship.postalCode.isEmpty {
            tspl += "TEXT 20,235,\"2\",0,1,1,\"Pos: \(ship.postalCode)\"\r\n"
        }

        // Garis Pembatas Bawah
        tspl += "BOX 10,260,454,262,1\r\n"

        // Footer: COD Status & Pengirim
        if ship.isCOD {
            tspl += "TEXT 20,272,\"3\",0,1,1,\"COD: \(CurrencyHelper.format(ship.codAmount))\"\r\n"
        } else {
            tspl += "TEXT 20,272,\"3\",0,1,1,\"NON-COD\"\r\n"
        }

        let sName = ship.senderName.isEmpty ? config.defaultSenderName : ship.senderName
        tspl += "TEXT 250,275,\"2\",0,1,1,\"Dr: \(String(sName.prefix(12)))\"\r\n"

        tspl += "PRINT 1,1\r\n"
        return tspl.data(using: .utf8) ?? Data()
    }

    // MARK: - 4. Build Slip Ekspedisi ESC/POS 58mm (Kertas Roll Thermal Standar)
    public static func buildShippingSlip58Data(order: Order, config: StoreConfig) -> Data {
        guard let ship = order.shippingInfo else { return Data() }
        var data = Data()

        let cmdInit: [UInt8] = [0x1B, 0x40]
        let cmdAlignCenter: [UInt8] = [0x1B, 0x61, 0x01]
        let cmdAlignLeft: [UInt8] = [0x1B, 0x61, 0x00]
        let cmdBoldOn: [UInt8] = [0x1B, 0x45, 0x01]
        let cmdBoldOff: [UInt8] = [0x1B, 0x45, 0x00]
        let cmdDoubleHeight: [UInt8] = [0x1B, 0x21, 0x10]
        let cmdNormalSize: [UInt8] = [0x1B, 0x21, 0x00]
        let cmdAutoCut: [UInt8] = [0x1D, 0x56, 0x42, 0x00]

        let width = 32
        let divider = String(repeating: "-", count: width) + "\r\n"
        let doubleDivider = String(repeating: "=", count: width) + "\r\n"

        data.append(contentsOf: cmdInit)
        data.append(contentsOf: cmdAlignCenter)
        data.append(contentsOf: cmdBoldOn)
        data.append(contentsOf: cmdDoubleHeight)
        data.append(encodeString(ship.courier.uppercased() + "\r\n"))
        data.append(contentsOf: cmdNormalSize)
        data.append(encodeString("[\(ship.service.uppercased())]\r\n"))
        data.append(contentsOf: cmdBoldOff)
        data.append(encodeString(doubleDivider))

        let tracking = ship.trackingNumber.isEmpty ? order.invoiceNumber : ship.trackingNumber
        data.append(contentsOf: cmdBoldOn)
        data.append(encodeString("NO RESI:\r\n\(tracking)\r\n"))
        data.append(contentsOf: cmdBoldOff)
        data.append(encodeString(divider))

        data.append(contentsOf: cmdAlignLeft)
        data.append(contentsOf: cmdBoldOn)
        data.append(encodeString("PENERIMA / TUJUAN:\r\n"))
        data.append(contentsOf: cmdBoldOff)
        data.append(contentsOf: cmdBoldOn)
        data.append(encodeString("\(ship.recipientName)\r\n"))
        data.append(contentsOf: cmdBoldOff)
        data.append(encodeString("Telp: \(ship.recipientPhone)\r\n"))
        data.append(encodeString("Alamat:\r\n\(ship.recipientAddress)\r\n"))
        if !ship.postalCode.isEmpty {
            data.append(encodeString("Kode Pos: \(ship.postalCode)\r\n"))
        }
        data.append(encodeString(divider))

        let sName = ship.senderName.isEmpty ? config.defaultSenderName : ship.senderName
        let sPhone = ship.senderPhone.isEmpty ? config.defaultSenderPhone : ship.senderPhone
        data.append(encodeString("PENGIRIM: \(sName) (\(sPhone))\r\n"))
        data.append(encodeString(divider))

        if ship.isCOD {
            data.append(contentsOf: cmdAlignCenter)
            data.append(contentsOf: cmdBoldOn)
            data.append(encodeString("*** COD: \(CurrencyHelper.format(ship.codAmount)) ***\r\n"))
            data.append(contentsOf: cmdBoldOff)
        } else {
            data.append(contentsOf: cmdAlignCenter)
            data.append(encodeString("*** NON-COD ***\r\n"))
        }

        data.append(encodeString(divider))
        data.append(encodeString("Dicetak via OmniPOS\r\n"))

        // Feed & Cut
        data.append(encodeString("\r\n\r\n\r\n\r\n"))
        if config.isAutoCutEnabled {
            data.append(contentsOf: cmdAutoCut)
        }
        return data
    }

    // MARK: - 5. Build Barcode Tag Stiker Produk 55x30 mm (ESC/POS Raster Murni Kassen MT200A)
    // Kalibrasi Presisi: 264 dots per label (33.0 mm) + 284 dots sobekan penutup, Zero Drift!
    private static let code128Patterns: [String] = [
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
    ]

    public static func renderProductBarcodeTag55x30Raster(
        storeName: String,
        productName: String,
        barcode: String,
        price: Double
    ) -> Data {
        let width = 384
        let height = 264 // 264 dots = 33.0 mm (30mm stiker + 3mm gap)
        let size = CGSize(width: width, height: height)

        // Gunakan UIGraphicsBeginImageContextWithOptions dengan scale 1.0 murni (384x264 dot)
        // Menjamin orientasi 100% tegak lurus, tidak terbalik, tidak mirror, dan ukuran presisi
        UIGraphicsBeginImageContextWithOptions(size, true, 1.0)
        guard let ctx = UIGraphicsGetCurrentContext() else {
            UIGraphicsEndImageContext()
            return Data()
        }

        // 1. Bersihkan background putih
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fill(CGRect(origin: .zero, size: size))

        // 2. Nama Toko (y=14)
        let storeUpper = (storeName.isEmpty ? "OMNIPOS STORE" : storeName).uppercased()
        let storeFont = UIFont.systemFont(ofSize: 15, weight: .heavy)
        let storeAttrs: [NSAttributedString.Key: Any] = [
            .font: storeFont,
            .foregroundColor: UIColor.black
        ]
        let storeSize = storeUpper.size(withAttributes: storeAttrs)
        let storeRect = CGRect(x: (CGFloat(width) - storeSize.width) / 2, y: 14, width: storeSize.width, height: storeSize.height)
        storeUpper.draw(in: storeRect, withAttributes: storeAttrs)

        // Divider Line 1 (y=34) - Tebal 2 dot
        ctx.setStrokeColor(UIColor.black.cgColor)
        ctx.setLineWidth(2)
        ctx.move(to: CGPoint(x: 10, y: 34))
        ctx.addLine(to: CGPoint(x: CGFloat(width) - 10, y: 34))
        ctx.strokePath()

        // 3. Nama Produk (y=38) - Tebal Heavy
        let dispName = String(productName.prefix(24))
        let prodFont = UIFont.systemFont(ofSize: 18, weight: .heavy)
        let prodAttrs: [NSAttributedString.Key: Any] = [
            .font: prodFont,
            .foregroundColor: UIColor.black
        ]
        let prodSize = dispName.size(withAttributes: prodAttrs)
        let prodRect = CGRect(x: (CGFloat(width) - prodSize.width) / 2, y: 38, width: prodSize.width, height: prodSize.height)
        dispName.draw(in: prodRect, withAttributes: prodAttrs)

        // 4. Barcode Code 128 (y=64, h=48)
        let cleanBc = barcode.filter { $0.asciiValue != nil && $0.asciiValue! >= 32 && $0.asciiValue! <= 126 }
        let finalBc = cleanBc.isEmpty ? "00000000" : cleanBc
        var codes: [Int] = [104] // Start B
        var csum = 104
        for (i, char) in finalBc.enumerated() {
            let v = Int(char.asciiValue!) - 32
            codes.append(v)
            csum += v * (i + 1)
        }
        codes.append(csum % 103)
        codes.append(106) // Stop

        var pattern = ""
        for code in codes {
            if code >= 0 && code < code128Patterns.count {
                pattern += code128Patterns[code]
            }
        }

        var moduleW: CGFloat = 2
        let totalModules = pattern.reduce(0) { $0 + (Int(String($1)) ?? 1) }
        var barTotalW = CGFloat(totalModules) * moduleW
        if barTotalW > CGFloat(width) - 24 {
            moduleW = 1
            barTotalW = CGFloat(totalModules) * moduleW
        }

        let startX = max(10, (CGFloat(width) - barTotalW) / 2)
        var curX = startX
        let barY: CGFloat = 64
        let barH: CGFloat = 48

        ctx.setFillColor(UIColor.black.cgColor)
        for (idx, char) in pattern.enumerated() {
            let w = CGFloat(Int(String(char)) ?? 1) * moduleW
            if idx % 2 == 0 {
                ctx.fill(CGRect(x: curX, y: barY, width: w, height: barH))
            }
            curX += w
        }

        // 5. Barcode Teks (y=114) - Tebal Monospaced Heavy
        let codeFont = UIFont.monospacedSystemFont(ofSize: 15, weight: .heavy)
        let codeAttrs: [NSAttributedString.Key: Any] = [
            .font: codeFont,
            .foregroundColor: UIColor.black
        ]
        let codeSize = finalBc.size(withAttributes: codeAttrs)
        let codeRect = CGRect(x: (CGFloat(width) - codeSize.width) / 2, y: 114, width: codeSize.width, height: codeSize.height)
        finalBc.draw(in: codeRect, withAttributes: codeAttrs)

        // Divider Line 2 (y=134) - Tebal 2 dot
        ctx.setStrokeColor(UIColor.black.cgColor)
        ctx.setLineWidth(2)
        ctx.move(to: CGPoint(x: 10, y: 134))
        ctx.addLine(to: CGPoint(x: CGFloat(width) - 10, y: 134))
        ctx.strokePath()

        // 6. Price Box (y=140 s/d 208, height=68) - Border Tebal 3 dot
        let boxRect = CGRect(x: 14, y: 140, width: CGFloat(width) - 28, height: 68)
        ctx.setStrokeColor(UIColor.black.cgColor)
        ctx.setLineWidth(3)
        ctx.stroke(boxRect)

        let priceStr = CurrencyHelper.format(price)
        let priceFont = UIFont.systemFont(ofSize: 28, weight: .heavy)
        let priceAttrs: [NSAttributedString.Key: Any] = [
            .font: priceFont,
            .foregroundColor: UIColor.black
        ]
        let priceSize = priceStr.size(withAttributes: priceAttrs)
        let priceRect = CGRect(
            x: (CGFloat(width) - priceSize.width) / 2,
            y: 140 + (68 - priceSize.height) / 2,
            width: priceSize.width,
            height: priceSize.height
        )
        priceStr.draw(in: priceRect, withAttributes: priceAttrs)

        // Ambil hasil render UIKit yang sudah dipastikan 100% tegak lurus
        guard let renderedImage = UIGraphicsGetImageFromCurrentImageContext(),
              let cgImage = renderedImage.cgImage else {
            UIGraphicsEndImageContext()
            return Data()
        }
        UIGraphicsEndImageContext()

        // Ekstraksi 1-bit ESC/POS Raster (GS v 0) langsung dari pixel CGImage murni persis Android
        let bytesPerRow = 48
        var rasterData = Data()

        // Header GS v 0 murni tanpa byte perintah asing
        let xL = UInt8(bytesPerRow & 0xFF)
        let xH = UInt8((bytesPerRow >> 8) & 0xFF)
        let yL = UInt8(height & 0xFF)
        let yH = UInt8((height >> 8) & 0xFF)
        rasterData.append(contentsOf: [0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH])

        guard let dataProvider = cgImage.dataProvider,
              let cfData = dataProvider.data,
              let rawPtr = CFDataGetBytePtr(cfData) else {
            return rasterData
        }

        let cgBytesPerRow = cgImage.bytesPerRow
        let bytesPerPixel = max(1, cgImage.bitsPerPixel / 8)

        // Konversi ke 1-bit dengan threshold 170 agar teks dan barcode tebal solid tanpa memicu self-test
        for y in 0..<height {
            let rowOffset = y * cgBytesPerRow
            for byteIdx in 0..<bytesPerRow {
                var b: UInt8 = 0
                for bit in 0..<8 {
                    let x = byteIdx * 8 + bit
                    if x < width {
                        let pixelOffset = rowOffset + x * bytesPerPixel
                        let r = Int(rawPtr[pixelOffset])
                        let g = Int(rawPtr[pixelOffset + 1])
                        let bCol = Int(rawPtr[pixelOffset + 2])
                        let gray = (r * 299 + g * 587 + bCol * 114) / 1000
                        if gray < 170 {
                            b |= (1 << (7 - bit))
                        }
                    }
                }
                rasterData.append(b)
            }
        }

        return rasterData
    }

    public static func buildSingleProductBarcodeTag55x30Raster(
        product: Product,
        storeName: String
    ) -> Data {
        let barcode = product.barcode.isEmpty ? product.sku : product.barcode
        return renderProductBarcodeTag55x30Raster(
            storeName: storeName,
            productName: product.name,
            barcode: barcode,
            price: product.price
        )
    }

    public static func renderProductBarcodeTag55x30Image(
        storeName: String,
        productName: String,
        barcode: String,
        price: Double
    ) -> UIImage {
        let width: CGFloat = 384
        let height: CGFloat = 264
        let size = CGSize(width: width, height: height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: size, format: format)

        return renderer.image { ctx in
            let cg = ctx.cgContext
            cg.setFillColor(UIColor.white.cgColor)
            cg.fill(CGRect(origin: .zero, size: size))

            let storeUpper = (storeName.isEmpty ? "OMNIPOS STORE" : storeName).uppercased()
            let storeFont = UIFont.boldSystemFont(ofSize: 15)
            let storeAttrs: [NSAttributedString.Key: Any] = [.font: storeFont, .foregroundColor: UIColor.black]
            let storeSize = storeUpper.size(withAttributes: storeAttrs)
            storeUpper.draw(in: CGRect(x: (width - storeSize.width) / 2, y: 14, width: storeSize.width, height: storeSize.height), withAttributes: storeAttrs)

            cg.setStrokeColor(UIColor.black.cgColor)
            cg.setLineWidth(1)
            cg.move(to: CGPoint(x: 10, y: 34))
            cg.addLine(to: CGPoint(x: width - 10, y: 34))
            cg.strokePath()

            let dispName = String(productName.prefix(24))
            let prodFont = UIFont.boldSystemFont(ofSize: 18)
            let prodAttrs: [NSAttributedString.Key: Any] = [.font: prodFont, .foregroundColor: UIColor.black]
            let prodSize = dispName.size(withAttributes: prodAttrs)
            dispName.draw(in: CGRect(x: (width - prodSize.width) / 2, y: 38, width: prodSize.width, height: prodSize.height), withAttributes: prodAttrs)

            let cleanBc = barcode.filter { $0.asciiValue != nil && $0.asciiValue! >= 32 && $0.asciiValue! <= 126 }
            let finalBc = cleanBc.isEmpty ? "00000000" : cleanBc
            var codes: [Int] = [104]
            var csum = 104
            for (i, char) in finalBc.enumerated() {
                let v = Int(char.asciiValue!) - 32
                codes.append(v)
                csum += v * (i + 1)
            }
            codes.append(csum % 103)
            codes.append(106)

            var pattern = ""
            for code in codes {
                if code >= 0 && code < code128Patterns.count {
                    pattern += code128Patterns[code]
                }
            }

            var moduleW: CGFloat = 2
            let totalModules = pattern.reduce(0) { $0 + (Int(String($1)) ?? 1) }
            var barTotalW = CGFloat(totalModules) * moduleW
            if barTotalW > width - 24 {
                moduleW = 1
                barTotalW = CGFloat(totalModules) * moduleW
            }

            let startX = max(10, (width - barTotalW) / 2)
            var curX = startX
            cg.setFillColor(UIColor.black.cgColor)
            for (idx, char) in pattern.enumerated() {
                let w = CGFloat(Int(String(char)) ?? 1) * moduleW
                if idx % 2 == 0 {
                    cg.fill(CGRect(x: curX, y: 64, width: w, height: 44))
                }
                curX += w
            }

            let codeFont = UIFont.monospacedSystemFont(ofSize: 15, weight: .bold)
            let codeAttrs: [NSAttributedString.Key: Any] = [.font: codeFont, .foregroundColor: UIColor.black]
            let codeSize = finalBc.size(withAttributes: codeAttrs)
            finalBc.draw(in: CGRect(x: (width - codeSize.width) / 2, y: 112, width: codeSize.width, height: codeSize.height), withAttributes: codeAttrs)

            cg.setStrokeColor(UIColor.black.cgColor)
            cg.setLineWidth(1)
            cg.move(to: CGPoint(x: 10, y: 132))
            cg.addLine(to: CGPoint(x: width - 10, y: 132))
            cg.strokePath()

            let boxRect = CGRect(x: 14, y: 138, width: width - 28, height: 66)
            cg.stroke(boxRect)

            let priceStr = CurrencyHelper.format(price)
            let priceFont = UIFont.boldSystemFont(ofSize: 26)
            let priceAttrs: [NSAttributedString.Key: Any] = [.font: priceFont, .foregroundColor: UIColor.black]
            let priceSize = priceStr.size(withAttributes: priceAttrs)
            priceStr.draw(in: CGRect(x: (width - priceSize.width) / 2, y: 138 + (66 - priceSize.height) / 2, width: priceSize.width, height: priceSize.height), withAttributes: priceAttrs)
        }
    }

    public static func imageToEscPosRaster(image: UIImage) -> Data {
        guard let cgImage = image.cgImage else { return Data() }
        let targetWidth = 384
        let aspect = CGFloat(cgImage.height) / CGFloat(cgImage.width)
        let width = targetWidth
        let height = Int(CGFloat(width) * aspect)
        let bytesPerRow = (width + 7) / 8

        var data = Data()
        let xL = UInt8(bytesPerRow & 0xFF)
        let xH = UInt8((bytesPerRow >> 8) & 0xFF)
        let yL = UInt8(height & 0xFF)
        let yH = UInt8((height >> 8) & 0xFF)
        data.append(contentsOf: [0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH])

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        var rgbaPixels = [UInt8](repeating: 255, count: width * height * 4)
        guard let context = CGContext(
            data: &rgbaPixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return Data() }

        context.setFillColor(UIColor.white.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

        for y in 0..<height {
            let yCg = (height - 1) - y
            for byteIdx in 0..<bytesPerRow {
                var b: UInt8 = 0
                for bit in 0..<8 {
                    let x = byteIdx * 8 + bit
                    if x < width {
                        let offset = (yCg * width + x) * 4
                        let r = Int(rgbaPixels[offset])
                        let g = Int(rgbaPixels[offset + 1])
                        let bCol = Int(rgbaPixels[offset + 2])
                        let gray = (r * 299 + g * 587 + bCol * 114) / 1000
                        if gray < 128 {
                            b |= (1 << (7 - bit))
                        }
                    }
                }
                data.append(b)
            }
        }
        return data
    }

    public static func createBlankEscPosRaster(width: Int = 384, height: Int = 284) -> Data {
        let bytesPerRow = (width + 7) / 8
        var data = Data()
        let xL = UInt8(bytesPerRow & 0xFF)
        let xH = UInt8((bytesPerRow >> 8) & 0xFF)
        let yL = UInt8(height & 0xFF)
        let yH = UInt8((height >> 8) & 0xFF)
        data.append(contentsOf: [0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH])
        let blankRow = [UInt8](repeating: 0, count: bytesPerRow)
        for _ in 0..<height {
            data.append(contentsOf: blankRow)
        }
        return data
    }

    public static func buildProductBarcodeTag55x30Raster(
        product: Product,
        storeName: String,
        quantity: Int = 1
    ) -> Data {
        let safeQty = max(1, min(10, quantity))
        var data = Data()
        data.append(contentsOf: cmdInit)

        let labelRaster = buildSingleProductBarcodeTag55x30Raster(product: product, storeName: storeName)

        for _ in 0..<safeQty {
            data.append(labelRaster)
        }

        // 1 stiker kosong penutup (284 dots) untuk pendorongan ke pisau sobek
        data.append(createBlankEscPosRaster(width: 384, height: 284))
        return data
    }

    private static func encodeString(_ text: String) -> Data {
        // Encoding GBK / ISO-8859-1 untuk thermal receipt
        let cfEncoding = CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)
        let stringEncoding = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(cfEncoding))
        return text.data(using: stringEncoding) ?? text.data(using: .ascii) ?? Data()
    }
}

