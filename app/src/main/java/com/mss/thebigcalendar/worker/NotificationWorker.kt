package com.mss.thebigcalendar.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.service.NotificationService
import kotlinx.coroutines.flow.first

/**
 * Worker para verificar e enviar notificações pendentes
 * Funciona como backup para o AlarmManager
 */
class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotificationWorker"
        const val WORK_NAME = "notification_worker"
        
        // Objeto para sincronização de threads (compartilhado entre instâncias)
        private val notificationLock = Any()
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "🔔 NotificationWorker executando")
            
            val activityRepository = ActivityRepository(applicationContext)
            val notificationService = NotificationService(applicationContext)
            
            // Buscar atividades com notificações habilitadas
            val activities = activityRepository.activities.first()
            val currentTime = System.currentTimeMillis()
            
            var notificationsSent = 0
            
            activities.forEach { activity ->
                if (activity.notificationSettings.isEnabled && 
                    activity.notificationSettings.notificationType != com.mss.thebigcalendar.data.model.NotificationType.NONE) {
                    
                    // Se for recorrente, verificar se a ocorrência atual está excluída (concluída/excluída)
                    val isCurrentOccurrenceExcluded = if (activity.recurrenceRule?.isNotEmpty() == true) {
                        if (activity.recurrenceRule.startsWith("FREQ=HOURLY")) {
                            val timeString = activity.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            val instanceId = if (timeString != null) "${activity.id}_${activity.date}_$timeString" else "${activity.id}_${activity.date}"
                            activity.excludedInstances.contains(instanceId)
                        } else {
                            activity.excludedDates.contains(activity.date)
                        }
                    } else {
                        false
                    }
                    
                    if (isCurrentOccurrenceExcluded) {
                        Log.d(TAG, "🔔 NotificationWorker - Ocorrência atual da atividade recorrente ${activity.title} (${activity.id}) está excluída/concluída. Pulando.")
                        return@forEach
                    }
                    
                    // Calcular quando a notificação deveria ter sido enviada
                    val triggerTime = getTriggerTimeWithNotificationType(activity)
                    
                    // Calcular tolerância dinâmica baseada no tipo de notificação
                    val tolerance = calculateToleranceForNotification(activity)
                    val timeDiff = currentTime - triggerTime
                    
                    if (timeDiff >= 0 && timeDiff <= tolerance) {
                        Log.d(TAG, "🔔 Enviando notificação tardia para: ${activity.title} (${timeDiff/1000}s de atraso, tolerância: ${tolerance/1000}s)")
                        
                        // ✅ Verificar se já foi enviada recentemente (deduplicação)
                        // Usar o mesmo ID que o NotificationService usa
                        val activityIdForNotification = if (activity.id.contains("_")) {
                            activity.id
                        } else {
                            "${activity.id}_${activity.date}"
                        }
                        
                        // ✅ Sincronização para evitar condição de corrida
                        synchronized(notificationLock) {
                            Log.d(TAG, "🔔 Worker - Verificando deduplicação para ID: $activityIdForNotification")
                            
                            // ✅ Verificar se a atividade foi adiada recentemente
                            // Verificar tanto o ID específico quanto o ID base para atividades recorrentes
                            val baseId = if (activity.id.contains("_")) {
                                activity.id.split("_")[0]
                            } else {
                                activity.id
                            }
                            
                            val wasSnoozed = wasActivityRecentlySnoozed(activityIdForNotification) || 
                                           wasActivityRecentlySnoozed(baseId) ||
                                           wasActivityRecentlySnoozed(activity.id)
                            
                            if (wasSnoozed) {
                                Log.d(TAG, "🔔 Worker - Notificação tardia bloqueada (atividade foi adiada recentemente) para: ${activity.title}")
                            } else if (hasNotificationBeenSentRecently(activityIdForNotification)) {
                                Log.d(TAG, "🔔 Worker - Notificação tardia bloqueada (duplicata) para: ${activity.title}")
                            } else {
                                Log.d(TAG, "🔔 Worker - Notificação tardia permitida para: ${activity.title}")
                                notificationService.showNotification(activity)
                                notificationsSent++
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "🔔 NotificationWorker concluído. Notificações enviadas: $notificationsSent")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "🔔 Erro no NotificationWorker", e)
            Result.retry()
        }
    }
    
    private fun calculateNotificationTime(activity: com.mss.thebigcalendar.data.model.Activity): java.time.LocalTime {
        val startTime = activity.startTime ?: java.time.LocalTime.of(9, 0)
        
        return when (activity.notificationSettings.notificationType) {
            com.mss.thebigcalendar.data.model.NotificationType.FIFTEEN_MINUTES_BEFORE -> 
                startTime.minusMinutes(15)
            com.mss.thebigcalendar.data.model.NotificationType.THIRTY_MINUTES_BEFORE -> 
                startTime.minusMinutes(30)
            com.mss.thebigcalendar.data.model.NotificationType.ONE_HOUR_BEFORE -> 
                startTime.minusHours(1)
            com.mss.thebigcalendar.data.model.NotificationType.ONE_DAY_BEFORE -> 
                startTime // Para um dia antes, manter o mesmo horário
            else -> startTime
        }
    }
    
    private fun getTriggerTime(dateString: String, notificationTime: java.time.LocalTime): Long {
        val date = java.time.LocalDate.parse(dateString)
        val dateTime = java.time.LocalDateTime.of(date, notificationTime)
        return dateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    private fun getTriggerTimeWithNotificationType(activity: com.mss.thebigcalendar.data.model.Activity): Long {
        val activityDate = java.time.LocalDate.parse(activity.date)
        val startTime = activity.startTime ?: java.time.LocalTime.of(9, 0)
        
        val notificationDateTime = when (activity.notificationSettings.notificationType) {
            com.mss.thebigcalendar.data.model.NotificationType.FIFTEEN_MINUTES_BEFORE -> 
                java.time.LocalDateTime.of(activityDate, startTime.minusMinutes(15))
            com.mss.thebigcalendar.data.model.NotificationType.THIRTY_MINUTES_BEFORE -> 
                java.time.LocalDateTime.of(activityDate, startTime.minusMinutes(30))
            com.mss.thebigcalendar.data.model.NotificationType.ONE_HOUR_BEFORE -> 
                java.time.LocalDateTime.of(activityDate, startTime.minusHours(1))
            com.mss.thebigcalendar.data.model.NotificationType.ONE_DAY_BEFORE -> 
                java.time.LocalDateTime.of(activityDate.minusDays(1), startTime)
            else -> 
                java.time.LocalDateTime.of(activityDate, startTime)
        }
        
        return notificationDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    
    /**
     * Calcula a tolerância dinâmica para verificação de notificações tardias
     * baseada no tipo de notificação e na importância da atividade
     */
    private fun calculateToleranceForNotification(activity: com.mss.thebigcalendar.data.model.Activity): Long {
        val notificationType = activity.notificationSettings.notificationType
        val minutesBefore = notificationType.minutesBefore ?: 0
        
        return when {
            // Notificações de muito longo prazo (1 dia+): tolerância de 1 hora
            minutesBefore >= 1440 -> 3600000L // 1 hora
            
            // Notificações de longo prazo (2-12 horas): tolerância de 30 minutos
            minutesBefore >= 120 -> 1800000L // 30 minutos
            
            // Notificações de médio prazo (30min-2h): tolerância de 15 minutos
            minutesBefore >= 30 -> 900000L // 15 minutos
            
            // Notificações de curto prazo (5-15min): tolerância de 10 minutos
            minutesBefore >= 5 -> 600000L // 10 minutos
            
            // Notificações imediatas (0-5min): tolerância de 5 minutos
            else -> 300000L // 5 minutos
        }
    }
    
    /**
     * Verifica se uma notificação já foi enviada recentemente para evitar duplicatas
     */
    private fun hasNotificationBeenSentRecently(activityId: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("notification_tracking", Context.MODE_PRIVATE)
        val key = "notification_sent_$activityId"
        val lastSentTime = prefs.getLong(key, 0)
        val currentTime = System.currentTimeMillis()
        
        val timeDiff = currentTime - lastSentTime
        val wasSentRecently = timeDiff < 60000L // 1 minuto (janela mais curta)
        
        Log.d(TAG, "🔔 Worker - Verificação de deduplicação - ID: $activityId, Última vez: $lastSentTime, Agora: $currentTime, Diferença: ${timeDiff/1000}s, Janela: 60s")
        
        if (wasSentRecently) {
            Log.d(TAG, "🔔 Worker - Notificação já enviada recentemente para $activityId (${timeDiff/1000}s atrás)")
        } else {
            Log.d(TAG, "🔔 Worker - Notificação não foi enviada recentemente para $activityId (${timeDiff/1000}s atrás)")
        }
        
        return wasSentRecently
    }
    
    /**
     * Verifica se uma atividade foi adiada recentemente para evitar notificações tardias desnecessárias
     */
    private fun wasActivityRecentlySnoozed(activityId: String): Boolean {
        val prefs = applicationContext.getSharedPreferences("notification_tracking", Context.MODE_PRIVATE)
        val key = "activity_snoozed_$activityId"
        val snoozedTime = prefs.getLong(key, 0)
        val currentTime = System.currentTimeMillis()
        
        val timeDiff = currentTime - snoozedTime
        val wasRecentlySnoozed = timeDiff < 300000L // 5 minutos (janela para atividades adiadas)
        
        Log.d(TAG, "🔔 Worker - Verificação de adiamento - ID: $activityId, Adiada em: $snoozedTime, Agora: $currentTime, Diferença: ${timeDiff/1000}s, Janela: 300s")
        
        if (wasRecentlySnoozed) {
            Log.d(TAG, "🔔 Worker - Atividade foi adiada recentemente para $activityId (${timeDiff/1000}s atrás)")
        } else {
            Log.d(TAG, "🔔 Worker - Atividade não foi adiada recentemente para $activityId (${timeDiff/1000}s atrás)")
        }
        
        return wasRecentlySnoozed
    }
}
