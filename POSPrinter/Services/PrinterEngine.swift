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

    private static func encodeString(_ text: String) -> Data {
        // Encoding GBK / ISO-8859-1 untuk thermal receipt
        let cfEncoding = CFStringEncoding(CFStringEncodings.GB_18030_2000.rawValue)
        let stringEncoding = String.Encoding(rawValue: CFStringConvertEncodingToNSStringEncoding(cfEncoding))
        return text.data(using: stringEncoding) ?? text.data(using: .ascii) ?? Data()
    }
}
