package com.mss.thebigcalendar.ui.viewmodel

import android.app.Application
import android.content.ContentValues.TAG
import android.content.Context
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.tasks.Task
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.CalendarDay
import com.mss.thebigcalendar.data.model.CalendarUiState
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.SearchResult
import com.mss.thebigcalendar.data.model.Theme
import com.mss.thebigcalendar.data.model.AppIconMode
import com.mss.thebigcalendar.data.model.ViewMode
import com.mss.thebigcalendar.data.model.JsonSchedule
import com.mss.thebigcalendar.data.model.toActivity
import com.mss.thebigcalendar.data.model.JsonCalendar
import com.mss.thebigcalendar.data.model.JsonHoliday
import com.mss.thebigcalendar.data.repository.JsonCalendarRepository

import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.data.repository.HolidayRepository
import com.mss.thebigcalendar.data.repository.SettingsRepository
import com.mss.thebigcalendar.service.GoogleAuthService
import com.mss.thebigcalendar.service.GoogleCalendarService
import com.mss.thebigcalendar.service.NotificationService
import com.mss.thebigcalendar.service.SearchService
import com.mss.thebigcalendar.service.RecurrenceService
import com.mss.thebigcalendar.data.repository.DeletedActivityRepository
import com.mss.thebigcalendar.data.repository.CompletedActivityRepository
import com.mss.thebigcalendar.data.repository.AlarmRepository
import com.mss.thebigcalendar.data.repository.SyncRepository
import com.mss.thebigcalendar.data.service.BackupService
import com.mss.thebigcalendar.data.service.BackupInfo
import com.mss.thebigcalendar.service.VisibilityService
import com.mss.thebigcalendar.data.model.GeminiAction
import com.mss.thebigcalendar.data.model.GeminiCommandResult
import com.mss.thebigcalendar.data.model.NotificationSettings
import com.mss.thebigcalendar.data.model.NotificationType
import com.mss.thebigcalendar.data.model.VisibilityLevel
import com.mss.thebigcalendar.service.GeminiService
import com.mss.thebigcalendar.service.GeminiExecutionResult
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import androidx.work.WorkManager
import androidx.work.PeriodicWorkRequestBuilder
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.work.Constraints
import androidx.work.NetworkType
import com.mss.thebigcalendar.service.ProgressiveSyncService
import com.mss.thebigcalendar.worker.GoogleCalendarSyncWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import com.mss.thebigcalendar.widget.EventListWidgetProvider
import android.widget.RemoteViews
import android.net.Uri
import android.app.PendingIntent
import com.mss.thebigcalendar.widget.EventListWidgetService
import com.mss.thebigcalendar.MainActivity
import com.mss.thebigcalendar.R
import com.google.firebase.crashlytics.FirebaseCrashlytics

class CalendarViewModel(application: Application) : AndroidViewModel(application) {

    // Repositories
    val settingsRepository = SettingsRepository(application)
    private val activityRepository = ActivityRepository(application)
    private val holidayRepository = HolidayRepository(application)
    private val deletedActivityRepository = DeletedActivityRepository(application)
    private val completedActivityRepository = CompletedActivityRepository(application)
    private val alarmRepository = AlarmRepository(application)
    private val jsonCalendarRepository = JsonCalendarRepository(application)
    private val syncRepository = SyncRepository(application)
    
    // Services
    private val googleAuthService = GoogleAuthService(application)
    private val googleCalendarService = GoogleCalendarService(application)
    private val progressiveSyncService = ProgressiveSyncService(application, googleCalendarService)
    private val searchService = SearchService()
    private val recurrenceService = RecurrenceService()
    private val backupService = BackupService(application, activityRepository, deletedActivityRepository, completedActivityRepository, alarmRepository)
    private val visibilityService = VisibilityService(application)
    private val pdfGenerationService = com.mss.thebigcalendar.data.service.PdfGenerationService(application)
    private val backupScheduler = com.mss.thebigcalendar.service.BackupScheduler(application)
    private val geminiService = GeminiService()
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady: Boolean = false

    // State Management
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    // ===== AUTO BACKUP FUNCTIONS =====

    fun saveAutoBackupSettings(settings: com.mss.thebigcalendar.data.repository.AutoBackupSettings) {
        viewModelScope.launch {
            settingsRepository.saveAutoBackupSettings(settings)
            backupScheduler.schedule(settings)
        }
    }

    // ===== CLOUD BACKUP FUNCTIONS =====

    fun listCloudBackups() {
        val account = _uiState.value.googleSignInAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isListingCloudBackups = true, cloudBackupError = null) }
            backupService.listCloudBackupFiles(account)
                .onSuccess { files ->
                    _uiState.update { it.copy(cloudBackupFiles = files, isListingCloudBackups = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        isListingCloudBackups = false, 
                        cloudBackupError = getApplication<Application>().getString(R.string.list_cloud_backups_failed, error.message ?: "")
                    ) }
                }
        }
    }

    fun createCloudBackup() {
        val account = _uiState.value.googleSignInAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, backupMessage = null, isBackupError = false) }
            backupService.createCloudBackup(account)
                .onSuccess {
                    _uiState.update { it.copy(
                        isBackingUp = false, 
                        backupMessage = getApplication<Application>().getString(R.string.cloud_backup_success),
                        isBackupError = false
                    ) }
                    listCloudBackups() // Refresh the list
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        isBackingUp = false, 
                        backupMessage = getApplication<Application>().getString(R.string.cloud_backup_failed, error.message ?: ""),
                        isBackupError = true
                    ) }
                }
        }
    }

    fun restoreFromCloudBackup(fileId: String, fileName: String, password: String? = null) {
        val account = _uiState.value.googleSignInAccount ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, restoreMessage = null, isRestoreError = false) }
            backupService.restoreFromCloudBackup(account, fileId, fileName, password)
                .onSuccess { restoreResult ->
                    // Cancelar alarmes existentes e limpar o repositório de alarmes
                    val notificationService = NotificationService(getApplication())
                    val alarmService = com.mss.thebigcalendar.service.AlarmService(
                        getApplication(),
                        alarmRepository,
                        notificationService
                    )
                    try {
                        val currentAlarms = alarmRepository.getAllAlarms()
                        currentAlarms.forEach { alarm ->
                            alarmService.cancelAlarm(alarm.id)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CalendarViewModel", "Erro ao cancelar alarmes existentes na restauração em nuvem: ${e.message}", e)
                    }
                    alarmRepository.clearAllAlarms()

                    // Logic to handle the restored data
                    activityRepository.clearAllActivities()
                    deletedActivityRepository.clearAllDeletedActivities()
                    completedActivityRepository.clearAllCompletedActivities()

                    activityRepository.saveAllActivities(restoreResult.activities)
                    deletedActivityRepository.saveAllDeletedActivities(restoreResult.deletedActivities)
                    completedActivityRepository.saveAllCompletedActivities(restoreResult.completedActivities)

                    // Salvar e agendar os novos alarmes
                    restoreResult.alarms.forEach { alarm ->
                        alarmRepository.saveAlarm(alarm)
                        if (alarm.isEnabled) {
                            alarmService.scheduleAlarm(alarm)
                        }
                    }

                    // Agendar notificações para as atividades
                    restoreResult.activities.forEach { activity -> notificationService.scheduleNotification(activity) }

                    _uiState.update { it.copy(
                        isRestoring = false, 
                        restoreMessage = getApplication<Application>().getString(R.string.restore_success, restoreResult.backupFileName),
                        isRestoreError = false,
                        showDecryptionDialog = false,
                        decryptionCloudFileId = null,
                        decryptionCloudFileName = null,
                        decryptionErrorMessage = null
                    ) }
                    loadData() // Reload all data
                }
                .onFailure { error ->
                    if (error is com.mss.thebigcalendar.crypto.DecryptionRequiredException || error is com.mss.thebigcalendar.crypto.DecryptionFailedException) {
                        _uiState.update { it.copy(
                            showDecryptionDialog = true,
                            decryptionCloudFileId = fileId,
                            decryptionCloudFileName = fileName,
                            decryptionErrorMessage = error.message,
                            isRestoring = false
                        ) }
                    } else {
                        _uiState.update { it.copy(
                            isRestoring = false, 
                            restoreMessage = getApplication<Application>().getString(R.string.restore_failed, error.message ?: ""),
                            isRestoreError = true
                        ) }
                    }
                }
        }
    }

    fun deleteCloudBackup(fileId: String) {
        val account = _uiState.value.googleSignInAccount ?: return
        viewModelScope.launch {
            backupService.deleteCloudBackup(account, fileId)
                .onSuccess {
                    listCloudBackups() // Refresh the list
                }
                .onFailure { error ->
                    _uiState.update { it.copy(
                        cloudBackupError = getApplication<Application>().getString(R.string.delete_cloud_backup_failed, error.message ?: "")
                    ) }
                }
        }
    }

    fun clearCloudBackupError() {
        _uiState.update { it.copy(cloudBackupError = null) }
    }
    
    // Cache System
    private var updateJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var cachedCalendarDays: List<CalendarDay>? = null
    private var lastUpdateParams: String? = null
    private var cachedBirthdays: Map<LocalDate, List<Activity>> = emptyMap()
    private var cachedNotes: Map<LocalDate, List<Activity>> = emptyMap()
    private var cachedTasks: Map<LocalDate, List<Activity>> = emptyMap()

    init {
        loadSettings()
        loadData()
        checkForExistingSignIn()
        // Garantir que o calendário inicie no mês atual
        onGoToToday()
        
        // Registrar broadcast receiver para atualizações de notificações
        registerNotificationBroadcastReceiver()

        // Observar mudanças no ano exibido para carregar os feriados nacionais dinamicamente
        viewModelScope.launch {
            var lastYear: Int? = null
            _uiState.collect { state ->
                val currentYear = state.displayedYearMonth.year
                if (currentYear != lastYear) {
                    lastYear = currentYear
                    loadHolidaysForYear(currentYear)
                }
            }
        }

        // Observar duplicidades
        viewModelScope.launch {
            activityRepository.activities.collect { allActs ->
                val duplicates = getDuplicateGroups(allActs)
                _uiState.update { it.copy(duplicateGroups = duplicates) }
            }
        }
    }

    fun setCalendarScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.setCalendarScale(scale)
            _uiState.update { it.copy(calendarScale = scale) }
        }
    }

    fun setHideOtherMonthDays(hide: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHideOtherMonthDays(hide)
            _uiState.update { it.copy(hideOtherMonthDays = hide) }
        }
    }

    fun setPureBlackTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPureBlackTheme(enabled)
            _uiState.update { it.copy(pureBlackTheme = enabled) }
        }
    }

    fun setPrimaryColor(colorHex: String) {
        viewModelScope.launch {
            settingsRepository.setPrimaryColor(colorHex)
            _uiState.update { it.copy(primaryColor = colorHex) }
        }
    }

    fun setUnfixHeadersOnScroll(unfix: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUnfixHeadersOnScroll(unfix)
            _uiState.update { it.copy(unfixHeadersOnScroll = unfix) }
        }
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCrashlyticsEnabled(enabled)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
            _uiState.update { it.copy(isCrashlyticsEnabled = enabled) }
        }
    }

    fun setEncryptionSettings(enabled: Boolean, password: String) {
        if (!enabled) {
            disableEncryption(password)
        } else {
            viewModelScope.launch {
                settingsRepository.saveEncryptionSettings(true, password)
                _uiState.update { it.copy(isEncryptionEnabled = true, encryptionPassword = password) }
                syncActivitiesWithCloud(password)
            }
        }
    }

    fun disableEncryption(password: String) {
        val account = _uiState.value.googleSignInAccount
        if (account == null) {
            viewModelScope.launch {
                settingsRepository.saveEncryptionSettings(false, "")
                _uiState.update { it.copy(isEncryptionEnabled = false, encryptionPassword = "") }
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true, syncErrorMessage = null) }
            backupService.syncActivitiesWithCloud(account, providedPassword = password, isDisablingEncryption = true)
                .onSuccess {
                    settingsRepository.saveEncryptionSettings(false, "")
                    _uiState.update { it.copy(
                        isSyncing = false,
                        isEncryptionEnabled = false,
                        encryptionPassword = "",
                        showDecryptionDialog = false,
                        decryptionErrorMessage = null,
                        lastGoogleSyncTime = System.currentTimeMillis()
                    ) }
                    loadData()
                    viewModelScope.launch {
                        delay(500)
                        notifyWidgetsDataChanged()
                    }
                }
                .onFailure { exception ->
                    Log.e("CalendarViewModel", "❌ Erro ao desativar criptografia", exception)
                    _uiState.update { it.copy(
                        isSyncing = false,
                        syncErrorMessage = getApplication<Application>().getString(R.string.disable_encryption_failed, exception.message ?: "")
                    ) }
                }
        }
    }

    fun saveMaxLocalBackups(max: Int) {
        viewModelScope.launch {
            settingsRepository.saveMaxLocalBackups(max)
            _uiState.update { it.copy(maxLocalBackups = max) }
            val directoryUriString = _uiState.value.backupDirectoryUri
            if (!directoryUriString.isNullOrBlank()) {
                try {
                    backupService.pruneLocalBackups(Uri.parse(directoryUriString))
                    loadBackupFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveMaxCloudBackups(max: Int) {
        viewModelScope.launch {
            settingsRepository.saveMaxCloudBackups(max)
            _uiState.update { it.copy(maxCloudBackups = max) }
            val account = _uiState.value.googleSignInAccount
            if (account != null) {
                try {
                    backupService.pruneCloudBackups(account)
                    listCloudBackups()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun openCalendarVisualizationSettings() {
        _uiState.update { it.copy(isCalendarVisualizationSettingsOpen = true, isSettingsScreenOpen = false) }
    }

    fun closeCalendarVisualizationSettings() {
        _uiState.update { it.copy(isCalendarVisualizationSettingsOpen = false, isSettingsScreenOpen = true) }
    }

    fun openSyncSettings() {
        _uiState.update { it.copy(isSyncScreenOpen = true, isSettingsScreenOpen = false) }
    }

    fun closeSyncSettings() {
        _uiState.update { it.copy(isSyncScreenOpen = false, isSettingsScreenOpen = true) }
    }

    fun onMainOnboardingComplete() {
        viewModelScope.launch {
            settingsRepository.setHasSeenMainOnboarding(true)
            _uiState.update { it.copy(hasSeenMainOnboarding = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Limpar job pendente quando o ViewModel for destruído
        updateJob?.cancel()
        // Limpar cache
        clearCalendarCache()
        // Desregistrar broadcast receiver
        try {
            getApplication<Application>().unregisterReceiver(notificationBroadcastReceiver)
        } catch (_: Exception) {
            // Ignorar erro se o receiver não estiver registrado
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
            // Ignorar erro ao liberar TextToSpeech
        }
    }
    
    private val notificationBroadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.mss.thebigcalendar.ACTIVITY_COMPLETED") {
                val activityId = intent.getStringExtra("activity_id")
                if (activityId != null) {
                    // Atualizar a UI
                    updateAllDateDependentUI()
                }
            }
        }
    }
    
    private fun registerNotificationBroadcastReceiver() {
        val filter = android.content.IntentFilter("com.mss.thebigcalendar.ACTIVITY_COMPLETED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(notificationBroadcastReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            getApplication<Application>().registerReceiver(notificationBroadcastReceiver, filter)
        }
    }

    /**
     * Gera uma chave única baseada nos parâmetros que afetam o calendário
     * A data selecionada não afeta o cache do calendário, apenas a exibição dos detalhes
     */
    private fun generateCalendarCacheKey(): String {
        val state = _uiState.value
        return buildString {
            append("${state.displayedYearMonth}_")
            // Removido selectedDate - não afeta o cache do calendário
            append("${state.filterOptions.showHolidays}_")
            append("${state.filterOptions.showEvents}_")
            append("${state.filterOptions.showTasks}_")
            append("${state.filterOptions.showNotes}_")
            append("${state.filterOptions.showBirthdays}_")
            append("${state.filterOptions.showCommemorative}_")
            append("${state.showCompletedActivities}_")
            append("${state.showMoonPhases}_")
            append("${state.activities.size}_")
            append("${state.completedActivities.size}_")
            append("${state.nationalHolidays.size}_")
            append("${state.commemorativeDates.size}_")
            append("${state.jsonHolidays.size}_")
            append("${state.jsonCalendars.size}")
        }
    }

    /**
     * Limpa o cache do calendário quando necessário
     * O cache deve ser limpo apenas quando:
     * - O mês/ano exibido muda
     * - As atividades/feriados mudam
     * - Os filtros mudam
     * - O ViewModel é destruído
     * NÃO deve ser limpo quando apenas a data selecionada muda
     */
    private fun clearCalendarCache() {
        cachedCalendarDays = null
        lastUpdateParams = null
    }
    
    private fun clearActivityCache() {
        cachedBirthdays = emptyMap()
        cachedNotes = emptyMap()
        cachedTasks = emptyMap()
    }

    private fun checkForExistingSignIn() {
        val account = googleAuthService.getLastSignedInAccount()
        if (account != null) {
            _uiState.update { it.copy(googleSignInAccount = account) }
            // Sincronização automática na inicialização: usar sincronização progressiva e respeitar o limite de 24 horas
            viewModelScope.launch {
                val lastSync = syncRepository.getLastSyncTime()
                val currentTime = System.currentTimeMillis()
                val timeSinceLastSync = currentTime - lastSync
                
                // Apenas sincronizar se passou mais de 24 horas (diário)
                if (timeSinceLastSync >= 24 * 60 * 60 * 1000) {
                    performProgressiveSync(account, forceFullSync = false)
                } else {
                    // Atualizar o timestamp em memória para refletir a última sincronização conhecida
                    _uiState.update { it.copy(lastGoogleSyncTime = lastSync) }
                }
            }
        }
    }
    


    fun onSignInClicked() {
        val signInIntent = googleAuthService.getSignInIntent()
        _uiState.update { it.copy(signInIntent = signInIntent, isLoggingIn = true) }
    }

    fun onSignInLaunched() {
        _uiState.update { it.copy(signInIntent = null) }
    }

    fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        val account = googleAuthService.handleSignInResult(task)
        val loginSuccess = account != null
        _uiState.update { it.copy(
            googleSignInAccount = account,
            isLoggingIn = false,
            loginMessage = if(loginSuccess) getApplication<Application>().getString(com.mss.thebigcalendar.R.string.login_success_message) else getApplication<Application>().getString(com.mss.thebigcalendar.R.string.login_failure_message)
        ) }
        if (loginSuccess) {
            // No primeiro login, sempre fazemos uma sincronização progressiva completa para garantir que todos os dados sejam importados
            performProgressiveSync(account!!, forceFullSync = true)
        } else {
            android.util.Log.w("CalendarViewModel", "Sign-in failed, not fetching events.")
        }
    }

    fun clearLoginMessage() {
        _uiState.update { it.copy(loginMessage = null) }
    }

    fun signOut() {
        googleAuthService.signOut {
            _uiState.update { it.copy(googleSignInAccount = null) }
            viewModelScope.launch {
                activityRepository.deleteAllActivitiesFromGoogle()
            }
        }
    }

    
    /**
     * Cria aniversários de exemplo para teste se nenhum for detectado automaticamente
     */
    private fun createSampleBirthdays() {
        viewModelScope.launch {
            val currentYear = LocalDate.now().year
            val sampleBirthdays = listOf(
                Activity(
                    id = "sample_birthday_1",
                    title = "João Silva - Aniversário",
                    description = "Aniversário do João Silva",
                    date = "$currentYear-03-15",
                    startTime = null,
                    endTime = null,
                    isAllDay = true,
                    location = null,
                    categoryColor = "#FF69B4",
                    activityType = ActivityType.BIRTHDAY,
                    recurrenceRule = "YEARLY",
                    showInCalendar = true,
                    isFromGoogle = false,
                    excludedDates = emptyList(),
                    wikipediaLink = null
                ),
                Activity(
                    id = "sample_birthday_2",
                    title = "Maria Santos - Aniversário",
                    description = "Aniversário da Maria Santos",
                    date = "$currentYear-07-22",
                    startTime = null,
                    endTime = null,
                    isAllDay = true,
                    location = null,
                    categoryColor = "#FF69B4",
                    activityType = ActivityType.BIRTHDAY,
                    recurrenceRule = "YEARLY",
                    showInCalendar = true,
                    isFromGoogle = false,
                    excludedDates = emptyList(),
                    wikipediaLink = null
                )
            )
            
            // Salvar os aniversários de exemplo
            activityRepository.saveAllActivities(sampleBirthdays)
            
            // Atualizar a UI
            updateAllDateDependentUI()
        }
    }


    private fun loadSettings() {
        viewModelScope.launch {
            settingsRepository.theme.collect { theme ->
                _uiState.update { it.copy(theme = theme) }
            }
        }
        viewModelScope.launch {
            settingsRepository.calendarScale.collect { scale ->
                _uiState.update { it.copy(calendarScale = scale) }
            }
        }
        viewModelScope.launch {
            settingsRepository.hideOtherMonthDays.collect { hide ->
                _uiState.update { it.copy(hideOtherMonthDays = hide) }
            }
        }
        viewModelScope.launch {
            settingsRepository.pureBlackTheme.collect { enabled ->
                _uiState.update { it.copy(pureBlackTheme = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.primaryColor.collect { color ->
                _uiState.update { it.copy(primaryColor = color) }
            }
        }
        viewModelScope.launch {
            settingsRepository.unfixHeadersOnScroll.collect { unfix ->
                _uiState.update { it.copy(unfixHeadersOnScroll = unfix) }
            }
        }
        viewModelScope.launch {
            settingsRepository.welcomeName.collect { welcomeName ->
                _uiState.update { it.copy(welcomeName = welcomeName) }
            }
        }
        viewModelScope.launch {
            settingsRepository.filterOptions.collect { filterOptions ->
                _uiState.update { it.copy(filterOptions = filterOptions) }
                updateAllDateDependentUI()
            }
        }
        viewModelScope.launch {
            settingsRepository.showMoonPhases.collect { showMoonPhases ->
                _uiState.update { it.copy(showMoonPhases = showMoonPhases) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showCompletedActivities.collect { showCompleted ->
                _uiState.update { it.copy(showCompletedActivities = showCompleted) }
                updateAllDateDependentUI()
            }
        }
        viewModelScope.launch {
            settingsRepository.animationType.collect { animationType ->
                _uiState.update { it.copy(animationType = animationType) }
            }
        }
        viewModelScope.launch {
            settingsRepository.language.collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        viewModelScope.launch {
            settingsRepository.autoBackupSettings.collect { settings ->
                _uiState.update { it.copy(autoBackupSettings = settings) }
            }
        }
        viewModelScope.launch {
            settingsRepository.sidebarFilterVisibility.collect { sidebarFilterVisibility ->
                _uiState.update { it.copy(sidebarFilterVisibility = sidebarFilterVisibility) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isCrashlyticsEnabled.collect { enabled ->
                _uiState.update { it.copy(isCrashlyticsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isEncryptionEnabled.collect { enabled ->
                _uiState.update { it.copy(isEncryptionEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.encryptionPassword.collect { password ->
                _uiState.update { it.copy(encryptionPassword = password) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backupDirectoryUri.collect { uri ->
                _uiState.update { it.copy(backupDirectoryUri = uri) }
                if (!uri.isNullOrBlank()) {
                    loadBackupFiles()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.hasSeenMainOnboarding.collect { seen ->
                _uiState.update { it.copy(hasSeenMainOnboarding = seen) }
            }
        }
        viewModelScope.launch {
            settingsRepository.maxLocalBackups.collect { max ->
                _uiState.update { it.copy(maxLocalBackups = max) }
            }
        }
        viewModelScope.launch {
            settingsRepository.maxCloudBackups.collect { max ->
                _uiState.update { it.copy(maxCloudBackups = max) }
            }
        }
        viewModelScope.launch {
            settingsRepository.appIconMode.collect { mode ->
                _uiState.update { it.copy(appIconMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.syncedDevicesJson.collect { json ->
                _uiState.update { it.copy(syncedDevices = parseSyncedDevices(json)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiApiKey.collect { key ->
                _uiState.update { it.copy(geminiApiKey = key) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiVoiceFeedback.collect { feedback ->
                _uiState.update { it.copy(geminiVoiceFeedback = feedback) }
            }
        }
        viewModelScope.launch {
            settingsRepository.geminiModel.collect { model ->
                _uiState.update { it.copy(geminiModel = model) }
            }
        }
        try {
            textToSpeech = TextToSpeech(getApplication()) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.getDefault())
                    isTtsReady = (result != TextToSpeech.LANG_MISSING_DATA &&
                                  result != TextToSpeech.LANG_NOT_SUPPORTED)
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Erro ao inicializar TextToSpeech", e)
        }
        viewModelScope.launch {
            // Observar o estado de login do Google e o nome de boas-vindas
            _uiState.collect { uiState ->
                val googleAccount = uiState.googleSignInAccount
                val currentWelcomeName = uiState.welcomeName

                if (googleAccount != null && currentWelcomeName.isBlank()) {
                    val firstName = googleAccount.displayName?.split(" ")?.firstOrNull()
                    if (!firstName.isNullOrBlank()) {
                        settingsRepository.saveWelcomeName(firstName)
                    }
                }
            }
        }
    }

    private fun parseSyncedDevices(jsonString: String): List<com.mss.thebigcalendar.data.model.SyncedDevice> {
        val list = mutableListOf<com.mss.thebigcalendar.data.model.SyncedDevice>()
        if (jsonString.isBlank()) return list
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                list.add(
                    com.mss.thebigcalendar.data.model.SyncedDevice(
                        platform = obj.optString("platform", ""),
                        deviceName = obj.optString("deviceName", ""),
                        lastSyncTime = obj.optLong("lastSyncTime", 0L)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }


    
    /**
     * Verifica e executa o rollover de tarefas não concluídas de dias anteriores para hoje.
     * Atualiza o cache, a UI e os widgets caso alguma tarefa tenha sido movida.
     */
    fun checkAndPerformRollover() {
        viewModelScope.launch {
            val rolledCount = com.mss.thebigcalendar.service.RolloverManager.performRollover(getApplication())
            if (rolledCount > 0) {
                clearCalendarCache()
                clearActivityCache()
                updateAllDateDependentUI()
                notifyWidgetsDataChanged()
            }
        }
    }

    private fun loadData() {
        // Primeiro limpar atividades JSON antigas
        viewModelScope.launch {
            cleanupOldJsonActivities()
        }
        
        // Executar rollover de tarefas não concluídas de dias anteriores
        checkAndPerformRollover()

        // Carregar apenas as atividades do mês atual
        loadActivitiesForCurrentMonth()
        
        // Carregar calendários JSON
        loadJsonCalendars()
        
        viewModelScope.launch {
            deletedActivityRepository.deletedActivities.collect { deletedActivities ->
                _uiState.update { it.copy(deletedActivities = deletedActivities) }
            }
        }
        
        viewModelScope.launch {
            completedActivityRepository.completedActivities.collect { completedActivities ->
                _uiState.update { it.copy(completedActivities = completedActivities) }
                // Limpar cache quando as atividades completadas mudam
                clearCalendarCache()
                // Usar debounce para evitar múltiplas atualizações rápidas
                updateAllDateDependentUI()
            }
        }

        
        // Aguardar tempo suficiente para garantir que a animação complete
        viewModelScope.launch {
            kotlinx.coroutines.delay(1700) // 1.7 segundos para garantir que a animação de 1.5s complete
            _uiState.update { it.copy(isCalendarLoaded = true) }
        }
    }

    /**
     * Pula a animação de carregamento quando o app já está em execução
     */
    fun skipLoadingAnimation() {
        _uiState.update { it.copy(isCalendarLoaded = true) }
    }

    /**
     * Carrega apenas as atividades do mês atual
     * Otimização para reduzir uso de memória e melhorar performance
     */
    private fun loadActivitiesForCurrentMonth() {
        val currentMonth = _uiState.value.displayedYearMonth
        
        viewModelScope.launch {
            activityRepository.getActivitiesForMonth(currentMonth).collect { activities ->
                _uiState.update { it.copy(activities = activities) }
                
                // Limpar cache quando as atividades mudam
                clearCalendarCache()
                clearActivityCache()
                // Usar debounce para evitar múltiplas atualizações rápidas
                updateAllDateDependentUI()
            }
        }
    }

    /**
     * Atualiza as atividades quando o mês muda
     */
    private fun updateActivitiesForNewMonth(newMonth: YearMonth) {
        viewModelScope.launch {
            activityRepository.getActivitiesForMonth(newMonth).collect { activities ->
                _uiState.update { it.copy(activities = activities) }
                
                // Limpar cache quando as atividades mudam
                clearCalendarCache()
                clearActivityCache()
                // Atualizar o calendário
                updateAllDateDependentUI()
            }
        }
    }



    private var loadedHolidaysYear: Int? = null

    private fun loadHolidaysForYear(year: Int) {
        if (loadedHolidaysYear == year) return
        loadedHolidaysYear = year
        viewModelScope.launch {
            try {
                val nationalHolidaysList = mutableListOf<Holiday>().apply {
                    addAll(holidayRepository.getNationalHolidays(year - 1))
                    addAll(holidayRepository.getNationalHolidays(year))
                    addAll(holidayRepository.getNationalHolidays(year + 1))
                }
                val commemorativeDatesList = mutableListOf<Holiday>().apply {
                    addAll(holidayRepository.getCommemorativeDates(year - 1))
                    addAll(holidayRepository.getCommemorativeDates(year))
                    addAll(holidayRepository.getCommemorativeDates(year + 1))
                }
                _uiState.update { currentState ->
                    currentState.copy(
                        nationalHolidays = nationalHolidaysList.associateBy { LocalDate.parse(it.date) },
                        commemorativeDates = commemorativeDatesList.associateBy { LocalDate.parse(it.date) }
                    )
                }
                clearCalendarCache()
                updateAllDateDependentUI()
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Erro ao carregar feriados para o ano $year", e)
            }
        }
    }
    


    private suspend fun updateCalendarDays() {
        val state = _uiState.value
        
        // Verificar se podemos usar o cache
        val currentCacheKey = generateCalendarCacheKey()
        if (cachedCalendarDays != null && lastUpdateParams == currentCacheKey) {
            return
        }
        
        // Cache miss - precisamos recalcular

        val firstDayOfMonth = state.displayedYearMonth.atDay(1)
        val daysFromPrevMonthOffset = (firstDayOfMonth.dayOfWeek.value % 7)
        val gridStartDate = firstDayOfMonth.minusDays(daysFromPrevMonthOffset.toLong())

        val newCalendarDays = withContext(Dispatchers.Default) {
            List(42) { i ->
                val date = gridStartDate.plusDays(i.toLong())
                
                // Coletar todas as atividades para este dia (incluindo repetitivas)
                val allActivitiesForThisDay = mutableListOf<Activity>()
                
                val tasksForThisDay = if (state.filterOptions.showTasks || state.filterOptions.showEvents || state.filterOptions.showNotes || state.filterOptions.showBirthdays || state.filterOptions.showCommemorative) {
                    
                    state.activities.forEach { activity ->
                        try {
                            // EXCLUIR atividades JSON importadas das tasks para evitar duplicação
                            val isJsonImported = activity.location?.startsWith("JSON_IMPORTED_") == true
                            if (isJsonImported) {
                                return@forEach // Pular atividades JSON importadas
                            }
                            
                            val activityDate = LocalDate.parse(activity.date)
                            val typeMatches = (state.filterOptions.showTasks && activity.activityType == ActivityType.TASK) ||
                                    (state.filterOptions.showEvents && activity.activityType == ActivityType.EVENT) ||
                                    (state.filterOptions.showNotes && activity.activityType == ActivityType.NOTE) ||
                                    (state.filterOptions.showBirthdays && activity.activityType == ActivityType.BIRTHDAY)
                            
                            if (typeMatches) {
                                // Para aniversários, verificar se é o mesmo dia e mês (ignorando o ano)
                                val dateMatches = if (activity.activityType == ActivityType.BIRTHDAY) {
                                    activityDate.month == date.month && activityDate.dayOfMonth == date.dayOfMonth
                                } else {
                                    activityDate.isEqual(date)
                                }
                                
                                if (dateMatches) {
                                    // Verificar se a atividade deve aparecer no calendário
                                    val shouldShowInCalendar = activity.showInCalendar
                                    
                                    // Para atividades recorrentes, verificar se esta data específica foi excluída
                                    val isExcluded = if (activity.recurrenceRule?.isNotEmpty() == true) {
                                        if (activity.recurrenceRule.startsWith("FREQ=HOURLY") == true) {
                                            // Para atividades HOURLY, verificar se a instância específica foi excluída
                                            val timeString = activity.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                                            val instanceId = "${activity.id}_${date}_${timeString}"
                                            activity.excludedInstances.contains(instanceId)
                                        } else {
                                            // Para outras atividades, verificar se a data foi excluída
                                            activity.excludedDates.contains(date.toString())
                                        }
                                    } else {
                                        false
                                    }
                                    
                                    if (shouldShowInCalendar && !isExcluded) {
                                        allActivitiesForThisDay.add(activity)
                                    }
                                }
                                
                                // Se a atividade é repetitiva, calcular se deve aparecer neste dia
                                if (activity.recurrenceRule?.isNotEmpty() == true && 
                                    activity.showInCalendar) {
                                    
                                    val recurringInstances = calculateRecurringInstancesForDate(activity, date)
                                    allActivitiesForThisDay.addAll(recurringInstances)
                                }
                            }
                        } catch (_: Exception) {
                            // Erro ao processar atividade - continuar com outras atividades
                        }
                    }
                    
                    // Adicionar tarefas finalizadas se a opção estiver ativada
                    if (state.showCompletedActivities) {
                        state.completedActivities.forEach { completedActivity ->
                            try {
                                val activityDate = LocalDate.parse(completedActivity.date)
                                val dateMatches = activityDate.isEqual(date)
                                
                                if (dateMatches) {
                                    allActivitiesForThisDay.add(completedActivity)
                                }
                            } catch (e: Exception) {
                                // Erro ao processar tarefa finalizada - continuar com outras
                            }
                        }
                    }
                    
                    // Adicionar data comemorativa se aplicável
                    val commemorativeDate = state.commemorativeDates[date]
                    if (state.filterOptions.showCommemorative && commemorativeDate != null) {
                        allActivitiesForThisDay.add(
                            Activity(
                                id = "commemorative_${commemorativeDate.name}_${date}",
                                title = commemorativeDate.name,
                                description = null,
                                date = date.toString(),
                                startTime = null,
                                endTime = null,
                                isAllDay = true,
                                location = null,
                                categoryColor = "#FF9800",
                                activityType = ActivityType.COMMEMORATIVE,
                                recurrenceRule = null
                            )
                        )
                    }
                    
                    // Incluir tarefas finalizadas na lista final se a opção estiver ativada
                    val finalTasksList = if (state.showCompletedActivities) {
                        allActivitiesForThisDay.sortedWith(compareByDescending<Activity> { it.categoryColor.toIntOrNull()
                            ?: 0 }.thenBy { it.startTime ?: LocalTime.MIN })
                    } else {
                        // Filtrar apenas atividades não finalizadas
                        allActivitiesForThisDay.filter { !it.isCompleted }.sortedWith(compareByDescending<Activity> { it.categoryColor.toIntOrNull()
                            ?: 0 }.thenBy { it.startTime ?: LocalTime.MIN })
                    }
                    
                    finalTasksList
                } else {
                    emptyList()
                }

                val holidayForThisDay = if (state.filterOptions.showHolidays) state.nationalHolidays[date] else null
                // Buscar agendamentos JSON para este dia
                val monthDay = date.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
                val jsonHolidaysForThisDay = state.jsonHolidays[monthDay] ?: emptyList()

                CalendarDay(
                    date = date,
                    isCurrentMonth = date.month == state.displayedYearMonth.month,
                    isSelected = date.isEqual(state.selectedDate),
                    isToday = date.isEqual(LocalDate.now()),
                    tasks = tasksForThisDay,
                    holiday = holidayForThisDay,
                    jsonHolidays = jsonHolidaysForThisDay,
                    isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY,
                    isNationalHoliday = holidayForThisDay?.type == com.mss.thebigcalendar.data.model.HolidayType.NATIONAL,
                    isJsonHolidayDay = jsonHolidaysForThisDay.isNotEmpty()
                )
            }
        }
        
        // Salvar no cache
        cachedCalendarDays = newCalendarDays
        lastUpdateParams = currentCacheKey
        
        _uiState.update { it.copy(calendarDays = newCalendarDays) }
    }

    private fun updateTasksForSelectedDate() {
        val state = _uiState.value
        val isDifferentMonth = state.selectedDate.month != state.displayedYearMonth.month ||
                state.selectedDate.year != state.displayedYearMonth.year
        
        if (isDifferentMonth) {
            _uiState.update { 
                it.copy(
                    tasksForSelectedDate = emptyList(),
                    birthdaysForSelectedDate = emptyList(),
                    notesForSelectedDate = emptyList()
                ) 
            }
            updateJsonCalendarActivitiesForSelectedDate()
            return
        }
        
        // Separar aniversários das outras atividades usando cache
        val birthdays = if (state.filterOptions.showBirthdays) {
            cachedBirthdays[state.selectedDate] ?: run {
                val filtered = state.activities.filter { activity ->
                    try {
                        if (activity.activityType == ActivityType.BIRTHDAY) {
                            val activityDate = LocalDate.parse(activity.date)
                            // Para aniversários, verificar se é o mesmo dia e mês (ignorando o ano)
                            activityDate.month == state.selectedDate.month && activityDate.dayOfMonth == state.selectedDate.dayOfMonth
                        } else {
                            false
                        }
                    } catch (_: Exception) {
                        false
                    }
                }.sortedBy { it.title }
                
                // Atualizar cache
                cachedBirthdays = cachedBirthdays + (state.selectedDate to filtered)
                filtered
            }
        } else {
            emptyList()
        }
        
        // Separar notas das outras atividades usando cache
        val notes = if (state.filterOptions.showNotes) {
            cachedNotes[state.selectedDate] ?: run {
                val filtered = state.activities.filter { activity ->
                    try {
                        if (activity.activityType == ActivityType.NOTE) {
                            val activityDate = LocalDate.parse(activity.date)
                            activityDate.isEqual(state.selectedDate)
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }.sortedBy { it.title }
                
                // Atualizar cache
                cachedNotes = cachedNotes + (state.selectedDate to filtered)
                filtered
            }
        } else {
            emptyList()
        }
        
        // Filtrar outras atividades (excluindo aniversários e notas)
        // Coletar todas as tarefas para o dia selecionado (incluindo repetitivas)
        val allTasksForSelectedDate = mutableListOf<Activity>()
        
        state.activities.forEach { activity ->
            try {
                if (activity.activityType == ActivityType.BIRTHDAY || activity.activityType == ActivityType.NOTE) {
                    // Pular aniversários e notas
                    return@forEach
                }
                
                val activityDate = LocalDate.parse(activity.date)
                val typeMatches = (state.filterOptions.showTasks && activity.activityType == ActivityType.TASK) ||
                        (state.filterOptions.showEvents && activity.activityType == ActivityType.EVENT)
                
                if (typeMatches) {
                    // Verificar se a atividade deve aparecer no calendário
                    // NOTA: Para a seção de agendamentos, não aplicamos o filtro showInCalendar
                    // pois queremos que todas as tarefas apareçam aqui, mesmo as que não são mostradas no calendário
                    
                    val dateMatches = activityDate.isEqual(state.selectedDate)
                    if (dateMatches) {
                        // Para atividades recorrentes, verificar se esta data específica foi excluída
                        val isExcluded = if (activity.recurrenceRule?.isNotEmpty() == true) {
                            if (activity.recurrenceRule?.startsWith("FREQ=HOURLY") == true) {
                                // Para atividades HOURLY, verificar se a instância específica foi excluída
                                val timeString = activity.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                                val instanceId = "${activity.id}_${state.selectedDate}_${timeString}"
                                activity.excludedInstances.contains(instanceId)
                            } else {
                                // Para outras atividades, verificar se a data foi excluída
                                activity.excludedDates.contains(state.selectedDate.toString())
                            }
                        } else {
                            false
                        }
                        
                        if (!isExcluded) {
                            allTasksForSelectedDate.add(activity)
                        }
                    }
                    
                    // Se a atividade é repetitiva, calcular se deve aparecer neste dia
                    if (activity.recurrenceRule?.isNotEmpty() == true) {
                        
                        val recurringInstances = calculateRecurringInstancesForDate(activity, state.selectedDate)
                        allTasksForSelectedDate.addAll(recurringInstances)
                        
                    }
                }
            } catch (_: Exception) {
                // Erro ao processar atividade - continuar com outras
            }
        }
        
        // Adicionar tarefas finalizadas para o dia selecionado
        state.completedActivities.forEach { completedActivity ->
            try {
                val activityDate = LocalDate.parse(completedActivity.date)
                val dateMatches = activityDate.isEqual(state.selectedDate)
                
                if (dateMatches) {
                    if (allTasksForSelectedDate.none { it.id == completedActivity.id }) {
                        allTasksForSelectedDate.add(completedActivity)
                    }
                }
            } catch (e: Exception) {
                // Erro ao processar tarefa finalizada - continuar com outras
            }
        }

        // Filtrar atividades JSON importadas da seção "Agendamentos para..."
        val otherTasks = allTasksForSelectedDate
            .filter { activity -> 
                // Excluir atividades JSON importadas (marcadas com location começando com "JSON_IMPORTED_")
                val isJsonImported = activity.location?.startsWith("JSON_IMPORTED_") == true
                !isJsonImported
            }
            .sortedWith(
                compareBy<Activity> { it.isCompleted }
                    .thenBy { if (it.isCompleted) it.lastModified else 0L }
                    .thenByDescending { it.categoryColor.toIntOrNull() ?: 0 }
                    .thenBy { it.startTime ?: LocalTime.MIN }
            )
        
        // Atualizar todas as listas
        _uiState.update { 
            it.copy(
                tasksForSelectedDate = otherTasks,
                birthdaysForSelectedDate = birthdays,
                notesForSelectedDate = notes
            ) 
        }
        
        // Atualizar agendamentos JSON
        updateJsonCalendarActivitiesForSelectedDate()
    }

    private fun updateHolidaysForSelectedDate() {
        val state = _uiState.value
        val isDifferentMonth = state.selectedDate.month != state.displayedYearMonth.month ||
                state.selectedDate.year != state.displayedYearMonth.year
        val holidays = if (state.filterOptions.showHolidays && !isDifferentMonth) {
            state.nationalHolidays[state.selectedDate]?.let { listOf(it) } ?: emptyList()
        } else {
            emptyList()
        }
        _uiState.update { it.copy(holidaysForSelectedDate = holidays) }
    }



    private fun updateCommemorativeDatesForSelectedDate() {
        val state = _uiState.value
        val isDifferentMonth = state.selectedDate.month != state.displayedYearMonth.month ||
                state.selectedDate.year != state.displayedYearMonth.year
        val commemoratives = if (state.filterOptions.showCommemorative && !isDifferentMonth) {
            state.commemorativeDates[state.selectedDate]?.let { listOf(it) } ?: emptyList()
        } else {
            emptyList()
        }
        _uiState.update { it.copy(commemorativeDatesForSelectedDate = commemoratives) }
    }

    private fun updateAllDateDependentUI() {
        // Cancelar atualização anterior se ainda estiver pendente
        updateJob?.cancel()
        
        // Agendar nova atualização com debounce de 100ms
        updateJob = viewModelScope.launch {
            delay(100) // Debounce de 100ms
            updateCalendarDays()
            updateTasksForSelectedDate()
            updateJsonCalendarActivitiesForSelectedDate()
            updateHolidaysForSelectedDate()
            updateCommemorativeDatesForSelectedDate()
        }
    }

    /**
     * Atualiza apenas a propriedade isSelected dos dias do calendário
     * sem recriar todo o cache - otimização para mudança de data selecionada
     * 
     * Esta otimização permite que o dia selecionado seja marcado visualmente
     * sem invalidar o cache do calendário, mantendo a performance
     */
    private fun updateSelectedDateInCalendar() {
        val state = _uiState.value
        val updatedCalendarDays = state.calendarDays.map { day ->
            day.copy(isSelected = day.date.isEqual(state.selectedDate))
        }
        _uiState.update { it.copy(calendarDays = updatedCalendarDays) }
    }

    // ===== WIDGET NOTIFICATION FUNCTIONS =====
    
    /**
     * Notifica todos os widgets sobre mudanças nos dados
     */
    private fun notifyWidgetsDataChanged() {
        try {
            val context = getApplication<Application>()
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            // Notificar EventListWidgetProvider
            val componentName = ComponentName(context, com.mss.thebigcalendar.widget.EventListWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            if (appWidgetIds.isNotEmpty()) {
                Log.d("CalendarViewModel", "📱 Notificando ${appWidgetIds.size} widgets do tipo EventListWidgetProvider")
                
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    component = componentName
                }
                context.sendBroadcast(intent)
                
                // Forçar atualização do RemoteViewsFactory
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.event_list_view)
            }
            // Trigger automatic sync with the cloud
            triggerCloudSync()
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "❌ Erro ao notificar widgets", e)
        }
    }

    // ===== CALENDAR NAVIGATION FUNCTIONS =====
    
    fun onPreviousMonth() {
        val newMonth = _uiState.value.displayedYearMonth.minusMonths(1)
        _uiState.update { it.copy(displayedYearMonth = newMonth) }
        updateActivitiesForNewMonth(newMonth)
    }

    fun onNextMonth() {
        val newMonth = _uiState.value.displayedYearMonth.plusMonths(1)
        _uiState.update { it.copy(displayedYearMonth = newMonth) }
        updateActivitiesForNewMonth(newMonth)
    }

    fun onPreviousYear() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.minusYears(1)) }
    }

    fun onNextYear() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.plusYears(1)) }
    }

    fun onGoToToday() {
        val currentState = _uiState.value
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()
        val monthChanged = currentState.displayedYearMonth != currentMonth
        
        _uiState.update {
            it.copy(
                displayedYearMonth = currentMonth,
                selectedDate = today
            )
        }
        
        if (monthChanged) {
            updateActivitiesForNewMonth(currentMonth)
        } else {
            // ✅ Quando o mês não muda, atualizar todas as dependências da data
            updateSelectedDateInCalendar()
            updateTasksForSelectedDate()
            updateJsonCalendarActivitiesForSelectedDate()
            updateHolidaysForSelectedDate()
            updateCommemorativeDatesForSelectedDate()
        }
    }
    
    // ===== END CALENDAR NAVIGATION FUNCTIONS =====

    fun onDateSelected(date: LocalDate) {
        val state = _uiState.value
        val shouldOpenModal = state.selectedDate.isEqual(date) && date.month == state.displayedYearMonth.month
        val monthChanged = state.displayedYearMonth.month != date.month || state.displayedYearMonth.year != date.year

        _uiState.update {
            it.copy(
                selectedDate = date,
                displayedYearMonth = if (monthChanged) {
                    YearMonth.from(date)
                } else {
                    it.displayedYearMonth
                },
                activityIdWithDeleteButtonVisible = null // Esconde o botão ao selecionar nova data
            )
        }
        
        if (monthChanged) {
            // Se o mês mudou, carregar atividades do novo mês
            val newMonth = YearMonth.from(date)
            updateActivitiesForNewMonth(newMonth)
        } else {
            // Se apenas a data selecionada mudou, atualizar apenas a marcação visual
            updateSelectedDateInCalendar()
            updateTasksForSelectedDate()
            updateJsonCalendarActivitiesForSelectedDate()
            updateHolidaysForSelectedDate()
            updateCommemorativeDatesForSelectedDate()
        }

        if (shouldOpenModal) {
            openCreateActivityModal()
        }
    }

    fun onYearlyMonthClicked(yearMonth: YearMonth) {
        val currentState = _uiState.value
        val monthChanged = currentState.displayedYearMonth != yearMonth
        
        _uiState.update {
            it.copy(
                displayedYearMonth = yearMonth,
                selectedDate = yearMonth.atDay(1),
                viewMode = ViewMode.MONTHLY,
                isSidebarOpen = false
            )
        }
        
        // Se o mês mudou, carregar atividades do novo mês
        if (monthChanged) {
            updateActivitiesForNewMonth(yearMonth)
        }
    }

    fun onViewModeChange(newMode: ViewMode) {
        _uiState.update { it.copy(viewMode = newMode, isSidebarOpen = false) }
    }

    fun onFilterChange(key: String, value: Boolean) {
        when (key) {
            "showCompletedActivities" -> {
                // Atualizar o estado imediatamente
                _uiState.update { it.copy(showCompletedActivities = value) }
                
                // Salvar a configuração
                viewModelScope.launch {
                    settingsRepository.saveShowCompletedActivities(value)
                }
                
                // Atualizar a UI
                updateAllDateDependentUI()
            }
            "showMoonPhases" -> {
                // Atualizar o estado imediatamente
                _uiState.update { it.copy(showMoonPhases = value) }
                
                // Salvar a configuração
                viewModelScope.launch {
                    settingsRepository.saveShowMoonPhases(value)
                }
            }
            else -> {
                // Verificar se é um filtro de calendário JSON
                if (key.startsWith("jsonCalendar_")) {
                    val calendarId = key.removePrefix("jsonCalendar_")
                    toggleJsonCalendarVisibility(calendarId, value)
                } else {
                    // Filtros normais
                    val currentFilters = _uiState.value.filterOptions
                    val newFilters = when (key) {
                        "showHolidays" -> currentFilters.copy(showHolidays = value)
                        "showEvents" -> currentFilters.copy(showEvents = value)
                        "showTasks" -> currentFilters.copy(showTasks = value)
                        "showNotes" -> currentFilters.copy(showNotes = value)
                        "showBirthdays" -> currentFilters.copy(showBirthdays = value)
                        "showCommemorative" -> currentFilters.copy(showCommemorative = value)
                        else -> currentFilters
                    }
                    
                    _uiState.update { it.copy(filterOptions = newFilters) }
                    
                    // Salvar configurações
                    viewModelScope.launch {
                        settingsRepository.saveFilterOptions(newFilters)
                    }
                    
                    // Atualizar a UI
                    updateAllDateDependentUI()
                }
            }
        }
    }

    fun onThemeChange(newTheme: Theme) {
        viewModelScope.launch {
            settingsRepository.saveTheme(newTheme)
        }
    }

    fun onAppIconModeChange(mode: AppIconMode) {
        viewModelScope.launch {
            settingsRepository.saveAppIconMode(mode)
        }
    }

    fun onAnimationTypeChange(newAnimationType: com.mss.thebigcalendar.data.model.AnimationType) {
        viewModelScope.launch {
            settingsRepository.saveAnimationType(newAnimationType)
        }
    }

    suspend fun onLanguageChange(newLanguage: com.mss.thebigcalendar.data.model.Language) {
        Log.d("CalendarViewModel", "🌐 onLanguageChange chamado com: ${newLanguage.displayName} (${newLanguage.code})")
        settingsRepository.saveLanguage(newLanguage)
    }

    /**
     * Limpa a mensagem de mudança de idioma
     */
    fun clearLanguageChangedMessage() {
        _uiState.update { it.copy(languageChangedMessage = null) }
    }

    /**
     * Solicita confirmação para deletar um calendário JSON
     */
    fun requestDeleteJsonCalendar(jsonCalendar: com.mss.thebigcalendar.data.model.JsonCalendar) {
        _uiState.update { 
            it.copy(
                showDeleteJsonCalendarDialog = true,
                jsonCalendarToDelete = jsonCalendar
            ) 
        }
    }

    /**
     * Cancela a solicitação de deletar calendário JSON
     */
    fun cancelDeleteJsonCalendar() {
        _uiState.update { 
            it.copy(
                showDeleteJsonCalendarDialog = false,
                jsonCalendarToDelete = null
            ) 
        }
    }

    /**
     * Remove um calendário JSON importado (após confirmação)
     */
    fun confirmDeleteJsonCalendar() {
        val calendarToDelete = _uiState.value.jsonCalendarToDelete
        if (calendarToDelete != null) {
            viewModelScope.launch {
                try {
                    
                    // Converter cor para string para comparação
                    val calendarColorString = String.format("#%08X", calendarToDelete.color.toArgb())
                    
                    // Remover atividades JSON do repositório de atividades
                    activityRepository.deleteJsonActivitiesByCalendar(calendarToDelete.title, calendarColorString)
                    
                    // Remover do repositório de calendários JSON
                    jsonCalendarRepository.removeJsonCalendar(calendarToDelete.id)
                    
                    // Recarregar calendários JSON
                    loadJsonCalendars()
                    
                    // Recarregar atividades do mês atual para atualizar a UI
                    loadActivitiesForCurrentMonth()
                    
                    // Fechar dialog
                    _uiState.update { 
                        it.copy(
                            showDeleteJsonCalendarDialog = false,
                            jsonCalendarToDelete = null
                        ) 
                    }
                    
                    
                } catch (e: Exception) {
                    Log.e("CalendarViewModel", "❌ Erro ao remover calendário JSON", e)
                    // Fechar dialog mesmo em caso de erro
                    _uiState.update { 
                        it.copy(
                            showDeleteJsonCalendarDialog = false,
                            jsonCalendarToDelete = null
                        ) 
                    }
                }
            }
        }
    }

    /**
     * Fecha o dialog de permissão de segundo plano
     */
    fun dismissBackgroundPermissionDialog() {
        _uiState.update { it.copy(showBackgroundPermissionDialog = false) }
    }

    fun dismissDecryptionDialog() {
        _uiState.update { it.copy(
            showDecryptionDialog = false,
            decryptionBackupUri = null,
            decryptionCloudFileId = null,
            decryptionCloudFileName = null,
            decryptionErrorMessage = null
        ) }
    }

    /**
     * Solicita permissão de segundo plano (chamado pelo dialog)
     */
    fun requestBackgroundPermission() {
        // Esta função será chamada pela MainActivity
        _uiState.update { it.copy(showBackgroundPermissionDialog = false) }
    }

    fun showDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = true) }
    }

    fun dismissDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = false) }
    }

    fun deleteActivityDirectly(activityId: String) {
        viewModelScope.launch {
            val activity = activityRepository.activities.first().find { it.id == activityId }
            if (activity != null) {
                val notificationService = NotificationService(getApplication())
                notificationService.cancelActivityNotifications(activity)
                deletedActivityRepository.addDeletedActivity(activity)
            }
            activityRepository.deleteActivity(activityId)
            loadData()
        }
    }

    fun removeAllDuplicates() {
        viewModelScope.launch {
            val allActs = activityRepository.activities.first()
            val duplicates = getDuplicateGroups(allActs)
            if (duplicates.isEmpty()) return@launch

            val notificationService = NotificationService(getApplication())
            duplicates.forEach { group ->
                val toDelete = group.drop(1)
                toDelete.forEach { act ->
                    notificationService.cancelActivityNotifications(act)
                    deletedActivityRepository.addDeletedActivity(act)
                    activityRepository.deleteActivity(act.id)
                }
            }

            _uiState.update { it.copy(showDuplicateDialog = false) }
            loadData()
        }
    }

    fun getDuplicateGroups(activities: List<Activity>): List<List<Activity>> {
        data class ActivitySignature(
            val title: String,
            val date: String,
            val startTime: java.time.LocalTime?,
            val endTime: java.time.LocalTime?,
            val isAllDay: Boolean,
            val description: String,
            val location: String,
            val activityType: ActivityType,
            val recurrenceRule: String
        )
        
        fun Activity.toSignature(): ActivitySignature {
            return ActivitySignature(
                title = title.trim().lowercase(),
                date = date,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                description = description?.trim()?.lowercase() ?: "",
                location = location?.trim()?.lowercase() ?: "",
                activityType = activityType,
                recurrenceRule = recurrenceRule?.trim()?.lowercase() ?: ""
            )
        }

        val customActivities = activities.filter { it.location?.startsWith("JSON_IMPORTED_") != true }
        val groups = customActivities.groupBy { it.toSignature() }
        return groups.values.filter { it.size > 1 }
    }



    fun onSaveActivity(activityData: Activity, syncWithGoogle: Boolean = false) {
        viewModelScope.launch {
            // Verificar duplicidade de agendamentos (ignorando ID e cor de prioridade, tratando instâncias recorrentes)
            val baseIdToSave = activityData.id.split("_").first()
            val isDuplicate = _uiState.value.activities.any { existing ->
                val existingBaseId = existing.id.split("_").first()
                if (existingBaseId == baseIdToSave) false
                else {
                    existing.title.trim().equals(activityData.title.trim(), ignoreCase = true) &&
                    existing.date == activityData.date &&
                    existing.startTime == activityData.startTime &&
                    existing.endTime == activityData.endTime &&
                    existing.isAllDay == activityData.isAllDay &&
                    (existing.description?.trim() ?: "").equals(activityData.description?.trim() ?: "", ignoreCase = true) &&
                    (existing.location?.trim() ?: "").equals(activityData.location?.trim() ?: "", ignoreCase = true) &&
                    existing.activityType == activityData.activityType &&
                    (existing.recurrenceRule?.trim() ?: "").equals(activityData.recurrenceRule?.trim() ?: "", ignoreCase = true)
                }
            }

            if (isDuplicate) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        getApplication<Application>().getString(R.string.duplicate_activity_exists),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // Verificar se é uma edição de instância recorrente
            val isEditingRecurringInstance = activityData.id.contains("_") && 
                                           activityData.id != "new" && 
                                           !activityData.id.isBlank()

            
            if (isEditingRecurringInstance) {
                // É uma edição de instância recorrente - aplicar mudanças à atividade base
                val baseId = activityData.id.split("_").first()
                val baseActivity = _uiState.value.activities.find { it.id == baseId }
                
                if (baseActivity != null) {
                    // Aplicar mudanças à atividade base, mantendo o ID original
                    val updatedBaseActivity = baseActivity.copy(
                        title = activityData.title,
                        description = activityData.description,
                        startTime = activityData.startTime,
                        endTime = activityData.endTime,
                        isAllDay = activityData.isAllDay,
                        location = activityData.location,
                        categoryColor = activityData.categoryColor,
                        notificationSettings = activityData.notificationSettings,
                        visibility = activityData.visibility,
                        showInCalendar = activityData.showInCalendar
                        // NÃO alterar recurrenceRule para manter a recorrência
                    )
                    
                    // Salvar a atividade base atualizada
                    activityRepository.saveActivity(updatedBaseActivity)
                    
                    val notificationService = NotificationService(getApplication())
                    // Cancelar notificação da instância anterior
                    notificationService.cancelNotification(activityData.id)
                    val originalInstanceDate = activityData.id.split("_").getOrNull(1)
                    if (originalInstanceDate != null) {
                        notificationService.cancelNotification("${baseId}_$originalInstanceDate")
                    }

                    // Agendar notificação se configurada - usar a instância com a data atualizada
                    if (activityData.notificationSettings.isEnabled &&
                        activityData.notificationSettings.notificationType != com.mss.thebigcalendar.data.model.NotificationType.NONE) {
                        
                        val targetDate = activityData.date.takeIf { it.isNotBlank() } ?: originalInstanceDate
                        if (targetDate != null) {
                            val instanceActivity = activityData.copy(
                                id = "${baseId}_$targetDate",
                                date = targetDate
                            )
                            notificationService.scheduleNotification(instanceActivity)
                        }
                    }
                    
                    // Sincronizar com Google Calendar se for evento do Google
                    if (updatedBaseActivity.isFromGoogle) {
                        updateGoogleCalendarEvent(updatedBaseActivity)
                    }
                    
                    closeCreateActivityModal()
                    return@launch
                }
            }
            
            // Lógica original para atividades não recorrentes ou novas
            var activityToSave = if (activityData.id == "new" || activityData.id.isBlank()) {
                activityData.copy(
                    id = UUID.randomUUID().toString(),
                    lastModified = System.currentTimeMillis()
                )
            } else {
                activityData.copy(lastModified = System.currentTimeMillis())
            }

            // Verificar se é uma edição de atividade existente do Google
            val isEditingGoogleEvent = activityToSave.id != "new" &&
                                     _uiState.value.activities.any { it.id == activityToSave.id && it.isFromGoogle }

            // Se for uma edição de atividade existente, verificar se a repetição foi alterada
            val existingActivity = if (activityToSave.id != "new") {
                _uiState.value.activities.find { it.id == activityToSave.id }
            } else null

            // Cancelar notificações da atividade anterior (cobrindo alteração de data, horário, recorrência ou desativação de notificações)
            val notificationService = NotificationService(getApplication())
            if (existingActivity != null) {
                notificationService.cancelActivityNotifications(existingActivity)
            }

            val repetitionChanged = existingActivity?.let { existing ->
                existing.recurrenceRule != activityToSave.recurrenceRule
            } ?: false

            // Se a repetição foi removida, remover todas as instâncias recorrentes
            if (repetitionChanged && (activityToSave.recurrenceRule.isNullOrEmpty() || activityToSave.recurrenceRule == "NONE")) {
                println("🔄 Repetição removida para atividade: ${existingActivity!!.title}")
                println("🔄 Regra anterior: ${existingActivity.recurrenceRule}")
                println("🔄 Nova regra: ${activityToSave.recurrenceRule}")
                removeRecurringInstances(existingActivity)
            }

            // Se for nova e o usuário optou por sincronizar com Google, tentar inserir primeiro no Google
            val isNewActivity = activityData.id == "new" || activityData.id.isBlank()
            if (isNewActivity && syncWithGoogle) {
                val account = _uiState.value.googleSignInAccount
                if (account != null) {
                    try {
                        val googleEventId = insertGoogleCalendarEvent(activityToSave, account)
                        if (!googleEventId.isNullOrBlank()) {
                            // Usar o ID do Google e marcar como vindo do Google para habilitar atualizações/exclusões
                            activityToSave = activityToSave.copy(id = googleEventId, isFromGoogle = true)
                        }
                    } catch (e: Exception) {
                        Log.e("CalendarViewModel", "❌ Falha ao inserir no Google Calendar", e)
                    }
                }
            }

            
            // Salvar a atividade principal (com possível ID do Google)
            activityRepository.saveActivity(activityToSave)

            // ✅ Agendar notificação se configurada
            if (activityToSave.notificationSettings.isEnabled &&
                activityToSave.notificationSettings.notificationType != com.mss.thebigcalendar.data.model.NotificationType.NONE) {
                notificationService.scheduleNotification(activityToSave)
            }

            // NOTA: Não geramos mais instâncias repetitivas automaticamente
            // As tarefas repetitivas serão calculadas dinamicamente quando o usuário navegar pelos meses
            if (activityToSave.recurrenceRule?.isNotEmpty() == true && activityToSave.recurrenceRule != "CUSTOM") {
            }

            // Sincronizar com Google Calendar se for edição de evento existente
            if (isEditingGoogleEvent) {
                updateGoogleCalendarEvent(activityToSave)
            }

            // Recarregar atividades do mês atual após salvar
            loadActivitiesForCurrentMonth()
            
            // Notificar widgets sobre a mudança com delay para garantir persistência
            viewModelScope.launch {
                delay(500) // Aguardar 500ms para garantir que os dados foram persistidos
                notifyWidgetsDataChanged()
            }
            
            // Verificar se é uma nova atividade criada e solicitar permissão contextualmente
            val isNewActivityCreated = activityData.id == "new" || activityData.id.isBlank()
            checkAndRequestBackgroundPermissionIfNeeded(activityToSave, isNewActivityCreated)
            
            closeCreateActivityModal()
        }
    }

    /**
     * Verifica se deve solicitar permissão de segundo plano contextualmente
     */
    private fun checkAndRequestBackgroundPermissionIfNeeded(activity: Activity, isNewActivityCreated: Boolean) {
        // This check is no longer necessary as the app now uses AlarmManager and WorkManager,
        // which are more robust and do not require this permission.
    }

    /**
     * Calcula instâncias repetitivas para uma data específica
     */
    private fun calculateRecurringInstancesForDate(baseActivity: Activity, targetDate: LocalDate): List<Activity> {
        val instances = mutableListOf<Activity>()
        
        try {
            val baseDate = LocalDate.parse(baseActivity.date)
            val targetDateString = targetDate.toString()
            
            // Verificar se esta data específica foi excluída
            if (baseActivity.excludedDates.contains(targetDateString)) {
                return instances
            }
            
            // Se a data base é posterior à data alvo, não há instâncias
            if (baseDate.isAfter(targetDate)) {
                return instances
            }
            
            // Para atividades HOURLY, permitir múltiplas ocorrências no mesmo dia
            // Para outras atividades, não adicionar instância recorrente se for o mesmo dia
            if (baseDate.isEqual(targetDate) && 
                !(baseActivity.recurrenceRule == "HOURLY" || baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true)) {
                return instances
            }
            
            when {
                baseActivity.recurrenceRule == "HOURLY" || baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true -> {
                    // Para regras HOURLY complexas, usar o RecurrenceService
                    if (baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true) {
                        val recurrenceService = RecurrenceService()
                        val startOfMonth = targetDate.withDayOfMonth(1)
                        val endOfMonth = targetDate.with(TemporalAdjusters.lastDayOfMonth())
                        
                        val recurringInstances = recurrenceService.generateRecurringInstances(
                            baseActivity,
                            startOfMonth,
                            endOfMonth
                        )
                        
                        // Filtrar apenas instâncias para a data específica
                        val instancesForTargetDate = recurringInstances.filter { 
                            LocalDate.parse(it.date).isEqual(targetDate) 
                        }
                        
                        // Verificar se cada instância não foi excluída
                        instancesForTargetDate.forEach { instance ->
                            val timeString = instance.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                            val instanceId = "${baseActivity.id}_${targetDate}_${timeString}"
                            val isExcluded = baseActivity.excludedInstances.contains(instanceId)
                            
                            if (!isExcluded) {
                                instances.add(instance)
                            }
                        }
                        
                    } else {
                        // Para regras HOURLY simples, verificar se a data alvo é posterior à data base
                        val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                        if (daysDiff > 0) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                baseActivity.recurrenceRule == "DAILY" -> {
                    // Verificar se a data alvo é um múltiplo de dias a partir da data base
                    val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                    if (daysDiff > 0) {
                        val instance = baseActivity.copy(
                            id = "${baseActivity.id}_${targetDate}",
                            date = targetDate.toString()
                        )
                        instances.add(instance)
                    }
                }
                baseActivity.recurrenceRule == "WEEKLY" -> {
                    // Verificar se a data alvo é um múltiplo de semanas a partir da data base
                    val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                    if (daysDiff > 0 && daysDiff % 7 == 0L) {
                        val instance = baseActivity.copy(
                            id = "${baseActivity.id}_${targetDate}",
                            date = targetDate.toString()
                        )
                        instances.add(instance)
                    }
                }
                baseActivity.recurrenceRule == "MONTHLY" -> {
                    // Verificar se a data alvo é um múltiplo de meses a partir da data base
                    val monthsDiff = ChronoUnit.MONTHS.between(baseDate, targetDate)
                    if (monthsDiff > 0) {
                        // Manter o mesmo dia do mês, ajustando para meses com menos dias
                        val targetDay = minOf(baseDate.dayOfMonth, targetDate.lengthOfMonth())
                        val adjustedDate = targetDate.withDayOfMonth(targetDay)
                        
                        if (adjustedDate.isEqual(targetDate)) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                baseActivity.recurrenceRule == "YEARLY" -> {
                    // Verificar se a data alvo é um múltiplo de anos a partir da data base
                    val yearsDiff = ChronoUnit.YEARS.between(baseDate, targetDate)
                    if (yearsDiff > 0) {
                        val targetDay = minOf(baseDate.dayOfMonth, targetDate.lengthOfMonth())
                        val adjustedDate = targetDate.withDayOfMonth(targetDay)
                        if (baseDate.month == targetDate.month && adjustedDate.isEqual(targetDate)) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                else -> {
                    // Para regras personalizadas (CUSTOM), usar o RecurrenceService
                    if (baseActivity.recurrenceRule?.startsWith("FREQ=") == true) {
                        val recurrenceService = RecurrenceService()
                        val startOfMonth = targetDate.withDayOfMonth(1)
                        val endOfMonth = targetDate.with(TemporalAdjusters.lastDayOfMonth())
                        
                        val recurringInstances = recurrenceService.generateRecurringInstances(
                            baseActivity, startOfMonth, endOfMonth
                        )
                        
                        // Verificar se alguma instância corresponde à data alvo
                        val matchingInstance = recurringInstances.find { instance ->
                            LocalDate.parse(instance.date).isEqual(targetDate)
                        }
                        
                        if (matchingInstance != null) {
                            instances.add(matchingInstance)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Erro ao calcular instâncias repetitivas - retornar lista vazia
        }
        
        return instances
    }

    /**
     * Calcula a próxima ocorrência para uma atividade recorrente horária
     */
    private fun calculateNextHourlyOccurrence(activity: Activity, currentDate: LocalDate, currentTime: LocalTime?): Pair<LocalDate, LocalTime?> {
        val recurrenceService = RecurrenceService()
        
        // Gerar instâncias para os próximos 7 dias para encontrar a próxima ocorrência
        val startDate = currentDate.plusDays(1)
        val endDate = currentDate.plusDays(7)
        
        val recurringInstances = recurrenceService.generateRecurringInstances(
            activity, startDate, endDate
        )
        
        // Encontrar a primeira instância que não está excluída
        val nextInstance = recurringInstances.firstOrNull { instance ->
            val instanceDate = LocalDate.parse(instance.date)
            val instanceTime = instance.startTime
            val timeString = instanceTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
            val instanceId = "${activity.id}_${instanceDate}_${timeString}"
            
            !activity.excludedInstances.contains(instanceId)
        }
        
        return if (nextInstance != null) {
            Pair(LocalDate.parse(nextInstance.date), nextInstance.startTime)
        } else {
            // Se não encontrar próxima instância, avançar para o próximo dia na mesma hora
            val nextDate = currentDate.plusDays(1)
            Pair(nextDate, currentTime)
        }
    }

    /**
     * Remove todas as instâncias recorrentes de uma atividade
     */
    private fun removeRecurringInstances(baseActivity: Activity) {
        viewModelScope.launch {
            try {
                // Buscar todas as atividades que são instâncias desta atividade base
                val allActivities = _uiState.value.activities
                
                val instancesToRemove = allActivities.filter { activity ->
                    // Verificar se é uma instância recorrente (ID contém o ID base + data)
                    val isInstance = activity.id.startsWith("${baseActivity.id}_") && 
                                   activity.id != baseActivity.id
                    isInstance
                }
                
                // Remover todas as instâncias
                instancesToRemove.forEach { instance ->
                    activityRepository.deleteActivity(instance.id)
                }
                
            } catch (e: Exception) {
                println("❌ Erro ao remover instâncias recorrentes: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Atualiza um evento no Google Calendar quando ele é editado no app
     */
    private fun updateGoogleCalendarEvent(activity: Activity) {
        viewModelScope.launch {
            try {
                val account = _uiState.value.googleSignInAccount
                if (account != null) {
                    val calendarService = googleCalendarService.getCalendarService(account)
                    
                    withContext(Dispatchers.IO) {
                        try {
                            // Criar objeto de evento do Google Calendar
                            val googleEvent = com.google.api.services.calendar.model.Event().apply {
                                summary = activity.title
                                description = activity.description
                                location = activity.location
                                
                                // Configurar data e hora
                                if (activity.isAllDay) {
                                    start = com.google.api.services.calendar.model.EventDateTime().apply {
                                        date = com.google.api.client.util.DateTime(activity.date)
                                    }
                                    end = com.google.api.services.calendar.model.EventDateTime().apply {
                                        date = com.google.api.client.util.DateTime(activity.date)
                                    }
                                } else {
                                    val startDateTime = java.time.LocalDateTime.of(
                                        java.time.LocalDate.parse(activity.date),
                                        activity.startTime ?: java.time.LocalTime.of(9, 0)
                                    )
                                    val endDateTime = java.time.LocalDateTime.of(
                                        java.time.LocalDate.parse(activity.date),
                                        activity.endTime ?: java.time.LocalTime.of(10, 0)
                                    )
                                    
                                    start = com.google.api.services.calendar.model.EventDateTime().apply {
                                        dateTime = com.google.api.client.util.DateTime(
                                            startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        )
                                    }
                                    end = com.google.api.services.calendar.model.EventDateTime().apply {
                                        dateTime = com.google.api.client.util.DateTime(
                                            endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        )
                                    }
                                }
                                
                                // Configurar regra de repetição se houver
                                if (!activity.recurrenceRule.isNullOrEmpty()) {
                                    recurrence = listOf(activity.recurrenceRule)
                                }
                            }
                            
                            // Atualizar o evento no Google Calendar
                            calendarService.events().update("primary", activity.id, googleEvent).execute()
                            
                        } catch (e: Exception) {
                            Log.w("CalendarViewModel", "⚠️ Não foi possível atualizar evento no Google Calendar: ${activity.title}", e)
                        }
                    }
                } else {
                    Log.w("CalendarViewModel", "⚠️ Usuário não está logado no Google, não é possível atualizar no Google Calendar")
                }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro ao atualizar evento no Google Calendar: ${activity.title}", e)
            }
        }
    }

    /**
     * Insere um novo evento no Google Calendar e retorna o ID do evento criado
     */
    private suspend fun insertGoogleCalendarEvent(activity: Activity, account: GoogleSignInAccount): String? {
        return withContext(Dispatchers.IO) {
            try {
                val calendarService = googleCalendarService.getCalendarService(account)

                val googleEvent = com.google.api.services.calendar.model.Event().apply {
                    summary = activity.title
                    description = activity.description
                    location = activity.location

                    // Datas/horários
                    if (activity.isAllDay) {
                        start = com.google.api.services.calendar.model.EventDateTime().apply {
                            date = com.google.api.client.util.DateTime(activity.date)
                        }
                        end = com.google.api.services.calendar.model.EventDateTime().apply {
                            date = com.google.api.client.util.DateTime(activity.date)
                        }
                    } else {
                        val startDateTime = java.time.LocalDateTime.of(
                            java.time.LocalDate.parse(activity.date),
                            activity.startTime ?: java.time.LocalTime.of(9, 0)
                        )
                        val endDateTime = java.time.LocalDateTime.of(
                            java.time.LocalDate.parse(activity.date),
                            activity.endTime ?: (activity.startTime?.plusHours(1) ?: java.time.LocalTime.of(10, 0))
                        )

                        start = com.google.api.services.calendar.model.EventDateTime().apply {
                            dateTime = com.google.api.client.util.DateTime(
                                startDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            )
                        }
                        end = com.google.api.services.calendar.model.EventDateTime().apply {
                            dateTime = com.google.api.client.util.DateTime(
                                endDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                            )
                        }
                    }

                    // Regra de repetição: para aniversários, padronizar YEARLY se não houver
                    when (activity.activityType) {
                        com.mss.thebigcalendar.data.model.ActivityType.BIRTHDAY -> {
                            val rule = activity.recurrenceRule?.takeIf { it.isNotEmpty() } ?: "RRULE:FREQ=YEARLY"
                            recurrence = listOf(rule)
                        }
                        else -> {
                            if (!activity.recurrenceRule.isNullOrEmpty()) {
                                recurrence = listOf(activity.recurrenceRule)
                            }
                        }
                    }
                }

                val created = calendarService.events().insert("primary", googleEvent).execute()
                created?.id
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro ao inserir evento no Google Calendar", e)
                null
            }
        }
    }

    /**
     * Deleta um evento do Google Calendar quando ele é removido do app
     */
    private fun deleteFromGoogleCalendar(activity: Activity) {
        viewModelScope.launch {
            try {
                val account = _uiState.value.googleSignInAccount
                if (account != null) {
                    val calendarService = googleCalendarService.getCalendarService(account)
                    
                    // Tentar deletar o evento usando o ID original do Google
                    withContext(Dispatchers.IO) {
                        try {
                            calendarService.events().delete("primary", activity.id).execute()
                        } catch (e: Exception) {
                            Log.w("CalendarViewModel", "⚠️ Não foi possível deletar evento do Google Calendar: ${activity.title}", e)
                            
                            // Se falhar, tentar buscar o evento pelo título e data
                            try {
                                val events = calendarService.events().list("primary")
                                    .setQ(activity.title) // Buscar por título
                                    .setTimeMin(com.google.api.client.util.DateTime(Instant.parse("${activity.date}T00:00:00Z").toEpochMilli()))
                                    .setTimeMax(com.google.api.client.util.DateTime(Instant.parse("${activity.date}T23:59:59Z").toEpochMilli()))
                                    .execute()
                                
                                events.items?.forEach { event ->
                                    if (event.summary == activity.title) {
                                        calendarService.events().delete("primary", event.id).execute()
                                    }
                                }
                            } catch (searchException: Exception) {
                                Log.e("CalendarViewModel", "❌ Falha ao buscar e deletar evento do Google Calendar: ${activity.title}", searchException)
                            }
                        }
                    }
                } else {
                    Log.w("CalendarViewModel", "⚠️ Usuário não está logado no Google, não é possível deletar do Google Calendar")
                }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro ao deletar evento do Google Calendar: ${activity.title}", e)
            }
        }
    }

    fun onDeleteActivityConfirm() {
        viewModelScope.launch {
            _uiState.value.activityIdToDelete?.let { activityId ->
                // Buscar a atividade pelo ID ou, se for instância recorrente, buscar pela atividade base
                var activityToDelete = _uiState.value.activities.find { it.id == activityId }
                
                // Se não encontrou pelo ID e parece ser uma instância recorrente, buscar pela atividade base
                if (activityToDelete == null && activityId.contains("_")) {
                    val baseId = activityId.split("_").first()
                    activityToDelete = _uiState.value.activities.find { it.id == baseId }
                }
                
                // Se ainda não encontrou, buscar por título e regra de recorrência
                if (activityToDelete == null && activityId.contains("_")) {
                    val baseId = activityId.split("_").first()
                    val allActivities = _uiState.value.activities
                    activityToDelete = allActivities.find { 
                        it.id == baseId || 
                        (it.id.contains(baseId) && it.id.contains("_"))
                    }
                }
                
                // Se ainda não encontrou, buscar nas atividades finalizadas
                if (activityToDelete == null) {
                    activityToDelete = _uiState.value.completedActivities.find { it.id == activityId }
                }
                
                if (activityToDelete != null) {
                    if (activityToDelete.isCompleted) {
                        deletedActivityRepository.addDeletedActivity(activityToDelete)
                        completedActivityRepository.removeCompletedActivity(activityId)
                        updateAllDateDependentUI()
                        notifyWidgetsDataChanged()
                        _uiState.update { it.copy(activityIdToDelete = null) }
                        return@launch
                    }

                    // ✅ Cancelar notificação antes de deletar
                    val notificationService = NotificationService(getApplication())
                    
                    // Se é uma atividade recorrente, cancelar TODAS as notificações recorrentes
                    if (recurrenceService.isRecurring(activityToDelete)) {
                        try {
                            // ✅ Cancelar todas as notificações de todas as instâncias futuras
                            notificationService.cancelAllRecurringNotifications(activityToDelete)
                            
                            // ✅ Buscar apenas atividades com o mesmo ID base (mais eficiente)
                            val allActivities = _uiState.value.activities
                            val baseId = activityToDelete.id
                            val recurringActivities = allActivities.filter { activity ->
                                // Buscar pela atividade base ou instâncias que começam com o mesmo ID
                                activity.id == baseId || 
                                (activity.id.startsWith("${baseId}_") && 
                                 activity.title == activityToDelete.title && 
                                 activity.recurrenceRule == activityToDelete.recurrenceRule)
                            }
                            
                            
                            // ✅ Mover todas as instâncias para a lixeira (com limite de segurança)
                            val maxActivities = 100 // Limite para evitar loop infinito
                            recurringActivities.take(maxActivities).forEach { activity ->
                                deletedActivityRepository.addDeletedActivity(activity)
                                activityRepository.deleteActivity(activity.id)
                                
                                // Sincronizar com Google Calendar se for evento do Google
                                if (activity.isFromGoogle) {
                                    deleteFromGoogleCalendar(activity)
                                }
                            }
                            
                            if (recurringActivities.size > maxActivities) {
                            }
                            
                        } catch (e: Exception) {
                            Log.e("CalendarViewModel", "❌ Erro ao deletar atividades recorrentes", e)
                            // Fallback: deletar apenas a atividade base
                            deletedActivityRepository.addDeletedActivity(activityToDelete)
                            activityRepository.deleteActivity(activityId)
                        }
                        
                    } else {
                        // Cancelar notificações da atividade única
                        notificationService.cancelActivityNotifications(activityToDelete)

                        // Mover para a lixeira
                        deletedActivityRepository.addDeletedActivity(activityToDelete)
                        
                        // Deletar da lista principal
                        activityRepository.deleteActivity(activityId)
                        
                        // ✅ Desativar despertadores órfãos para atividades não repetitivas
                        disableOrphanedAlarms(activityId, activityToDelete.title)
                        
                        // Sincronizar com Google Calendar se for evento do Google
                        if (activityToDelete.isFromGoogle) {
                            deleteFromGoogleCalendar(activityToDelete)
                        }
                        
                    }
                }
            }
            
            // Notificar widgets sobre a mudança
            notifyWidgetsDataChanged()
            
            cancelDeleteActivity()
        }
    }

    

    fun onBackupRequest() {
        viewModelScope.launch {
            val directoryUri = _uiState.value.backupDirectoryUri
            if (directoryUri.isNullOrBlank()) {
                _uiState.update { it.copy(needsBackupDirectorySelection = true) }
                return@launch
            }

            try {
                val result = backupService.createBackup(Uri.parse(directoryUri))
                result.fold(
                    onSuccess = { backupFileName ->
                        _uiState.update { it.copy(
                            backupMessage = getApplication<Application>().getString(R.string.backup_created_success, backupFileName),
                            isBackupError = false
                        ) }
                        loadBackupFiles()
                    },
                    onFailure = { exception ->
                        Log.e("CalendarViewModel", "❌ Erro ao criar backup", exception)
                        _uiState.update { it.copy(
                            backupMessage = getApplication<Application>().getString(R.string.backup_create_failed, exception.message ?: ""),
                            isBackupError = true
                        ) }
                    }
                )
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro inesperado durante backup", e)
                _uiState.update { it.copy(
                    backupMessage = getApplication<Application>().getString(R.string.unexpected_error, e.message ?: ""),
                    isBackupError = true
                ) }
            }
        }
    }

    fun onBackupDirectorySelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(uri, takeFlags)

                settingsRepository.saveBackupDirectoryUri(uri.toString())
                _uiState.update { it.copy(needsBackupDirectorySelection = false) }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Falha ao salvar o diretório de backup", e)
                _uiState.update { it.copy(
                    backupMessage = getApplication<Application>().getString(R.string.backup_dir_set_failed),
                    isBackupError = true
                ) }
            }
        }
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null, isBackupError = false, needsBackupDirectorySelection = false) }
    }

    fun onRestoreRequest() { println("ViewModel: Pedido de restauração recebido.") }

    fun openSidebar() = _uiState.update { it.copy(isSidebarOpen = true) }
    fun closeSidebar() = _uiState.update { it.copy(isSidebarOpen = false) }

    fun onNavigateToSettings(screen: String?) {
        _uiState.update { it.copy(isSettingsScreenOpen = true, isSidebarOpen = false) }
    }

    fun closeSettingsScreen() {
        _uiState.update { it.copy(isSettingsScreenOpen = false) }
    }

    fun onWelcomeNameChange(newName: String) {
        viewModelScope.launch {
            settingsRepository.saveWelcomeName(newName)
        }
    }

    fun openCreateActivityModal(activity: Activity? = null, activityType: ActivityType = ActivityType.EVENT) {
        val template = if (activity != null) {
            // Se for uma instância recorrente, carregar os dados da atividade base
            if (activity.id.contains("_") && activity.id != "new") {
                val baseId = activity.id.split("_").first()
                val baseActivity = _uiState.value.activities.find { it.id == baseId }
                baseActivity ?: activity
            } else {
                activity
            }
        } else {
            Activity(
                id = "new",
                title = "",
                description = null,
                date = _uiState.value.selectedDate.toString(),
                startTime = null,
                endTime = null,
                isAllDay = true,
                location = null,
                categoryColor = if (activityType == ActivityType.TASK) "#3B82F6" else "#F43F5E",
                activityType = activityType,
                recurrenceRule = null,
                showInCalendar = true // Por padrão, mostrar no calendário
            )
        }
        _uiState.update { it.copy(activityToEdit = template, activityIdWithDeleteButtonVisible = null) }
    }

    fun closeCreateActivityModal() = _uiState.update { it.copy(activityToEdit = null) }

    fun onTaskLongPressed(activityId: String) {
        _uiState.update { currentState ->
            if (currentState.activityIdWithDeleteButtonVisible == activityId) {
                // If the same item is clicked again, hide the buttons
                currentState.copy(activityIdWithDeleteButtonVisible = null)
            } else {
                // Otherwise, show buttons for the new item
                currentState.copy(activityIdWithDeleteButtonVisible = activityId)
            }
        }
    }

    fun hideDeleteButton() {
        _uiState.update { it.copy(activityIdWithDeleteButtonVisible = null) }
    }

    fun toggleSidebarFilterVisibility(filterKey: String) {
        val currentVisibility = _uiState.value.sidebarFilterVisibility
        val newVisibility = when (filterKey) {
            "showHolidays" -> currentVisibility.copy(showHolidays = !currentVisibility.showHolidays)
            "showEvents" -> currentVisibility.copy(showEvents = !currentVisibility.showEvents)
            "showTasks" -> currentVisibility.copy(showTasks = !currentVisibility.showTasks)
            "showBirthdays" -> currentVisibility.copy(showBirthdays = !currentVisibility.showBirthdays)
            "showNotes" -> currentVisibility.copy(showNotes = !currentVisibility.showNotes)
            "showCommemorative" -> currentVisibility.copy(showCommemorative = !currentVisibility.showCommemorative)
            "showCompletedActivities" -> currentVisibility.copy(showCompletedTasks = !currentVisibility.showCompletedTasks)
            "showMoonPhases" -> currentVisibility.copy(showMoonPhases = !currentVisibility.showMoonPhases)
            else -> currentVisibility
        }
        
        // Se a opção foi removida do sidebar (agora está false), também desativar o filtro
        val shouldDisableFilter = when (filterKey) {
            "showHolidays" -> !newVisibility.showHolidays && currentVisibility.showHolidays
            "showEvents" -> !newVisibility.showEvents && currentVisibility.showEvents
            "showTasks" -> !newVisibility.showTasks && currentVisibility.showTasks
            "showBirthdays" -> !newVisibility.showBirthdays && currentVisibility.showBirthdays
            "showNotes" -> !newVisibility.showNotes && currentVisibility.showNotes
            "showCommemorative" -> !newVisibility.showCommemorative && currentVisibility.showCommemorative
            "showCompletedActivities" -> !newVisibility.showCompletedTasks && currentVisibility.showCompletedTasks
            "showMoonPhases" -> !newVisibility.showMoonPhases && currentVisibility.showMoonPhases
            else -> false
        }
        
        _uiState.update { it.copy(sidebarFilterVisibility = newVisibility) }
        
        // Se a opção foi removida do sidebar, desativar o filtro correspondente
        if (shouldDisableFilter) {
            onFilterChange(filterKey, false)
        }
        
        viewModelScope.launch {
            settingsRepository.saveSidebarFilterVisibility(newVisibility)
        }
    }
    
    fun markActivityAsCompleted(activityId: String) {
        val now = System.currentTimeMillis()
        // Atualização imediata na UI para iniciar a animação de descida instantaneamente
        _uiState.update { currentState ->
            val updatedTasks = currentState.tasksForSelectedDate.map { task ->
                if (task.id == activityId) {
                    task.copy(isCompleted = true, lastModified = now)
                } else {
                    task
                }
            }.sortedWith(
                compareBy<Activity> { it.isCompleted }
                    .thenBy { if (it.isCompleted) it.lastModified else 0L }
                    .thenByDescending { it.categoryColor.toIntOrNull() ?: 0 }
                    .thenBy { it.startTime ?: java.time.LocalTime.MIN }
            )
            currentState.copy(
                tasksForSelectedDate = updatedTasks,
                activityIdWithDeleteButtonVisible = null,
                recentlyCompletedTaskId = activityId
            )
        }

        viewModelScope.launch {
            delay(750)
            if (_uiState.value.recentlyCompletedTaskId == activityId) {
                _uiState.update { it.copy(recentlyCompletedTaskId = null) }
            }
        }

        viewModelScope.launch {
            markActivityAsCompletedInternal(activityId)
        }
    }
    
    /**
     * Função interna para marcar atividade como concluída (pode ser chamada por notificações)
     */
    fun markActivityAsCompletedInternal(activityId: String) {
        viewModelScope.launch {
            
            // Verificar se é uma instância recorrente (ID contém data)
            val isRecurringInstance = activityId.contains("_") && activityId.split("_").size >= 2
            
            if (isRecurringInstance) {
                // Tratar instância recorrente específica
                val parts = activityId.split("_")
                val baseId = parts[0]
                val instanceDate = parts[1]
                val instanceTime = if (parts.size >= 3) parts[2] else null

                
                // Buscar a atividade base
                val baseActivity = _uiState.value.activities.find { it.id == baseId }
                
                if (baseActivity != null && recurrenceService.isRecurring(baseActivity)) {

                    
                    // Criar instância específica para salvar como concluída
                    val instanceToComplete = baseActivity.copy(
                        id = activityId,
                        date = instanceDate,
                        isCompleted = true,
                        showInCalendar = false,
                        lastModified = System.currentTimeMillis()
                    )
                    
                    // Salvar instância específica como concluída
                    completedActivityRepository.addCompletedActivity(instanceToComplete)
                    
                    // Para atividades HOURLY, adicionar instância específica à lista de exclusões
                    // Para outras atividades, adicionar data à lista de exclusões
                    val updatedBaseActivity = if (baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true) {
                        // Para atividades HOURLY, sempre incluir horário no ID da instância
                        val timeString = instanceTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                        val instanceId = "${baseActivity.id}_${instanceDate}_${timeString}"
                        val updatedExcludedInstances = baseActivity.excludedInstances + instanceId
                        baseActivity.copy(
                            excludedInstances = updatedExcludedInstances,
                            lastModified = System.currentTimeMillis()
                        )
                    } else {
                        val updatedExcludedDates = baseActivity.excludedDates + instanceDate
                        baseActivity.copy(
                            excludedDates = updatedExcludedDates,
                            lastModified = System.currentTimeMillis()
                        )
                    }
                    
                        // Atualizar a atividade base com a nova lista de exclusões
                        activityRepository.saveActivity(updatedBaseActivity)
                        
                        // Cancelar a notificação para a instância específica concluída
                        val notificationService = NotificationService(getApplication())
                        notificationService.cancelNotification(activityId)
                        
                        // Atualizar a UI
                        updateAllDateDependentUI()
                        
                        // Notificar widgets sobre a mudança
                        notifyWidgetsDataChanged()
                    
                }
            } else {
                // Tratar atividade única ou atividade base
                val activityToComplete = _uiState.value.activities.find { it.id == activityId }
                
                if (activityToComplete != null) {
                    // Verificar se é uma atividade recorrente
                    if (recurrenceService.isRecurring(activityToComplete)) {
                        // Para atividades recorrentes (primeira instância), sempre tratar como instância específica
                        val activityDate = activityToComplete.date

                        
                        // Criar instância específica para salvar como concluída
                        val instanceToComplete = activityToComplete.copy(
                            id = activityId,
                            date = activityDate,
                            isCompleted = true,
                            showInCalendar = false,
                            lastModified = System.currentTimeMillis()
                        )
                        
                        // Salvar instância específica como concluída
                        completedActivityRepository.addCompletedActivity(instanceToComplete)
                        
                        // Para atividades HOURLY, implementar estratégia especial para primeira instância
                        val updatedBaseActivity = if (activityToComplete.recurrenceRule?.startsWith("FREQ=HOURLY") == true) {
                            val activityTime = activityToComplete.startTime
                            val timeString = activityTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                            val instanceId = "${activityToComplete.id}_${activityDate}_${timeString}"
                            
                            // Para TODAS as instâncias, apenas adicionar à lista de exclusões
                            val updatedExcludedInstances = activityToComplete.excludedInstances + instanceId
                            activityToComplete.copy(
                                excludedInstances = updatedExcludedInstances,
                                lastModified = System.currentTimeMillis()
                            )
                        } else {
                            val updatedExcludedDates = activityToComplete.excludedDates + activityDate
                            activityToComplete.copy(
                                excludedDates = updatedExcludedDates,
                                lastModified = System.currentTimeMillis()
                            )
                        }
                        
                        // Atualizar a atividade base com a nova lista de exclusões
                        activityRepository.saveActivity(updatedBaseActivity)

                        // Cancelar a notificação para a primeira instância recorrente
                        val notificationService = NotificationService(getApplication())
                        val timeString = activityToComplete.startTime?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                        val instanceId = if (activityToComplete.recurrenceRule?.startsWith("FREQ=HOURLY") == true && timeString != null) {
                            "${activityToComplete.id}_${activityToComplete.date}_$timeString"
                        } else {
                            "${activityToComplete.id}_${activityToComplete.date}"
                        }
                        notificationService.cancelNotification(instanceId)

                        // Atualizar a UI
                        updateAllDateDependentUI()
                        
                        // Notificar widgets sobre a mudança
                        notifyWidgetsDataChanged()
                        
                    } else {
                        // Tratar atividade única (não recorrente)
                        
                        // Marcar como concluída e salvar no repositório de finalizadas
                        val completedActivity = activityToComplete.copy(
                            isCompleted = true,
                            showInCalendar = false, // Ocultar do calendário mensal
                            lastModified = System.currentTimeMillis()
                        )
                        
                        // Salvar no repositório de atividades finalizadas
                        completedActivityRepository.addCompletedActivity(completedActivity)
                        
                        // Remover da lista principal
                        activityRepository.deleteActivity(activityId)
                        
                        // ✅ Desativar despertadores órfãos para atividades não repetitivas finalizadas
                        disableOrphanedAlarms(activityId, activityToComplete.title)
                        
                        // Sincronizar com Google Calendar se for evento do Google
                        if (activityToComplete.isFromGoogle) {
                            deleteFromGoogleCalendar(activityToComplete)
                        }

                        
                        // Atualizar a UI após marcar como concluída
                        updateAllDateDependentUI()
                        
                        // Notificar widgets sobre a mudança
                        notifyWidgetsDataChanged()
                    }
                }
            }
        }
    }

    fun requestDeleteActivity(activityId: String) {
        _uiState.update { it.copy(activityIdToDelete = activityId, activityIdWithDeleteButtonVisible = null) }
    }

    fun cancelDeleteActivity() = _uiState.update { it.copy(activityIdToDelete = null) }

    /**
     * Desativa despertadores órfãos quando uma atividade não repetitiva é apagada ou finalizada
     */
    private suspend fun disableOrphanedAlarms(activityId: String, activityTitle: String) {
        try {
            
            // Buscar todos os alarmes ativos
            val activeAlarms = alarmRepository.getActiveAlarms()
            
            // Filtrar alarmes que podem estar associados a esta atividade
            val orphanedAlarms = activeAlarms.filter { alarm ->
                // Verificar se o alarme pode estar associado à atividade
                alarm.label.contains(activityTitle, ignoreCase = true) ||
                alarm.id == activityId ||
                alarm.id.contains(activityId)
            }
            
            if (orphanedAlarms.isNotEmpty()) {
                
                // Desativar cada despertador órfão
                orphanedAlarms.forEach { alarm ->
                    val updatedAlarm = alarm.copy(
                        isEnabled = false,
                        lastModified = System.currentTimeMillis()
                    )
                    
                    val result = alarmRepository.saveAlarm(updatedAlarm)
                    if (result.isSuccess) {
                        
                        // Cancelar o alarme no sistema
                        val notificationService = NotificationService(getApplication())
                        notificationService.cancelNotification(alarm.id)
                    } else {
                        Log.w("CalendarViewModel", "⚠️ Falha ao desativar despertador órfão: ${alarm.label}")
                    }
                }
            } else {
            }
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "❌ Erro ao desativar despertadores órfãos", e)
        }
    }

    /**
     * Limpa todos os despertadores órfãos (despertadores sem atividade correspondente)
     * Esta função pode ser chamada periodicamente para manter o sistema limpo
     */
    suspend fun cleanupOrphanedAlarms() {
        try {
            
            // Buscar todos os alarmes ativos
            val activeAlarms = alarmRepository.getActiveAlarms()
            
            // Buscar todas as atividades ativas
            val allActivities = activityRepository.activities.first()
            
            // Identificar alarmes órfãos
            val orphanedAlarms = activeAlarms.filter { alarm ->
                // Verificar se existe uma atividade correspondente
                val hasCorrespondingActivity = allActivities.any { activity ->
                    alarm.label.contains(activity.title, ignoreCase = true) ||
                    alarm.id == activity.id ||
                    alarm.id.contains(activity.id)
                }
                
                !hasCorrespondingActivity
            }
            
            if (orphanedAlarms.isNotEmpty()) {
                
                // Desativar cada despertador órfão
                orphanedAlarms.forEach { alarm ->
                    val updatedAlarm = alarm.copy(
                        isEnabled = false,
                        lastModified = System.currentTimeMillis()
                    )
                    
                    val result = alarmRepository.saveAlarm(updatedAlarm)
                    if (result.isSuccess) {
                        
                        // Cancelar o alarme no sistema
                        val notificationService = NotificationService(getApplication())
                        notificationService.cancelNotification(alarm.id)
                    } else {
                        Log.w("CalendarViewModel", "⚠️ Falha ao limpar despertador órfão: ${alarm.label}")
                    }
                }
                
            } else {
            }
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "❌ Erro durante limpeza de despertadores órfãos", e)
        }
    }

    fun onSaintDayClick(saint: Holiday) {
        _uiState.update { it.copy(saintInfoToShow = saint) }
    }

    fun onSaintInfoDialogDismiss() {
        _uiState.update { it.copy(saintInfoToShow = null) }
    }

    // Funções de pesquisa
    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        
        viewModelScope.launch {
            try {
                val allActivities = activityRepository.activities.first()
                val results = searchService.search(
                    query = query,
                    activities = allActivities,
                    nationalHolidays = _uiState.value.nationalHolidays,
                    commemorativeDates = _uiState.value.commemorativeDates
                )
                _uiState.update { it.copy(searchResults = results) }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Erro ao realizar busca", e)
            }
        }
    }

    fun onSearchResultClick(result: SearchResult) {
        println("🎯 Resultado selecionado: ${result.title} (${result.type})")
        result.date?.let { targetDate ->
            println("📅 Navegando para data: $targetDate")
            val currentState = _uiState.value
            val targetYearMonth = java.time.YearMonth.from(targetDate)
            val monthChanged = currentState.displayedYearMonth != targetYearMonth
            
            _uiState.update { 
                it.copy(
                    displayedYearMonth = targetYearMonth,
                    selectedDate = targetDate
                )
            }
            
            if (monthChanged) {
                updateActivitiesForNewMonth(targetYearMonth)
            } else {
                updateAllDateDependentUI()
            }
        }
        
        // Limpar pesquisa
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun onSearchIconClick() {
        println("🔍 Abrindo tela de pesquisa")
        _uiState.update { it.copy(isSearchScreenOpen = true) }
    }

    fun onChartIconClick() {
        println("📊 Abrindo tela de gráfico")
        _uiState.update { it.copy(isChartScreenOpen = true) }
    }

        fun closeSearchScreen() {
        println("🚪 Fechando tela de pesquisa")
        _uiState.update {
            it.copy(
                isSearchScreenOpen = false,
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun closeChartScreen() {
        println("🚪 Fechando tela de gráfico")
        _uiState.update { it.copy(isChartScreenOpen = false) }
    }

    // --- Tela de Notas ---
    fun onNotesClick() {
        println("📝 Abrindo tela de notas")
        _uiState.update { it.copy(isNotesScreenOpen = true, isSidebarOpen = false) }
    }

    fun closeNotesScreen() {
        println("🚪 Fechando tela de notas")
        _uiState.update { it.copy(isNotesScreenOpen = false) }
    }

    // --- Alarmes ---
    fun onAlarmsClick() {
        println("⏰ Abrindo tela de alarmes")
        _uiState.update { it.copy(isAlarmsScreenOpen = true, isSidebarOpen = false) }
    }

    fun closeAlarmsScreen() {
        println("🚪 Fechando tela de alarmes")
        _uiState.update { it.copy(isAlarmsScreenOpen = false) }
    }

    // --- Tarefas Concluídas ---
    fun onCompletedTasksClick() {
        println("📋 Abrindo tela de tarefas concluídas")
        _uiState.update { it.copy(isCompletedTasksScreenOpen = true) }
    }

    fun closeCompletedTasksScreen() {
        println("🚪 Fechando tela de tarefas concluídas")
        _uiState.update { it.copy(isCompletedTasksScreenOpen = false) }
    }
    
    // --- Impressão de Calendário ---
    fun onPrintCalendarClick() {
        println("🖨️ Abrindo tela de impressão de calendário")
        _uiState.update { it.copy(isPrintCalendarScreenOpen = true) }
    }

    fun closePrintCalendarScreen() {
        println("🚪 Fechando tela de impressão de calendário")
        _uiState.update { it.copy(isPrintCalendarScreenOpen = false, isSidebarOpen = false) }
    }
    
    fun generateCalendarPdf(printOptions: com.mss.thebigcalendar.ui.screens.PrintOptions, onPdfGenerated: (String) -> Unit) {
        viewModelScope.launch {
            try {
                android.util.Log.d("CalendarViewModel", "🖨️ Gerando PDF do calendário para ${printOptions.selectedMonth}")
                
                // Filtrar dados do mês selecionado
                val monthActivities = _uiState.value.activities.filter { 
                    val activityDate = LocalDate.parse(it.date)
                    activityDate.year == printOptions.selectedMonth.year && 
                    activityDate.month == printOptions.selectedMonth.month 
                }
                
                android.util.Log.d("CalendarViewModel", "📅 Atividades encontradas: ${monthActivities.size}")
                
                val monthHolidays = _uiState.value.nationalHolidays.values.filter { holiday ->
                    val holidayDate = LocalDate.parse(holiday.date)
                    holidayDate.year == printOptions.selectedMonth.year && 
                    holidayDate.month == printOptions.selectedMonth.month 
                }
                
                android.util.Log.d("CalendarViewModel", "🎉 Feriados encontrados: ${monthHolidays.size}")
                
                val monthJsonHolidays = _uiState.value.activities.filter { activity ->
                    val activityDate = LocalDate.parse(activity.date)
                    val isJsonImported = activity.location?.startsWith("JSON_IMPORTED_") == true
                    val dateMatches = activityDate.year == printOptions.selectedMonth.year && 
                                    activityDate.month == printOptions.selectedMonth.month
                    isJsonImported && dateMatches
                }.map { activity ->
                    // Converter Activity para JsonHoliday para o PDF
                    val activityColor = try {
                        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(activity.categoryColor))
                    } catch (e: Exception) {
                        androidx.compose.ui.graphics.Color.Blue
                    }
                    
                    com.mss.thebigcalendar.data.model.JsonHoliday(
                        id = activity.id,
                        name = activity.title,
                        date = activity.date,
                        summary = activity.description,
                        wikipediaLink = activity.wikipediaLink,
                        calendarId = "unknown",
                        calendarTitle = "JSON Calendar",
                        calendarColor = activityColor
                    )
                }
                
                // Obter fases da lua para o mês selecionado
                val moonPhaseRepository = com.mss.thebigcalendar.data.repository.MoonPhaseRepository(getApplication())
                val monthMoonPhases = moonPhaseRepository.getMoonPhasesForMonth(
                    java.time.YearMonth.from(printOptions.selectedMonth)
                )
                
                // Gerar PDF
                android.util.Log.d("CalendarViewModel", "🔄 Chamando PdfGenerationService...")
                val pdfFile = pdfGenerationService.generateCalendarPdf(
                    printOptions = printOptions,
                    activities = monthActivities,
                    holidays = monthHolidays,
                    jsonHolidays = monthJsonHolidays,
                    moonPhases = monthMoonPhases
                )
                android.util.Log.d("CalendarViewModel", "✅ PdfGenerationService concluído!")
                
                android.util.Log.d("CalendarViewModel", "✅ PDF gerado com sucesso: ${pdfFile.absolutePath}")
                
                // Notificar que o PDF foi gerado
                onPdfGenerated(pdfFile.absolutePath)
                
            } catch (e: Exception) {
                android.util.Log.e("CalendarViewModel", "❌ Erro ao gerar PDF: ${e.message}", e)
                // TODO: Mostrar erro para o usuário
            }
        }
    }
    
    fun deleteCompletedActivity(activityId: String) {
        viewModelScope.launch {
            try {
                // Remover da lista de atividades concluídas
                completedActivityRepository.removeCompletedActivity(activityId)
                
                // Atualizar o estado da UI
                _uiState.update { currentState ->
                    currentState.copy(
                        completedActivities = currentState.completedActivities.filter { it.id != activityId }
                    )
                }
                
                println("🗑️ Atividade concluída removida permanentemente: $activityId")
            } catch (e: Exception) {
                println("❌ Erro ao remover atividade concluída: ${e.message}")
            }
        }
    }

    // --- Lixeira ---
    
    fun onTrashIconClick() {
        println("🗑️ Abrindo lixeira")
        _uiState.update { it.copy(isTrashScreenOpen = true) }
    }
    
    fun onTrashSortOrderChange(sortOrder: String) {
        println("🔄 Alterando ordem da lixeira: $sortOrder")
        _uiState.update { it.copy(trashSortOrder = sortOrder) }
    }
    
    fun forceGoogleSync() {
        val account = _uiState.value.googleSignInAccount
        if (account != null) {
            performProgressiveSync(account, forceFullSync = true)
        }
    }
    
    fun manualGoogleSync() {
        syncActivitiesWithCloud()
    }
    
    fun syncGoogleCalendarSimple() {
        syncActivitiesWithCloud()
    }
    
    fun onManualSync() {
        syncActivitiesWithCloud()
    }

    fun syncActivitiesWithCloud(providedPassword: String? = null) {
        val account = _uiState.value.googleSignInAccount ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true, syncErrorMessage = null) }
            try {
                backupService.syncActivitiesWithCloud(account, providedPassword)
                    .onSuccess {
                        _uiState.update { it.copy(
                            isSyncing = false,
                            showDecryptionDialog = false,
                            decryptionErrorMessage = null,
                            lastGoogleSyncTime = System.currentTimeMillis()
                        ) }
                        loadData()
                        viewModelScope.launch {
                            delay(500)
                            notifyWidgetsDataChanged()
                        }
                    }
                    .onFailure { exception ->
                        Log.e("CalendarViewModel", "❌ Erro na sincronização com o Drive", exception)
                        if (exception is com.mss.thebigcalendar.crypto.DecryptionRequiredException || exception is com.mss.thebigcalendar.crypto.DecryptionFailedException) {
                            _uiState.update { it.copy(
                                isSyncing = false,
                                showDecryptionDialog = true,
                                decryptionErrorMessage = exception.message
                            ) }
                        } else {
                            _uiState.update { it.copy(
                                isSyncing = false,
                                syncErrorMessage = getApplication<Application>().getString(R.string.sync_failed, exception.message ?: "")
                            ) }
                        }
                    }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro inesperado na sincronização com o Drive", e)
                _uiState.update { it.copy(
                    isSyncing = false,
                    syncErrorMessage = getApplication<Application>().getString(R.string.unexpected_error, e.message ?: "")
                ) }
            }
        }
    }

    fun triggerCloudSync() {
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2000) // Debounce de 2 segundos para evitar múltiplos uploads consecutivos
            val account = _uiState.value.googleSignInAccount
            if (account != null) {
                try {
                    backupService.syncActivitiesWithCloud(account)
                } catch (e: Exception) {
                    Log.e("CalendarViewModel", "Erro na sincronização automática em nuvem: ${e.message}", e)
                }
            }
        }
    }

    private fun performProgressiveSync(account: GoogleSignInAccount, forceFullSync: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncErrorMessage = null, syncProgress = null) }

            try {
                val result = progressiveSyncService.syncProgressively(account, forceFullSync = forceFullSync) { progress ->
                    _uiState.update { it.copy(syncProgress = progress) }
                }

                result.fold(
                    onSuccess = { totalEvents ->
                        _uiState.update { it.copy(
                            isSyncing = false,
                            lastGoogleSyncTime = System.currentTimeMillis()
                        ) }

                        // Agendar próxima sincronização automática
                        scheduleAutomaticSync()

                        updateAllDateDependentUI()
                    },                    onFailure = { exception ->
                        Log.e("CalendarViewModel", "❌ Erro na sincronização progressiva", exception)
                        _uiState.update { it.copy(
                            isSyncing = false,
                            syncErrorMessage = getApplication<Application>().getString(R.string.sync_failed, exception.message ?: "")
                        ) }
                    }
                )
                
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro inesperado na sincronização", e)
                _uiState.update { it.copy(
                    isSyncing = false,
                    syncErrorMessage = getApplication<Application>().getString(R.string.unexpected_error, e.message ?: "")
                ) }
            }
        }
    }
    
    /**
     * Agenda sincronização automática diária
     */
    private fun scheduleAutomaticSync() {
        val workManager = WorkManager.getInstance(getApplication())
        
        // Cancelar trabalhos anteriores
        workManager.cancelAllWorkByTag("google_calendar_sync")
        
        // Criar restrições (só sincronizar com internet)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Agendar sincronização diária
        val syncWorkRequest = PeriodicWorkRequestBuilder<GoogleCalendarSyncWorker>(
            1, TimeUnit.DAYS
        )
            .setConstraints(constraints)
            .addTag("google_calendar_sync")
            .build()
        
        workManager.enqueue(syncWorkRequest)
    }
    
    fun closeTrashScreen() {
        println("🚪 Fechando lixeira")
        _uiState.update { it.copy(isTrashScreenOpen = false) }
    }
    
    fun restoreDeletedActivity(deletedActivityId: String) {
        viewModelScope.launch {
            val restoredActivity = deletedActivityRepository.restoreActivity(deletedActivityId)
            if (restoredActivity != null) {
                // Restaurar a atividade
                activityRepository.addActivity(restoredActivity.copy(lastModified = System.currentTimeMillis()))
                println("✅ Atividade restaurada: ${restoredActivity.title}")
            }
        }
    }

    private suspend fun ActivityRepository.addActivity(activity: Activity) {
        saveActivity(activity)
    }

    fun removeDeletedActivity(deletedActivityId: String) {
        viewModelScope.launch {
            deletedActivityRepository.removeDeletedActivity(deletedActivityId)
            println("🗑️ Atividade removida permanentemente da lixeira")
        }
    }
    
    fun clearAllDeletedActivities() {
        viewModelScope.launch {
            deletedActivityRepository.clearAllDeletedActivities()
            println("🗑️ Lixeira esvaziada")
        }
    }
    
    // --- Tarefas Finalizadas ---
    
    fun toggleCompletedActivitiesVisibility() {
        val newValue = !_uiState.value.showCompletedActivities
        _uiState.update { it.copy(showCompletedActivities = newValue) }
        viewModelScope.launch {
            settingsRepository.saveShowCompletedActivities(newValue)
        }
        updateAllDateDependentUI()
    }
    
    fun toggleMoonPhasesVisibility() {
        _uiState.update { it.copy(showMoonPhases = !it.showMoonPhases) }
    }
    
    fun getCompletedActivities(): List<Activity> {
        return _uiState.value.completedActivities
    }
    
    /**
     * Calcula os dados para o gráfico de barras dos últimos 7 dias
     */
    fun getLast7DaysCompletedTasksData(): List<com.mss.thebigcalendar.ui.components.BarChartData> {
        val today = LocalDate.now()
        val completedActivities = _uiState.value.completedActivities
        
        // Criar lista dos últimos 7 dias
        val last7Days = (0..6).map { daysAgo ->
            today.minusDays(daysAgo.toLong())
        }.reversed() // Do mais antigo para o mais recente
        
        return last7Days.map { date ->
            val dateString = date.toString()
            val count = completedActivities.count { activity ->
                activity.date == dateString
            }
            
            // Formatar o label do dia (ex: "Seg", "Ter", etc.)
            val dayLabel = when (date.dayOfWeek.value) {
                1 -> "Seg"
                2 -> "Ter" 
                3 -> "Qua"
                4 -> "Qui"
                5 -> "Sex"
                6 -> "Sáb"
                7 -> "Dom"
                else -> date.dayOfWeek.name.take(3)
            }
            
            com.mss.thebigcalendar.ui.components.BarChartData(
                label = dayLabel,
                value = count,
                color = when (count) {
                    0 -> Color(0xFF79747E) // outline color
                    in 1..2 -> Color(0xFF6750A4) // primary color
                    in 3..5 -> Color(0xFF625B71) // secondary color
                    else -> Color(0xFF7D5260) // tertiary color
                }
            )
        }
    }
    
    /**
     * Calcula os dados para o gráfico de barras do último ano (12 meses)
     */
    fun getLastYearCompletedTasksData(): List<com.mss.thebigcalendar.ui.components.BarChartData> {
        val today = LocalDate.now()
        val completedActivities = _uiState.value.completedActivities
        
        // Criar lista dos últimos 12 meses
        val last12Months = (0..11).map { monthsAgo ->
            today.minusMonths(monthsAgo.toLong())
        }.reversed() // Do mais antigo para o mais recente
        
        return last12Months.map { date ->
            val yearMonth = "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
            val count = completedActivities.count { activity ->
                activity.date.startsWith(yearMonth)
            }
            
            // Formatar o label do mês (ex: "Jan", "Fev", etc.)
            val monthLabel = when (date.monthValue) {
                1 -> "Jan"
                2 -> "Fev"
                3 -> "Mar"
                4 -> "Abr"
                5 -> "Mai"
                6 -> "Jun"
                7 -> "Jul"
                8 -> "Ago"
                9 -> "Set"
                10 -> "Out"
                11 -> "Nov"
                12 -> "Dez"
                else -> date.month.name.take(3)
            }
            
            com.mss.thebigcalendar.ui.components.BarChartData(
                label = monthLabel,
                value = count,
                color = when (count) {
                    0 -> Color(0xFF79747E) // outline color
                    in 1..5 -> Color(0xFF6750A4) // primary color
                    in 6..15 -> Color(0xFF625B71) // secondary color
                    in 16..30 -> Color(0xFF7D5260) // tertiary color
                    else -> Color(0xFF4A148C) // high productivity color
                }
            )
        }
    }
    
    // --- Backup ---
    
    fun onBackupIconClick() {
        println("💾 Abrindo tela de backup")
        _uiState.update { it.copy(isBackupScreenOpen = true, isSidebarOpen = false, isSettingsScreenOpen = false) }
    }
    
    fun closeBackupScreen() {
        println("🚪 Fechando tela de backup")
        _uiState.update { it.copy(
            isBackupScreenOpen = false,
            backupMessage = null,
            isSettingsScreenOpen = true
        ) }
    }
    
    /**
     * Verifica se o app tem permissão para sobrepor outros apps
     */
    fun hasOverlayPermission(): Boolean {
        return visibilityService.hasOverlayPermission()
    }
    
    /**
     * Solicita permissão para sobrepor outros apps
     */
    fun requestOverlayPermission(): Intent {
        return visibilityService.requestOverlayPermission()
    }
    
    /**
     * Testa o alerta de visibilidade alta
     */
    fun testHighVisibilityAlert() {
        visibilityService.testHighVisibilityAlert()
    }
    
    fun loadBackupFiles() {
        viewModelScope.launch {
            val directoryUriString = _uiState.value.backupDirectoryUri
            if (directoryUriString.isNullOrBlank()) {
                _uiState.update { it.copy(backupFiles = emptyList(), isListingLocalBackups = false) }
                return@launch
            }

            _uiState.update { it.copy(isListingLocalBackups = true) }
            try {
                val directoryUri = Uri.parse(directoryUriString)
                val backupDocumentFiles = backupService.listBackupFiles(directoryUri)
                val backupInfos = backupDocumentFiles.mapNotNull { file ->
                    backupService.getBackupInfo(file).getOrNull()
                }
                _uiState.update { it.copy(backupFiles = backupInfos, isListingLocalBackups = false) }
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "❌ Erro ao carregar arquivos de backup via SAF", e)
                _uiState.update { it.copy(
                    backupFiles = emptyList(), 
                    backupMessage = getApplication<Application>().getString(R.string.backup_load_failed), 
                    isBackupError = true,
                    isListingLocalBackups = false
                ) }
            }
        }
    }
    
    fun deleteBackupFile(backupUri: String) {
        viewModelScope.launch {
            val result = backupService.deleteBackupFile(Uri.parse(backupUri))
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(
                        backupMessage = getApplication<Application>().getString(R.string.backup_deleted_success),
                        isBackupError = false
                    ) }
                    loadBackupFiles()
                },
                onFailure = { exception ->
                    _uiState.update { it.copy(
                        backupMessage = getApplication<Application>().getString(R.string.backup_delete_failed, exception.message ?: ""),
                        isBackupError = true
                    ) }
                }
            )
        }
    }

    fun restoreFromBackup(backupUri: String, password: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    backupMessage = null,
                    isBackupError = false,
                    isRestoringBackup = true,
                    localBackupUriBeingRestored = backupUri,
                    restoreProgress = 0f
                ) }

                val restoreResult = backupService.restoreFromBackup(Uri.parse(backupUri), password)
                restoreResult.fold(
                    onSuccess = { result ->
                        clearAllCurrentData()
                        _uiState.update { it.copy(restoreProgress = 0.2f) }

                        result.activities.forEach { activity -> activityRepository.saveActivity(activity) }
                        _uiState.update { it.copy(restoreProgress = 0.5f) }

                        result.deletedActivities.forEach { deletedActivity -> deletedActivityRepository.addDeletedActivity(deletedActivity.originalActivity) }
                        _uiState.update { it.copy(restoreProgress = 0.65f) }

                        result.completedActivities.forEach { activity -> completedActivityRepository.addCompletedActivity(activity) }
                        _uiState.update { it.copy(restoreProgress = 0.8f) }

                        // Salvar e agendar os alarmes restaurados
                        val notificationService = NotificationService(getApplication())
                        val alarmService = com.mss.thebigcalendar.service.AlarmService(
                            getApplication(),
                            alarmRepository,
                            notificationService
                        )
                        result.alarms.forEach { alarm ->
                            alarmRepository.saveAlarm(alarm)
                            if (alarm.isEnabled) {
                                alarmService.scheduleAlarm(alarm)
                            }
                        }

                        _uiState.update { it.copy(
                            backupMessage = getApplication<Application>().getString(R.string.backup_restored_success),
                            isBackupError = false,
                            showDecryptionDialog = false,
                            decryptionBackupUri = null,
                            decryptionErrorMessage = null
                        ) }

                        loadData()
                        loadBackupFiles()
                        _uiState.update { it.copy(restoreProgress = 0.9f) }

                        result.activities.forEach { activity -> notificationService.scheduleNotification(activity) }

                        viewModelScope.launch {
                            delay(500)
                            notifyWidgetsDataChanged()
                            _uiState.update { it.copy(
                                isRestoringBackup = false,
                                localBackupUriBeingRestored = null,
                                restoreProgress = 1f
                            ) }
                        }
                    },
                    onFailure = { exception ->
                        if (exception is com.mss.thebigcalendar.crypto.DecryptionRequiredException || exception is com.mss.thebigcalendar.crypto.DecryptionFailedException) {
                            _uiState.update { it.copy(
                                showDecryptionDialog = true,
                                decryptionBackupUri = backupUri,
                                decryptionErrorMessage = exception.message,
                                isRestoringBackup = false
                            ) }
                        } else {
                            _uiState.update { it.copy(
                                backupMessage = getApplication<Application>().getString(R.string.backup_restore_failed, exception.message ?: ""),
                                isBackupError = true,
                                isRestoringBackup = false,
                                localBackupUriBeingRestored = null
                            ) }
                        }
                    }
                )

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    backupMessage = getApplication<Application>().getString(R.string.unexpected_error, e.message ?: ""),
                    isBackupError = true,
                    isRestoringBackup = false,
                    localBackupUriBeingRestored = null
                ) }
            }
        }
    }
    
    private suspend fun clearAllCurrentData() {
        try {
            // Cancelar alarmes existentes e limpar o repositório de alarmes
            val alarmService = com.mss.thebigcalendar.service.AlarmService(
                getApplication(),
                alarmRepository,
                NotificationService(getApplication())
            )
            val currentAlarms = alarmRepository.getAllAlarms()
            currentAlarms.forEach { alarm ->
                alarmService.cancelAlarm(alarm.id)
            }
            alarmRepository.clearAllAlarms()

            // Limpar todas as atividades
            val currentActivities = activityRepository.activities.first()
            currentActivities.forEach { activity ->
                activityRepository.deleteActivity(activity.id)
            }
            
            // Limpar lixeira
            deletedActivityRepository.clearAllDeletedActivities()
            
            // Limpar atividades concluídas
            completedActivityRepository.clearAllCompletedActivities()
            
            println("🗑️ Dados atuais limpos para restauração")
        } catch (e: Exception) {
            println("⚠️ Erro ao limpar dados atuais: ${e.message}")
        }
    }
    
    fun openJsonConfigScreen() {
        _uiState.update {
            it.copy(
                isJsonConfigScreenOpen = true,
                selectedJsonFileName = null,
                selectedJsonUri = null
            )
        }
    }
    
    fun openJsonConfigScreen(fileName: String, uri: android.net.Uri) {
        _uiState.update {
            it.copy(
                isJsonConfigScreenOpen = true,
                selectedJsonFileName = fileName,
                selectedJsonUri = uri
            )
        }
    }
    
    fun closeJsonConfigScreen() {
        _uiState.update { 
            it.copy(
                isJsonConfigScreenOpen = false,
                isSettingsScreenOpen = true, // Garantir que a tela de configurações permaneça aberta
                selectedJsonFileName = null,
                selectedJsonUri = null
            ) 
        }
    }
    
    fun saveJsonConfig(title: String, color: androidx.compose.ui.graphics.Color, jsonContent: String = "") {
        viewModelScope.launch {
            try {
                
                // Obter dados do estado antes de processar
                val currentState = _uiState.value
                val fileName = currentState.selectedJsonFileName
                val uri = currentState.selectedJsonUri
                
                if (fileName != null && uri != null) {
                    // Processar o arquivo JSON (salva atividades e depois o calendário)
                    processJsonFile(fileName, uri, title, color)
                } else if (jsonContent.isNotBlank()) {
                    // Processar conteúdo JSON digitado diretamente (salva atividades e depois o calendário)
                    processJsonContent(jsonContent, title, color)
                } else {
                    Log.e(TAG, "Nem arquivo nem conteúdo JSON fornecidos")
                }
                
                // Fechar a tela de configuração
                closeJsonConfigScreen()
                
                // Recarregar dados
                loadData()
                
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar configuração JSON", e)
            }
        }
    }
    
    private suspend fun processJsonFile(fileName: String, uri: android.net.Uri, calendarTitle: String, calendarColor: androidx.compose.ui.graphics.Color) {
        try {
            
            // Ler o arquivo JSON
            val jsonString = readJsonFile(uri)
            if (jsonString == null) {
                Log.e(TAG, "Erro ao ler arquivo JSON")
                return
            }
            
            // Fazer parse do JSON
            val jsonArray = JSONArray(jsonString)
            val schedules = mutableListOf<JsonSchedule>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    
                    val name = jsonObject.getString("name")
                    val date = jsonObject.getString("date")
                    val summary = try {
                        jsonObject.optString("summary", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao obter summary do item $i: ${e.message}")
                        null
                    }
                    val wikipediaLink = try {
                        jsonObject.optString("wikipediaLink", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao obter wikipediaLink do item $i: ${e.message}")
                        null
                    }
                    
                    
                    val schedule = JsonSchedule(
                        name = name,
                        date = date,
                        summary = summary,
                        wikipediaLink = wikipediaLink
                    )
                    schedules.add(schedule)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar item $i do JSON: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            
            // Converter para Activities
            val activities = schedules.map { jsonSchedule ->
                jsonSchedule.toActivity(java.time.LocalDate.now().year, calendarTitle, calendarColor)
            }
            
            // 1. Salvar no banco de dados primeiro!
            activityRepository.saveAllActivities(activities)
            
            // 2. Criar e salvar o calendário JSON depois, para que ao disparar o collect as atividades já existam
            val jsonCalendar = JsonCalendar(
                id = UUID.randomUUID().toString(),
                title = calendarTitle,
                color = calendarColor,
                fileName = fileName,
                importDate = System.currentTimeMillis(),
                isVisible = true
            )
            jsonCalendarRepository.saveJsonCalendar(jsonCalendar)
            
            // Recarregar atividades para atualizar a UI
            loadActivitiesForCurrentMonth()
            
            // Atualizar agendamentos JSON para a data selecionada
            updateJsonCalendarActivitiesForSelectedDate()
            
            // Notificar widgets sobre a mudança
            viewModelScope.launch {
                delay(500) // Aguardar 500ms para garantir que os dados foram persistidos
                notifyWidgetsDataChanged()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar arquivo JSON", e)
        }
    }
    
    private suspend fun processJsonContent(jsonContent: String, calendarTitle: String, calendarColor: androidx.compose.ui.graphics.Color) {
        try {
            
            
            
            // Fazer parse do JSON
            Log.d(TAG, "JSON string length: ${jsonContent.length}")
            val jsonArray = JSONArray(jsonContent)
            val schedules = mutableListOf<JsonSchedule>()
            
            for (i in 0 until jsonArray.length()) {
                try {
                    val jsonObject = jsonArray.getJSONObject(i)
                    
                    val name = jsonObject.getString("name")
                    val date = jsonObject.getString("date")
                    val summary = try {
                        jsonObject.optString("summary", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao obter summary do item $i: ${e.message}")
                        null
                    }
                    val wikipediaLink = try {
                        jsonObject.optString("wikipediaLink", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Erro ao obter wikipediaLink do item $i: ${e.message}")
                        null
                    }
                    
                    
                    val schedule = JsonSchedule(
                        name = name,
                        date = date,
                        summary = summary,
                        wikipediaLink = wikipediaLink
                    )
                    schedules.add(schedule)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Erro ao processar item $i do JSON: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            
            // Converter para Activities
            val activities = schedules.map { jsonSchedule ->
                jsonSchedule.toActivity(java.time.LocalDate.now().year, calendarTitle, calendarColor)
            }
            
            // 1. Salvar no banco de dados primeiro!
            activityRepository.saveAllActivities(activities)
            
            // 2. Criar e salvar o calendário JSON depois
            val jsonCalendar = JsonCalendar(
                id = UUID.randomUUID().toString(),
                title = calendarTitle,
                color = calendarColor,
                fileName = "conteudo_digitado.json",
                importDate = System.currentTimeMillis(),
                isVisible = true
            )
            jsonCalendarRepository.saveJsonCalendar(jsonCalendar)
            
            // Recarregar atividades para atualizar a UI
            loadActivitiesForCurrentMonth()
            
            // Atualizar agendamentos JSON para a data selecionada
            updateJsonCalendarActivitiesForSelectedDate()
            
            // Notificar widgets sobre a mudança
            viewModelScope.launch {
                delay(500) // Aguardar 500ms para garantir que os dados foram persistidos
                notifyWidgetsDataChanged()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar conteúdo JSON", e)
        }
    }

    private suspend fun readJsonFile(uri: android.net.Uri): String? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "InputStream é null para URI: $uri")
                return null
            }
            
            inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val content = reader.readText()
                content
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao ler arquivo JSON", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Carrega os calendários JSON importados
     */
    private fun loadJsonCalendars() {
        viewModelScope.launch {
            jsonCalendarRepository.getAllJsonCalendars().collect { calendars ->
                _uiState.update { it.copy(jsonCalendars = calendars) }
                // Processar agendamentos JSON como holidays
                viewModelScope.launch {
                    processJsonHolidays(calendars)
                }
                // Atualizar agendamentos JSON para a data selecionada
                updateJsonCalendarActivitiesForSelectedDate()
            }
        }
    }
    
    /**
     * Processa os calendários JSON e cria holidays para exibição no calendário
     */
    private suspend fun processJsonHolidays(calendars: List<JsonCalendar>) {
        val jsonHolidaysMap = mutableMapOf<String, MutableList<JsonHoliday>>()
        
        calendars.forEach { calendar ->
            if (calendar.isVisible) {
                // Buscar TODAS as atividades deste calendário (não apenas do mês atual)
                val calendarActivities = getAllJsonActivitiesForCalendar(calendar)
                
                // Converter atividades para JsonHoliday
                calendarActivities.forEach { activity ->
                    try {
                        val activityDate = LocalDate.parse(activity.date)
                        val monthDay = activityDate.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))
                        
                        val jsonHoliday = JsonHoliday(
                            id = activity.id,
                            name = activity.title,
                            date = monthDay,
                            summary = activity.description,
                            wikipediaLink = activity.wikipediaLink,
                            calendarId = calendar.id,
                            calendarTitle = calendar.title,
                            calendarColor = calendar.color
                        )
                        
                        jsonHolidaysMap.getOrPut(monthDay) { mutableListOf() }.add(jsonHoliday)
                    } catch (e: Exception) {
                        // Ignorar atividades com data inválida
                    }
                }
            }
        }
        
        _uiState.update { it.copy(jsonHolidays = jsonHolidaysMap) }
        updateAllDateDependentUI()
    }
    
    /**
     * Busca todas as atividades JSON de um calendário específico
     * Esta função busca em TODAS as atividades, não apenas do mês atual
     */
    private suspend fun getAllJsonActivitiesForCalendar(calendar: JsonCalendar): List<Activity> {
        return try {
            // Buscar todas as atividades do repositório (não apenas do mês atual)
            val allActivities = activityRepository.activities.first()
            
            // Filtrar apenas as atividades deste calendário JSON
            allActivities.filter { activity ->
                try {
                    // Verificar se é uma atividade JSON importada
                    val isJsonActivity = activity.location?.startsWith("JSON_IMPORTED_") == true
                    
                    if (!isJsonActivity) {
                        return@filter false
                    }
                    
                    // Comparar por título/location (preferencial) ou cor
                    val matchesLocation = activity.location == "JSON_IMPORTED_${calendar.title}"
                    val activityColorString = activity.categoryColor
                    val calendarColorString = String.format("#%08X", calendar.color.toArgb())
                    val matchesColor = activityColorString.equals(calendarColorString, ignoreCase = true)
                    
                    matchesLocation || matchesColor
                } catch (e: Exception) {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Erro ao buscar atividades JSON para calendário ${calendar.title}", e)
            emptyList()
        }
    }
    
    /**
     * Atualiza os agendamentos JSON para a data selecionada
     */
    private fun updateJsonCalendarActivitiesForSelectedDate() {
        val currentState = _uiState.value
        val selectedDate = currentState.selectedDate
        val visibleJsonCalendars = currentState.jsonCalendars.filter { it.isVisible }
        
        val jsonCalendarActivities = mutableMapOf<String, List<Activity>>()
        
        visibleJsonCalendars.forEach { jsonCalendar ->
            
            // Filtrar atividades que pertencem a este calendário JSON
            val calendarActivities = currentState.activities.filter { activity ->
                // Verificar se a atividade pertence a este calendário JSON
                val activityColor = try {
                    Color(android.graphics.Color.parseColor(activity.categoryColor))
                } catch (e: Exception) {
                    Color.Blue
                }
                
                val activityDate = try {
                    LocalDate.parse(activity.date)
                } catch (e: Exception) {
                    null
                }
                
                val isJsonImported = activity.location?.startsWith("JSON_IMPORTED_") == true
                val dateMatches = activityDate != null && 
                                  activityDate.month == selectedDate.month && 
                                  activityDate.dayOfMonth == selectedDate.dayOfMonth
                val colorMatches = activityColor == jsonCalendar.color
                
                dateMatches && colorMatches && activity.showInCalendar && isJsonImported
            }
            
            
            if (calendarActivities.isNotEmpty()) {
                jsonCalendarActivities[jsonCalendar.id] = calendarActivities
            }
        }
        
        
        _uiState.update { 
            it.copy(jsonCalendarActivitiesForSelectedDate = jsonCalendarActivities) 
        }
    }
    
    /**
     * Atualiza a visibilidade de um calendário JSON
     */
    fun toggleJsonCalendarVisibility(calendarId: String, isVisible: Boolean) {
        viewModelScope.launch {
            jsonCalendarRepository.updateJsonCalendarVisibility(calendarId, isVisible)
            // Atualizar diretamente o estado local e reprocessar holidays
            val currentCalendars = _uiState.value.jsonCalendars.map { calendar ->
                if (calendar.id == calendarId) calendar.copy(isVisible = isVisible) else calendar
            }
            _uiState.update { it.copy(jsonCalendars = currentCalendars) }
            // Limpar holidays existentes e reprocessar apenas os visíveis
            _uiState.update { it.copy(jsonHolidays = emptyMap()) }
            viewModelScope.launch { processJsonHolidays(currentCalendars) }
            updateJsonCalendarActivitiesForSelectedDate()
            // Atualizar o calendário mensal para refletir as mudanças
            updateAllDateDependentUI()
        }
    }
    
    /**
     * Remove um calendário JSON
     */
    fun removeJsonCalendar(calendarId: String) {
        viewModelScope.launch {
            jsonCalendarRepository.removeJsonCalendar(calendarId)
            // Atualizar diretamente o estado local e reprocessar holidays
            val currentCalendars = _uiState.value.jsonCalendars.filter { it.id != calendarId }
            _uiState.update { it.copy(jsonCalendars = currentCalendars) }
            // Limpar holidays existentes e reprocessar apenas os visíveis
            _uiState.update { it.copy(jsonHolidays = emptyMap()) }
            viewModelScope.launch { processJsonHolidays(currentCalendars) }
            updateJsonCalendarActivitiesForSelectedDate()
            // Atualizar o calendário mensal para refletir as mudanças
            updateAllDateDependentUI()
        }
    }
    
    /**
     * Mostra informações de um agendamento JSON
     */
    fun showJsonHolidayInfo(jsonHoliday: JsonHoliday) {
        _uiState.update { it.copy(jsonHolidayInfoToShow = jsonHoliday) }
    }
    
    /**
     * Esconde informações do agendamento JSON
     */
    fun hideJsonHolidayInfo() {
        _uiState.update { it.copy(jsonHolidayInfoToShow = null) }
    }

    /**
     * Remove atividades JSON antigas que não têm o campo location marcado corretamente
     */
    suspend fun cleanupOldJsonActivities() {
        try {
            val allActivities = activityRepository.activities.firstOrNull() ?: emptyList()
            val jsonCalendars = jsonCalendarRepository.getAllJsonCalendars().firstOrNull() ?: emptyList()
            
            // Identificar atividades que são JSON mas não têm location marcado
            val oldJsonActivities = allActivities.filter { activity ->
                // Verificar se é uma atividade JSON baseada na cor e título
                jsonCalendars.any { jsonCalendar ->
                    val calendarColorString = String.format("#%08X", jsonCalendar.color.toArgb())
                    activity.categoryColor == calendarColorString &&
                    activity.location?.startsWith("JSON_IMPORTED_") != true
                }
            }
            
            
            // Remover atividades JSON antigas
            oldJsonActivities.forEach { activity ->
                activityRepository.deleteActivity(activity.id)
            }
            
            // Recarregar dados após limpeza
            loadActivitiesForCurrentMonth()
            loadJsonCalendars()
            
        } catch (e: Exception) {
            Log.e("CalendarViewModel", "Erro ao limpar atividades JSON antigas", e)
        }
    }

    fun importPredefinedMilitaryCalendar() {
        viewModelScope.launch {
            try {
                val alreadyImported = jsonCalendarRepository.getAllJsonCalendars().first().any { it.id == "PREDEFINED_MILITARY_HOLIDAYS" }
                if (alreadyImported) return@launch
                
                val context = getApplication<Application>()
                val rawId = context.resources.getIdentifier("military_holidays", "raw", context.packageName)
                if (rawId == 0) {
                    Log.e(TAG, "Recurso raw/military_holidays não encontrado")
                    return@launch
                }
                val inputStream = context.resources.openRawResource(rawId)
                val jsonString = inputStream.use { it.bufferedReader().readText() }
                
                val calendarTitle = getApplication<Application>().getString(R.string.military_holidays)
                val calendarColor = androidx.compose.ui.graphics.Color(0xFF2E7D32)
                
                val jsonArray = JSONArray(jsonString)
                val schedules = mutableListOf<JsonSchedule>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val date = jsonObject.getString("date")
                    val summary = try {
                        jsonObject.optString("summary", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    val wikipediaLink = try {
                        jsonObject.optString("wikipediaLink", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    schedules.add(JsonSchedule(name, date, summary, wikipediaLink))
                }
                
                val activities = schedules.map { jsonSchedule ->
                    jsonSchedule.toActivity(java.time.LocalDate.now().year, calendarTitle, calendarColor)
                }
                
                // 1. Salvar no banco de dados primeiro!
                activityRepository.saveAllActivities(activities)
                
                // 2. Salvar o calendário depois
                val jsonCalendar = JsonCalendar(
                    id = "PREDEFINED_MILITARY_HOLIDAYS",
                    title = calendarTitle,
                    color = calendarColor,
                    fileName = "military_holidays.json",
                    importDate = System.currentTimeMillis(),
                    isVisible = true
                )
                jsonCalendarRepository.saveJsonCalendar(jsonCalendar)
                
                // Recarregar atividades para atualizar a UI
                loadActivitiesForCurrentMonth()
                updateJsonCalendarActivitiesForSelectedDate()
                
                viewModelScope.launch {
                    delay(500)
                    notifyWidgetsDataChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar feriados militares predefinidos", e)
            }
        }
    }

    fun importPredefinedSaintsCalendar() {
        viewModelScope.launch {
            try {
                val alreadyImported = jsonCalendarRepository.getAllJsonCalendars().first().any { it.id == "PREDEFINED_SAINTS" }
                if (alreadyImported) return@launch
                
                val context = getApplication<Application>()
                val rawId = context.resources.getIdentifier("saints_data", "raw", context.packageName)
                if (rawId == 0) {
                    Log.e(TAG, "Recurso raw/saints_data não encontrado")
                    return@launch
                }
                val inputStream = context.resources.openRawResource(rawId)
                val jsonString = inputStream.use { it.bufferedReader().readText() }
                
                val calendarTitle = getApplication<Application>().getString(R.string.catholic_saint_days)
                val calendarColor = androidx.compose.ui.graphics.Color(0xFFFFA000)
                
                val jsonArray = JSONArray(jsonString)
                val schedules = mutableListOf<JsonSchedule>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val date = jsonObject.getString("date")
                    val summary = try {
                        jsonObject.optString("summary", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    val wikipediaLink = try {
                        jsonObject.optString("wikipediaLink", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    schedules.add(JsonSchedule(name, date, summary, wikipediaLink))
                }
                
                val activities = schedules.map { jsonSchedule ->
                    jsonSchedule.toActivity(java.time.LocalDate.now().year, calendarTitle, calendarColor)
                }
                
                // 1. Salvar no banco de dados primeiro!
                activityRepository.saveAllActivities(activities)
                
                // 2. Salvar o calendário depois
                val jsonCalendar = JsonCalendar(
                    id = "PREDEFINED_SAINTS",
                    title = calendarTitle,
                    color = calendarColor,
                    fileName = "saints_data.json",
                    importDate = System.currentTimeMillis(),
                    isVisible = true
                )
                jsonCalendarRepository.saveJsonCalendar(jsonCalendar)
                
                // Recarregar atividades para atualizar a UI
                loadActivitiesForCurrentMonth()
                updateJsonCalendarActivitiesForSelectedDate()
                
                viewModelScope.launch {
                    delay(500)
                    notifyWidgetsDataChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar santos católicos predefinidos", e)
            }
        }
    }

    fun importPredefinedProfessionalDaysCalendar() {
        viewModelScope.launch {
            try {
                val alreadyImported = jsonCalendarRepository.getAllJsonCalendars().first().any { it.id == "PREDEFINED_PROFESSIONAL_DAYS" }
                if (alreadyImported) return@launch
                
                val context = getApplication<Application>()
                val rawId = context.resources.getIdentifier("professional_days", "raw", context.packageName)
                if (rawId == 0) {
                    Log.e(TAG, "Recurso raw/professional_days não encontrado")
                    return@launch
                }
                val inputStream = context.resources.openRawResource(rawId)
                val jsonString = inputStream.use { it.bufferedReader().readText() }
                
                val calendarTitle = getApplication<Application>().getString(R.string.professional_days)
                val calendarColor = androidx.compose.ui.graphics.Color(0xFF1976D2)
                
                val jsonArray = JSONArray(jsonString)
                val schedules = mutableListOf<JsonSchedule>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val name = jsonObject.getString("name")
                    val date = jsonObject.getString("date")
                    val summary = try {
                        jsonObject.optString("summary", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    val wikipediaLink = try {
                        jsonObject.optString("wikipediaLink", null).takeIf { it.isNotEmpty() }
                    } catch (e: Exception) {
                        null
                    }
                    schedules.add(JsonSchedule(name, date, summary, wikipediaLink))
                }
                
                val activities = schedules.map { jsonSchedule ->
                    jsonSchedule.toActivity(java.time.LocalDate.now().year, calendarTitle, calendarColor)
                }
                
                // 1. Salvar no banco de dados primeiro!
                activityRepository.saveAllActivities(activities)
                
                // 2. Salvar o calendário depois
                val jsonCalendar = JsonCalendar(
                    id = "PREDEFINED_PROFESSIONAL_DAYS",
                    title = calendarTitle,
                    color = calendarColor,
                    fileName = "professional_days.json",
                    importDate = System.currentTimeMillis(),
                    isVisible = true
                )
                jsonCalendarRepository.saveJsonCalendar(jsonCalendar)
                
                // Recarregar atividades para atualizar a UI
                loadActivitiesForCurrentMonth()
                updateJsonCalendarActivitiesForSelectedDate()
                
                viewModelScope.launch {
                    delay(500)
                    notifyWidgetsDataChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao importar datas profissionais predefinidas", e)
            }
        }
    }

    // ===== GEMINI ASSISTANT FUNCTIONS =====

    fun openGeminiAssistantFromOutside() {
        _uiState.update {
            it.copy(
                isSidebarOpen = false,
                isCalendarVisualizationSettingsOpen = false,
                isSyncScreenOpen = false,
                isSettingsScreenOpen = false,
                isSearchScreenOpen = false,
                isChartScreenOpen = false,
                isNotesScreenOpen = false,
                isAlarmsScreenOpen = false,
                isTrashScreenOpen = false,
                isBackupScreenOpen = false,
                isCompletedTasksScreenOpen = false,
                isPrintCalendarScreenOpen = false,
                isJsonConfigScreenOpen = false,
                activityToEdit = null,
                isGeminiAssistantOpen = true,
                geminiErrorMessage = null
            )
        }
    }

    fun openGeminiAssistant() {
        _uiState.update {
            it.copy(
                isGeminiAssistantOpen = true,
                geminiErrorMessage = null
            )
        }
    }

    fun closeGeminiAssistant() {
        _uiState.update {
            it.copy(
                isGeminiAssistantOpen = false
            )
        }
    }

    fun openGeminiSettings() {
        _uiState.update { it.copy(isGeminiSettingsOpen = true) }
    }

    fun closeGeminiSettings() {
        _uiState.update { it.copy(isGeminiSettingsOpen = false) }
    }

    fun saveGeminiSettings(apiKey: String, voiceFeedback: Boolean, model: String) {
        viewModelScope.launch {
            settingsRepository.saveGeminiApiKey(apiKey)
            settingsRepository.saveGeminiVoiceFeedback(voiceFeedback)
            settingsRepository.saveGeminiModel(model)
        }
    }

    fun speakGeminiResponse(text: String) {
        if (_uiState.value.geminiVoiceFeedback && isTtsReady && text.isNotBlank()) {
            try {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "gemini_tts_${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.e("CalendarViewModel", "Erro ao reproduzir voz", e)
            }
        }
    }

    fun updateGeminiActivityDescription(newDescription: String) {
        val currentActivity = _uiState.value.geminiLastActivity ?: return
        val updatedActivity = currentActivity.copy(
            description = newDescription,
            lastModified = System.currentTimeMillis()
        )
        viewModelScope.launch {
            activityRepository.saveActivity(updatedActivity)
            _uiState.update {
                it.copy(
                    geminiLastActivity = updatedActivity,
                    activities = it.activities.map { act -> if (act.id == updatedActivity.id) updatedActivity else act }
                )
            }
            loadActivitiesForCurrentMonth()
            notifyWidgetsDataChanged()
        }
    }

    fun processGeminiCommand(prompt: String) {
        if (prompt.isBlank()) return
        val apiKey = _uiState.value.geminiApiKey
        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(
                    isGeminiAssistantOpen = true,
                    isGeminiSettingsOpen = true,
                    geminiErrorMessage = getApplication<Application>().getString(R.string.gemini_no_api_key)
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isGeminiAssistantOpen = true,
                isGeminiProcessing = true,
                geminiLastPrompt = prompt,
                geminiErrorMessage = null,
                geminiErrorDetails = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val existingActivities = _uiState.value.activities
            val model = _uiState.value.geminiModel
            val result = geminiService.executeCommand(
                prompt = prompt,
                apiKey = apiKey,
                model = model,
                existingActivities = existingActivities
            )

            when (result) {
                is GeminiExecutionResult.Error -> {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiErrorMessage = result.message,
                                geminiErrorDetails = result.details
                            )
                        }
                    }
                }
                is GeminiExecutionResult.Success -> {
                    handleGeminiCommandResult(result.command)
                }
            }
        }
    }

    private suspend fun handleGeminiCommandResult(command: GeminiCommandResult) {
        val notificationService = NotificationService(getApplication())

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

                val categoryColor = when (actType) {
                    ActivityType.TASK -> "#3B82F6"
                    ActivityType.NOTE -> "#10B981"
                    ActivityType.BIRTHDAY -> "#FF69B4"
                    ActivityType.EVENT -> "#F43F5E"
                    ActivityType.COMMEMORATIVE -> "#8B5CF6"
                }

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

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isGeminiProcessing = false,
                            geminiLastResult = command,
                            geminiLastActivity = newActivity,
                            geminiPreviousActivity = null,
                            canUndoGeminiAction = true,
                            geminiErrorMessage = null,
                            geminiErrorDetails = null
                        )
                    }
                    speakGeminiResponse(command.replyMessage)
                }

                loadActivitiesForCurrentMonth()
                notifyWidgetsDataChanged()
            }

            GeminiAction.UPDATE -> {
                val allActivities = _uiState.value.activities
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

                    val updated = target.copy(
                        title = command.title ?: target.title,
                        date = command.date ?: target.date,
                        description = command.description ?: target.description,
                        startTime = command.startTime?.let { try { java.time.LocalTime.parse(it) } catch (e: Exception) { target.startTime } } ?: target.startTime,
                        endTime = command.endTime?.let { try { java.time.LocalTime.parse(it) } catch (e: Exception) { target.endTime } } ?: target.endTime,
                        isAllDay = command.isAllDay,
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

                    // Se a repetição mudou e foi removida, remover instâncias antigas
                    if (target.recurrenceRule != updated.recurrenceRule &&
                        (updated.recurrenceRule.isNullOrEmpty() || updated.recurrenceRule == "NONE")) {
                        removeRecurringInstances(target)
                    }

                    activityRepository.saveActivity(updated)
                    if (updated.notificationSettings.isEnabled) {
                        notificationService.cancelActivityNotifications(target)
                        notificationService.scheduleNotification(updated)
                    }

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiLastActivity = updated,
                                geminiPreviousActivity = target,
                                canUndoGeminiAction = true,
                                geminiErrorMessage = null,
                                geminiErrorDetails = null
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }

                    loadActivitiesForCurrentMonth()
                    notifyWidgetsDataChanged()
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiErrorMessage = "Não encontrei o agendamento para atualizar.",
                                geminiErrorDetails = "Nenhuma atividade corresponde ao título ou ID informado no comando."
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }
                }
            }

            GeminiAction.DELETE -> {
                val allActivities = _uiState.value.activities
                val target = command.targetActivityId?.let { id -> allActivities.find { it.id == id } }
                    ?: command.targetActivityTitle?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }
                    ?: command.title?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }

                if (target != null) {
                    notificationService.cancelActivityNotifications(target)
                    deletedActivityRepository.addDeletedActivity(target)
                    activityRepository.deleteActivity(target.id)

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiLastActivity = null,
                                geminiPreviousActivity = target,
                                canUndoGeminiAction = true,
                                geminiErrorMessage = null,
                                geminiErrorDetails = null
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }

                    loadActivitiesForCurrentMonth()
                    notifyWidgetsDataChanged()
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiErrorMessage = "Não encontrei o agendamento para excluir.",
                                geminiErrorDetails = "Nenhuma atividade corresponde ao título ou ID informado no comando."
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }
                }
            }

            GeminiAction.COMPLETE -> {
                val allActivities = _uiState.value.activities
                val target = command.targetActivityId?.let { id -> allActivities.find { it.id == id } }
                    ?: command.targetActivityTitle?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }
                    ?: command.title?.let { title ->
                        allActivities.find { it.title.contains(title, ignoreCase = true) }
                    }

                if (target != null) {
                    val completed = target.copy(isCompleted = true, lastModified = System.currentTimeMillis())
                    completedActivityRepository.addCompletedActivity(completed)
                    activityRepository.saveActivity(completed)
                    notificationService.cancelActivityNotifications(target)

                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiLastActivity = completed,
                                geminiPreviousActivity = target,
                                canUndoGeminiAction = true,
                                geminiErrorMessage = null,
                                geminiErrorDetails = null
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }

                    loadActivitiesForCurrentMonth()
                    notifyWidgetsDataChanged()
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                isGeminiProcessing = false,
                                geminiLastResult = command,
                                geminiErrorMessage = "Não encontrei a tarefa para concluir.",
                                geminiErrorDetails = "Nenhuma atividade corresponde ao título ou ID informado no comando."
                            )
                        }
                        speakGeminiResponse(command.replyMessage)
                    }
                }
            }

            GeminiAction.QUERY, GeminiAction.NONE -> {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isGeminiProcessing = false,
                            geminiLastResult = command,
                            geminiErrorMessage = null,
                            geminiErrorDetails = null
                        )
                    }
                    speakGeminiResponse(command.replyMessage)
                }
            }
        }
    }

    fun undoLastGeminiAction() {
        val lastResult = _uiState.value.geminiLastResult ?: return
        val lastActivity = _uiState.value.geminiLastActivity
        val previousActivity = _uiState.value.geminiPreviousActivity
        val notificationService = NotificationService(getApplication())

        viewModelScope.launch(Dispatchers.IO) {
            when (lastResult.action) {
                GeminiAction.CREATE -> {
                    if (lastActivity != null) {
                        notificationService.cancelActivityNotifications(lastActivity)
                        if (!lastActivity.recurrenceRule.isNullOrEmpty() && lastActivity.recurrenceRule != "NONE") {
                            removeRecurringInstances(lastActivity)
                        }
                        activityRepository.deleteActivity(lastActivity.id)
                    }
                }
                GeminiAction.UPDATE -> {
                    if (previousActivity != null) {
                        activityRepository.saveActivity(previousActivity)
                        notificationService.scheduleNotification(previousActivity)
                    }
                }
                GeminiAction.DELETE -> {
                    if (previousActivity != null) {
                        deletedActivityRepository.removeDeletedActivity(previousActivity.id)
                        activityRepository.saveActivity(previousActivity)
                        if (previousActivity.notificationSettings.isEnabled) {
                            notificationService.scheduleNotification(previousActivity)
                        }
                    }
                }
                GeminiAction.COMPLETE -> {
                    if (previousActivity != null) {
                        completedActivityRepository.removeCompletedActivity(previousActivity.id)
                        val reopened = previousActivity.copy(isCompleted = false)
                        activityRepository.saveActivity(reopened)
                    }
                }
                else -> {}
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        canUndoGeminiAction = false,
                        geminiLastActivity = null,
                        geminiPreviousActivity = null,
                        geminiLastResult = null,
                        geminiErrorMessage = null,
                        geminiErrorDetails = null
                    )
                }
                val undoneMsg = getApplication<Application>().getString(R.string.gemini_action_undone)
                speakGeminiResponse(undoneMsg)
            }

            loadActivitiesForCurrentMonth()
            notifyWidgetsDataChanged()
        }
    }
}