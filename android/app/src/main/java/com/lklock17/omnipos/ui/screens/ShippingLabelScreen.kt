package com.lklock17.omnipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.*
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassButton
import com.lklock17.omnipos.ui.components.LiquidGlassCard

enum class LabelPrintFormat(val title: String, val subtitle: String) {
    STICKER_58X40("Stiker 58x40 mm", "Stiker Tempel"),
    LABEL_4X6("Label 4x6 Inci", "Resi Ekspedisi"),
    SLIP_58("Struk 58mm", "Kertas Roll POS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShippingLabelScreen() {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(LabelPrintFormat.STICKER_58X40) }
    var showAddressBook by remember { mutableStateOf(false) }
    var showPrintConfirmDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Active Shipping State
    var courier by remember { mutableStateOf("J&T Express") }
    var service by remember { mutableStateOf("REGULER") }
    var trackingNumber by remember { mutableStateOf("JT" + (100000000..999999999).random()) }
    var recipientName by remember { mutableStateOf("Budi Pratama") }
    var recipientPhone by remember { mutableStateOf("0812-9876-5432") }
    var recipientAddress by remember { mutableStateOf("Jl. Merdeka No. 45, RT 02/05, Gambir, Jakarta Pusat") }
    var postalCode by remember { mutableStateOf("10110") }
    var weightKg by remember { mutableStateOf(1.0) }
    var isCOD by remember { mutableStateOf(false) }
    var codAmount by remember { mutableStateOf(150000.0) }
    var notes by remember { mutableStateOf("FRAGILE - JANGAN DIBANTING") }

    val activeShipping = remember(courier, service, trackingNumber, recipientName, recipientPhone, recipientAddress, postalCode, weightKg, isCOD, codAmount, notes) {
        ShippingInfo(
            courier = courier,
            service = service,
            trackingNumber = trackingNumber,
            senderName = DataManager.storeConfig.value.defaultSenderName,
            senderPhone = DataManager.storeConfig.value.defaultSenderPhone,
            senderAddress = DataManager.storeConfig.value.defaultSenderAddress,
            recipientName = recipientName,
            recipientPhone = recipientPhone,
            recipientAddress = recipientAddress,
            postalCode = postalCode,
            weightKg = weightKg,
            isCOD = isCOD,
            codAmount = codAmount,
            notes = notes
        )
    }

    val workingOrder = remember(activeShipping) {
        Order(
            items = listOf(
                OrderItem(
                    product = Product(name = "Paket Pengiriman Retail", price = 150000.0),
                    quantity = 1
                )
            ),
            orderType = OrderType.SHIPPING,
            shippingInfo = activeShipping
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Label Pengiriman", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    TextButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Resi", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                },
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
            // 1. Format Selector (58x40 vs 4x6 vs 58mm)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LabelPrintFormat.values().forEach { fmt ->
                    val isSelected = selectedFormat == fmt
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedFormat = fmt },
                        color = if (isSelected) Color(0xFF2563EB) else Color(0x1AFFFFFF),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0x22FFFFFF)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = fmt.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = fmt.subtitle,
                                fontSize = 9.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.9f) else Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 2. Quick Address Book Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showAddressBook = true },
                    color = Color(0x1AFFFFFF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4D38BDF8)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Buku Alamat (${DataManager.savedRecipients.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val saved = SavedRecipient(
                                name = recipientName,
                                phone = recipientPhone,
                                address = recipientAddress,
                                postalCode = postalCode,
                                defaultCourier = courier,
                                notes = notes,
                                lastUsed = System.currentTimeMillis()
                            )
                            DataManager.saveOrUpdateRecipient(saved, context)
                            Toast.makeText(context, "Alamat \"$recipientName\" disimpan ke Buku Alamat!", Toast.LENGTH_SHORT).show()
                        },
                    color = Color(0x3310B981),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x6610B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Simpan",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }

            // 3. Visual Preview (Stiker 58x40 / Label 4x6 / Struk 58mm)
            when (selectedFormat) {
                LabelPrintFormat.STICKER_58X40 -> {
                    Sticker58x40Preview(activeShipping, DataManager.storeConfig.value)
                }
                LabelPrintFormat.LABEL_4X6 -> {
                    Label4x6Preview(activeShipping, DataManager.storeConfig.value)
                }
                LabelPrintFormat.SLIP_58 -> {
                    Slip58Preview(activeShipping)
                }
            }

            // 4. Action Buttons (Print with Confirmation)
            val printTitle = when (selectedFormat) {
                LabelPrintFormat.STICKER_58X40 -> "Cetak Stiker 58x40 mm (TSPL)"
                LabelPrintFormat.LABEL_4X6 -> "Cetak Label 4x6 Thermal (TSPL)"
                LabelPrintFormat.SLIP_58 -> "Cetak Resi ke Struk POS (Auto-Cut)"
            }

            LiquidGlassButton(
                text = printTitle,
                icon = if (selectedFormat == LabelPrintFormat.STICKER_58X40) Icons.Default.LocalOffer else Icons.Default.Print,
                gradientColors = if (selectedFormat == LabelPrintFormat.STICKER_58X40)
                    listOf(Color(0xFF059669), Color(0xFF10B981))
                else
                    listOf(Color(0xFF2563EB), Color(0xFF7C3AED)),
                enabled = true
            ) {
                showPrintConfirmDialog = true
            }

            // Bluetooth Status Banner
            if (!BluetoothPrinterManager.isConnected.value) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0x22F59E0B),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66F59E0B))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Printer Bluetooth belum terhubung. Buka tab Pengaturan untuk menyambungkan printer.",
                            fontSize = 12.sp,
                            color = Color(0xFFFDE68A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Anti-Accidental Print Confirmation Dialog
    if (showPrintConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPrintConfirmDialog = false },
            title = { Text("Konfirmasi Cetak", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Apakah Anda yakin ingin mencetak:\n\n" +
                    "• Format: ${selectedFormat.title}\n" +
                    "• Kurir: ${activeShipping.courier} (${activeShipping.service})\n" +
                    "• Penerima: ${activeShipping.recipientName}\n" +
                    "• No Resi: ${activeShipping.trackingNumber}\n\n" +
                    "Data akan langsung dikirim ke printer thermal."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrintConfirmDialog = false
                        val data = when (selectedFormat) {
                            LabelPrintFormat.STICKER_58X40 -> PrinterEngine.buildTSPLSticker58x40Data(workingOrder, DataManager.storeConfig.value)
                            LabelPrintFormat.LABEL_4X6 -> PrinterEngine.buildTSPLLabelData(workingOrder, DataManager.storeConfig.value)
                            LabelPrintFormat.SLIP_58 -> PrinterEngine.buildShippingSlip58Data(workingOrder, DataManager.storeConfig.value)
                        }

                        BluetoothPrinterManager.sendData(data) { success, errorMsg ->
                            if (success) {
                                Toast.makeText(context, "${selectedFormat.title} berhasil dicetak!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, errorMsg ?: "Gagal mencetak.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Ya, Cetak Sekarang", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintConfirmDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Address Book Sheet
    if (showAddressBook) {
        AddressBookScreen(
            onSelectRecipient = { sel ->
                recipientName = sel.name
                recipientPhone = sel.phone
                recipientAddress = sel.address
                postalCode = sel.postalCode
                courier = sel.defaultCourier
                if (sel.notes.isNotEmpty()) notes = sel.notes
                showAddressBook = false
            },
            onClose = { showAddressBook = false }
        )
    }

    // Quick Edit Dialog
    if (showEditDialog) {
        ShippingEditDialog(
            active = activeShipping,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                courier = updated.courier
                service = updated.service
                trackingNumber = updated.trackingNumber
                recipientName = updated.recipientName
                recipientPhone = updated.recipientPhone
                recipientAddress = updated.recipientAddress
                postalCode = updated.postalCode
                weightKg = updated.weightKg
                isCOD = updated.isCOD
                codAmount = updated.codAmount
                notes = updated.notes
                showEditDialog = false
            }
        )
    }
}

// 1. Sticker 58x40 Visual Component
@Composable
private fun Sticker58x40Preview(ship: ShippingInfo, config: StoreConfig) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ship.courier.uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Surface(
                    color = Color(0x1A000000),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "[${ship.service.take(8).uppercase()}]",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = Color.Black, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Barcode Simulation
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BarcodeLines(height = 28)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = ship.trackingNumber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Divider(color = Color.Black, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Recipient Details
            Row {
                Text("Kpd: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(ship.recipientName, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }
            Text("Telp: ${ship.recipientPhone}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(ship.recipientAddress, fontSize = 11.sp, color = Color.Black, maxLines = 2)
            if (ship.postalCode.isNotEmpty()) {
                Text("Pos: ${ship.postalCode}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color.Black, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ship.isCOD) {
                    Surface(color = Color.Black, shape = RoundedCornerShape(3.dp)) {
                        Text(
                            text = "COD: ${Order.formatRupiah(ship.codAmount)}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Surface(color = Color(0x1A000000), shape = RoundedCornerShape(3.dp)) {
                        Text(
                            text = "NON-COD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val senderName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
                Text(
                    text = "Dr: ${senderName.take(14)}",
                    fontSize = 10.sp,
                    color = Color.Black
                )
            }
        }
    }
}

// 2. Label 4x6 Visual Component
@Composable
private fun Label4x6Preview(ship: ShippingInfo, config: StoreConfig) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ship.courier.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Text(
                    text = "[${ship.service.uppercase()}]",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Black, thickness = 2.dp)

            // Barcode
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BarcodeLines(height = 42)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = ship.trackingNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Divider(color = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))

            // COD & Weight
            val weightDisplay = String.format(java.util.Locale.US, "%.1f", ship.weightKg)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (ship.isCOD) "C O D : ${Order.formatRupiah(ship.codAmount)}" else "NON-COD",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Text(
                    text = "BERAT: $weightDisplay KG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))

            // Pengirim
            val senderName = if (ship.senderName.isEmpty()) config.defaultSenderName else ship.senderName
            val senderPhone = if (ship.senderPhone.isEmpty()) config.defaultSenderPhone else ship.senderPhone
            Text("PENGIRIM: $senderName ($senderPhone)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)

            Spacer(modifier = Modifier.height(6.dp))
            Divider(color = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))

            // Penerima
            Text("PENERIMA / TUJUAN:", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
            Text(ship.recipientName, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Text("Telp: ${ship.recipientPhone}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(ship.recipientAddress, fontSize = 12.sp, color = Color.Black)
            if (ship.postalCode.isNotEmpty()) {
                Text("Kode Pos: ${ship.postalCode}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))

            // Catatan
            Text("INSTRUKSI: ${ship.notes.uppercase()}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

// 3. Slip 58mm Visual Component
@Composable
private fun Slip58Preview(ship: ShippingInfo) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🧾 SLIP EKSPEDISI STRUK 58MM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Divider(color = Color.Black, modifier = Modifier.padding(vertical = 4.dp))
            Text(ship.courier.uppercase(), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.Black)
            Text("[${ship.service.uppercase()}]", fontSize = 11.sp, color = Color.Black)
            Text("RESI: ${ship.trackingNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Divider(color = Color.Black, modifier = Modifier.padding(vertical = 4.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("PENERIMA:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(ship.recipientName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text("Telp: ${ship.recipientPhone}", fontSize = 11.sp, color = Color.Black)
                Text("Alamat: ${ship.recipientAddress}", fontSize = 11.sp, color = Color.Black)
            }
            Divider(color = Color.Black, modifier = Modifier.padding(vertical = 4.dp))
            Text(
                if (ship.isCOD) "*** COD: ${Order.formatRupiah(ship.codAmount)} ***" else "*** NON-COD ***",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text("--- AUTO-CUT SETELAH CETAK ---", fontSize = 9.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun BarcodeLines(height: Int) {
    Row(
        modifier = Modifier.height(height.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (i in 0..24) {
            val width = if (i % 3 == 0) 3.dp else (if (i % 2 == 0) 1.dp else 2.dp)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(Color.Black)
            )
        }
    }
}

@Composable
private fun ShippingEditDialog(
    active: ShippingInfo,
    onDismiss: () -> Unit,
    onSave: (ShippingInfo) -> Unit
) {
    var courier by remember { mutableStateOf(active.courier) }
    var service by remember { mutableStateOf(active.service) }
    var trackingNumber by remember { mutableStateOf(active.trackingNumber) }
    var recipientName by remember { mutableStateOf(active.recipientName) }
    var recipientPhone by remember { mutableStateOf(active.recipientPhone) }
    var recipientAddress by remember { mutableStateOf(active.recipientAddress) }
    var postalCode by remember { mutableStateOf(active.postalCode) }
    var notes by remember { mutableStateOf(active.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah Data Resi", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = courier, onValueChange = { courier = it }, label = { Text("Kurir") })
                OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Layanan") })
                OutlinedTextField(value = trackingNumber, onValueChange = { trackingNumber = it }, label = { Text("Nomor Resi / AWB") })
                OutlinedTextField(value = recipientName, onValueChange = { recipientName = it }, label = { Text("Nama Penerima") })
                OutlinedTextField(value = recipientPhone, onValueChange = { recipientPhone = it }, label = { Text("No. HP") })
                OutlinedTextField(value = recipientAddress, onValueChange = { recipientAddress = it }, label = { Text("Alamat") }, maxLines = 3)
                OutlinedTextField(value = postalCode, onValueChange = { postalCode = it }, label = { Text("Kode Pos") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Catatan") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val updated = active.copy(
                    courier = courier,
                    service = service,
                    trackingNumber = trackingNumber,
                    recipientName = recipientName,
                    recipientPhone = recipientPhone,
                    recipientAddress = recipientAddress,
                    postalCode = postalCode,
                    notes = notes
                )
                onSave(updated)
            }) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

