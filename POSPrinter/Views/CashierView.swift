import SwiftUI
import AVFoundation
import AudioToolbox

struct CashierView: View {
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var btManager = BluetoothManager.shared

    @State private var searchQuery: String = ""
    @State private var selectedCategory: String = "Semua"
    @State private var cart: [OrderItem] = []
    @State private var showCartSheet: Bool = false
    @State private var showPaymentSheet: Bool = false
    @State private var showShippingModal: Bool = false
    @State private var showBarcodeScannerSheet: Bool = false
    @State private var scanFeedbackMessage: String = ""
    @State private var showScanFeedbackToast: Bool = false
    
    // Transaksi State
    @State private var orderType: OrderType = .offline
    @State private var shippingInfo: ShippingInfo = ShippingInfo()
    @State private var discountAmount: Double = 0.0
    @State private var paymentMethod: PaymentMethod = .cash
    @State private var cashGiven: String = ""
    @State private var lastCompletedOrder: Order? = nil
    @State private var showSuccessModal: Bool = false
    @State private var showPrintReceiptConfirm: Bool = false
    @State private var showPrintLabelConfirm: Bool = false
    @State private var showPrintStickerConfirm: Bool = false
    @State private var showAddressBookSheet: Bool = false
    @State private var printStatusMsg: String = ""
    @State private var showPrintStatusAlert: Bool = false

    var filteredProducts: [Product] {
        dataManager.products.filter { p in
            let matchCategory = (selectedCategory == "Semua" || p.category == selectedCategory)
            let matchQuery = searchQuery.isEmpty || p.name.localizedCaseInsensitiveContains(searchQuery) || p.sku.localizedCaseInsensitiveContains(searchQuery) || p.barcode.localizedCaseInsensitiveContains(searchQuery)
            return matchCategory && matchQuery
        }
    }

    var cartTotalItems: Int {
        cart.reduce(0) { $0 + $1.quantity }
    }

    var cartSubtotal: Double {
        cart.reduce(0) { $0 + $1.subtotal }
    }

    var cartTax: Double {
        guard dataManager.storeConfig.isTaxEnabled else { return 0 }
        let afterDisc = max(0, cartSubtotal - discountAmount)
        return afterDisc * (dataManager.storeConfig.taxRate / 100.0)
    }

    var cartGrandTotal: Double {
        max(0, cartSubtotal - discountAmount) + cartTax
    }

    let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                VStack(spacing: 0) {
                    // Top Search Bar
                    searchBarSection

                    // Category Chips Selector
                    categoryChipsSection

                    // Product Grid View
                    ScrollView(showsIndicators: false) {
                        if filteredProducts.isEmpty {
                            emptyStateView
                        } else {
                            LazyVGrid(columns: columns, spacing: 14) {
                                ForEach(filteredProducts) { product in
                                    ProductCard(product: product) {
                                        addToCart(product)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                            .padding(.bottom, 90) // space for floating cart bar
                        }
                    }
                }
            }
            .navigationTitle("Kasir POS")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showCartSheet = true }) {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "cart.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.cyan)

                            if cartTotalItems > 0 {
                                Text("\(cartTotalItems)")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(4)
                                    .background(Color.red)
                                    .clipShape(Circle())
                                    .offset(x: 10, y: -8)
                            }
                        }
                    }
                }
            }
            .overlay(alignment: .bottom) {
                if !cart.isEmpty {
                    floatingCartBar
                }
            }
            .sheet(isPresented: $showCartSheet) {
                cartSheetView
            }
            .sheet(isPresented: $showPaymentSheet) {
                paymentSheetView
            }
            .sheet(isPresented: $showShippingModal) {
                shippingInputSheet
            }
            .sheet(isPresented: $showAddressBookSheet) {
                AddressBookView { selected in
                    shippingInfo.recipientName = selected.name
                    shippingInfo.recipientPhone = selected.phone
                    shippingInfo.recipientAddress = selected.address
                    shippingInfo.postalCode = selected.postalCode
                    shippingInfo.courier = selected.defaultCourier
                    shippingInfo.notes = selected.notes
                }
            }
            .sheet(isPresented: $showSuccessModal) {
                successModalView
            }
            .sheet(isPresented: $showBarcodeScannerSheet) {
                BarcodeScannerModal(onScanned: { code in
                    let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
                    if let matched = dataManager.products.first(where: {
                        (!$0.barcode.isEmpty && $0.barcode.caseInsensitiveCompare(trimmed) == .orderedSame) ||
                        (!$0.sku.isEmpty && $0.sku.caseInsensitiveCompare(trimmed) == .orderedSame)
                    }) {
                        addToCart(matched)
                        scanFeedbackMessage = "+1 \(matched.name)"
                        showScanFeedbackToast = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            showScanFeedbackToast = false
                        }
                    } else {
                        scanFeedbackMessage = "Barcode \(trimmed) tidak ditemukan"
                        showScanFeedbackToast = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            showScanFeedbackToast = false
                        }
                    }
                })
            }
            .alert("Status Cetak", isPresented: $showPrintStatusAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(printStatusMsg)
            }
        }
    }

    // MARK: - Search Bar with Barcode Scanner & Hardware Scanner Auto-Add
    private var searchBarSection: some View {
        HStack(spacing: 8) {
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.cyan)
                TextField("Cari / scan barcode...", text: $searchQuery)
                    .foregroundColor(.white)
                    .onChange(of: searchQuery) { newQuery in
                        let trimmed = newQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                        if trimmed.count >= 6 {
                            if let matched = dataManager.products.first(where: {
                                !$0.barcode.isEmpty && $0.barcode.caseInsensitiveCompare(trimmed) == .orderedSame
                            }) {
                                addToCart(matched)
                                let generator = UINotificationFeedbackGenerator()
                                generator.notificationOccurred(.success)
                                searchQuery = ""
                            }
                        }
                    }
                if !searchQuery.isEmpty {
                    Button(action: { searchQuery = "" }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.gray)
                    }
                }
            }
            .padding(11)
            .background(Color.white.opacity(0.08))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.white.opacity(0.18), lineWidth: 1)
            )

            // Tombol Kamera Barcode Scanner
            Button(action: { showBarcodeScannerSheet = true }) {
                Image(systemName: "barcode.viewfinder")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 44, height: 44)
                    .background(
                        LinearGradient(colors: [Color.blue, Color.cyan], startPoint: .topLeading, endPoint: .bottomTrailing)
                    )
                    .cornerRadius(12)
                    .shadow(color: .cyan.opacity(0.4), radius: 6, x: 0, y: 3)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .overlay(alignment: .top) {
            if showScanFeedbackToast {
                Text(scanFeedbackMessage)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(scanFeedbackMessage.contains("tidak") ? Color.red : Color.green)
                    .cornerRadius(20)
                    .shadow(radius: 6)
                    .offset(y: -40)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .animation(.easeInOut, value: showScanFeedbackToast)
            }
        }
    }

    // MARK: - Category Chips
    private var categoryChipsSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(dataManager.categories, id: \.self) { cat in
                    let isSelected = selectedCategory == cat
                    Button(action: {
                        selectedCategory = cat
                    }) {
                        Text(cat)
                            .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(
                                isSelected
                                    ? LinearGradient(colors: [Color.blue, Color.cyan], startPoint: .topLeading, endPoint: .bottomTrailing)
                                    : LinearGradient(colors: [Color.white.opacity(0.09), Color.white.opacity(0.05)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            )
                            .foregroundColor(.white)
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(isSelected ? Color.cyan.opacity(0.8) : Color.white.opacity(0.12), lineWidth: 1)
                            )
                            .shadow(color: isSelected ? Color.blue.opacity(0.35) : Color.clear, radius: 4, y: 2)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
    }

    // MARK: - Empty State
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: "tray")
                .font(.system(size: 50))
                .foregroundColor(.gray.opacity(0.6))
            Text("Tidak ada produk ditemukan")
                .font(.headline)
                .foregroundColor(.gray)
            Text("Tambahkan produk di menu Produk & Stok.")
                .font(.subheadline)
                .foregroundColor(.gray.opacity(0.8))
        }
        .padding(.top, 80)
    }

    // MARK: - Floating Cart Bar
    private var floatingCartBar: some View {
        Button(action: { showCartSheet = true }) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(cartTotalItems) Produk di Keranjang")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.85))
                    Text(CurrencyHelper.format(cartGrandTotal))
                        .font(.headline)
                        .foregroundColor(.white)
                }

                Spacer()

                HStack(spacing: 6) {
                    Text("Buka Kasir")
                        .font(.subheadline.bold())
                    Image(systemName: "arrow.right.circle.fill")
                        .font(.title3)
                }
                .foregroundColor(.white)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(
                LinearGradient(colors: [Color.blue, Color(red: 0.1, green: 0.4, blue: 0.9)],
                               startPoint: .leading, endPoint: .trailing)
            )
            .cornerRadius(16)
            .shadow(color: Color.blue.opacity(0.35), radius: 8, y: 4)
            .padding(.horizontal, 16)
            .padding(.bottom, 12)
        }
    }

    // MARK: - Cart Sheet
    private var cartSheetView: some View {
        NavigationView {
            VStack(spacing: 0) {
                // Order Type Selector (Offline vs Shipping)
                Picker("Tipe Pesanan", selection: $orderType) {
                    ForEach(OrderType.allCases, id: \.self) { type in
                        Text(type.rawValue).tag(type)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                // Shipping info quick banner if shipping chosen
                if orderType == .shipping {
                    Button(action: { showShippingModal = true }) {
                        HStack {
                            Image(systemName: "shippingbox.fill")
                                .foregroundColor(.orange)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(shippingInfo.recipientName.isEmpty ? "Isi Data Alamat & Kurir Pengiriman" : "Tujuan: \(shippingInfo.recipientName) (\(shippingInfo.courier))")
                                    .font(.subheadline.bold())
                                    .foregroundColor(.primary)
                                Text(shippingInfo.isCOD ? "COD: \(CurrencyHelper.format(shippingInfo.codAmount))" : "Paket Standar Non-COD")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                        }
                        .padding(12)
                        .background(Color.orange.opacity(0.12))
                        .cornerRadius(10)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                    }
                }

                // Cart Item List
                List {
                    ForEach(cart) { item in
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(item.product.name)
                                    .font(.headline)
                                Text(CurrencyHelper.format(item.product.price) + " / " + item.product.unit)
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            HStack(spacing: 10) {
                                Button(action: { decreaseItem(item) }) {
                                    Image(systemName: "minus.circle.fill")
                                        .font(.title3)
                                        .foregroundColor(.red.opacity(0.8))
                                }

                                Text("\(item.quantity)")
                                    .font(.headline)
                                    .frame(minWidth: 24)

                                Button(action: { increaseItem(item) }) {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.title3)
                                        .foregroundColor(.blue)
                                }
                            }

                            Text(CurrencyHelper.format(item.subtotal))
                                .font(.subheadline.bold())
                                .frame(minWidth: 80, alignment: .trailing)
                        }
                        .padding(.vertical, 4)
                    }
                    .onDelete(perform: removeCartItems)
                }
                .listStyle(.insetGrouped)

                // Summary & Checkout
                VStack(spacing: 8) {
                    HStack {
                        Text("Subtotal")
                        Spacer()
                        Text(CurrencyHelper.format(cartSubtotal))
                    }
                    .font(.subheadline)
                    .foregroundColor(.secondary)

                    if dataManager.storeConfig.isTaxEnabled {
                        HStack {
                            Text("PPN (\(Int(dataManager.storeConfig.taxRate))%)")
                            Spacer()
                            Text(CurrencyHelper.format(cartTax))
                        }
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    }

                    HStack {
                        Text("Total Pembayaran")
                            .font(.headline)
                        Spacer()
                        Text(CurrencyHelper.format(cartGrandTotal))
                            .font(.title3.bold())
                            .foregroundColor(.blue)
                    }

                    Button(action: {
                        showCartSheet = false
                        showPaymentSheet = true
                    }) {
                        Text("Lanjutkan ke Pembayaran")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color.blue)
                            .cornerRadius(12)
                    }
                    .padding(.top, 4)
                }
                .padding(16)
                .background(Color(UIColor.secondarySystemGroupedBackground))
            }
            .navigationTitle("Keranjang Belanja")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Tutup") { showCartSheet = false }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !cart.isEmpty {
                        Button("Kosongkan") { cart.removeAll() }
                            .foregroundColor(.red)
                    }
                }
            }
        }
    }

    // MARK: - Shipping Input Sheet
    private var shippingInputSheet: some View {
        NavigationView {
            Form {
                Section {
                    Button(action: { showAddressBookSheet = true }) {
                        HStack {
                            Image(systemName: "person.crop.rectangle.stack.fill")
                                .foregroundColor(.blue)
                            Text("Pilih dari Buku Alamat Tersimpan")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.blue)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundColor(.gray)
                        }
                    }
                }

                Section(header: Text("Ekspedisi & Kurir")) {
                    Picker("Kurir", selection: $shippingInfo.courier) {
                        ForEach(["J&T Express", "JNE", "SiCepat", "Shopee Xpress", "GoSend", "GrabExpress", "Anteraja", "Lion Parcel"], id: \.self) {
                            Text($0)
                        }
                    }
                    Picker("Layanan", selection: $shippingInfo.service) {
                        ForEach(["REGULER", "NEXT DAY", "CARGO", "HEMAT", "INSTANT", "SAMEDAY"], id: \.self) {
                            Text($0)
                        }
                    }
                    TextField("No. Resi / AWB (Opsional/Auto)", text: $shippingInfo.trackingNumber)
                    HStack {
                        Text("Berat Paket (Kg)")
                        Spacer()
                        TextField("1.0", value: $shippingInfo.weightKg, format: .number)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                }

                Section(header: Text("Tujuan & Penerima")) {
                    TextField("Nama Penerima", text: $shippingInfo.recipientName)
                    TextField("No. Telepon / WA Penerima", text: $shippingInfo.recipientPhone)
                        .keyboardType(.phonePad)
                    TextEditor(text: $shippingInfo.recipientAddress)
                        .frame(minHeight: 60)
                    TextField("Kode Pos", text: $shippingInfo.postalCode)
                        .keyboardType(.numberPad)
                }

                Section(header: Text("Metode COD (Cash on Delivery)")) {
                    Toggle("Paket COD", isOn: $shippingInfo.isCOD)
                    if shippingInfo.isCOD {
                        HStack {
                            Text("Nominal Tagihan COD")
                            Spacer()
                            TextField("0", value: $shippingInfo.codAmount, format: .number)
                                .keyboardType(.numberPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }

                Section(header: Text("Instruksi Pengiriman")) {
                    TextField("e.g. Fragile / Jangan Dibanting", text: $shippingInfo.notes)
                }

                Section {
                    Button(action: {
                        guard !shippingInfo.recipientName.isEmpty else { return }
                        let saved = SavedRecipient(
                            name: shippingInfo.recipientName,
                            phone: shippingInfo.recipientPhone,
                            address: shippingInfo.recipientAddress,
                            postalCode: shippingInfo.postalCode,
                            defaultCourier: shippingInfo.courier,
                            notes: shippingInfo.notes
                        )
                        dataManager.saveOrUpdateRecipient(saved)
                        printStatusMsg = "Alamat \(saved.name) berhasil disimpan ke Buku Alamat!"
                        showPrintStatusAlert = true
                    }) {
                        HStack {
                            Image(systemName: "square.and.arrow.down.fill")
                                .foregroundColor(.green)
                            Text("Simpan Penerima Ini ke Buku Alamat")
                                .foregroundColor(.green)
                        }
                    }
                    .disabled(shippingInfo.recipientName.isEmpty || shippingInfo.recipientAddress.isEmpty)
                }
            }
            .navigationTitle("Data Pengiriman Paket")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Simpan") {
                        if shippingInfo.isCOD && shippingInfo.codAmount == 0 {
                            shippingInfo.codAmount = cartGrandTotal
                        }
                        showShippingModal = false
                    }
                    .font(.headline)
                }
            }
        }
    }

    // MARK: - Payment Sheet View
    private var paymentSheetView: some View {
        NavigationView {
            VStack(spacing: 16) {
                VStack(spacing: 6) {
                    Text("Total Tagihan")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Text(CurrencyHelper.format(cartGrandTotal))
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(.blue)
                }
                .padding(.top, 10)

                // Metode Bayar
                Picker("Metode Pembayaran", selection: $paymentMethod) {
                    ForEach(PaymentMethod.allCases, id: \.self) { method in
                        Text(method.rawValue).tag(method)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 16)

                if paymentMethod == .cash {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Uang Diterima:")
                            .font(.caption.bold())
                            .foregroundColor(.secondary)

                        TextField("Rp 0", text: $cashGiven)
                            .font(.title2.bold())
                            .keyboardType(.numberPad)
                            .padding(12)
                            .background(Color(UIColor.secondarySystemGroupedBackground))
                            .cornerRadius(10)

                        // Quick cash chips
                        HStack(spacing: 8) {
                            Button("Uang Pas") {
                                cashGiven = "\(Int(cartGrandTotal))"
                            }
                            .buttonStyle(QuickCashStyle())

                            Button("50.000") { cashGiven = "50000" }
                                .buttonStyle(QuickCashStyle())

                            Button("100.000") { cashGiven = "100000" }
                                .buttonStyle(QuickCashStyle())

                            Button("200.000") { cashGiven = "200000" }
                                .buttonStyle(QuickCashStyle())
                        }

                        let given = Double(cashGiven) ?? 0
                        let change = max(0, given - cartGrandTotal)
                        HStack {
                            Text("Kembalian:")
                                .font(.headline)
                            Spacer()
                            Text(CurrencyHelper.format(change))
                                .font(.title3.bold())
                                .foregroundColor(given >= cartGrandTotal ? .green : .red)
                        }
                        .padding(.top, 8)
                    }
                    .padding(.horizontal, 16)
                }

                Spacer()

                // Final Pay & Print Button
                Button(action: finalizeOrder) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                        Text("Selesaikan & Cetak Struk")
                    }
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.green)
                    .cornerRadius(12)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
            }
            .navigationTitle("Pembayaran")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Kembali") { showPaymentSheet = false }
                }
            }
        }
    }

    // MARK: - Success Modal
    private var successModalView: some View {
        ZStack {
            LiquidGlassBackground()

            VStack(spacing: 22) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 68))
                    .foregroundColor(.green)
                    .shadow(color: .green.opacity(0.4), radius: 10, y: 5)

                Text("Transaksi Berhasil!")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.white)

                if let order = lastCompletedOrder {
                    VStack(spacing: 6) {
                        Text(order.invoiceNumber)
                            .font(.system(size: 13, weight: .semibold, design: .monospaced))
                            .foregroundColor(.white.opacity(0.7))
                        Text(order.formattedTotal)
                            .font(.system(size: 28, weight: .black))
                            .foregroundColor(.cyan)
                    }
                    .padding(.vertical, 8)

                    Divider().background(Color.white.opacity(0.2))

                    VStack(spacing: 12) {
                        LiquidGlassButton(
                            title: "Cetak Struk Thermal (Auto-Cut)",
                            icon: "printer.fill",
                            tintColor: Color(red: 0.15, green: 0.45, blue: 0.95)
                        ) {
                            showPrintReceiptConfirm = true
                        }

                        if order.orderType == .shipping {
                            LiquidGlassButton(
                                title: "Cetak Stiker 58x40 mm (TSPL)",
                                icon: "tag.fill",
                                tintColor: Color(red: 0.1, green: 0.7, blue: 0.3)
                            ) {
                                showPrintStickerConfirm = true
                            }

                            LiquidGlassButton(
                                title: "Cetak Label Pengiriman 4x6",
                                icon: "shippingbox.fill",
                                tintColor: Color(red: 0.95, green: 0.45, blue: 0.05)
                            ) {
                                showPrintLabelConfirm = true
                            }
                        }
                    }
                    .padding(.horizontal, 24)
                }

                Spacer()

                Button(action: { showSuccessModal = false }) {
                    Text("Transaksi Baru (Selesai)")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white.opacity(0.85))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(Color.white.opacity(0.12))
                        .cornerRadius(12)
                }
                .padding(.bottom, 24)
            }
            .padding(.top, 40)
        }
        .alert("Konfirmasi Cetak Struk", isPresented: $showPrintReceiptConfirm) {
            Button("Batal", role: .cancel) { }
            Button("Ya, Cetak") {
                if let ord = lastCompletedOrder {
                    printReceipt(ord)
                }
            }
        } message: {
            Text("Apakah Anda yakin ingin mencetak struk untuk invoice \(lastCompletedOrder?.invoiceNumber ?? "")?")
        }
        .alert("Konfirmasi Cetak Label 4x6", isPresented: $showPrintLabelConfirm) {
            Button("Batal", role: .cancel) { }
            Button("Ya, Cetak") {
                if let ord = lastCompletedOrder {
                    printShippingLabel(ord)
                }
            }
        } message: {
            Text("Apakah Anda yakin ingin mencetak label resi 4x6 untuk \(lastCompletedOrder?.shippingInfo?.trackingNumber ?? "")?")
        }
        .alert("Konfirmasi Cetak Stiker 58x40", isPresented: $showPrintStickerConfirm) {
            Button("Batal", role: .cancel) { }
            Button("Ya, Cetak") {
                if let ord = lastCompletedOrder {
                    printShippingSticker58x40(ord)
                }
            }
        } message: {
            Text("Apakah Anda yakin ingin mencetak stiker thermal 58x40 mm untuk \(lastCompletedOrder?.shippingInfo?.recipientName ?? "")?")
        }
    }

    // MARK: - Helper Actions
    private func addToCart(_ product: Product) {
        if let idx = cart.firstIndex(where: { $0.product.id == product.id }) {
            cart[idx].quantity += 1
        } else {
            cart.append(OrderItem(product: product, quantity: 1))
        }
    }

    private func increaseItem(_ item: OrderItem) {
        if let idx = cart.firstIndex(where: { $0.id == item.id }) {
            cart[idx].quantity += 1
        }
    }

    private func decreaseItem(_ item: OrderItem) {
        if let idx = cart.firstIndex(where: { $0.id == item.id }) {
            if cart[idx].quantity > 1 {
                cart[idx].quantity -= 1
            } else {
                cart.remove(at: idx)
            }
        }
    }

    private func removeCartItems(at offsets: IndexSet) {
        cart.remove(atOffsets: offsets)
    }

    private func finalizeOrder() {
        let givenAmount = Double(cashGiven) ?? cartGrandTotal
        let order = Order(
            items: cart,
            discount: discountAmount,
            taxRate: dataManager.storeConfig.isTaxEnabled ? dataManager.storeConfig.taxRate : 0,
            paymentMethod: paymentMethod,
            paymentAmount: givenAmount,
            orderType: orderType,
            shippingInfo: orderType == .shipping ? shippingInfo : nil
        )

        dataManager.processOrder(order)
        lastCompletedOrder = order

        // Clear state (tanpa auto-print kaget, konfirmasi di success modal)
        cart.removeAll()
        discountAmount = 0
        cashGiven = ""
        shippingInfo = ShippingInfo()
        orderType = .offline
        showPaymentSheet = false
        showSuccessModal = true
    }

    private func printReceipt(_ order: Order) {
        guard btManager.isConnected else {
            printStatusMsg = "Printer Bluetooth belum terhubung. Hubungkan printer di menu Pengaturan."
            showPrintStatusAlert = true
            return
        }
        let data = PrinterEngine.buildReceiptData(order: order, config: dataManager.storeConfig)
        btManager.sendData(data) { success in
            printStatusMsg = success ? "Struk kasir berhasil dicetak dan dipotong (Auto-Cut)!" : "Gagal mencetak struk."
            showPrintStatusAlert = true
        }
    }

    private func printShippingLabel(_ order: Order) {
        guard btManager.isConnected else {
            printStatusMsg = "Printer Bluetooth belum terhubung. Hubungkan printer di menu Pengaturan."
            showPrintStatusAlert = true
            return
        }
        let data = PrinterEngine.buildTSPLLabelData(order: order, config: dataManager.storeConfig)
        btManager.sendData(data) { success in
            printStatusMsg = success ? "Label pengiriman 4x6 berhasil dikirim ke printer label!" : "Gagal mencetak label."
            showPrintStatusAlert = true
        }
    }

    private func printShippingSticker58x40(_ order: Order) {
        guard btManager.isConnected else {
            printStatusMsg = "Printer Bluetooth belum terhubung. Hubungkan printer di menu Pengaturan."
            showPrintStatusAlert = true
            return
        }
        let data = PrinterEngine.buildTSPLSticker58x40Data(order: order, config: dataManager.storeConfig)
        btManager.sendData(data) { success in
            printStatusMsg = success ? "Stiker thermal 58x40 mm berhasil dikirim ke printer!" : "Gagal mencetak stiker."
            showPrintStatusAlert = true
        }
    }
}

// MARK: - Product Card Component (Liquid Glass Aesthetic)
struct ProductCard: View {
    let product: Product
    let onAddToCart: () -> Void

    var body: some View {
        Button(action: onAddToCart) {
            VStack(alignment: .leading, spacing: 8) {
                // Product Image or Icon
                ZStack(alignment: .topTrailing) {
                    if let b64 = product.imageBase64, let uiImage = ImageHelper.fromBase64(b64) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(height: 110)
                            .clipped()
                            .cornerRadius(10)
                    } else {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(LinearGradient(colors: [Color.blue.opacity(0.18), Color.purple.opacity(0.18)], startPoint: .topLeading, endPoint: .bottomTrailing))
                            .frame(height: 110)
                            .overlay(
                                Image(systemName: categoryIcon(product.category))
                                    .font(.system(size: 38))
                                    .foregroundColor(.cyan.opacity(0.85))
                            )
                    }

                    // Stock Pill
                    Text("Stok: \(product.stock)")
                        .font(.system(size: 10, weight: .bold))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(product.isLowStock ? Color.red.opacity(0.9) : Color.black.opacity(0.7))
                        .foregroundColor(.white)
                        .cornerRadius(8)
                        .padding(6)
                }

                // Info
                VStack(alignment: .leading, spacing: 3) {
                    Text(product.name)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)

                    Text(product.formattedPrice)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.cyan)

                    Text(product.sku)
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.55))
                }
                .padding(.horizontal, 4)
                .padding(.bottom, 6)
            }
            .padding(8)
            .background(Color.white.opacity(0.08))
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color.white.opacity(0.18), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.2), radius: 5, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private func categoryIcon(_ cat: String) -> String {
        switch cat.lowercased() {
        case "makanan": return "fork.knife"
        case "minuman": return "cup.and.saucer.fill"
        case "snack": return "takeoutbag.and.cup.and.straw.fill"
        case "pakaian": return "tshirt.fill"
        case "elektronik": return "cable.connector.horizontal"
        case "aksesoris": return "eyeglasses"
        default: return "bag.fill"
        }
    }
}

struct QuickCashStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.caption.bold())
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(configuration.isPressed ? Color.blue.opacity(0.2) : Color(UIColor.systemGray5))
            .foregroundColor(.primary)
            .cornerRadius(8)
    }
}

// MARK: - Barcode Scanner Camera Modal (Vision / AVFoundation)
struct BarcodeScannerModal: View {
    @Environment(\.presentationMode) private var presentationMode
    var onScanned: (String) -> Void
    @State private var lastScannedCode: String = ""

    var body: some View {
        NavigationView {
            ZStack {
                Color.black.edgesIgnoringSafeArea(.all)

                BarcodeScannerView { code in
                    lastScannedCode = code
                    onScanned(code)
                }
                .edgesIgnoringSafeArea(.all)

                // Viewfinder Reticle Overlay
                VStack {
                    Text("Arahkan kamera ke stiker barcode produk")
                        .font(.subheadline)
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color.black.opacity(0.6))
                        .cornerRadius(20)
                        .padding(.top, 24)

                    Spacer()

                    // Target Box
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color.cyan, lineWidth: 3)
                            .frame(width: 280, height: 180)

                        Rectangle()
                            .fill(Color.red.opacity(0.8))
                            .frame(width: 250, height: 2)
                    }

                    Spacer()

                    if !lastScannedCode.isEmpty {
                        Text("Terdeteksi: \(lastScannedCode)")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.green)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color.black.opacity(0.8))
                            .cornerRadius(12)
                            .padding(.bottom, 20)
                    }
                }
            }
            .navigationTitle("Pindai Barcode Kasir")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Selesai") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(.cyan)
                    .font(.body.bold())
                }
            }
        }
    }
}

struct BarcodeScannerView: UIViewControllerRepresentable {
    var onScanned: (String) -> Void

    func makeUIViewController(context: Context) -> BarcodeScannerViewController {
        let vc = BarcodeScannerViewController()
        vc.onScanned = onScanned
        return vc
    }

    func updateUIViewController(_ uiViewController: BarcodeScannerViewController, context: Context) {}
}

class BarcodeScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var captureSession: AVCaptureSession?
    var previewLayer: AVCaptureVideoPreviewLayer?
    var onScanned: ((String) -> Void)?
    var isProcessing = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupCamera()
    }

    private func setupCamera() {
        let session = AVCaptureSession()
        guard let device = AVCaptureDevice.default(for: .video) else { return }
        guard let input = try? AVCaptureDeviceInput(device: device) else { return }

        if session.canAddInput(input) {
            session.addInput(input)
        }

        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            output.metadataObjectTypes = [.code128, .ean13, .ean8, .qr, .code39]
        }

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.frame = view.layer.bounds
        preview.videoGravity = .resizeAspectFill
        view.layer.addSublayer(preview)
        previewLayer = preview

        self.captureSession = session
        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession?.isRunning == true {
            captureSession?.stopRunning()
        }
    }

    func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard !isProcessing,
              let first = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let str = first.stringValue else { return }

        isProcessing = true
        AudioServicesPlaySystemSound(1057) // Beep
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        onScanned?(str)

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            self?.isProcessing = false
        }
    }
}
