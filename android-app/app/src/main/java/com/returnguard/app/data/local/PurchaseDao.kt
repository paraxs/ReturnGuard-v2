package com.returnguard.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {

    @Query("SELECT * FROM purchases ORDER BY archived ASC, (purchaseDateEpochDay + returnDays) ASC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases")
    suspend fun getAllOnce(): List<PurchaseEntity>

    @Upsert
    suspend fun upsert(item: PurchaseEntity)

    @Upsert
    suspend fun upsertAll(items: List<PurchaseEntity>)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM purchases")
    suspend fun clearAll()

    @Query("UPDATE purchases SET archived = :archived, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAtMillis: Long)

    @Query(
        """
        SELECT * FROM purchases
        WHERE archived = 0
          AND returnDays > 0
          AND (purchaseDateEpochDay + returnDays) BETWEEN :fromEpochDay AND :toEpochDay
        ORDER BY (purchaseDateEpochDay + returnDays) ASC
        """
    )
    suspend fun getDueItemsBetween(fromEpochDay: Long, toEpochDay: Long): List<PurchaseEntity>
}
