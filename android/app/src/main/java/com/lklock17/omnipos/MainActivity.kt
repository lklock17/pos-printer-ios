package com.lklock17.omnipos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lklock17.omnipos.services.DataManager
import com.lklock17.omnipos.ui.screens.*
import com.lklock17.omnipos.ui.theme.OmniPOSTheme

enum class MainTab(val title: String, val icon: ImageVector) {
    CASHIER("Kasir", Icons.Default.ShoppingCart),
    PRODUCTS("Produk", Icons.Default.Inventory2),
    SHIPPING("Pengiriman", Icons.Default.LocalShipping),
    HISTORY("Riwayat", Icons.Default.ReceiptLong),
    SETTINGS("Pengaturan", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataManager.loadData(this)

        setContent {
            OmniPOSTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    var selectedTab by remember { mutableStateOf(MainTab.CASHIER) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                MainTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF38BDF8),
                            selectedTextColor = Color(0xFF38BDF8),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color(0x3338BDF8)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF0A0E1A)
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = Color(0xFF0A0E1A)
        ) {
            when (selectedTab) {
                MainTab.CASHIER -> CashierScreen()
                MainTab.PRODUCTS -> ProductScreen()
                MainTab.SHIPPING -> ShippingLabelScreen()
                MainTab.HISTORY -> OrderHistoryScreen()
                MainTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
