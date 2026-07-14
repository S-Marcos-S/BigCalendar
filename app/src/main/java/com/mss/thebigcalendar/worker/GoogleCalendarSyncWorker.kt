package com.mss.thebigcalendar.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.mss.thebigcalendar.service.GoogleCalendarService
import com.mss.thebigcalendar.service.ProgressiveSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleCalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "GoogleCalendarSyncWorker"
        const val WORK_NAME = "google_calendar_sync"
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Result.success()
    }
}
