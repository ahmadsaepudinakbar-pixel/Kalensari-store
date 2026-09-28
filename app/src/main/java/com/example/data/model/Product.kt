package com.example.data.model

data class Product(
    val id: String,
    val name: String,
    val category: String, // "Makanan" or "Minuman"
    val brand: String,
    val price: Long,
    val discountedPrice: Long? = null,
    val description: String,
    val imageUrl: String,
    val isAvailable: Boolean = true
) {
    val effectivePrice: Long
        get() = discountedPrice ?: price

    val hasDiscount: Boolean
        get() = discountedPrice != null && discountedPrice < price

    val discountPercentage: Int
        get() = if (hasDiscount) {
            (((price - discountedPrice!!) * 100) / price).toInt()
        } else 0
}
