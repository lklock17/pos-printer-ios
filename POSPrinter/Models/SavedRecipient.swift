import Foundation

public struct SavedRecipient: Identifiable, Codable, Equatable {
    public var id: UUID = UUID()
    public var name: String
    public var phone: String
    public var address: String
    public var postalCode: String
    public var defaultCourier: String
    public var notes: String
    public var lastUsed: Date

    public init(id: UUID = UUID(),
                name: String,
                phone: String = "",
                address: String = "",
                postalCode: String = "",
                defaultCourier: String = "J&T Express",
                notes: String = "",
                lastUsed: Date = Date()) {
        self.id = id
        self.name = name
        self.phone = phone
        self.address = address
        self.postalCode = postalCode
        self.defaultCourier = defaultCourier
        self.notes = notes
        self.lastUsed = lastUsed
    }
}

