import SwiftUI

struct SettingsView: View {
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var bluetooth = BluetoothManager.shared

    @State private var showSavedAlert = false
    @State private var showPrinterScanSheet = false
    @State private var testPrintAlertMessage = ""
    @State private var showTestPrintAlert = false
    @State private var showConfirmTestReceipt = false
    @State private var showConfirmTestLabel = false

    var body: some View {
        NavigationView {
            Form {
                // Section 1: Bluetooth Connection Status
                Section(header: Text("Koneksi Printer Bluetooth")) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(bluetooth.connectedPrinterName)
                                .font(.headline)
                            Text(bluetooth.statusMessage)
                                .font(.caption)
                                .foregroundColor(bluetooth.isConnected ? .green : .secondary)
                        }
                        Spacer()
                        if bluetooth.isConnected {
                            Button("Putuskan", role: .destructive) {
                                bluetooth.disconnect()
                            }
                            .buttonStyle(.bordered)
                            .controlSize(.small)
                        } else {
                            Button("Scan Printer") {
                                showPrinterScanSheet = true
                            }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.small)
                        }
                    }
                }

                // Section 2: Identitas Toko
                Section(header: Text("Identitas Toko Kasir")) {
                    HStack {
                        Text("Nama Toko:")
                            .foregroundColor(.secondary)
                        TextField("Nama Toko", text: $dataManager.storeConfig.storeName)
                    }

                    HStack {
                        Text("Slogan / Tagline:")
                            .foregroundColor(.secondary)
                        TextField("Kasir & Pengiriman Terpadu", text: $dataManager.storeConfig.storeTagline)
                    }

                    HStack {
                        Text("Alamat Toko:")
                            .foregroundColor(.secondary)
                        TextField("Alamat Toko", text: $dataManager.storeConfig.storeAddress)
                    }

                    HStack {
                        Text("No. Telepon / WA:")
                            .foregroundColor(.secondary)
                        TextField("No. Telepon", text: $dataManager.storeConfig.storePhone)
                            .keyboardType(.phonePad)
                    }

                    HStack {
                        Text("Pesan Footer:")
                            .foregroundColor(.secondary)
                        TextField("Terima kasih atas kunjungan Anda!", text: $dataManager.storeConfig.footerMessage)
                    }
                }

                // Section 3: Profil Pengirim Pengiriman Default
                Section(header: Text("Profil Default Pengirim Paket (Resi)")) {
                    HStack {
                        Text("Nama Pengirim:")
                            .foregroundColor(.secondary)
                        TextField("Nama Pengirim", text: $dataManager.storeConfig.defaultSenderName)
                    }

                    HStack {
                        Text("No. Telp Pengirim:")
                            .foregroundColor(.secondary)
                        TextField("No. HP Pengirim", text: $dataManager.storeConfig.defaultSenderPhone)
                            .keyboardType(.phonePad)
                    }

                    HStack {
                        Text("Alamat Pengirim:")
                            .foregroundColor(.secondary)
                        TextField("Alamat Asal Paket", text: $dataManager.storeConfig.defaultSenderAddress)
                    }
                }

                // Section 4: DYMO 4XL Printer Bridge (Laptop Wi-Fi)
                Section(header: Text("Printer Label DYMO 4XL (Laptop Wi-Fi)")) {
                    HStack {
                        Text("Host / IP Laptop:")
                            .foregroundColor(.secondary)
                        TextField("desktop-kurc90q.local:8088", text: $dataManager.storeConfig.dymoBridgeHost)
                            .autocapitalization(.none)
                            .disableAutocorrection(true)
                    }

                    Text("Gunakan 'desktop-kurc90q.local:8088' atau IP laptop. Terhubung otomatis selama HP dan laptop berada di Wi-Fi yang sama.")
                        .font(.caption2)
                        .foregroundColor(.gray)
                }

                // Section 4: Mesin Kasir & Kertas POS
                Section(header: Text("Konfigurasi Printer POS Thermal")) {
                    Picker("Lebar Kertas", selection: $dataManager.storeConfig.paperWidth) {
                        Text("58mm (32 Karakter)").tag(32)
                        Text("80mm (48 Karakter)").tag(48)
                    }

                    Toggle("Aktifkan Auto-Cut (Pemotong Otomatis)", isOn: $dataManager.storeConfig.isAutoCutEnabled)

                    Stepper("Feed Sebelum Potong: \(dataManager.storeConfig.feedLinesBeforeCut) Baris",
                            value: $dataManager.storeConfig.feedLinesBeforeCut, in: 2...10)

                    Toggle("Aktifkan PPN / Pajak", isOn: $dataManager.storeConfig.isTaxEnabled)
                    if dataManager.storeConfig.isTaxEnabled {
                        HStack {
                            Text("Persentase Pajak (%):")
                                .foregroundColor(.secondary)
                            Spacer()
                            TextField("11.0", value: $dataManager.storeConfig.taxRate, format: .number)
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }

                // Section 5: Printer Label Pengiriman
                Section(header: Text("Printer Label Pengiriman (Thermal Label)")) {
                    HStack {
                        Text("Format Label:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("4x6 Inci (100 x 150 mm) TSPL")
                            .font(.system(size: 14, weight: .medium))
                    }
                    HStack {
                        Text("Kompatibilitas:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("Xprinter, Kassen, TSC, Barcode BLE")
                            .font(.caption)
                    }
                }

                // Section 6: Integrasi Browser Web
                Section(header: Text("Integrasi Web Browser (URL Scheme)")) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Aplikasi web kasir atau Safari dapat mencetak otomatis via link URL:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("posprint://print?store=TokoKu&item=Kopi:20000:2")
                            .font(.system(.caption, design: .monospaced))
                            .padding(6)
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(6)
                    }
                }

                // Section 7: Uji Coba Cetak (Test Print)
                Section(header: Text("Uji Coba Cetak (Test Print)")) {
                    Button(action: { showConfirmTestReceipt = true }) {
                        HStack {
                            Image(systemName: "printer.fill")
                            Text("Test Print Struk Kasir (Auto-Cut)")
                            Spacer()
                            if !bluetooth.isConnected {
                                Image(systemName: "exclamationmark.circle")
                                    .foregroundColor(.orange)
                            }
                        }
                    }
                    .disabled(!bluetooth.isConnected)

                    Button(action: { showConfirmTestLabel = true }) {
                        HStack {
                            Image(systemName: "shippingbox.fill")
                            Text("Test Print Label 4x6 (TSPL)")
                            Spacer()
                            if !bluetooth.isConnected {
                                Image(systemName: "exclamationmark.circle")
                                    .foregroundColor(.orange)
                            }
                        }
                    }
                    .disabled(!bluetooth.isConnected)
                }

                // Section 8: Tombol Simpan
                Section {
                    Button(action: saveSettings) {
                        HStack {
                            Spacer()
                            Text("Simpan Pengaturan")
                                .bold()
                            Spacer()
                        }
                    }
                }

                // Section 9: Info Aplikasi
                Section(header: Text("Tentang Aplikasi")) {
                    HStack {
                        Text("Aplikasi:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("OmniPOS Universal")
                            .bold()
                    }
                    HStack {
                        Text("Versi:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("1.0.0 (Build 1)")
                    }
                    HStack {
                        Text("Protokol Cetak:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("ESC/POS & TSPL Dual Engine")
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Pengaturan")
            .sheet(isPresented: $showPrinterScanSheet) {
                PrinterScanView()
            }
            .alert("Pengaturan", isPresented: $showSavedAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Semua perubahan profil dan konfigurasi printer berhasil disimpan!")
            }
            .alert("Uji Cetak", isPresented: $showTestPrintAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(testPrintAlertMessage)
            }
            .alert("Konfirmasi Test Print Struk", isPresented: $showConfirmTestReceipt) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak") {
                    testPrintReceipt()
                }
            } message: {
                Text("Apakah Anda yakin ingin mengirim struk kasir uji coba ke printer thermal (Auto-Cut)?")
            }
            .alert("Konfirmasi Test Print Label 4x6", isPresented: $showConfirmTestLabel) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak") {
                    testPrintShippingLabel()
                }
            } message: {
                Text("Apakah Anda yakin ingin mengirim label pengiriman 4x6 uji coba ke printer label (TSPL)?")
            }
        }
    }

    private func saveSettings() {
        dataManager.saveConfig()
        showSavedAlert = true
    }

    private func testPrintReceipt() {
        dataManager.saveConfig()
        let testProduct1 = Product(name: "Test Produk Struk 1", price: 15000)
        let testProduct2 = Product(name: "Test Produk Struk 2", price: 25000)
        let testOrder = Order(
            items: [
                OrderItem(product: testProduct1, quantity: 1),
                OrderItem(product: testProduct2, quantity: 2)
            ],
            paymentAmount: 70000,
            orderType: .offline
        )

        let data = PrinterEngine.buildReceiptData(order: testOrder, config: dataManager.storeConfig)
        bluetooth.sendData(data) { success in
            testPrintAlertMessage = success ? "Perintah test struk kasir berhasil dikirim ke printer!" : "Gagal mengirim data ke printer."
            showTestPrintAlert = true
        }
    }

    private func testPrintShippingLabel() {
        dataManager.saveConfig()
        let testProduct = Product(name: "Test Paket Retail 4x6", price: 95000)
        let testShip = ShippingInfo(
            courier: "J&T Express",
            service: "REGULER",
            trackingNumber: "TEST-4X6-TSPL",
            senderName: dataManager.storeConfig.defaultSenderName,
            senderPhone: dataManager.storeConfig.defaultSenderPhone,
            senderAddress: dataManager.storeConfig.defaultSenderAddress,
            recipientName: "Test Penerima Label",
            recipientPhone: "0812-0000-1111",
            recipientAddress: "Jl. Percobaan No. 123, Kel. Pengujian, Jakarta",
            postalCode: "12345",
            weightKg: 1.0,
            isCOD: false,
            codAmount: 0.0,
            notes: "TEST PRINT TSPL 4X6"
        )
        let testOrder = Order(
            items: [OrderItem(product: testProduct, quantity: 1)],
            orderType: .shipping,
            shippingInfo: testShip
        )

        let data = PrinterEngine.buildTSPLLabelData(order: testOrder, config: dataManager.storeConfig)
        bluetooth.sendData(data) { success in
            testPrintAlertMessage = success ? "Perintah test label 4x6 TSPL berhasil dikirim ke printer label!" : "Gagal mengirim data label ke printer."
            showTestPrintAlert = true
        }
    }
}
