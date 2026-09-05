package com.mss.thebigcalendar.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.NotificationType
import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.widget.EventListWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Calendar

object RolloverManager {
    private const val TAG = "RolloverManager"
    const val ACTION_MIDNIGHT_ROLLOVER = "com.mss.thebigcalendar.ACTION_MIDNIGHT_ROLLOVER"
    private const val MIDNIGHT_ROLLOVER_ALARM_ID = 8888

    private val mutex = Mutex()
    
    @Volatile
    private var lastCheckedDate: LocalDate? = null

    /**
     * Executa o processo de rollover para tarefas não concluídas de dias anteriores.
     * Cancela as notificações do dia anterior, move para a data atual, salva no repositório,
     * reagenda/dispara as novas notificações e notifica os widgets da tela inicial.
     * Se já foi verificado hoje e não for forçado, retorna imediatamente sem acessar o disco.
     * Retorna a quantidade de tarefas que foram puladas.
     */
    suspend fun performRollover(context: Context, force: Boolean = false): Int = mutex.withLock {
        val today = LocalDate.now()
        if (!force && lastCheckedDate == today) {
            Log.d(TAG, "⏭️ Rollover já verificado para a data de hoje ($today). Retornando sem overhead.")
            return 0
        }

        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔄 Iniciando verificação de rollover para $today...")
                val activityRepository = ActivityRepository(context)
                val notificationService = NotificationService(context)

                val allActivities = activityRepository.activities.first()
                Log.d(TAG, "Total de atividades no repositório: ${allActivities.size}")

                val activitiesToRollover = allActivities.filter { activity ->
                    // Apenas tarefas com rollover habilitado e não concluídas
                    if (!activity.rollover || activity.isCompleted) {
                        return@filter false
                    }
                    // Apenas atividades de ocorrência única (não recorrentes)
                    if (!activity.recurrenceRule.isNullOrEmpty() && activity.recurrenceRule != "NONE") {
                        return@filter false
                    }
                    // Apenas atividades com data anterior a hoje
                    try {
                        val activityDate = LocalDate.parse(activity.date)
                        activityDate.isBefore(today)
                    } catch (e: Exception) {
                        Log.w(TAG, "Data inválida na atividade ${activity.title}: ${activity.date}")
                        false
                    }
                }

                if (activitiesToRollover.isEmpty()) {
                    Log.d(TAG, "Nenhuma atividade pendente de rollover para $today.")
                    lastCheckedDate = today
                    return@withContext 0
                }

                Log.d(TAG, "📋 Encontradas ${activitiesToRollover.size} atividades para pular para hoje ($today).")

                // 1. Cancelar alarmes/notificações da data anterior para não gerar alarmes órfãos/stale
                activitiesToRollover.forEach { activity ->
                    try {
                        notificationService.cancelActivityNotifications(activity)
                        Log.d(TAG, "🔔 Notificação anterior cancelada para: ${activity.title} (data antiga: ${activity.date})")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao cancelar notificação antiga de ${activity.title}", e)
                    }
                }

                // 2. Criar instâncias atualizadas com a nova data (hoje)
                val updatedActivities = activitiesToRollover.map { activity ->
                    Log.d(TAG, "Movendo atividade: ${activity.title} de ${activity.date} para $today")
                    activity.copy(
                        date = today.toString(),
                        lastModified = System.currentTimeMillis()
                    )
                }

                // 3. Salvar no repositório
                activityRepository.saveAllActivities(updatedActivities)
                Log.d(TAG, "💾 Salvas com sucesso ${updatedActivities.size} atividades com nova data.")

                // 4. Reagendar ou exibir notificações para a nova data
                updatedActivities.forEach { updatedActivity ->
                    if (updatedActivity.notificationSettings.isEnabled &&
                        updatedActivity.notificationSettings.notificationType != NotificationType.NONE
                    ) {
                        try {
                            val triggerTime = notificationService.getNotificationTriggerTime(updatedActivity)
                            val now = System.currentTimeMillis()

                            if (triggerTime > now) {
                                // Horário futuro no dia de hoje: agendar no AlarmManager e WorkManager
                                notificationService.scheduleNotification(updatedActivity)
                                Log.d(TAG, "⏰ Notificação reagendada para hoje: ${updatedActivity.title} (timestamp: $triggerTime)")
                            } else {
                                // O horário de hoje já passou ou não há horário específico (dia todo):
                                // Exibir notificação imediatamente para que o usuário seja alertado
                                withContext(Dispatchers.Main) {
                                    notificationService.showNotification(updatedActivity)
                                }
                                Log.d(TAG, "🔔 Horário da notificação já passou hoje. Exibindo notificação imediata para: ${updatedActivity.title}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Erro ao processar notificação para atividade pulada: ${updatedActivity.title}", e)
                        }
                    }
                }

                // 5. Notificar widgets para refletir a mudança na tela inicial
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val componentName = ComponentName(context, EventListWidgetProvider::class.java)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                    if (appWidgetIds.isNotEmpty()) {
                        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                            component = componentName
                        }
                        context.sendBroadcast(intent)
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.event_list_view)
                        Log.d(TAG, "📱 Widgets notificados com sucesso (${appWidgetIds.size} widgets)")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao notificar widgets após rollover", e)
                }

                lastCheckedDate = today
                Log.d(TAG, "✅ Rollover concluído com sucesso: ${updatedActivities.size} atividades puladas.")
                updatedActivities.size
            } catch (e: Exception) {
                Log.e(TAG, "❌ Erro durante execução do Rollover", e)
                0
            }
        }
    }

    /**
     * Agenda um alarme exato para a próxima meia-noite (00:00:05) para disparar o rollover
     * mesmo se o aplicativo estiver completamente fechado ou o dispositivo em economia de bateria.
     */
    fun scheduleMidnightRollover(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = ACTION_MIDNIGHT_ROLLOVER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                MIDNIGHT_ROLLOVER_ALARM_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Cancelar alarme anterior para evitar duplicidades
            alarmManager.cancel(pendingIntent)

            // Calcular a próxima meia-noite: 00:00:05 de amanhã
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }
            val triggerTime = calendar.timeInMillis

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
            Log.d(TAG, "🌙 Rollover de meia-noite agendado via AlarmManager para: ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao agendar rollover de meia-noite", e)
        }
    }
}
