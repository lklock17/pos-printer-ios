import Foundation
import SwiftUI

public struct Product: Identifiable, Codable, Equatable {
    public var id: UUID = UUID()
    public var name: String
    public var sku: String
    public var barcode: String
    public var price: Double
    public var costPrice: Double
    public var stock: Int
    public var category: String
    public var unit: String // "pcs", "porsi", "box", "kg"
    public var imageBase64: String? // Optional base64 encoded photo
    public var notes: String?

    public init(id: UUID = UUID(),
                name: String,
                sku: String = "",
                barcode: String = "",
                price: Double,
                costPrice: Double = 0,
                stock: Int = 10,
                category: String = "Umum",
                unit: String = "pcs",
                imageBase64: String? = nil,
                notes: String? = nil) {
        self.id = id
        self.name = name
        self.sku = sku.isEmpty ? BarcodeHelper.generateSku() : sku
        self.barcode = barcode.isEmpty ? BarcodeHelper.generateEan12() : barcode
        self.price = price
        self.costPrice = costPrice
        self.stock = stock
        self.category = category
        self.unit = unit
        self.imageBase64 = imageBase64
        self.notes = notes
    }

    public var isLowStock: Bool {
        return stock <= 5
    }

    public var isOutOfStock: Bool {
        return stock <= 0
    }

    public var formattedPrice: String {
        return CurrencyHelper.format(price)
    }

    public var profitMargin: Double {
        guard price > 0 else { return 0 }
        return ((price - costPrice) / price) * 100.0
    }
}

public struct CurrencyHelper {
    public static func format(_ amount: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.locale = Locale(identifier: "id_ID")
        formatter.currencySymbol = "Rp "
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: amount)) ?? "Rp \(Int(amount))"
    }

    public static func formatPlain(_ amount: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = "."
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
    }
}

public struct BarcodeHelper {
    public static func generateEan12() -> String {
        let random9 = (1...9).map { _ in String(Int.random(in: 0...9)) }.joined()
        return "899\(random9)"
    }

    public static func generateSku() -> String {
        return "PRD-\(Int.random(in: 1000...9999))"
    }
}

