package com.example.data.repository

import com.example.data.local.CartDao
import com.example.data.local.OrderDao
import com.example.data.model.CartItem
import com.example.data.model.OrderHistory
import com.example.data.model.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class StoreRepository(
    private val cartDao: CartDao,
    private val orderDao: OrderDao
) {
    val cartItems: Flow<List<CartItem>> = cartDao.getAllCartItems()
    val orderHistory: Flow<List<OrderHistory>> = orderDao.getAllOrders()

    private val _products = MutableStateFlow<List<Product>>(getDefaultProducts())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val whatsappNumber = "6282114541041"
    val storeWebsiteUrl = "https://kalensaristore80353.store.link/"
    val storeEmail = "ahmadsaepudinakbar@gmail.com"
    val storeAddress = "Desa Kalensari, Kecamatan Compreng, Kabupaten Subang, Jawa Barat"

    suspend fun addToCart(product: Product, quantity: Int = 1, note: String = "") {
        val existing = cartDao.getCartItemById(product.id)
        if (existing != null) {
            val updated = existing.copy(
                quantity = existing.quantity + quantity,
                note = if (note.isNotBlank()) note else existing.note
            )
            cartDao.insertOrUpdate(updated)
        } else {
            val newItem = CartItem(
                productId = product.id,
                name = product.name,
                brand = product.brand,
                category = product.category,
                price = product.effectivePrice,
                quantity = quantity,
                note = note,
                imageUrl = product.imageUrl
            )
            cartDao.insertOrUpdate(newItem)
        }
    }

    suspend fun updateCartQuantity(productId: String, newQuantity: Int) {
        if (newQuantity <= 0) {
            cartDao.deleteById(productId)
        } else {
            val existing = cartDao.getCartItemById(productId)
            if (existing != null) {
                cartDao.update(existing.copy(quantity = newQuantity))
            }
        }
    }

    suspend fun updateCartItemNote(productId: String, note: String) {
        val existing = cartDao.getCartItemById(productId)
        if (existing != null) {
            cartDao.update(existing.copy(note = note))
        }
    }

    suspend fun removeFromCart(productId: String) {
        cartDao.deleteById(productId)
    }

    suspend fun clearCart() {
        cartDao.clearCart()
    }

    suspend fun saveOrder(order: OrderHistory): Long {
        return orderDao.insertOrder(order)
    }

    suspend fun deleteOrder(orderId: Long) {
        orderDao.deleteOrderById(orderId)
    }

    fun buildWhatsAppOrderUrl(
        customerName: String,
        customerPhone: String,
        deliveryMethod: String,
        address: String,
        cartList: List<CartItem>,
        deliveryFee: Long,
        grandTotal: Long,
        extraNote: String
    ): String {
        val sb = StringBuilder()
        sb.append("Halo Kalensari Store, saya mau pesan:\n\n")

        cartList.forEach { item ->
            sb.append("• ${item.quantity}x ${item.name} (${item.brand}) - Rp ${formatRupiah(item.subtotal)}\n")
            if (item.note.isNotBlank()) {
                sb.append("  (Catatan: ${item.note})\n")
            }
        }

        sb.append("\n━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Pengiriman: $deliveryMethod\n")
        if (deliveryMethod == "Delivery" && deliveryFee > 0) {
            sb.append("Ongkir: Rp ${formatRupiah(deliveryFee)}\n")
        }
        sb.append("Total Belanja: Rp ${formatRupiah(grandTotal)}\n")
        sb.append("Pembayaran: Cash / COD (Bayar di Tempat)\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("Detail Pemesan:\n")
        sb.append("Nama: $customerName\n")
        sb.append("No. HP: $customerPhone\n")
        if (address.isNotBlank()) {
            sb.append("Alamat Pengiriman: $address\n")
        }
        if (extraNote.isNotBlank()) {
            sb.append("Catatan Tambahan: $extraNote\n")
        }
        sb.append("\nTerima kasih Kalensari Store!")

        val encodedText = try {
            URLEncoder.encode(sb.toString(), "UTF-8")
        } catch (e: Exception) {
            sb.toString().replace(" ", "%20")
        }

        return "https://wa.me/$whatsappNumber?text=$encodedText"
    }

    suspend fun syncLiveProducts(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            _isSyncing.value = true
            val request = Request.Builder()
                .url(storeWebsiteUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) KalensariStoreApp")
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

            val parsedProducts = parseProductsFromHtml(body)
            if (parsedProducts.isNotEmpty()) {
                _products.value = parsedProducts
                Result.success(parsedProducts.size)
            } else {
                Result.success(_products.value.size)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isSyncing.value = false
        }
    }

    private fun parseProductsFromHtml(html: String): List<Product> {
        val list = mutableListOf<Product>()
        try {
            val scriptPattern = Regex("<script[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            val matches = scriptPattern.findAll(html)
            for (match in matches) {
                val scriptContent = match.groupValues[1]
                if (scriptContent.contains("pageContext") && scriptContent.contains("grouped-products")) {
                    val root = JSONObject(scriptContent)
                    val fallback = root.optJSONObject("pageContext")?.optJSONObject("fallback") ?: continue

                    var catObj: JSONObject? = null
                    val keys = fallback.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.contains("grouped-products")) {
                            catObj = fallback.optJSONObject(key)
                            break
                        }
                    }

                    if (catObj != null) {
                        val catKeys = catObj.keys()
                        while (catKeys.hasNext()) {
                            val catName = catKeys.next() // "makanan" or "minuman"
                            val cleanCategory = if (catName.equals("minuman", ignoreCase = true)) "Minuman" else "Makanan"
                            val prodArray = catObj.optJSONArray(catName) ?: continue

                            for (i in 0 until prodArray.length()) {
                                val p = prodArray.getJSONObject(i)
                                val id = p.optString("_id", "p_$i")
                                val name = p.optString("name", "Produk")
                                val variants = p.optJSONArray("variants")
                                val variant = variants?.optJSONObject(0)

                                val price = variant?.optLong("price", 0L) ?: 0L
                                val discPriceRaw = variant?.optString("discountedPrice", "") ?: ""
                                val discountedPrice = discPriceRaw.toLongOrNull()
                                val description = variant?.optString("description", "") ?: ""
                                val brand = variant?.optJSONObject("properties")?.optString("brand", "Kalensari") ?: "Kalensari"
                                val images = variant?.optJSONArray("images")
                                val imgUrl = images?.optString(0, "") ?: ""

                                list.add(
                                    Product(
                                        id = id,
                                        name = name,
                                        category = cleanCategory,
                                        brand = brand,
                                        price = price,
                                        discountedPrice = discountedPrice,
                                        description = description,
                                        imageUrl = imgUrl,
                                        isAvailable = true
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Keep existing products if parse error
        }
        return if (list.isNotEmpty()) list else getDefaultProducts()
    }

    companion object {
        fun formatRupiah(amount: Long): String {
            return String.format("%,d", amount).replace(',', '.')
        }

        fun getDefaultProducts(): List<Product> = listOf(
            // === MAKANAN ===
            Product(
                id = "6aba2093f206a5ffad4462c4",
                name = "Lotek Bongko",
                category = "Makanan",
                brand = "Teh Ida",
                price = 12000,
                discountedPrice = 8000,
                description = "1 porsi lotek khas segar dengan bumbu kacang gurih mantap",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/o-fvve-chatgpt%20image%20sep%2028%2C%202026%2C%2005_14_09%20am.png?versionId=sSlnWC5X3v6SfdE8SJ7kfFpkAAtYEG66"
            ),
            Product(
                id = "6aba2093f206a5ffad4462c6",
                name = "Bakso Sapi Biasa",
                category = "Makanan",
                brand = "Zyan Bakso",
                price = 10000,
                discountedPrice = null,
                description = "1 porsi bakso sapi kuah gurih segar dengan mie dan bihun",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/pf0do4-chatgpt%20image%20sep%2028%2C%202026%2C%2006_23_05%20am.png?versionId=LvDf81yvhC2OIgUPloRKlaBbRFi.9BuH"
            ),
            Product(
                id = "6aba2093f206a5ffad4462c8",
                name = "Mie Ayam Pedas",
                category = "Makanan",
                brand = "H. Diman, Mang Edo, Mang Tardug",
                price = 10000,
                discountedPrice = null,
                description = "1 porsi mie kenyal dengan topping ayam lezat bumbu pedas mantap",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/0qg0at-chatgpt%20image%20sep%2028%2C%202026%2C%2002_03_31%20pm.png?versionId=EH1aAjwRvQd3dMY93ItbAgINe5lzjnTw"
            ),
            Product(
                id = "6aba2093f206a5ffad4462ca",
                name = "Mie Ayam Biasa",
                category = "Makanan",
                brand = "H. Diman, Mang Edo, Mang Tardug",
                price = 10000,
                discountedPrice = null,
                description = "1 porsi mie ayam original gurih lezat dengan bumbu rempah pilihan",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/m5q9je-chatgpt%20image%20sep%2028%2C%202026%2C%2002_02_42%20pm.png?versionId=Lci_mH9SOmBpD2nCjBV_Z_o_XXpxpW8v"
            ),
            Product(
                id = "6aba2093f206a5ffad4462cc",
                name = "Bakso Tulang",
                category = "Makanan",
                brand = "Zyan Bakso",
                price = 25000,
                discountedPrice = 18000,
                description = "1 porsi bakso dengan tulang sumsum iga mantap gurih nikmat",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/agx1iw-chatgpt%20image%20sep%2028%2C%202026%2C%2006_29_33%20am.png?versionId=1n1pJ2ilQgM4jrhR5X_0LEG9mB.lTmg8"
            ),
            Product(
                id = "6aba2093f206a5ffad4462ce",
                name = "Bakso Telur",
                category = "Makanan",
                brand = "Zyan Bakso",
                price = 12000,
                discountedPrice = 10000,
                description = "1 porsi bakso isi telur utuh dengan kuah kaldu segar",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/8a69b3-chatgpt%20image%20sep%2028%2C%202026%2C%2006_34_03%20am.png?versionId=m_nc2BhsK7d4LdKAPdxnMAiRHAV.Q2qB"
            ),
            Product(
                id = "6aba2093f206a5ffad4462d0",
                name = "Bakso Urat",
                category = "Makanan",
                brand = "Zyan Bakso",
                price = 18000,
                discountedPrice = 15000,
                description = "1 porsi bakso urat berdaging kenyal kriuk lezat",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/obbajp-chatgpt%20image%20sep%2028%2C%202026%2C%2006_36_33%20am.png?versionId=h_Evc0EcQtdz4PFbFk8aZey0jgRRXZ.p"
            ),
            Product(
                id = "6aba2093f206a5ffad4462d2",
                name = "Nasi Kebuli",
                category = "Makanan",
                brand = "Teh iyoh",
                price = 25000,
                discountedPrice = 20000,
                description = "1 porsi nasi kebuli khas rempah wangi gurih komplit",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/u4fafx-chatgpt%20image%20sep%2028%2C%202026%2C%2002_14_04%20pm.png?versionId=DHZD_c9LScH15.An7XpzcTJMrjDf0AJk"
            ),
            Product(
                id = "6aba2093f206a5ffad4462d4",
                name = "Nasi Goreng",
                category = "Makanan",
                brand = "Kang Diki Sueb",
                price = 13000,
                discountedPrice = null,
                description = "1 porsi nasi goreng lezat ala Kalensari dengan bumbu spesial",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/h38vn8-chatgpt%20image%20sep%2028%2C%202026%2C%2002_11_47%20pm.png?versionId=Q1UfoJozDqlb8kaNfjJqp6nKv74i_B3F"
            ),
            Product(
                id = "6aba2093f206a5ffad4462d6",
                name = "Pecel Lele",
                category = "Makanan",
                brand = "Mang Tardug",
                price = 15000,
                discountedPrice = null,
                description = "Lele goreng renyah kriuk dengan sambal tomat terasi segar lalapan",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/ota8dn-chatgpt%20image%20sep%2028%2C%202026%2C%2002_25_58%20pm.png?versionId=JUVeTbSTZiWtp5heJSYA9RlF7OmxzgOE"
            ),
            Product(
                id = "6aba2093f206a5ffad4462d8",
                name = "Pecel Lele + Nasi",
                category = "Makanan",
                brand = "Mang Tardug",
                price = 20000,
                discountedPrice = null,
                description = "Paket komplit pecel lele goreng garing + nasi putih pulen hangat",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/ota8dn-chatgpt%20image%20sep%2028%2C%202026%2C%2002_25_58%20pm.png?versionId=JUVeTbSTZiWtp5heJSYA9RlF7OmxzgOE"
            ),
            Product(
                id = "6aba2093f206a5ffad4462da",
                name = "Pecel Ayam",
                category = "Makanan",
                brand = "Mang Tardug",
                price = 20000,
                discountedPrice = null,
                description = "Ayam goreng bumbu ungkep gurih meresap dengan sambal pedas nikmat",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/s1j6xi-chatgpt%20image%20sep%2028%2C%202026%2C%2002_24_40%20pm.png?versionId=daNVkTOKSqZw_EU5ERJdtgZb57aN5VWx"
            ),

            // === MINUMAN ===
            Product(
                id = "6aba2093f206a5ffad4462dc",
                name = "Jus Alpukat",
                category = "Minuman",
                brand = "Teh Liya",
                price = 10000,
                discountedPrice = null,
                description = "1 cup besar jus alpukat kental segar dengan sirup cokelat manis",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/3x8j0a-chatgpt%20image%20sep%2028%2C%202026%2C%2006_52_41%20am.png?versionId=eKLzC7y3fgCWrkTSAdcaLHEyEYAdZMsh"
            ),
            Product(
                id = "6aba2093f206a5ffad4462de",
                name = "Jus Buah Naga",
                category = "Minuman",
                brand = "Teh Liya",
                price = 10000,
                discountedPrice = null,
                description = "1 cup besar jus buah naga merah segar kaya vitamin dan menyegarkan",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/p8vus5-chatgpt%20image%20sep%2028%2C%202026%2C%2007_22_02%20am.png?versionId=e6H7dwvYmMOZrI86QKN98dyVxHcc8V0M"
            ),
            Product(
                id = "6aba2093f206a5ffad4462e0",
                name = "Jus Tomat",
                category = "Minuman",
                brand = "Teh Liya",
                price = 10000,
                discountedPrice = null,
                description = "1 cup besar jus tomat segar pilihan penambah stamina",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/o4d1bt-chatgpt%20image%20sep%2028%2C%202026%2C%2007_20_47%20am.png?versionId=IbXRZdg2vp6bCuXJPch5YT7FgQZXXcRM"
            ),
            Product(
                id = "6aba2093f206a5ffad4462e2",
                name = "Jus Mangga",
                category = "Minuman",
                brand = "Teh Liya",
                price = 10000,
                discountedPrice = null,
                description = "1 cup besar jus mangga manis harum segar pelepas dahaga",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/lqk2sp-chatgpt%20image%20sep%2028%2C%202026%2C%2007_22_59%20am.png?versionId=FADsI7qbgepPpQt7XVGl901Q_3cKHFQW"
            ),
            Product(
                id = "6aba2093f206a5ffad4462e4",
                name = "Es Teh Manis",
                category = "Minuman",
                brand = "Tea DESA",
                price = 3000,
                discountedPrice = null,
                description = "1 cup besar es teh melati wangi dingin segar khas pedesaan",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/abqncl-hops-3260267377.webp?versionId=P4DG3eGis6QyEzh9ZFQm3CBF8p9DEJwx"
            ),
            Product(
                id = "6aba2093f206a5ffad4462e6",
                name = "Es Teh Matcha Late",
                category = "Minuman",
                brand = "Tea DESA",
                price = 6000,
                discountedPrice = null,
                description = "1 cup besar rasa Green Tea Matcha creamy nikmat menyegarkan",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/c9c6rx-images%20%281%29.jpg?versionId=K0P2vQjt8SpfWctljxkmCkUO_8AfkYf2"
            ),
            Product(
                id = "6aba2093f206a5ffad4462e8",
                name = "Es Teh Matcha Premium",
                category = "Minuman",
                brand = "Tea DESA",
                price = 15000,
                discountedPrice = 12000,
                description = "1 cup besar matcha premium kental kaya rasa dengan susu spesial",
                imageUrl = "https://cdn.store.link/products/kalensaristore80353/bkv3a5-images.jpg?versionId=jKTeHTYDtwRD2qs6CWqYs9ZM2EBZ9emC"
            )
        )
    }
}
