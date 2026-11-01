import SwiftUI

struct SettingsView: View {
    @ObservedObject var bluetooth = BluetoothManager.shared
    @State private var config = ReceiptConfig.load()
    @State private var showSavedAlert = false

    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Identitas Toko")) {
                    HStack {
                        Text("Nama Toko:")
                        TextField("Nama Toko", text: $config.storeName)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("Alamat:")
                        TextField("Alamat", text: $config.storeAddress)
                            .multilineTextAlignment(.trailing)
                    }
                    HStack {
                        Text("No. Telepon:")
                        TextField("No. Telepon", text: $config.storePhone)
                            .keyboardType(.phonePad)
                            .multilineTextAlignment(.trailing)
                    }
                }

                Section(header: Text("Pesan Penutup / Footer Struk")) {
                    TextField("Pesan Terima Kasih", text: $config.footerMessage)
                }

                Section(header: Text("Konfigurasi Mesin Kasir")) {
                    Toggle("Aktifkan Auto-Cut (Pemotong)", isOn: $config.isAutoCutEnabled)
                    Picker("Lebar Kertas", selection: $config.paperWidth) {
                        Text("58mm (32 Karakter)").tag(32)
                        Text("80mm (48 Karakter)").tag(48)
                    }
                }

                Section(header: Text("Integrasi Web Browser (URL Scheme)")) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Aplikasi kasir web / Safari dapat mencetak otomatis ke aplikasi ini menggunakan URL:")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text("posprint://print?store=TokoKu&item=Kopi:20000:2")
                            .font(.system(.caption, design: .monospaced))
                            .padding(6)
                            .background(Color(.systemGray6))
                            .cornerRadius(6)
                    }
                }

                Section {
                    Button(action: saveSettings) {
                        HStack {
                            Spacer()
                            Text("Simpan Pengaturan").bold()
                            Spacer()
                        }
                    }

                    Button(action: testPrint) {
                        HStack {
                            Spacer()
                            Text("Test Print Struk Percobaan")
                                .foregroundColor(bluetooth.isConnected ? .blue : .gray)
                            Spacer()
                        }
                    }
                    .disabled(!bluetooth.isConnected)
                }
            }
            .navigationTitle("Pengaturan Struk")
            .alert("Pengaturan Disimpan", isPresented: $showSavedAlert) {
                Button("OK", role: .cancel) { }
            }
        }
    }

    func saveSettings() {
        config.save()
        showSavedAlert = true
    }

    func testPrint() {
        config.save()
        let testItems = [
            ReceiptItem(name: "Test Item 1", price: 10000, quantity: 1),
            ReceiptItem(name: "Test Item 2", price: 20000, quantity: 2)
        ]
        let data = ReceiptBuilder.buildReceipt(config: config, items: testItems, cashPaid: 50000)
        bluetooth.sendData(data)
    }
}
