package com.mss.thebigcalendar.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.GeminiAction
import com.mss.thebigcalendar.data.model.GeminiCommandResult
import com.mss.thebigcalendar.data.model.NotificationSettings
import com.mss.thebigcalendar.data.model.NotificationType
import com.mss.thebigcalendar.data.model.VisibilityLevel
import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.data.repository.CompletedActivityRepository
import com.mss.thebigcalendar.data.repository.DeletedActivityRepository
import com.mss.thebigcalendar.service.GeminiExecutionResult
import com.mss.thebigcalendar.service.GeminiService
import com.mss.thebigcalendar.service.NotificationService
import com.mss.thebigcalendar.service.RecurrenceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

/**
 * Worker do WorkManager para processar agendamentos do assistente de IA em segundo plano.
 * Continua a execução mesmo que o usuário feche a tela ou tire o app dos recentes.
 * Ao concluir, emite uma notificação nativa ao usuário e encerra-se automaticamente.
 */
class GeminiSchedulerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "GeminiSchedulerWorker"
        const val KEY_PROMPT = "gemini_prompt"
        const val KEY_API_KEY = "gemini_api_key"
        const val KEY_MODEL = "gemini_model"
        const val WORK_NAME = "gemini_scheduler_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prompt = inputData.getString(KEY_PROMPT) ?: return@withContext Result.failure()
        val apiKey = inputData.getString(KEY_API_KEY) ?: return@withContext Result.failure()
        val model = inputData.getString(KEY_MODEL) ?: GeminiService.DEFAULT_MODEL

        Log.d(TAG, "Iniciando processamento em segundo plano do comando IA: $prompt")

        val activityRepository = ActivityRepository(applicationContext)
        val recurrenceService = RecurrenceService()
        val notificationService = NotificationService(applicationContext)
        val geminiService = GeminiService()

        return@withContext try {
            val existingActivities = activityRepository.activities.first()
            val result = geminiService.executeCommand(
                prompt = prompt,
                apiKey = apiKey,
                model = model,
                existingActivities = existingActivities
            )

            when (result) {
                is GeminiExecutionResult.Success -> {
                    val command = result.command
                    handleCommand(command, activityRepository, recurrenceService, notificationService)
                    
                    // Notificar o usuário que o agendamento foi concluído
                    val notifTitle = "Agendamento com IA Concluído"
                    val notifMessage = command.replyMessage.ifBlank {
                        "Sua solicitação foi agendada com sucesso no Big Calendar."
                    }
                    notificationService.showAiSchedulingSuccessNotification(notifTitle, notifMessage)
                    Log.d(TAG, "Agendamento concluído com sucesso via Worker!")
                    Result.success()
                }
                is GeminiExecutionResult.Error -> {
                    Log.w(TAG, "Erro retornado pelo Gemini: ${result.message}")
                    notificationService.showAiSchedulingSuccessNotification(
                        "Falha ao agendar com IA",
                        result.message
                    )
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exceção no GeminiSchedulerWorker", e)
            notificationService.showAiSchedulingSuccessNotification(
                "Falha ao agendar com IA",
                "Ocorreu um erro ao processar o agendamento em segundo plano: ${e.localizedMessage ?: e.message}"
            )
            Result.failure()
        }
    }

    private suspend fun handleCommand(
        command: GeminiCommandResult,
        activityRepository: ActivityRepository,
        recurrenceService: RecurrenceService,
        notificationService: NotificationService
    ) {
        when (command.action) {
            GeminiAction.CREATE -> {
                val actType = try {
                    command.activityType?.let { ActivityType.valueOf(it.uppercase()) } ?: ActivityType.TASK
                } catch (e: Exception) {
                    ActivityType.TASK
                }

                val visibility = try {
                    command.visibility?.let { VisibilityLevel.valueOf(it.uppercase()) } ?: VisibilityLevel.LOW
                } catch (e: Exception) {
                    VisibilityLevel.LOW
                }

                val startTime = command.startTime?.let {
                    try { java.time.LocalTime.parse(it) } catch (e: Exception) { null }
                }

                val endTime = command.endTime?.let {
                    try { java.time.LocalTime.parse(it) } catch (e: Exception) { null }
                }

                val actDate = command.date ?: LocalDate.now().toString()
                val categoryColor = resolveCategoryColor(command.priority, actType)
                val recurrenceRule = recurrenceService.normalizeRecurrenceRule(command.recurrenceRule)

                val newActivity = Activity(
                    id = UUID.randomUUID().toString(),
                    title = command.title?.ifBlank { "Nova atividade" } ?: "Nova atividade",
                    description = command.description,
                    date = actDate,
                    startTime = startTime,
                    endTime = endTime,
                    isAllDay = command.isAllDay,
                    location = null,
                    categoryColor = categoryColor,
                    activityType = actType,
                    recurrenceRule = recurrenceRule,
                    notificationSettings = NotificationSettings(
                        isEnabled = command.notificationEnabled,
                        notificationType = if (command.notificationEnabled) {
                            if (command.notificationMinutesBefore > 0) NotificationType.CUSTOM else NotificationType.BEFORE_ACTIVITY
                        } else NotificationType.NONE,
                        customMinutesBefore = if (command.notificationMinutesBefore > 0) command.notificationMinutesBefore else null
                    ),
                    isCompleted = false,
                    visibility = visibility,
                    showInCalendar = true,
                    isFromGoogle = false,
                    lastModified = System.currentTimeMillis()
                )

                activityRepository.saveActivity(newActivity)

                if (newActivity.notificationSettings.isEnabled) {
                    notificationService.scheduleNotification(newActivity)
                }
            }
            GeminiAction.UPDATE -> {
                val allActivities = activityRepository.activities.first()
                val target = command.targetActivityId?.let { id -> allActivities.find { it.id == id } }
                    ?: command.targetActivityTitle?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }
                    ?: command.title?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }

                if (target != null) {
                    val updatedRecurrenceRule = if (command.recurrenceRule != null) {
                        recurrenceService.normalizeRecurrenceRule(command.recurrenceRule)
                    } else {
                        target.recurrenceRule
                    }

                    val updatedCategoryColor = if (!command.priority.isNullOrBlank()) {
                        resolveCategoryColor(command.priority, target.activityType)
                    } else {
                        target.categoryColor
                    }

                    val updated = target.copy(
                        title = command.title ?: target.title,
                        date = command.date ?: target.date,
                        description = command.description ?: target.description,
                        startTime = command.startTime?.let { try { java.time.LocalTime.parse(it) } catch (e: Exception) { target.startTime } } ?: target.startTime,
                        endTime = command.endTime?.let { try { java.time.LocalTime.parse(it) } catch (e: Exception) { target.endTime } } ?: target.endTime,
                        isAllDay = command.isAllDay,
                        categoryColor = updatedCategoryColor,
                        recurrenceRule = updatedRecurrenceRule,
                        notificationSettings = if (command.notificationEnabled) {
                            target.notificationSettings.copy(
                                isEnabled = true,
                                notificationType = if (command.notificationMinutesBefore > 0) NotificationType.CUSTOM else NotificationType.BEFORE_ACTIVITY,
                                customMinutesBefore = if (command.notificationMinutesBefore > 0) command.notificationMinutesBefore else null
                            )
                        } else target.notificationSettings,
                        lastModified = System.currentTimeMillis()
                    )

                    activityRepository.saveActivity(updated)
                    if (updated.notificationSettings.isEnabled) {
                        notificationService.cancelActivityNotifications(target)
                        notificationService.scheduleNotification(updated)
                    }
                }
            }
            GeminiAction.DELETE -> {
                val allActivities = activityRepository.activities.first()
                val target = command.targetActivityId?.let { id -> allActivities.find { it.id == id } }
                    ?: command.targetActivityTitle?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }
                    ?: command.title?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }

                if (target != null) {
                    notificationService.cancelActivityNotifications(target)
                    DeletedActivityRepository(applicationContext).addDeletedActivity(target)
                    activityRepository.deleteActivity(target.id)
                }
            }
            GeminiAction.COMPLETE -> {
                val allActivities = activityRepository.activities.first()
                val target = command.targetActivityId?.let { id -> allActivities.find { it.id == id } }
                    ?: command.targetActivityTitle?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }
                    ?: command.title?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }

                if (target != null) {
                    val completed = target.copy(isCompleted = true, lastModified = System.currentTimeMillis())
                    CompletedActivityRepository(applicationContext).addCompletedActivity(completed)
                    activityRepository.saveActivity(completed)
                    notificationService.cancelActivityNotifications(target)
                }
            }
            GeminiAction.QUERY, GeminiAction.NONE -> {
                // Apenas consulta
            }
        }
    }

    private fun resolveCategoryColor(priorityOrColor: String?, actType: ActivityType): String {
        if (priorityOrColor.isNullOrBlank()) {
            return when (actType) {
                ActivityType.TASK -> "2"
                ActivityType.EVENT -> "2"
                ActivityType.NOTE -> "#9C27B0"
                ActivityType.BIRTHDAY -> "#FF69B4"
                ActivityType.COMMEMORATIVE -> "#FF9800"
            }
        }

        val clean = priorityOrColor.trim().lowercase()
        return when (clean) {
            "1", "baixa", "baixa prioridade", "low", "branca", "branco", "white" -> "1"
            "2", "media", "média", "media prioridade", "média prioridade", "medium", "normal", "padrao", "padrão", "azul", "blue" -> "2"
            "3", "alta", "alta prioridade", "high", "importante", "muito importante", "amarela", "amarelo", "yellow" -> "3"
            "4", "urgente", "urgência", "urgencia", "urgent", "maxima", "máxima", "maxima prioridade", "máxima prioridade", "critica", "crítica", "altissima", "altíssima", "vermelha", "vermelho", "red" -> "4"
            "verde", "green" -> "#4CAF50"
            "laranja", "orange" -> "#FF9800"
            "roxo", "roxa", "purple" -> "#9C27B0"
            "rosa", "pink" -> "#FF69B4"
            "cinza", "gray", "grey" -> "#9E9E9E"
            "preto", "preta", "black" -> "#212121"
            else -> {
                if (clean.startsWith("#")) clean.uppercase()
                else if (clean.length == 6 && clean.all { it in "0123456789abcdefABCDEF" }) "#${clean.uppercase()}"
                else when (actType) {
                    ActivityType.TASK -> "2"
                    ActivityType.EVENT -> "2"
                    ActivityType.NOTE -> "#9C27B0"
                    ActivityType.BIRTHDAY -> "#FF69B4"
                    ActivityType.COMMEMORATIVE -> "#FF9800"
                }
            }
        }
    }
}
