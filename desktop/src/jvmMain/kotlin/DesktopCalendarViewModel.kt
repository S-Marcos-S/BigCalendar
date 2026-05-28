import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.ui.components.CalendarUtils
import com.mss.thebigcalendar.ui.viewmodel.BaseCalendarViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update

class DesktopCalendarViewModel(scope: CoroutineScope) : BaseCalendarViewModel(scope) {
    
    init {
        updateCalendarDays()
    }

    override fun updateCalendarDays() {
        val days = CalendarUtils.generateCalendarDays(
            uiState.value.displayedYearMonth,
            uiState.value.selectedDate
        )
        _uiState.update { it.copy(calendarDays = days) }
    }

    override fun onSearchIconClick() {}
    override fun onChartIconClick() {}
    override fun onTrashIconClick() {}
    override fun onBackupIconClick() {}
    override fun onNotesClick() {}
    override fun onAlarmsClick() {}
    override fun onPrintCalendarClick() {}
    override fun onNavigateToSettings(screen: String) {}
    override fun openCreateActivityModal(activityType: ActivityType) {}
}
