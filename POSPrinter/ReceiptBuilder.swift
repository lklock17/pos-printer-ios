import Foundation

struct ReceiptItem: Identifiable, Codable {
    var id = UUID()
    var name: String
    var price: Int
    var quantity: Int

    var subtotal: Int {
        return price * quantity
    }
}

struct ReceiptConfig: Codable {
    var storeName: String = "TOKO SAYA"
    var storeAddress: String = "Jl. Contoh No. 123"
    var storePhone: String = "0812-3456-7890"
    var footerMessage: String = "Terima kasih atas kunjungan Anda!"
    var isAutoCutEnabled: Bool = true
    var paperWidth: Int = 32 // 32 chars for 58mm

    static let defaultsKey = "ReceiptConfig_Key"

    static func load() -> ReceiptConfig {
        if let data = UserDefaults.standard.data(forKey: defaultsKey),
           let config = try? JSONDecoder().decode(ReceiptConfig.self, from: data) {
            return config
        }
        return ReceiptConfig()
    }

    func save() {
        if let encoded = try? JSONEncoder().encode(self) {
            UserDefaults.standard.set(encoded, forKey: ReceiptConfig.defaultsKey)
        }
    }
}

class ReceiptBuilder {
    // ESC/POS Command Constants
    static let ESC: [UInt8] = [0x1B]
    static let GS: [UInt8]  = [0x1D]

    static let cmdInit: [UInt8]         = [0x1B, 0x40]            // ESC @
    static let cmdAlignLeft: [UInt8]    = [0x1B, 0x61, 0x00]      // ESC a 0
    static let cmdAlignCenter: [UInt8]  = [0x1B, 0x61, 0x01]      // ESC a 1
    static let cmdAlignRight: [UInt8]   = [0x1B, 0x61, 0x02]      // ESC a 2
    static let cmdBoldOn: [UInt8]       = [0x1B, 0x45, 0x01]      // ESC E 1
    static let cmdBoldOff: [UInt8]      = [0x1B, 0x45, 0x00]      // ESC E 0
    static let cmdFeed5: [UInt8]        = [0x1B, 0x64, 0x05]      // Feed 5 baris
    static let cmdAutoCut: [UInt8]      = [0x1D, 0x56, 0x42, 0x00] // GS V 66 0 (Cut with feed)

    static func formatRow(left: String, right: String, width: Int = 32) -> String {
        let spaceCount = width - left.count - right.count
        if spaceCount < 1 {
            let allowed = max(1, width - right.count - 1)
            let trimmedLeft = String(left.prefix(allowed))
            return trimmedLeft + " " + right + "\r\n"
        }
        return left + String(repeating: " ", count: spaceCount) + right + "\r\n"
    }

    static func formatRupiah(_ amount: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = "."
        let numStr = formatter.string(from: NSNumber(value: amount)) ?? "\(amount)"
        return "Rp " + numStr
    }

    static func buildReceipt(config: ReceiptConfig, items: [ReceiptItem], cashPaid: Int = 0) -> Data {
        var data = Data()

        // 1. Inisialisasi
        data.append(contentsOf: cmdInit)

        // 2. Header Toko
        data.append(contentsOf: cmdAlignCenter)
        data.append(contentsOf: cmdBoldOn)
        data.append((config.storeName + "\r\n").data(using: .utf8) ?? Data())
        data.append(contentsOf: cmdBoldOff)
        data.append((config.storeAddress + "\r\n").data(using: .utf8) ?? Data())
        data.append((config.storePhone + "\r\n").data(using: .utf8) ?? Data())
        data.append((String(repeating: "=", count: config.paperWidth) + "\r\n").data(using: .utf8) ?? Data())

        // 3. Info Transaksi
        let dateStr = DateFormatter.localizedString(from: Date(), dateStyle: .short, timeStyle: .short)
        data.append(contentsOf: cmdAlignLeft)
        data.append(formatRow(left: "Waktu:", right: dateStr, width: config.paperWidth).data(using: .utf8) ?? Data())
        data.append((String(repeating: "-", count: config.paperWidth) + "\r\n").data(using: .utf8) ?? Data())

        // 4. Daftar Produk
        var grandTotal = 0
        for item in items {
            let itemLine = "\(item.quantity)x \(item.name)"
            let priceLine = formatRupiah(item.subtotal)
            data.append(formatRow(left: itemLine, right: priceLine, width: config.paperWidth).data(using: .utf8) ?? Data())
            grandTotal += item.subtotal
        }
        data.append((String(repeating: "-", count: config.paperWidth) + "\r\n").data(using: .utf8) ?? Data())

        // 5. Total Pembayaran
        data.append(contentsOf: cmdBoldOn)
        data.append(formatRow(left: "TOTAL:", right: formatRupiah(grandTotal), width: config.paperWidth).data(using: .utf8) ?? Data())
        data.append(contentsOf: cmdBoldOff)

        if cashPaid > 0 {
            data.append(formatRow(left: "Bayar:", right: formatRupiah(cashPaid), width: config.paperWidth).data(using: .utf8) ?? Data())
            let change = max(0, cashPaid - grandTotal)
            data.append(formatRow(left: "Kembali:", right: formatRupiah(change), width: config.paperWidth).data(using: .utf8) ?? Data())
        }
        data.append((String(repeating: "=", count: config.paperWidth) + "\r\n").data(using: .utf8) ?? Data())

        // 6. Footer Struk
        data.append(contentsOf: cmdAlignCenter)
        data.append((config.footerMessage + "\r\n").data(using: .utf8) ?? Data())

        // 7. Feed Kertas
        data.append(contentsOf: cmdFeed5)

        // 8. Auto Cut jika diaktifkan
        if config.isAutoCutEnabled {
            data.append(contentsOf: cmdAutoCut)
        }

        return data
    }

    // Parse URL Scheme: posprint://print?text=Hello+World atau posprint://print?store=...
    static func buildFromURL(_ url: URL, defaultConfig: ReceiptConfig) -> Data {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return buildReceipt(config: defaultConfig, items: [])
        }

        var config = defaultConfig
        var customItems: [ReceiptItem] = []

        if let queryItems = components.queryItems {
            for q in queryItems {
                if q.name == "store", let val = q.value {
                    config.storeName = val
                }
                if q.name == "address", let val = q.value {
                    config.storeAddress = val
                }
                if q.name == "phone", let val = q.value {
                    config.storePhone = val
                }
                if q.name == "item", let val = q.value {
                    // format item=Kopi:20000:2
                    let parts = val.components(separatedBy: ":")
                    if parts.count >= 2 {
                        let name = parts[0]
                        let price = Int(parts[1]) ?? 0
                        let qty = parts.count >= 3 ? (Int(parts[2]) ?? 1) : 1
                        customItems.append(ReceiptItem(name: name, price: price, quantity: qty))
                    }
                }
            }
        }

        if customItems.isEmpty {
            customItems.append(ReceiptItem(name: "Transaksi Web", price: 25000, quantity: 1))
        }

        return buildReceipt(config: config, items: customItems)
    }
}
