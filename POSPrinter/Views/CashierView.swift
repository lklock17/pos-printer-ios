import SwiftUI

struct CashierView: View {
    @ObservedObject var bluetooth = BluetoothManager.shared
    @State private var config = ReceiptConfig.load()
    @State private var items: [ReceiptItem] = [
        ReceiptItem(name: "Kopi Latte", price: 25000, quantity: 1),
        ReceiptItem(name: "Roti Cokelat", price: 15000, quantity: 2)
    ]
    @State private var newItemName = ""
    @State private var newItemPrice = ""
    @State private var newItemQty = "1"
    @State private var cashPaidText = "60000"
    @State private var isPrinting = false

    var grandTotal: Int {
        items.reduce(0) { $0 + $1.subtotal }
    }

    var body: some View {
        NavigationView {
            Form {
                // Section Status Printer
                Section(header: Text("Status Printer")) {
                    HStack {
                        Image(systemName: bluetooth.isConnected ? "printer.fill" : "printer.slash")
                            .foregroundColor(bluetooth.isConnected ? .green : .red)
                        VStack(alignment: .leading) {
                            Text(bluetooth.connectedPrinterName)
                                .font(.headline)
                            Text(bluetooth.statusMessage)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }

                // Section Daftar Item Belanja
                Section(header: Text("Daftar Pesanan")) {
                    ForEach($items) { $item in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(item.name).font(.subheadline).bold()
                                Text("\(ReceiptBuilder.formatRupiah(item.price)) x \(item.quantity)")
                                    .font(.caption).foregroundColor(.secondary)
                            }
                            Spacer()
                            Text(ReceiptBuilder.formatRupiah(item.subtotal))
                                .font(.subheadline)
                            Stepper("", value: $item.quantity, in: 1...99)
                                .labelsHidden()
                        }
                    }
                    .onDelete { indexSet in
                        items.remove(atOffsets: indexSet)
                    }

                    // Tambah Item Cepat
                    HStack {
                        TextField("Nama Produk", text: $newItemName)
                        TextField("Harga", text: $newItemPrice)
                            .keyboardType(.numberPad)
                            .frame(width: 80)
                        Button(action: addItem) {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(.blue)
                        }
                    }
                }

                // Section Pembayaran
                Section(header: Text("Total & Pembayaran")) {
                    HStack {
                        Text("TOTAL AKHIR").bold()
                        Spacer()
                        Text(ReceiptBuilder.formatRupiah(grandTotal))
                            .font(.title3).bold().foregroundColor(.blue)
                    }

                    HStack {
                        Text("Uang Bayar:")
                        Spacer()
                        TextField("0", text: $cashPaidText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }

                    if let cash = Int(cashPaidText), cash >= grandTotal {
                        HStack {
                            Text("Kembalian:")
                            Spacer()
                            Text(ReceiptBuilder.formatRupiah(cash - grandTotal))
                                .foregroundColor(.green).bold()
                        }
                    }
                }

                // Tombol Cetak
                Section {
                    Button(action: printReceipt) {
                        HStack {
                            Spacer()
                            if isPrinting {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                Text(" Mencetak...")
                            } else {
                                Image(systemName: "printer.fill")
                                Text(" Cetak Struk & Auto Cut")
                            }
                            Spacer()
                        }
                        .padding(8)
                        .font(.headline)
                        .foregroundColor(.white)
                    }
                    .listRowBackground(bluetooth.isConnected ? Color.blue : Color.gray)
                    .disabled(!bluetooth.isConnected || isPrinting || items.isEmpty)
                }
            }
            .navigationTitle("Kasir POS")
            .onAppear {
                config = ReceiptConfig.load()
            }
        }
    }

    func addItem() {
        guard !newItemName.isEmpty, let price = Int(newItemPrice) else { return }
        let qty = Int(newItemQty) ?? 1
        items.append(ReceiptItem(name: newItemName, price: price, quantity: qty))
        newItemName = ""
        newItemPrice = ""
    }

    func printReceipt() {
        isPrinting = true
        let cash = Int(cashPaidText) ?? grandTotal
        let data = ReceiptBuilder.buildReceipt(config: config, items: items, cashPaid: cash)
        bluetooth.sendData(data) { _ in
            isPrinting = false
        }
    }
}
