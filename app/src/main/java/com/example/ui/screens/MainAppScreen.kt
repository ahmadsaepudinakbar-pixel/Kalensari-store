package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.StoreRepository
import com.example.ui.viewmodel.StoreViewModel

data class NavTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun MainAppScreen(
    viewModel: StoreViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val cartSubtotal by viewModel.cartSubtotal.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Handle back button on sub-screens
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    val tabs = listOf(
        NavTabItem("Katalog", Icons.Default.RestaurantMenu, "tab_catalog"),
        NavTabItem("Keranjang", Icons.Default.ShoppingCart, "tab_cart"),
        NavTabItem("Riwayat", Icons.Default.History, "tab_orders"),
        NavTabItem("Web Store", Icons.Default.Language, "tab_webstore"),
        NavTabItem("Info", Icons.Default.Info, "tab_info")
    )

    val openWhatsApp: (String) -> Unit = { url ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "WhatsApp tidak ditemukan. Membuka di browser...", Toast.LENGTH_SHORT).show()
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(webIntent)
        }
    }

    if (isTablet) {
        // Tablet / Large Screen Layout with NavigationRail
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                modifier = Modifier.testTag("nav_rail")
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationRailItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            if (index == 1 && cartCount > 0) {
                                BadgedBox(badge = { Badge { Text(cartCount.toString()) } }) {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }

            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                modifier = Modifier.weight(1f)
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenContent(
                        tabIndex = selectedTabIndex,
                        viewModel = viewModel,
                        onNavigateToHome = { selectedTabIndex = 0 },
                        onNavigateToCart = { selectedTabIndex = 1 },
                        onOpenWhatsApp = openWhatsApp
                    )
                }
            }
        }
    } else {
        // Phone Layout with Bottom NavigationBar
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (selectedTabIndex == 0 && cartCount > 0) {
                    ExtendedFloatingActionButton(
                        onClick = { selectedTabIndex = 1 },
                        icon = {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        },
                        text = {
                            Text(
                                text = "$cartCount Item • Rp ${StoreRepository.formatRupiah(cartSubtotal)}",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("floating_cart_btn")
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav")
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTabIndex == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            icon = {
                                if (index == 1 && cartCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(cartCount.toString(), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    ) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.title)
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            },
            modifier = modifier
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScreenContent(
                    tabIndex = selectedTabIndex,
                    viewModel = viewModel,
                    onNavigateToHome = { selectedTabIndex = 0 },
                    onNavigateToCart = { selectedTabIndex = 1 },
                    onOpenWhatsApp = openWhatsApp
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    tabIndex: Int,
    viewModel: StoreViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToCart: () -> Unit,
    onOpenWhatsApp: (String) -> Unit
) {
    when (tabIndex) {
        0 -> HomeScreen(
            viewModel = viewModel,
            onNavigateToCart = onNavigateToCart
        )
        1 -> CartScreen(
            viewModel = viewModel,
            onNavigateToHome = onNavigateToHome,
            onOpenWhatsApp = onOpenWhatsApp
        )
        2 -> OrdersHistoryScreen(
            viewModel = viewModel,
            onNavigateToHome = onNavigateToHome
        )
        3 -> WebStoreScreen()
        4 -> StoreInfoScreen()
    }
}
