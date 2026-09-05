package com.mss.thebigcalendar.worker

import android.content.Context
import android.util.Log

import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mss.thebigcalendar.service.RolloverManager

class RolloverWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "RolloverWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "RolloverWorker starting.")
            val count = RolloverManager.performRollover(applicationContext)
            Log.d(TAG, "RolloverWorker finished. Rolled over $count activities.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in RolloverWorker", e)
            Result.failure()
        }
    }
}
