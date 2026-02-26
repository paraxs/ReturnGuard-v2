package com.returnguard.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val items: List<BackupItem>,
)

@Serializable
data class BackupItem(
    val id: String,
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
