package com.mss.thebigcalendar.ui.viewmodel

import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.CalendarUiState
import com.mss.thebigcalendar.data.model.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth

abstract class BaseCalendarViewModel(protected val scope: CoroutineScope) {
    protected val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    fun onPreviousMonth() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.minusMonths(1)) }
        updateCalendarDays()
    }

    fun onNextMonth() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.plusMonths(1)) }
        updateCalendarDays()
    }

    fun onPreviousYear() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.minusYears(1)) }
        updateCalendarDays()
    }

    fun onNextYear() {
        _uiState.update { it.copy(displayedYearMonth = it.displayedYearMonth.plusYears(1)) }
        updateCalendarDays()
    }

    fun onGoToToday() {
        _uiState.update { it.copy(
            displayedYearMonth = YearMonth.now(),
            selectedDate = LocalDate.now()
        ) }
        updateCalendarDays()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        updateCalendarDays()
    }

    fun onViewModeChange(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun openSidebar() {
        _uiState.update { it.copy(isSidebarOpen = true) }
    }

    fun closeSidebar() {
        _uiState.update { it.copy(isSidebarOpen = false) }
    }

    abstract fun updateCalendarDays()
    
    // UI Navigation placeholders
    abstract fun onSearchIconClick()
    abstract fun onChartIconClick()
    abstract fun onTrashIconClick()
    abstract fun onBackupIconClick()
    abstract fun onNotesClick()
    abstract fun onAlarmsClick()
    abstract fun onPrintCalendarClick()
    abstract fun onNavigateToSettings(screen: String)
    abstract fun openCreateActivityModal(activityType: ActivityType)
}
