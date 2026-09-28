package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.KalensariDatabase
import com.example.data.model.CartItem
import com.example.data.model.OrderHistory
import com.example.data.model.Product
import com.example.data.repository.StoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoreViewModel(application: Application) : AndroidViewModel(application) {

    private val db = KalensariDatabase.getDatabase(application)
    val repository = StoreRepository(db.cartDao(), db.orderDao())

    val allProducts: StateFlow<List<Product>> = repository.products
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    val cartItems: StateFlow<List<CartItem>> = repository.cartItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val orderHistory: StateFlow<List<OrderHistory>> = repository.orderHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Semua")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedBrand = MutableStateFlow("Semua")
    val selectedBrand: StateFlow<String> = _selectedBrand.asStateFlow()

    // Filtered Products
    val filteredProducts: StateFlow<List<Product>> = combine(
        allProducts,
        _searchQuery,
        _selectedCategory,
        _selectedBrand
    ) { products, query, cat, brand ->
        products.filter { p ->
            val matchQuery = query.isBlank() ||
                    p.name.contains(query, ignoreCase = true) ||
                    p.brand.contains(query, ignoreCase = true) ||
                    p.description.contains(query, ignoreCase = true)
            val matchCategory = cat == "Semua" || p.category.equals(cat, ignoreCase = true)
            val matchBrand = brand == "Semua" || p.brand.equals(brand, ignoreCase = true)

            matchQuery && matchCategory && matchBrand
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cart calculations
    val cartCount: StateFlow<Int> = cartItems.combine(cartItems) { items, _ ->
        items.sumOf { it.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cartSubtotal: StateFlow<Long> = cartItems.combine(cartItems) { items, _ ->
        items.sumOf { it.subtotal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    // Checkout form state
    private val _customerName = MutableStateFlow("")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _deliveryMethod = MutableStateFlow("Delivery") // "Delivery" or "Ambil di toko"
    val deliveryMethod: StateFlow<String> = _deliveryMethod.asStateFlow()

    private val _addressDusun = MutableStateFlow("")
    val addressDusun: StateFlow<String> = _addressDusun.asStateFlow()

    private val _addressRtRw = MutableStateFlow("")
    val addressRtRw: StateFlow<String> = _addressRtRw.asStateFlow()

    private val _addressDesa = MutableStateFlow("Kalensari")
    val addressDesa: StateFlow<String> = _addressDesa.asStateFlow()

    private val _addressKecamatan = MutableStateFlow("Compreng")
    val addressKecamatan: StateFlow<String> = _addressKecamatan.asStateFlow()

    private val _orderNotes = MutableStateFlow("")
    val orderNotes: StateFlow<String> = _orderNotes.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    val deliveryFee: Long
        get() = if (_deliveryMethod.value == "Delivery") 5000L else 0L

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSelectedBrand(brand: String) {
        _selectedBrand.value = brand
    }

    fun setCustomerName(name: String) {
        _customerName.value = name
    }

    fun setCustomerPhone(phone: String) {
        _customerPhone.value = phone
    }

    fun setDeliveryMethod(method: String) {
        _deliveryMethod.value = method
    }

    fun setAddressDusun(dusun: String) {
        _addressDusun.value = dusun
    }

    fun setAddressRtRw(rtrw: String) {
        _addressRtRw.value = rtrw
    }

    fun setAddressDesa(desa: String) {
        _addressDesa.value = desa
    }

    fun setAddressKecamatan(kec: String) {
        _addressKecamatan.value = kec
    }

    fun setOrderNotes(notes: String) {
        _orderNotes.value = notes
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addToCart(product: Product, quantity: Int = 1, note: String = "") {
        viewModelScope.launch {
            repository.addToCart(product, quantity, note)
            _userMessage.value = "${product.name} dimasukkan ke keranjang"
        }
    }

    fun updateCartQuantity(productId: String, newQuantity: Int) {
        viewModelScope.launch {
            repository.updateCartQuantity(productId, newQuantity)
        }
    }

    fun removeFromCart(productId: String) {
        viewModelScope.launch {
            repository.removeFromCart(productId)
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            repository.clearCart()
        }
    }

    fun syncFromWeb() {
        viewModelScope.launch {
            val result = repository.syncLiveProducts()
            result.onSuccess { count ->
                _userMessage.value = "Katalog berhasil diperbarui ($count produk)"
            }.onFailure {
                _userMessage.value = "Katalog offline digunakan"
            }
        }
    }

    fun checkout(onOpenWhatsApp: (url: String) -> Unit) {
        val currentCart = cartItems.value
        if (currentCart.isEmpty()) {
            _userMessage.value = "Keranjang Anda masih kosong"
            return
        }

        val name = _customerName.value.trim()
        if (name.isEmpty()) {
            _userMessage.value = "Mohon isi nama pemesan"
            return
        }

        val method = _deliveryMethod.value
        val fullAddress = if (method == "Delivery") {
            val dusun = _addressDusun.value.trim()
            val rtrw = _addressRtRw.value.trim()
            val desa = _addressDesa.value.trim()
            val kec = _addressKecamatan.value.trim()
            if (dusun.isEmpty()) {
                _userMessage.value = "Mohon isi alamat dusun/nama jalan"
                return
            }
            "Dusun $dusun, RT/RW $rtrw, Desa $desa, Kec. $kec"
        } else {
            "Ambil langsung di Kalensari Store"
        }

        val subtotal = currentCart.sumOf { it.subtotal }
        val fee = if (method == "Delivery") 5000L else 0L
        val grandTotal = subtotal + fee

        val itemsSummary = currentCart.joinToString(", ") { "${it.quantity}x ${it.name}" }

        val order = OrderHistory(
            customerName = name,
            customerPhone = _customerPhone.value.trim(),
            deliveryMethod = method,
            deliveryAddress = fullAddress,
            notes = _orderNotes.value.trim(),
            itemsSummary = itemsSummary,
            itemsSubtotal = subtotal,
            deliveryFee = fee,
            grandTotal = grandTotal,
            status = "Terkirim ke WhatsApp"
        )

        viewModelScope.launch {
            repository.saveOrder(order)
            val waUrl = repository.buildWhatsAppOrderUrl(
                customerName = name,
                customerPhone = _customerPhone.value.trim(),
                deliveryMethod = method,
                address = fullAddress,
                cartList = currentCart,
                deliveryFee = fee,
                grandTotal = grandTotal,
                extraNote = _orderNotes.value.trim()
            )
            repository.clearCart()
            _userMessage.value = "Pesanan siap dikirim ke WhatsApp!"
            onOpenWhatsApp(waUrl)
        }
    }

    fun reorder(order: OrderHistory) {
        viewModelScope.launch {
            _customerName.value = order.customerName
            _customerPhone.value = order.customerPhone
            _deliveryMethod.value = order.deliveryMethod
            _userMessage.value = "Detail pemesan dimuat ulang untuk pesanan baru"
        }
    }

    fun deleteOrderHistory(orderId: Long) {
        viewModelScope.launch {
            repository.deleteOrder(orderId)
        }
    }
}
