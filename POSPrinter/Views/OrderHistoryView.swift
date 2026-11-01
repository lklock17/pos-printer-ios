import SwiftUI

struct OrderHistoryView: View {
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var btManager = BluetoothManager.shared

    @State private var selectedFilter: Int = 0 // 0: Semua, 1: Kasir, 2: Pengiriman
    @State private var selectedOrderForDetail: Order? = nil
    @State private var showPrintAlert: Bool = false
    @State private var printAlertMessage: String = ""

    var filteredOrders: [Order] {
        dataManager.orders.filter { ord in
            switch selectedFilter {
            case 1: return ord.orderType == .offline
            case 2: return ord.orderType == .shipping
            default: return true
            }
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                VStack(spacing: 0) {
                    // Top Summary Card (Omzet & Laba Hari Ini)
                    todaySummaryCard

                    // Filter Segment
                    Picker("Filter", selection: $selectedFilter) {
                        Text("Semua (\(dataManager.orders.count))").tag(0)
                        Text("Kasir (\(dataManager.orders.filter { $0.orderType == .offline }.count))").tag(1)
                        Text("Pengiriman (\(dataManager.orders.filter { $0.orderType == .shipping }.count))").tag(2)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                    // List Transaksi
                    if filteredOrders.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "doc.text.magnifyingglass")
                                .font(.system(size: 48))
                                .foregroundColor(.white.opacity(0.4))
                            Text("Belum ada riwayat transaksi")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("Transaksi dari kasir atau pengiriman paket akan tercatat di sini.")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        Spacer()
                    } else {
                        List {
                            ForEach(filteredOrders) { order in
                                OrderRowItem(order: order)
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        selectedOrderForDetail = order
                                    }
                            }
                        }
                        .listStyle(PlainListStyle())
                    }
                }
            }
            .navigationTitle("Riwayat & Laporan")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: $selectedOrderForDetail) { order in
                OrderDetailSheet(order: order, onReprintReceipt: {
                    reprintReceipt(order: order)
                }, onReprintLabel: {
                    reprintLabel(order: order)
                })
            }
            .alert("Status Cetak", isPresented: $showPrintAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(printAlertMessage)
            }
        }
    }

    // MARK: - Summary Card
    private var todaySummaryCard: some View {
        VStack(spacing: 12) {
            HStack {
                Text("Ringkasan Hari Ini")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.secondary)
                    .textCase(.uppercase)
                Spacer()
                Text("\(dataManager.todayOrders.count) Transaksi")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(.blue)
            }

            HStack(spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Total Penjualan")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text(CurrencyHelper.format(dataManager.todaySalesTotal))
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.primary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Divider().frame(height: 36)

                VStack(alignment: .leading, spacing: 4) {
                    Text("Estimasi Keuntungan")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text(CurrencyHelper.format(dataManager.todayProfitTotal))
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.green)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 14)
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    // MARK: - Reprint Logic
    private func reprintReceipt(order: Order) {
        guard btManager.isConnected else {
            printAlertMessage = "Printer Bluetooth belum terhubung."
            showPrintAlert = true
            return
        }
        let data = PrinterEngine.buildReceiptData(order: order, config: dataManager.storeConfig)
        btManager.sendData(data) { success in
            printAlertMessage = success ? "Struk berhasil dicetak ulang!" : "Gagal mengirim data ke printer."
            showPrintAlert = true
        }
    }

    private func reprintLabel(order: Order) {
        guard btManager.isConnected else {
            printAlertMessage = "Printer Bluetooth belum terhubung."
            showPrintAlert = true
            return
        }
        let data = PrinterEngine.buildTSPLLabelData(order: order, config: dataManager.storeConfig)
        btManager.sendData(data) { success in
            printAlertMessage = success ? "Label 4x6 berhasil dicetak ulang!" : "Gagal mengirim data ke printer."
            showPrintAlert = true
        }
    }
}

// Subview: Order Row
struct OrderRowItem: View {
    let order: Order

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(order.orderType == .shipping ? Color.orange.opacity(0.15) : Color.blue.opacity(0.15))
                    .frame(width: 44, height: 44)
                Image(systemName: order.orderType == .shipping ? "shippingbox.fill" : "cart.fill")
                    .foregroundColor(order.orderType == .shipping ? .orange : .blue)
                    .font(.system(size: 18))
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(order.invoiceNumber)
                        .font(.system(size: 14, weight: .bold))
                    if order.orderType == .shipping {
                        Text("PAKET")
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.orange.opacity(0.15))
                            .foregroundColor(.orange)
                            .cornerRadius(4)
                    }
                }

                Text("\(order.items.count) produk • \(order.formattedDate)")
                    .font(.caption2)
                    .foregroundColor(.secondary)

                if let ship = order.shippingInfo {
                    Text("\(ship.courier) (\(ship.recipientName))")
                        .font(.caption2)
                        .foregroundColor(.blue)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                Text(order.formattedTotal)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)

                Text(order.paymentMethod.rawValue)
                    .font(.caption2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.gray.opacity(0.12))
                    .foregroundColor(.secondary)
                    .cornerRadius(4)
            }
        }
        .padding(.vertical, 4)
    }
}

// Subview: Order Detail Sheet
struct OrderDetailSheet: View {
    let order: Order
    var onReprintReceipt: () -> Void
    var onReprintLabel: () -> Void
    @Environment(\.presentationMode) private var presentationMode

    @State private var showConfirmReceipt: Bool = false
    @State private var showConfirmLabel: Bool = false

    var body: some View {
        NavigationView {
            List {
                Section(header: Text("Informasi Transaksi")) {
                    HStack {
                        Text("No. Faktur:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(order.invoiceNumber)
                            .bold()
                    }
                    HStack {
                        Text("Waktu:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(order.formattedDate)
                    }
                    HStack {
                        Text("Tipe Pesanan:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(order.orderType.rawValue)
                            .foregroundColor(order.orderType == .shipping ? .orange : .blue)
                            .font(.system(size: 14, weight: .semibold))
                    }
                }

                Section(header: Text("Daftar Item (\(order.items.count))")) {
                    ForEach(order.items) { item in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.product.name)
                                    .font(.system(size: 15, weight: .medium))
                                Text("\(item.quantity) x \(CurrencyHelper.format(item.product.price))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Text(CurrencyHelper.format(item.subtotal))
                                .font(.system(size: 15, weight: .semibold))
                        }
                    }
                }

                Section(header: Text("Rincian Pembayaran")) {
                    HStack {
                        Text("Subtotal:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(CurrencyHelper.format(order.subtotal))
                    }
                    if order.discount > 0 {
                        HStack {
                            Text("Diskon:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("-\(CurrencyHelper.format(order.discount))")
                                .foregroundColor(.red)
                        }
                    }
                    if order.tax > 0 {
                        HStack {
                            Text("PPN:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(CurrencyHelper.format(order.tax))
                        }
                    }
                    HStack {
                        Text("Total Pembayaran:")
                            .bold()
                        Spacer()
                        Text(order.formattedTotal)
                            .font(.headline)
                            .foregroundColor(.blue)
                    }
                    HStack {
                        Text("Metode Bayar:")
                            .foregroundColor(.secondary)
                        Spacer()
                        Text(order.paymentMethod.rawValue)
                    }
                    if order.paymentMethod == .cash {
                        HStack {
                            Text("Uang Diterima:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(CurrencyHelper.format(order.paymentAmount))
                        }
                        HStack {
                            Text("Kembalian:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(CurrencyHelper.format(order.change))
                                .bold()
                                .foregroundColor(.green)
                        }
                    }
                }

                if let ship = order.shippingInfo {
                    Section(header: Text("Detail Pengiriman Ekspedisi")) {
                        HStack {
                            Text("Kurir & Layanan:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(ship.courier) (\(ship.service))")
                                .font(.system(size: 14, weight: .semibold))
                        }
                        HStack {
                            Text("No. Resi:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text(ship.trackingNumber)
                                .font(.system(size: 14, weight: .bold, design: .monospaced))
                        }
                        HStack {
                            Text("Penerima:")
                                .foregroundColor(.secondary)
                            Spacer()
                            Text("\(ship.recipientName) (\(ship.recipientPhone))")
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Alamat Pengiriman:")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(ship.recipientAddress)
                                .font(.subheadline)
                        }
                        if ship.isCOD {
                            HStack {
                                Text("Status COD:")
                                    .foregroundColor(.red)
                                Spacer()
                                Text(CurrencyHelper.format(ship.codAmount))
                                    .bold()
                                    .foregroundColor(.red)
                            }
                        }
                    }
                }

                Section {
                    Button(action: { showConfirmReceipt = true }) {
                        HStack {
                            Image(systemName: "printer.fill")
                            Text("Cetak Ulang Struk Thermal (Auto-Cut)")
                                .bold()
                        }
                        .frame(maxWidth: .infinity)
                    }

                    if order.orderType == .shipping {
                        Button(action: { showConfirmLabel = true }) {
                            HStack {
                                Image(systemName: "shippingbox.fill")
                                Text("Cetak Ulang Label 4x6 (TSPL)")
                                    .bold()
                            }
                            .foregroundColor(.orange)
                            .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
            .navigationTitle("Detail Transaksi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Tutup") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .alert("Konfirmasi Cetak Ulang Struk", isPresented: $showConfirmReceipt) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak") {
                    onReprintReceipt()
                }
            } message: {
                Text("Apakah Anda yakin ingin mencetak ulang struk faktur \(order.invoiceNumber)?")
            }
            .alert("Konfirmasi Cetak Ulang Label 4x6", isPresented: $showConfirmLabel) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak") {
                    onReprintLabel()
                }
            } message: {
                Text("Apakah Anda yakin ingin mencetak ulang label pengiriman 4x6 untuk resi \(order.shippingInfo?.trackingNumber ?? order.invoiceNumber)?")
            }
        }
    }
}

