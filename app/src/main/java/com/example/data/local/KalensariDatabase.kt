package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.CartItem
import com.example.data.model.OrderHistory

@Database(
    entities = [CartItem::class, OrderHistory::class],
    version = 1,
    exportSchema = false
)
abstract class KalensariDatabase : RoomDatabase() {
    abstract fun cartDao(): CartDao
    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile
        private var INSTANCE: KalensariDatabase? = null

        fun getDatabase(context: Context): KalensariDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KalensariDatabase::class.java,
                    "kalensari_store.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
