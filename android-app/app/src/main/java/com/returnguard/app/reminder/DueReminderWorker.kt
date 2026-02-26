package com.returnguard.app.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.returnguard.app.MainActivity
import com.returnguard.app.R
import com.returnguard.app.data.local.ReturnGuardDatabase
import com.returnguard.app.data.repository.PurchaseRepository
import java.time.LocalDate

class DueReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val repository by lazy {
        val db = ReturnGuardDatabase.getInstance(applicationContext)
        PurchaseRepository(db.purchaseDao())
    }

    override suspend fun doWork(): Result {
        val today = LocalDate.now().toEpochDay()
        val horizon = today + 1L

        val dueItems = repository.getDueItemsBetween(today, horizon)
        if (dueItems.isEmpty()) return Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        dueItems.forEach { item ->
            val dueEpochDay = item.purchaseDateEpochDay + item.returnDays
            val daysLeft = dueEpochDay - today
            val title = when (daysLeft.toInt()) {
                0 -> "Heute letzter Rückgabetag"
                1 -> "Morgen letzter Rückgabetag"
                else -> "Rückgabe in $daysLeft Tagen"
            }
            val content = listOf(item.productName, item.merchant)
                .filter { it.isNotBlank() }
                .joinToString(" • ")

            val pendingIntent = android.app.PendingIntent.getActivity(
                applicationContext,
                item.id.hashCode(),
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(applicationContext, ReminderConstants.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(applicationContext)
                .notify((item.id + dueEpochDay).hashCode(), notification)
        }

        return Result.success()
    }
}
