package com.mss.thebigcalendar.data.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

enum class ActivityType {
    EVENT,
    TASK,
    NOTE,
    BIRTHDAY
}

enum class VisibilityLevel {
    LOW,    // Baixa
    MEDIUM, // Média
    HIGH    // Alta
}

data class Activity(
    val id: String,
    val title: String,
    val description: String?,
    val date: String, // "yyyy-MM-dd"
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val isAllDay: Boolean,
    val location: String?,
    val categoryColor: String,
    val activityType: ActivityType,
    val recurrenceRule: String?,
    // Move logic related to NotificationSettings if it's too platform-specific
    val isCompleted: Boolean = false,
    val visibility: VisibilityLevel = VisibilityLevel.LOW,
    val showInCalendar: Boolean = true,
    val isFromGoogle: Boolean = false,
    val excludedDates: List<String> = emptyList(),
    val excludedInstances: List<String> = emptyList(),
    val wikipediaLink: String? = null,
    val rollover: Boolean = false
)

@Immutable
data class CalendarDay(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isSelected: Boolean = false,
    val isToday: Boolean = false,
    val tasks: List<Activity> = emptyList(),
    val holiday: Holiday? = null,
    val isWeekend: Boolean = false,
    val isNationalHoliday: Boolean = false,
    val isSaintDay: Boolean = false,
    val isJsonHolidayDay: Boolean = false
)

enum class Theme { LIGHT, DARK, SYSTEM }
enum class ViewMode { MONTHLY, YEARLY }
enum class HolidayType { NATIONAL, COMMEMORATIVE, SAINT, JSON_IMPORT }

@Immutable
data class Holiday(
    val name: String, 
    val date: String, 
    val type: HolidayType, 
    val summary: String? = null, 
    val wikipediaLink: String? = null
)
