package com.mss.thebigcalendar

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mss.thebigcalendar.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TheBigCalendarApplication : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize Crashlytics based on user consent
        appScope.launch {
            val settingsRepository = SettingsRepository(this@TheBigCalendarApplication)
            val isEnabled = settingsRepository.isCrashlyticsEnabled.first()
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
        }

        // Agendar o RolloverWorker
        scheduleRolloverWorker()
    }

    private fun scheduleRolloverWorker() {
        val workManager = WorkManager.getInstance(this)
        val rolloverRequest =
            androidx.work.OneTimeWorkRequestBuilder<com.mss.thebigcalendar.worker.RolloverWorker>()
                .build()

        workManager.enqueue(rolloverRequest)
    }
}
