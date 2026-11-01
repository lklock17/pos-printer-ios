import SwiftUI

struct ShippingLabelView: View {
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var btManager = BluetoothManager.shared

    @State private var selectedOrder: Order? = nil
    @State private var customShipping = ShippingInfo()
    @State private var isCustomMode: Bool = false
    
    // Form Edit State
    @State private var courier: String = "J&T Express"
    @State private var service: String = "REGULER"
    @State private var trackingNumber: String = ""
    @State private var recipientName: String = "Budi Pratama"
    @State private var recipientPhone: String = "0812-9876-5432"
    @State private var recipientAddress: String = "Jl. Merdeka No. 45, RT 02/05, Gambir, Jakarta Pusat"
    @State private var postalCode: String = "10110"
    @State private var weightKgText: String = "1.0"
    @State private var isCOD: Bool = false
    @State private var codAmountText: String = "150000"
    @State private var notes: String = "FRAGILE - JANGAN DIBANTING"
    
    @State private var showPrintSuccessAlert: Bool = false
    @State private var printAlertMessage: String = ""
    @State private var showEditSheet: Bool = false

    let courierOptions = ["J&T Express", "JNE Express", "SiCepat", "Shopee Xpress", "AnterAja", "Ninja Xpress", "POS Indonesia", "GoSend"]
    let serviceOptions = ["REGULER", "EZ / STANDAR", "BEST / NEXT DAY", "CARGO", "HEMAT", "SAME DAY"]

    var shippingOrders: [Order] {
        dataManager.orders.filter { $0.orderType == .shipping && $0.shippingInfo != nil }
    }

    var activeShippingInfo: ShippingInfo {
        if let ord = selectedOrder, let s = ord.shippingInfo {
            return s
        }
        return ShippingInfo(
            courier: courier,
            service: service,
            trackingNumber: trackingNumber.isEmpty ? "AWB-\(Int(Date().timeIntervalSince1970))" : trackingNumber,
            senderName: dataManager.storeConfig.defaultSenderName,
            senderPhone: dataManager.storeConfig.defaultSenderPhone,
            senderAddress: dataManager.storeConfig.defaultSenderAddress,
            recipientName: recipientName,
            recipientPhone: recipientPhone,
            recipientAddress: recipientAddress,
            postalCode: postalCode,
            weightKg: Double(weightKgText) ?? 1.0,
            isCOD: isCOD,
            codAmount: Double(codAmountText) ?? 0.0,
            notes: notes
        )
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Source Selector: Order Terakhir vs Buat Label Baru
                    sourcePickerSection

                    // Visual 4x6 Label Preview Card
                    labelPreviewCard

                    // Action Buttons (Print TSPL 4x6 & Print ESC/POS 58/80)
                    actionButtonsSection

                    // Quick Edit Shipping Details
                    editSummarySection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(UIColor.systemGroupedBackground))
            .navigationTitle("Label Pengiriman 4x6")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Edit Resi") {
                        showEditSheet = true
                    }
                }
            }
            .sheet(isPresented: $showEditSheet) {
                editShippingModal
            }
            .alert("Status Cetak", isPresented: $showPrintSuccessAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(printAlertMessage)
            }
            .onAppear {
                initDefaultSelection()
            }
        }
    }

    private func initDefaultSelection() {
        if let first = shippingOrders.first {
            selectedOrder = first
            isCustomMode = false
        } else {
            isCustomMode = true
            if trackingNumber.isEmpty {
                trackingNumber = "JT\(Int.random(in: 100000000...999999999))"
            }
        }
    }

    // MARK: - Source Selector
    private var sourcePickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Pilih Sumber Resi:")
                .font(.caption)
                .foregroundColor(.secondary)
                .textCase(.uppercase)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    Button(action: {
                        isCustomMode = true
                        selectedOrder = nil
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle.fill")
                            Text("Buat Resi Manual")
                        }
                        .font(.subheadline)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(isCustomMode ? Color.blue : Color(UIColor.secondarySystemBackground))
                        .foregroundColor(isCustomMode ? .white : .primary)
                        .cornerRadius(8)
                    }

                    ForEach(shippingOrders) { order in
                        Button(action: {
                            isCustomMode = false
                            selectedOrder = order
                        }) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(order.shippingInfo?.trackingNumber ?? order.invoiceNumber)
                                    .font(.caption)
                                    .fontWeight(.bold)
                                Text("\(order.shippingInfo?.courier ?? "Kurir") • \(order.shippingInfo?.recipientName ?? "")")
                                    .font(.caption2)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(selectedOrder?.id == order.id && !isCustomMode ? Color.blue : Color(UIColor.secondarySystemBackground))
                            .foregroundColor(selectedOrder?.id == order.id && !isCustomMode ? .white : .primary)
                            .cornerRadius(8)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 4)
    }

    // MARK: - 4x6 Label Visual Preview
    private var labelPreviewCard: some View {
        let ship = activeShippingInfo
        let sender = dataManager.storeConfig

        return VStack(spacing: 0) {
            // Header: Courier & Service
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "shippingbox.fill")
                        .foregroundColor(.red)
                    Text(ship.courier.uppercased())
                        .font(.system(size: 18, weight: .black))
                }
                Spacer()
                Text("[\(ship.service.uppercased())]")
                    .font(.system(size: 15, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.08))
                    .cornerRadius(4)
            }
            .padding(12)
            .background(Color.yellow.opacity(0.2))

            Divider().background(Color.black)

            // Barcode & Tracking Number
            VStack(spacing: 6) {
                // Simulated Barcode Lines
                HStack(spacing: 2) {
                    ForEach(0..<45, id: \.self) { i in
                        Rectangle()
                            .fill(Color.black)
                            .frame(width: (i % 3 == 0 || i % 7 == 0) ? 3 : 1.5, height: 48)
                    }
                }
                .padding(.top, 8)

                Text(ship.trackingNumber)
                    .font(.system(size: 16, weight: .black, design: .monospaced))
                    .tracking(2)
            }
            .padding(.vertical, 6)

            Divider().background(Color.black)

            // COD & Berat Row
            HStack {
                if ship.isCOD {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("C O D")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.red)
                        Text(CurrencyHelper.format(ship.codAmount))
                            .font(.system(size: 14, weight: .bold))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.red.opacity(0.12))
                    .cornerRadius(6)
                } else {
                    Text("NON-COD")
                        .font(.system(size: 12, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.gray.opacity(0.15))
                        .cornerRadius(4)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("BERAT PAKET")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundColor(.secondary)
                    Text(String(format: "%.1f KG", ship.weightKg))
                        .font(.system(size: 14, weight: .black))
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)

            Divider().background(Color.black)

            // Pengirim (Sender)
            VStack(alignment: .leading, spacing: 3) {
                Text("PENGIRIM:")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.secondary)
                Text("\(ship.senderName.isEmpty ? sender.defaultSenderName : ship.senderName) (\(ship.senderPhone.isEmpty ? sender.defaultSenderPhone : ship.senderPhone))")
                    .font(.system(size: 12, weight: .bold))
                Text(ship.senderAddress.isEmpty ? sender.defaultSenderAddress : ship.senderAddress)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color(UIColor.systemGray6).opacity(0.5))

            Divider().background(Color.black)

            // Penerima (Recipient) - Very Prominent
            VStack(alignment: .leading, spacing: 4) {
                Text("PENERIMA / TUJUAN:")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(.blue)

                Text(ship.recipientName)
                    .font(.system(size: 17, weight: .black))
                    .foregroundColor(.primary)

                Text("Telp: \(ship.recipientPhone)")
                    .font(.system(size: 13, weight: .bold))

                Text(ship.recipientAddress)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.primary)

                if !ship.postalCode.isEmpty {
                    Text("KODE POS: \(ship.postalCode)")
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)

            Divider().background(Color.black)

            // Ringkasan Isi Barang
            VStack(alignment: .leading, spacing: 4) {
                Text("ISI PAKET:")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.secondary)

                if let ord = selectedOrder {
                    ForEach(ord.items.prefix(3)) { it in
                        Text("• \(it.product.name) (x\(it.quantity))")
                            .font(.system(size: 11))
                            .lineLimit(1)
                    }
                } else {
                    Text("• Produk Retail / Paket Pesanan (x1)")
                        .font(.system(size: 11))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)

            Divider().background(Color.black)

            // Instruksi Khusus
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
                    .font(.caption)
                Text(ship.notes)
                    .font(.system(size: 11, weight: .bold))
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(Color.orange.opacity(0.1))

            Divider().background(Color.black)

            // Tanda Tangan
            HStack(spacing: 16) {
                VStack(spacing: 24) {
                    Text("Kurir Penjemput")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(.gray)
                }
                VStack(spacing: 24) {
                    Text("Penerima Paket")
                        .font(.system(size: 10))
                        .foregroundColor(.secondary)
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(.gray)
                }
            }
            .padding(12)
        }
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.black, lineWidth: 2)
        )
        .shadow(color: Color.black.opacity(0.08), radius: 6, x: 0, y: 3)
    }

    // MARK: - Action Buttons
    private var actionButtonsSection: some View {
        VStack(spacing: 12) {
            // Tombol Cetak Label 4x6 TSPL
            Button(action: printTSPLLabel) {
                HStack(spacing: 10) {
                    Image(systemName: "shippingbox.fill")
                        .font(.headline)
                    Text("Cetak Label 4x6 Thermal (TSPL)")
                        .font(.headline)
                        .fontWeight(.bold)
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(btManager.isConnected ? Color.blue : Color.gray)
                .cornerRadius(12)
                .shadow(color: Color.blue.opacity(0.3), radius: 4, x: 0, y: 2)
            }

            // Tombol Cetak Resi ke Struk POS Thermal 58mm/80mm
            Button(action: printReceiptSlip) {
                HStack(spacing: 10) {
                    Image(systemName: "printer.fill")
                        .font(.subheadline)
                    Text("Cetak Resi ke Struk POS 58/80mm (Auto-Cut)")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
                .foregroundColor(.blue)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(Color.blue.opacity(0.12))
                .cornerRadius(12)
            }

            if !btManager.isConnected {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundColor(.orange)
                        .font(.caption)
                    Text("Printer Bluetooth belum terhubung. Hubungkan di tab Pengaturan.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding(.top, 2)
            }
        }
    }

    // MARK: - Edit Summary Section
    private var editSummarySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Ringkasan Pengiriman")
                    .font(.subheadline)
                    .fontWeight(.bold)
                Spacer()
                Button("Ubah") {
                    showEditSheet = true
                }
                .font(.caption)
                .foregroundColor(.blue)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Ekspedisi: \(activeShippingInfo.courier) (\(activeShippingInfo.service))")
                    .font(.caption)
                Text("Nomor Resi: \(activeShippingInfo.trackingNumber)")
                    .font(.caption)
                Text("Tujuan: \(activeShippingInfo.recipientName) (\(activeShippingInfo.recipientPhone))")
                    .font(.caption)
                Text("Alamat: \(activeShippingInfo.recipientAddress)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(12)
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(8)
        }
        .padding(.top, 4)
    }

    // MARK: - Edit Shipping Modal
    private var editShippingModal: some View {
        NavigationView {
            Form {
                Section(header: Text("Ekspedisi & Nomor Resi")) {
                    Picker("Pilih Kurir", selection: $courier) {
                        ForEach(courierOptions, id: \.self) { c in
                            Text(c).tag(c)
                        }
                    }

                    Picker("Layanan", selection: $service) {
                        ForEach(serviceOptions, id: \.self) { s in
                            Text(s).tag(s)
                        }
                    }

                    HStack {
                        Text("No. Resi / AWB:")
                        TextField("Nomor Resi", text: $trackingNumber)
                        Button(action: generateRandomTracking) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .foregroundColor(.blue)
                        }
                    }
                }

                Section(header: Text("Penerima Paket")) {
                    HStack {
                        Text("Nama:")
                        TextField("Nama penerima", text: $recipientName)
                    }

                    HStack {
                        Text("No. Telp:")
                        TextField("08...", text: $recipientPhone)
                            .keyboardType(.phonePad)
                    }

                    HStack {
                        Text("Alamat Lengkap:")
                        TextField("Jalan, RT/RW, Kelurahan, Kota", text: $recipientAddress)
                    }

                    HStack {
                        Text("Kode Pos:")
                        TextField("12345", text: $postalCode)
                            .keyboardType(.numberPad)
                    }
                }

                Section(header: Text("Ketentuan Paket & COD")) {
                    HStack {
                        Text("Berat Paket (KG):")
                        TextField("1.0", text: $weightKgText)
                            .keyboardType(.decimalPad)
                    }

                    Toggle("Pembayaran COD", isOn: $isCOD)

                    if isCOD {
                        HStack {
                            Text("Nominal COD (Rp):")
                            TextField("0", text: $codAmountText)
                                .keyboardType(.numberPad)
                        }
                    }

                    HStack {
                        Text("Instruksi / Catatan:")
                        TextField("FRAGILE, Jangan Dibanting", text: $notes)
                    }
                }
            }
            .navigationTitle("Ubah Info Resi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Selesai") {
                        showEditSheet = false
                    }
                    .bold()
                }
            }
        }
    }

    private func generateRandomTracking() {
        let prefix = courier.prefix(2).uppercased()
        trackingNumber = "\(prefix)\(Int.random(in: 100000000...999999999))"
    }

    // MARK: - Print Methods
    private func printTSPLLabel() {
        guard btManager.isConnected else {
            printAlertMessage = "Printer Bluetooth belum terhubung. Silakan buka menu Pengaturan untuk memindai printer label 4x6."
            showPrintSuccessAlert = true
            return
        }

        let dummyOrder = createWorkingOrder()
        let tsplData = PrinterEngine.buildTSPLLabelData(order: dummyOrder, config: dataManager.storeConfig)

        btManager.sendData(tsplData) { success in
            if success {
                printAlertMessage = "Label pengiriman 4x6 berhasil dikirim ke printer thermal label!"
            } else {
                printAlertMessage = "Gagal mengirim data label ke printer."
            }
            showPrintSuccessAlert = true
        }
    }

    private func printReceiptSlip() {
        guard btManager.isConnected else {
            printAlertMessage = "Printer Bluetooth belum terhubung. Silakan buka menu Pengaturan untuk memindai printer POS 58/80mm."
            showPrintSuccessAlert = true
            return
        }

        let dummyOrder = createWorkingOrder()
        let receiptData = PrinterEngine.buildReceiptData(order: dummyOrder, config: dataManager.storeConfig)

        btManager.sendData(receiptData) { success in
            if success {
                printAlertMessage = "Resi pengiriman berhasil dicetak ke struk POS dan otomatis dipotong (Auto-Cut)!"
            } else {
                printAlertMessage = "Gagal mencetak struk."
            }
            showPrintSuccessAlert = true
        }
    }

    private func createWorkingOrder() -> Order {
        if let ord = selectedOrder {
            return ord
        }

        let dummyItem = OrderItem(
            product: Product(name: "Paket Pengiriman Retail", price: 150000),
            quantity: 1
        )
        return Order(
            items: [dummyItem],
            orderType: .shipping,
            shippingInfo: activeShippingInfo
        )
    }
}
