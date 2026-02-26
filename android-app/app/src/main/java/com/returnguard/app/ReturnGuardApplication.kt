package com.returnguard.app

import android.app.Application
import com.returnguard.app.reminder.ReminderScheduler

class ReturnGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureNotificationChannel(this)
        ReminderScheduler.ensureScheduled(this)
    }
}
