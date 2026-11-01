package com.lklock17.omnipos.services

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lklock17.omnipos.models.*
import java.util.Calendar

object DataManager {
    private val gson = Gson()
    private const val PREFS_NAME = "omnipos_data_prefs"
    private const val KEY_PRODUCTS = "products_json"
    private const val KEY_ORDERS = "orders_json"
    private const val KEY_RECIPIENTS = "recipients_json"
    private const val KEY_CONFIG = "config_json"

    val products = mutableStateListOf<Product>()
    val orders = mutableStateListOf<Order>()
    val savedRecipients = mutableStateListOf<SavedRecipient>()
    val categories = mutableStateListOf(
        "Semua", "Makanan", "Minuman", "Snack", "Pakaian", "Elektronik", "Aksesoris", "Lainnya"
    )
    var storeConfig = mutableStateOf(StoreConfig())

    private var isLoaded = false

    fun loadData(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Config
        val configJson = prefs.getString(KEY_CONFIG, null)
        if (!configJson.isNullOrEmpty()) {
            try {
                storeConfig.value = gson.fromJson(configJson, StoreConfig::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Products
        val productsJson = prefs.getString(KEY_PRODUCTS, null)
        if (!productsJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Product>>() {}.type
                val loaded: List<Product> = gson.fromJson(productsJson, type)
                products.clear()
                products.addAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (products.isEmpty()) {
            createSampleProducts(context)
        }

        // Orders
        val ordersJson = prefs.getString(KEY_ORDERS, null)
        if (!ordersJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<Order>>() {}.type
                val loaded: List<Order> = gson.fromJson(ordersJson, type)
                orders.clear()
                orders.addAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Recipients
        val recipientsJson = prefs.getString(KEY_RECIPIENTS, null)
        if (!recipientsJson.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<SavedRecipient>>() {}.type
                val loaded: List<SavedRecipient> = gson.fromJson(recipientsJson, type)
                savedRecipients.clear()
                savedRecipients.addAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (savedRecipients.isEmpty()) {
            createSampleRecipients(context)
        }

        // Populate Categories
        products.forEach {
            if (!categories.contains(it.category) && it.category != "Semua") {
                categories.add(it.category)
            }
        }

        isLoaded = true
    }

    fun saveAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putString(KEY_PRODUCTS, gson.toJson(products))
        prefs.putString(KEY_ORDERS, gson.toJson(orders))
        prefs.putString(KEY_RECIPIENTS, gson.toJson(savedRecipients))
        prefs.putString(KEY_CONFIG, gson.toJson(storeConfig.value))
        prefs.apply()
    }

    // Product CRUD
    fun addProduct(product: Product, context: Context) {
        products.add(0, product)
        if (!categories.contains(product.category) && product.category != "Semua") {
            categories.add(product.category)
        }
        saveAll(context)
    }

    fun updateProduct(product: Product, context: Context) {
        val index = products.indexOfFirst { it.id == product.id }
        if (index != -1) {
            products[index] = product
            if (!categories.contains(product.category) && product.category != "Semua") {
                categories.add(product.category)
            }
            saveAll(context)
        }
    }

    fun deleteProduct(product: Product, context: Context) {
        products.removeAll { it.id == product.id }
        saveAll(context)
    }

    fun adjustStock(productId: String, delta: Int, context: Context) {
        val index = products.indexOfFirst { it.id == productId }
        if (index != -1) {
            val p = products[index]
            p.stock = maxOf(0, p.stock + delta)
            products[index] = p
            saveAll(context)
        }
    }

    // Order Processing
    fun processOrder(order: Order, context: Context) {
        order.items.forEach { item ->
            adjustStock(item.product.id, -item.quantity, context)
        }
        orders.add(0, order)
        saveAll(context)
    }

    // Recipient CRUD
    fun saveOrUpdateRecipient(recipient: SavedRecipient, context: Context) {
        val index = savedRecipients.indexOfFirst { 
            it.id == recipient.id || (it.name.equals(recipient.name, ignoreCase = true) && it.phone == recipient.phone) 
        }
        recipient.lastUsed = System.currentTimeMillis()
        if (index != -1) {
            savedRecipients[index] = recipient
        } else {
            savedRecipients.add(0, recipient)
        }
        saveAll(context)
    }

    fun deleteRecipient(recipient: SavedRecipient, context: Context) {
        savedRecipients.removeAll { it.id == recipient.id }
        saveAll(context)
    }

    // Analytics
    fun getTodayOrders(): List<Order> {
        val now = Calendar.getInstance()
        val orderCal = Calendar.getInstance()
        return orders.filter {
            orderCal.timeInMillis = it.date
            now.get(Calendar.YEAR) == orderCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == orderCal.get(Calendar.DAY_OF_YEAR)
        }
    }

    fun getTodaySales(): Double = getTodayOrders().sumOf { it.total }

    fun getTodayProfit(): Double {
        return getTodayOrders().sumOf { order ->
            order.items.sumOf { item ->
                val margin = (item.product.price - item.product.costPrice) * item.quantity
                maxOf(0.0, margin)
            }
        }
    }

    private fun createSampleProducts(context: Context) {
        products.addAll(listOf(
            Product(name = "Kopi Latte Aren", sku = "KOP-001", barcode = "8991001001", price = 24000.0, costPrice = 10000.0, stock = 25, category = "Minuman", unit = "cup"),
            Product(name = "Matcha Espresso", sku = "KOP-002", barcode = "8991001002", price = 28000.0, costPrice = 12000.0, stock = 18, category = "Minuman", unit = "cup"),
            Product(name = "Croissant Cokelat", sku = "SNK-001", barcode = "8992001001", price = 22000.0, costPrice = 9000.0, stock = 12, category = "Makanan", unit = "pcs"),
            Product(name = "Nasi Goreng Spesial", sku = "FOD-001", barcode = "8993001001", price = 35000.0, costPrice = 15000.0, stock = 30, category = "Makanan", unit = "porsi"),
            Product(name = "Kaos Polo Katun Premium", sku = "APL-001", barcode = "8994001001", price = 125000.0, costPrice = 65000.0, stock = 8, category = "Pakaian", unit = "pcs"),
            Product(name = "Kabel Data Type-C 65W", sku = "ELC-001", barcode = "8995001001", price = 45000.0, costPrice = 18000.0, stock = 4, category = "Elektronik", unit = "pcs")
        ))
        saveAll(context)
    }

    private fun createSampleRecipients(context: Context) {
        savedRecipients.addAll(listOf(
            SavedRecipient(name = "Budi Pratama", phone = "0812-9876-5432", address = "Jl. Merdeka No. 45, RT 02/05, Gambir, Jakarta Pusat", postalCode = "10110", defaultCourier = "J&T Express", notes = "Pagar hitam depan pos"),
            SavedRecipient(name = "Siti Rahmawati", phone = "0856-1122-3344", address = "Komplek Graha Indah Blok C3 No. 12, Sukajadi, Bandung", postalCode = "40161", defaultCourier = "SiCepat", notes = "Titip satpam jika tidak ada orang"),
            SavedRecipient(name = "Andi Saputra", phone = "0878-5544-3322", address = "Ruko Mega Boulevard No. 8, Wonokromo, Surabaya", postalCode = "60241", defaultCourier = "JNE Express", notes = "Jam kerja 09.00 - 17.00")
        ))
        saveAll(context)
    }
}

