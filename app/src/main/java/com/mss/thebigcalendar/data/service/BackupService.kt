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
            val isEncrypted = settingsRepository.isEncryptionEnabled.first()
            val password = settingsRepository.encryptionPassword.first()
            val finalBackupData = if (isEncrypted && password.isNotEmpty()) {
                com.mss.thebigcalendar.crypto.CryptoHelper.encrypt(backupData, password)
            } else {
                backupData
            }
            tempFile.writeText(finalBackupData, Charsets.UTF_8)

            val appProperties = mapOf(
                "totalActivities" to activities.size.toString(),
                "totalDeletedActivities" to deletedActivities.size.toString(),
                "totalCompletedActivities" to completedActivities.size.toString()
            )

            val driveService = getGoogleDriveService(account)
            val uploadedFile = driveService.uploadBackupFile(tempFile, appProperties)

            if (uploadedFile != null) {
                try {
                    pruneCloudBackups(account)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
            val files = driveService.getBackupFiles() ?: emptyList()
            val backupFiles = files.filter { it.name?.startsWith(BACKUP_FILE_PREFIX) == true }
            Result.success(backupFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloudBackup(account: GoogleSignInAccount, fileId: String, fileName: String, providedPassword: String? = null): Result<RestoreResult> = withContext(Dispatchers.IO) {
        notificationService.showRestoreInProgressNotification()
        try {
            val driveService = getGoogleDriveService(account)
            val tempFile = File.createTempFile("restore_", ".json", context.cacheDir)

            android.util.Log.d("BackupService", "📥 Baixando arquivo do Google Drive...")
            driveService.downloadBackupFile(fileId, tempFile)

            val fileContent = tempFile.readText(Charsets.UTF_8)
            android.util.Log.d("BackupService", "📄 Conteúdo do arquivo baixado (tamanho=${fileContent.length}): $fileContent")

            val result = restoreFromBackup(Uri.fromFile(tempFile), providedPassword)

            tempFile.delete()

            if (result.isSuccess) {
                notificationService.showRestoreCompleteNotification(fileName)
            } else {
                android.util.Log.e("BackupService", "❌ Falha na restauração do backup", result.exceptionOrNull())
                notificationService.showRestoreFailedNotification(result.exceptionOrNull()?.message ?: "Erro desconhecido")
            }

            result
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "❌ Erro durante o processo de restore em nuvem", e)
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

            val isEncrypted = settingsRepository.isEncryptionEnabled.first()
            val password = settingsRepository.encryptionPassword.first()
            val finalBackupData = if (isEncrypted && password.isNotEmpty()) {
                com.mss.thebigcalendar.crypto.CryptoHelper.encrypt(backupData, password)
            } else {
                backupData
            }

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
                    writer.write(finalBackupData)
                }
            }

            try {
                pruneLocalBackups(directoryUri)
            } catch (e: Exception) {
                e.printStackTrace()
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

            val isEncrypted = com.mss.thebigcalendar.crypto.CryptoHelper.isEncrypted(content)
            val json = if (isEncrypted) {
                val password = settingsRepository.encryptionPassword.first()
                if (password.isNotEmpty()) {
                    try {
                        val decrypted = com.mss.thebigcalendar.crypto.CryptoHelper.decrypt(content, password)
                        JSONObject(decrypted)
                    } catch (e: Exception) {
                        JSONObject(content)
                    }
                } else {
                    JSONObject(content)
                }
            } else {
                JSONObject(content)
            }

            val info = BackupInfo(
                fileName = backupFile.name ?: "Unknown",
                uri = backupFile.uri.toString(),
                fileSize = backupFile.length(),
                createdAt = json.optString("createdAt", ""),
                totalActivities = json.optInt("totalActivities", 0),
                totalDeletedActivities = json.optInt("totalDeletedActivities", 0),
                totalCompletedActivities = json.optInt("totalCompletedActivities", 0),
                backupVersion = json.optString("backupVersion", "1.0"),
                isEncrypted = isEncrypted
            )

            Result.success(info)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restaura dados de um arquivo de backup usando SAF.
     */
    suspend fun restoreFromBackup(backupUri: Uri, providedPassword: String? = null): Result<RestoreResult> = withContext(Dispatchers.IO) {
        notificationService.showRestoreInProgressNotification()
        try {
            val content = context.contentResolver.openInputStream(backupUri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: run {
                val errorMessage = "Não foi possível ler o arquivo de backup para restauração."
                notificationService.showRestoreFailedNotification(errorMessage)
                return@withContext Result.failure(Exception(errorMessage))
            }

            val finalContent = if (com.mss.thebigcalendar.crypto.CryptoHelper.isEncrypted(content)) {
                val passwordToUse = providedPassword ?: settingsRepository.encryptionPassword.first()
                if (passwordToUse.isEmpty()) {
                    val error = com.mss.thebigcalendar.crypto.DecryptionRequiredException("Este arquivo de backup está criptografado. Uma senha é necessária.")
                    notificationService.showRestoreFailedNotification("Backup criptografado. Senha necessária.")
                    return@withContext Result.failure(error)
                }
                try {
                    com.mss.thebigcalendar.crypto.CryptoHelper.decrypt(content, passwordToUse)
                } catch (e: Exception) {
                    val error = com.mss.thebigcalendar.crypto.DecryptionFailedException("Senha incorreta ou erro ao descriptografar.")
                    notificationService.showRestoreFailedNotification("Senha do backup incorreta.")
                    return@withContext Result.failure(error)
                }
            } else {
                content
            }

            val json = JSONObject(finalContent)

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

    suspend fun pruneLocalBackups(directoryUri: Uri) = withContext(Dispatchers.IO) {
        val limit = settingsRepository.maxLocalBackups.first()
        if (limit <= 0) return@withContext

        val files = listBackupFiles(directoryUri)
        val backupFiles = files.filter { it.isFile && it.name?.startsWith(BACKUP_FILE_PREFIX) == true }
        if (backupFiles.size > limit) {
            val sorted = backupFiles.sortedBy { it.lastModified() }
            val toDelete = sorted.size - limit
            for (i in 0 until toDelete) {
                sorted[i].delete()
            }
        }
    }

    suspend fun pruneCloudBackups(account: GoogleSignInAccount) = withContext(Dispatchers.IO) {
        val limit = settingsRepository.maxCloudBackups.first()
        if (limit <= 0) return@withContext

        val driveService = getGoogleDriveService(account)
        val files = driveService.getBackupFiles() ?: emptyList()
        val backupFiles = files.filter { it.name?.startsWith(BACKUP_FILE_PREFIX) == true }
        if (backupFiles.size > limit) {
            val sorted = backupFiles.sortedBy { it.createdTime?.value ?: 0L }
            val toDelete = sorted.size - limit
            for (i in 0 until toDelete) {
                try {
                    driveService.deleteBackupFile(sorted[i].id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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
            description = activityJson.optString("description").let { if (it == "null" || it.isEmpty()) "" else it },
            date = activityJson.getString("date"),
            startTime = startTime,
            endTime = endTime,
            isAllDay = activityJson.optBoolean("isAllDay", false),
            location = activityJson.optString("location").takeIf { it.isNotEmpty() && it != "null" },
            categoryColor = activityJson.optString("categoryColor", "#3B82F6"),
            activityType = activityType,
            recurrenceRule = activityJson.optString("recurrenceRule").takeIf { it.isNotEmpty() && it != "null" },
            notificationSettings = notificationSettings,
            isCompleted = activityJson.optBoolean("isCompleted", false),
            visibility = visibility,
            showInCalendar = activityJson.optBoolean("showInCalendar", true), // Por padrão, mostrar no calendário
            isFromGoogle = activityJson.optBoolean("isFromGoogle", false),
            excludedDates = parseStringArray(activityJson.optJSONArray("excludedDates")),
            excludedInstances = parseStringArray(activityJson.optJSONArray("excludedInstances")),
            wikipediaLink = activityJson.optString("wikipediaLink").takeIf { it.isNotEmpty() && it != "null" }, // Preservar link da Wikipedia se existir
            lastModified = activityJson.optLong("lastModified", 0L)
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

    private fun serializeActivityToJson(activity: Activity): JSONObject {
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
        
        val excludedDatesArray = JSONArray()
        activity.excludedDates.forEach { excludedDatesArray.put(it) }
        activityJson.put("excludedDates", excludedDatesArray)
        
        val excludedInstancesArray = JSONArray()
        activity.excludedInstances.forEach { excludedInstancesArray.put(it) }
        activityJson.put("excludedInstances", excludedInstancesArray)
        
        activityJson.put("wikipediaLink", activity.wikipediaLink ?: "")
        activityJson.put("lastModified", activity.lastModified)
        
        val notificationJson = JSONObject()
        notificationJson.put("isEnabled", activity.notificationSettings.isEnabled)
        notificationJson.put("notificationType", activity.notificationSettings.notificationType.name)
        notificationJson.put("customMinutesBefore", activity.notificationSettings.customMinutesBefore)
        notificationJson.put("notificationTime", activity.notificationSettings.notificationTime?.toString() ?: "")
        activityJson.put("notificationSettings", notificationJson)
        
        return activityJson
    }

    suspend fun syncActivitiesWithCloud(account: GoogleSignInAccount, providedPassword: String? = null, isDisablingEncryption: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val driveService = getGoogleDriveService(account)
            val drive = driveService.drive
            
            // 1. Procurar arquivo TBCalendar_Sync_Data.json
            val resultList = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'TBCalendar_Sync_Data.json'")
                .setFields("files(id, name)")
                .execute()
            val files = resultList.files ?: emptyList()
            
            var remoteActivities = emptyList<Activity>()
            var remoteCompleted = emptyList<Activity>()
            var remoteDeletedIds = emptySet<String>()
            var remoteDevicesJsonArray = JSONArray()
            var fileId: String? = null
            
            if (files.isNotEmpty()) {
                val foundFile = files[0]
                fileId = foundFile.id
                val tempFile = File.createTempFile("temp_sync_download", ".json", context.cacheDir)
                try {
                    drive.files().get(fileId).executeMediaAndDownloadTo(tempFile.outputStream())
                    var content = tempFile.readText(Charsets.UTF_8)
                    if (content.isNotEmpty()) {
                        if (com.mss.thebigcalendar.crypto.CryptoHelper.isEncrypted(content)) {
                            val passwordToUse = providedPassword ?: settingsRepository.encryptionPassword.first()
                            if (passwordToUse.isEmpty()) {
                                throw com.mss.thebigcalendar.crypto.DecryptionRequiredException("Sincronização em nuvem está criptografada, mas nenhuma senha está configurada localmente.")
                            }
                            try {
                                content = com.mss.thebigcalendar.crypto.CryptoHelper.decrypt(content, passwordToUse)
                                if (!isDisablingEncryption) {
                                    settingsRepository.saveEncryptionSettings(true, passwordToUse)
                                }
                            } catch (e: Exception) {
                                if (!isDisablingEncryption) {
                                    settingsRepository.saveEncryptionSettings(false, "")
                                }
                                throw com.mss.thebigcalendar.crypto.DecryptionFailedException("Senha incorreta ao descriptografar dados de sincronização em nuvem.")
                            }
                        } else {
                            if (!isDisablingEncryption) {
                                if (providedPassword != null && providedPassword.isNotEmpty()) {
                                    settingsRepository.saveEncryptionSettings(true, providedPassword)
                                } else {
                                    settingsRepository.saveEncryptionSettings(false, "")
                                }
                            }
                        }
                        val json = JSONObject(content)
                        
                        val devArray = json.optJSONArray("devices")
                        if (devArray != null) {
                            remoteDevicesJsonArray = devArray
                        }

                        // Parsear atividades remotas
                        val parsedActs = mutableListOf<Activity>()
                        val actsArray = json.optJSONArray("activities")
                        if (actsArray != null) {
                            for (i in 0 until actsArray.length()) {
                                try {
                                    parsedActs.add(parseActivityFromJson(actsArray.getJSONObject(i)))
                                } catch (e: Exception) {
                                    // Ignorar erros individuais
                                }
                            }
                        }
                        remoteActivities = parsedActs
                        
                        // Parsear atividades concluídas remotas
                        val parsedCompleted = mutableListOf<Activity>()
                        val completedArray = json.optJSONArray("completedActivities")
                        if (completedArray != null) {
                            for (i in 0 until completedArray.length()) {
                                try {
                                    parsedCompleted.add(parseActivityFromJson(completedArray.getJSONObject(i)))
                                } catch (e: Exception) {
                                    // Ignorar
                                }
                            }
                        }
                        remoteCompleted = parsedCompleted
                        
                        // Parsear itens excluídos remotos
                        val parsedDeletedIds = mutableSetOf<String>()
                        val deletedArray = json.optJSONArray("deletedActivities")
                        if (deletedArray != null) {
                            for (i in 0 until deletedArray.length()) {
                                try {
                                    val optObj = deletedArray.optJSONObject(i)
                                    if (optObj != null) {
                                        val origObj = optObj.optJSONObject("originalActivity")
                                        val origId = origObj?.optString("id") ?: optObj.optString("id")
                                        if (!origId.isNullOrEmpty()) {
                                            parsedDeletedIds.add(origId)
                                        }
                                    } else {
                                        val strId = deletedArray.getString(i)
                                        if (!strId.isNullOrEmpty()) {
                                            parsedDeletedIds.add(strId)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Ignorar
                                }
                            }
                        }
                        remoteDeletedIds = parsedDeletedIds
                    }
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            }
            
            // 2. Mesclar com dados locais
            val localActivities = activityRepository.activities.first()
            val localCompleted = completedActivityRepository.completedActivities.first()
            val localDeleted = deletedActivityRepository.deletedActivities.first()
            
            val localDeletedIds = localDeleted.map { it.originalActivity.id }.toSet()
            var finalDeletedIds = localDeletedIds + remoteDeletedIds

            // Separar locais personalizadas e importadas
            val localCustomActive = localActivities.filter { it.location?.startsWith("JSON_IMPORTED_") != true }
            val localJsonActive = localActivities.filter { it.location?.startsWith("JSON_IMPORTED_") == true }

            val localCustomCompleted = localCompleted.filter { it.location?.startsWith("JSON_IMPORTED_") != true }
            val localJsonCompleted = localCompleted.filter { it.location?.startsWith("JSON_IMPORTED_") == true }

            // Filtrar remotas para garantir que não tenham lixo importado
            val remoteCustomActive = remoteActivities.filter { it.location?.startsWith("JSON_IMPORTED_") != true }
            val remoteCustomCompleted = remoteCompleted.filter { it.location?.startsWith("JSON_IMPORTED_") != true }
            
            data class SyncSignature(
                val title: String,
                val date: String,
                val startTime: String?,
                val endTime: String?,
                val isAllDay: Boolean,
                val description: String,
                val location: String,
                val activityType: String,
                val recurrenceRule: String
            )
            
            fun Activity.toSyncSignature() = SyncSignature(
                title = title.trim().lowercase(),
                date = date,
                startTime = startTime?.toString(),
                endTime = endTime?.toString(),
                isAllDay = isAllDay,
                description = description?.trim()?.lowercase() ?: "",
                location = location?.trim()?.lowercase() ?: "",
                activityType = activityType.name,
                recurrenceRule = recurrenceRule?.trim()?.lowercase() ?: ""
            )

            val allCustomActivities = (localCustomActive + remoteCustomActive + localCustomCompleted + remoteCustomCompleted)
                .filter { it.id !in finalDeletedIds }
                .distinctBy { it.id }

            val mergedCustomCompleted = mutableListOf<Activity>()
            val mergedCustomActive = mutableListOf<Activity>()

            val completedSignatures = mutableSetOf<SyncSignature>()
            val activeSignatures = mutableSetOf<SyncSignature>()
            val discardedIds = mutableSetOf<String>()

            allCustomActivities.forEach { act ->
                val id = act.id
                val localActiveAct = localCustomActive.find { it.id == id }
                val remoteActiveAct = remoteCustomActive.find { it.id == id }
                val localCompletedAct = localCustomCompleted.find { it.id == id }
                val remoteCompletedAct = remoteCustomCompleted.find { it.id == id }

                val activeVersion = if (localActiveAct != null && remoteActiveAct != null) {
                    if (localActiveAct.lastModified >= remoteActiveAct.lastModified) localActiveAct else remoteActiveAct
                } else {
                    localActiveAct ?: remoteActiveAct
                }

                val completedVersion = if (localCompletedAct != null && remoteCompletedAct != null) {
                    if (localCompletedAct.lastModified >= remoteCompletedAct.lastModified) localCompletedAct else remoteCompletedAct
                } else {
                    localCompletedAct ?: remoteCompletedAct
                }

                if (activeVersion != null && completedVersion != null) {
                    if (activeVersion.lastModified > completedVersion.lastModified) {
                        val sig = activeVersion.toSyncSignature()
                        if (activeSignatures.add(sig)) {
                            mergedCustomActive.add(activeVersion)
                        } else {
                            discardedIds.add(id)
                        }
                    } else {
                        val sig = completedVersion.toSyncSignature()
                        if (completedSignatures.add(sig)) {
                            mergedCustomCompleted.add(completedVersion.copy(isCompleted = true, showInCalendar = false))
                        } else {
                            discardedIds.add(id)
                        }
                    }
                } else if (activeVersion != null) {
                    val sig = activeVersion.toSyncSignature()
                    if (activeSignatures.add(sig)) {
                        mergedCustomActive.add(activeVersion)
                    } else {
                        discardedIds.add(id)
                    }
                } else if (completedVersion != null) {
                    val sig = completedVersion.toSyncSignature()
                    if (completedSignatures.add(sig)) {
                        mergedCustomCompleted.add(completedVersion.copy(isCompleted = true, showInCalendar = false))
                    } else {
                        discardedIds.add(id)
                    }
                }
            }

            finalDeletedIds = finalDeletedIds + discardedIds
            
            val finalActive = mergedCustomActive + localJsonActive
            val finalCompleted = mergedCustomCompleted + localJsonCompleted
            
            // 3. Salvar no banco local
            activityRepository.clearAllActivities()
            activityRepository.saveAllActivities(finalActive)
            
            completedActivityRepository.clearAllCompletedActivities()
            completedActivityRepository.saveAllCompletedActivities(finalCompleted)
            
            // Atualizar banco de excluídos
            deletedActivityRepository.clearAllDeletedActivities()
            val updatedDeletedActivities = mutableListOf<DeletedActivity>()
            finalDeletedIds.forEach { deletedId ->
                val localFound = localDeleted.find { it.originalActivity.id == deletedId }
                if (localFound != null) {
                    updatedDeletedActivities.add(localFound)
                } else {
                    val origAct = (localCustomActive + remoteCustomActive + localCustomCompleted + remoteCustomCompleted)
                        .find { it.id == deletedId }
                        ?: Activity(
                            id = deletedId,
                            title = "Excluído",
                            description = null,
                            date = java.time.LocalDate.now().toString(),
                            startTime = null,
                            endTime = null,
                            isAllDay = true,
                            location = null,
                            categoryColor = "1",
                            activityType = com.mss.thebigcalendar.data.model.ActivityType.EVENT,
                            recurrenceRule = null,
                            notificationSettings = com.mss.thebigcalendar.data.model.NotificationSettings()
                        )
                    updatedDeletedActivities.add(
                        DeletedActivity(
                            id = java.util.UUID.randomUUID().toString(),
                            originalActivity = origAct,
                            deletedAt = LocalDateTime.now(),
                            deletedBy = "sync"
                        )
                    )
                }
            }
            deletedActivityRepository.saveAllDeletedActivities(updatedDeletedActivities)
            
            // 3b. Mesclar lista de dispositivos
            val mergedDevices = mutableListOf<JSONObject>()
            val currentPlatform = "android"
            val currentDeviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
            val currentTime = System.currentTimeMillis()
            val thirtyDaysAgo = currentTime - (30L * 24L * 60L * 60L * 1000L)

            for (i in 0 until remoteDevicesJsonArray.length()) {
                val devObj = remoteDevicesJsonArray.optJSONObject(i) ?: continue
                val p = devObj.optString("platform", "")
                val n = devObj.optString("deviceName", "")
                val t = devObj.optLong("lastSyncTime", 0L)
                
                if ((p == currentPlatform && n == currentDeviceName) || t < thirtyDaysAgo) {
                    continue
                }
                mergedDevices.add(devObj)
            }

            val currentDeviceJson = JSONObject().apply {
                put("platform", currentPlatform)
                put("deviceName", currentDeviceName)
                put("lastSyncTime", currentTime)
            }
            mergedDevices.add(currentDeviceJson)

            val updatedDevicesArray = JSONArray()
            mergedDevices.forEach { updatedDevicesArray.put(it) }
            settingsRepository.saveSyncedDevicesJson(updatedDevicesArray.toString())

            // 4. Upload do arquivo atualizado
            val syncJson = JSONObject().apply {
                put("backupVersion", "1.1")
                put("createdAt", LocalDateTime.now().toString())
                put("appVersion", "TheBigCalendar")
                
                val activitiesJsonArray = JSONArray()
                mergedCustomActive.forEach { activitiesJsonArray.put(serializeActivityToJson(it)) }
                put("activities", activitiesJsonArray)
                
                val completedJsonArray = JSONArray()
                mergedCustomCompleted.forEach { completedJsonArray.put(serializeActivityToJson(it)) }
                put("completedActivities", completedJsonArray)
                
                val deletedJsonArray = JSONArray()
                finalDeletedIds.forEach { deletedJsonArray.put(it) }
                put("deletedActivities", deletedJsonArray)
                
                put("devices", updatedDevicesArray)
            }
            
            val isEncrypted = if (isDisablingEncryption) false else settingsRepository.isEncryptionEnabled.first()
            val password = settingsRepository.encryptionPassword.first()
            val syncContent = if (isEncrypted && password.isNotEmpty()) {
                com.mss.thebigcalendar.crypto.CryptoHelper.encrypt(syncJson.toString(), password)
            } else {
                syncJson.toString()
            }
            
            val tempUploadFile = File.createTempFile("TBCalendar_Sync_Data", ".json", context.cacheDir)
            try {
                tempUploadFile.writeText(syncContent, Charsets.UTF_8)
                
                val mediaContent = com.google.api.client.http.FileContent("application/json", tempUploadFile)
                
                if (fileId != null) {
                    val updateMetadata = com.google.api.services.drive.model.File().apply {
                        name = "TBCalendar_Sync_Data.json"
                    }
                    drive.files().update(fileId, updateMetadata, mediaContent).execute()
                } else {
                    val createMetadata = com.google.api.services.drive.model.File().apply {
                        name = "TBCalendar_Sync_Data.json"
                        parents = listOf("appDataFolder")
                    }
                    drive.files().create(createMetadata, mediaContent).execute()
                }
            } finally {
                if (tempUploadFile.exists()) tempUploadFile.delete()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("BackupService", "Erro na sincronização automática em nuvem: ${e.message}", e)
            Result.failure(e)
        }
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
    val backupVersion: String,
    val isEncrypted: Boolean = false
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
