import Foundation
import SwiftUI
import Combine

public class DataManager: ObservableObject {
    public static let shared = DataManager()

    @Published public var products: [Product] = []
    @Published public var orders: [Order] = []
    @Published public var categories: [String] = [
        "Semua", "Makanan", "Minuman", "Snack", "Pakaian", "Elektronik", "Aksesoris", "Lainnya"
    ]
    @Published public var storeConfig: StoreConfig = StoreConfig()

    private let productsFile = "omnipos_products.json"
    private let ordersFile = "omnipos_orders.json"
    private let configFile = "omnipos_config.json"

    private init() {
        loadData()
    }

    // MARK: - Persistence Paths
    private func getDocumentsDirectory() -> URL {
        return FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    // MARK: - Load & Save
    public func loadData() {
        loadConfig()
        loadProducts()
        loadOrders()

        // Jika belum ada produk, buat sample data
        if products.isEmpty {
            createSampleProducts()
        }
    }

    public func saveAll() {
        saveProducts()
        saveOrders()
        saveConfig()
    }

    private func saveProducts() {
        let url = getDocumentsDirectory().appendingPathComponent(productsFile)
        do {
            let data = try JSONEncoder().encode(products)
            try data.write(to: url)
        } catch {
            print("Error saving products: \(error)")
        }
    }

    private func loadProducts() {
        let url = getDocumentsDirectory().appendingPathComponent(productsFile)
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let data = try Data(contentsOf: url)
            products = try JSONDecoder().decode([Product].self, from: data)
        } catch {
            print("Error loading products: \(error)")
        }
    }

    private func saveOrders() {
        let url = getDocumentsDirectory().appendingPathComponent(ordersFile)
        do {
            let data = try JSONEncoder().encode(orders)
            try data.write(to: url)
        } catch {
            print("Error saving orders: \(error)")
        }
    }

    private func loadOrders() {
        let url = getDocumentsDirectory().appendingPathComponent(ordersFile)
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let data = try Data(contentsOf: url)
            orders = try JSONDecoder().decode([Order].self, from: data)
        } catch {
            print("Error loading orders: \(error)")
        }
    }

    public func saveConfig() {
        let url = getDocumentsDirectory().appendingPathComponent(configFile)
        do {
            let data = try JSONEncoder().encode(storeConfig)
            try data.write(to: url)
        } catch {
            print("Error saving config: \(error)")
        }
    }

    private func loadConfig() {
        let url = getDocumentsDirectory().appendingPathComponent(configFile)
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        do {
            let data = try Data(contentsOf: url)
            storeConfig = try JSONDecoder().decode(StoreConfig.self, from: data)
        } catch {
            print("Error loading config: \(error)")
        }
    }

    // MARK: - Product CRUD
    public func addProduct(_ product: Product) {
        products.insert(product, at: 0)
        if !categories.contains(product.category) && product.category != "Semua" {
            categories.append(product.category)
        }
        saveProducts()
    }

    public func updateProduct(_ product: Product) {
        if let idx = products.firstIndex(where: { $0.id == product.id }) {
            products[idx] = product
            if !categories.contains(product.category) && product.category != "Semua" {
                categories.append(product.category)
            }
            saveProducts()
        }
    }

    public func deleteProduct(at offsets: IndexSet) {
        products.remove(atOffsets: offsets)
        saveProducts()
    }

    public func deleteProduct(_ product: Product) {
        products.removeAll { $0.id == product.id }
        saveProducts()
    }

    public func adjustStock(productId: UUID, delta: Int) {
        if let idx = products.firstIndex(where: { $0.id == productId }) {
            products[idx].stock = max(0, products[idx].stock + delta)
            saveProducts()
        }
    }

    // MARK: - Order Processing
    public func processOrder(_ order: Order) {
        // Kurangi stok untuk tiap produk yang dibeli
        for item in order.items {
            adjustStock(productId: item.product.id, delta: -item.quantity)
        }
        orders.insert(order, at: 0)
        saveOrders()
    }

    // MARK: - Reports Helper
    public var todayOrders: [Order] {
        let calendar = Calendar.current
        return orders.filter { calendar.isDateInToday($0.date) }
    }

    public var todaySalesTotal: Double {
        return todayOrders.reduce(0) { $0 + $1.total }
    }

    public var todayProfitTotal: Double {
        return todayOrders.reduce(0) { orderSum, order in
            let orderProfit = order.items.reduce(0) { itemSum, item in
                let margin = (item.product.price - item.product.costPrice) * Double(item.quantity)
                return itemSum + max(0, margin)
            }
            return orderSum + orderProfit
        }
    }

    public var lowStockProducts: [Product] {
        return products.filter { $0.isLowStock }
    }

    // MARK: - Sample Data
    private func createSampleProducts() {
        products = [
            Product(name: "Kopi Latte Aren", sku: "KOP-001", barcode: "8991001001", price: 24000, costPrice: 10000, stock: 25, category: "Minuman", unit: "cup"),
            Product(name: "Matcha Espresso", sku: "KOP-002", barcode: "8991001002", price: 28000, costPrice: 12000, stock: 18, category: "Minuman", unit: "cup"),
            Product(name: "Croissant Cokelat", sku: "SNK-001", barcode: "8992001001", price: 22000, costPrice: 9000, stock: 12, category: "Makanan", unit: "pcs"),
            Product(name: "Nasi Goreng Spesial", sku: "FOD-001", barcode: "8993001001", price: 35000, costPrice: 15000, stock: 30, category: "Makanan", unit: "porsi"),
            Product(name: "Kaos Polo Katun Premium", sku: "APL-001", barcode: "8994001001", price: 125000, costPrice: 65000, stock: 8, category: "Pakaian", unit: "pcs"),
            Product(name: "Kabel Data Type-C Fast 65W", sku: "ELC-001", barcode: "8995001001", price: 45000, costPrice: 18000, stock: 4, category: "Elektronik", unit: "pcs")
        ]
        saveProducts()
    }
}

