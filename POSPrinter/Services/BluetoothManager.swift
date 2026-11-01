import Foundation
import CoreBluetooth

struct DiscoveredPrinter: Identifiable, Equatable {
    let id: UUID
    let name: String
    let peripheral: CBPeripheral
    let rssi: Int
}

class BluetoothManager: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    static let shared = BluetoothManager()

    @Published var isBluetoothPoweredOn = false
    @Published var isScanning = false
    @Published var isConnected = false
    @Published var connectedPrinterName: String = "Belum Terhubung"
    @Published var discoveredPrinters: [DiscoveredPrinter] = []
    @Published var statusMessage: String = "Bluetooth siap."

    private var centralManager: CBCentralManager!
    private var targetPeripheral: CBPeripheral?
    private var writeCharacteristic: CBCharacteristic?

    // Keep-Alive & Auto-Reconnect Settings
    private let lastConnectedUUIDKey = "omnipos_last_connected_printer_uuid"
    private var keepAliveTimer: Timer?
    private var autoReconnectTimer: Timer?
    private var isManualDisconnect = false

    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }

    func startScan() {
        guard centralManager.state == .poweredOn else {
            statusMessage = "Nyalakan Bluetooth iPhone terlebih dahulu."
            return
        }
        discoveredPrinters.removeAll()
        isScanning = true
        statusMessage = "Mencari printer Bluetooth di sekitar..."
        centralManager.scanForPeripherals(withServices: nil, options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func stopScan() {
        centralManager.stopScan()
        isScanning = false
        statusMessage = "Pencarian dihentikan."
    }

    func connect(to printer: DiscoveredPrinter) {
        isManualDisconnect = false
        stopScan()
        targetPeripheral = printer.peripheral
        targetPeripheral?.delegate = self
        UserDefaults.standard.set(printer.id.uuidString, forKey: lastConnectedUUIDKey)
        statusMessage = "Menghubungkan ke \(printer.name)..."
        centralManager.connect(printer.peripheral, options: [
            CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
        ])
    }

    func disconnect() {
        isManualDisconnect = true
        stopAutoReconnect()
        stopKeepAlive()
        UserDefaults.standard.removeObject(forKey: lastConnectedUUIDKey)
        if let peripheral = targetPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }

    // MARK: - Keep-Alive Heartbeat (Mencegah Firmware Printer Auto-Sleep / Auto-Disconnect)
    private func startKeepAlive() {
        stopKeepAlive()
        DispatchQueue.main.async {
            // Kirim status query setiap 15 detik agar koneksi Bluetooth printer tetap ON terus
            self.keepAliveTimer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: true) { [weak self] _ in
                guard let self = self, self.isConnected, let char = self.writeCharacteristic, let peripheral = self.targetPeripheral else { return }
                // ESC/POS DLE EOT 1 status query (tidak menghasilkan cetakan kertas, menjaga link aktif)
                let ping = Data([0x10, 0x04, 0x01])
                let writeType: CBCharacteristicWriteType = char.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
                peripheral.writeValue(ping, for: char, type: writeType)
            }
        }
    }

    private func stopKeepAlive() {
        keepAliveTimer?.invalidate()
        keepAliveTimer = nil
    }

    // MARK: - Auto-Reconnect (Sambung Ulang Otomatis Jika Terputus)
    private func startAutoReconnect() {
        guard !isManualDisconnect else { return }
        stopAutoReconnect()

        DispatchQueue.main.async {
            self.autoReconnectTimer = Timer.scheduledTimer(withTimeInterval: 3.5, repeats: true) { [weak self] _ in
                guard let self = self else { return }
                if self.isConnected {
                    self.stopAutoReconnect()
                    return
                }

                // 1. Coba sambung ulang ke peripheral yang tersimpan
                if let target = self.targetPeripheral {
                    self.statusMessage = "Menyambung ulang otomatis ke \(target.name ?? "Printer")..."
                    self.centralManager.connect(target, options: [
                        CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
                    ])
                    return
                }

                // 2. Cari dari UUID yang pernah terhubung
                if let savedIdString = UserDefaults.standard.string(forKey: self.lastConnectedUUIDKey),
                   let savedUUID = UUID(uuidString: savedIdString) {
                    let known = self.centralManager.retrievePeripherals(withIdentifiers: [savedUUID])
                    if let first = known.first {
                        self.targetPeripheral = first
                        self.targetPeripheral?.delegate = self
                        self.statusMessage = "Menyambung ulang otomatis..."
                        self.centralManager.connect(first, options: [
                            CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
                        ])
                        return
                    }
                }

                // 3. Scan ulang jika peripheral belum ditemukan
                if !self.isScanning {
                    self.startScan()
                }
            }
        }
    }

    private func stopAutoReconnect() {
        autoReconnectTimer?.invalidate()
        autoReconnectTimer = nil
    }

    // MARK: - CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isBluetoothPoweredOn = (central.state == .poweredOn)
        if central.state == .poweredOn {
            statusMessage = "Bluetooth Aktif."

            // Auto-Connect ke printer terakhir yang pernah terhubung
            if let savedIdString = UserDefaults.standard.string(forKey: lastConnectedUUIDKey),
               let savedUUID = UUID(uuidString: savedIdString) {
                let known = centralManager.retrievePeripherals(withIdentifiers: [savedUUID])
                if let lastPrinter = known.first {
                    targetPeripheral = lastPrinter
                    targetPeripheral?.delegate = self
                    statusMessage = "Menyambung otomatis ke \(lastPrinter.name ?? "Printer")..."
                    centralManager.connect(lastPrinter, options: [
                        CBConnectPeripheralOptionNotifyOnDisconnectionKey: true
                    ])
                    return
                }
            }

            startScan()
        } else {
            stopKeepAlive()
            stopAutoReconnect()
            statusMessage = "Bluetooth tidak aktif / tidak diizinkan."
            isConnected = false
            connectedPrinterName = "Belum Terhubung"
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        let name = peripheral.name ?? advertisementData[CBAdvertisementDataLocalNameKey] as? String ?? "Printer Bluetooth"

        let newPrinter = DiscoveredPrinter(id: peripheral.identifier, name: name, peripheral: peripheral, rssi: RSSI.intValue)
        if !discoveredPrinters.contains(where: { $0.id == peripheral.identifier }) {
            DispatchQueue.main.async {
                self.discoveredPrinters.append(newPrinter)
            }
        }

        // Auto-connect jika menemukan device yang cocok dengan ID tersimpan
        if let savedId = UserDefaults.standard.string(forKey: lastConnectedUUIDKey),
           savedId == peripheral.identifier.uuidString,
           !isConnected {
            connect(to: newPrinter)
        }
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        stopAutoReconnect()
        DispatchQueue.main.async {
            self.isConnected = true
            self.connectedPrinterName = peripheral.name ?? "Printer Thermal"
            self.statusMessage = "Terhubung ke \(self.connectedPrinterName) (Selalu Aktif)"
        }
        UserDefaults.standard.set(peripheral.identifier.uuidString, forKey: lastConnectedUUIDKey)
        peripheral.discoverServices(nil)
        startKeepAlive()
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.isConnected = false
            self.statusMessage = "Gagal terhubung: \(error?.localizedDescription ?? "Error")"
        }
        if !isManualDisconnect {
            startAutoReconnect()
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        stopKeepAlive()
        DispatchQueue.main.async {
            self.isConnected = false
            self.connectedPrinterName = "Belum Terhubung"
            self.writeCharacteristic = nil
            if self.isManualDisconnect {
                self.statusMessage = "Koneksi printer diputuskan."
            } else {
                self.statusMessage = "Koneksi printer terputus. Mencoba menyambung kembali otomatis..."
                self.startAutoReconnect()
            }
        }
    }

    // MARK: - CBPeripheralDelegate
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard let services = peripheral.services else { return }
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard let characteristics = service.characteristics else { return }
        for char in characteristics {
            // Karakteristik untuk kirim data: write / writeWithoutResponse
            if char.properties.contains(.write) || char.properties.contains(.writeWithoutResponse) {
                self.writeCharacteristic = char
                DispatchQueue.main.async {
                    self.statusMessage = "Printer siap menerima cetakan (Selalu Aktif)!"
                }
                break
            }
        }
    }

    // MARK: - Kirim Data ESC/POS & TSPL
    func sendData(_ data: Data, completion: ((Bool) -> Void)? = nil) {
        guard let peripheral = targetPeripheral, let char = writeCharacteristic else {
            statusMessage = "Printer belum terhubung atau karakteristik tidak ditemukan."
            completion?(false)
            return
        }

        let writeType: CBCharacteristicWriteType = char.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
        let chunkSize = 120 // Ukuran aman untuk MTU BLE di iOS
        var offset = 0

        DispatchQueue.global(qos: .userInitiated).async {
            while offset < data.count {
                let thisChunkSize = min(chunkSize, data.count - offset)
                let chunk = data.subdata(in: offset..<(offset + thisChunkSize))
                peripheral.writeValue(chunk, for: char, type: writeType)
                offset += thisChunkSize
                Thread.sleep(forTimeInterval: 0.04) // Delay 40ms antar paket agar buffer printer stabil
            }

            DispatchQueue.main.async {
                self.statusMessage = "Data cetak berhasil dikirim! (Koneksi Tetap ON)"
                completion?(true)
                // KONEKSI TETAP AKTIF TERUS (TIDAK ADA AUTO-DISCONNECT)
            }
        }
    }
}
