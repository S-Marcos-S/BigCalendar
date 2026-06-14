package com.mss.thebigcalendar.service


import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.VisibilityLevel
import java.time.LocalDateTime
import java.time.ZoneId

class NotificationService(
    private val context: Context
) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    // SharedPreferences para controle de deduplicação
    private val prefs = context.getSharedPreferences("notification_tracking", Context.MODE_PRIVATE)
    
    // Objeto para sincronização de threads
    private val notificationLock = Any()

    companion object {
        private const val TAG = "NotificationService"
        const val CHANNEL_ID = "calendar_notifications"
        const val CHANNEL_NAME = "Lembretes do Calendário"
        const val CHANNEL_DESCRIPTION = "Notificações para atividades e tarefas do calendário"

        const val AUTO_BACKUP_CHANNEL_ID = "auto_backup_notifications"
        const val AUTO_BACKUP_CHANNEL_NAME = "Backups Automáticos"
        const val AUTO_BACKUP_CHANNEL_DESCRIPTION = "Notificações sobre backups automáticos"

        const val MANUAL_BACKUP_CHANNEL_ID = "manual_backup_notifications"
        const val MANUAL_BACKUP_CHANNEL_NAME = "Backup e Restauração Manual"
        const val MANUAL_BACKUP_CHANNEL_DESCRIPTION = "Notificações sobre backup e restauração manual"
        
        // Ações para as notificações
        const val ACTION_VIEW_ACTIVITY = "com.mss.thebigcalendar.VIEW_ACTIVITY"
        const val ACTION_SNOOZE = "com.mss.thebigcalendar.SNOOZE"
        const val ACTION_DISMISS = "com.mss.thebigcalendar.DISMISS"
        const val ACTION_AUTO_BACKUP = "com.mss.thebigcalendar.ACTION_AUTO_BACKUP"
        
        const val AUTO_BACKUP_NOTIFICATION_ID = 9999
        
        // Extras para as notificações
        const val EXTRA_ACTIVITY_ID = "activity_id"
        const val EXTRA_ACTIVITY_TITLE = "activity_title"
        const val EXTRA_ACTIVITY_DATE = "activity_date"
        const val EXTRA_ACTIVITY_TIME = "activity_time"
        const val EXTRA_VISIBILITY = "activity_visibility"
        
        // Chaves para controle de deduplicação
        private const val KEY_NOTIFICATION_SENT = "notification_sent_"
        private const val NOTIFICATION_WINDOW_MS = 60000L // 1 minuto (janela mais curta para evitar bloqueios)
    }

    init {
        createNotificationChannels()
        cleanOldNotifications() // Limpar notificações antigas
    }

    /**
     * Cria os canais de notificação (necessário para Android 8.0+)
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val calendarChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, 
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                setBypassDnd(true) // Ignorar "Não perturbe"
            }
            notificationManager.createNotificationChannel(calendarChannel)

            val backupChannel = NotificationChannel(
                AUTO_BACKUP_CHANNEL_ID,
                AUTO_BACKUP_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = AUTO_BACKUP_CHANNEL_DESCRIPTION
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(backupChannel)

            val manualBackupChannel = NotificationChannel(
                MANUAL_BACKUP_CHANNEL_ID,
                MANUAL_BACKUP_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = MANUAL_BACKUP_CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(manualBackupChannel)
        }
    }

    fun showBackupInProgressNotification() {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle("Realizando backup")
            .setContentText("Aguarde, o backup está em andamento...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setProgress(0, 0, true)
        notificationManager.notify(1, builder.build())
    }

    fun showBackupCompleteNotification(fileName: String) {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_done)
            .setContentTitle("Backup concluído")
            .setContentText("O arquivo $fileName foi salvo com sucesso.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notificationManager.notify(1, builder.build())
    }

    fun showBackupFailedNotification(error: String) {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_off)
            .setContentTitle("Falha no backup")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(1, builder.build())
    }

    fun showAutoBackupInProgressNotification() {
        val builder = NotificationCompat.Builder(context, AUTO_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_upload)
            .setContentTitle("Realizando Backup Automático")
            .setContentText("O backup agendado está sendo executado...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setProgress(0, 0, true)
        notificationManager.notify(AUTO_BACKUP_NOTIFICATION_ID, builder.build())
    }

    fun cancelAutoBackupInProgressNotification() {
        notificationManager.cancel(AUTO_BACKUP_NOTIFICATION_ID)
    }

    fun showRestoreInProgressNotification() {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_download)
            .setContentTitle("Restaurando backup")
            .setContentText("Aguarde, a restauração está em andamento...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setProgress(0, 0, true)
        notificationManager.notify(2, builder.build())
    }

    fun showRestoreCompleteNotification(fileName: String) {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_done)
            .setContentTitle("Restauração concluída")
            .setContentText("O backup do arquivo $fileName foi restaurado com sucesso.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notificationManager.notify(2, builder.build())
    }

    fun showRestoreFailedNotification(error: String) {
        val builder = NotificationCompat.Builder(context, MANUAL_BACKUP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_off)
            .setContentTitle("Falha na restauração")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        notificationManager.notify(2, builder.build())
    }

    fun showAutoBackupSuccessNotification(backupType: com.mss.thebigcalendar.data.repository.BackupType, backupPath: String) {
        val notification = NotificationCompat.Builder(context, AUTO_BACKUP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Backup Automático Concluído")
            .setContentText(context.getString(R.string.backup_success_notification, backupType.name.lowercase()))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun showAutoBackupFailedNotification(backupType: com.mss.thebigcalendar.data.repository.BackupType, errorMessage: String) {
        val notification = NotificationCompat.Builder(context, AUTO_BACKUP_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Falha no Backup Automático")
            .setContentText(context.getString(R.string.backup_failed_notification, backupType.name.lowercase(), errorMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * Agenda uma notificação para uma atividade
     */
    fun scheduleNotification(activity: Activity) {
        if (!activity.notificationSettings.isEnabled || 
            activity.notificationSettings.notificationType == com.mss.thebigcalendar.data.model.NotificationType.NONE) {
            return
        }
        
        
        // Não iniciar serviço foreground automaticamente - será iniciado apenas quando necessário
        
        // Se não há horário específico, usar início do dia (00:00)
        if (activity.startTime == null) {
        }

        val notificationTime = calculateNotificationTime(activity)
        val triggerTime = getTriggerTime(activity.date, notificationTime)
        
        
        // Cancelar notificação anterior se existir
        cancelNotification(activity.id)
        
        // ✅ Criar intent para exibir a notificação visual
        // Para atividades recorrentes, usar o ID da instância específica
        val activityIdForNotification = if (activity.id.contains("_")) {
            // Já é uma instância específica
            activity.id
        } else {
            // É uma atividade base, criar ID da instância específica
            // Para atividades HOURLY, incluir o horário no ID
            if (activity.recurrenceRule?.startsWith("FREQ=HOURLY") == true && activity.startTime != null) {
                val timeString = activity.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "${activity.id}_${activity.date}_${timeString}"
            } else {
                "${activity.id}_${activity.date}"
            }
        }
        
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_VIEW_ACTIVITY
            putExtra(EXTRA_ACTIVITY_ID, activityIdForNotification)
            putExtra(EXTRA_ACTIVITY_TITLE, activity.title)
            putExtra(EXTRA_ACTIVITY_DATE, activity.date)
            putExtra(EXTRA_ACTIVITY_TIME, activity.startTime?.toString() ?: "")
            putExtra(EXTRA_VISIBILITY, activity.visibility.name)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activityIdForNotification.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        
        // Verificar se o timestamp é no futuro
        if (triggerTime <= System.currentTimeMillis()) {
            return
        }
        
        // Usar método mais confiável baseado na versão do Android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+: setExactAndAllowWhileIdle - funciona mesmo com otimizações de bateria
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Android 4.4+: setExact - mais preciso que set()
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            // Android < 4.4: set - método básico
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        
        // Agendar WorkManager como backup
        scheduleWorkManagerBackup(activity, triggerTime)
        

    }
    
    /**
     * Agenda um WorkManager como backup para a notificação
     */
    private fun scheduleWorkManagerBackup(activity: Activity, triggerTime: Long) {
        try {
            val delay = triggerTime - System.currentTimeMillis()
            if (delay > 0) {
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.mss.thebigcalendar.worker.NotificationWorker>()
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .addTag("notification_backup_${activity.id}")
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
                            .setRequiresBatteryNotLow(false)
                            .setRequiresStorageNotLow(false)
                            .build()
                    )
                    .build()
                
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                    "notification_${activity.id}",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "🔔 Erro ao agendar WorkManager backup", e)
        }
    }

    /**
     * Cancela uma notificação agendada e a notificação atual
     */
    fun cancelNotification(activityId: String) {
        Log.d(TAG, "🔔 Cancelando notificação para atividade: $activityId")
        Log.d(TAG, "🔔 CANCELAMENTO DE NOTIFICAÇÃO INICIADO!")
        
        // Cancelar o alarme agendado
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            activityId.hashCode(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        // Tentar cancelar também o alarme base (para casos de instâncias recorrentes)
        if (activityId.contains("_")) {
            val baseId = activityId.split("_")[0]
            val baseIntent = Intent(context, NotificationReceiver::class.java)
            val basePendingIntent = PendingIntent.getBroadcast(
                context,
                baseId.hashCode(),
                baseIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(basePendingIntent)
            Log.d(TAG, "🔔 Alarmes base e instância cancelados")
        } else {
            Log.d(TAG, "🔔 Alarme cancelado")
        }
        
        // ✅ CANCELAR WORKMANAGER BACKUP
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("notification_${activityId}")
            Log.d(TAG, "🔔 WorkManager backup cancelado para: $activityId")
        } catch (e: Exception) {
            Log.e(TAG, "🔔 Erro ao cancelar WorkManager backup", e)
        }
        
        // Cancelar a notificação atual (se estiver sendo exibida)
        val notificationId = activityId.hashCode()
        Log.d(TAG, "🔔 Cancelando notificação com ID: $notificationId")
        notificationManager.cancel(notificationId)
        
        // Tentar cancelar também com IDs alternativos (para casos de instâncias recorrentes)
        val baseId = if (activityId.contains("_")) {
            activityId.split("_")[0]
        } else {
            activityId
        }
        val baseNotificationId = baseId.hashCode()
        if (baseNotificationId != notificationId) {
            Log.d(TAG, "🔔 Cancelando também notificação base com ID: $baseNotificationId")
            notificationManager.cancel(baseNotificationId)
        }
        
        Log.d(TAG, "🔔 Notificação e alarme cancelados com sucesso")
    }

    /**
     * Cancela todas as notificações de uma atividade recorrente
     * Inclui todas as instâncias futuras que podem ter sido agendadas
     */
    fun cancelAllRecurringNotifications(baseActivity: Activity) {
        Log.d(TAG, "🔔 Cancelando TODAS as notificações recorrentes para: ${baseActivity.title}")
        
        try {
            // ✅ Cancelar a notificação da atividade base
            cancelNotification(baseActivity.id)
            
            // ✅ Cancelar WorkManager backups relacionados
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork("notification_${baseActivity.id}")
            
            // ✅ Para atividades HOURLY, cancelar apenas instâncias próximas (7 dias)
            if (baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true && baseActivity.startTime != null) {
                val timeString = baseActivity.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                val today = java.time.LocalDate.now()
                
                // ✅ Reduzir para apenas 7 dias para evitar loop infinito
                for (i in -1..7) {
                    val futureDate = today.plusDays(i.toLong())
                    val instanceId = "${baseActivity.id}_${futureDate}_${timeString}"
                    cancelNotification(instanceId)
                }
            } else {
                // ✅ Para outras recorrências, cancelar apenas instâncias próximas (30 dias)
                val today = java.time.LocalDate.now()
                for (i in -1..30) {
                    val futureDate = today.plusDays(i.toLong())
                    val instanceId = "${baseActivity.id}_${futureDate}"
                    cancelNotification(instanceId)
                }
            }
            
            // ✅ Cancelar alarmes por título (mais eficiente)
            val titleHash = baseActivity.title.hashCode()
            try {
                alarmManager.cancel(PendingIntent.getBroadcast(
                    context,
                    titleHash,
                    Intent(context, NotificationReceiver::class.java),
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                ))
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Erro ao cancelar alarme por título", e)
            }
            
            Log.d(TAG, "🔔 Cancelamento de notificações recorrentes concluído")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao cancelar notificações recorrentes", e)
        }
        
        Log.d(TAG, "🔔 Todas as notificações recorrentes canceladas para: ${baseActivity.title}")
    }

    /**
     * Calcula o horário da notificação baseado nas configurações
     */
    private fun calculateNotificationTime(activity: Activity): LocalDateTime {
        val notificationType = activity.notificationSettings.notificationType
        
        // Se há um horário específico de notificação configurado, usar ele
        if (activity.notificationSettings.notificationTime != null) {
            return LocalDateTime.parse("${activity.date}T${activity.notificationSettings.notificationTime}")
        }
        
        // Se não há horário específico de notificação, calcular baseado no tipo
        val activityDateTime = if (activity.startTime != null) {
            LocalDateTime.parse("${activity.date}T${activity.startTime}")
        } else {
            LocalDateTime.parse("${activity.date}T00:00")
        }
        
        return when (notificationType) {
            com.mss.thebigcalendar.data.model.NotificationType.NONE -> activityDateTime
            com.mss.thebigcalendar.data.model.NotificationType.BEFORE_ACTIVITY -> {
                // Para BEFORE_ACTIVITY, usar o horário exato da atividade (0 minutos antes)
                activityDateTime
            }
            com.mss.thebigcalendar.data.model.NotificationType.CUSTOM -> {
                val customMinutes = activity.notificationSettings.customMinutesBefore ?: 15
                activityDateTime.minusMinutes(customMinutes.toLong())
            }
            else -> {
                val minutes = notificationType.minutesBefore ?: 15
                activityDateTime.minusMinutes(minutes.toLong())
            }
        }
    }

    /**
     * Converte LocalDateTime para timestamp do sistema
     */
    private fun getTriggerTime(date: String, notificationTime: LocalDateTime): Long {
        return notificationTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Mostra uma notificação imediatamente (para testes)
     */
    fun showNotification(activity: Activity) {
        Log.d(TAG, "🔔 showNotification chamado para: ${activity.title}")
        
        // ✅ Verificar se a notificação já foi enviada recentemente (deduplicação)
        val activityIdForNotification = if (activity.id.contains("_")) {
            activity.id
        } else {
            // Para atividades não recorrentes, usar o ID original
            activity.id
        }
        
        // ✅ Sincronização para evitar condição de corrida
        synchronized(notificationLock) {
            Log.d(TAG, "🔔 Verificando deduplicação para ID: $activityIdForNotification")
            
            if (hasNotificationBeenSentRecently(activityIdForNotification)) {
                Log.d(TAG, "🔔 Notificação duplicada bloqueada para: ${activity.title}")
                return
            }
            
            // ✅ Marcar como enviada ANTES de processar (evita duplicatas)
            markNotificationAsSent(activityIdForNotification)
            Log.d(TAG, "🔔 Notificação marcada como enviada ANTES do processamento para: ${activity.title}")
        }
        
        // Verificar permissões primeiro
        val permissionChecker = NotificationPermissionChecker(context)
        if (!permissionChecker.canShowNotifications()) {
            Log.e(TAG, "🔔 Não é possível mostrar notificações - permissões não concedidas")
            return
        }
        
        // Verificar se precisa exibir alerta de visibilidade
        if (activity.visibility != VisibilityLevel.LOW) {
            Log.d(TAG, "🔔 Usando VisibilityService para visibilidade: ${activity.visibility}")
            // Usar VisibilityService para alertas especiais
            val visibilityService = VisibilityService(context)
            visibilityService.showVisibilityAlert(activity)
            return
        }
        
        // ✅ Intent para abrir a MainActivity quando tocar na notificação
        val mainIntent = Intent(context, com.mss.thebigcalendar.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("selected_activity_id", activity.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            activity.id.hashCode(),
            mainIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = getNotificationSound()
        Log.d(TAG, "🔔 Som da notificação: $soundUri")
        
        val snooze5minPendingIntent = createSnoozePendingIntent(activity, 5)
        val snooze30minPendingIntent = createSnoozePendingIntent(activity, 30)
        val snooze1hourPendingIntent = createSnoozePendingIntent(activity, 60)
        val dismissPendingIntent = createDismissPendingIntent(activity)
        
        Log.d(TAG, "🔔 Criando PendingIntents - Snooze 5min: ${snooze5minPendingIntent != null}, Snooze 30min: ${snooze30minPendingIntent != null}, Snooze 1h: ${snooze1hourPendingIntent != null}, Dismiss: ${dismissPendingIntent != null}")
        Log.d(TAG, "🔔 Snooze 5min PendingIntent ID: ${(activity.id + "_snooze_5").hashCode()}")
        Log.d(TAG, "🔔 Snooze 30min PendingIntent ID: ${(activity.id + "_snooze_30").hashCode()}")
        Log.d(TAG, "🔔 Snooze 1h PendingIntent ID: ${(activity.id + "_snooze_60").hashCode()}")
        Log.d(TAG, "🔔 Dismiss PendingIntent ID: ${(activity.id + "_dismiss").hashCode()}")
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_calendar)
            .setContentTitle("🔔 Lembrete: ${activity.title}")
            .setContentText(getNotificationText(activity))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Finalizado",
                dismissPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_revert,
                "Adiar 5 min",
                snooze5minPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_revert,
                "Adiar 30 min",
                snooze30minPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_revert,
                "Adiar 1 hora",
                snooze1hourPendingIntent
            )
            .setVibrate(longArrayOf(0, 500, 200, 500)) // ✅ Adicionar vibração
            .setLights(0xFF0000FF.toInt(), 1000, 1000) // ✅ Adicionar luz LED
            .build()

        Log.d(TAG, "🔔 Enviando notificação com ID: ${activity.id.hashCode()}")
        notificationManager.notify(activity.id.hashCode(), notification)
        
        Log.d(TAG, "🔔 Notificação enviada com sucesso!")
    }

    /**
     * Gera o texto da notificação
     */
    private fun getNotificationText(activity: Activity): String {
        val timeText = if (activity.startTime != null) {
            " às ${String.format("%02d:%02d", activity.startTime.hour, activity.startTime.minute)}"
        } else {
            ""
        }
        
        return "Atividade programada para hoje$timeText"
    }

    /**
     * Obtém o som padrão de notificação do sistema
     */
    private fun getNotificationSound(): android.net.Uri? {
        return android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
    }

    /**
     * Cria intent para adiar notificação
     */
    private fun createSnoozePendingIntent(activity: Activity, minutes: Int): PendingIntent? {
        return try {
            // Para atividades recorrentes, usar o ID da instância específica
            val activityIdForNotification = if (activity.id.contains("_")) {
                activity.id
            } else {
                // Para atividades não recorrentes, usar o ID original
                activity.id
            }
            
            val snoozeIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_ACTIVITY_ID, activityIdForNotification)
                putExtra("snooze_minutes", minutes)
            }
            
            PendingIntent.getBroadcast(
                context,
                (activityIdForNotification + "_snooze_$minutes").hashCode(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar PendingIntent de adiamento", e)
            null
        }
    }
    /**
     * Cria intent para cancelar notificação
     */
    private fun createDismissPendingIntent(activity: Activity): PendingIntent? {
        return try {
            // Para atividades recorrentes, usar o ID da instância específica
            val activityIdForNotification = if (activity.id.contains("_")) {
                activity.id
            } else {
                // Para atividades não recorrentes, usar o ID original
                activity.id
            }
            
            val dismissIntent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_ACTIVITY_ID, activityIdForNotification)
            }
            
            PendingIntent.getBroadcast(
                context,
                (activityIdForNotification + "_dismiss").hashCode(),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao criar PendingIntent de finalização", e)
            null
        }
    }

    /**
     * Agenda uma notificação adiada para a atividade
     */
    fun scheduleSnoozedNotification(activity: Activity, minutes: Int) {
        try {
            // Calcular o horário de execução
            val now = java.time.LocalDateTime.now()
            val executionTime = now.plusMinutes(minutes.toLong())
            
            // Criar intent para a notificação adiada
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_VIEW_ACTIVITY
                putExtra(EXTRA_ACTIVITY_ID, activity.id)
                putExtra(EXTRA_ACTIVITY_TITLE, activity.title)
                putExtra(EXTRA_ACTIVITY_DATE, activity.date)
                putExtra(EXTRA_ACTIVITY_TIME, activity.startTime?.toString() ?: "")
            }
            
            // Criar PendingIntent único para o adiamento
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                (activity.id + "_snooze_${minutes}min").hashCode(),
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Agendar o alarme
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    executionTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    android.app.AlarmManager.RTC_WAKEUP,
                    executionTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    pendingIntent
                )
            }
            
        } catch (e: Exception) {
            // Erro ao agendar notificação adiada
        }
    }
    
    /**
     * Verifica se uma notificação já foi enviada recentemente para evitar duplicatas
     */
    private fun hasNotificationBeenSentRecently(activityId: String): Boolean {
        val key = KEY_NOTIFICATION_SENT + activityId
        val lastSentTime = prefs.getLong(key, 0)
        val currentTime = System.currentTimeMillis()
        
        val timeDiff = currentTime - lastSentTime
        val wasSentRecently = timeDiff < NOTIFICATION_WINDOW_MS
        
        Log.d(TAG, "🔔 Verificação de deduplicação - ID: $activityId, Última vez: $lastSentTime, Agora: $currentTime, Diferença: ${timeDiff/1000}s, Janela: ${NOTIFICATION_WINDOW_MS/1000}s")
        
        if (wasSentRecently) {
            Log.d(TAG, "🔔 Notificação já enviada recentemente para $activityId (${timeDiff/1000}s atrás)")
        } else {
            Log.d(TAG, "🔔 Notificação não foi enviada recentemente para $activityId (${timeDiff/1000}s atrás)")
        }
        
        return wasSentRecently
    }
    
    /**
     * Marca uma notificação como enviada para controle de deduplicação
     */
    private fun markNotificationAsSent(activityId: String) {
        val key = KEY_NOTIFICATION_SENT + activityId
        val currentTime = System.currentTimeMillis()
        
        prefs.edit()
            .putLong(key, currentTime)
            .apply()
            
        Log.d(TAG, "🔔 Notificação marcada como enviada para $activityId")
    }
    
    /**
     * Limpa o histórico de notificações enviadas (útil para testes)
     */
    fun clearNotificationHistory() {
        prefs.edit().clear().apply()
        Log.d(TAG, "🔔 Histórico de notificações limpo")
    }
    
    /**
     * Limpa notificações antigas do histórico (mais de 1 hora)
     */
    private fun cleanOldNotifications() {
        val currentTime = System.currentTimeMillis()
        val oneHourAgo = currentTime - 3600000L // 1 hora
        
        val allKeys = prefs.all.keys
        val editor = prefs.edit()
        var cleanedCount = 0
        
        for (key in allKeys) {
            if (key.startsWith(KEY_NOTIFICATION_SENT)) {
                val lastSentTime = prefs.getLong(key, 0)
                if (lastSentTime < oneHourAgo) {
                    editor.remove(key)
                    cleanedCount++
                }
            }
        }
        
        editor.apply()
        
        if (cleanedCount > 0) {
            Log.d(TAG, "🔔 Limpeza automática: $cleanedCount notificações antigas removidas")
        }
    }
}
