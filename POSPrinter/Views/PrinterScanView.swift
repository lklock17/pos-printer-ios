import SwiftUI

struct PrinterScanView: View {
    @ObservedObject var bluetooth = BluetoothManager.shared

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Status Koneksi")) {
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(bluetooth.connectedPrinterName)
                                .font(.headline)
                            Text(bluetooth.statusMessage)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if bluetooth.isConnected {
                            Button("Putuskan", role: .destructive) {
                                bluetooth.disconnect()
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                Section(header: HStack {
                    Text("Printer Terdeteksi")
                    Spacer()
                    if bluetooth.isScanning {
                        ProgressView()
                            .scaleEffect(0.8)
                    }
                }) {
                    if bluetooth.discoveredPrinters.isEmpty {
                        Text(bluetooth.isScanning ? "Mencari perangkat Bluetooth..." : "Tidak ada printer terdeteksi. Tarik ke bawah untuk scan.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(bluetooth.discoveredPrinters) { printer in
                            HStack {
                                Image(systemName: "printer")
                                    .foregroundColor(.blue)
                                VStack(alignment: .leading) {
                                    Text(printer.name)
                                        .font(.subheadline).bold()
                                    Text("RSSI: \(printer.rssi) dBm")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Button("Hubungkan") {
                                    bluetooth.connect(to: printer)
                                }
                                .buttonStyle(.borderedProminent)
                                .controlSize(.small)
                            }
                        }
                    }
                }
            }
            .navigationTitle("Pilih Printer")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        if bluetooth.isScanning {
                            bluetooth.stopScan()
                        } else {
                            bluetooth.startScan()
                        }
                    }) {
                        Image(systemName: bluetooth.isScanning ? "stop.circle" : "arrow.clockwise")
                    }
                }
            }
            .refreshable {
                bluetooth.startScan()
            }
        }
    }
}
