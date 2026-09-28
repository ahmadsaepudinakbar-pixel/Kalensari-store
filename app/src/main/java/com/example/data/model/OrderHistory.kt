package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "order_history")
data class OrderHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderDate: Long = System.currentTimeMillis(),
    val customerName: String,
    val customerPhone: String,
    val deliveryMethod: String, // "Ambil di toko" or "Delivery"
    val deliveryAddress: String, // Dusun, RT/RW, Desa, Kec
    val notes: String = "",
    val itemsSummary: String, // e.g. "2x Lotek Bongko, 1x Es teh Matcha Late"
    val itemsSubtotal: Long,
    val deliveryFee: Long,
    val grandTotal: Long,
    val status: String = "Terkirim ke WhatsApp"
)
