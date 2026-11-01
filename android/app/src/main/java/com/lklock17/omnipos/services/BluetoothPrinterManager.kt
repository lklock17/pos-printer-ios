package com.lklock17.omnipos.services

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

object BluetoothPrinterManager {
    // Standard SPP UUID for thermal printers (ESC/POS & TSPL)
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    val isConnected = mutableStateOf(false)
    val isConnecting = mutableStateOf(false)
    val connectedDeviceName = mutableStateOf<String?>(null)
    val pairedDevices = mutableStateListOf<BluetoothDevice>()

    private var currentSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(context: Context) {
        pairedDevices.clear()
        bluetoothAdapter?.bondedDevices?.let {
            pairedDevices.addAll(it)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice, onResult: (Boolean, String?) -> Unit) {
        if (isConnecting.value) return
        isConnecting.value = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                disconnect()

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
            } catch (e: Exception) {
                disconnect()
                withContext(Dispatchers.Main) {
                    isConnecting.value = false
                    isConnected.value = false
                    connectedDeviceName.value = null
                    onResult(false, e.localizedMessage ?: "Gagal terhubung ke printer.")
                }
            }
        }
    }

    fun disconnect() {
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
                disconnect()
                withContext(Dispatchers.Main) {
                    onResult(false, "Koneksi printer terputus: ${e.localizedMessage}")
                }
            }
        }
    }
}

