package com.mss.thebigcalendar.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mss.thebigcalendar.data.repository.AutoBackupSettings
import com.mss.thebigcalendar.data.repository.BackupFrequency
import java.util.Calendar

class BackupScheduler(private val context: Context) {

    companion object {
        private const val TAG = "BackupScheduler"
        private const val AUTO_BACKUP_ALARM_ID = 7777
    }

    fun schedule(settings: AutoBackupSettings) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationService.ACTION_AUTO_BACKUP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AUTO_BACKUP_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel existing alarm first to prevent duplicates
        alarmManager.cancel(pendingIntent)

        if (settings.enabled) {
            val initialDelay = calculateInitialDelay(settings.hour, settings.minute)
            val triggerTime = System.currentTimeMillis() + initialDelay

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
                Log.d(TAG, "🔄 Backup automático agendado via AlarmManager para: ${java.util.Date(triggerTime)}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro ao agendar backup automático com AlarmManager", e)
            }
        } else {
            Log.d(TAG, "🔄 Backup automático desabilitado")
        }
    }

    fun scheduleNext(settings: AutoBackupSettings) {
        if (!settings.enabled) return

        val repeatInterval = when (settings.frequency) {
            BackupFrequency.DAILY -> 1
            BackupFrequency.TWO_DAYS -> 2
            BackupFrequency.WEEKLY -> 7
            BackupFrequency.MONTHLY -> 30
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationService.ACTION_AUTO_BACKUP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AUTO_BACKUP_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextRun = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, repeatInterval)
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerTime = nextRun.timeInMillis

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "🔄 Próximo backup automático agendado para: ${java.util.Date(triggerTime)}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao agendar próxima recorrência do backup automático", e)
        }
    }

    fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationService.ACTION_AUTO_BACKUP
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            AUTO_BACKUP_ALARM_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "🔄 Backup automático cancelado no AlarmManager")
    }

    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val nextRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (nextRun.before(now)) {
            nextRun.add(Calendar.DAY_OF_MONTH, 1)
        }

        return nextRun.timeInMillis - now.timeInMillis
    }
}
