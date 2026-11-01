package com.lklock17.omnipos.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinterManager {
    // Standard SPP UUID for thermal printers (ESC/POS & TSPL)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val PREFS_NAME = "omnipos_bt_prefs"
    private const val KEY_LAST_DEVICE = "last_bt_device_address"

    val isConnected = mutableStateOf(false)
    val isConnecting = mutableStateOf(false)
    val connectedDeviceName = mutableStateOf<String?>(null)
    val pairedDevices = mutableStateListOf<BluetoothDevice>()

    private var currentSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var lastTargetDevice: BluetoothDevice? = null

    private var keepAliveJob: Job? = null
    private var autoReconnectJob: Job? = null
    private var isManualDisconnect = false

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(context: Context) {
        pairedDevices.clear()
        bluetoothAdapter?.bondedDevices?.let {
            pairedDevices.addAll(it)
        }

        // Cek jika belum terhubung, coba auto-connect ke printer terakhir
        if (!isConnected.value && !isManualDisconnect) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastAddress = prefs.getString(KEY_LAST_DEVICE, null)
            if (!lastAddress.isNullOrEmpty()) {
                val matched = pairedDevices.firstOrNull { it.address == lastAddress }
                if (matched != null) {
                    connectToDevice(matched, context) { _, _ -> }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, context: Context? = null, onResult: (Boolean, String?) -> Unit) {
        if (isConnecting.value) return
        isConnecting.value = true
        isManualDisconnect = false
        lastTargetDevice = device

        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_LAST_DEVICE, device.address).apply()
        }

        stopAutoReconnect()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                internalDisconnect()

                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()

                currentSocket = socket
                outputStream = socket.outputStream

                withContext(Dispatchers.Main) {
                    isConnected.value = true
                    isConnecting.value = false
                    connectedDeviceName.value = device.name ?: device.address
                    onResult(true, null)
                }

                // Jalankan Keep-Alive Heartbeat agar firmware printer TIDAK auto-sleep
                startKeepAlive()

            } catch (e: Exception) {
                internalDisconnect()
                withContext(Dispatchers.Main) {
                    isConnecting.value = false
                    isConnected.value = false
                    connectedDeviceName.value = null
                    onResult(false, e.localizedMessage ?: "Gagal terhubung ke printer.")
                }

                if (!isManualDisconnect) {
                    startAutoReconnect(context)
                }
            }
        }
    }

    // MARK: - Keep-Alive Heartbeat (ESC/POS Status Query Tiap 15 Detik)
    private fun startKeepAlive() {
        stopKeepAlive()
        keepAliveJob = CoroutineScope(Dispatchers.IO).launch {
            val ping = byteArrayOf(0x10, 0x04, 0x01) // DLE EOT 1
            while (isActive && isConnected.value) {
                delay(15000L) // 15 detik
                try {
                    outputStream?.write(ping)
                    outputStream?.flush()
                } catch (e: Exception) {
                    // Jika ping gagal, koneksi terputus -> picu auto-reconnect
                    withContext(Dispatchers.Main) {
                        isConnected.value = false
                        connectedDeviceName.value = null
                    }
                    if (!isManualDisconnect) {
                        startAutoReconnect(null)
                    }
                    break
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    // MARK: - Auto-Reconnect (Sambung Ulang Otomatis)
    private fun startAutoReconnect(context: Context?) {
        if (isManualDisconnect) return
        stopAutoReconnect()

        autoReconnectJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && !isConnected.value && !isManualDisconnect) {
                delay(4000L) // Coba sambung setiap 4 detik
                val device = lastTargetDevice
                if (device != null) {
                    try {
                        internalDisconnect()
                        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                        bluetoothAdapter?.cancelDiscovery()
                        socket.connect()

                        currentSocket = socket
                        outputStream = socket.outputStream

                        withContext(Dispatchers.Main) {
                            isConnected.value = true
                            isConnecting.value = false
                            connectedDeviceName.value = device.name ?: device.address
                        }
                        startKeepAlive()
                        break
                    } catch (_: Exception) {
                        // Lanjutkan perulangan jika printer masih mati / di luar jangkauan
                    }
                }
            }
        }
    }

    private fun stopAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
    }

    fun disconnect(context: Context? = null) {
        isManualDisconnect = true
        stopKeepAlive()
        stopAutoReconnect()
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_LAST_DEVICE).apply()
        }
        internalDisconnect()
    }

    private fun internalDisconnect() {
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            currentSocket?.close()
        } catch (_: Exception) {}
        outputStream = null
        currentSocket = null
        isConnected.value = false
        connectedDeviceName.value = null
    }

    fun sendData(data: ByteArray, onResult: (Boolean, String?) -> Unit) {
        if (!isConnected.value || outputStream == null) {
            onResult(false, "Printer belum terhubung. Silakan hubungkan printer Bluetooth terlebih dahulu.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                outputStream?.write(data)
                outputStream?.flush()
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                internalDisconnect()
                withContext(Dispatchers.Main) {
                    onResult(false, "Koneksi printer terputus: ${e.localizedMessage}")
                }
                if (!isManualDisconnect) {
                    startAutoReconnect(null)
                }
            }
        }
    }
}
