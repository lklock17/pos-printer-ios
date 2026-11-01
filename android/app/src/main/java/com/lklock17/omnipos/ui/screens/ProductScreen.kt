package com.lklock17.omnipos.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lklock17.omnipos.models.BarcodeHelper
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.Product
import com.lklock17.omnipos.services.BluetoothPrinterManager
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.services.PrinterEngine
import com.lklock17.omnipos.ui.components.LiquidGlassBackground
import com.lklock17.omnipos.ui.components.LiquidGlassCard
import com.lklock17.omnipos.utils.ImageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen() {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var stockFilter by remember { mutableStateOf(0) } // 0: Semua, 1: Menipis, 2: Habis
    var selectedCategoryFilter by remember { mutableStateOf("Semua") }
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }
    var productToPrintBarcode by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(searchText, stockFilter, selectedCategoryFilter, DataManager.products.size) {
        DataManager.products.filter { p ->
            val matchQuery = searchText.isBlank() ||
                p.name.contains(searchText, ignoreCase = true) ||
                p.sku.contains(searchText, ignoreCase = true) ||
                p.barcode.contains(searchText)
            val matchCategory = selectedCategoryFilter == "Semua" || p.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchStock = when (stockFilter) {
                1 -> p.isLowStock && p.stock > 0
                2 -> p.stock <= 0
                else -> true
            }
            matchQuery && matchCategory && matchStock
        }
    }

    val totalStockValue = remember(DataManager.products.toList()) {
        DataManager.products.sumOf { it.price * it.stock }
    }
    val lowStockCount = remember(DataManager.products.toList()) {
        DataManager.products.count { it.isLowStock && it.stock > 0 }
    }
    val outOfStockCount = remember(DataManager.products.toList()) {
        DataManager.products.count { it.stock <= 0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Katalog Produk & Stok", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                        Text("${DataManager.products.size} SKU Terdaftar", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            productToEdit = null
                            showProductForm = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tambah", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0x330F1424))
            )
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        LiquidGlassBackground(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 1. Inventory Summary Bar (Statistik Singkat Toko)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Nilai Stok Card
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp)),
                        color = Color(0x1F2563EB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("Total Nilai Stok", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = Order.formatRupiah(totalStockValue),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Menipis Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { stockFilter = if (stockFilter == 1) 0 else 1 },
                        color = if (stockFilter == 1) Color(0x33F59E0B) else Color(0x14FFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (stockFilter == 1) Color(0xFFF59E0B) else Color(0x1AFFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Stok Menipis", fontSize = 10.sp, color = Color(0xFFF59E0B))
                            Text("$lowStockCount Item", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    // Habis Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { stockFilter = if (stockFilter == 2) 0 else 2 },
                        color = if (stockFilter == 2) Color(0x33EF4444) else Color(0x14FFFFFF),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (stockFilter == 2) Color(0xFFEF4444) else Color(0x1AFFFFFF))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text("Stok Habis", fontSize = 10.sp, color = Color(0xFFEF4444))
                            Text("$outOfStockCount Item", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // 2. Search Field Modern
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text("Cari nama produk, SKU, barcode...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x0EFFFFFF),
                        focusedBorderColor = Color(0xFF0EA5E9),
                        unfocusedBorderColor = Color(0x22FFFFFF)
                    )
                )

                // 3. Category Filter Chips (Horizontal)
                LazyRow(
                    modifier = Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DataManager.categories) { cat ->
                        val isSel = selectedCategoryFilter == cat
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedCategoryFilter = cat },
                            color = if (isSel) Color(0xFF2563EB) else Color(0x14FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSel) Color(0xFF38BDF8) else Color(0x1CFFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // 4. Product List
                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(54.dp), tint = Color(0x40FFFFFF))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Tidak ada produk ditemukan", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    productToEdit = null
                                    showProductForm = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("+ Tambah Produk Baru")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filteredProducts, key = { it.id }) { product ->
                            ProductManagementRow(
                                product = product,
                                onAdjustStock = { delta ->
                                    DataManager.adjustStock(product.id, delta, context)
                                },
                                onPrintBarcode = { productToPrintBarcode = product },
                                onEdit = {
                                    productToEdit = product
                                    showProductForm = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Full-screen / Sheet Product Form Dialog (Add / Edit)
    if (showProductForm) {
        ProductFormSheet(
            existingProduct = productToEdit,
            onDismiss = {
                showProductForm = false
                productToEdit = null
            },
            onSave = { updated ->
                if (productToEdit == null) {
                    DataManager.addProduct(updated, context)
                    Toast.makeText(context, "Produk \"${updated.name}\" berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                } else {
                    DataManager.updateProduct(updated, context)
                    Toast.makeText(context, "Produk \"${updated.name}\" berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                }
                showProductForm = false
                productToEdit = null
            },
            onDelete = if (productToEdit != null) {
                {
                    productToDelete = productToEdit
                    showProductForm = false
                }
            } else null
        )
    }

    // Delete Confirmation Dialog
    if (productToDelete != null) {
        val target = productToDelete!!
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Hapus Produk?", fontWeight = FontWeight.Bold) },
            text = { Text("Produk \"${target.name}\" akan dihapus permanen dari katalog toko.") },
            confirmButton = {
                Button(
                    onClick = {
                        DataManager.deleteProduct(target, context)
                        productToDelete = null
                        Toast.makeText(context, "Produk dihapus", Toast.LENGTH_SHORT).show()
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

    // Print Barcode Dialog (55x30 mm)
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
    onEdit: () -> Unit
) {
    val bitmap = remember(product.imageBase64) { ImageHelper.decodeBase64ToBitmap(product.imageBase64) }

    LiquidGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Gambar Produk / Category Icon Thumbnail (56dp)
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp)),
                color = Color(0x2EFFFFFF)
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
                                    listOf(Color(0x332563EB), Color(0x228B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = productCategoryIcon(product.category),
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 2. Info Detail (Nama, Kategori, SKU, Harga, Laba)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Order.formatRupiah(product.price),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    if (product.costPrice > 0 && product.profitNominal > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(Laba ${Order.formatRupiah(product.profitNominal)})",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Kolom Aksi Kanan: Badge Stok + Tombol Cetak Barcode & Stepper Rapi
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Stock Badge Pill
                val stockColor = when {
                    product.isOutOfStock -> Color(0xFFEF4444)
                    product.isLowStock -> Color(0xFFF59E0B)
                    else -> Color(0xFF10B981)
                }
                Surface(
                    color = stockColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, stockColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "Stok: ${product.stock} ${product.unit}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = stockColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Row Aksi: Tombol Cetak Barcode + Stepper Rapi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tombol Cetak Barcode Kassen MT200A
                    FilledIconButton(
                        onClick = onPrintBarcode,
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0x3310B981))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Cetak Barcode",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Stepper Rapi dengan Angka Stok Terlihat Jelas (Desain Kapsul Elegan)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22FFFFFF))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable { onAdjustStock(-1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Kurang Stok",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Text(
                            text = "${product.stock}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp)
                        )

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7))
                                .clickable { onAdjustStock(1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tambah Stok",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-Featured Product Form Sheet (Tambah & Edit Produk)
 * Dilengkapi Pemilihan Foto Kamera & Galeri, Pemindaian Barcode,
 * Kalkulasi Estimasi Margin Laba, Satuan, dan SKU Otomatis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductFormSheet(
    existingProduct: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isEditMode = existingProduct != null

    var name by remember { mutableStateOf(existingProduct?.name ?: "") }
    var priceText by remember { mutableStateOf(existingProduct?.price?.let { if (it > 0) it.toLong().toString() else "" } ?: "") }
    var costPriceText by remember { mutableStateOf(existingProduct?.costPrice?.let { if (it > 0) it.toLong().toString() else "" } ?: "") }
    var stockText by remember { mutableStateOf(existingProduct?.stock?.toString() ?: "10") }
    var minStockText by remember { mutableStateOf(existingProduct?.minStockAlert?.toString() ?: "5") }
    var sku by remember { mutableStateOf(existingProduct?.sku?.ifEmpty { BarcodeHelper.generateSku() } ?: BarcodeHelper.generateSku()) }
    var barcode by remember { mutableStateOf(existingProduct?.barcode?.ifEmpty { BarcodeHelper.generateEan12() } ?: BarcodeHelper.generateEan12()) }
    var selectedCategory by remember { mutableStateOf(existingProduct?.category ?: "Makanan") }
    var customCategory by remember { mutableStateOf("") }
    var showCustomCategoryInput by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(existingProduct?.unit ?: "pcs") }
    var notes by remember { mutableStateOf(existingProduct?.notes ?: "") }

    // Foto State
    var selectedBitmap by remember {
        mutableStateOf<Bitmap?>(existingProduct?.imageBase64?.let { ImageHelper.decodeBase64ToBitmap(it) })
    }
    var imageBase64String by remember { mutableStateOf(existingProduct?.imageBase64) }

    // Activity Launchers untuk Foto Kamera & Galeri
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            selectedBitmap = bmp
            imageBase64String = ImageHelper.bitmapToBase64(bmp, maxDimension = 512, quality = 82)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageHelper.uriToBitmap(context, uri, maxDimension = 512)
            if (bmp != null) {
                selectedBitmap = bmp
                imageBase64String = ImageHelper.bitmapToBase64(bmp, maxDimension = 512, quality = 82)
            }
        }
    }

    // Perhitungan Laba & Margin
    val sellPrice = priceText.toDoubleOrNull() ?: 0.0
    val costPrice = costPriceText.toDoubleOrNull() ?: 0.0
    val profitNominal = maxOf(0.0, sellPrice - costPrice)
    val marginPercentage = if (sellPrice > 0) (profitNominal / sellPrice) * 100.0 else 0.0

    val availableUnits = listOf("pcs", "cup", "porsi", "box", "botol", "kg", "pack", "lembar", "paket")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F172A)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                TopAppBar(
                    title = {
                        Text(
                            text = if (isEditMode) "Edit Produk" else "Tambah Produk Baru",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val finalBarcode = barcode.trim().ifEmpty { BarcodeHelper.generateEan12() }
                                val finalSku = sku.trim().ifEmpty { BarcodeHelper.generateSku() }
                                val finalCategory = if (showCustomCategoryInput && customCategory.isNotBlank()) {
                                    customCategory.trim()
                                } else {
                                    selectedCategory
                                }
                                val p = Product(
                                    id = existingProduct?.id ?: java.util.UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    sku = finalSku,
                                    barcode = finalBarcode,
                                    price = sellPrice,
                                    costPrice = costPrice,
                                    stock = stockText.toIntOrNull() ?: 0,
                                    minStockAlert = minStockText.toIntOrNull() ?: 5,
                                    category = finalCategory,
                                    unit = selectedUnit.trim(),
                                    imageBase64 = imageBase64String,
                                    notes = notes.trim().ifEmpty { null }
                                )
                                onSave(p)
                            },
                            enabled = name.isNotBlank() && sellPrice > 0
                        ) {
                            Text(
                                text = "Simpan",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (name.isNotBlank() && sellPrice > 0) Color(0xFF38BDF8) else Color.Gray
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                )

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. Section: Foto Produk
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("FOTO PRODUK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Image Preview Box (90x90 dp)
                            Surface(
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                color = Color(0x24FFFFFF),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0EA5E9))
                            ) {
                                if (selectedBitmap != null) {
                                    Image(
                                        bitmap = selectedBitmap!!.asImageBitmap(),
                                        contentDescription = "Foto Produk",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddAPhoto,
                                            contentDescription = null,
                                            tint = Color(0xFF0EA5E9),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // Image Action Buttons
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { cameraLauncher.launch(null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Kamera", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Button(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3338BDF8)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Galeri", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                                    }
                                }

                                if (selectedBitmap != null) {
                                    TextButton(
                                        onClick = {
                                            selectedBitmap = null
                                            imageBase64String = null
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hapus Foto", color = Color(0xFFEF4444), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 2. Section: Informasi Dasar
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("INFORMASI DASAR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

                        // Nama Produk
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nama Produk *") },
                            placeholder = { Text("Contoh: Kopi Latte Aren") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Kategori Chips
                        Text("Kategori Produk:", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DataManager.categories.filter { it != "Semua" }) { cat ->
                                val isSel = selectedCategory == cat && !showCustomCategoryInput
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedCategory = cat
                                            showCustomCategoryInput = false
                                        },
                                    color = if (isSel) Color(0xFF2563EB) else Color(0x1EFFFFFF),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) Color(0xFF38BDF8) else Color(0x20FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            item {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showCustomCategoryInput = true },
                                    color = if (showCustomCategoryInput) Color(0xFF0284C7) else Color(0x14FFFFFF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "+ Kategori Baru",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (showCustomCategoryInput) {
                            OutlinedTextField(
                                value = customCategory,
                                onValueChange = { customCategory = it },
                                label = { Text("Nama Kategori Baru") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Satuan
                        Text("Satuan Produk:", fontSize = 13.sp, color = Color(0xFFCBD5E1))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableUnits) { u ->
                                val isSel = selectedUnit.equals(u, ignoreCase = true)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { selectedUnit = u },
                                    color = if (isSel) Color(0xFF0D9488) else Color(0x1EFFFFFF),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSel) Color(0xFF2DD4BF) else Color(0x20FFFFFF)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = u,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // SKU & Barcode Inputs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = sku,
                                onValueChange = { sku = it },
                                label = { Text("SKU / Kode") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = { sku = BarcodeHelper.generateSku() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Generate SKU", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            try {
                                                val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
                                                    .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS)
                                                    .enableAutoZoom()
                                                    .build()
                                                val scanner = com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(context, options)
                                                scanner.startScan()
                                                    .addOnSuccessListener { barcodeObj ->
                                                        val code = barcodeObj.rawValue?.trim() ?: ""
                                                        if (code.isNotEmpty()) barcode = code
                                                    }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Kamera scanner: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        }
                                        IconButton(onClick = { barcode = BarcodeHelper.generateEan12() }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "Generate EAN12", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 3. Section: Harga & Margin Keuntungan
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("HARGA & MARGIN KEUNTUNGAN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Harga Jual (Rp) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = costPriceText,
                                onValueChange = { costPriceText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Harga Modal (HPP)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Live Margin Calculation Card
                        if (sellPrice > 0) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp)),
                                color = Color(0x1A10B981),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3310B981))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Estimasi Keuntungan:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Text(
                                            text = "${Order.formatRupiah(profitNominal)} / $selectedUnit",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981)
                                        )
                                    }

                                    Surface(
                                        color = if (marginPercentage >= 20.0) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = String.format(java.util.Locale.US, "Margin: %.1f%%", marginPercentage),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Section: Inventaris & Stok
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("MANAJEMEN INVENTARIS & STOK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Jumlah Stok") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = minStockText,
                                onValueChange = { minStockText = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Batas Menipis") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Quick Stepper Buttons for Stock
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(-5, -1, 1, 5, 10).forEach { delta ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val current = stockText.toIntOrNull() ?: 0
                                            stockText = maxOf(0, current + delta).toString()
                                        },
                                    color = if (delta > 0) Color(0x252563EB) else Color(0x1AEF4444),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (delta > 0) "+$delta" else "$delta",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (delta > 0) Color(0xFF38BDF8) else Color(0xFFEF4444),
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // 5. Section: Catatan Produk
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("CATATAN TAMBAHAN (OPSIONAL)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = { Text("Keterangan produk, rasa, ukuran, atau lokasi rak...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 6. Tombol Simpan Besar di Bawah
                    Button(
                        onClick = {
                            val finalBarcode = barcode.trim().ifEmpty { BarcodeHelper.generateEan12() }
                            val finalSku = sku.trim().ifEmpty { BarcodeHelper.generateSku() }
                            val finalCategory = if (showCustomCategoryInput && customCategory.isNotBlank()) {
                                customCategory.trim()
                            } else {
                                selectedCategory
                            }
                            val p = Product(
                                id = existingProduct?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim(),
                                sku = finalSku,
                                barcode = finalBarcode,
                                price = sellPrice,
                                costPrice = costPrice,
                                stock = stockText.toIntOrNull() ?: 0,
                                minStockAlert = minStockText.toIntOrNull() ?: 5,
                                category = finalCategory,
                                unit = selectedUnit.trim(),
                                imageBase64 = imageBase64String,
                                notes = notes.trim().ifEmpty { null }
                            )
                            onSave(p)
                        },
                        enabled = name.isNotBlank() && sellPrice > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditMode) "Perbarui Produk" else "Simpan Produk ke Katalog",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }

                    // 7. Tombol Hapus Produk (Hanya Saat Edit Mode)
                    if (isEditMode && onDelete != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66EF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hapus Produk Ini",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
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
