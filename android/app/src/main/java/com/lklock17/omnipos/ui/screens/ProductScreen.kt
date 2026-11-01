package com.lklock17.omnipos.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.BarcodeHelper
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.Product
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassBackground
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen() {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf(0) } // 0: Semua, 1: Menipis, 2: Habis
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var productToPrintBarcode by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(searchText, stockFilter, DataManager.products.size) {
        DataManager.products.filter { p ->
            val matchQuery = searchText.isBlank() ||
                p.name.contains(searchText, ignoreCase = true) ||
                p.sku.contains(searchText, ignoreCase = true) ||
                p.barcode.contains(searchText)
            val matchStock = when (stockFilter) {
                1 -> p.isLowStock && p.stock > 0
                2 -> p.stock <= 0
                else -> true
            }
            matchQuery && matchStock
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Katalog Produk & Stok", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Tambah Produk", tint = Color(0xFF38BDF8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x330F1424))
            )
        },
        containerColor = Color(0xFF0F1424)
    ) { padding ->
        LiquidGlassBackground(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Cari produk berdasarkan nama / SKU...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
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

                // Stock Filter Tabs (Identik dengan iOS Segmented Picker)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val lowCount = DataManager.products.count { it.isLowStock && it.stock > 0 }
                    val outCount = DataManager.products.count { it.stock <= 0 }
                    val tabs = listOf(
                        "Semua (${DataManager.products.size})",
                        "Menipis ($lowCount)",
                        "Habis ($outCount)"
                    )
                    tabs.forEachIndexed { index, label ->
                        val isSelected = stockFilter == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF2563EB) else Color(0x1AFFFFFF))
                                .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0x20FFFFFF), RoundedCornerShape(10.dp))
                                .clickable { stockFilter = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        ProductManagementRow(
                            product = product,
                            onAdjustStock = { delta ->
                                DataManager.adjustStock(product.id, delta, context)
                            },
                            onPrintBarcode = { productToPrintBarcode = product },
                            onEdit = { productToEdit = product },
                            onDelete = { productToDelete = product }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || productToEdit != null) {
        val initial = productToEdit
        ProductFormDialog(
            product = initial,
            onDismiss = {
                showAddDialog = false
                productToEdit = null
            },
            onSave = { updated ->
                if (initial == null) {
                    DataManager.addProduct(updated, context)
                } else {
                    DataManager.updateProduct(updated, context)
                }
                showAddDialog = false
                productToEdit = null
            }
        )
    }

    // Delete Confirmation
    if (productToDelete != null) {
        val target = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Hapus Produk?") },
            text = { Text("Produk \"${target.name}\" akan dihapus dari katalog.") },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.deleteProduct(target, context)
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) { Text("Batal") }
            }
        )
    }

    // Print Barcode Dialog (55x30 mm - Max 10)
    if (productToPrintBarcode != null) {
        val prod = productToPrintBarcode!!
        PrintBarcodeTagDialog(
            product = prod,
            onDismiss = { productToPrintBarcode = null },
            onPrint = { qty ->
                if (!BluetoothPrinterManager.isConnected.value) {
                    Toast.makeText(context, "Printer belum terhubung! Silakan hubungkan Kassen MT200A di tab Pengaturan.", Toast.LENGTH_LONG).show()
                } else {
                    val storeName = DataManager.storeConfig.value.storeName
                    val singleLabelRaster = PrinterEngine.buildSingleProductBarcodeTag55x30Raster(prod, storeName)
                    val tearOffBlank = PrinterEngine.createBlankEscPosRaster(384, 284)
                    val count = qty.coerceIn(1, 10)
                    val labels = List(count) { singleLabelRaster }

                    Toast.makeText(context, "Mencetak $count stiker barcode untuk ${prod.name}...", Toast.LENGTH_SHORT).show()
                    BluetoothPrinterManager.sendBatchLabels(
                        labels = labels,
                        tearOffRaster = tearOffBlank,
                        interLabelDelayMs = 1300L,
                        onResult = { success, errMsg ->
                            if (success) {
                                Toast.makeText(context, "Selesai mencetak $count stiker!", Toast.LENGTH_SHORT).show()
                                productToPrintBarcode = null
                            } else {
                                Toast.makeText(context, "Gagal mencetak: $errMsg", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        )
    }
}

@Composable
private fun ProductManagementRow(
    product: Product,
    onAdjustStock: (Int) -> Unit,
    onPrintBarcode: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Thumbnail Category Icon (Matching iOS 50dp)
            Surface(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = Color(0x2EFFFFFF)
            ) {
                Icon(
                    imageVector = productCategoryIcon(product.category),
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Info Detail (Name, Category + SKU, Price)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0x332563EB),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = product.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SKU: ${product.sku.ifEmpty { "-" }}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = Order.formatRupiah(product.price),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Kolom Kanan Rapi: Badge Stok di Atas & Tombol Aksi Bersih di Bawah (Matching iOS)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stock Badge Pill
                Surface(
                    color = if (product.isLowStock) Color(0x33EF4444) else Color(0x3310B981),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Stok: ${product.stock} ${product.unit}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (product.isLowStock) Color(0xFFEF4444) else Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Row Aksi: Cetak Barcode, Quick - / +, Delete
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tombol Cetak Barcode (Hijau)
                    FilledIconButton(
                        onClick = onPrintBarcode,
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x3310B981))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Barcode",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Quick Minus (-)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                            .clickable { onAdjustStock(-1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "-1",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Quick Plus (+)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                            .clickable { onAdjustStock(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "+1",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Delete (Merah Halus)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x22EF4444))
                            .clickable { onDelete() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun productCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
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

@Composable
private fun PrintBarcodeTagDialog(
    product: Product,
    onDismiss: () -> Unit,
    onPrint: (Int) -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    val barcodeText = if (product.barcode.isNotEmpty()) product.barcode else product.sku.ifEmpty { "899000000000" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cetak Barcode Harga (55x30)", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Sticker 55x30 Mock Preview Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(8.dp)),
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = DataManager.storeConfig.value.storeName.ifEmpty { "OMNIPOS STORE" }.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Divider(color = Color.Black, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = product.name.take(24),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Barcode Simulation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            barcodeText.forEachIndexed { i, ch ->
                                val barW = if (ch.code % 2 == 0) 3.dp else 1.5.dp
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(barW)
                                        .background(Color.Black)
                                )
                                Spacer(modifier = Modifier.width(if (i % 3 == 0) 2.5.dp else 1.5.dp))
                            }
                        }
                        Text(
                            text = barcodeText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Divider(color = Color.Black, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                        // Price Box
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, Color.Black, RoundedCornerShape(4.dp)),
                            color = Color.White
                        ) {
                            Text(
                                text = Order.formatRupiah(product.price),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                modifier = Modifier
                                    .padding(vertical = 6.dp)
                                    .wrapContentWidth(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                // Quantity Selector (Max 10)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Jumlah Stiker yang Dicetak (Maks. 10):", fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x33FFFFFF))
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-1", tint = Color.White)
                        }
                        Text(
                            text = "$quantity Stiker",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                        FilledIconButton(
                            onClick = { if (quantity < 10) quantity++ },
                            enabled = quantity < 10,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Otomatis memajukan 1 stiker penutup agar pas disobek.",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPrint(quantity) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cetak Sekarang ($quantity)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun ProductFormDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var priceText by remember { mutableStateOf(product?.price?.toString() ?: "") }
    var costPriceText by remember { mutableStateOf(product?.costPrice?.toString() ?: "") }
    var stockText by remember { mutableStateOf(product?.stock?.toString() ?: "0") }
    var sku by remember { mutableStateOf(product?.sku ?: BarcodeHelper.generateSku()) }
    var barcode by remember { mutableStateOf(product?.barcode?.ifEmpty { BarcodeHelper.generateEan12() } ?: BarcodeHelper.generateEan12()) }
    var category by remember { mutableStateOf(product?.category ?: "Semua") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Tambah Produk" else "Edit Produk", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Produk*") }, singleLine = true)
                OutlinedTextField(value = priceText, onValueChange = { priceText = it }, label = { Text("Harga Jual (Rp)*") }, singleLine = true)
                OutlinedTextField(value = costPriceText, onValueChange = { costPriceText = it }, label = { Text("Harga Modal (Rp)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = stockText, onValueChange = { stockText = it }, label = { Text("Stok Awal") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { sku = BarcodeHelper.generateSku() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Buat SKU", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = { barcode = BarcodeHelper.generateEan12() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Buat Barcode", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalBarcode = barcode.trim().ifEmpty { BarcodeHelper.generateEan12() }
                    val finalSku = sku.trim().ifEmpty { BarcodeHelper.generateSku() }
                    val p = Product(
                        id = product?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        price = priceText.toDoubleOrNull() ?: 0.0,
                        costPrice = costPriceText.toDoubleOrNull() ?: 0.0,
                        stock = stockText.toIntOrNull() ?: 0,
                        sku = finalSku,
                        barcode = finalBarcode,
                        category = category,
                        unit = unit.trim()
                    )
                    onSave(p)
                },
                enabled = name.isNotBlank() && (priceText.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

