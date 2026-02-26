package com.returnguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PurchaseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ReturnGuardDatabase : RoomDatabase() {

    abstract fun purchaseDao(): PurchaseDao

    companion object {
        @Volatile
        private var INSTANCE: ReturnGuardDatabase? = null

        fun getInstance(context: Context): ReturnGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ReturnGuardDatabase::class.java,
                    "returnguard.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
