import SwiftUI

public struct AddressBookView: View {
    @ObservedObject var dataManager = DataManager.shared
    @Environment(\.presentationMode) var presentationMode
    
    // Callback when an address is chosen
    public var onSelect: ((SavedRecipient) -> Void)? = nil

    @State private var searchText: String = ""
    @State private var showAddSheet: Bool = false
    @State private var editingRecipient: SavedRecipient? = nil
    @State private var recipientToDelete: SavedRecipient? = nil
    @State private var showDeleteConfirm: Bool = false

    var filteredRecipients: [SavedRecipient] {
        if searchText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return dataManager.savedRecipients.sorted { $0.lastUsed > $1.lastUsed }
        }
        return dataManager.savedRecipients.filter {
            $0.name.localizedCaseInsensitiveContains(searchText) ||
            $0.phone.localizedCaseInsensitiveContains(searchText) ||
            $0.address.localizedCaseInsensitiveContains(searchText) ||
            $0.postalCode.localizedCaseInsensitiveContains(searchText) ||
            $0.defaultCourier.localizedCaseInsensitiveContains(searchText)
        }.sorted { $0.lastUsed > $1.lastUsed }
    }

    public var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                VStack(spacing: 14) {
                    // Search Bar
                    searchBarSection

                    // List of Recipients
                    if filteredRecipients.isEmpty {
                        emptyStateSection
                    } else {
                        recipientsListSection
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
            }
            .navigationTitle("Buku Alamat Penerima")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Tutup") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(.white.opacity(0.8))
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showAddSheet = true }) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus.circle.fill")
                            Text("Tambah")
                        }
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.blue)
                    }
                }
            }
            .sheet(isPresented: $showAddSheet) {
                RecipientFormSheet(recipient: nil) { newRecipient in
                    dataManager.saveOrUpdateRecipient(newRecipient)
                }
            }
            .sheet(item: $editingRecipient) { item in
                RecipientFormSheet(recipient: item) { updatedRecipient in
                    dataManager.saveOrUpdateRecipient(updatedRecipient)
                }
            }
            .alert(isPresented: $showDeleteConfirm) {
                Alert(
                    title: Text("Hapus Alamat?"),
                    message: Text("Alamat penerima \"\(recipientToDelete?.name ?? "")\" akan dihapus dari daftar tersimpan."),
                    primaryButton: .destructive(Text("Hapus")) {
                        if let item = recipientToDelete {
                            dataManager.deleteRecipient(item)
                        }
                    },
                    secondaryButton: .cancel(Text("Batal"))
                )
            }
        }
    }

    // MARK: - Search Bar
    private var searchBarSection: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.white.opacity(0.5))

            TextField("Cari nama penerima, no. telp, atau alamat...", text: $searchText)
                .foregroundColor(.white)
                .font(.system(size: 14))

            if !searchText.isEmpty {
                Button(action: { searchText = "" }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.white.opacity(0.6))
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.white.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
        )
    }

    // MARK: - Recipients List
    private var recipientsListSection: some View {
        ScrollView(showsIndicators: false) {
            LazyVStack(spacing: 12) {
                ForEach(filteredRecipients) { item in
                    recipientCard(item)
                }
            }
            .padding(.bottom, 24)
        }
    }

    private func recipientCard(_ item: SavedRecipient) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Name & Courier Badge
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.name)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)

                    HStack(spacing: 6) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 11))
                            .foregroundColor(.green.opacity(0.9))
                        Text(item.phone)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.white.opacity(0.85))
                    }
                }

                Spacer()

                // Courier Pill
                Text(item.defaultCourier)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.orange)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.orange.opacity(0.18))
                    .cornerRadius(8)
            }

            // Address
            Text(item.address)
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.8))
                .lineLimit(3)

            if !item.postalCode.isEmpty {
                HStack(spacing: 4) {
                    Text("Kode Pos:")
                        .font(.system(size: 12))
                        .foregroundColor(.white.opacity(0.5))
                    Text(item.postalCode)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(.white.opacity(0.9))
                }
            }

            if !item.notes.isEmpty {
                HStack(spacing: 4) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 11))
                        .foregroundColor(.yellow.opacity(0.8))
                    Text(item.notes)
                        .font(.system(size: 12, weight: .regular))
                        .foregroundColor(.yellow.opacity(0.85))
                }
            }

            Divider()
                .background(Color.white.opacity(0.1))

            // Action Buttons: Edit, Hapus, & Gunakan Alamat
            HStack(spacing: 10) {
                Button(action: {
                    recipientToDelete = item
                    showDeleteConfirm = true
                }) {
                    Image(systemName: "trash")
                        .font(.system(size: 14))
                        .foregroundColor(.red.opacity(0.8))
                        .padding(8)
                        .background(Color.red.opacity(0.15))
                        .clipShape(Circle())
                }

                Button(action: {
                    editingRecipient = item
                }) {
                    Image(systemName: "pencil")
                        .font(.system(size: 14))
                        .foregroundColor(.blue.opacity(0.9))
                        .padding(8)
                        .background(Color.blue.opacity(0.15))
                        .clipShape(Circle())
                }

                Spacer()

                Button(action: {
                    var used = item
                    used.lastUsed = Date()
                    dataManager.saveOrUpdateRecipient(used)
                    onSelect?(used)
                    presentationMode.wrappedValue.dismiss()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "checkmark.circle.fill")
                        Text("Gunakan Alamat")
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        LinearGradient(
                            gradient: Gradient(colors: [Color.blue, Color.purple]),
                            startPoint: .leading,
                            endPoint: .trailing
                        )
                    )
                    .cornerRadius(10)
                }
            }
        }
        .padding(14)
        .liquidGlassCard(cornerRadius: 16)
    }

    // MARK: - Empty State
    private var emptyStateSection: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "person.crop.rectangle.stack")
                .font(.system(size: 48))
                .foregroundColor(.white.opacity(0.3))

            Text("Belum Ada Alamat Tersimpan")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.white)

            Text("Simpan alamat pelanggan langganan agar pengiriman selanjutnya tinggal pilih dalam 1 sentuhan.")
                .font(.system(size: 13))
                .foregroundColor(.white.opacity(0.6))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: { showAddSheet = true }) {
                HStack(spacing: 8) {
                    Image(systemName: "plus")
                    Text("Tambah Alamat Pertama")
                }
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(Color.blue)
                .cornerRadius(12)
            }
            .padding(.top, 8)
            Spacer()
        }
    }
}

// MARK: - Recipient Form Sheet (Add / Edit)
public struct RecipientFormSheet: View {
    @Environment(\.presentationMode) var presentationMode

    var initialRecipient: SavedRecipient?
    var onSave: (SavedRecipient) -> Void

    @State private var name: String = ""
    @State private var phone: String = ""
    @State private var address: String = ""
    @State private var postalCode: String = ""
    @State private var courier: String = "J&T Express"
    @State private var notes: String = ""

    let couriers = ["J&T Express", "JNE Express", "SiCepat", "Shopee Xpress", "AnterAja", "Ninja Xpress", "POS Indonesia", "GoSend"]

    public init(recipient: SavedRecipient? = nil, onSave: @escaping (SavedRecipient) -> Void) {
        self.initialRecipient = recipient
        self.onSave = onSave
        _name = State(initialValue: recipient?.name ?? "")
        _phone = State(initialValue: recipient?.phone ?? "")
        _address = State(initialValue: recipient?.address ?? "")
        _postalCode = State(initialValue: recipient?.postalCode ?? "")
        _courier = State(initialValue: recipient?.defaultCourier ?? "J&T Express")
        _notes = State(initialValue: recipient?.notes ?? "")
    }

    public var body: some View {
        NavigationView {
            ZStack {
                LiquidGlassBackground()

                ScrollView {
                    VStack(spacing: 16) {
                        formField(title: "Nama Penerima*", text: $name, placeholder: "Contoh: Budi Pratama")
                        formField(title: "Nomor WhatsApp / HP*", text: $phone, placeholder: "0812-xxxx-xxxx", keyboard: .phonePad)
                        
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Alamat Lengkap*")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white.opacity(0.8))
                            
                            TextEditor(text: $address)
                                .frame(height: 80)
                                .padding(8)
                                .background(Color.white.opacity(0.08))
                                .cornerRadius(12)
                                .foregroundColor(.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                                )
                        }

                        HStack(spacing: 12) {
                            formField(title: "Kode Pos", text: $postalCode, placeholder: "10110", keyboard: .numberPad)
                            
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Ekspedisi Pilihan")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white.opacity(0.8))

                                Picker("Kurir", selection: $courier) {
                                    ForEach(couriers, id: \.self) { c in
                                        Text(c).tag(c)
                                    }
                                }
                                .pickerStyle(MenuPickerStyle())
                                .padding(.horizontal, 12)
                                .padding(.vertical, 10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.08))
                                .cornerRadius(12)
                                .foregroundColor(.white)
                            }
                        }

                        formField(title: "Catatan Pengiriman", text: $notes, placeholder: "Patokan rumah / instruksi kurir")

                        Spacer(minLength: 20)

                        // Save Button
                        Button(action: saveRecipient) {
                            HStack {
                                Image(systemName: "checkmark.circle.fill")
                                Text("Simpan ke Buku Alamat")
                            }
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(
                                LinearGradient(
                                    gradient: Gradient(colors: [Color.blue, Color.purple]),
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .cornerRadius(14)
                        }
                        .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || address.trimmingCharacters(in: .whitespaces).isEmpty)
                        .opacity((name.trimmingCharacters(in: .whitespaces).isEmpty || address.trimmingCharacters(in: .whitespaces).isEmpty) ? 0.5 : 1.0)
                    }
                    .padding(16)
                }
            }
            .navigationTitle(initialRecipient == nil ? "Tambah Penerima" : "Edit Penerima")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Batal") {
                        presentationMode.wrappedValue.dismiss()
                    }
                    .foregroundColor(.white.opacity(0.8))
                }
            }
        }
    }

    private func formField(title: String, text: Binding<String>, placeholder: String, keyboard: UIKeyboardType = .default) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white.opacity(0.8))

            TextField(placeholder, text: text)
                .keyboardType(keyboard)
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
                .foregroundColor(.white)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
        }
    }

    private func saveRecipient() {
        let recipient = SavedRecipient(
            id: initialRecipient?.id ?? UUID(),
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            phone: phone.trimmingCharacters(in: .whitespacesAndNewlines),
            address: address.trimmingCharacters(in: .whitespacesAndNewlines),
            postalCode: postalCode.trimmingCharacters(in: .whitespacesAndNewlines),
            defaultCourier: courier,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines),
            lastUsed: Date()
        )
        onSave(recipient)
        presentationMode.wrappedValue.dismiss()
    }
}
