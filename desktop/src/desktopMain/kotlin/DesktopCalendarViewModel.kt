import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.mss.thebigcalendar.data.getDataStoreProvider
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.HolidayType
import com.mss.thebigcalendar.data.model.Theme
import com.mss.thebigcalendar.data.model.VisibilityLevel
import com.mss.thebigcalendar.data.model.NotificationSettings
import com.mss.thebigcalendar.data.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

@Serializable
data class DesktopFilterOptions(
    val showHolidays: Boolean = true,
    val showSaintDays: Boolean = false,
    val showEvents: Boolean = true,
    val showTasks: Boolean = true,
    val showNotes: Boolean = true,
    val showBirthdays: Boolean = true,
    val showProfessionalDays: Boolean = false,
    val showMilitaryHolidays: Boolean = false
)

@Serializable
data class DesktopSidebarFilterVisibility(
    val showHolidays: Boolean = true,
    val showSaintDays: Boolean = true,
    val showEvents: Boolean = true,
    val showTasks: Boolean = true,
    val showNotes: Boolean = true,
    val showBirthdays: Boolean = true,
    val showProfessionalDays: Boolean = true,
    val showMilitaryHolidays: Boolean = true,
    val showCompletedTasks: Boolean = true,
    val showMoonPhases: Boolean = true
)

@Serializable
data class DesktopQuote(
    val autor: String,
    val frase: String
)

@Serializable
data class DesktopJsonCalendar(
    val id: String,
    val title: String,
    val isVisible: Boolean
)

@Serializable
data class DesktopSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: String, // "yyyy-MM-dd"
    val type: Type
) {
    enum class Type {
        ACTIVITY,
        HOLIDAY
    }
}

@Serializable
data class DesktopCloudBackupInfo(
    val id: String,
    val name: String,
    val createdTime: String
)

data class DesktopUiState(
    val displayedYearMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val activities: List<Activity> = emptyList(),
    val filterOptions: DesktopFilterOptions = DesktopFilterOptions(),
    val theme: Theme = Theme.SYSTEM,
    val pureBlackTheme: Boolean = false,
    val welcomeName: String = "Usuário",
    val searchQuery: String = "",
    val searchResults: List<DesktopSearchResult> = emptyList(),
    val activityToEdit: Activity? = null,
    val showSettings: Boolean = false,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val cloudBackups: List<DesktopCloudBackupInfo> = emptyList(),
    val showCloudBackupDialog: Boolean = false,
    val isFetchingCloudBackups: Boolean = false,
    val quote: DesktopQuote? = null,
    val isRestoringBackup: Boolean = false,
    val restoreProgress: Float = 0f,
    val deletedActivityIds: List<String> = emptyList(),
    val googleAccountEmail: String? = null,
    val sidebarFilterVisibility: DesktopSidebarFilterVisibility = DesktopSidebarFilterVisibility(),
    val showCompletedActivities: Boolean = false,
    val showMoonPhases: Boolean = false,
    val jsonCalendars: List<DesktopJsonCalendar> = emptyList(),
    val viewMode: ViewMode = ViewMode.MONTHLY,
    val isEncryptionEnabled: Boolean = false,
    val encryptionPassword: String = "",
    val showDecryptionDialog: Boolean = false,
    val isDecryptionLoading: Boolean = false,
    val decryptionBackupFile: java.io.File? = null,
    val decryptionErrorMessage: String? = null,
    val duplicateGroups: List<List<Activity>> = emptyList(),
    val showDuplicateDialog: Boolean = false
)

class DesktopCalendarViewModel(private val scope: CoroutineScope) {

    private val dataStore = getDataStoreProvider().create("desktop_calendar_settings")

    private val _uiState = MutableStateFlow(DesktopUiState())
    val uiState: StateFlow<DesktopUiState> = _uiState.asStateFlow()

    // Chaves do DataStore
    private val KEY_ACTIVITIES = stringPreferencesKey("activities")
    private val KEY_DELETED_ACTIVITIES = stringPreferencesKey("deleted_activities")
    private val KEY_THEME = stringPreferencesKey("theme")
    private val KEY_PURE_BLACK = booleanPreferencesKey("pure_black_theme")
    private val KEY_WELCOME_NAME = stringPreferencesKey("welcome_name")
    private val KEY_FILTERS = stringPreferencesKey("filters")
    private val KEY_SIDEBAR_FILTER_VISIBILITY = stringPreferencesKey("sidebar_filter_visibility")
    private val KEY_SHOW_COMPLETED_ACTIVITIES = booleanPreferencesKey("show_completed_activities")
    private val KEY_SHOW_MOON_PHASES = booleanPreferencesKey("show_moon_phases")
    private val KEY_LAST_QUOTE_DATE = stringPreferencesKey("last_quote_date")
    private val KEY_LAST_QUOTE_INDEX = intPreferencesKey("last_quote_index")
    private val KEY_IS_ENCRYPTION_ENABLED = booleanPreferencesKey("is_encryption_enabled")
    private val KEY_ENCRYPTION_PASSWORD = stringPreferencesKey("encryption_password")

    // Listas pré-carregadas de feriados e datas especiais
    var nationalHolidays: List<Holiday> = emptyList()
        private set
    var saintDays: List<Holiday> = emptyList()
        private set
    var professionalDays: List<Holiday> = emptyList()
        private set
    var militaryHolidays: List<Holiday> = emptyList()
        private set
    var commemorativeDates: List<Holiday> = emptyList()
        private set

    init {
        loadPredefinedData()
        loadData()
        if (hasTokens()) {
            checkGoogleAccount()
            syncActivitiesWithCloud()
        }

        scope.launch {
            _uiState
                .map { it.activities }
                .distinctUntilChanged()
                .collect { activitiesList ->
                    val duplicates = getDuplicateGroups(activitiesList)
                    _uiState.update { it.copy(duplicateGroups = duplicates) }
                }
        }
    }

    private fun loadPredefinedData() {
        // Feriados Nacionais fixos
        nationalHolidays = listOf(
            Holiday("Confraternização Universal", "01-01", HolidayType.NATIONAL, "Ano Novo - Celebração universal do início do ano civil."),
            Holiday("Carnaval", "03-03", HolidayType.NATIONAL, "Carnaval - Ponto facultativo / Festividade nacional."),
            Holiday("Carnaval", "03-04", HolidayType.NATIONAL, "Carnaval - Terça-feira de Carnaval."),
            Holiday("Paixão de Cristo", "04-18", HolidayType.NATIONAL, "Sexta-feira Santa - Celebração religiosa cristã."),
            Holiday("Tiradentes", "04-21", HolidayType.NATIONAL, "Homenagem a Joaquim José da Silva Xavier (Tiradentes), mártir da Inconfidência Mineira."),
            Holiday("Dia do Trabalho", "05-01", HolidayType.NATIONAL, "Dia do Trabalhador - Celebração dos direitos trabalhistas."),
            Holiday("Corpus Christi", "06-19", HolidayType.NATIONAL, "Corpus Christi - Celebração religiosa católica."),
            Holiday("Independência do Brasil", "09-07", HolidayType.NATIONAL, "Proclamação da Independência do Brasil em relação a Portugal (1822)."),
            Holiday("Nossa Sr.a Aparecida - Padroeira do Brasil", "10-12", HolidayType.NATIONAL, "Dia das Crianças / Dia de Nossa Senhora Aparecida, padroeira do país."),
            Holiday("Finados", "11-02", HolidayType.NATIONAL, "Dia de Finados - Dia de recordação dos fiéis falecidos."),
            Holiday("Proclamação da República", "11-15", HolidayType.NATIONAL, "Proclamação da República Brasileira (1889)."),
            Holiday("Dia Nacional de Zumbi e da Consciência Negra", "11-20", HolidayType.NATIONAL, "Homenagem a Zumbi dos Palmares e reflexão sobre a cultura e inserção negra."),
            Holiday("Natal", "12-25", HolidayType.NATIONAL, "Celebração cristã do nascimento de Jesus Cristo.")
        )

        // Datas comemorativas
        commemorativeDates = listOf(
            Holiday("Dia Internacional da Mulher", "03-08", HolidayType.COMMEMORATIVE, "Celebração das conquistas sociais, políticas e econômicas das mulheres."),
            Holiday("Dia das Mães", "05-11", HolidayType.COMMEMORATIVE, "Homenagem especial a todas as mães."),
            Holiday("Dia dos Namorados", "06-12", HolidayType.COMMEMORATIVE, "Celebração do amor e da união entre casais."),
            Holiday("Dia do Amigo", "07-20", HolidayType.COMMEMORATIVE, "Celebração da amizade."),
            Holiday("Dia dos Pais", "08-10", HolidayType.COMMEMORATIVE, "Homenagem especial a todos os pais."),
            Holiday("Dia das Crianças", "10-12", HolidayType.COMMEMORATIVE, "Celebração da infância e direitos da criança.")
        )

        // Carregar do classpath
        saintDays = parsePredefinedJson("saints_data.json", HolidayType.SAINT)
        professionalDays = parsePredefinedJson("professional_days.json", HolidayType.JSON_IMPORT)
        militaryHolidays = parsePredefinedJson("military_holidays.json", HolidayType.JSON_IMPORT)
        loadQuotes()
    }

    private var quotes: List<DesktopQuote> = emptyList()

    private fun loadQuotes() {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("frases.json")
            if (stream != null) {
                val jsonString = stream.bufferedReader().use { it.readText() }
                quotes = Json { ignoreUnknownKeys = true }.decodeFromString<List<DesktopQuote>>(jsonString)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parsePredefinedJson(fileName: String, type: HolidayType): List<Holiday> {
        return try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(fileName)
                ?: return emptyList()
            val jsonString = stream.bufferedReader().use { it.readText() }
            
            val items = Json { ignoreUnknownKeys = true }.decodeFromString<List<JsonPredefinedItem>>(jsonString)
            items.map {
                Holiday(
                    name = it.name,
                    date = it.date,
                    type = type,
                    summary = it.summary,
                    wikipediaLink = it.wikipediaLink
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Serializable
    private data class JsonPredefinedItem(
        val name: String,
        val date: String,
        val summary: String? = null,
        val wikipediaLink: String? = null
    )

    private fun loadData() {
        scope.launch {
            dataStore.data.catch { e ->
                e.printStackTrace()
            }.collect { preferences ->
                val themeStr = preferences[KEY_THEME] ?: "SYSTEM"
                val theme = try { Theme.valueOf(themeStr) } catch(e: Exception) { Theme.SYSTEM }
                val pureBlack = preferences[KEY_PURE_BLACK] ?: false
                val welcomeName = preferences[KEY_WELCOME_NAME] ?: "Usuário"
                
                val filterStr = preferences[KEY_FILTERS]
                val filters = if (filterStr != null) {
                    try {
                        Json.decodeFromString<DesktopFilterOptions>(filterStr)
                    } catch(e: Exception) {
                        DesktopFilterOptions()
                    }
                } else {
                    DesktopFilterOptions()
                }

                val sidebarFilterVisibilityStr = preferences[KEY_SIDEBAR_FILTER_VISIBILITY]
                val sidebarFilterVisibility = if (sidebarFilterVisibilityStr != null) {
                    try {
                        Json.decodeFromString<DesktopSidebarFilterVisibility>(sidebarFilterVisibilityStr)
                    } catch(e: Exception) {
                        DesktopSidebarFilterVisibility()
                    }
                } else {
                    DesktopSidebarFilterVisibility()
                }

                val showCompletedActivities = preferences[KEY_SHOW_COMPLETED_ACTIVITIES] ?: false
                val showMoonPhases = preferences[KEY_SHOW_MOON_PHASES] ?: false
                val isEncryptionEnabled = preferences[KEY_IS_ENCRYPTION_ENABLED] ?: false
                val encryptionPassword = preferences[KEY_ENCRYPTION_PASSWORD] ?: ""

                val activitiesStr = preferences[KEY_ACTIVITIES]
                val activitiesList = if (activitiesStr != null) {
                    try {
                        Json.decodeFromString<List<Activity>>(activitiesStr)
                    } catch(e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val deletedStr = preferences[KEY_DELETED_ACTIVITIES]
                val deletedList = if (deletedStr != null) {
                    try {
                        Json.decodeFromString<List<String>>(deletedStr)
                    } catch(e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                val lastDate = preferences[KEY_LAST_QUOTE_DATE] ?: ""
                val lastIndex = preferences[KEY_LAST_QUOTE_INDEX] ?: 0
                    
                    val today = LocalDate.now().toString()
                    val selectedQuote = if (quotes.isNotEmpty()) {
                        if (today != lastDate) {
                            val nextIndex = (lastIndex + 1) % quotes.size
                            scope.launch {
                                dataStore.edit { prefs ->
                                    prefs[KEY_LAST_QUOTE_DATE] = today
                                    prefs[KEY_LAST_QUOTE_INDEX] = nextIndex
                                }
                            }
                            quotes[nextIndex]
                        } else {
                            quotes[lastIndex]
                        }
                    } else null

                    _uiState.update {
                        it.copy(
                            theme = theme,
                            pureBlackTheme = pureBlack,
                            welcomeName = welcomeName,
                            filterOptions = filters,
                            activities = activitiesList,
                            sidebarFilterVisibility = sidebarFilterVisibility,
                            showCompletedActivities = showCompletedActivities,
                            showMoonPhases = showMoonPhases,
                            quote = selectedQuote,
                            deletedActivityIds = deletedList,
                            isEncryptionEnabled = isEncryptionEnabled,
                            encryptionPassword = encryptionPassword
                        )
                    }

                try {
                    val home = System.getProperty("user.home")
                    val dir = java.io.File(home, ".thebigcalendar")
                    if (!dir.exists()) dir.mkdirs()
                    val activitiesFile = java.io.File(dir, "activities.json")
                    if (activitiesStr != null) {
                        activitiesFile.writeText(activitiesStr)
                    } else {
                        activitiesFile.writeText("[]")
                    }
                } catch(e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveData() {
        scope.launch {
            val activitiesJson = Json.encodeToString(_uiState.value.activities)
            val deletedJson = Json.encodeToString(_uiState.value.deletedActivityIds)
            dataStore.edit { preferences ->
                preferences[KEY_THEME] = _uiState.value.theme.name
                preferences[KEY_PURE_BLACK] = _uiState.value.pureBlackTheme
                preferences[KEY_WELCOME_NAME] = _uiState.value.welcomeName
                preferences[KEY_FILTERS] = Json.encodeToString(_uiState.value.filterOptions)
                preferences[KEY_ACTIVITIES] = activitiesJson
                preferences[KEY_DELETED_ACTIVITIES] = deletedJson
                preferences[KEY_SIDEBAR_FILTER_VISIBILITY] = Json.encodeToString(_uiState.value.sidebarFilterVisibility)
                preferences[KEY_SHOW_COMPLETED_ACTIVITIES] = _uiState.value.showCompletedActivities
                preferences[KEY_SHOW_MOON_PHASES] = _uiState.value.showMoonPhases
                preferences[KEY_IS_ENCRYPTION_ENABLED] = _uiState.value.isEncryptionEnabled
                preferences[KEY_ENCRYPTION_PASSWORD] = _uiState.value.encryptionPassword
            }

            try {
                withContext(Dispatchers.IO) {
                    val home = System.getProperty("user.home")
                    val dir = java.io.File(home, ".thebigcalendar")
                    if (!dir.exists()) dir.mkdirs()
                    val activitiesFile = java.io.File(dir, "activities.json")
                    activitiesFile.writeText(activitiesJson)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun updateDisplayedMonth(offset: Int) {
        _uiState.update {
            val newMonth = it.displayedYearMonth.plusMonths(offset.toLong())
            it.copy(displayedYearMonth = newMonth)
        }
    }

    fun setDisplayedMonth(yearMonth: YearMonth) {
        _uiState.update { it.copy(displayedYearMonth = yearMonth) }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun updateDisplayedYear(offset: Int) {
        _uiState.update {
            val newMonth = it.displayedYearMonth.plusYears(offset.toLong())
            it.copy(displayedYearMonth = newMonth)
        }
    }

    fun onYearlyMonthClicked(yearMonth: YearMonth) {
        _uiState.update {
            it.copy(
                displayedYearMonth = yearMonth,
                viewMode = ViewMode.MONTHLY
            )
        }
    }

    fun addOrUpdateActivity(
        title: String,
        description: String?,
        date: String,
        startTime: LocalTime?,
        endTime: LocalTime?,
        isAllDay: Boolean,
        categoryColor: String,
        type: ActivityType
    ): Boolean {
        val currentList = _uiState.value.activities.toMutableList()
        val editingActivity = _uiState.value.activityToEdit

        val baseIdToSave = (editingActivity?.id ?: "").split("_").first()
        val targetLocation = editingActivity?.location
        val targetRecurrenceRule = editingActivity?.recurrenceRule

        val isDuplicate = currentList.any { existing ->
            val existingBaseId = existing.id.split("_").first()
            if (existingBaseId == baseIdToSave) false
            else if (existing.isCompleted) false // Ignorar concluídos
            else {
                existing.title.trim().equals(title.trim(), ignoreCase = true) &&
                existing.date == date &&
                existing.startTime == startTime &&
                existing.endTime == endTime &&
                existing.isAllDay == isAllDay &&
                (existing.description?.trim() ?: "").equals(description?.trim() ?: "", ignoreCase = true) &&
                (existing.location?.trim() ?: "").equals(targetLocation?.trim() ?: "", ignoreCase = true) &&
                existing.activityType == type &&
                (existing.recurrenceRule?.trim() ?: "").equals(targetRecurrenceRule?.trim() ?: "", ignoreCase = true)
            }
        }

        if (isDuplicate) {
            _uiState.update { it.copy(syncMessage = "Aviso: Já existe um agendamento idêntico!") }
            return false
        }

        if (editingActivity != null) {
            if (editingActivity.location?.startsWith("JSON_IMPORTED_") == true) return false
            val updated = editingActivity.copy(
                title = title,
                description = description,
                date = date,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                categoryColor = categoryColor,
                activityType = type,
                lastModified = System.currentTimeMillis()
            )
            val index = currentList.indexOfFirst { it.id == editingActivity.id }
            if (index != -1) {
                currentList[index] = updated
            }
        } else {
            val newActivity = Activity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                date = date,
                startTime = startTime,
                endTime = endTime,
                isAllDay = isAllDay,
                location = null,
                categoryColor = categoryColor,
                activityType = type,
                recurrenceRule = null,
                lastModified = System.currentTimeMillis()
            )
            currentList.add(newActivity)
        }

        _uiState.update { it.copy(activities = currentList, activityToEdit = null, syncMessage = null) }
        saveData()
        if (hasTokens()) {
            syncActivitiesWithCloud()
        }
        return true
    }

    fun deleteActivity(id: String) {
        val activity = _uiState.value.activities.find { it.id == id }
        if (activity?.location?.startsWith("JSON_IMPORTED_") == true) return
        val currentList = _uiState.value.activities.filter { it.id != id }
        val newDeleted = if (id !in _uiState.value.deletedActivityIds) {
            _uiState.value.deletedActivityIds + id
        } else {
            _uiState.value.deletedActivityIds
        }
        _uiState.update { it.copy(activities = currentList, deletedActivityIds = newDeleted) }
        saveData()
        if (hasTokens()) {
            syncActivitiesWithCloud()
        }
    }

    fun showDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = true) }
    }

    fun dismissDuplicateDialog() {
        _uiState.update { it.copy(showDuplicateDialog = false) }
    }

    fun removeAllDuplicates() {
        val allActs = _uiState.value.activities
        val duplicates = getDuplicateGroups(allActs)
        if (duplicates.isEmpty()) return

        var currentList = _uiState.value.activities
        var newDeleted = _uiState.value.deletedActivityIds

        duplicates.forEach { group ->
            val toDelete = group.drop(1)
            toDelete.forEach { act ->
                currentList = currentList.filter { it.id != act.id }
                if (act.id !in newDeleted) {
                    newDeleted = newDeleted + act.id
                }
            }
        }

        _uiState.update { it.copy(activities = currentList, deletedActivityIds = newDeleted, showDuplicateDialog = false) }
        saveData()
        if (hasTokens()) {
            syncActivitiesWithCloud()
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

    fun toggleActivityCompletion(activity: Activity) {
        if (activity.location?.startsWith("JSON_IMPORTED_") == true) return
        val currentList = _uiState.value.activities.map {
            if (it.id == activity.id) {
                it.copy(
                    isCompleted = !it.isCompleted,
                    lastModified = System.currentTimeMillis()
                )
            } else {
                it
            }
        }
        _uiState.update { it.copy(activities = currentList) }
        saveData()
        if (hasTokens()) {
            syncActivitiesWithCloud()
        }
    }

    fun setActivityToEdit(activity: Activity?) {
        _uiState.update { it.copy(activityToEdit = activity) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        performSearch(query)
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        val normalized = query.trim().lowercase()
        val results = mutableListOf<DesktopSearchResult>()

        // 1. Pesquisar em atividades (excluindo atividades de calendário JSON pré-importadas)
        _uiState.value.activities.filter { activity ->
            activity.location?.startsWith("JSON_IMPORTED_") != true && (
                activity.title.lowercase().contains(normalized) ||
                (activity.description?.lowercase()?.contains(normalized) == true)
            )
        }.forEach { activity ->
            val subtitle = when (activity.activityType) {
                ActivityType.EVENT -> "Evento"
                ActivityType.TASK -> "Tarefa"
                ActivityType.BIRTHDAY -> "Aniversário"
                ActivityType.NOTE -> "Nota"
            }
            results.add(
                DesktopSearchResult(
                    id = activity.id,
                    title = activity.title,
                    subtitle = subtitle,
                    date = activity.date,
                    type = DesktopSearchResult.Type.ACTIVITY
                )
            )
        }

        // 2. Pesquisar em feriados nacionais
        nationalHolidays.filter { holiday ->
            holiday.name.lowercase().contains(normalized) ||
            (holiday.summary?.lowercase()?.contains(normalized) == true)
        }.forEach { holiday ->
            results.add(
                DesktopSearchResult(
                    id = "holiday_${holiday.name}_${holiday.date}",
                    title = holiday.name,
                    subtitle = "Feriado Nacional",
                    date = resolveHolidayDate(holiday.date),
                    type = DesktopSearchResult.Type.HOLIDAY
                )
            )
        }

        // 3. Pesquisar em datas comemorativas
        commemorativeDates.filter { holiday ->
            holiday.name.lowercase().contains(normalized) ||
            (holiday.summary?.lowercase()?.contains(normalized) == true)
        }.forEach { holiday ->
            results.add(
                DesktopSearchResult(
                    id = "commemorative_${holiday.name}_${holiday.date}",
                    title = holiday.name,
                    subtitle = "Data Comemorativa",
                    date = resolveHolidayDate(holiday.date),
                    type = DesktopSearchResult.Type.HOLIDAY
                )
            )
        }

        // 4. Pesquisar em santos do dia
        saintDays.filter { holiday ->
            holiday.name.lowercase().contains(normalized) ||
            (holiday.summary?.lowercase()?.contains(normalized) == true)
        }.forEach { holiday ->
            results.add(
                DesktopSearchResult(
                    id = "saint_${holiday.name}_${holiday.date}",
                    title = holiday.name,
                    subtitle = "Santo do Dia",
                    date = resolveHolidayDate(holiday.date),
                    type = DesktopSearchResult.Type.HOLIDAY
                )
            )
        }

        // 5. Pesquisar em profissões
        professionalDays.filter { holiday ->
            holiday.name.lowercase().contains(normalized) ||
            (holiday.summary?.lowercase()?.contains(normalized) == true)
        }.forEach { holiday ->
            results.add(
                DesktopSearchResult(
                    id = "professional_${holiday.name}_${holiday.date}",
                    title = holiday.name,
                    subtitle = "Profissões",
                    date = resolveHolidayDate(holiday.date),
                    type = DesktopSearchResult.Type.HOLIDAY
                )
            )
        }

        // 6. Pesquisar em feriados militares
        militaryHolidays.filter { holiday ->
            holiday.name.lowercase().contains(normalized) ||
            (holiday.summary?.lowercase()?.contains(normalized) == true)
        }.forEach { holiday ->
            results.add(
                DesktopSearchResult(
                    id = "military_${holiday.name}_${holiday.date}",
                    title = holiday.name,
                    subtitle = "Feriado Militar",
                    date = resolveHolidayDate(holiday.date),
                    type = DesktopSearchResult.Type.HOLIDAY
                )
            )
        }

        // Ordenar resultados
        val sortedResults = results.sortedWith(
            compareBy<DesktopSearchResult> { result ->
                if (result.title.lowercase().startsWith(normalized)) 0 else 1
            }.thenBy { result ->
                val today = LocalDate.now()
                val targetDate = try { LocalDate.parse(result.date) } catch(e: Exception) { today }
                kotlin.math.abs(targetDate.toEpochDay() - today.toEpochDay())
            }
        )

        _uiState.update { it.copy(searchResults = sortedResults) }
    }

    private fun resolveHolidayDate(dateString: String): String {
        val currentYear = LocalDate.now().year
        return try {
            if (dateString.matches(Regex("\\d{2}-\\d{2}"))) {
                val parts = dateString.split("-")
                LocalDate.of(currentYear, parts[0].toInt(), parts[1].toInt()).toString()
            } else {
                LocalDate.parse(dateString).toString()
            }
        } catch (e: Exception) {
            LocalDate.now().toString()
        }
    }

    fun selectSearchResult(result: DesktopSearchResult) {
        val targetDate = try { LocalDate.parse(result.date) } catch(e: Exception) { LocalDate.now() }
        _uiState.update {
            it.copy(
                selectedDate = targetDate,
                displayedYearMonth = YearMonth.from(targetDate),
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    fun setWelcomeName(name: String) {
        _uiState.update { it.copy(welcomeName = name) }
        saveData()
    }

    fun setTheme(theme: Theme) {
        _uiState.update { it.copy(theme = theme) }
        saveData()
    }

    fun setPureBlackTheme(enabled: Boolean) {
        _uiState.update { it.copy(pureBlackTheme = enabled) }
        saveData()
    }

    fun setEncryptionSettings(enabled: Boolean, password: String) {
        if (!enabled) {
            disableEncryption(password)
        } else {
            _uiState.update { it.copy(isEncryptionEnabled = true, encryptionPassword = password) }
            saveData()
            syncActivitiesWithCloud(password)
        }
    }

    fun disableEncryption(password: String) {
        scope.launch {
            _uiState.update { it.copy(isSyncing = true, isDecryptionLoading = true, syncMessage = "Desativando criptografia e atualizando nuvem...") }
            val oldEncryptionState = _uiState.value.isEncryptionEnabled
            val oldPassword = _uiState.value.encryptionPassword
            
            _uiState.update { it.copy(isEncryptionEnabled = false, encryptionPassword = "") }
            saveData()
            
            try {
                syncActivitiesWithCloud(providedPassword = password, isDisablingEncryption = true)
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isEncryptionEnabled = oldEncryptionState, 
                        encryptionPassword = oldPassword,
                        isSyncing = false,
                        isDecryptionLoading = false,
                        syncMessage = "Falha ao desativar criptografia: ${e.message}"
                    ) 
                }
                saveData()
            }
        }
    }

    fun dismissDecryptionDialog() {
        _uiState.update { it.copy(
            showDecryptionDialog = false,
            isDecryptionLoading = false,
            decryptionBackupFile = null,
            decryptionErrorMessage = null
        ) }
    }

    fun setShowSettings(show: Boolean) {
        _uiState.update { it.copy(showSettings = show) }
    }

    fun toggleFilter(type: String) {
        val current = _uiState.value.filterOptions
        val updated = when(type) {
            "holidays" -> current.copy(showHolidays = !current.showHolidays)
            "saintDays" -> current.copy(showSaintDays = !current.showSaintDays)
            "events" -> current.copy(showEvents = !current.showEvents)
            "tasks" -> current.copy(showTasks = !current.showTasks)
            "notes" -> current.copy(showNotes = !current.showNotes)
            "birthdays" -> current.copy(showBirthdays = !current.showBirthdays)
            "professionalDays" -> current.copy(showProfessionalDays = !current.showProfessionalDays)
            "militaryHolidays" -> current.copy(showMilitaryHolidays = !current.showMilitaryHolidays)
            else -> current
        }
        _uiState.update { it.copy(filterOptions = updated) }
        saveData()
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    fun syncGoogleCalendar() {
        scope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = "Iniciando sincronização...") }
            try {
                val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
                if (secretStream == null) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncMessage = "Aviso: arquivo 'client_secrets.json' não encontrado nos recursos. Veja as instruções para ativá-lo."
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(syncMessage = "Acesse o seu navegador para autorizar...") }

                val localActivities = _uiState.value.activities
                val localToUpload = localActivities.filter { !it.isFromGoogle }
                val uploadedMappings = mutableMapOf<String, Activity>()

                val activitiesList = withContext(Dispatchers.IO) {
                    val transport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport()
                    val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()

                    val clientSecrets = com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(
                        jsonFactory, java.io.InputStreamReader(secretStream)
                    )

                    val flow = com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
                        transport, jsonFactory, clientSecrets,
                        listOf(
                            "https://www.googleapis.com/auth/calendar",
                            "https://www.googleapis.com/auth/calendar.events",
                            "https://www.googleapis.com/auth/drive.appdata"
                        )
                    )
                        .setDataStoreFactory(com.google.api.client.util.store.FileDataStoreFactory(java.io.File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
                        .setAccessType("offline")
                        .build()

                    val receiver = com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build()
                    val credential = com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

                    val calendarService = com.google.api.services.calendar.Calendar.Builder(
                        transport, jsonFactory, credential
                    )
                        .setApplicationName("TheBigCalendar")
                        .build()

                    // 1. Enviar eventos locais que não vieram do Google
                    if (localToUpload.isNotEmpty()) {
                        _uiState.update { it.copy(syncMessage = "Enviando ${localToUpload.size} compromissos locais para o Google...") }
                        for (localAct in localToUpload) {
                            try {
                                val event = com.google.api.services.calendar.model.Event().apply {
                                    summary = localAct.title
                                    description = localAct.description
                                    location = localAct.location
                                }

                                val startDateTime = if (localAct.isAllDay) {
                                    val localDate = java.time.LocalDate.parse(localAct.date)
                                    val startInstant = localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                                    val dateVal = com.google.api.client.util.DateTime(true, startInstant, null)
                                    com.google.api.services.calendar.model.EventDateTime().setDate(dateVal)
                                } else {
                                    val localDateTime = java.time.LocalDateTime.of(java.time.LocalDate.parse(localAct.date), localAct.startTime ?: java.time.LocalTime.of(9, 0))
                                    val zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault())
                                    val startVal = com.google.api.client.util.DateTime(zonedDateTime.toInstant().toEpochMilli())
                                    com.google.api.services.calendar.model.EventDateTime().setDateTime(startVal)
                                }
                                event.start = startDateTime

                                val endDateTime = if (localAct.isAllDay) {
                                    val localDate = java.time.LocalDate.parse(localAct.date).plusDays(1)
                                    val endInstant = localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                                    val dateVal = com.google.api.client.util.DateTime(true, endInstant, null)
                                    com.google.api.services.calendar.model.EventDateTime().setDate(dateVal)
                                } else {
                                    val localDateTime = java.time.LocalDateTime.of(
                                        java.time.LocalDate.parse(localAct.date),
                                        localAct.endTime ?: (localAct.startTime ?: java.time.LocalTime.of(9, 0)).plusHours(1)
                                    )
                                    val zonedDateTime = localDateTime.atZone(java.time.ZoneId.systemDefault())
                                    val endVal = com.google.api.client.util.DateTime(zonedDateTime.toInstant().toEpochMilli())
                                    com.google.api.services.calendar.model.EventDateTime().setDateTime(endVal)
                                }
                                event.end = endDateTime

                                val createdEvent = calendarService.events().insert("primary", event).execute()
                                if (createdEvent.id != null) {
                                    val updatedAct = localAct.copy(
                                        id = createdEvent.id,
                                        isFromGoogle = true
                                    )
                                    uploadedMappings[localAct.id] = updatedAct
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                throw Exception("Falha ao enviar compromisso local '${localAct.title}': ${e.message}", e)
                            }
                        }
                    }

                    _uiState.update { it.copy(syncMessage = "Buscando compromissos do Google Calendar...") }

                    // 2. Buscar eventos do Google
                    val currentYear = YearMonth.now().year
                    val startDateTime = com.google.api.client.util.DateTime(java.time.OffsetDateTime.of(currentYear, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
                    val endDateTime = com.google.api.client.util.DateTime(java.time.OffsetDateTime.of(currentYear, 12, 31, 23, 59, 59, 0, java.time.ZoneOffset.UTC).toInstant().toEpochMilli())

                    val eventsResult = calendarService.events().list("primary")
                        .setTimeMin(startDateTime)
                        .setTimeMax(endDateTime)
                        .setMaxResults(2500)
                        .execute()

                    val googleEvents = eventsResult.items ?: emptyList()

                    googleEvents.mapNotNull { event ->
                        try {
                            val start = event.start?.dateTime?.value ?: event.start?.date?.value ?: return@mapNotNull null
                            val end = event.end?.dateTime?.value ?: event.end?.date?.value

                            val startDate = if (event.start?.dateTime == null) {
                                java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                            } else {
                                java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            }

                            val startTime = if (event.start?.dateTime != null) {
                                java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                            } else null

                            val endTime = if (end != null && event.end?.dateTime != null) {
                                java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                            } else null

                            val summary = event.summary ?: "Sem título"
                            val isBirthday = event.start?.dateTime == null &&
                                             (summary.contains("aniversário", ignoreCase = true) ||
                                              summary.contains("birthday", ignoreCase = true))

                            Activity(
                                id = event.id ?: UUID.randomUUID().toString(),
                                title = summary,
                                description = event.description,
                                date = startDate.toString(),
                                startTime = startTime,
                                endTime = endTime,
                                isAllDay = event.start?.dateTime == null,
                                location = event.location,
                                categoryColor = if (isBirthday) "#FF69B4" else "#4285F4",
                                activityType = if (isBirthday) ActivityType.BIRTHDAY else ActivityType.EVENT,
                                recurrenceRule = event.recurrence?.firstOrNull(),
                                notificationSettings = NotificationSettings(),
                                showInCalendar = true,
                                isFromGoogle = true,
                                excludedDates = emptyList()
                            )
                        } catch(e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                }

                // Substitui os itens locais que foram enviados pelo seu correspondente com ID gerado pelo Google
                val currentActivities = _uiState.value.activities.map { localAct ->
                    uploadedMappings[localAct.id] ?: localAct
                }.toMutableList()

                var newCount = 0
                activitiesList.forEach { remote ->
                    val index = currentActivities.indexOfFirst { it.id == remote.id || (it.title == remote.title && it.date == remote.date) }
                    if (index != -1) {
                        currentActivities[index] = remote
                    } else {
                        currentActivities.add(remote)
                        newCount++
                    }
                }

                val finalMessage = if (uploadedMappings.isNotEmpty()) {
                    "Sincronização concluída! ${uploadedMappings.size} locais enviados, $newCount novos importados do Google."
                } else {
                    "Sincronização concluída! $newCount novos eventos importados."
                }

                _uiState.update {
                    it.copy(
                        activities = currentActivities,
                        isSyncing = false,
                        syncMessage = finalMessage,
                        showDecryptionDialog = false,
                        isDecryptionLoading = false
                    )
                }
                saveData()

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncMessage = "Erro ao sincronizar: ${e.message}"
                    )
                }
            }
        }
    }

    fun restoreBackup(file: java.io.File, password: String? = null) {
        scope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = "Iniciando restauração do backup...") }
            try {
                var content = withContext(Dispatchers.IO) {
                    file.readText(Charsets.UTF_8)
                }

                if (com.mss.thebigcalendar.crypto.CryptoHelper.isEncrypted(content)) {
                    val passwordToUse = password ?: _uiState.value.encryptionPassword
                    if (passwordToUse.isEmpty()) {
                        throw com.mss.thebigcalendar.crypto.DecryptionRequiredException("Este arquivo de backup está criptografado. Uma senha é necessária.")
                    }
                    try {
                        content = com.mss.thebigcalendar.crypto.CryptoHelper.decrypt(content, passwordToUse)
                    } catch (e: Exception) {
                        throw com.mss.thebigcalendar.crypto.DecryptionFailedException("Senha incorreta ao descriptografar arquivo de backup.")
                    }
                    _uiState.update { 
                        it.copy(
                            isDecryptionLoading = true, 
                            decryptionErrorMessage = null,
                            isEncryptionEnabled = true,
                            encryptionPassword = passwordToUse
                        ) 
                    }
                }

                val jsonElement = Json.parseToJsonElement(content)
                val jsonObject = jsonElement.jsonObject

                // Verificar versão do backup
                val backupVersion = jsonObject["backupVersion"]?.jsonPrimitive?.content ?: "1.0"
                if (backupVersion != "1.0" && backupVersion != "1.1") {
                    throw Exception("Versão de backup não suportada: $backupVersion")
                }

                // Extrair atividades ativas e concluídas
                val activitiesArray = jsonObject["activities"]?.jsonArray ?: emptyList()
                val completedActivitiesArray = jsonObject["completedActivities"]?.jsonArray ?: emptyList()

                val restoredActivities = mutableListOf<Activity>()

                for (element in activitiesArray) {
                    try {
                        val actObj = element.jsonObject
                        restoredActivities.add(parseActivityFromJsonObject(actObj))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                for (element in completedActivitiesArray) {
                    try {
                        val actObj = element.jsonObject
                        // Assegurar que atividades no array completedActivities estejam marcadas como concluídas
                        val act = parseActivityFromJsonObject(actObj).copy(isCompleted = true)
                        restoredActivities.add(act)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Restaurar configurações se disponíveis no backup
                val settingsObj = jsonObject["settings"]?.jsonObject
                var restoredWelcomeName = _uiState.value.welcomeName
                var restoredTheme = _uiState.value.theme

                if (settingsObj != null) {
                    val welcomeNameStr = settingsObj["welcomeName"]?.jsonPrimitive?.content
                    if (!welcomeNameStr.isNullOrBlank()) {
                        restoredWelcomeName = welcomeNameStr
                    }
                    val themeNameStr = settingsObj["theme"]?.jsonPrimitive?.content
                    if (!themeNameStr.isNullOrBlank()) {
                        restoredTheme = try { Theme.valueOf(themeNameStr) } catch(e: Exception) { restoredTheme }
                    }
                }

                // Mesclar as atividades restauradas com a lista atual
                val currentActivities = _uiState.value.activities.toMutableList()
                var updatedCount = 0
                var insertedCount = 0

                restoredActivities.forEach { restored ->
                    val index = currentActivities.indexOfFirst { it.id == restored.id || (it.title == restored.title && it.date == restored.date) }
                    if (index != -1) {
                        currentActivities[index] = restored
                        updatedCount++
                    } else {
                        currentActivities.add(restored)
                        insertedCount++
                    }
                }

                _uiState.update {
                    it.copy(
                        activities = currentActivities,
                        welcomeName = restoredWelcomeName,
                        theme = restoredTheme,
                        isSyncing = false,
                        syncMessage = "Backup restaurado! $insertedCount novos compromissos importados, $updatedCount atualizados.",
                        showDecryptionDialog = false,
                        isDecryptionLoading = false,
                        decryptionBackupFile = null,
                        decryptionErrorMessage = null
                    )
                }
                saveData()

            } catch (e: Exception) {
                e.printStackTrace()
                if (e is com.mss.thebigcalendar.crypto.DecryptionRequiredException || e is com.mss.thebigcalendar.crypto.DecryptionFailedException) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            showDecryptionDialog = true,
                            isDecryptionLoading = false,
                            decryptionBackupFile = file,
                            decryptionErrorMessage = e.message
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            showDecryptionDialog = false,
                            isDecryptionLoading = false,
                            syncMessage = "Erro ao restaurar backup: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? {
        if (this == null || this is kotlinx.serialization.json.JsonNull) return null
        val content = this.jsonPrimitive.content
        return content.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun parseActivityFromJsonObject(obj: kotlinx.serialization.json.JsonObject): Activity {
        val id = obj["id"].asStringOrNull() ?: UUID.randomUUID().toString()
        val title = obj["title"].asStringOrNull() ?: "Sem título"
        val description = obj["description"].asStringOrNull() ?: ""
        val date = obj["date"].asStringOrNull() ?: LocalDate.now().toString()
        
        val startTimeStr = obj["startTime"].asStringOrNull()
        val startTime = startTimeStr?.takeIf { it.isNotEmpty() }?.let {
            try { LocalTime.parse(it) } catch (e: Exception) { null }
        }
        
        val endTimeStr = obj["endTime"].asStringOrNull()
        val endTime = endTimeStr?.takeIf { it.isNotEmpty() }?.let {
            try { LocalTime.parse(it) } catch (e: Exception) { null }
        }
        
        val isAllDay = obj["isAllDay"]?.jsonPrimitive?.booleanOrNull ?: true
        val location = obj["location"].asStringOrNull()
        val categoryColor = obj["categoryColor"].asStringOrNull() ?: "#4285F4"
        
        val activityTypeStr = obj["activityType"].asStringOrNull() ?: "EVENT"
        val activityType = try { ActivityType.valueOf(activityTypeStr) } catch(e: Exception) { ActivityType.EVENT }
        
        val recurrenceRule = obj["recurrenceRule"].asStringOrNull()
        val isCompleted = obj["isCompleted"]?.jsonPrimitive?.booleanOrNull ?: false
        
        val visibilityStr = obj["visibility"].asStringOrNull() ?: "LOW"
        val visibility = try { VisibilityLevel.valueOf(visibilityStr) } catch(e: Exception) { VisibilityLevel.LOW }
        
        val showInCalendar = obj["showInCalendar"]?.jsonPrimitive?.booleanOrNull ?: true
        val isFromGoogle = obj["isFromGoogle"]?.jsonPrimitive?.booleanOrNull ?: false
        
        val excludedDates = obj["excludedDates"]?.jsonArray?.mapNotNull { it.asStringOrNull() } ?: emptyList()
        val excludedInstances = obj["excludedInstances"]?.jsonArray?.mapNotNull { it.asStringOrNull() } ?: emptyList()
        val wikipediaLink = obj["wikipediaLink"].asStringOrNull()
        
        val rollover = obj["rollover"]?.jsonPrimitive?.booleanOrNull ?: false
        val lastModified = obj["lastModified"]?.jsonPrimitive?.longOrNull ?: 0L

        val notifObj = obj["notificationSettings"]?.jsonObject
        val notificationSettings = if (notifObj != null) {
            val notifEnabled = notifObj["isEnabled"]?.jsonPrimitive?.booleanOrNull ?: false
            val notifTypeStr = notifObj["notificationType"]?.jsonPrimitive?.content ?: "FIFTEEN_MINUTES_BEFORE"
            val notifType = try {
                com.mss.thebigcalendar.data.model.NotificationType.valueOf(notifTypeStr)
            } catch(e: Exception) {
                com.mss.thebigcalendar.data.model.NotificationType.FIFTEEN_MINUTES_BEFORE
            }
            val customMinutes = notifObj["customMinutesBefore"]?.jsonPrimitive?.intOrNull ?: 15
            val notifTimeStr = notifObj["notificationTime"]?.jsonPrimitive?.content
            val notifTime = notifTimeStr?.takeIf { it.isNotEmpty() }?.let {
                try { LocalTime.parse(it) } catch (e: Exception) { null }
            }
            NotificationSettings(
                isEnabled = notifEnabled,
                notificationType = notifType,
                customMinutesBefore = customMinutes,
                notificationTime = notifTime
            )
        } else {
            NotificationSettings()
        }

        return Activity(
            id = id,
            title = title,
            description = description,
            date = date,
            startTime = startTime,
            endTime = endTime,
            isAllDay = isAllDay,
            location = location,
            categoryColor = categoryColor,
            activityType = activityType,
            recurrenceRule = recurrenceRule,
            isCompleted = isCompleted,
            visibility = visibility,
            showInCalendar = showInCalendar,
            isFromGoogle = isFromGoogle,
            excludedDates = excludedDates,
            excludedInstances = excludedInstances,
            wikipediaLink = wikipediaLink,
            notificationSettings = notificationSettings,
            rollover = rollover,
            lastModified = lastModified
        )
    }

    fun fetchCloudBackups() {
        scope.launch {
            _uiState.update { 
                it.copy(
                    isFetchingCloudBackups = true, 
                    showCloudBackupDialog = true, 
                    syncMessage = null 
                ) 
            }
            try {
                val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
                if (secretStream == null) {
                    _uiState.update {
                        it.copy(
                            isFetchingCloudBackups = false,
                            showCloudBackupDialog = false,
                            syncMessage = "Aviso: arquivo 'client_secrets.json' não encontrado. Não é possível acessar a nuvem."
                        )
                    }
                    return@launch
                }

                val backupsList = withContext(Dispatchers.IO) {
                    val transport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport()
                    val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()

                    val clientSecrets = com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(
                        jsonFactory, java.io.InputStreamReader(secretStream)
                    )

                    val flow = com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
                        transport, jsonFactory, clientSecrets,
                        listOf(
                            "https://www.googleapis.com/auth/calendar",
                            "https://www.googleapis.com/auth/calendar.events",
                            "https://www.googleapis.com/auth/drive.appdata"
                        )
                    )
                        .setDataStoreFactory(com.google.api.client.util.store.FileDataStoreFactory(java.io.File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
                        .setAccessType("offline")
                        .build()

                    val receiver = com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build()
                    val credential = com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

                    val driveService = com.google.api.services.drive.Drive.Builder(
                        transport, jsonFactory, credential
                    )
                        .setApplicationName("TheBigCalendar")
                        .build()

                    // List files in appDataFolder
                    val filesResult = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setFields("files(id, name, createdTime)")
                        .execute()

                    val filesList = filesResult.getFiles() ?: emptyList()

                    filesList.mapNotNull { file ->
                        if (file.getName()?.contains("backup_") == true || file.getName()?.contains(".json") == true) {
                            val timeStr = file.getCreatedTime()?.toString() ?: ""
                            DesktopCloudBackupInfo(
                                id = file.getId() ?: "",
                                name = file.getName() ?: "Backup sem nome",
                                createdTime = timeStr
                            )
                        } else null
                    }.sortedByDescending { it.createdTime }
                }

                _uiState.update {
                    it.copy(
                        cloudBackups = backupsList,
                        isFetchingCloudBackups = false
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isFetchingCloudBackups = false,
                        showCloudBackupDialog = false,
                        syncMessage = "Erro ao buscar backups na nuvem: ${e.message}"
                    )
                }
            }
        }
    }

    fun restoreCloudBackup(fileId: String, password: String? = null) {
        scope.launch {
            _uiState.update {
                it.copy(
                    isFetchingCloudBackups = true,
                    syncMessage = "Baixando arquivo de backup da nuvem..."
                )
            }
            try {
                val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
                if (secretStream == null) {
                    throw Exception("Arquivo 'client_secrets.json' não encontrado.")
                }

                val tempFile = withContext(Dispatchers.IO) {
                    val transport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport()
                    val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()

                    val clientSecrets = com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(
                        jsonFactory, java.io.InputStreamReader(secretStream)
                    )

                    val flow = com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
                        transport, jsonFactory, clientSecrets,
                        listOf(
                            "https://www.googleapis.com/auth/calendar",
                            "https://www.googleapis.com/auth/calendar.events",
                            "https://www.googleapis.com/auth/drive.appdata"
                        )
                    )
                        .setDataStoreFactory(com.google.api.client.util.store.FileDataStoreFactory(java.io.File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
                        .setAccessType("offline")
                        .build()

                    val receiver = com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build()
                    val credential = com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

                    val driveService = com.google.api.services.drive.Drive.Builder(
                        transport, jsonFactory, credential
                    )
                        .setApplicationName("TheBigCalendar")
                        .build()

                    val localTempFile = java.io.File.createTempFile("cloud_restore_", ".json")
                    localTempFile.deleteOnExit()

                    java.io.FileOutputStream(localTempFile).use { outputStream ->
                        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                    }

                    localTempFile
                }

                // Restore from the temp file we downloaded
                restoreBackup(tempFile, password)

                _uiState.update {
                    it.copy(
                        showCloudBackupDialog = false,
                        isFetchingCloudBackups = false
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isFetchingCloudBackups = false,
                        showCloudBackupDialog = false,
                        syncMessage = "Erro ao restaurar backup da nuvem: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissCloudBackupDialog() {
        _uiState.update { it.copy(showCloudBackupDialog = false) }
    }

    fun onFilterChange(key: String, value: Boolean) {
        val current = _uiState.value.filterOptions
        val updated = when(key) {
            "showHolidays" -> current.copy(showHolidays = value)
            "showSaintDays" -> current.copy(showSaintDays = value)
            "showEvents" -> current.copy(showEvents = value)
            "showTasks" -> current.copy(showTasks = value)
            "showNotes" -> current.copy(showNotes = value)
            "showBirthdays" -> current.copy(showBirthdays = value)
            "showProfessionalDays" -> current.copy(showProfessionalDays = value)
            "showMilitaryHolidays" -> current.copy(showMilitaryHolidays = value)
            else -> current
        }
        _uiState.update { it.copy(filterOptions = updated) }
        
        if (key == "showCompletedActivities") {
            _uiState.update { it.copy(showCompletedActivities = value) }
        }
        if (key == "showMoonPhases") {
            _uiState.update { it.copy(showMoonPhases = value) }
        }
        
        saveData()
    }

    fun toggleSidebarFilterVisibility(filterKey: String) {
        val currentVisibility = _uiState.value.sidebarFilterVisibility
        val newVisibility = when (filterKey) {
            "showHolidays" -> currentVisibility.copy(showHolidays = !currentVisibility.showHolidays)
            "showEvents" -> currentVisibility.copy(showEvents = !currentVisibility.showEvents)
            "showTasks" -> currentVisibility.copy(showTasks = !currentVisibility.showTasks)
            "showBirthdays" -> currentVisibility.copy(showBirthdays = !currentVisibility.showBirthdays)
            "showNotes" -> currentVisibility.copy(showNotes = !currentVisibility.showNotes)
            "showSaintDays" -> currentVisibility.copy(showSaintDays = !currentVisibility.showSaintDays)
            "showProfessionalDays" -> currentVisibility.copy(showProfessionalDays = !currentVisibility.showProfessionalDays)
            "showMilitaryHolidays" -> currentVisibility.copy(showMilitaryHolidays = !currentVisibility.showMilitaryHolidays)
            "showCompletedActivities" -> currentVisibility.copy(showCompletedTasks = !currentVisibility.showCompletedTasks)
            "showMoonPhases" -> currentVisibility.copy(showMoonPhases = !currentVisibility.showMoonPhases)
            else -> currentVisibility
        }
        
        // Se a opção foi removida do sidebar, desativar o filtro correspondente
        val shouldDisableFilter = when (filterKey) {
            "showHolidays" -> !newVisibility.showHolidays && currentVisibility.showHolidays
            "showEvents" -> !newVisibility.showEvents && currentVisibility.showEvents
            "showTasks" -> !newVisibility.showTasks && currentVisibility.showTasks
            "showBirthdays" -> !newVisibility.showBirthdays && currentVisibility.showBirthdays
            "showNotes" -> !newVisibility.showNotes && currentVisibility.showNotes
            "showSaintDays" -> !newVisibility.showSaintDays && currentVisibility.showSaintDays
            "showProfessionalDays" -> !newVisibility.showProfessionalDays && currentVisibility.showProfessionalDays
            "showMilitaryHolidays" -> !newVisibility.showMilitaryHolidays && currentVisibility.showMilitaryHolidays
            "showCompletedActivities" -> !newVisibility.showCompletedTasks && currentVisibility.showCompletedTasks
            "showMoonPhases" -> !newVisibility.showMoonPhases && currentVisibility.showMoonPhases
            else -> false
        }
        
        _uiState.update { it.copy(sidebarFilterVisibility = newVisibility) }
        
        if (shouldDisableFilter) {
            onFilterChange(filterKey, false)
        }
        
        saveData()
    }

    fun hasTokens(): Boolean {
        val home = System.getProperty("user.home")
        val tokensDir = java.io.File(home, ".thebigcalendar/tokens")
        if (!tokensDir.exists() || !tokensDir.isDirectory) return false
        val files = tokensDir.listFiles()
        return files != null && files.any { it.isFile && it.name != "lock" }
    }

    private fun getDriveService(): com.google.api.services.drive.Drive {
        val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
            ?: java.io.File("client_secrets.json").let { if (it.exists()) it.inputStream() else null }
            ?: java.io.File("desktop/src/desktopMain/resources/client_secrets.json").let { if (it.exists()) it.inputStream() else null }
            ?: throw Exception("Arquivo client_secrets.json não encontrado.")

        val transport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
        val clientSecrets = com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(
            jsonFactory, java.io.InputStreamReader(secretStream)
        )

        val flow = com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
            transport, jsonFactory, clientSecrets,
            listOf(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/calendar.events",
                "https://www.googleapis.com/auth/drive.appdata"
            )
        )
            .setDataStoreFactory(com.google.api.client.util.store.FileDataStoreFactory(java.io.File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
            .setAccessType("offline")
            .build()

        val receiver = com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build()
        val credential = com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        return com.google.api.services.drive.Drive.Builder(transport, jsonFactory, credential)
            .setApplicationName("The Big Calendar")
            .build()
    }

    private fun getCalendarService(): com.google.api.services.calendar.Calendar {
        val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
            ?: java.io.File("client_secrets.json").let { if (it.exists()) it.inputStream() else null }
            ?: java.io.File("desktop/src/desktopMain/resources/client_secrets.json").let { if (it.exists()) it.inputStream() else null }
            ?: throw Exception("Arquivo client_secrets.json não encontrado.")

        val transport = com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
        val clientSecrets = com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.load(
            jsonFactory, java.io.InputStreamReader(secretStream)
        )

        val flow = com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder(
            transport, jsonFactory, clientSecrets,
            listOf(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/calendar.events",
                "https://www.googleapis.com/auth/drive.appdata"
            )
        )
            .setDataStoreFactory(com.google.api.client.util.store.FileDataStoreFactory(java.io.File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
            .setAccessType("offline")
            .build()

        val receiver = com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver.Builder().setPort(8888).build()
        val credential = com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        return com.google.api.services.calendar.Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName("The Big Calendar")
            .build()
    }

    fun checkGoogleAccount() {
        if (!hasTokens()) {
            _uiState.update { it.copy(googleAccountEmail = null) }
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val calendarService = getCalendarService()
                val calendar = calendarService.calendars().get("primary").execute()
                val email = calendar.id
                _uiState.update { it.copy(googleAccountEmail = email) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disconnectGoogleAccount() {
        scope.launch(Dispatchers.IO) {
            try {
                val home = System.getProperty("user.home")
                val tokensDir = java.io.File(home, ".thebigcalendar/tokens")
                if (tokensDir.exists() && tokensDir.isDirectory) {
                    tokensDir.listFiles()?.forEach { it.delete() }
                }
                _uiState.update { 
                    it.copy(
                        googleAccountEmail = null,
                        isEncryptionEnabled = false,
                        encryptionPassword = ""
                    ) 
                }
                saveData()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun syncActivitiesWithCloud(providedPassword: String? = null, isDisablingEncryption: Boolean = false) {
        scope.launch {
            _uiState.update { it.copy(isSyncing = true, syncMessage = "Iniciando sincronização...") }
            try {
                val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
                    ?: java.io.File("client_secrets.json").let { if (it.exists()) it.inputStream() else null }
                    ?: java.io.File("desktop/src/desktopMain/resources/client_secrets.json").let { if (it.exists()) it.inputStream() else null }

                if (secretStream == null) {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            syncMessage = "Aviso: arquivo 'client_secrets.json' não encontrado. Não é possível acessar a nuvem."
                        )
                    }
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    val driveService = getDriveService()

                    // 1. Procurar arquivo TBCalendar_Sync_Data.json
                    val resultList = driveService.files().list()
                        .setSpaces("appDataFolder")
                        .setQ("name = 'TBCalendar_Sync_Data.json'")
                        .setFields("files(id, name)")
                        .execute()
                    val files = resultList.files ?: emptyList()

                    var remoteActivities = emptyList<Activity>()
                    var remoteCompleted = emptyList<Activity>()
                    var remoteDeletedIds = emptySet<String>()
                    var remoteDevicesArray = emptyList<kotlinx.serialization.json.JsonElement>()
                    var fileId: String? = null

                    if (files.isNotEmpty()) {
                        val foundFile = files[0]
                        fileId = foundFile.id
                        val tempFile = java.io.File.createTempFile("temp_sync_download", ".json")
                        try {
                            driveService.files().get(fileId).executeMediaAndDownloadTo(tempFile.outputStream())
                            var content = tempFile.readText(Charsets.UTF_8)
                            if (content.isNotEmpty()) {
                                if (com.mss.thebigcalendar.crypto.CryptoHelper.isEncrypted(content)) {
                                    val passwordToUse = providedPassword ?: _uiState.value.encryptionPassword
                                    if (passwordToUse.isEmpty()) {
                                        throw com.mss.thebigcalendar.crypto.DecryptionRequiredException("Sincronização em nuvem está criptografada, mas nenhuma senha está configurada localmente.")
                                    }
                                    try {
                                        content = com.mss.thebigcalendar.crypto.CryptoHelper.decrypt(content, passwordToUse)
                                    } catch (e: Exception) {
                                        throw com.mss.thebigcalendar.crypto.DecryptionFailedException("Senha incorreta ao descriptografar dados de sincronização em nuvem.")
                                    }
                                    if (!isDisablingEncryption) {
                                        _uiState.update { 
                                            it.copy(
                                                isDecryptionLoading = true, 
                                                decryptionErrorMessage = null,
                                                isEncryptionEnabled = true,
                                                encryptionPassword = passwordToUse
                                            ) 
                                        }
                                    }
                                } else {
                                    if (!isDisablingEncryption) {
                                        if (providedPassword != null && providedPassword.isNotEmpty()) {
                                            _uiState.update { 
                                                it.copy(
                                                    isEncryptionEnabled = true,
                                                    encryptionPassword = providedPassword
                                                ) 
                                            }
                                        } else {
                                            _uiState.update { 
                                                it.copy(
                                                    isEncryptionEnabled = false,
                                                    encryptionPassword = ""
                                                ) 
                                            }
                                        }
                                        saveData()
                                    }
                                }
                                val jsonObject = Json.parseToJsonElement(content).jsonObject
                                
                                remoteDevicesArray = jsonObject["devices"]?.jsonArray ?: emptyList()
                                val activitiesArray = jsonObject["activities"]?.jsonArray ?: emptyList()
                                val parsedActs = mutableListOf<Activity>()
                                for (element in activitiesArray) {
                                    try {
                                        parsedActs.add(parseActivityFromJsonObject(element.jsonObject))
                                    } catch(e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                remoteActivities = parsedActs

                                val completedArray = jsonObject["completedActivities"]?.jsonArray ?: emptyList()
                                val parsedCompleted = mutableListOf<Activity>()
                                for (element in completedArray) {
                                    try {
                                        parsedCompleted.add(parseActivityFromJsonObject(element.jsonObject).copy(isCompleted = true))
                                    } catch(e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                remoteCompleted = parsedCompleted

                                val deletedArray = jsonObject["deletedActivities"]?.jsonArray ?: emptyList()
                                val parsedDeleted = mutableSetOf<String>()
                                for (element in deletedArray) {
                                    try {
                                        val optObj = element as? kotlinx.serialization.json.JsonObject
                                        if (optObj != null) {
                                            val origObj = optObj["originalActivity"]?.jsonObject
                                            val origId = origObj?.get("id")?.jsonPrimitive?.content ?: optObj["id"]?.jsonPrimitive?.content
                                            if (!origId.isNullOrEmpty()) {
                                                parsedDeleted.add(origId)
                                            }
                                        } else {
                                            val strId = element.jsonPrimitive.content
                                            if (strId.isNotEmpty()) {
                                                parsedDeleted.add(strId)
                                            }
                                        }
                                    } catch(e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                remoteDeletedIds = parsedDeleted
                            }
                        } finally {
                            if (tempFile.exists()) tempFile.delete()
                        }
                    }

                    // 2. Mesclar com dados locais
                    val localActivities = _uiState.value.activities
                    val localDeletedIds = _uiState.value.deletedActivityIds.toSet()
                    
                    var finalDeletedIds = localDeletedIds + remoteDeletedIds

                    // Separar locais personalizadas e importadas
                    val localCustomActive = localActivities.filter { !it.isCompleted && it.location?.startsWith("JSON_IMPORTED_") != true }
                    val localJsonActive = localActivities.filter { !it.isCompleted && it.location?.startsWith("JSON_IMPORTED_") == true }

                    val localCustomCompleted = localActivities.filter { it.isCompleted && it.location?.startsWith("JSON_IMPORTED_") != true }
                    val localJsonCompleted = localActivities.filter { it.isCompleted && it.location?.startsWith("JSON_IMPORTED_") == true }

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
                                    mergedCustomCompleted.add(completedVersion.copy(isCompleted = true))
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
                                    mergedCustomCompleted.add(completedVersion.copy(isCompleted = true))
                            } else {
                                discardedIds.add(id)
                            }
                        }
                    }

                    finalDeletedIds = finalDeletedIds + discardedIds
                    
                    val finalActive = mergedCustomActive + localJsonActive
                    val finalCompleted = mergedCustomCompleted + localJsonCompleted
                    
                    val mergedActivities = finalActive + finalCompleted

                    // 3. Salvar no banco local (State e Datastore)
                    _uiState.update { 
                        it.copy(
                            activities = mergedActivities,
                            deletedActivityIds = finalDeletedIds.toList(),
                            isSyncing = false,
                            syncMessage = "Sincronização com a nuvem concluída!",
                            showDecryptionDialog = false,
                            decryptionErrorMessage = null
                        ) 
                    }
                    saveData()
                    checkGoogleAccount()

                    // 3b. Mesclar lista de dispositivos
                    val mergedDevices = mutableListOf<kotlinx.serialization.json.JsonObject>()
                    
                    val osName = System.getProperty("os.name").lowercase()
                    val currentPlatform = when {
                        osName.contains("linux") -> "linux"
                        osName.contains("windows") -> "windows"
                        else -> "desktop"
                    }
                    
                    val currentDeviceName = try {
                        java.net.InetAddress.getLocalHost().hostName
                    } catch (e: Exception) {
                        System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "Desktop-PC"
                    }
                    
                    val currentTime = System.currentTimeMillis()
                    val thirtyDaysAgo = currentTime - (30L * 24L * 60L * 60L * 1000L)
                    
                    for (element in remoteDevicesArray) {
                        try {
                            val devObj = element.jsonObject
                            val p = devObj["platform"]?.jsonPrimitive?.content ?: ""
                            val n = devObj["deviceName"]?.jsonPrimitive?.content ?: ""
                            val t = devObj["lastSyncTime"]?.jsonPrimitive?.longOrNull ?: 0L
                            
                            if ((p == currentPlatform && n == currentDeviceName) || t < thirtyDaysAgo) {
                                continue
                            }
                            mergedDevices.add(devObj)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    val currentDeviceJson = kotlinx.serialization.json.buildJsonObject {
                        put("platform", kotlinx.serialization.json.JsonPrimitive(currentPlatform))
                        put("deviceName", kotlinx.serialization.json.JsonPrimitive(currentDeviceName))
                        put("lastSyncTime", kotlinx.serialization.json.JsonPrimitive(currentTime))
                    }
                    mergedDevices.add(currentDeviceJson)

                    // 4. Upload do arquivo atualizado
                    val activitiesJsonList = mergedCustomActive
                    val completedJsonList = mergedCustomCompleted

                    val tempUploadFile = java.io.File.createTempFile("TBCalendar_Sync_Data", ".json")
                    try {
                        val syncJson = kotlinx.serialization.json.buildJsonObject {
                            put("backupVersion", kotlinx.serialization.json.JsonPrimitive("1.1"))
                            put("createdAt", kotlinx.serialization.json.JsonPrimitive(java.time.LocalDateTime.now().toString()))
                            put("appVersion", kotlinx.serialization.json.JsonPrimitive("TheBigCalendar"))
                            
                            put("activities", kotlinx.serialization.json.buildJsonArray {
                                activitiesJsonList.forEach { act ->
                                    add(Json.parseToJsonElement(Json.encodeToString(act)))
                                }
                            })
                            
                            put("completedActivities", kotlinx.serialization.json.buildJsonArray {
                                completedJsonList.forEach { act ->
                                    add(Json.parseToJsonElement(Json.encodeToString(act)))
                                }
                            })
                            
                            put("deletedActivities", kotlinx.serialization.json.buildJsonArray {
                                finalDeletedIds.forEach { id ->
                                    add(kotlinx.serialization.json.JsonPrimitive(id))
                                }
                            })

                            put("devices", kotlinx.serialization.json.buildJsonArray {
                                mergedDevices.forEach { add(it) }
                            })
                        }

                        val shouldEncrypt = if (isDisablingEncryption) false else (_uiState.value.isEncryptionEnabled && _uiState.value.encryptionPassword.isNotEmpty())
                        val syncContent = if (shouldEncrypt) {
                            com.mss.thebigcalendar.crypto.CryptoHelper.encrypt(Json.encodeToString(syncJson), _uiState.value.encryptionPassword)
                        } else {
                            Json.encodeToString(syncJson)
                        }

                        tempUploadFile.writeText(syncContent, Charsets.UTF_8)

                        val mediaContent = com.google.api.client.http.FileContent("application/json", tempUploadFile)

                        if (fileId != null) {
                            val updateMetadata = com.google.api.services.drive.model.File().apply {
                                name = "TBCalendar_Sync_Data.json"
                            }
                            driveService.files().update(fileId, updateMetadata, mediaContent).execute()
                        } else {
                            val createMetadata = com.google.api.services.drive.model.File().apply {
                                name = "TBCalendar_Sync_Data.json"
                                parents = listOf("appDataFolder")
                            }
                            driveService.files().create(createMetadata, mediaContent).execute()
                        }
                        _uiState.update { 
                            it.copy(
                                isSyncing = false,
                                syncMessage = "Sincronização com a nuvem concluída!",
                                showDecryptionDialog = false,
                                isDecryptionLoading = false
                            ) 
                        }
                        saveData()
                    } finally {
                        if (tempUploadFile.exists()) tempUploadFile.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (e is com.mss.thebigcalendar.crypto.DecryptionRequiredException || e is com.mss.thebigcalendar.crypto.DecryptionFailedException) {
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            showDecryptionDialog = true,
                            isDecryptionLoading = false,
                            encryptionPassword = "",
                            isEncryptionEnabled = false,
                            decryptionErrorMessage = e.message
                        ) 
                    }
                    saveData()
                } else {
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            showDecryptionDialog = false,
                            isDecryptionLoading = false,
                            syncMessage = "Erro na sincronização: ${e.message}"
                        ) 
                    }
                }
            }
        }
    }
}
