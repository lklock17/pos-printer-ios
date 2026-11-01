package com.lklock17.omnipos.ui.screens

import android.content.Context
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.models.SavedRecipient
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.ui.components.LiquidGlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookScreen(
    onSelectRecipient: ((SavedRecipient) -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var recipientToEdit by remember { mutableStateOf<SavedRecipient?>(null) }
    var recipientToDelete by remember { mutableStateOf<SavedRecipient?>(null) }

    val filteredList = remember(searchText, DataManager.savedRecipients.size) {
        if (searchText.isBlank()) {
            DataManager.savedRecipients.sortedByDescending { it.lastUsed }
        } else {
            DataManager.savedRecipients.filter {
                it.name.contains(searchText, ignoreCase = true) ||
                it.phone.contains(searchText, ignoreCase = true) ||
                it.address.contains(searchText, ignoreCase = true) ||
                it.postalCode.contains(searchText, ignoreCase = true) ||
                it.defaultCourier.contains(searchText, ignoreCase = true)
            }.sortedByDescending { it.lastUsed }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buku Alamat Penerima", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Tambah", tint = Color(0xFF38BDF8))
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
                placeholder = { Text("Cari nama, telp, atau alamat...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Gray)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color(0x1AFFFFFF),
                    unfocusedContainerColor = Color(0x0FFFFFFF)
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ContactPhone,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color.White.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Belum ada alamat tersimpan",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Simpan alamat langganan agar kirim paket tinggal 1 klik.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tambah Alamat Pertama")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        RecipientCardItem(
                            recipient = item,
                            onSelect = if (onSelectRecipient != null) {
                                {
                                    item.lastUsed = System.currentTimeMillis()
                                    DataManager.saveOrUpdateRecipient(item, context)
                                    onSelectRecipient(item)
                                    onClose?.invoke()
                                }
                            } else null,
                            onEdit = { recipientToEdit = item },
                            onDelete = { recipientToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Dialog
    if (showAddDialog || recipientToEdit != null) {
        val initial = recipientToEdit
        RecipientFormDialog(
            recipient = initial,
            onDismiss = {
                showAddDialog = false
                recipientToEdit = null
            },
            onSave = { updated ->
                DataManager.saveOrUpdateRecipient(updated, context)
                showAddDialog = false
                recipientToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (recipientToDelete != null) {
        val target = recipientToDelete!!
        AlertDialog(
            onDismissRequest = { recipientToDelete = null },
            title = { Text("Hapus Alamat?") },
            text = { Text("Alamat penerima \"${target.name}\" akan dihapus dari daftar tersimpan.") },
            confirmButton = {
                TextButton(onClick = {
                    DataManager.deleteRecipient(target, context)
                    recipientToDelete = null
                }) {
                    Text("Hapus", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recipientToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun RecipientCardItem(
    recipient: SavedRecipient,
    onSelect: (() -> Unit)?,
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
                    text = recipient.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = recipient.phone,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            // Courier Pill
            Surface(
                color = Color(0x33F97316),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x80F97316))
            ) {
                Text(
                    text = recipient.defaultCourier,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF97316),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = recipient.address,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 2
        )

        if (recipient.postalCode.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Kode Pos: ${recipient.postalCode}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        if (recipient.notes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Catatan: ${recipient.notes}",
                fontSize = 12.sp,
                color = Color(0xFFFCD34D)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = Color(0x22FFFFFF))
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x33EF4444))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x333B82F6))
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Ubah",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (onSelect != null) {
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gunakan Alamat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecipientFormDialog(
    recipient: SavedRecipient?,
    onDismiss: () -> Unit,
    onSave: (SavedRecipient) -> Unit
) {
    var name by remember { mutableStateOf(recipient?.name ?: "") }
    var phone by remember { mutableStateOf(recipient?.phone ?: "") }
    var address by remember { mutableStateOf(recipient?.address ?: "") }
    var postalCode by remember { mutableStateOf(recipient?.postalCode ?: "") }
    var courier by remember { mutableStateOf(recipient?.defaultCourier ?: "J&T Express") }
    var notes by remember { mutableStateOf(recipient?.notes ?: "") }

    val couriers = listOf("J&T Express", "JNE Express", "SiCepat", "Shopee Xpress", "AnterAja", "Ninja Xpress", "POS Indonesia", "GoSend")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (recipient == null) "Tambah Alamat Baru" else "Edit Alamat Penerima", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Penerima*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("No. HP / WA*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Alamat Lengkap*") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it },
                        label = { Text("Kode Pos") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = courier,
                        onValueChange = { courier = it },
                        label = { Text("Kurir Pilihan") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan / Instruksi Pengiriman") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = SavedRecipient(
                        id = recipient?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.trim(),
                        phone = phone.trim(),
                        address = address.trim(),
                        postalCode = postalCode.trim(),
                        defaultCourier = courier,
                        notes = notes.trim(),
                        lastUsed = System.currentTimeMillis()
                    )
                    onSave(saved)
                },
                enabled = name.isNotBlank() && address.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
