import SwiftUI

struct ContentView: View {
    @ObservedObject var bluetooth = BluetoothManager.shared
    @ObservedObject var dataManager = DataManager.shared
    @State private var selectedTab = 0
    @State private var autoPrintAlertMessage = ""
    @State private var showAutoPrintAlert = false

    init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithDefaultBackground()
        appearance.backgroundEffect = UIBlurEffect(style: .systemUltraThinMaterialDark)
        appearance.backgroundColor = UIColor(red: 0.06, green: 0.08, blue: 0.14, alpha: 0.85)
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            CashierView()
                .tabItem {
                    Label("Kasir", systemImage: "cart.fill")
                }
                .tag(0)

            ProductManagementView()
                .tabItem {
                    Label("Produk", systemImage: "cube.box.fill")
                }
                .tag(1)

            ShippingLabelView()
                .tabItem {
                    Label("Pengiriman", systemImage: "shippingbox.fill")
                }
                .tag(2)

            OrderHistoryView()
                .tabItem {
                    Label("Riwayat", systemImage: "chart.bar.doc.horizontal.fill")
                }
                .tag(3)

            SettingsView()
                .tabItem {
                    Label("Pengaturan", systemImage: "gearshape.fill")
                }
                .tag(4)
        }
        .accentColor(.cyan)
        .preferredColorScheme(.dark)
        .onOpenURL { url in
            handleDeepLink(url: url)
        }
        .alert("Auto-Print Web", isPresented: $showAutoPrintAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(autoPrintAlertMessage)
        }
    }

    private func handleDeepLink(url: URL) {
        guard url.scheme == "posprint" else { return }

        // Format contoh: posprint://print?store=TokoKu&item=Kopi:20000:2
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true),
              let queryItems = components.queryItems else {
            return
        }

        var parsedItems: [OrderItem] = []
        var storeName = dataManager.storeConfig.storeName

        for item in queryItems {
            if item.name == "store", let val = item.value {
                storeName = val
            } else if item.name == "item", let val = item.value {
                // Format: Nama:Harga:Qty
                let parts = val.components(separatedBy: ":")
                if parts.count >= 2 {
                    let name = parts[0]
                    let price = Double(parts[1]) ?? 0
                    let qty = parts.count >= 3 ? (Int(parts[2]) ?? 1) : 1
                    let prod = Product(name: name, price: price)
                    parsedItems.append(OrderItem(product: prod, quantity: qty))
                }
            }
        }

        if parsedItems.isEmpty {
            parsedItems.append(OrderItem(product: Product(name: "Web Checkout Item", price: 10000), quantity: 1))
        }

        var tempConfig = dataManager.storeConfig
        tempConfig.storeName = storeName

        let order = Order(
            items: parsedItems,
            paymentMethod: .cash,
            paymentAmount: parsedItems.reduce(0) { $0 + $1.subtotal },
            orderType: .offline
        )

        if bluetooth.isConnected {
            let data = PrinterEngine.buildReceiptData(order: order, config: tempConfig)
            bluetooth.sendData(data) { success in
                autoPrintAlertMessage = success ? "Struk dari transaksi web berhasil dicetak dan otomatis dipotong!" : "Gagal mencetak struk."
                showAutoPrintAlert = true
                if success {
                    dataManager.processOrder(order)
                }
            }
        } else {
            autoPrintAlertMessage = "Perintah cetak web diterima, namun Bluetooth printer belum terhubung. Silakan hubungkan printer di tab Pengaturan."
            showAutoPrintAlert = true
            selectedTab = 4
        }
    }
}
