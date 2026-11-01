import Foundation

public struct ShippingInfo: Codable, Equatable {
    public var courier: String         // e.g. "J&T Express", "JNE", "SiCepat", "Shopee Xpress"
    public var service: String         // e.g. "EZ / REGULER", "NEXT DAY", "CARGO", "COD"
    public var trackingNumber: String   // e.g. "JP88291039281"
    public var senderName: String
    public var senderPhone: String
    public var senderAddress: String
    public var recipientName: String
    public var recipientPhone: String
    public var recipientAddress: String
    public var postalCode: String
    public var weightKg: Double        // e.g. 1.0 kg
    public var isCOD: Bool
    public var codAmount: Double
    public var notes: String            // e.g. "Fragile / Jangan dibanting"

    public init(courier: String = "J&T Express",
                service: String = "REGULER",
                trackingNumber: String = "",
                senderName: String = "",
                senderPhone: String = "",
                senderAddress: String = "",
                recipientName: String = "",
                recipientPhone: String = "",
                recipientAddress: String = "",
                postalCode: String = "",
                weightKg: Double = 1.0,
                isCOD: Bool = false,
                codAmount: Double = 0.0,
                notes: String = "Fragile - Jangan Dibanting") {
        self.courier = courier
        self.service = service
        self.trackingNumber = trackingNumber.isEmpty ? "AWB-\(Int(Date().timeIntervalSince1970))" : trackingNumber
        self.senderName = senderName
        self.senderPhone = senderPhone
        self.senderAddress = senderAddress
        self.recipientName = recipientName
        self.recipientPhone = recipientPhone
        self.recipientAddress = recipientAddress
        self.postalCode = postalCode
        self.weightKg = weightKg
        self.isCOD = isCOD
        self.codAmount = codAmount
        self.notes = notes
    }
}

