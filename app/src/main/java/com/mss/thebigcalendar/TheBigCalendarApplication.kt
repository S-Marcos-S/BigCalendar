package com.mss.thebigcalendar

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
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

        if (isMainProcess()) {
            // Initialize Crashlytics and Analytics based on user consent in main process
            appScope.launch {
                if (com.google.firebase.FirebaseApp.getApps(this@TheBigCalendarApplication).isNotEmpty()) {
                    val settingsRepository = SettingsRepository(this@TheBigCalendarApplication)
                    val isEnabled = settingsRepository.isCrashlyticsEnabled.first()
                    FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
                    FirebaseAnalytics.getInstance(this@TheBigCalendarApplication).setAnalyticsCollectionEnabled(isEnabled)
                }
            }

            // Agendar o RolloverWorker no processo principal
            scheduleRolloverWorker()
        }
    }

    private fun isMainProcess(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return packageName == getProcessName()
        }
        val pid = android.os.Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
        val runningProcesses = am?.runningAppProcesses
        if (runningProcesses != null) {
            for (processInfo in runningProcesses) {
                if (processInfo.pid == pid) {
                    return packageName == processInfo.processName
                }
            }
        }
        return true
    }

    private fun scheduleRolloverWorker() {
        val workManager = WorkManager.getInstance(this)

        // 1. OneTimeWorkRequest imediato com política REPLACE para garantir execução ao iniciar
        val immediateRequest =
            androidx.work.OneTimeWorkRequestBuilder<com.mss.thebigcalendar.worker.RolloverWorker>()
                .build()
        workManager.enqueueUniqueWork(
            "rollover_worker_immediate",
            androidx.work.ExistingWorkPolicy.REPLACE,
            immediateRequest
        )

        // 2. PeriodicWorkRequest a cada 12 horas como camada de garantia em segundo plano
        val periodicRequest =
            androidx.work.PeriodicWorkRequestBuilder<com.mss.thebigcalendar.worker.RolloverWorker>(
                12, java.util.concurrent.TimeUnit.HOURS
            ).build()
        workManager.enqueueUniquePeriodicWork(
            "rollover_worker_periodic",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        // 3. Agendar alarme exato de meia-noite via AlarmManager para virada do dia
        com.mss.thebigcalendar.service.RolloverManager.scheduleMidnightRollover(this)
    }
}
