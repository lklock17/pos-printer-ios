import SwiftUI

struct ProductFormView: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject var dataManager = DataManager.shared

    var existingProduct: Product?

    @State private var name: String = ""
    @State private var sku: String = ""
    @State private var barcode: String = ""
    @State private var priceText: String = ""
    @State private var costPriceText: String = ""
    @State private var stockText: String = ""
    @State private var selectedCategory: String = "Makanan"
    @State private var customCategory: String = ""
    @State private var showCustomCategoryField: Bool = false
    @State private var unit: String = "pcs"
    @State private var notes: String = ""
    
    // Photo State
    @State private var selectedImage: UIImage? = nil
    @State private var showImageActionSheet: Bool = false
    @State private var showImagePicker: Bool = false
    @State private var pickerSource: ImagePicker.SourceType = .photoLibrary
    
    @State private var showAlert: Bool = false
    @State private var alertMessage: String = ""

    let availableUnits = ["pcs", "cup", "porsi", "box", "botol", "kg", "pack", "lembar", "paket"]

    var isEditMode: Bool {
        existingProduct != nil
    }

    var calculatedPrice: Double {
        Double(priceText.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    var calculatedCost: Double {
        Double(costPriceText.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    var profitNominal: Double {
        max(0, calculatedPrice - calculatedCost)
    }

    var profitMarginPercentage: Double {
        guard calculatedPrice > 0 else { return 0 }
        return (profitNominal / calculatedPrice) * 100.0
    }

    var body: some View {
        NavigationView {
            Form {
                // Section 1: Foto Produk
                Section(header: Text("Foto Produk")) {
                    HStack(spacing: 16) {
                        if let img = selectedImage {
                            Image(uiImage: img)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 80, height: 80)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.blue, lineWidth: 1.5))
                        } else {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12)
                                    .fill(Color(UIColor.systemGray6))
                                    .frame(width: 80, height: 80)
                                Image(systemName: "photo.badge.plus")
                                    .font(.system(size: 28))
                                    .foregroundColor(.blue)
                            }
                        }

                        VStack(alignment: .leading, spacing: 8) {
                            Button(action: { showImageActionSheet = true }) {
                                Label(selectedImage == nil ? "Pilih / Ambil Foto" : "Ganti Foto",
                                      systemImage: "camera.fill")
                                    .font(.system(size: 14, weight: .medium))
                            }

                            if selectedImage != nil {
                                Button(role: .destructive, action: { selectedImage = nil }) {
                                    Label("Hapus Foto", systemImage: "trash")
                                        .font(.caption)
                                        .foregroundColor(.red)
                                }
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }

                // Section 2: Informasi Dasar
                Section(header: Text("Informasi Dasar Produk")) {
                    HStack {
                        Text("Nama Produk:")
                            .foregroundColor(.secondary)
                        TextField("Contoh: Kopi Latte Aren", text: $name)
                    }

                    HStack {
                        Text("Kategori:")
                            .foregroundColor(.secondary)
                        Picker("", selection: $selectedCategory) {
                            ForEach(dataManager.categories.filter { $0 != "Semua" }, id: \.self) { cat in
                                Text(cat).tag(cat)
                            }
                            Text("+ Tambah Kategori Baru...").tag("__NEW__")
                        }
                        .pickerStyle(MenuPickerStyle())
                    }

                    if selectedCategory == "__NEW__" {
                        HStack {
                            Text("Kategori Baru:")
                                .foregroundColor(.secondary)
                            TextField("Nama kategori baru", text: $customCategory)
                        }
                    }

                    HStack {
                        Text("Satuan:")
                            .foregroundColor(.secondary)
                        Picker("", selection: $unit) {
                            ForEach(availableUnits, id: \.self) { u in
                                Text(u).tag(u)
                            }
                        }
                        .pickerStyle(MenuPickerStyle())
                    }

                    HStack {
                        Text("SKU / Kode:")
                            .foregroundColor(.secondary)
                        TextField("Contoh: KOP-001", text: $sku)
                        Button(action: generateRandomSKU) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.footnote)
                                .foregroundColor(.blue)
                        }
                    }

                    HStack {
                        Text("Barcode:")
                            .foregroundColor(.secondary)
                        TextField("Scan atau ketik barcode", text: $barcode)
                            .keyboardType(.numberPad)
                    }
                }

                // Section 3: Harga & Keuntungan
                Section(header: Text("Harga & Margin Laba")) {
                    HStack {
                        Text("Harga Jual (Rp):")
                            .foregroundColor(.secondary)
                        TextField("0", text: $priceText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }

                    HStack {
                        Text("Harga Modal / HPP:")
                            .foregroundColor(.secondary)
                        TextField("0", text: $costPriceText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                    }

                    if calculatedPrice > 0 {
                        HStack {
                            Text("Estimasi Keuntungan:")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("\(CurrencyHelper.format(profitNominal)) / \(unit)")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.green)
                                Text(String(format: "Margin: %.1f%%", profitMarginPercentage))
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }

                // Section 4: Manajemen Stok
                Section(header: Text("Inventaris & Stok")) {
                    HStack {
                        Text("Jumlah Stok:")
                            .foregroundColor(.secondary)
                        TextField("0", text: $stockText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                        Text(unit)
                            .foregroundColor(.secondary)
                    }
                }

                // Section 5: Catatan Tambahan
                Section(header: Text("Catatan / Keterangan")) {
                    TextField("Catatan produk opsional (misal: rasa, ukuran)", text: $notes)
                }
            }
            .navigationTitle(isEditMode ? "Edit Produk" : "Tambah Produk")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Batal") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: saveProduct) {
                        Text("Simpan")
                            .bold()
                    }
                }
            }
            .actionSheet(isPresented: $showImageActionSheet) {
                ActionSheet(title: Text("Pilih Foto Produk"), buttons: [
                    .default(Text("Ambil Foto dari Kamera")) {
                        pickerSource = .camera
                        showImagePicker = true
                    },
                    .default(Text("Pilih dari Galeri Foto")) {
                        pickerSource = .photoLibrary
                        showImagePicker = true
                    },
                    .cancel(Text("Batal"))
                ])
            }
            .sheet(isPresented: $showImagePicker) {
                ImagePicker(sourceType: pickerSource) { img in
                    self.selectedImage = img
                }
            }
            .alert(isPresented: $showAlert) {
                Alert(title: Text("Perhatian"), message: Text(alertMessage), dismissButton: .default(Text("OK")))
            }
            .onAppear {
                populateDataIfEditing()
            }
        }
    }

    private func populateDataIfEditing() {
        if let p = existingProduct {
            name = p.name
            sku = p.sku
            barcode = p.barcode
            priceText = String(Int(p.price))
            costPriceText = String(Int(p.costPrice))
            stockText = String(p.stock)
            selectedCategory = p.category
            unit = p.unit
            notes = p.notes ?? ""
            if let b64 = p.imageBase64, let img = ImageHelper.fromBase64(b64) {
                selectedImage = img
            }
        } else {
            generateRandomSKU()
        }
    }

    private func generateRandomSKU() {
        let prefix = selectedCategory.prefix(3).uppercased()
        let num = Int.random(in: 100...999)
        sku = "\(prefix.isEmpty ? "PRD" : prefix)-\(num)"
    }

    private func saveProduct() {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            alertMessage = "Silakan masukkan nama produk terlebih dahulu."
            showAlert = true
            return
        }

        guard calculatedPrice > 0 else {
            alertMessage = "Harga jual produk harus lebih besar dari Rp 0."
            showAlert = true
            return
        }

        let finalCategory: String
        if selectedCategory == "__NEW__" {
            let catTrimmed = customCategory.trimmingCharacters(in: .whitespacesAndNewlines)
            finalCategory = catTrimmed.isEmpty ? "Umum" : catTrimmed
        } else {
            finalCategory = selectedCategory
        }

        let stockVal = Int(stockText) ?? 0
        let base64Img = selectedImage != nil ? ImageHelper.toBase64(image: selectedImage!) : nil

        if var prod = existingProduct {
            prod.name = trimmedName
            prod.category = finalCategory
            prod.sku = sku
            prod.barcode = barcode
            prod.price = calculatedPrice
            prod.costPrice = calculatedCost
            prod.stock = stockVal
            prod.unit = unit
            prod.imageBase64 = base64Img
            prod.notes = notes.isEmpty ? nil : notes
            dataManager.updateProduct(prod)
        } else {
            let newProd = Product(
                name: trimmedName,
                sku: sku,
                barcode: barcode,
                price: calculatedPrice,
                costPrice: calculatedCost,
                stock: stockVal,
                category: finalCategory,
                unit: unit,
                imageBase64: base64Img,
                notes: notes.isEmpty ? nil : notes
            )
            dataManager.addProduct(newProd)
        }

        presentationMode.wrappedValue.dismiss()
    }
}

