package com.lklock17.omnipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.OrderType
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderHistoryScreen() {
    val context = LocalContext.current
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var showReceiptConfirm by remember { mutableStateOf(false) }
    var showStickerConfirm by remember { mutableStateOf(false) }
    var showLabelConfirm by remember { mutableStateOf(false) }

    val todaySales = remember(DataManager.orders.size) { DataManager.getTodaySales() }
    val todayProfit = remember(DataManager.orders.size) { DataManager.getTodayProfit() }
    val todayCount = remember(DataManager.orders.size) { DataManager.getTodayOrders().size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat & Laporan", fontWeight = FontWeight.Bold, color = Color.White) },
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
        ) {
            // Summary Card
            LiquidGlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    text = "RINGKASAN HARI INI ($todayCount Transaksi)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Total Penjualan", fontSize = 11.sp, color = Color.Gray)
                        Text(Order.formatRupiah(todaySales), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text("Estimasi Keuntungan", fontSize = 11.sp, color = Color.Gray)
                        Text(Order.formatRupiah(todayProfit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }

            Text(
                text = "SEMUA TRANSAKSI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(DataManager.orders, key = { it.id }) { order ->
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOrder = order }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(order.invoiceNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                    if (order.orderType == OrderType.SHIPPING) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = Color(0x33F97316), shape = RoundedCornerShape(4.dp)) {
                                            Text("PAKET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316), modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Text("${order.items.size} item • ${order.formattedDate}", fontSize = 11.sp, color = Color.Gray)
                                if (order.shippingInfo != null) {
                                    Text("${order.shippingInfo.courier} • Kpd: ${order.shippingInfo.recipientName}", fontSize = 11.sp, color = Color(0xFF38BDF8))
                                }
                            }
                            Text(order.formattedTotal, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Order Detail Modal
    if (selectedOrder != null) {
        val ord = selectedOrder!!
        AlertDialog(
            onDismissRequest = { selectedOrder = null },
            title = { Text("Detail ${ord.invoiceNumber}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Waktu: ${ord.formattedDate}", fontSize = 12.sp, color = Color.Gray)
                    Text("Total: ${ord.formattedTotal}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Metode: ${ord.paymentMethod.label}", fontSize = 12.sp)

                    if (ord.shippingInfo != null) {
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Info Pengiriman:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Kurir: ${ord.shippingInfo.courier} (${ord.shippingInfo.service})", fontSize = 12.sp)
                        Text("Resi: ${ord.shippingInfo.trackingNumber}", fontSize = 12.sp)
                        Text("Tujuan: ${ord.shippingInfo.recipientName} (${ord.shippingInfo.recipientPhone})", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("OPSI CETAK ULANG:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                    Button(
                        onClick = { showReceiptConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Cetak Ulang Struk (Auto-Cut)")
                    }

                    if (ord.orderType == OrderType.SHIPPING) {
                        Button(
                            onClick = { showStickerConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("Cetak Stiker 58x40 mm (TSPL)")
                        }

                        Button(
                            onClick = { showLabelConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Text("Cetak Ulang Label 4x6 (TSPL)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedOrder = null }) { Text("Tutup") }
            }
        )
    }

    // Print Receipt Confirm
    if (showReceiptConfirm && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { showReceiptConfirm = false },
            title = { Text("Konfirmasi Cetak Struk") },
            text = { Text("Cetak ulang struk nota ${selectedOrder?.invoiceNumber}?") },
            confirmButton = {
                Button(onClick = {
                    showReceiptConfirm = false
                    val data = PrinterEngine.buildReceiptData(selectedOrder!!, DataManager.storeConfig.value)
                    BluetoothPrinterManager.sendData(data) { ok, err ->
                        Toast.makeText(context, if (ok) "Struk berhasil dicetak!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Ya, Cetak") }
            },
            dismissButton = { TextButton(onClick = { showReceiptConfirm = false }) { Text("Batal") } }
        )
    }

    // Print 58x40 Sticker Confirm
    if (showStickerConfirm && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { showStickerConfirm = false },
            title = { Text("Konfirmasi Cetak Stiker 58x40") },
            text = { Text("Cetak stiker alamat 58x40 mm untuk ${selectedOrder?.shippingInfo?.recipientName}?") },
            confirmButton = {
                Button(onClick = {
                    showStickerConfirm = false
                    val data = PrinterEngine.buildTSPLSticker58x40Data(selectedOrder!!, DataManager.storeConfig.value)
                    BluetoothPrinterManager.sendData(data) { ok, err ->
                        Toast.makeText(context, if (ok) "Stiker 58x40 berhasil dicetak!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Ya, Cetak") }
            },
            dismissButton = { TextButton(onClick = { showStickerConfirm = false }) { Text("Batal") } }
        )
    }

    // Print 4x6 Label Confirm
    if (showLabelConfirm && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { showLabelConfirm = false },
            title = { Text("Konfirmasi Cetak Label 4x6") },
            text = { Text("Cetak ulang label 4x6 untuk resi ${selectedOrder?.shippingInfo?.trackingNumber}?") },
            confirmButton = {
                Button(onClick = {
                    showLabelConfirm = false
                    val data = PrinterEngine.buildTSPLLabelData(selectedOrder!!, DataManager.storeConfig.value)
                    BluetoothPrinterManager.sendData(data) { ok, err ->
                        Toast.makeText(context, if (ok) "Label 4x6 berhasil dicetak!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Ya, Cetak") }
            },
            dismissButton = { TextButton(onClick = { showLabelConfirm = false }) { Text("Batal") } }
        )
    }
}
