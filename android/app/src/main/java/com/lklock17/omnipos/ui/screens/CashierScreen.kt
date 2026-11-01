package com.lklock17.omnipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.*
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassBackground
import com.lklock17.omnipos.ui.components.LiquidGlassButton
import com.lklock17.omnipos.ui.components.LiquidGlassCard
import com.lklock17.omnipos.utils.ImageHelper

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

    // Scan Popup Flow: "Mau pesan berapa?" & "Sudah atau Tambah Lagi?"
    var scannedProductForQty by remember { mutableStateOf<Product?>(null) }
    var scanQuantity by remember { mutableStateOf(1) }
    var showPostScanChoice by remember { mutableStateOf(false) }
    var lastAddedProductName by remember { mutableStateOf("") }
    var lastAddedQty by remember { mutableStateOf(1) }

    // Success & Print Confirmation State
    var lastCompletedOrder by remember { mutableStateOf<Order?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var printTargetFormat by remember { mutableStateOf<LabelPrintFormat?>(null) }
    var showPrintReceiptConfirm by remember { mutableStateOf(false) }
    var showPrintShippingConfirm by remember { mutableStateOf(false) }

    val launchScanner: () -> Unit = {
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
                            scannedProductForQty = matched
                            scanQuantity = 1
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
    }

    val filteredProducts = remember(selectedCategory, searchQuery, DataManager.products.size) {
        DataManager.products.filter { p ->
            val matchCat = (selectedCategory == "Semua" || p.category.equals(selectedCategory, ignoreCase = true))
            val matchQuery = searchQuery.isBlank() ||
                p.name.contains(searchQuery, ignoreCase = true) ||
                p.barcode.contains(searchQuery) ||
                p.sku.contains(searchQuery, ignoreCase = true)
            matchCat && matchQuery
        }
    }

    val cartTotal = remember(cart.toList()) { cart.sumOf { it.subtotal } }
    val cartCount = remember(cart.toList()) { cart.sumOf { it.quantity } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kasir OmniPOS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        val storeName = DataManager.storeConfig.value.storeName.ifEmpty { "Toko Utama" }
                        Text(storeName, color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                },
                actions = {
                    if (cart.isNotEmpty()) {
                        IconButton(onClick = { cart.clear() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Kosongkan", tint = Color(0xFFEF4444))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x330F1424))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        LiquidGlassBackground(modifier = Modifier.padding(padding)) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Search Bar Compact & Barcode Camera Scanner (Height 42dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Search Box (42dp height)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Cari produk / ketik barcode...",
                                        color = Color.Gray,
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { query ->
                                        searchQuery = query
                                        val trimmed = query.trim()
                                        if (trimmed.length >= 6) {
                                            val matched = DataManager.products.firstOrNull {
                                                it.barcode.isNotBlank() && it.barcode.equals(trimmed, ignoreCase = true)
                                            }
                                            if (matched != null) {
                                                searchQuery = ""
                                                scannedProductForQty = matched
                                                scanQuantity = 1
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Tombol Camera Barcode Scanner (42dp height)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF0284C7), Color(0xFF06B6D4))
                                    )
                                )
                                .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(12.dp))
                                .clickable { launchScanner() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode Kamera",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Category Chips Horizontal
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

                    // 3. Products Grid (2 Columns)
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
                            val inCartItem = cart.firstOrNull { it.product.id == product.id }
                            ProductItemCard(
                                product = product,
                                cartItemQuantity = inCartItem?.quantity ?: 0,
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

                // 4. Floating Bottom Cart Bar
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

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = Order.formatRupiah(cartTotal),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // 5. Cart & Checkout Bottom Sheet
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
                // Header Sheet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Keranjang Belanja ($cartCount)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    TextButton(onClick = { cart.clear(); showCartSheet = false }) {
                        Text("Kosongkan", color = Color(0xFFEF4444), fontSize = 13.sp)
                    }
                }

                // Daftar Item di Keranjang (Scrollable)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cart, key = { it.product.id }) { item ->
                        CartItemRow(
                            item = item,
                            onIncrease = {
                                val idx = cart.indexOfFirst { it.product.id == item.product.id }
                                if (idx != -1) {
                                    cart[idx] = item.copy(quantity = item.quantity + 1)
                                }
                            },
                            onDecrease = {
                                val idx = cart.indexOfFirst { it.product.id == item.product.id }
                                if (idx != -1) {
                                    if (item.quantity > 1) {
                                        cart[idx] = item.copy(quantity = item.quantity - 1)
                                    } else {
                                        cart.removeAt(idx)
                                    }
                                }
                            },
                            onRemove = {
                                cart.removeAll { it.product.id == item.product.id }
                            }
                        )
                    }
                }

                Divider(color = Color(0x22FFFFFF))

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
                                textAlign = TextAlign.Center
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
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Cash Amount Input & Quick Cash Buttons
                if (paymentMethod == PaymentMethod.CASH) {
                    OutlinedTextField(
                        value = cashGivenText,
                        onValueChange = { cashGivenText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Uang Tunai Diterima (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Quick Cash Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { cashGivenText = cartTotal.toLong().toString() },
                            color = Color(0x2210B981),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Uang Pas", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(vertical = 6.dp), textAlign = TextAlign.Center)
                        }

                        listOf(10000, 20000, 50000, 100000).forEach { addNom ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val cur = cashGivenText.toDoubleOrNull() ?: cartTotal
                                        cashGivenText = (cur + addNom).toLong().toString()
                                    },
                                color = Color(0x1AFFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+${addNom / 1000}rb", fontSize = 11.sp, color = Color.White, modifier = Modifier.padding(vertical = 6.dp), textAlign = TextAlign.Center)
                            }
                        }
                    }

                    val givenAmount = cashGivenText.toDoubleOrNull() ?: cartTotal
                    val change = maxOf(0.0, givenAmount - cartTotal)
                    if (change > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Kembalian:", fontSize = 13.sp, color = Color.Gray)
                            Text(
                                text = Order.formatRupiah(change),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cetak Stiker Paket 58x40 (TSPL)")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSuccessDialog = false }) {
                    Text("Selesai")
                }
            }
        )
    }

    // Dialog Konfirmasi Cetak Struk Kasir
    if (showPrintReceiptConfirm && lastCompletedOrder != null) {
        val completed = lastCompletedOrder!!
        AlertDialog(
            onDismissRequest = { showPrintReceiptConfirm = false },
            title = { Text("Konfirmasi Cetak Struk") },
            text = { Text("Yakin ingin mencetak struk kasir untuk nota ${completed.invoiceNumber}?") },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = PrinterEngine.buildReceiptData(completed, DataManager.storeConfig.value)
                        BluetoothPrinterManager.sendData(payload) { success, err ->
                            if (success) {
                                Toast.makeText(context, "Struk kasir berhasil dicetak!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal mencetak struk: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                        showPrintReceiptConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Ya, Cetak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintReceiptConfirm = false }) { Text("Batal") }
            }
        )
    }

    // Dialog Konfirmasi Cetak Label Paket
    if (showPrintShippingConfirm && lastCompletedOrder != null) {
        val completed = lastCompletedOrder!!
        AlertDialog(
            onDismissRequest = { showPrintShippingConfirm = false },
            title = { Text("Konfirmasi Cetak Label Paket") },
            text = { Text("Yakin ingin mencetak label paket stiker 58x40 untuk nota ${completed.invoiceNumber}?") },
            confirmButton = {
                Button(
                    onClick = {
                        val payload = PrinterEngine.buildTSPLSticker58x40Data(completed, DataManager.storeConfig.value)
                        BluetoothPrinterManager.sendData(payload) { success, err ->
                            if (success) {
                                Toast.makeText(context, "Label paket stiker berhasil dicetak!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Gagal mencetak: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                        showPrintShippingConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Ya, Cetak")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrintShippingConfirm = false }) { Text("Batal") }
            }
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

    // 1. Popup "Mau Pesan Berapa?" Saat Scan Sukses
    if (scannedProductForQty != null) {
        val prod = scannedProductForQty!!
        val bitmap = remember(prod.imageBase64) { ImageHelper.decodeBase64ToBitmap(prod.imageBase64) }

        AlertDialog(
            onDismissRequest = { scannedProductForQty = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mau Pesan Berapa?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Ringkasan Produk
                    Surface(
                        color = Color(0x1FFFFFFF),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = prod.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x2838BDF8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(categoryIcon(prod.category), contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(prod.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = Order.formatRupiah(prod.price),
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Stok tersedia: ${prod.stock} ${prod.unit}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Stepper Jumlah Pesanan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledIconButton(
                            onClick = { if (scanQuantity > 1) scanQuantity -= 1 },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x33FFFFFF))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Kurang", tint = Color.White)
                        }

                        Text(
                            text = "$scanQuantity",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        FilledIconButton(
                            onClick = { scanQuantity += 1 },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Tambah", tint = Color.White)
                        }
                    }

                    // Quick Chips (+1, +2, +5, +10)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10).forEach { qty ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { scanQuantity = qty },
                                color = if (scanQuantity == qty) Color(0xFF0284C7) else Color(0x1FFFFFFF),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$qty",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Subtotal Preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Subtotal:", fontSize = 13.sp, color = Color.Gray)
                        Text(
                            text = Order.formatRupiah(prod.price * scanQuantity),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = maxOf(1, scanQuantity)
                        val existing = cart.indexOfFirst { it.product.id == prod.id }
                        if (existing >= 0) {
                            val current = cart[existing]
                            cart[existing] = current.copy(quantity = current.quantity + qty)
                        } else {
                            cart.add(OrderItem(product = prod, quantity = qty))
                        }
                        lastAddedProductName = prod.name
                        lastAddedQty = qty
                        scannedProductForQty = null
                        showPostScanChoice = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedProductForQty = null }) {
                    Text("Batal", color = Color.Gray)
                }
            }
        )
    }

    // 2. Dialog Pilihan: "Sudah (Lihat Keranjang)" atau "Tambah Produk Lagi"
    if (showPostScanChoice) {
        AlertDialog(
            onDismissRequest = { showPostScanChoice = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Produk Ditambahkan!", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "$lastAddedQty x \"$lastAddedProductName\" telah masuk ke keranjang.\n\nApakah sudah selesai atau mau tambah produk lagi?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPostScanChoice = false
                        launchScanner()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tambah Produk Lagi")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPostScanChoice = false
                        showCartSheet = true
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sudah (Lihat Keranjang)", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun CartItemRow(
    item: OrderItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    val bitmap = remember(item.product.imageBase64) { ImageHelper.decodeBase64ToBitmap(item.product.imageBase64) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color(0x2EFFFFFF)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = item.product.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = categoryIcon(item.product.category),
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Nama & Harga
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${Order.formatRupiah(item.product.price)} / ${item.product.unit}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Text(
                    text = Order.formatRupiah(item.subtotal),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }

            // Stepper Quantity
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x28FFFFFF))
                        .clickable(onClick = onDecrease),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-1", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                Text(
                    text = "${item.quantity}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB))
                        .clickable(onClick = onIncrease),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun ProductItemCard(
    product: Product,
    cartItemQuantity: Int = 0,
    onAddToCart: () -> Unit
) {
    val bitmap = remember(product.imageBase64) { ImageHelper.decodeBase64ToBitmap(product.imageBase64) }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !product.isOutOfStock, onClick = onAddToCart),
        cornerRadius = 16.dp
    ) {
        // Visual Header / Banner (Photo or Gradient Icon)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x24FFFFFF))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0x383B82F6), Color(0x288B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon(product.category),
                        contentDescription = null,
                        tint = Color(0xFF38BDF8).copy(alpha = 0.85f),
                        modifier = Modifier.size(42.dp)
                    )
                }
            }

            // Stock Pill Badge in Top-Right
            val stockColor = when {
                product.isOutOfStock -> Color(0xFFEF4444)
                product.isLowStock -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                color = stockColor.copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (product.isOutOfStock) "Habis" else "${product.stock} ${product.unit}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            // Cart Quantity Indicator in Top-Left (if in cart)
            if (cartItemQuantity > 0) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    color = Color(0xFF2563EB),
                    shape = CircleShape
                ) {
                    Text(
                        text = "${cartItemQuantity}x",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
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
                color = if (product.isOutOfStock) Color.Gray else Color(0xFF38BDF8)
            )

            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                color = if (product.isOutOfStock) Color(0x33FFFFFF) else Color(0xFF2563EB)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Tambah",
                    tint = if (product.isOutOfStock) Color.Gray else Color.White,
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
