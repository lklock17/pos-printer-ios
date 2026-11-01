import Foundation

public enum PaymentMethod: String, Codable, CaseIterable {
    case cash = "Tunai"
    case qris = "QRIS"
    case transfer = "Transfer Bank"
    case card = "Kartu Debit/Kredit"
}

public enum OrderType: String, Codable, CaseIterable {
    case offline = "Kasir Langsung"
    case shipping = "Pengiriman (Paket 4x6)"
}

public struct OrderItem: Identifiable, Codable, Equatable {
    public var id: UUID = UUID()
    public var product: Product
    public var quantity: Int
    public var itemDiscount: Double = 0.0
    public var notes: String = ""

    public var subtotal: Double {
        return max(0, (product.price * Double(quantity)) - itemDiscount)
    }

    public init(id: UUID = UUID(), product: Product, quantity: Int, itemDiscount: Double = 0, notes: String = "") {
        self.id = id
        self.product = product
        self.quantity = quantity
        self.itemDiscount = itemDiscount
        self.notes = notes
    }
}

public struct Order: Identifiable, Codable, Equatable {
    public var id: UUID = UUID()
    public var invoiceNumber: String
    public var date: Date
    public var items: [OrderItem]
    public var subtotal: Double
    public var discount: Double
    public var tax: Double
    public var total: Double
    public var paymentMethod: PaymentMethod
    public var paymentAmount: Double
    public var change: Double
    public var orderType: OrderType
    public var shippingInfo: ShippingInfo?

    public init(id: UUID = UUID(),
                invoiceNumber: String = "",
                date: Date = Date(),
                items: [OrderItem],
                discount: Double = 0.0,
                taxRate: Double = 0.0,
                paymentMethod: PaymentMethod = .cash,
                paymentAmount: Double = 0.0,
                orderType: OrderType = .offline,
                shippingInfo: ShippingInfo? = nil) {
        self.id = id
        self.items = items
        self.date = date
        self.discount = discount
        self.paymentMethod = paymentMethod
        self.orderType = orderType
        self.shippingInfo = shippingInfo

        let rawSubtotal = items.reduce(0) { $0 + $1.subtotal }
        self.subtotal = rawSubtotal

        let afterDiscount = max(0, rawSubtotal - discount)
        let calcTax = afterDiscount * (taxRate / 100.0)
        self.tax = calcTax
        self.total = afterDiscount + calcTax

        self.paymentAmount = paymentAmount == 0 ? self.total : paymentAmount
        self.change = max(0, self.paymentAmount - self.total)

        if invoiceNumber.isEmpty {
            let df = DateFormatter()
            df.dateFormat = "yyyyMMdd-HHmmss"
            self.invoiceNumber = "TRX-" + df.string(from: date)
        } else {
            self.invoiceNumber = invoiceNumber
        }
    }

    public var totalQuantity: Int {
        return items.reduce(0) { $0 + $1.quantity }
    }

    public var formattedTotal: String {
        return CurrencyHelper.format(total)
    }

    public var formattedDate: String {
        let df = DateFormatter()
        df.dateFormat = "dd/MM/yyyy HH:mm"
        return df.string(from: date)
    }
}
