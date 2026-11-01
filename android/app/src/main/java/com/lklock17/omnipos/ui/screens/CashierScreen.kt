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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.*
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassBackground
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x330F1424))
            )
        },
        containerColor = Color(0xFF0F1424)
    ) { padding ->
        LiquidGlassBackground(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar with Camera Barcode Scanner & Hardware Scanner Auto-Add
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                val trimmed = query.trim()
                                if (trimmed.length >= 6) {
                                    val matched = DataManager.products.firstOrNull { 
                                        it.barcode.isNotBlank() && it.barcode.equals(trimmed, ignoreCase = true)
                                    }
                                    if (matched != null) {
                                        val existing = cart.indexOfFirst { it.product.id == matched.id }
                                        if (existing >= 0) {
                                            val current = cart[existing]
                                            cart[existing] = current.copy(quantity = current.quantity + 1)
                                        } else {
                                            cart.add(OrderItem(product = matched, quantity = 1))
                                        }
                                        Toast.makeText(context, "+1 ${matched.name} ditambahkan!", Toast.LENGTH_SHORT).show()
                                        searchQuery = ""
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Cari produk atau scan barcode...", color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.Gray)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0x18FFFFFF),
                                unfocusedContainerColor = Color(0x10FFFFFF),
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0x28FFFFFF)
                            )
                        )

                        // Tombol Kamera Barcode Scanner (Gradient Liquid Glass)
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF2563EB), Color(0xFF06B6D4))
                                    )
                                )
                                .border(1.dp, Color(0x4DFFFFFF), RoundedCornerShape(12.dp))
                                .clickable {
                                    try {
                                        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                                            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
                                            .enableAutoZoom()
                                            .build()
                                        val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
                                        scanner.startScan()
                                            .addOnSuccessListener { barcode ->
                                                val code = barcode.rawValue?.trim() ?: ""
                                                if (code.isNotEmpty()) {
                                                    val matched = DataManager.products.firstOrNull {
                                                        it.barcode.equals(code, ignoreCase = true) || it.sku.equals(code, ignoreCase = true)
                                                    }
                                                    if (matched != null) {
                                                        val existing = cart.indexOfFirst { it.product.id == matched.id }
                                                        if (existing >= 0) {
                                                            val current = cart[existing]
                                                            cart[existing] = current.copy(quantity = current.quantity + 1)
                                                        } else {
                                                            cart.add(OrderItem(product = matched, quantity = 1))
                                                        }
                                                        Toast.makeText(context, "+1 ${matched.name} ditambahkan!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "Produk dengan barcode $code tidak ditemukan.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                Toast.makeText(context, "Scanner: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Buka kamera scanner gagal: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode Kamera", tint = Color.White)
                        }
                    }

                    // Category Chips (Identik dengan iOS Capsule Gradient)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DataManager.categories) { cat ->
                            val isSelected = selectedCategory == cat
                            val bgBrush = if (isSelected) {
                                Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4)))
                            } else {
                                Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF)))
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(bgBrush)
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF38BDF8) else Color(0x24FFFFFF),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Products Grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
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

                // Floating Bottom Cart Bar (Identik dengan iOS Floating Pill)
                if (cartCount > 0) {
                    val cartGradient = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1D4ED8), Color(0xFF06B6D4))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(cartGradient)
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(22.dp))
                            .clickable { showCartSheet = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color.White,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "$cartCount",
                                        color = Color(0xFF1D4ED8),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Buka Keranjang",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Text(
                                text = Order.formatRupiah(cartTotal),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
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
                        LabelPrintFormat.STICKER_55X30 -> PrinterEngine.buildTSPLSticker55x30Data(lastCompletedOrder!!, DataManager.storeConfig.value)
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
            .clickable(onClick = onAddToCart),
        cornerRadius = 16.dp
    ) {
        // Visual Header / Banner matching iOS
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0x383B82F6), Color(0x288B5CF6))
                    )
                )
        ) {
            Icon(
                imageVector = categoryIcon(product.category),
                contentDescription = null,
                tint = Color(0xFF38BDF8).copy(alpha = 0.85f),
                modifier = Modifier
                    .size(42.dp)
                    .align(Alignment.Center)
            )

            // Stock Pill Badge in Top-Right
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                color = if (product.isLowStock) Color(0xDDEF4444) else Color(0xB2000000),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Stok: ${product.stock}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Product Details
        Text(
            text = product.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            minLines = 2
        )

        if (product.sku.isNotEmpty() || product.barcode.isNotEmpty()) {
            Text(
                text = "SKU: ${product.sku.ifEmpty { product.barcode }}",
                fontSize = 10.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Order.formatRupiah(product.price),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF38BDF8)
            )

            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                color = Color(0xFF2563EB)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Tambah",
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

private fun categoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category.lowercase()) {
        "makanan" -> Icons.Default.Restaurant
        "minuman" -> Icons.Default.LocalCafe
        "snack" -> Icons.Default.Fastfood
        "pakaian" -> Icons.Default.Checkroom
        "elektronik" -> Icons.Default.Devices
        "aksesoris" -> Icons.Default.Watch
        else -> Icons.Default.Inventory2
    }
}

