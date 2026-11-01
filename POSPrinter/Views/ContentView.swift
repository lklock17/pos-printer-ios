import SwiftUI

struct ContentView: View {
    @ObservedObject var bluetooth = BluetoothManager.shared
    @State private var selectedTab = 0
    @State private var autoPrintAlertMessage = ""
    @State private var showAutoPrintAlert = false

    var body: some View {
        TabView(selection: $selectedTab) {
            CashierView()
                .tabItem {
                    Label("Kasir", systemImage: "cart.fill")
                }
                .tag(0)

            SettingsView()
                .tabItem {
                    Label("Pengaturan", systemImage: "gearshape.fill")
                }
                .tag(1)

            PrinterScanView()
                .tabItem {
                    Label("Printer", systemImage: "printer.fill")
                }
                .tag(2)
        }
        .onOpenURL { url in
            handleDeepLink(url: url)
        }
        .alert("Auto-Print Web", isPresented: $showAutoPrintAlert) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(autoPrintAlertMessage)
        }
    }

    func handleDeepLink(url: URL) {
        guard url.scheme == "posprint" else { return }

        let config = ReceiptConfig.load()
        let payload = ReceiptBuilder.buildFromURL(url, defaultConfig: config)

        if bluetooth.isConnected {
            bluetooth.sendData(payload) { success in
                autoPrintAlertMessage = success ? "Struk dari browser berhasil dicetak dan dipotong!" : "Gagal mengirim data ke printer."
                showAutoPrintAlert = true
            }
        } else {
            autoPrintAlertMessage = "Data struk diterima dari browser, namun printer Bluetooth belum terhubung. Silakan hubungkan printer di tab 'Printer'."
            showAutoPrintAlert = true
            selectedTab = 2
        }
    }
}
