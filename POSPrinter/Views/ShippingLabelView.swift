import SwiftUI

enum TargetPrintType {
    case tspl4x6
    case receiptSlip
}

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
    
    // Popup & Dialog State
    @State private var showPrintConfirmDialog: Bool = false
    @State private var targetPrintType: TargetPrintType = .tspl4x6
    @State private var showPrintResultAlert: Bool = false
    @State private var printResultMessage: String = ""
    @State private var showEditSheet: Bool = false
    @State private var showPrinterScanSheet: Bool = false

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
            ZStack {
                // Liquid Glass Ambient Background
                LiquidGlassBackground()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 18) {
                        // Source Selector: Order Terakhir vs Buat Label Baru
                        sourcePickerSection

                        // Visual 4x6 Label Preview Card (High Contrast Thermal Paper)
                        labelPreviewCard

                        // Action Buttons (Print TSPL 4x6 & Print ESC/POS 58/80)
                        actionButtonsSection

                        // Quick Edit Shipping Details
                        editSummarySection
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Label Pengiriman 4x6")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showEditSheet = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "pencil.circle.fill")
                            Text("Edit Resi")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.cyan)
                    }
                }
            }
            .sheet(isPresented: $showEditSheet) {
                editShippingModal
            }
            .sheet(isPresented: $showPrinterScanSheet) {
                PrinterScanView()
            }
            // Dialog Konfirmasi Sebelum Print (Mencegah Salah Klik)
            .alert("Konfirmasi Cetak", isPresented: $showPrintConfirmDialog) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak Sekarang") {
                    if targetPrintType == .tspl4x6 {
                        executePrintTSPLLabel()
                    } else {
                        executePrintReceiptSlip()
                    }
                }
            } message: {
                if targetPrintType == .tspl4x6 {
                    Text("Apakah Anda yakin ingin mencetak Label 4x6 ini ke Printer Label Thermal?\n\n• Resi: \(activeShippingInfo.trackingNumber)\n• Penerima: \(activeShippingInfo.recipientName)\n• Kurir: \(activeShippingInfo.courier)")
                } else {
                    Text("Apakah Anda yakin ingin mencetak Resi Pengiriman ini ke Printer Struk POS Thermal (Auto-Cut)?\n\n• Resi: \(activeShippingInfo.trackingNumber)\n• Penerima: \(activeShippingInfo.recipientName)")
                }
            }
            // Alert Hasil Cetak
            .alert("Status Cetak", isPresented: $showPrintResultAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(printResultMessage)
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

    // MARK: - Source Selector (Liquid Glass Chips)
    private var sourcePickerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("PILIH SUMBER RESI")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.white.opacity(0.65))
                .tracking(1)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    Button(action: {
                        isCustomMode = true
                        selectedOrder = nil
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "plus.circle.fill")
                                .foregroundColor(isCustomMode ? .cyan : .white.opacity(0.8))
                            Text("Buat Resi Baru")
                                .font(.system(size: 13, weight: .semibold))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(isCustomMode ? Color.blue.opacity(0.4) : Color.white.opacity(0.08))
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(isCustomMode ? Color.cyan : Color.white.opacity(0.18), lineWidth: isCustomMode ? 1.5 : 0.8)
                        )
                        .foregroundColor(.white)
                    }

                    ForEach(shippingOrders) { order in
                        let isSelected = (selectedOrder?.id == order.id && !isCustomMode)
                        Button(action: {
                            isCustomMode = false
                            selectedOrder = order
                        }) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(order.shippingInfo?.trackingNumber ?? order.invoiceNumber)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                                Text("\(order.shippingInfo?.courier ?? "Kurir") • \(order.shippingInfo?.recipientName ?? "")")
                                    .font(.system(size: 10))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(isSelected ? Color.blue.opacity(0.4) : Color.white.opacity(0.08))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isSelected ? Color.cyan : Color.white.opacity(0.18), lineWidth: isSelected ? 1.5 : 0.8)
                            )
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    // MARK: - 4x6 Label Visual Preview (100% High-Contrast Thermal Sticker Paper)
    private var labelPreviewCard: some View {
        let ship = activeShippingInfo
        let sender = dataManager.storeConfig

        return VStack(spacing: 0) {
            // Header: Courier & Service
            HStack(alignment: .center) {
                HStack(spacing: 8) {
                    Image(systemName: "shippingbox.fill")
                        .foregroundColor(courierColor(ship.courier))
                        .font(.system(size: 20))
                    Text(ship.courier.uppercased())
                        .font(.system(size: 19, weight: .black))
                        .foregroundColor(courierColor(ship.courier))
                }

                Spacer()

                Text("[\(ship.service.uppercased())]")
                    .font(.system(size: 14, weight: .black))
                    .foregroundColor(.black)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.black.opacity(0.08))
                    .cornerRadius(6)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(courierColor(ship.courier).opacity(0.12))

            Divider().background(Color.black.opacity(0.8))

            // Barcode & Tracking Number
            VStack(spacing: 6) {
                // High-Contrast Simulated Barcode Lines
                HStack(spacing: 2.2) {
                    ForEach(0..<48, id: \.self) { i in
                        Rectangle()
                            .fill(Color.black)
                            .frame(width: (i % 4 == 0 || i % 7 == 0) ? 3.5 : (i % 2 == 0 ? 2 : 1.2), height: 50)
                    }
                }
                .padding(.top, 10)

                Text(ship.trackingNumber)
                    .font(.system(size: 17, weight: .black, design: .monospaced))
                    .foregroundColor(.black)
                    .tracking(2.5)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(Color.white)

            Divider().background(Color.black.opacity(0.8))

            // COD & Berat Row
            HStack {
                if ship.isCOD {
                    HStack(spacing: 6) {
                        Text("C O D")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.red)
                            .cornerRadius(4)

                        Text(CurrencyHelper.format(ship.codAmount))
                            .font(.system(size: 15, weight: .black))
                            .foregroundColor(.black)
                    }
                } else {
                    Text("NON-COD")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(.black.opacity(0.85))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("BERAT PAKET")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Color.black.opacity(0.6))
                    Text(String(format: "%.1f KG", ship.weightKg))
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(.black)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 9)
            .background(Color.black.opacity(0.03))

            Divider().background(Color.black.opacity(0.8))

            // Pengirim (Sender)
            VStack(alignment: .leading, spacing: 3) {
                Text("PENGIRIM:")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color.black.opacity(0.55))

                let sName = ship.senderName.isEmpty ? sender.defaultSenderName : ship.senderName
                let sPhone = ship.senderPhone.isEmpty ? sender.defaultSenderPhone : ship.senderPhone
                let sAddr = ship.senderAddress.isEmpty ? sender.defaultSenderAddress : ship.senderAddress

                Text("\(sName) (\(sPhone))")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)

                Text(sAddr)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundColor(Color.black.opacity(0.75))
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.black.opacity(0.04))

            Divider().background(Color.black.opacity(0.8))

            // Penerima (Recipient) - SANGAT TEBAL & JELAS
            VStack(alignment: .leading, spacing: 5) {
                Text("PENERIMA / TUJUAN:")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(Color(red: 0.1, green: 0.4, blue: 0.85))

                Text(ship.recipientName)
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(.black)

                Text("Telp: \(ship.recipientPhone)")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.black)

                Text(ship.recipientAddress)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color.black.opacity(0.9))

                if !ship.postalCode.isEmpty {
                    Text("KODE POS: \(ship.postalCode)")
                        .font(.system(size: 12, weight: .black, design: .monospaced))
                        .foregroundColor(.black)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .background(Color.white)

            Divider().background(Color.black.opacity(0.8))

            // Ringkasan Isi Barang
            VStack(alignment: .leading, spacing: 4) {
                Text("ISI DETAIL BARANG:")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color.black.opacity(0.6))

                if let ord = selectedOrder {
                    ForEach(ord.items.prefix(3)) { it in
                        Text("• \(it.product.name) (x\(it.quantity))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.black)
                            .lineLimit(1)
                    }
                } else {
                    Text("• Paket Pengiriman Retail (x1)")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.black)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.black.opacity(0.02))

            Divider().background(Color.black.opacity(0.8))

            // Instruksi Khusus
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(Color(red: 0.85, green: 0.4, blue: 0.0))
                    .font(.system(size: 13))
                Text(ship.notes)
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(Color(red: 0.7, green: 0.3, blue: 0.0))
                Spacer()
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color(red: 1.0, green: 0.95, blue: 0.85))

            Divider().background(Color.black.opacity(0.8))

            // Tanda Tangan
            HStack(spacing: 20) {
                VStack(spacing: 26) {
                    Text("Kurir Penjemput")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.6))
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color.black.opacity(0.4))
                }
                VStack(spacing: 26) {
                    Text("Penerima Paket")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.6))
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color.black.opacity(0.4))
                }
            }
            .padding(14)
            .background(Color.white)
        }
        .environment(\.colorScheme, .light) // Pastikan kertas resi SELALU rendering kertas putih & teks hitam pekat
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.black.opacity(0.85), lineWidth: 2)
        )
        .shadow(color: Color.black.opacity(0.45), radius: 14, x: 0, y: 7)
    }

    private func courierColor(_ courierName: String) -> Color {
        let name = courierName.lowercased()
        if name.contains("sicepat") {
            return Color(red: 0.85, green: 0.1, blue: 0.1) // Merah SiCepat
        } else if name.contains("j&t") {
            return Color(red: 0.9, green: 0.15, blue: 0.15) // Merah J&T
        } else if name.contains("jne") {
            return Color(red: 0.1, green: 0.3, blue: 0.8) // Biru JNE
        } else if name.contains("shopee") || name.contains("spx") {
            return Color(red: 0.95, green: 0.4, blue: 0.05) // Oranye SPX
        } else if name.contains("anteraja") {
            return Color(red: 0.75, green: 0.1, blue: 0.5) // Magenta AnterAja
        } else {
            return Color(red: 0.15, green: 0.45, blue: 0.95)
        }
    }

    // MARK: - Action Buttons (Liquid Glass Styled with Confirmation)
    private var actionButtonsSection: some View {
        VStack(spacing: 12) {
            // Tombol Utama Cetak Label 4x6 TSPL (Memicu Konfirmasi Popup)
            LiquidGlassButton(
                title: "Cetak Label 4x6 Thermal (TSPL)",
                icon: "shippingbox.fill",
                tintColor: btManager.isConnected ? Color(red: 0.12, green: 0.55, blue: 0.98) : Color.gray,
                isEnabled: true
            ) {
                targetPrintType = .tspl4x6
                showPrintConfirmDialog = true
            }

            // Tombol Cetak Resi ke Struk POS 58mm/80mm (Memicu Konfirmasi Popup)
            Button(action: {
                targetPrintType = .receiptSlip
                showPrintConfirmDialog = true
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "printer.fill")
                        .font(.system(size: 15))
                    Text("Cetak Resi ke Struk POS 58/80mm (Auto-Cut)")
                        .font(.system(size: 14, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(Color.white.opacity(0.10))
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.22), lineWidth: 1)
                )
            }

            // Status Bluetooth Banner
            if !btManager.isConnected {
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(.yellow)
                    Text("Printer Bluetooth belum terhubung.")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(.white.opacity(0.85))
                    Spacer()
                    Button("Hubungkan") {
                        showPrinterScanSheet = true
                    }
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.cyan)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(Color.yellow.opacity(0.12))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.yellow.opacity(0.3), lineWidth: 1)
                )
            }
        }
    }

    // MARK: - Edit Summary Section (Liquid Glass Card)
    private var editSummarySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("RINGKASAN TUJUAN & RESI")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white.opacity(0.65))
                    .tracking(1)
                Spacer()
                Button("Ubah") {
                    showEditSheet = true
                }
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(.cyan)
            }

            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Text("Ekspedisi:")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                    Text("\(activeShippingInfo.courier) (\(activeShippingInfo.service))")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                }
                HStack {
                    Text("Nomor Resi:")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                    Text(activeShippingInfo.trackingNumber)
                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                        .foregroundColor(.cyan)
                }
                HStack {
                    Text("Penerima:")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                    Text("\(activeShippingInfo.recipientName) (\(activeShippingInfo.recipientPhone))")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white)
                }
                HStack(alignment: .top) {
                    Text("Alamat:")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.6))
                    Text(activeShippingInfo.recipientAddress)
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.85))
                        .lineLimit(2)
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 14)
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
                    Button(action: {
                        showEditSheet = false
                    }) {
                        Text("Selesai")
                            .bold()
                    }
                }
            }
        }
    }

    private func generateRandomTracking() {
        let prefix = courier.prefix(2).uppercased()
        trackingNumber = "\(prefix)\(Int.random(in: 100000000...999999999))"
    }

    // MARK: - Print Execution (Dipanggil HANYA setelah konfirmasi user)
    private func executePrintTSPLLabel() {
        guard btManager.isConnected else {
            printResultMessage = "Printer Bluetooth belum terhubung. Silakan buka menu Pengaturan atau tap 'Hubungkan' untuk memindai printer label 4x6."
            showPrintResultAlert = true
            return
        }

        let dummyOrder = createWorkingOrder()
        let tsplData = PrinterEngine.buildTSPLLabelData(order: dummyOrder, config: dataManager.storeConfig)

        btManager.sendData(tsplData) { success in
            if success {
                printResultMessage = "Label pengiriman 4x6 (\(activeShippingInfo.trackingNumber)) berhasil dikirim ke printer thermal label!"
            } else {
                printResultMessage = "Gagal mengirim data label ke printer."
            }
            showPrintResultAlert = true
        }
    }

    private func executePrintReceiptSlip() {
        guard btManager.isConnected else {
            printResultMessage = "Printer Bluetooth belum terhubung. Silakan hubungkan printer POS 58/80mm di menu Pengaturan."
            showPrintResultAlert = true
            return
        }

        let dummyOrder = createWorkingOrder()
        let receiptData = PrinterEngine.buildReceiptData(order: dummyOrder, config: dataManager.storeConfig)

        btManager.sendData(receiptData) { success in
            if success {
                printResultMessage = "Resi pengiriman berhasil dicetak ke struk POS dan otomatis dipotong (Auto-Cut)!"
            } else {
                printResultMessage = "Gagal mencetak struk resi."
            }
            showPrintResultAlert = true
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
