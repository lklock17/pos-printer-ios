package com.lklock17.omnipos.ui.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.OrderItem
import com.lklock17.omnipos.models.Product
import com.lklock17.omnipos.models.ShippingInfo
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val config = DataManager.storeConfig.value

    var storeName by remember { mutableStateOf(config.storeName) }
    var storeAddress by remember { mutableStateOf(config.storeAddress) }
    var storePhone by remember { mutableStateOf(config.storePhone) }
    var footerMessage by remember { mutableStateOf(config.footerMessage) }
    var isAutoCutEnabled by remember { mutableStateOf(config.isAutoCutEnabled) }

    LaunchedEffect(Unit) {
        BluetoothPrinterManager.refreshPairedDevices(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan & Printer", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Bluetooth Printer Status Card
            LiquidGlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("STATUS PRINTER BLUETOOTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (BluetoothPrinterManager.isConnected.value) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (BluetoothPrinterManager.isConnected.value)
                                    "Terhubung: ${BluetoothPrinterManager.connectedDeviceName.value}"
                                else
                                    "Belum Terhubung",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (BluetoothPrinterManager.isConnected.value) {
                        Button(
                            onClick = { BluetoothPrinterManager.disconnect() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Putuskan", fontSize = 11.sp)
                        }
                    } else {
                        Button(
                            onClick = { BluetoothPrinterManager.refreshPairedDevices(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("Scan Ulang", fontSize = 11.sp)
                        }
                    }
                }

                // List of Paired Devices
                if (!BluetoothPrinterManager.isConnected.value) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Perangkat Bluetooth Terpasang (Paired):", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (BluetoothPrinterManager.pairedDevices.isEmpty()) {
                        Text(
                            "Tidak ada printer bluetooth terpasang di HP. Silakan pairing printer (e.g. Kassen MT200A / POS-58 / Zjiang) via menu Bluetooth HP Anda terlebih dahulu.",
                            fontSize = 12.sp,
                            color = Color(0xFFFDE68A)
                        )
                    } else {
                        BluetoothPrinterManager.pairedDevices.forEach { dev ->
                            DeviceRowItem(device = dev) {
                                BluetoothPrinterManager.connectToDevice(dev) { ok, err ->
                                    Toast.makeText(context, if (ok) "Terhubung ke ${dev.name}!" else err ?: "Gagal terhubung.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }

            // 2. Test Print Tools
            LiquidGlassCard {
                Text("TES CETAK PRINTER (TEST PRINT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val dummyOrder = createDummyOrder()
                            val data = PrinterEngine.buildReceiptData(dummyOrder, config)
                            BluetoothPrinterManager.sendData(data) { ok, err ->
                                Toast.makeText(context, if (ok) "Tes cetak struk terkirim!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Struk 58mm", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val dummyOrder = createDummyOrder()
                            val data = PrinterEngine.buildTSPLSticker58x40Data(dummyOrder, config)
                            BluetoothPrinterManager.sendData(data) { ok, err ->
                                Toast.makeText(context, if (ok) "Tes cetak stiker 58x40 terkirim!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Text("Stiker 58x40", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val dummyOrder = createDummyOrder()
                            val data = PrinterEngine.buildTSPLLabelData(dummyOrder, config)
                            BluetoothPrinterManager.sendData(data) { ok, err ->
                                Toast.makeText(context, if (ok) "Tes cetak label 4x6 terkirim!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                    ) {
                        Text("Label 4x6", fontSize = 11.sp)
                    }
                }
            }

            // 3. Store Profile & Hardware Settings
            LiquidGlassCard {
                Text("PROFIL TOKO & PENGIRIM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(value = storeName, onValueChange = { storeName = it }, label = { Text("Nama Toko") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = storeAddress, onValueChange = { storeAddress = it }, label = { Text("Alamat Toko") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = storePhone, onValueChange = { storePhone = it }, label = { Text("Nomor HP / WA") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(value = footerMessage, onValueChange = { footerMessage = it }, label = { Text("Pesan Kaki Struk") }, modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Cut Otomatis", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Kirim kode potong otomatis setelah cetak", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = isAutoCutEnabled, onCheckedChange = { isAutoCutEnabled = it })
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val updated = config.copy(
                            storeName = storeName,
                            storeAddress = storeAddress,
                            storePhone = storePhone,
                            footerMessage = footerMessage,
                            defaultSenderName = storeName,
                            defaultSenderPhone = storePhone,
                            defaultSenderAddress = storeAddress,
                            isAutoCutEnabled = isAutoCutEnabled
                        )
                        DataManager.storeConfig.value = updated
                        DataManager.saveAll(context)
                        Toast.makeText(context, "Pengaturan toko disimpan!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Simpan Pengaturan Toko")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceRowItem(device: BluetoothDevice, onConnect: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onConnect),
        color = Color(0x1AFFFFFF),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(device.name ?: "Unknown Device", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                Text(device.address, fontSize = 11.sp, color = Color.Gray)
            }
            Text("Hubungkan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
        }
    }
}

private fun createDummyOrder(): Order {
    return Order(
        items = listOf(
            OrderItem(product = Product(name = "Kopi Latte Aren", price = 24000.0), quantity = 2),
            OrderItem(product = Product(name = "Croissant Cokelat", price = 22000.0), quantity = 1)
        ),
        total = 70000.0,
        shippingInfo = ShippingInfo(
            courier = "J&T Express",
            service = "REGULER",
            trackingNumber = "JT1234567890",
            recipientName = "Budi Pratama",
            recipientPhone = "0812-9876-5432",
            recipientAddress = "Jl. Merdeka No. 45, Gambir, Jakarta Pusat",
            postalCode = "10110",
            isCOD = true,
            codAmount = 70000.0
        )
    )
}
