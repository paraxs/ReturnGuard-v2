package com.returnguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: String,
    val productName: String,
    val merchant: String,
    val purchaseDateEpochDay: Long,
    val returnDays: Int,
    val warrantyMonths: Int,
    val priceCents: Long?,
    val notes: String,
    val archived: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
