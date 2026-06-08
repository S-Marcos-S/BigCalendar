package com.mss.thebigcalendar.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mss.thebigcalendar.data.model.CalendarFilterOptions
import com.mss.thebigcalendar.data.model.Theme
import com.mss.thebigcalendar.data.model.AnimationType
import com.mss.thebigcalendar.data.model.SidebarFilterVisibility
import com.mss.thebigcalendar.data.model.Language

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create the DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME = stringPreferencesKey("theme")
        val WELCOME_NAME = stringPreferencesKey("welcome_name")
        val SHOW_HOLIDAYS = booleanPreferencesKey("show_holidays")
        val SHOW_SAINT_DAYS = booleanPreferencesKey("show_saint_days")
        val SHOW_EVENTS = booleanPreferencesKey("show_events")
        val SHOW_TASKS = booleanPreferencesKey("show_tasks")
        val SHOW_BIRTHDAYS = booleanPreferencesKey("show_birthdays")
        val SHOW_NOTES = booleanPreferencesKey("show_notes")
        val SHOW_COMMEMORATIVE = booleanPreferencesKey("show_commemorative")
        val SHOW_MOON_PHASES = booleanPreferencesKey("show_moon_phases")
        val ANIMATION_TYPE = stringPreferencesKey("animation_type")
        val LANGUAGE = stringPreferencesKey("language")
        val CALENDAR_SCALE = stringPreferencesKey("calendar_scale")
        val HIDE_OTHER_MONTH_DAYS = booleanPreferencesKey("hide_other_month_days")
        val PURE_BLACK_THEME = booleanPreferencesKey("pure_black_theme")
        val PRIMARY_COLOR = stringPreferencesKey("primary_color")
        
        // Sidebar filter visibility
        val SIDEBAR_SHOW_HOLIDAYS = booleanPreferencesKey("sidebar_show_holidays")
        val SIDEBAR_SHOW_SAINT_DAYS = booleanPreferencesKey("sidebar_show_saint_days")
        val SIDEBAR_SHOW_EVENTS = booleanPreferencesKey("sidebar_show_events")
        val SIDEBAR_SHOW_TASKS = booleanPreferencesKey("sidebar_show_tasks")
        val SIDEBAR_SHOW_BIRTHDAYS = booleanPreferencesKey("sidebar_show_birthdays")
        val SIDEBAR_SHOW_NOTES = booleanPreferencesKey("sidebar_show_notes")
        val SIDEBAR_SHOW_COMPLETED_TASKS = booleanPreferencesKey("sidebar_show_completed_tasks")
        val SIDEBAR_SHOW_MOON_PHASES = booleanPreferencesKey("sidebar_show_moon_phases")
        val SIDEBAR_SHOW_COMMEMORATIVE = booleanPreferencesKey("sidebar_show_commemorative")

        // Auto Backup Settings
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_FREQUENCY = stringPreferencesKey("auto_backup_frequency")
        val AUTO_BACKUP_HOUR = stringPreferencesKey("auto_backup_hour")
        val AUTO_BACKUP_MINUTE = stringPreferencesKey("auto_backup_minute")
        val AUTO_BACKUP_TYPE = stringPreferencesKey("auto_backup_type")

        val CRASHLYTICS_ENABLED = booleanPreferencesKey("crashlytics_enabled")
        val HAS_SEEN_MAIN_ONBOARDING = booleanPreferencesKey("has_seen_main_onboarding")
        val BACKUP_DIRECTORY_URI = stringPreferencesKey("backup_directory_uri")
    }

    val backupDirectoryUri: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.BACKUP_DIRECTORY_URI]
        }

    suspend fun saveBackupDirectoryUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_DIRECTORY_URI] = uri
        }
    }

    val hasSeenMainOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_MAIN_ONBOARDING] ?: false
        }

    suspend fun setHasSeenMainOnboarding(seen: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAS_SEEN_MAIN_ONBOARDING] = seen
        }
    }

    val isCrashlyticsEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.CRASHLYTICS_ENABLED] ?: false
        }

    suspend fun setCrashlyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CRASHLYTICS_ENABLED] = enabled
        }
    }

    val theme: Flow<Theme> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[PreferencesKeys.THEME] ?: Theme.SYSTEM.name
            Theme.valueOf(themeName)
        }

    val welcomeName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.WELCOME_NAME] ?: ""
        }

    val showMoonPhases: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SHOW_MOON_PHASES] ?: false
        }

    val animationType: Flow<AnimationType> = context.dataStore.data
        .map { preferences ->
            val animationName = preferences[PreferencesKeys.ANIMATION_TYPE] ?: AnimationType.NONE.name
            AnimationType.valueOf(animationName)
        }

    val language: Flow<Language> = context.dataStore.data
        .map { preferences ->
            val languageCode = preferences[PreferencesKeys.LANGUAGE] ?: Language.SYSTEM.code
            Language.fromCode(languageCode)
        }

    // Calendar scale (persisted as String to ensure compatibility)
    val calendarScale: Flow<Float> = context.dataStore.data
        .map { preferences ->
            val raw = preferences[PreferencesKeys.CALENDAR_SCALE]
            raw?.toFloatOrNull() ?: 1f
        }

    suspend fun setCalendarScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.CALENDAR_SCALE] = scale.toString()
        }
    }

    val hideOtherMonthDays: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.HIDE_OTHER_MONTH_DAYS] ?: false
        }

    suspend fun setHideOtherMonthDays(hide: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.HIDE_OTHER_MONTH_DAYS] = hide
        }
    }

    val pureBlackTheme: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.PURE_BLACK_THEME] ?: false
        }

    suspend fun setPureBlackTheme(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PURE_BLACK_THEME] = enabled
        }
    }

    val primaryColor: Flow<String> = context.dataStore.data
        .map { preferences ->
            val savedColor = preferences[PreferencesKeys.PRIMARY_COLOR]
            when {
                savedColor == null -> "AUTO" // Primeira vez - usar automático
                else -> savedColor // Manter valor personalizado
            }
        }

    suspend fun setPrimaryColor(colorHex: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.PRIMARY_COLOR] = colorHex
        }
    }

    val sidebarFilterVisibility: Flow<SidebarFilterVisibility> = context.dataStore.data
        .map { preferences ->
            SidebarFilterVisibility(
                showHolidays = preferences[PreferencesKeys.SIDEBAR_SHOW_HOLIDAYS] ?: true,
                showSaintDays = preferences[PreferencesKeys.SIDEBAR_SHOW_SAINT_DAYS] ?: false, // Desativado por padrão
                showEvents = preferences[PreferencesKeys.SIDEBAR_SHOW_EVENTS] ?: true,
                showTasks = preferences[PreferencesKeys.SIDEBAR_SHOW_TASKS] ?: true,
                showBirthdays = preferences[PreferencesKeys.SIDEBAR_SHOW_BIRTHDAYS] ?: true,
                showNotes = preferences[PreferencesKeys.SIDEBAR_SHOW_NOTES] ?: true,
                showCompletedTasks = preferences[PreferencesKeys.SIDEBAR_SHOW_COMPLETED_TASKS] ?: true,
                showMoonPhases = preferences[PreferencesKeys.SIDEBAR_SHOW_MOON_PHASES] ?: true,
                showCommemorative = preferences[PreferencesKeys.SIDEBAR_SHOW_COMMEMORATIVE] ?: true
            )
        }

    val autoBackupSettings: Flow<AutoBackupSettings> = context.dataStore.data
        .map { preferences ->
            AutoBackupSettings(
                enabled = preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: false,
                frequency = BackupFrequency.valueOf(preferences[PreferencesKeys.AUTO_BACKUP_FREQUENCY] ?: BackupFrequency.DAILY.name),
                hour = preferences[PreferencesKeys.AUTO_BACKUP_HOUR]?.toIntOrNull() ?: 2,
                minute = preferences[PreferencesKeys.AUTO_BACKUP_MINUTE]?.toIntOrNull() ?: 0,
                backupType = BackupType.valueOf(preferences[PreferencesKeys.AUTO_BACKUP_TYPE] ?: BackupType.LOCAL.name)
            )
        }



            val filterOptions: Flow<CalendarFilterOptions> = context.dataStore.data
        .map { preferences ->
            CalendarFilterOptions(
                showHolidays = preferences[PreferencesKeys.SHOW_HOLIDAYS] ?: true,
                showSaintDays = preferences[PreferencesKeys.SHOW_SAINT_DAYS] ?: false, // Desativado por padrão na primeira inicialização
                showEvents = preferences[PreferencesKeys.SHOW_EVENTS] ?: true,
                showTasks = preferences[PreferencesKeys.SHOW_TASKS] ?: true,
                showBirthdays = preferences[PreferencesKeys.SHOW_BIRTHDAYS] ?: true,
                showNotes = preferences[PreferencesKeys.SHOW_NOTES] ?: true,
                showCommemorative = preferences[PreferencesKeys.SHOW_COMMEMORATIVE] ?: true
            )
        }

    suspend fun saveTheme(theme: Theme) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME] = theme.name
        }
    }

    suspend fun saveWelcomeName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.WELCOME_NAME] = name
        }
    }

    suspend fun saveFilterOptions(filterOptions: CalendarFilterOptions) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_HOLIDAYS] = filterOptions.showHolidays
            preferences[PreferencesKeys.SHOW_SAINT_DAYS] = filterOptions.showSaintDays
            preferences[PreferencesKeys.SHOW_EVENTS] = filterOptions.showEvents
            preferences[PreferencesKeys.SHOW_TASKS] = filterOptions.showTasks
            preferences[PreferencesKeys.SHOW_BIRTHDAYS] = filterOptions.showBirthdays
            preferences[PreferencesKeys.SHOW_NOTES] = filterOptions.showNotes
            preferences[PreferencesKeys.SHOW_COMMEMORATIVE] = filterOptions.showCommemorative
        }
    }

    suspend fun saveShowMoonPhases(showMoonPhases: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_MOON_PHASES] = showMoonPhases
        }
    }

    suspend fun saveAnimationType(animationType: AnimationType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ANIMATION_TYPE] = animationType.name
        }
    }

    suspend fun saveLanguage(language: Language) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LANGUAGE] = language.code
        }
        // Also save to SharedPreferences for immediate reload in MainActivity
        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("selected_language", language.code).apply()
    }

            suspend fun saveSidebarFilterVisibility(visibility: SidebarFilterVisibility) {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.SIDEBAR_SHOW_HOLIDAYS] = visibility.showHolidays
                preferences[PreferencesKeys.SIDEBAR_SHOW_SAINT_DAYS] = visibility.showSaintDays
                preferences[PreferencesKeys.SIDEBAR_SHOW_EVENTS] = visibility.showEvents
                preferences[PreferencesKeys.SIDEBAR_SHOW_TASKS] = visibility.showTasks
                preferences[PreferencesKeys.SIDEBAR_SHOW_BIRTHDAYS] = visibility.showBirthdays
                preferences[PreferencesKeys.SIDEBAR_SHOW_NOTES] = visibility.showNotes
                preferences[PreferencesKeys.SIDEBAR_SHOW_COMPLETED_TASKS] = visibility.showCompletedTasks
                preferences[PreferencesKeys.SIDEBAR_SHOW_MOON_PHASES] = visibility.showMoonPhases
                preferences[PreferencesKeys.SIDEBAR_SHOW_COMMEMORATIVE] = visibility.showCommemorative
            }
        }
    
        suspend fun saveAutoBackupSettings(settings: AutoBackupSettings) {
            context.dataStore.edit { preferences ->
                preferences[PreferencesKeys.AUTO_BACKUP_ENABLED] = settings.enabled
                preferences[PreferencesKeys.AUTO_BACKUP_FREQUENCY] = settings.frequency.name
                preferences[PreferencesKeys.AUTO_BACKUP_HOUR] = settings.hour.toString()
                preferences[PreferencesKeys.AUTO_BACKUP_MINUTE] = settings.minute.toString()
                preferences[PreferencesKeys.AUTO_BACKUP_TYPE] = settings.backupType.name
            }
        }
    
    
    }
