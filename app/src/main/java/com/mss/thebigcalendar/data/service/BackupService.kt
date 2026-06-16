package com.mss.thebigcalendar.data.service

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.model.File as DriveFile
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.DeletedActivity
import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.data.repository.CompletedActivityRepository
import com.mss.thebigcalendar.data.repository.DeletedActivityRepository
import com.mss.thebigcalendar.service.GoogleDriveService
import com.mss.thebigcalendar.service.NotificationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.mss.thebigcalendar.data.model.CalendarFilterOptions
import com.mss.thebigcalendar.data.model.Theme
import com.mss.thebigcalendar.data.model.AnimationType
import com.mss.thebigcalendar.data.model.SidebarFilterVisibility
import com.mss.thebigcalendar.data.model.Language
import com.mss.thebigcalendar.data.model.JsonCalendar
import com.mss.thebigcalendar.data.model.colorToString
import com.mss.thebigcalendar.data.model.toColor
import com.mss.thebigcalendar.data.repository.SettingsRepository
import com.mss.thebigcalendar.data.repository.JsonCalendarRepository
import com.mss.thebigcalendar.data.repository.AutoBackupSettings
import com.mss.thebigcalendar.data.repository.BackupFrequency
import com.mss.thebigcalendar.data.repository.BackupType
import com.mss.thebigcalendar.data.model.AlarmSettings
import com.mss.thebigcalendar.data.repository.AlarmRepository


class BackupService(
    private val context: Context,
    private val activityRepository: ActivityRepository,
    private val deletedActivityRepository: DeletedActivityRepository,
    private val completedActivityRepository: CompletedActivityRepository,
    private val alarmRepository: AlarmRepository = AlarmRepository(context)
) {

    private val notificationService = NotificationService(context)
    private val settingsRepository = SettingsRepository(context)
    private val jsonCalendarRepository = JsonCalendarRepository(context)

    private fun getGoogleDriveService(account: GoogleSignInAccount): GoogleDriveService {
        return GoogleDriveService(context, account)
    }

    suspend fun createCloudBackup(account: GoogleSignInAccount): Result<String> = withContext(Dispatchers.IO) {
        notificationService.showBackupInProgressNotification()
        val tempFile = File.createTempFile(BACKUP_FILE_PREFIX, BACKUP_FILE_EXTENSION, context.cacheDir)
        try {
            val activities = activityRepository.activities.first()
            val deletedActivities = deletedActivityRepository.deletedActivities.first()
            val completedActivities = completedActivityRepository.completedActivities.first()

            val backupData = createBackupJson(activities, deletedActivities, completedActivities)
            tempFile.writeText(backupData, Charsets.UTF_8)

            val appProperties = mapOf(
                "totalActivities" to activities.size.toString(),
                "totalDeletedActivities" to deletedActivities.size.toString(),
                "totalCompletedActivities" to completedActivities.size.toString()
            )

            val driveService = getGoogleDriveService(account)
            val uploadedFile = driveService.uploadBackupFile(tempFile, appProperties)

            if (uploadedFile != null) {
                notificationService.showBackupCompleteNotification(uploadedFile.name)
                Result.success(uploadedFile.id)
            } else {
                notificationService.showBackupFailedNotification("Falha ao fazer upload do arquivo de backup para o Google Drive")
                Result.failure(Exception("Falha ao fazer upload do arquivo de backup para o Google Drive"))
            }
        } catch (e: Exception) {
            notificationService.showBackupFailedNotification(e.message ?: "Erro desconhecido")
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun listCloudBackupFiles(account: GoogleSignInAccount): Result<List<DriveFile>> = withContext(Dispatchers.IO) {
        try {
            val driveService = getGoogleDriveService(account)
            val files = driveService.getBackupFiles()
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloudBackup(account: GoogleSignInAccount, fileId: String, fileName: String): Result<RestoreResult> = withContext(Dispatchers.IO) {
        notificationService.showRestoreInProgressNotification()
        try {
            val driveService = getGoogleDriveService(account)
            val tempFile = File.createTempFile("restore_", ".json", context.cacheDir)
            driveService.downloadBackupFile(fileId, tempFile)

            val result = restoreFromBackup(Uri.fromFile(tempFile))

            tempFile.delete()

            if (result.isSuccess) {
                notificationService.showRestoreCompleteNotification(fileName)
            } else {
                notificationService.showRestoreFailedNotification(result.exceptionOrNull()?.message ?: "Erro desconhecido")
            }

            result
        } catch (e: Exception) {
            notificationService.showRestoreFailedNotification(e.message ?: "Erro desconhecido")
            Result.failure(e)
        }
    }

    suspend fun deleteCloudBackup(account: GoogleSignInAccount, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val driveService = getGoogleDriveService(account)
            driveService.deleteBackupFile(fileId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    companion object {
        private const val TAG = "BackupService"
        private const val BACKUP_FOLDER = "TBCalendar/Backup"
        private const val BACKUP_FILE_PREFIX = "TBCalendar_Backup_"
        private const val BACKUP_FILE_EXTENSION = ".json"
    }

    /**
     * Gera um backup completo de todas as atividades e itens da lixeira usando SAF.
     */
    suspend fun createBackup(directoryUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        notificationService.showBackupInProgressNotification()
        try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri)
            if (directory == null || !directory.canWrite()) {
                val errorMessage = "Permissão negada para escrever no diretório selecionado."
                notificationService.showBackupFailedNotification(errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }

            // Coletar dados para backup
            val activities = activityRepository.activities.first()
            val deletedActivities = deletedActivityRepository.deletedActivities.first()
            val completedActivities = completedActivityRepository.completedActivities.first()

            // Criar estrutura JSON do backup
            val backupData = createBackupJson(activities, deletedActivities, completedActivities)

            // Gerar nome do arquivo com timestamp
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val backupFileName = "$BACKUP_FILE_PREFIX$timestamp$BACKUP_FILE_EXTENSION"

            // Criar arquivo de backup usando SAF
            val backupFile = directory.createFile("application/json", backupFileName)
            if (backupFile == null) {
                val errorMessage = "Falha ao criar arquivo de backup no diretório selecionado."
                notificationService.showBackupFailedNotification(errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }

            // Escrever no arquivo de backup
            context.contentResolver.openOutputStream(backupFile.uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(backupData)
                }
            }

            notificationService.showBackupCompleteNotification(backupFile.name ?: backupFileName)
            Result.success(backupFile.name ?: backupFileName)
        } catch (e: Exception) {
            notificationService.showBackupFailedNotification(e.message ?: "Erro desconhecido")
            Result.failure(e)
        }
    }

    /**
     * Cria a estrutura JSON do backup
     */
    private suspend fun createBackupJson(activities: List<Activity>, deletedActivities: List<DeletedActivity>, completedActivities: List<Activity>): String {
        val backupJson = JSONObject()
        
        // Metadados do backup
        backupJson.put("backupVersion", "1.1")
        backupJson.put("createdAt", LocalDateTime.now().toString())
        backupJson.put("appVersion", "TheBigCalendar")
        backupJson.put("totalActivities", activities.size)
        backupJson.put("totalDeletedActivities", deletedActivities.size)
        backupJson.put("totalCompletedActivities", completedActivities.size)
        
        // Serializar configurações
        try {
            val filterOptionsVal = settingsRepository.filterOptions.first()
            val showMoonPhasesVal = settingsRepository.showMoonPhases.first()
            val sidebarFilterVal = settingsRepository.sidebarFilterVisibility.first()
            val autoBackupVal = settingsRepository.autoBackupSettings.first()
            val backupDirUriVal = settingsRepository.backupDirectoryUri.first() ?: ""

            val settingsJson = JSONObject().apply {
                put("theme", settingsRepository.theme.first().name)
                put("welcomeName", settingsRepository.welcomeName.first())
                put("showHolidays", filterOptionsVal.showHolidays)
                put("showEvents", filterOptionsVal.showEvents)
                put("showTasks", filterOptionsVal.showTasks)
                put("showBirthdays", filterOptionsVal.showBirthdays)
                put("showNotes", filterOptionsVal.showNotes)
                put("showCommemorative", filterOptionsVal.showCommemorative)
                put("showMoonPhases", showMoonPhasesVal)
                put("animationType", settingsRepository.animationType.first().name)
                put("language", settingsRepository.language.first().code)
                put("calendarScale", settingsRepository.calendarScale.first().toString())
                put("hideOtherMonthDays", settingsRepository.hideOtherMonthDays.first())
                put("pureBlackTheme", settingsRepository.pureBlackTheme.first())
                put("primaryColor", settingsRepository.primaryColor.first())
                put("unfixHeadersOnScroll", settingsRepository.unfixHeadersOnScroll.first())
                
                put("sidebarShowHolidays", sidebarFilterVal.showHolidays)
                put("sidebarShowEvents", sidebarFilterVal.showEvents)
                put("sidebarShowTasks", sidebarFilterVal.showTasks)
                put("sidebarShowBirthdays", sidebarFilterVal.showBirthdays)
                put("sidebarShowNotes", sidebarFilterVal.showNotes)
                put("sidebarShowCompletedTasks", sidebarFilterVal.showCompletedTasks)
                put("sidebarShowMoonPhases", sidebarFilterVal.showMoonPhases)
                put("sidebarShowCommemorative", sidebarFilterVal.showCommemorative)
                
                // Auto Backup
                put("autoBackupEnabled", autoBackupVal.enabled)
                put("autoBackupFrequency", autoBackupVal.frequency.name)
                put("autoBackupHour", autoBackupVal.hour)
                put("autoBackupMinute", autoBackupVal.minute)
                put("autoBackupType", autoBackupVal.backupType.name)
                
                put("crashlyticsEnabled", settingsRepository.isCrashlyticsEnabled.first())
                put("hasSeenMainOnboarding", settingsRepository.hasSeenMainOnboarding.first())
                put("backupDirectoryUri", backupDirUriVal)
            }
            backupJson.put("settings", settingsJson)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao serializar configurações no backup: ${e.message}", e)
        }

        // Serializar calendários JSON
        try {
            val jsonCalendarsList = jsonCalendarRepository.getAllJsonCalendars().first()
            val jsonCalendarsArray = JSONArray()
            jsonCalendarsList.forEach { calendar ->
                val calendarJson = JSONObject().apply {
                    put("id", calendar.id)
                    put("title", calendar.title)
                    put("color", calendar.colorToString())
                    put("fileName", calendar.fileName)
                    put("importDate", calendar.importDate)
                    put("isVisible", calendar.isVisible)
                }
                jsonCalendarsArray.put(calendarJson)
            }
            backupJson.put("jsonCalendars", jsonCalendarsArray)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao serializar calendários JSON no backup: ${e.message}", e)
        }
        
        // Serializar alarmes (despertadores)
        try {
            val alarmsList = alarmRepository.getAllAlarms()
            val alarmsArray = JSONArray()
            alarmsList.forEach { alarm ->
                val alarmJson = JSONObject().apply {
                    put("id", alarm.id)
                    put("label", alarm.label)
                    put("time", alarm.time.toString())
                    put("isEnabled", alarm.isEnabled)
                    
                    val repeatDaysArray = JSONArray()
                    alarm.repeatDays.forEach { day ->
                        repeatDaysArray.put(day)
                    }
                    put("repeatDays", repeatDaysArray)
                    
                    put("soundEnabled", alarm.soundEnabled)
                    put("vibrationEnabled", alarm.vibrationEnabled)
                    put("snoozeMinutes", alarm.snoozeMinutes)
                    put("createdAt", alarm.createdAt)
                    put("lastModified", alarm.lastModified)
                    put("skippedDate", alarm.skippedDate)
                }
                alarmsArray.put(alarmJson)
            }
            backupJson.put("alarms", alarmsArray)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Erro ao serializar alarmes no backup: ${e.message}", e)
        }
        
        // Atividades ativas
        val activitiesArray = JSONArray()
        activities.forEach { activity ->
            val activityJson = JSONObject()
            activityJson.put("id", activity.id)
            activityJson.put("title", activity.title)
            activityJson.put("description", activity.description ?: "")
            activityJson.put("date", activity.date)
            activityJson.put("startTime", activity.startTime?.toString() ?: "")
            activityJson.put("endTime", activity.endTime?.toString() ?: "")
            activityJson.put("isAllDay", activity.isAllDay)
            activityJson.put("location", activity.location ?: "")
            activityJson.put("categoryColor", activity.categoryColor)
            activityJson.put("activityType", activity.activityType.name)
            activityJson.put("recurrenceRule", activity.recurrenceRule ?: "")
            activityJson.put("isCompleted", activity.isCompleted)
            activityJson.put("visibility", activity.visibility.name)
            activityJson.put("showInCalendar", activity.showInCalendar)
            activityJson.put("isFromGoogle", activity.isFromGoogle)
            
            // Campos de recorrência
            val excludedDatesArray = JSONArray()
            activity.excludedDates.forEach { date ->
                excludedDatesArray.put(date)
            }
            activityJson.put("excludedDates", excludedDatesArray)
            
            val excludedInstancesArray = JSONArray()
            activity.excludedInstances.forEach { instance ->
                excludedInstancesArray.put(instance)
            }
            activityJson.put("excludedInstances", excludedInstancesArray)
            
            activityJson.put("wikipediaLink", activity.wikipediaLink ?: "")
            
            // Configurações de notificação
            val notificationJson = JSONObject()
            notificationJson.put("isEnabled", activity.notificationSettings.isEnabled)
            notificationJson.put("notificationType", activity.notificationSettings.notificationType.name)
            notificationJson.put("customMinutesBefore", activity.notificationSettings.customMinutesBefore)
            notificationJson.put("notificationTime", activity.notificationSettings.notificationTime?.toString() ?: "")
            activityJson.put("notificationSettings", notificationJson)
            
            activitiesArray.put(activityJson)
        }
        backupJson.put("activities", activitiesArray)
        
        // Itens da lixeira
        val deletedActivitiesArray = JSONArray()
        deletedActivities.forEach { deletedActivity ->
            val deletedJson = JSONObject()
            deletedJson.put("id", deletedActivity.id)
            deletedJson.put("deletedAt", deletedActivity.deletedAt)
            deletedJson.put("deletedBy", deletedActivity.deletedBy)
            
            // Dados da atividade original
            val originalActivity = deletedActivity.originalActivity
            val originalJson = JSONObject()
            originalJson.put("id", originalActivity.id)
            originalJson.put("title", originalActivity.title)
            originalJson.put("description", originalActivity.description ?: "")
            originalJson.put("date", originalActivity.date)
            originalJson.put("startTime", originalActivity.startTime?.toString() ?: "")
            originalJson.put("endTime", originalActivity.endTime?.toString() ?: "")
            originalJson.put("isAllDay", originalActivity.isAllDay)
            originalJson.put("location", originalActivity.location ?: "")
            originalJson.put("categoryColor", originalActivity.categoryColor)
            originalJson.put("activityType", originalActivity.activityType.name)
            originalJson.put("recurrenceRule", originalActivity.recurrenceRule ?: "")
            originalJson.put("isCompleted", originalActivity.isCompleted)
            originalJson.put("visibility", originalActivity.visibility.name)
            originalJson.put("showInCalendar", originalActivity.showInCalendar)
            originalJson.put("isFromGoogle", originalActivity.isFromGoogle)
            
            deletedJson.put("originalActivity", originalJson)
            deletedActivitiesArray.put(deletedJson)
        }
        backupJson.put("deletedActivities", deletedActivitiesArray)
        
        // Atividades concluídas
        val completedActivitiesArray = JSONArray()
        completedActivities.forEach { activity ->
            val activityJson = JSONObject()
            activityJson.put("id", activity.id)
            activityJson.put("title", activity.title)
            activityJson.put("description", activity.description ?: "")
            activityJson.put("date", activity.date)
            activityJson.put("startTime", activity.startTime?.toString() ?: "")
            activityJson.put("endTime", activity.endTime?.toString() ?: "")
            activityJson.put("isAllDay", activity.isAllDay)
            activityJson.put("location", activity.location ?: "")
            activityJson.put("categoryColor", activity.categoryColor)
            activityJson.put("activityType", activity.activityType.name)
            activityJson.put("recurrenceRule", activity.recurrenceRule ?: "")
            activityJson.put("isCompleted", activity.isCompleted)
            activityJson.put("visibility", activity.visibility.name)
            activityJson.put("showInCalendar", activity.showInCalendar)
            activityJson.put("isFromGoogle", activity.isFromGoogle)
            
            // Campos de recorrência
            val excludedDatesArray = JSONArray()
            activity.excludedDates.forEach { date ->
                excludedDatesArray.put(date)
            }
            activityJson.put("excludedDates", excludedDatesArray)
            
            val excludedInstancesArray = JSONArray()
            activity.excludedInstances.forEach { instance ->
                excludedInstancesArray.put(instance)
            }
            activityJson.put("excludedInstances", excludedInstancesArray)
            
            activityJson.put("wikipediaLink", activity.wikipediaLink ?: "")
            
            // Configurações de notificação
            val notificationJson = JSONObject()
            notificationJson.put("isEnabled", activity.notificationSettings.isEnabled)
            notificationJson.put("notificationType", activity.notificationSettings.notificationType.name)
            notificationJson.put("customMinutesBefore", activity.notificationSettings.customMinutesBefore)
            notificationJson.put("notificationTime", activity.notificationSettings.notificationTime?.toString() ?: "")
            activityJson.put("notificationSettings", notificationJson)
            
            completedActivitiesArray.put(activityJson)
        }
        backupJson.put("completedActivities", completedActivitiesArray)
        
        return backupJson.toString(2) // Pretty print com indentação
    }

    /**
     * Lista todos os arquivos de backup disponíveis usando SAF.
     */
    suspend fun listBackupFiles(directoryUri: Uri): List<DocumentFile> = withContext(Dispatchers.IO) {
        try {
            val directory = DocumentFile.fromTreeUri(context, directoryUri)
            if (directory == null || !directory.canRead()) {
                return@withContext emptyList()
            }

            directory.listFiles()
                .filter { it.isFile && it.name?.startsWith(BACKUP_FILE_PREFIX) == true && it.name?.endsWith(BACKUP_FILE_EXTENSION) == true }
                .sortedByDescending { it.lastModified() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Obtém informações sobre um arquivo de backup usando SAF.
     */
    suspend fun getBackupInfo(backupFile: DocumentFile): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(backupFile.uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Não foi possível ler o arquivo de backup."))

            val json = JSONObject(content)

            val info = BackupInfo(
                fileName = backupFile.name ?: "Unknown",
                uri = backupFile.uri.toString(),
                fileSize = backupFile.length(),
                createdAt = json.optString("createdAt", ""),
                totalActivities = json.optInt("totalActivities", 0),
                totalDeletedActivities = json.optInt("totalDeletedActivities", 0),
                totalCompletedActivities = json.optInt("totalCompletedActivities", 0),
                backupVersion = json.optString("backupVersion", "1.0")
            )

            Result.success(info)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restaura dados de um arquivo de backup usando SAF.
     */
    suspend fun restoreFromBackup(backupUri: Uri): Result<RestoreResult> = withContext(Dispatchers.IO) {
        notificationService.showRestoreInProgressNotification()
        try {
            val content = context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: run {
                val errorMessage = "Não foi possível ler o arquivo de backup para restauração."
                notificationService.showRestoreFailedNotification(errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }

            val json = JSONObject(content)

            // Verificar versão do backup
            val backupVersion = json.optString("backupVersion", "1.0")
            if (backupVersion != "1.0" && backupVersion != "1.1") {
                val errorMessage = "Versão de backup não suportada: $backupVersion"
                notificationService.showRestoreFailedNotification(errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }

            // Extrair atividades
            val activitiesArray = json.optJSONArray("activities") ?: JSONArray()
            val restoredActivities = mutableListOf<com.mss.thebigcalendar.data.model.Activity>()

            for (i in 0 until activitiesArray.length()) {
                val activityJson = activitiesArray.getJSONObject(i)
                try {
                    val activity = parseActivityFromJson(activityJson)
                    restoredActivities.add(activity)
                } catch (e: Exception) {
                    // Erro ao parsear atividade - continuar com outras
                }
            }

            // Extrair itens da lixeira
            val deletedActivitiesArray = json.optJSONArray("deletedActivities") ?: JSONArray()
            val restoredDeletedActivities = mutableListOf<com.mss.thebigcalendar.data.model.DeletedActivity>()

            for (i in 0 until deletedActivitiesArray.length()) {
                val deletedJson = deletedActivitiesArray.getJSONObject(i)
                try {
                    val deletedActivity = parseDeletedActivityFromJson(deletedJson)
                    restoredDeletedActivities.add(deletedActivity)
                } catch (e: Exception) {
                    // Erro ao parsear item da lixeira - continuar com outros
                }
            }

            // Extrair atividades concluídas
            val completedActivitiesArray = json.optJSONArray("completedActivities") ?: JSONArray()
            val restoredCompletedActivities = mutableListOf<com.mss.thebigcalendar.data.model.Activity>()

            for (i in 0 until completedActivitiesArray.length()) {
                val activityJson = completedActivitiesArray.getJSONObject(i)
                try {
                    val activity = parseActivityFromJson(activityJson)
                    restoredCompletedActivities.add(activity)
                } catch (e: Exception) {
                    // Erro ao parsear atividade concluída - continuar com outras
                }
            }

            // Restaurar configurações
            val settingsJson = json.optJSONObject("settings")
            if (settingsJson != null) {
                try {
                    val themeName = settingsJson.optString("theme", Theme.SYSTEM.name)
                    settingsRepository.saveTheme(Theme.valueOf(themeName))
                    
                    val welcomeName = settingsJson.optString("welcomeName", "")
                    settingsRepository.saveWelcomeName(welcomeName)
                    
                    val filterOptions = CalendarFilterOptions(
                        showHolidays = settingsJson.optBoolean("showHolidays", true),
                        showEvents = settingsJson.optBoolean("showEvents", true),
                        showTasks = settingsJson.optBoolean("showTasks", true),
                        showBirthdays = settingsJson.optBoolean("showBirthdays", true),
                        showNotes = settingsJson.optBoolean("showNotes", true),
                        showCommemorative = settingsJson.optBoolean("showCommemorative", true)
                    )
                    settingsRepository.saveFilterOptions(filterOptions)
                    
                    val showMoonPhases = settingsJson.optBoolean("showMoonPhases", false)
                    settingsRepository.saveShowMoonPhases(showMoonPhases)
                    
                    val animationTypeName = settingsJson.optString("animationType", AnimationType.NONE.name)
                    settingsRepository.saveAnimationType(AnimationType.valueOf(animationTypeName))
                    
                    val languageCode = settingsJson.optString("language", Language.SYSTEM.code)
                    settingsRepository.saveLanguage(Language.fromCode(languageCode))
                    
                    val calendarScale = settingsJson.optString("calendarScale", "1f").toFloatOrNull() ?: 1f
                    settingsRepository.setCalendarScale(calendarScale)
                    
                    val hideOtherMonthDays = settingsJson.optBoolean("hideOtherMonthDays", false)
                    settingsRepository.setHideOtherMonthDays(hideOtherMonthDays)
                    
                    val pureBlackTheme = settingsJson.optBoolean("pureBlackTheme", false)
                    settingsRepository.setPureBlackTheme(pureBlackTheme)
                    
                    val primaryColor = settingsJson.optString("primaryColor", "AUTO")
                    settingsRepository.setPrimaryColor(primaryColor)
                    
                    val unfixHeadersOnScroll = settingsJson.optBoolean("unfixHeadersOnScroll", true)
                    settingsRepository.setUnfixHeadersOnScroll(unfixHeadersOnScroll)
                    
                    // Sidebar
                    val sidebarFilter = SidebarFilterVisibility(
                        showHolidays = settingsJson.optBoolean("sidebarShowHolidays", true),
                        showEvents = settingsJson.optBoolean("sidebarShowEvents", true),
                        showTasks = settingsJson.optBoolean("sidebarShowTasks", true),
                        showBirthdays = settingsJson.optBoolean("sidebarShowBirthdays", true),
                        showNotes = settingsJson.optBoolean("sidebarShowNotes", true),
                        showCompletedTasks = settingsJson.optBoolean("sidebarShowCompletedTasks", true),
                        showMoonPhases = settingsJson.optBoolean("sidebarShowMoonPhases", true),
                        showCommemorative = settingsJson.optBoolean("sidebarShowCommemorative", true)
                    )
                    settingsRepository.saveSidebarFilterVisibility(sidebarFilter)
                    
                    // Auto Backup
                    val autoBackup = AutoBackupSettings(
                        enabled = settingsJson.optBoolean("autoBackupEnabled", false),
                        frequency = BackupFrequency.valueOf(settingsJson.optString("autoBackupFrequency", BackupFrequency.DAILY.name)),
                        hour = settingsJson.optInt("autoBackupHour", 2),
                        minute = settingsJson.optInt("autoBackupMinute", 0),
                        backupType = BackupType.valueOf(settingsJson.optString("autoBackupType", BackupType.LOCAL.name))
                    )
                    settingsRepository.saveAutoBackupSettings(autoBackup)
                    
                    val crashlyticsEnabled = settingsJson.optBoolean("crashlyticsEnabled", false)
                    settingsRepository.setCrashlyticsEnabled(crashlyticsEnabled)
                    
                    val hasSeenMainOnboarding = settingsJson.optBoolean("hasSeenMainOnboarding", false)
                    settingsRepository.setHasSeenMainOnboarding(hasSeenMainOnboarding)
                    
                    val backupDirectoryUri = settingsJson.optString("backupDirectoryUri", "")
                    if (backupDirectoryUri.isNotEmpty()) {
                        settingsRepository.saveBackupDirectoryUri(backupDirectoryUri)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erro ao restaurar configurações do backup: ${e.message}", e)
                }
            }

            // Restaurar calendários JSON
            val jsonCalendarsArray = json.optJSONArray("jsonCalendars")
            if (jsonCalendarsArray != null) {
                try {
                    val restoredCalendars = mutableListOf<JsonCalendar>()
                    for (i in 0 until jsonCalendarsArray.length()) {
                        val calendarJson = jsonCalendarsArray.getJSONObject(i)
                        val calendar = JsonCalendar(
                            id = calendarJson.getString("id"),
                            title = calendarJson.getString("title"),
                            color = calendarJson.getString("color").toColor(),
                            fileName = calendarJson.getString("fileName"),
                            importDate = calendarJson.getLong("importDate"),
                            isVisible = calendarJson.optBoolean("isVisible", true)
                        )
                        restoredCalendars.add(calendar)
                    }
                    jsonCalendarRepository.overwriteAllJsonCalendars(restoredCalendars)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erro ao restaurar calendários JSON do backup: ${e.message}", e)
                }
            }

            // Restaurar alarmes
            val restoredAlarms = mutableListOf<com.mss.thebigcalendar.data.model.AlarmSettings>()
            val alarmsArray = json.optJSONArray("alarms")
            if (alarmsArray != null) {
                try {
                    for (i in 0 until alarmsArray.length()) {
                        val alarmJson = alarmsArray.getJSONObject(i)
                        val alarm = parseAlarmFromJson(alarmJson)
                        restoredAlarms.add(alarm)
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Erro ao restaurar alarmes do backup: ${e.message}", e)
                }
            }

            val result = RestoreResult(
                activities = restoredActivities,
                deletedActivities = restoredDeletedActivities,
                completedActivities = restoredCompletedActivities,
                backupFileName = DocumentFile.fromSingleUri(context, backupUri)?.name ?: "Unknown",
                backupCreatedAt = json.optString("createdAt", ""),
                alarms = restoredAlarms
            )

            notificationService.showRestoreCompleteNotification(result.backupFileName)
            Result.success(result)

        } catch (e: Exception) {
            notificationService.showRestoreFailedNotification(e.message ?: "Erro desconhecido")
            Result.failure(e)
        }
    }

    /**
     * Deleta um arquivo de backup usando SAF.
     */
    suspend fun deleteBackupFile(backupUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val documentFile = DocumentFile.fromSingleUri(context, backupUri)
            if (documentFile != null && documentFile.canWrite()) {
                if (documentFile.delete()) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Falha ao deletar o arquivo de backup."))
                }
            } else {
                Result.failure(Exception("Não foi possível obter permissão para deletar o arquivo."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parseia uma atividade a partir do JSON
     */
    private fun parseActivityFromJson(activityJson: JSONObject): com.mss.thebigcalendar.data.model.Activity {
        val startTime = activityJson.optString("startTime").takeIf { it.isNotEmpty() }?.let {
            try {
                java.time.LocalTime.parse(it)
            } catch (e: Exception) {
                null
            }
        }
        
        val endTime = activityJson.optString("endTime").takeIf { it.isNotEmpty() }?.let {
            try {
                java.time.LocalTime.parse(it)
            } catch (e: Exception) {
                null
            }
        }
        
        val activityType = try {
            com.mss.thebigcalendar.data.model.ActivityType.valueOf(activityJson.getString("activityType"))
        } catch (e: Exception) {
            com.mss.thebigcalendar.data.model.ActivityType.EVENT
        }
        
        val visibility = try {
            com.mss.thebigcalendar.data.model.VisibilityLevel.valueOf(activityJson.optString("visibility", "LOW"))
        } catch (e: Exception) {
            com.mss.thebigcalendar.data.model.VisibilityLevel.LOW
        }
        
        // Parsear configurações de notificação
        val notificationSettings = try {
            val notificationJson = activityJson.optJSONObject("notificationSettings")
            if (notificationJson != null) {
                val notificationType = try {
                    com.mss.thebigcalendar.data.model.NotificationType.valueOf(
                        notificationJson.optString("notificationType", "FIFTEEN_MINUTES_BEFORE")
                    )
                } catch (e: Exception) {
                    com.mss.thebigcalendar.data.model.NotificationType.FIFTEEN_MINUTES_BEFORE
                }
                
                val notificationTime = notificationJson.optString("notificationTime").takeIf { it.isNotEmpty() }?.let {
                    try {
                        java.time.LocalTime.parse(it)
                    } catch (e: Exception) {
                        null
                    }
                }
                
                com.mss.thebigcalendar.data.model.NotificationSettings(
                    isEnabled = notificationJson.optBoolean("isEnabled", true),
                    notificationType = notificationType,
                    customMinutesBefore = notificationJson.optInt("customMinutesBefore", 15),
                    notificationTime = notificationTime
                )
            } else {
                com.mss.thebigcalendar.data.model.NotificationSettings(
                    isEnabled = false,
                    notificationTime = null,
                    notificationType = com.mss.thebigcalendar.data.model.NotificationType.BEFORE_ACTIVITY,
                    customMinutesBefore = null
                )
            }
        } catch (e: Exception) {
            com.mss.thebigcalendar.data.model.NotificationSettings(
                isEnabled = false,
                notificationTime = null,
                notificationType = com.mss.thebigcalendar.data.model.NotificationType.BEFORE_ACTIVITY,
                customMinutesBefore = null
            )
        }
        
        return com.mss.thebigcalendar.data.model.Activity(
            id = activityJson.getString("id"),
            title = activityJson.getString("title"),
            description = activityJson.optString("description").takeIf { it.isNotEmpty() },
            date = activityJson.getString("date"),
            startTime = startTime,
            endTime = endTime,
            isAllDay = activityJson.optBoolean("isAllDay", false),
            location = activityJson.optString("location").takeIf { it.isNotEmpty() },
            categoryColor = activityJson.optString("categoryColor", "#3B82F6"),
            activityType = activityType,
            recurrenceRule = activityJson.optString("recurrenceRule").takeIf { it.isNotEmpty() },
            notificationSettings = notificationSettings,
            isCompleted = activityJson.optBoolean("isCompleted", false),
            visibility = visibility,
            showInCalendar = activityJson.optBoolean("showInCalendar", true), // Por padrão, mostrar no calendário
            isFromGoogle = activityJson.optBoolean("isFromGoogle", false),
            excludedDates = parseStringArray(activityJson.optJSONArray("excludedDates")),
            excludedInstances = parseStringArray(activityJson.optJSONArray("excludedInstances")),
            wikipediaLink = activityJson.optString("wikipediaLink").takeIf { it.isNotEmpty() } // Preservar link da Wikipedia se existir
        )
    }
    
    /**
     * Parseia um array de strings do JSON
     */
    private fun parseStringArray(jsonArray: JSONArray?): List<String> {
        if (jsonArray == null) return emptyList()
        
        val result = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            try {
                val value = jsonArray.getString(i)
                if (value.isNotEmpty()) {
                    result.add(value)
                }
            } catch (e: Exception) {
                // Ignorar valores inválidos
            }
        }
        return result
    }
    
    /**
     * Parseia um item da lixeira a partir do JSON
     */
    private fun parseDeletedActivityFromJson(deletedJson: JSONObject): com.mss.thebigcalendar.data.model.DeletedActivity {
        val originalActivityJson = deletedJson.getJSONObject("originalActivity")
        val originalActivity = parseActivityFromJson(originalActivityJson)
        
        return com.mss.thebigcalendar.data.model.DeletedActivity(
            id = deletedJson.getString("id"),
            originalActivity = originalActivity,
            deletedAt = deletedJson.optString("deletedAt").let { dateString ->
                try {
                    java.time.LocalDateTime.parse(dateString)
                } catch (e: Exception) {
                    java.time.LocalDateTime.now()
                }
            },
            deletedBy = deletedJson.optString("deletedBy", "Sistema")
        )
    }

    /**
     * Parseia um alarme a partir do JSON
     */
    private fun parseAlarmFromJson(alarmJson: JSONObject): com.mss.thebigcalendar.data.model.AlarmSettings {
        val repeatDaysArray = alarmJson.optJSONArray("repeatDays")
        val repeatDays = mutableSetOf<String>()
        if (repeatDaysArray != null) {
            for (i in 0 until repeatDaysArray.length()) {
                repeatDays.add(repeatDaysArray.getString(i))
            }
        }
        
        return com.mss.thebigcalendar.data.model.AlarmSettings(
            id = alarmJson.getString("id"),
            label = alarmJson.getString("label"),
            time = java.time.LocalTime.parse(alarmJson.getString("time")),
            isEnabled = alarmJson.getBoolean("isEnabled"),
            repeatDays = repeatDays,
            soundEnabled = alarmJson.optBoolean("soundEnabled", true),
            vibrationEnabled = alarmJson.optBoolean("vibrationEnabled", true),
            snoozeMinutes = alarmJson.optInt("snoozeMinutes", 5),
            createdAt = alarmJson.optLong("createdAt", System.currentTimeMillis()),
            lastModified = alarmJson.optLong("lastModified", System.currentTimeMillis()),
            skippedDate = alarmJson.optString("skippedDate", "").takeIf { it.isNotEmpty() && it != "null" }
        )
    }
}

/**
 * Classe para armazenar informações sobre um arquivo de backup
 */
data class BackupInfo(
    val fileName: String,
    val uri: String,
    val fileSize: Long,
    val createdAt: String,
    val totalActivities: Int,
    val totalDeletedActivities: Int,
    val totalCompletedActivities: Int,
    val backupVersion: String
)

/**
 * Classe para armazenar o resultado da restauração
 */
data class RestoreResult(
    val activities: List<com.mss.thebigcalendar.data.model.Activity>,
    val deletedActivities: List<com.mss.thebigcalendar.data.model.DeletedActivity>,
    val completedActivities: List<com.mss.thebigcalendar.data.model.Activity>,
    val backupFileName: String,
    val backupCreatedAt: String,
    val alarms: List<com.mss.thebigcalendar.data.model.AlarmSettings> = emptyList()
)
