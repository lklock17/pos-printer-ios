package com.lklock17.omnipos.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.Order
import com.lklock17.omnipos.models.Product
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen() {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = remember(searchText, DataManager.products.size) {
        if (searchText.isBlank()) {
            DataManager.products.toList()
        } else {
            DataManager.products.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                it.sku.contains(searchText, ignoreCase = true) ||
                it.barcode.contains(searchText)
            }
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
            // Search Bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Cari produk berdasarkan nama / SKU...", color = Color.Gray) },
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
                        onEdit = { productToEdit = product },
                        onDelete = { productToDelete = product }
                    )
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
}

@Composable
private fun ProductManagementRow(
    product: Product,
    onAdjustStock: (Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    LiquidGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Harga: ${Order.formatRupiah(product.price)} • Modal: ${Order.formatRupiah(product.costPrice)}",
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                )
                if (product.sku.isNotEmpty()) {
                    Text(
                        text = "SKU: ${product.sku} | Barcode: ${product.barcode}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = Color(0x22FFFFFF))
        Spacer(modifier = Modifier.height(8.dp))

        // Stock Adjust Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Stok: ${product.stock} ${product.unit}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (product.isLowStock) Color(0xFFEF4444) else Color(0xFF10B981)
                )
                if (product.isLowStock) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0x33EF4444),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "STOK MENIPIS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Quick - / + buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onAdjustStock(-1) },
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0x33FFFFFF))
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "-1", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = { onAdjustStock(1) },
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2563EB))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "+1", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0x33EF4444))
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
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
    var sku by remember { mutableStateOf(product?.sku ?: "") }
    var barcode by remember { mutableStateOf(product?.barcode ?: "") }
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
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = Product(
                        id = product?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        price = priceText.toDoubleOrNull() ?: 0.0,
                        costPrice = costPriceText.toDoubleOrNull() ?: 0.0,
                        stock = stockText.toIntOrNull() ?: 0,
                        sku = sku.trim(),
                        barcode = barcode.trim(),
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

