package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey
    val productId: String,
    val name: String,
    val brand: String,
    val category: String,
    val price: Long,
    val quantity: Int,
    val note: String = "",
    val imageUrl: String = ""
) {
    val subtotal: Long
        get() = price * quantity
}
