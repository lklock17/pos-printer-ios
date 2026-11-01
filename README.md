# POS Printer iOS (Thermal Bluetooth ESC/POS & Auto-Cut)

Aplikasi iOS Native (SwiftUI + CoreBluetooth) untuk mencetak struk kasir dan perintah **Auto-Cut** langsung ke printer thermal Bluetooth (Kassen MT200A / 58mm / 80mm ESC/POS).

---

## Fitur Utama

1. **Bluetooth Thermal Printer (CoreBluetooth):**
   - Otomatis memindai dan menghubungkan ke printer Bluetooth di sekitar.
   - Mengirim perintah ESC/POS standar dengan sistem *BLE chunking* (anti paket terputus).
   - Mendukung perintah **Auto-Cut** otomatis (`GS V 66 0`).

2. **Pengaturan Struk Kustom (Custom Receipt):**
   - Nama Toko, Alamat, dan Nomor Telepon.
   - Pesan Catatan Kaki / Footer Struk.
   - Switch On/Off Auto-Cut.
   - Pilihan lebar kertas (58mm 32 kolom / 80mm 48 kolom).

3. **Menu Kasir & Manajemen Produk:**
   - Tambah dan sesuaikan produk (nama barang, harga, jumlah).
   - Hitung total, uang bayar, dan kembalian secara otomatis.
   - Tombol cetak langsung ke printer Bluetooth.

4. **Auto-Print dari Web Browser (URL Scheme):**
   - Aplikasi ini mendaftarkan protokol URL khusus: `posprint://`
   - Dari Safari atau aplikasi web kasir Anda di iPhone, cukup panggil tautan:
     ```html
     <a href="posprint://print?store=Toko+Saya&item=Kopi+Latte:25000:2">Cetak Struk</a>
     ```
     iPhone akan otomatis membuka aplikasi ini, memproses struk, dan langsung mencetak serta memotong kertas!

---

## Cara Build Menjadi File IPA di GitHub (Gratis & Otomatis)

Anda tidak memerlukan komputer Mac untuk mengkompilasi file IPA. Repositori ini sudah dilengkapi **GitHub Actions** yang menggunakan komputer macOS di server GitHub untuk mengkompilasinya secara otomatis:

1. **Push folder ini ke repositori GitHub Anda:**
   ```bash
   git init
   git add .
   git commit -m "Initial commit POS Printer iOS"
   gh repo create pos-printer-ios --public --source=. --push
   ```
2. **GitHub Actions Berjalan Otomatis:**
   - Buka tab **Actions** di repositori GitHub Anda.
   - Workflow `Build iOS IPA` akan otomatis berjalan di macOS runner.
3. **Download File IPA:**
   - Setelah selesai (sekitar 2–3 menit), klik workflow run tersebut.
   - Di bagian bawah (**Artifacts**), unduh file **`POSPrinter-iOS-IPA`**.
   - Di dalamnya terdapat file **`POSPrinter.ipa`** yang siap dipasang.

---

## Cara Install File IPA ke iPhone / iPad

Gunakan salah satu alat sideloading gratis berikut:
1. **Sideloadly (Rekomendasi untuk Windows/Mac):**
   - Download Sideloadly di komputer (PC).
   - Hubungkan iPhone via kabel USB.
   - Drag and drop file `POSPrinter.ipa` ke Sideloadly, masukkan Apple ID Anda, klik **Start**.
2. **AltStore / TrollStore / Scarlet:**
   - Buka file `POSPrinter.ipa` langsung di iPhone untuk menginstalnya.

