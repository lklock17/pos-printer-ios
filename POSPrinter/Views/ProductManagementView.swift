import SwiftUI

struct ProductManagementView: View {
    @ObservedObject var dataManager = DataManager.shared

    @State private var searchQuery: String = ""
    @State private var selectedCategory: String = "Semua"
    @State private var stockFilter: Int = 0 // 0: Semua, 1: Menipis (<=5), 2: Habis (0)
    
    @State private var showAddProductSheet: Bool = false
    @State private var selectedProductToEdit: Product? = nil
    @State private var productToDelete: Product? = nil
    @State private var showDeleteConfirm: Bool = false
    @State private var productToPrintBarcode: Product? = nil

    var filteredProducts: [Product] {
        dataManager.products.filter { p in
            let matchCategory = (selectedCategory == "Semua" || p.category == selectedCategory)
            let matchQuery = searchQuery.isEmpty ||
                p.name.localizedCaseInsensitiveContains(searchQuery) ||
                p.sku.localizedCaseInsensitiveContains(searchQuery) ||
                p.barcode.localizedCaseInsensitiveContains(searchQuery)
            
            let matchStock: Bool
            switch stockFilter {
            case 1: matchStock = p.isLowStock && !p.isOutOfStock
            case 2: matchStock = p.isOutOfStock
            default: matchStock = true
            }

            return matchCategory && matchQuery && matchStock
        }
    }

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                VStack(spacing: 0) {
                    // Header Bar Pencarian
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.cyan)
                        TextField("Cari produk, SKU, barcode...", text: $searchQuery)
                            .foregroundColor(.white)
                        if !searchQuery.isEmpty {
                            Button(action: { searchQuery = "" }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.gray)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.white.opacity(0.18), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)

                    // Stock Filter Tabs
                    Picker("Filter Stok", selection: $stockFilter) {
                        Text("Semua (\(dataManager.products.count))").tag(0)
                        Text("Menipis (\(dataManager.products.filter { $0.isLowStock && !$0.isOutOfStock }.count))").tag(1)
                        Text("Habis (\(dataManager.products.filter { $0.isOutOfStock }.count))").tag(2)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)

                    // Kategori Chips Horizontal
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(dataManager.categories, id: \.self) { cat in
                                let isSelected = (selectedCategory == cat)
                                Button(action: { selectedCategory = cat }) {
                                    Text(cat)
                                        .font(.system(size: 13, weight: isSelected ? .bold : .regular))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 7)
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
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 8)
                    }

                    // List Produk
                    if filteredProducts.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "cube.box")
                                .font(.system(size: 50))
                                .foregroundColor(.white.opacity(0.4))
                            Text("Tidak ada produk ditemukan")
                                .font(.headline)
                                .foregroundColor(.white)
                            Text("Coba cari kata kunci lain atau tambahkan produk baru.")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 32)
                        }
                        Spacer()
                    } else {
                        List {
                            ForEach(filteredProducts) { product in
                                ProductRowItem(
                                    product: product,
                                    onEdit: { selectedProductToEdit = product },
                                    onPrintBarcode: { productToPrintBarcode = product },
                                    onStockAdjust: { delta in
                                        dataManager.adjustStock(productId: product.id, delta: delta)
                                    }
                                )
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) {
                                        productToDelete = product
                                        showDeleteConfirm = true
                                    } label: {
                                        Label("Hapus", systemImage: "trash")
                                    }

                                    Button {
                                        selectedProductToEdit = product
                                    } label: {
                                        Label("Edit", systemImage: "pencil")
                                    }
                                    .tint(.orange)

                                    Button {
                                        productToPrintBarcode = product
                                    } label: {
                                        Label("Cetak Barcode", systemImage: "printer.fill")
                                    }
                                    .tint(.green)
                                }
                            }
                        }
                        .listStyle(PlainListStyle())
                    }
                }
            }
            .navigationTitle("Kelola Produk & Stok")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showAddProductSheet = true }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(.cyan)
                    }
                }
            }
            .sheet(isPresented: $showAddProductSheet) {
                ProductFormView()
            }
            .sheet(item: $selectedProductToEdit) { product in
                ProductFormView(existingProduct: product)
            }
            .sheet(item: $productToPrintBarcode) { product in
                PrintBarcodeTagSheet(product: product)
            }
            .alert("Hapus Produk", isPresented: $showDeleteConfirm) {
                Button("Batal", role: .cancel) { }
                Button("Hapus", role: .destructive) {
                    if let p = productToDelete {
                        dataManager.deleteProduct(p)
                    }
                }
            } message: {
                Text("Apakah Anda yakin ingin menghapus produk '\(productToDelete?.name ?? "")'?")
            }
        }
    }
}

// Subview: Product Row Item
struct ProductRowItem: View {
    let product: Product
    var onEdit: () -> Void
    var onPrintBarcode: () -> Void
    var onStockAdjust: (Int) -> Void

    var body: some View {
        HStack(spacing: 12) {
            // Gambar Produk
            if let b64 = product.imageBase64, let img = ImageHelper.fromBase64(b64) {
                Image(uiImage: img)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 54, height: 54)
                    .clipShape(RoundedRectangle(cornerRadius: 10))
            } else {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(UIColor.secondarySystemBackground))
                        .frame(width: 54, height: 54)
                    Image(systemName: "cube.box.fill")
                        .font(.system(size: 24))
                        .foregroundColor(.blue.opacity(0.7))
                }
            }

            // Info Detail
            VStack(alignment: .leading, spacing: 4) {
                Text(product.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                HStack(spacing: 6) {
                    Text(product.category)
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.blue.opacity(0.1))
                        .foregroundColor(.blue)
                        .cornerRadius(4)

                    Text("SKU: \(product.sku)")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }

                HStack(spacing: 8) {
                    Text(product.formattedPrice)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.primary)

                    if product.costPrice > 0 {
                        Text("(Laba \(CurrencyHelper.format(product.price - product.costPrice)))")
                            .font(.caption2)
                            .foregroundColor(.green)
                    }
                }
            }

            Spacer()

            // Tombol Cetak Barcode & Kontrol Stok
            VStack(alignment: .trailing, spacing: 6) {
                stockBadge

                HStack(spacing: 8) {
                    // Tombol Cetak Stiker Barcode Harga
                    Button(action: onPrintBarcode) {
                        Image(systemName: "printer.fill")
                            .font(.system(size: 13))
                            .foregroundColor(.green)
                            .padding(6)
                            .background(Color.green.opacity(0.15))
                            .clipShape(Circle())
                    }
                    .buttonStyle(BorderlessButtonStyle())

                    HStack(spacing: 4) {
                        Button(action: { onStockAdjust(-1) }) {
                            Image(systemName: "minus.circle")
                                .font(.system(size: 18))
                                .foregroundColor(.gray)
                        }
                        .buttonStyle(BorderlessButtonStyle())

                        Text("\(product.stock)")
                            .font(.system(size: 14, weight: .bold))
                            .frame(minWidth: 20)

                        Button(action: { onStockAdjust(1) }) {
                            Image(systemName: "plus.circle")
                                .font(.system(size: 18))
                                .foregroundColor(.blue)
                        }
                        .buttonStyle(BorderlessButtonStyle())
                    }
                }
            }
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture {
            onEdit()
        }
    }

    private var stockBadge: some View {
        Group {
            if product.isOutOfStock {
                Text("Habis")
                    .font(.system(size: 11, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.red.opacity(0.15))
                    .foregroundColor(.red)
                    .clipShape(Capsule())
            } else if product.isLowStock {
                Text("Sisa \(product.stock) \(product.unit)")
                    .font(.system(size: 11, weight: .bold))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.orange.opacity(0.15))
                    .foregroundColor(.orange)
                    .clipShape(Capsule())
            } else {
                Text("Stok: \(product.stock) \(product.unit)")
                    .font(.caption2)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.green.opacity(0.15))
                    .foregroundColor(.green)
                    .clipShape(Capsule())
            }
        }
    }
}

// MARK: - Sheet Dialog Cetak Barcode Harga (55x30 mm - Maks 10)
struct PrintBarcodeTagSheet: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject var dataManager = DataManager.shared
    @ObservedObject var btManager = BluetoothManager.shared

    let product: Product
    @State private var quantity: Int = 1
    @State private var statusMessage: String = ""
    @State private var showAlert: Bool = false

    var barcodeText: String {
        product.barcode.isEmpty ? product.sku : product.barcode
    }

    var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                VStack(spacing: 20) {
                    // Preview Card Stiker 55x30
                    VStack(spacing: 6) {
                        Text(dataManager.storeConfig.storeName.isEmpty ? "OMNIPOS STORE" : dataManager.storeConfig.storeName.uppercased())
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.black)
                        
                        Divider().background(Color.black)

                        Text(String(product.name.prefix(24)))
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.black)

                        // Mock barcode
                        HStack(spacing: 2) {
                            ForEach(0..<20) { i in
                                Rectangle()
                                    .fill(Color.black)
                                    .frame(width: (i % 3 == 0) ? 3 : 1.5, height: 28)
                            }
                        }

                        Text(barcodeText)
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(.black)

                        Divider().background(Color.black)

                        Text(product.formattedPrice)
                            .font(.system(size: 18, weight: .heavy))
                            .foregroundColor(.black)
                            .padding(.vertical, 4)
                            .frame(maxWidth: .infinity)
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.black, lineWidth: 1.5))
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(10)
                    .shadow(color: .cyan.opacity(0.3), radius: 8, x: 0, y: 4)
                    .padding(.horizontal, 24)

                    // Quantity Stepper (1 to 10)
                    VStack(spacing: 8) {
                        Text("Jumlah Stiker (Maksimal 10):")
                            .font(.subheadline)
                            .foregroundColor(.white.opacity(0.8))

                        HStack(spacing: 20) {
                            Button(action: { if quantity > 1 { quantity -= 1 } }) {
                                Image(systemName: "minus.circle.fill")
                                    .font(.system(size: 32))
                                    .foregroundColor(quantity > 1 ? .cyan : .gray)
                            }

                            Text("\(quantity)")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)
                                .frame(minWidth: 40)

                            Button(action: { if quantity < 10 { quantity += 1 } }) {
                                Image(systemName: "plus.circle.fill")
                                    .font(.system(size: 32))
                                    .foregroundColor(quantity < 10 ? .cyan : .gray)
                            }
                        }

                        Text("• Otomatis memajukan 1 stiker penutup agar siap disobek.")
                            .font(.caption)
                            .foregroundColor(.green)
                    }
                    .padding()
                    .background(Color.white.opacity(0.08))
                    .cornerRadius(12)
                    .padding(.horizontal, 24)

                    Spacer()

                    // Tombol Cetak
                    Button(action: printStickers) {
                        HStack {
                            Image(systemName: "printer.fill")
                            Text("Cetak \(quantity) Stiker ke Kassen MT200A")
                                .fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.green)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 20)
                }
            }
            .navigationTitle("Cetak Barcode Harga (55x30)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Tutup") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(.white)
                }
            }
            .alert(isPresented: $showAlert) {
                Alert(title: Text("Status Cetak"), message: Text(statusMessage), dismissButton: .default(Text("OK")) {
                    if statusMessage.contains("Berhasil") {
                        presentationMode.wrappedValue.dismiss()
                    }
                })
            }
        }
    }

    private func printStickers() {
        guard btManager.isConnected else {
            statusMessage = "Printer belum terhubung! Silakan hubungkan Kassen MT200A di tab Pengaturan terlebih dahulu."
            showAlert = true
            return
        }

        let storeName = dataManager.storeConfig.storeName
        let payload = PrinterEngine.buildProductBarcodeTag55x30Raster(product: product, storeName: storeName, quantity: quantity)
        btManager.sendData(payload)
        statusMessage = "Berhasil mengirim \(quantity) stiker barcode ke printer!"
        showAlert = true
    }
}

