import Foundation

public struct StoreConfig: Codable, Equatable {
    // Store Information
    public var storeName: String
    public var storeTagline: String
    public var storeAddress: String
    public var storePhone: String
    public var footerMessage: String

    // Default Shipping / Sender Info
    public var defaultSenderName: String
    public var defaultSenderPhone: String
    public var defaultSenderAddress: String

    // POS & Tax Settings
    public var isTaxEnabled: Bool
    public var taxRate: Double         // e.g. 11% (PPN)
    public var paperWidth: Int         // 32 chars for 58mm, 48 chars for 80mm
    public var isAutoCutEnabled: Bool
    public var feedLinesBeforeCut: Int
    public var labelWidthInch: Double  // 4.0
    public var labelHeightInch: Double // 6.0

    public init(storeName: String = "OmniPOS Store",
                storeTagline: String = "Kasir & Pengiriman Terpadu",
                storeAddress: String = "Jl. Sudirman No. 88, Jakarta Pusat",
                storePhone: String = "0812-3456-7890",
                footerMessage: String = "Terima kasih atas kunjungan Anda!",
                defaultSenderName: String = "OmniPOS Fulfillment",
                defaultSenderPhone: String = "0812-3456-7890",
                defaultSenderAddress: String = "Jl. Sudirman No. 88, Jakarta Pusat 10220",
                isTaxEnabled: Bool = false,
                taxRate: Double = 11.0,
                paperWidth: Int = 32,
                isAutoCutEnabled: Bool = true,
                feedLinesBeforeCut: Int = 5,
                labelWidthInch: Double = 4.0,
                labelHeightInch: Double = 6.0) {
        self.storeName = storeName
        self.storeTagline = storeTagline
        self.storeAddress = storeAddress
        self.storePhone = storePhone
        self.footerMessage = footerMessage
        self.defaultSenderName = defaultSenderName
        self.defaultSenderPhone = defaultSenderPhone
        self.defaultSenderAddress = defaultSenderAddress
        self.isTaxEnabled = isTaxEnabled
        self.taxRate = taxRate
        self.paperWidth = paperWidth
        self.isAutoCutEnabled = isAutoCutEnabled
        self.feedLinesBeforeCut = feedLinesBeforeCut
        self.labelWidthInch = labelWidthInch
        self.labelHeightInch = labelHeightInch
    }
}

