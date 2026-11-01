import SwiftUI

enum TargetPrintType: String, CaseIterable {
    case tspl58x40 = "Stiker 58x40 mm"
    case tspl4x6 = "Label 4x6 Inci"
    case receiptSlip = "Slip Struk 58mm"
}

struct ShippingLabelView: View {
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var btManager = BluetoothManager.shared

    @State private var selectedOrder: Order? = nil
    @State private var isCustomMode: Bool = false
    @State private var selectedPrintFormat: TargetPrintType = .tspl58x40
    
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
    
    // Dialog & Modal State
    @State private var showPrintConfirmDialog: Bool = false
    @State private var showPrintResultAlert: Bool = false
    @State private var printResultMessage: String = ""
    @State private var showEditSheet: Bool = false
    @State private var showAddressBookSheet: Bool = false
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
                LiquidGlassBackground()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 16) {
                        // Source Selector: Order Terakhir vs Buat Label Baru
                        sourcePickerSection

                        // Quick Bar: Format Selector (58x40mm vs 4x6 vs POS Slip)
                        formatSelectorSection

                        // Quick Address Book Bar (Pilih Alamat & Simpan Alamat)
                        addressBookQuickBar

                        // Visual Preview Card (Stiker 58x40 mm / Label 4x6 / Struk 58mm)
                        if selectedPrintFormat == .tspl58x40 {
                            sticker58x40PreviewCard
                        } else if selectedPrintFormat == .tspl4x6 {
                            label4x6PreviewCard
                        } else {
                            slipStrukPreviewCard
                        }

                        // Action Buttons (Print Button with Confirmation Popup)
                        actionButtonsSection

                        // Quick Edit Shipping Details
                        editSummarySection
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                    .padding(.bottom, 32)
                }
            }
            .navigationTitle("Label Pengiriman")
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
            .sheet(isPresented: $showAddressBookSheet) {
                AddressBookView { selectedRecipient in
                    applyRecipient(selectedRecipient)
                }
            }
            .sheet(isPresented: $showPrinterScanSheet) {
                PrinterScanView()
            }
            // Dialog Konfirmasi Sebelum Print (Mencegah Salah Klik)
            .alert("Konfirmasi Cetak", isPresented: $showPrintConfirmDialog) {
                Button("Batal", role: .cancel) { }
                Button("Ya, Cetak Sekarang") {
                    executePrintSelectedFormat()
                }
            } message: {
                Text(confirmationMessageText)
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

    private var confirmationMessageText: String {
        let fmtName = selectedPrintFormat.rawValue
        let rec = activeShippingInfo.recipientName
        let awb = activeShippingInfo.trackingNumber
        let cr = activeShippingInfo.courier
        return "Apakah Anda yakin ingin mencetak:\n\n• Format: \(fmtName)\n• Kurir: \(cr)\n• Penerima: \(rec)\n• No Resi: \(awb)\n\nData akan langsung dikirim ke printer thermal."
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

    private func applyRecipient(_ rec: SavedRecipient) {
        recipientName = rec.name
        recipientPhone = rec.phone
        recipientAddress = rec.address
        postalCode = rec.postalCode
        courier = rec.defaultCourier
        if !rec.notes.isEmpty {
            notes = rec.notes
        }
        isCustomMode = true
        selectedOrder = nil
    }

    // MARK: - Source Selector
    private var sourcePickerSection: some View {
        VStack(alignment: .leading, spacing: 8) {
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
                            Text("Buat Resi Manual")
                                .font(.system(size: 13, weight: .semibold))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
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
                            .padding(.vertical, 7)
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

    // MARK: - Format Selector (58x40mm Stiker vs 4x6 vs POS Slip)
    private var formatSelectorSection: some View {
        HStack(spacing: 8) {
            formatChip(title: "Stiker 58x40 mm", subtitle: "Stiker Tempel", icon: "tag.fill", format: .tspl58x40)
            formatChip(title: "Label 4x6 Inci", subtitle: "Resi Ekspedisi", icon: "shippingbox.fill", format: .tspl4x6)
            formatChip(title: "Struk 58mm", subtitle: "Kertas Roll POS", icon: "printer.fill", format: .receiptSlip)
        }
    }

    private func formatChip(title: String, subtitle: String, icon: String, format: TargetPrintType) -> some View {
        let isSelected = selectedPrintFormat == format
        return Button(action: {
            selectedPrintFormat = format
        }) {
            VStack(spacing: 3) {
                HStack(spacing: 5) {
                    Image(systemName: icon)
                        .font(.system(size: 11))
                    Text(title)
                        .font(.system(size: 11, weight: .bold))
                }
                Text(subtitle)
                    .font(.system(size: 9))
                    .foregroundColor(isSelected ? .white.opacity(0.9) : .white.opacity(0.5))
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(isSelected ? Color.blue : Color.white.opacity(0.08))
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .stroke(isSelected ? Color.cyan : Color.white.opacity(0.15), lineWidth: 1)
            )
            .foregroundColor(.white)
        }
    }

    // MARK: - Address Book Quick Bar
    private var addressBookQuickBar: some View {
        HStack(spacing: 10) {
            Button(action: { showAddressBookSheet = true }) {
                HStack(spacing: 6) {
                    Image(systemName: "person.crop.rectangle.stack.fill")
                        .foregroundColor(.cyan)
                    Text("Buku Alamat Penerima (\(dataManager.savedRecipients.count))")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.10))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.cyan.opacity(0.4), lineWidth: 1)
                )
            }

            Button(action: saveCurrentRecipientToBook) {
                HStack(spacing: 5) {
                    Image(systemName: "square.and.arrow.down.fill")
                        .foregroundColor(.green)
                    Text("Simpan")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color.green.opacity(0.18))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.green.opacity(0.4), lineWidth: 1)
                )
            }
        }
    }

    private func saveCurrentRecipientToBook() {
        let ship = activeShippingInfo
        guard !ship.recipientName.isEmpty else { return }
        let newRecipient = SavedRecipient(
            name: ship.recipientName,
            phone: ship.recipientPhone,
            address: ship.recipientAddress,
            postalCode: ship.postalCode,
            defaultCourier: ship.courier,
            notes: ship.notes
        )
        dataManager.saveOrUpdateRecipient(newRecipient)
        printResultMessage = "Alamat \"\(newRecipient.name)\" berhasil disimpan ke Buku Alamat!"
        showPrintResultAlert = true
    }

    // MARK: - 1. STIKER THERMAL 58x40 MM PREVIEW (High-Contrast Adhesive Label)
    private var sticker58x40PreviewCard: some View {
        let ship = activeShippingInfo
        let sender = dataManager.storeConfig

        return VStack(spacing: 0) {
            // Header Bar: Stiker 58x40 mm Badge
            HStack {
                Text("🏷️ STIKER THERMAL TEMPEL 58 x 40 MM")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(Color.black.opacity(0.8))
                Spacer()
                Text("203 DPI / TSPL")
                    .font(.system(size: 9, weight: .bold, design: .monospaced))
                    .foregroundColor(Color.black.opacity(0.6))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(red: 0.93, green: 0.93, blue: 0.93))

            Divider().background(Color.black)

            // Sticker Content
            VStack(alignment: .leading, spacing: 5) {
                // Courier & Service Row
                HStack(alignment: .center) {
                    Text(ship.courier.uppercased())
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(courierColor(ship.courier))

                    Spacer()

                    Text("[\(ship.service.prefix(8).uppercased())]")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }

                // Barcode Resi Simulation (Code 128)
                VStack(spacing: 2) {
                    barcodeSimulationBars
                        .frame(height: 32)
                        .padding(.horizontal, 4)

                    Text(ship.trackingNumber)
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.black)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 2)

                Divider().background(Color.black.opacity(0.5))

                // Recipient Details (Tujuan Pengiriman)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("Kpd: ")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.black)
                        Text(ship.recipientName)
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(.black)
                            .lineLimit(1)
                    }

                    Text("Telp: \(ship.recipientPhone)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.black)

                    Text(ship.recipientAddress)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.9))
                        .lineLimit(2)

                    if !ship.postalCode.isEmpty {
                        Text("Kode Pos: \(ship.postalCode)")
                            .font(.system(size: 10, weight: .bold, design: .monospaced))
                            .foregroundColor(.black)
                    }
                }

                Divider().background(Color.black.opacity(0.5))

                // Bottom: COD Badge & Pengirim
                HStack(alignment: .center) {
                    if ship.isCOD {
                        Text("COD: \(CurrencyHelper.format(ship.codAmount))")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.black)
                            .cornerRadius(4)
                    } else {
                        Text("NON-COD")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(Color.black)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.black.opacity(0.1))
                            .cornerRadius(4)
                    }

                    Spacer()

                    let sName = ship.senderName.isEmpty ? sender.defaultSenderName : ship.senderName
                    Text("Dr: \(String(sName.prefix(14)))")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.8))
                }
            }
            .padding(10)
        }
        .environment(\.colorScheme, .light)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.black.opacity(0.85), lineWidth: 1.5)
        )
        .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: 5)
        .frame(maxWidth: 380)
    }

    // MARK: - 2. LABEL PENGIRIMAN 4x6 INCI PREVIEW
    private var label4x6PreviewCard: some View {
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
                        .font(.system(size: 18, weight: .black))
                        .foregroundColor(courierColor(ship.courier))
                }

                Spacer()

                Text("[\(ship.service.uppercased())]")
                    .font(.system(size: 13, weight: .black))
                    .foregroundColor(.black)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(Color.black.opacity(0.08))
                    .cornerRadius(6)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(courierColor(ship.courier).opacity(0.12))

            Divider().background(Color.black.opacity(0.8))

            // Barcode & Tracking Number
            VStack(spacing: 5) {
                barcodeSimulationBars
                    .frame(height: 52)
                    .padding(.horizontal, 16)

                Text(ship.trackingNumber)
                    .font(.system(size: 15, weight: .black, design: .monospaced))
                    .foregroundColor(.black)
                    .tracking(2)
            }
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity)
            .background(Color.white)

            Divider().background(Color.black.opacity(0.8))

            // COD & Berat Row
            HStack {
                if ship.isCOD {
                    HStack(spacing: 6) {
                        Text("C O D")
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.black)
                            .cornerRadius(4)
                        Text(CurrencyHelper.format(ship.codAmount))
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(.black)
                    }
                } else {
                    Text("NON-COD")
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(Color.black.opacity(0.8))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }

                Spacer()

                Text("BERAT: \(String(format: "%.1f", ship.weightKg)) KG")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(Color.black.opacity(0.04))

            Divider().background(Color.black.opacity(0.8))

            // Informasi Pengirim
            VStack(alignment: .leading, spacing: 2) {
                Text("PENGIRIM:")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color.black.opacity(0.6))
                
                let sName = ship.senderName.isEmpty ? sender.defaultSenderName : ship.senderName
                let sPhone = ship.senderPhone.isEmpty ? sender.defaultSenderPhone : ship.senderPhone
                let sAddr = ship.senderAddress.isEmpty ? sender.defaultSenderAddress : ship.senderAddress

                Text("\(sName) (\(sPhone))")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)
                Text(sAddr)
                    .font(.system(size: 11))
                    .foregroundColor(Color.black.opacity(0.75))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.black.opacity(0.02))

            Divider().background(Color.black.opacity(0.8))

            // Informasi Penerima (Tujuan Utama)
            VStack(alignment: .leading, spacing: 4) {
                Text("PENERIMA / TUJUAN:")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(Color(red: 0.1, green: 0.4, blue: 0.9))

                Text(ship.recipientName)
                    .font(.system(size: 16, weight: .black))
                    .foregroundColor(.black)

                Text("Telp: \(ship.recipientPhone)")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.black)

                Text(ship.recipientAddress)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundColor(Color.black.opacity(0.9))

                if !ship.postalCode.isEmpty {
                    Text("KODE POS: \(ship.postalCode)")
                        .font(.system(size: 11, weight: .black, design: .monospaced))
                        .foregroundColor(.black)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.08))
                        .cornerRadius(4)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color.white)

            Divider().background(Color.black.opacity(0.8))

            // Detail Barang & Catatan
            VStack(alignment: .leading, spacing: 3) {
                if let ord = selectedOrder {
                    ForEach(ord.items.prefix(3)) { it in
                        Text("• \(it.product.name) (x\(it.quantity))")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(.black)
                            .lineLimit(1)
                    }
                } else {
                    Text("• Paket Pengiriman Retail (x1)")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.black)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(10)
            .background(Color.black.opacity(0.02))

            Divider().background(Color.black.opacity(0.8))

            // Instruksi Khusus
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(Color(red: 0.85, green: 0.4, blue: 0.0))
                    .font(.system(size: 12))
                Text(ship.notes)
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(Color(red: 0.7, green: 0.3, blue: 0.0))
                Spacer()
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color(red: 1.0, green: 0.95, blue: 0.85))

            Divider().background(Color.black.opacity(0.8))

            // Tanda Tangan
            HStack(spacing: 20) {
                VStack(spacing: 22) {
                    Text("Kurir Penjemput")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.6))
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color.black.opacity(0.4))
                }
                VStack(spacing: 22) {
                    Text("Penerima Paket")
                        .font(.system(size: 9, weight: .medium))
                        .foregroundColor(Color.black.opacity(0.6))
                    Rectangle()
                        .frame(height: 1)
                        .foregroundColor(Color.black.opacity(0.4))
                }
            }
            .padding(12)
            .background(Color.white)
        }
        .environment(\.colorScheme, .light)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.black.opacity(0.85), lineWidth: 1.5)
        )
        .shadow(color: Color.black.opacity(0.4), radius: 12, x: 0, y: 6)
    }

    // MARK: - 3. SLIP STRUK EKSPEDISI 58 MM PREVIEW
    private var slipStrukPreviewCard: some View {
        let ship = activeShippingInfo
        let sender = dataManager.storeConfig

        return VStack(spacing: 6) {
            Text("🧾 SLIP EKSPEDISI (STRUK ROLL 58MM)")
                .font(.system(size: 10, weight: .black))
                .foregroundColor(Color.black.opacity(0.7))
                .padding(.top, 8)

            Divider().background(Color.black)

            Text(ship.courier.uppercased())
                .font(.system(size: 16, weight: .black))
                .foregroundColor(.black)
            Text("[\(ship.service.uppercased())]")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.black)

            Text("RESI: \(ship.trackingNumber)")
                .font(.system(size: 12, weight: .black, design: .monospaced))
                .foregroundColor(.black)

            Divider().background(Color.black.opacity(0.5))

            VStack(alignment: .leading, spacing: 3) {
                Text("PENERIMA:")
                    .font(.system(size: 10, weight: .bold))
                Text(ship.recipientName)
                    .font(.system(size: 13, weight: .black))
                Text("Telp: \(ship.recipientPhone)")
                    .font(.system(size: 11))
                Text(ship.recipientAddress)
                    .font(.system(size: 11))
                if !ship.postalCode.isEmpty {
                    Text("Kode Pos: \(ship.postalCode)")
                        .font(.system(size: 11, weight: .bold))
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 8)

            Divider().background(Color.black.opacity(0.5))

            if ship.isCOD {
                Text("*** COD: \(CurrencyHelper.format(ship.codAmount)) ***")
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(.black)
            } else {
                Text("*** NON-COD ***")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.black)
            }

            Text("--- AUTO-CUT SETELAH CETAK ---")
                .font(.system(size: 9))
                .foregroundColor(Color.black.opacity(0.5))
                .padding(.bottom, 8)
        }
        .environment(\.colorScheme, .light)
        .padding(.horizontal, 10)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color.black.opacity(0.7), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.4), radius: 10, x: 0, y: 5)
        .frame(maxWidth: 320)
    }

    // MARK: - Barcode Simulation
    private var barcodeSimulationBars: some View {
        HStack(spacing: 3) {
            ForEach(0..<28) { i in
                Rectangle()
                    .fill(Color.black)
                    .frame(width: (i % 3 == 0) ? 3.5 : (i % 2 == 0 ? 1.5 : 2.5))
            }
        }
    }

    private func courierColor(_ courierName: String) -> Color {
        let name = courierName.lowercased()
        if name.contains("sicepat") {
            return Color(red: 0.85, green: 0.1, blue: 0.1)
        } else if name.contains("j&t") {
            return Color(red: 0.9, green: 0.15, blue: 0.15)
        } else if name.contains("jne") {
            return Color(red: 0.1, green: 0.25, blue: 0.75)
        } else if name.contains("shopee") {
            return Color(red: 0.95, green: 0.35, blue: 0.1)
        } else if name.contains("gosend") || name.contains("gojek") {
            return Color(red: 0.05, green: 0.65, blue: 0.25)
        } else {
            return Color(red: 0.15, green: 0.45, blue: 0.95)
        }
    }

    // MARK: - Action Buttons
    private var actionButtonsSection: some View {
        VStack(spacing: 12) {
            // Main Print Button dynamically matching selected format
            let printButtonTitle = "Cetak \(selectedPrintFormat.rawValue)"
            let printIcon = selectedPrintFormat == .tspl58x40 ? "tag.fill" : (selectedPrintFormat == .tspl4x6 ? "shippingbox.fill" : "printer.fill")
            let tint = selectedPrintFormat == .tspl58x40 ? Color(red: 0.1, green: 0.7, blue: 0.3) : Color(red: 0.12, green: 0.55, blue: 0.98)

            LiquidGlassButton(
                title: printButtonTitle,
                icon: printIcon,
                tintColor: btManager.isConnected ? tint : Color.gray,
                isEnabled: true
            ) {
                showPrintConfirmDialog = true
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

    // MARK: - Edit Summary Section
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
                Section {
                    Button(action: {
                        showEditSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                            showAddressBookSheet = true
                        }
                    }) {
                        HStack {
                            Image(systemName: "person.crop.rectangle.stack.fill")
                                .foregroundColor(.blue)
                            Text("Pilih dari Buku Alamat Penerima")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.blue)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }

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

                Section {
                    Button(action: {
                        saveCurrentRecipientToBook()
                        showEditSheet = false
                    }) {
                        HStack {
                            Image(systemName: "square.and.arrow.down.fill")
                                .foregroundColor(.green)
                            Text("Simpan Data Ini ke Buku Alamat")
                                .foregroundColor(.green)
                        }
                    }
                    .disabled(recipientName.trimmingCharacters(in: .whitespaces).isEmpty || recipientAddress.trimmingCharacters(in: .whitespaces).isEmpty)
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

    // MARK: - Print Execution (Dispatched after Confirmation)
    private func executePrintSelectedFormat() {
        guard btManager.isConnected else {
            printResultMessage = "Printer Bluetooth belum terhubung. Silakan buka menu Pengaturan atau tap 'Hubungkan' terlebih dahulu."
            showPrintResultAlert = true
            return
        }

        let dummyOrder = createWorkingOrder()
        let dataToPrint: Data

        switch selectedPrintFormat {
        case .tspl58x40:
            dataToPrint = PrinterEngine.buildTSPLSticker58x40Data(order: dummyOrder, config: dataManager.storeConfig)
        case .tspl4x6:
            dataToPrint = PrinterEngine.buildTSPLLabelData(order: dummyOrder, config: dataManager.storeConfig)
        case .receiptSlip:
            dataToPrint = PrinterEngine.buildShippingSlip58Data(order: dummyOrder, config: dataManager.storeConfig)
        }

        btManager.sendData(dataToPrint) { success in
            if success {
                let fmt = selectedPrintFormat.rawValue
                printResultMessage = "\(fmt) berhasil dikirim ke printer thermal!"
            } else {
                printResultMessage = "Gagal mengirim data cetak ke printer."
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
