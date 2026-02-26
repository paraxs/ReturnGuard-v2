package com.returnguard.app.data.repository

import com.returnguard.app.data.backup.BackupFile
import com.returnguard.app.data.backup.BackupItem
import com.returnguard.app.data.local.PurchaseDao
import com.returnguard.app.data.local.PurchaseEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class PurchaseRepository(
    private val purchaseDao: PurchaseDao,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun observeAll(): Flow<List<PurchaseEntity>> = purchaseDao.observeAll()

    suspend fun add(input: NewPurchaseInput) {
        val now = System.currentTimeMillis()
        val item = PurchaseEntity(
            id = UUID.randomUUID().toString(),
            productName = input.productName.trim(),
            merchant = input.merchant.trim(),
            purchaseDateEpochDay = input.purchaseDate.toEpochDay(),
            returnDays = input.returnDays.coerceAtLeast(0),
            warrantyMonths = input.warrantyMonths.coerceAtLeast(0),
            priceCents = input.priceCents?.coerceAtLeast(0),
            notes = input.notes.trim(),
            archived = false,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        purchaseDao.upsert(item)
    }

    suspend fun delete(id: String) {
        purchaseDao.deleteById(id)
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        purchaseDao.setArchived(id = id, archived = archived, updatedAtMillis = System.currentTimeMillis())
    }

    suspend fun getDueItemsBetween(fromEpochDay: Long, toEpochDay: Long): List<PurchaseEntity> {
        return purchaseDao.getDueItemsBetween(fromEpochDay, toEpochDay)
    }

    suspend fun exportJson(): String {
        val items = purchaseDao.getAllOnce().map { it.toBackupItem() }
        val backup = BackupFile(
            exportedAtMillis = System.currentTimeMillis(),
            items = items,
        )
        return json.encodeToString(BackupFile.serializer(), backup)
    }

    suspend fun importJson(payload: String): Int {
        val backup = json.decodeFromString(BackupFile.serializer(), payload)
        val now = System.currentTimeMillis()
        val seenIds = mutableSetOf<String>()
        val normalized = backup.items.map { item ->
            var resolvedId = item.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            if (resolvedId in seenIds) {
                resolvedId = UUID.randomUUID().toString()
            }
            seenIds += resolvedId
            PurchaseEntity(
                id = resolvedId,
                productName = item.productName.ifBlank { "Unbenannt" },
                merchant = item.merchant,
                purchaseDateEpochDay = item.purchaseDateEpochDay,
                returnDays = item.returnDays.coerceAtLeast(0),
                warrantyMonths = item.warrantyMonths.coerceAtLeast(0),
                priceCents = item.priceCents?.coerceAtLeast(0),
                notes = item.notes,
                archived = item.archived,
                createdAtMillis = item.createdAtMillis.takeIf { it > 0 } ?: now,
                updatedAtMillis = now,
            )
        }
        purchaseDao.clearAll()
        purchaseDao.upsertAll(normalized)
        return normalized.size
    }

    private fun PurchaseEntity.toBackupItem(): BackupItem {
        return BackupItem(
            id = id,
            productName = productName,
            merchant = merchant,
            purchaseDateEpochDay = purchaseDateEpochDay,
            returnDays = returnDays,
            warrantyMonths = warrantyMonths,
            priceCents = priceCents,
            notes = notes,
            archived = archived,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }
}

data class NewPurchaseInput(
    val productName: String,
    val merchant: String,
    val purchaseDate: LocalDate,
    val returnDays: Int,
    val warrantyMonths: Int,
    val priceCents: Long?,
    val notes: String,
)
