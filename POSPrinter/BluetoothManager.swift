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
        stopScan()
        targetPeripheral = printer.peripheral
        targetPeripheral?.delegate = self
        statusMessage = "Menghubungkan ke \(printer.name)..."
        centralManager.connect(printer.peripheral, options: nil)
    }

    func disconnect() {
        if let peripheral = targetPeripheral {
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }

    // MARK: - CBCentralManagerDelegate
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isBluetoothPoweredOn = (central.state == .poweredOn)
        if central.state == .poweredOn {
            statusMessage = "Bluetooth Aktif. Siap scan printer."
            startScan()
        } else {
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
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        DispatchQueue.main.async {
            self.isConnected = true
            self.connectedPrinterName = peripheral.name ?? "Kassen Printer"
            self.statusMessage = "Terhubung ke \(self.connectedPrinterName)"
        }
        peripheral.discoverServices(nil)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.isConnected = false
            self.statusMessage = "Gagal terhubung: \(error?.localizedDescription ?? "Error")"
        }
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        DispatchQueue.main.async {
            self.isConnected = false
            self.connectedPrinterName = "Belum Terhubung"
            self.writeCharacteristic = nil
            self.statusMessage = "Koneksi printer terputus."
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
                    self.statusMessage = "Printer siap menerima cetakan!"
                }
                break
            }
        }
    }

    // MARK: - Kirim Data ESC/POS dengan Chunking (Cegah Paket Dropped di BLE)
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
                self.statusMessage = "Data cetak dan auto-cut terkirim!"
                completion?(true)

                // Jeda 2.5 detik lalu lepas koneksi agar komputer Windows bisa langsung mencetak
                let autoDisconnect = UserDefaults.standard.object(forKey: "AutoDisconnect_Key") == nil ? true : UserDefaults.standard.bool(forKey: "AutoDisconnect_Key")
                if autoDisconnect {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                        self.disconnect()
                        self.statusMessage = "Bluetooth dilepas otomatis (Komputer & iOS siap cetak)"
                    }
                }
            }
        }
    }
}
