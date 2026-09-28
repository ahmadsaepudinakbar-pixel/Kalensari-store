package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.OrderHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Query("SELECT * FROM order_history ORDER BY orderDate DESC")
    fun getAllOrders(): Flow<List<OrderHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderHistory): Long

    @Query("DELETE FROM order_history WHERE id = :orderId")
    suspend fun deleteOrderById(orderId: Long)
}
