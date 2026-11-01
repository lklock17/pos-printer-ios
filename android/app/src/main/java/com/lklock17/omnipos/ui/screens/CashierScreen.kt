package com.lklock17.omnipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lklock17.omnipos.models.*
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassButton
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen() {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("Semua") }
    var searchQuery by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<OrderItem>() }

    // Checkout Sheet State
    var showCartSheet by remember { mutableStateOf(false) }
    var orderType by remember { mutableStateOf(OrderType.OFFLINE) }
    var paymentMethod by remember { mutableStateOf(PaymentMethod.CASH) }
    var cashGivenText by remember { mutableStateOf("") }
    var shippingInfo by remember { mutableStateOf(ShippingInfo()) }
    var showShippingDialog by remember { mutableStateOf(false) }
    var showAddressBookSheet by remember { mutableStateOf(false) }

    // Success & Print Confirmation State
    var lastCompletedOrder by remember { mutableStateOf<Order?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var printTargetFormat by remember { mutableStateOf<LabelPrintFormat?>(null) }
    var showPrintReceiptConfirm by remember { mutableStateOf(false) }
    var showPrintShippingConfirm by remember { mutableStateOf(false) }

    val filteredProducts = remember(selectedCategory, searchQuery, DataManager.products.size) {
        DataManager.products.filter { p ->
            val matchCat = (selectedCategory == "Semua" || p.category == selectedCategory)
            val matchQuery = searchQuery.isBlank() || p.name.contains(searchQuery, ignoreCase = true) || p.barcode.contains(searchQuery)
            matchCat && matchQuery
        }
    }

    val cartTotal = remember(cart.toList()) { cart.sumOf { it.subtotal } }
    val cartCount = remember(cart.toList()) { cart.sumOf { it.quantity } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kasir OmniPOS", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0E1A))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text("Cari produk atau scan barcode...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x0EFFFFFF)
                    )
                )

                // Category Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DataManager.categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategory = cat },
                            color = if (isSelected) Color(0xFF2563EB) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // Products Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (cartCount > 0) 80.dp else 0.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductItemCard(
                            product = product,
                            onAddToCart = {
                                val idx = cart.indexOfFirst { it.product.id == product.id }
                                if (idx != -1) {
                                    val current = cart[idx]
                                    cart[idx] = current.copy(quantity = current.quantity + 1)
                                } else {
                                    cart.add(OrderItem(product = product, quantity = 1))
                                }
                            }
                        )
                    }
                }
            }

            // Floating Bottom Cart Bar
            if (cartCount > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showCartSheet = true },
                    color = Color(0xFF1D4ED8),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = Color.White) {
                                Text("$cartCount", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Lihat Keranjang", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text(Order.formatRupiah(cartTotal), color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // Cart & Checkout Bottom Sheet
    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            containerColor = Color(0xFF0F172A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Keranjang Belanja", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)

                // Order Type Selector
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OrderType.values().forEach { type ->
                        val isSel = orderType == type
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { orderType = type },
                            color = if (isSel) Color(0xFF2563EB) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = type.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // If Shipping Order: Quick address summary & edit
                if (orderType == OrderType.SHIPPING) {
                    LiquidGlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (shippingInfo.recipientName.isEmpty()) "Pilih Alamat Pengiriman Paket" else "Tujuan: ${shippingInfo.recipientName} (${shippingInfo.courier})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (shippingInfo.recipientAddress.isEmpty()) "Tap untuk isi alamat penerima & resi" else shippingInfo.recipientAddress,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                            }
                            Button(
                                onClick = { showShippingDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Atur Resi", fontSize = 11.sp)
                            }
                        }
                    }
                }

                // Payment Method Selector
                Text("Metode Pembayaran", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethod.values().forEach { pm ->
                        val isSel = paymentMethod == pm
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { paymentMethod = pm },
                            color = if (isSel) Color(0xFF10B981) else Color(0x1AFFFFFF),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = pm.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Cash Amount Input
                if (paymentMethod == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = cashGivenText,
                        onValueChange = { cashGivenText = it },
                        label = { Text("Uang Tunai Diterima (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Complete Order Button
                val givenAmount = cashGivenText.toDoubleOrNull() ?: cartTotal
                val change = maxOf(0.0, givenAmount - cartTotal)

                LiquidGlassButton(
                    text = "Bayar Sekarang (${Order.formatRupiah(cartTotal)})",
                    icon = Icons.Default.CheckCircle,
                    gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669))
                ) {
                    val order = Order(
                        items = cart.toList(),
                        total = cartTotal,
                        paymentMethod = paymentMethod,
                        paymentAmount = givenAmount,
                        change = change,
                        orderType = orderType,
                        shippingInfo = if (orderType == OrderType.SHIPPING) shippingInfo else null
                    )
                    DataManager.processOrder(order, context)
                    lastCompletedOrder = order
                    cart.clear()
                    showCartSheet = false
                    showSuccessDialog = true
                }
            }
        }
    }

    // Success Modal with Anti-Accidental Print Buttons
    if (showSuccessDialog && lastCompletedOrder != null) {
        val completed = lastCompletedOrder!!
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transaksi Berhasil!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No. Nota: ${completed.invoiceNumber}")
                    Text("Total: ${completed.formattedTotal}", fontWeight = FontWeight.Bold)
                    if (completed.change > 0) {
                        Text("Kembalian: ${Order.formatRupiah(completed.change)}", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PILIHAN CETAK (KONFIRMASI):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)

                    // 1. Print Receipt Button
                    Button(
                        onClick = { showPrintReceiptConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cetak Struk POS (Auto-Cut)")
                    }

                    // 2. If Shipping: Print 58x40 Sticker Button
                    if (completed.orderType == OrderType.SHIPPING) {
                        Button(
                            onClick = {
                                printTargetFormat = LabelPrintFormat.STICKER_58X40
                                showPrintShippingConfirm = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Stiker 58x40 mm (TSPL)")
                        }

                        // 3. Print 4x6 Label Button
                        Button(
                            onClick = {
                                printTargetFormat = LabelPrintFormat.LABEL_4X6
                                showPrintShippingConfirm = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Label 4x6 Thermal (TSPL)")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSuccessDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    // Anti-Accidental Print Struk Confirmation
    if (showPrintReceiptConfirm && lastCompletedOrder != null) {
        AlertDialog(
            onDismissRequest = { showPrintReceiptConfirm = false },
            title = { Text("Konfirmasi Cetak Struk") },
            text = { Text("Apakah Anda yakin ingin mencetak struk nota ${lastCompletedOrder?.invoiceNumber}?") },
            confirmButton = {
                Button(onClick = {
                    showPrintReceiptConfirm = false
                    val data = PrinterEngine.buildReceiptData(lastCompletedOrder!!, DataManager.storeConfig.value)
                    BluetoothPrinterManager.sendData(data) { ok, err ->
                        Toast.makeText(context, if (ok) "Struk berhasil dicetak!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Ya, Cetak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintReceiptConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Anti-Accidental Print Shipping Label / Sticker Confirmation
    if (showPrintShippingConfirm && lastCompletedOrder != null && printTargetFormat != null) {
        val fmt = printTargetFormat!!
        val ship = lastCompletedOrder?.shippingInfo
        AlertDialog(
            onDismissRequest = { showPrintShippingConfirm = false },
            title = { Text("Konfirmasi Cetak ${fmt.title}") },
            text = {
                Text(
                    "Cetak ${fmt.title} ke printer thermal?\n\n" +
                    "• Penerima: ${ship?.recipientName}\n" +
                    "• Kurir: ${ship?.courier}\n" +
                    "• Resi: ${ship?.trackingNumber}"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPrintShippingConfirm = false
                    val data = when (fmt) {
                        LabelPrintFormat.STICKER_58X40 -> PrinterEngine.buildTSPLSticker58x40Data(lastCompletedOrder!!, DataManager.storeConfig.value)
                        LabelPrintFormat.LABEL_4X6 -> PrinterEngine.buildTSPLLabelData(lastCompletedOrder!!, DataManager.storeConfig.value)
                        LabelPrintFormat.SLIP_58 -> PrinterEngine.buildShippingSlip58Data(lastCompletedOrder!!, DataManager.storeConfig.value)
                    }
                    BluetoothPrinterManager.sendData(data) { ok, err ->
                        Toast.makeText(context, if (ok) "${fmt.title} berhasil dicetak!" else err ?: "Gagal.", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Ya, Cetak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintShippingConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Address Book Sheet Selection
    if (showAddressBookSheet) {
        AddressBookScreen(
            onSelectRecipient = { rec ->
                shippingInfo = shippingInfo.copy(
                    recipientName = rec.name,
                    recipientPhone = rec.phone,
                    recipientAddress = rec.address,
                    postalCode = rec.postalCode,
                    courier = rec.defaultCourier,
                    notes = rec.notes
                )
                showAddressBookSheet = false
            },
            onClose = { showAddressBookSheet = false }
        )
    }

    // Shipping Input Dialog in Cashier
    if (showShippingDialog) {
        ShippingEditDialog(
            active = shippingInfo,
            onDismiss = { showShippingDialog = false },
            onSave = { updated ->
                shippingInfo = updated
                showShippingDialog = false
            }
        )
    }
}

@Composable
private fun ProductItemCard(
    product: Product,
    onAddToCart: () -> Unit
) {
    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddToCart)
    ) {
        Text(
            text = product.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = Order.formatRupiah(product.price),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF38BDF8)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stok: ${product.stock}",
                fontSize = 11.sp,
                color = if (product.isLowStock) Color(0xFFEF4444) else Color.Gray
            )
            Surface(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape),
                color = Color(0xFF2563EB)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
