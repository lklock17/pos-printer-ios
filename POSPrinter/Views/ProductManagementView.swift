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
                                ProductRowItem(product: product, onEdit: {
                                    selectedProductToEdit = product
                                }, onStockAdjust: { delta in
                                    dataManager.adjustStock(productId: product.id, delta: delta)
                                })
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

            // Kontrol Stok Cepat & Status
            VStack(alignment: .trailing, spacing: 6) {
                stockBadge

                HStack(spacing: 6) {
                    Button(action: { onStockAdjust(-1) }) {
                        Image(systemName: "minus.circle")
                            .font(.system(size: 18))
                            .foregroundColor(.gray)
                    }
                    .buttonStyle(BorderlessButtonStyle())

                    Text("\(product.stock)")
                        .font(.system(size: 14, weight: .bold))
                        .frame(minWidth: 24)

                    Button(action: { onStockAdjust(1) }) {
                        Image(systemName: "plus.circle")
                            .font(.system(size: 18))
                            .foregroundColor(.blue)
                    }
                    .buttonStyle(BorderlessButtonStyle())
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

