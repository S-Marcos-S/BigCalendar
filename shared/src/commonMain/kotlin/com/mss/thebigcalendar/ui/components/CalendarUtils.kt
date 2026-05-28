package com.mss.thebigcalendar.ui.components

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import com.mss.thebigcalendar.data.model.CalendarDay

object CalendarUtils {
    
    fun getContrastingTextColor(backgroundColor: Color): Color {
        val luminance = (0.299 * backgroundColor.red +
                0.587 * backgroundColor.green +
                0.114 * backgroundColor.blue)
        return if (luminance > 0.5) Color.Black else Color.White
    }

    fun getWeekDayAbbreviations(locale: Locale = Locale.getDefault()): List<String> {
        val sunday = DayOfWeek.SUNDAY
        val orderedDays = (0..6).map { sunday.plus(it.toLong()) }

        return orderedDays.map {
            it.getDisplayName(TextStyle.SHORT_STANDALONE, locale).uppercase(locale)
        }
    }

    fun generateCalendarDays(
        yearMonth: YearMonth,
        selectedDate: LocalDate
    ): List<CalendarDay> {
        val firstDayOfMonth = yearMonth.atDay(1)
        val firstDayOfWeekValue = firstDayOfMonth.dayOfWeek.value // MONDAY (1) to SUNDAY (7)
        val daysFromPrevMonthOffset = (firstDayOfWeekValue % 7) // Offset para iniciar a semana no Domingo

        val gridStartDate = firstDayOfMonth.minusDays(daysFromPrevMonthOffset.toLong())

        val calendarDays = mutableListOf<CalendarDay>()
        var currentDateIterator = gridStartDate

        // Gera 42 dias (6 semanas * 7 dias)
        repeat(42) {
            val day = CalendarDay(
                date = currentDateIterator,
                isCurrentMonth = currentDateIterator.month == yearMonth.month,
                isSelected = currentDateIterator.isEqual(selectedDate)
            )
            calendarDays.add(day)
            currentDateIterator = currentDateIterator.plusDays(1)
        }
        return calendarDays
    }
}
