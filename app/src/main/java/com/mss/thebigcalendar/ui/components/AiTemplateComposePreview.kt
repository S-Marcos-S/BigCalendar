package com.mss.thebigcalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.CalendarAiTemplateSpec
import com.mss.thebigcalendar.data.model.CalendarLayoutType
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.TemplateFontFamily
import com.mss.thebigcalendar.data.model.TitleAlignment
import com.mss.thebigcalendar.data.model.WeekDayDisplayFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AiTemplateComposePreview(
    template: CalendarAiTemplateSpec,
    selectedMonth: YearMonth,
    activities: List<Activity> = emptyList(),
    holidays: List<Holiday> = emptyList(),
    moonPhases: List<MoonPhase> = emptyList(),
    modifier: Modifier = Modifier
) {
    val pageBg = remember(template.pageBackgroundColor) {
        CalendarAiTemplateSpec.parseHexColor(template.pageBackgroundColor, Color.White)
    }
    val gridBg = remember(template.gridBackgroundColor) {
        CalendarAiTemplateSpec.parseHexColor(template.gridBackgroundColor, Color.White)
    }
    val primaryColor = remember(template.primaryColor) {
        CalendarAiTemplateSpec.parseHexColor(template.primaryColor, Color.Black)
    }
    val secondaryColor = remember(template.secondaryColor) {
        CalendarAiTemplateSpec.parseHexColor(template.secondaryColor, Color.DarkGray)
    }
    val weekendColor = remember(template.weekendColor) {
        CalendarAiTemplateSpec.parseHexColor(template.weekendColor, Color.Red)
    }
    val holidayColor = remember(template.holidayColor) {
        CalendarAiTemplateSpec.parseHexColor(template.holidayColor, Color.Red)
    }
    val gridBorderColor = remember(template.gridBorderColor) {
        CalendarAiTemplateSpec.parseHexColor(template.gridBorderColor, Color.LightGray)
    }
    val weekdayHeaderBg = remember(template.weekdayHeaderBackgroundColor) {
        template.weekdayHeaderBackgroundColor?.let { CalendarAiTemplateSpec.parseHexColor(it) }
    }
    val weekdayTextColor = remember(template.weekdayTextColor) {
        CalendarAiTemplateSpec.parseHexColor(template.weekdayTextColor, Color.DarkGray)
    }
    val dayNumberColor = remember(template.dayNumberColor) {
        CalendarAiTemplateSpec.parseHexColor(template.dayNumberColor, Color.Black)
    }

    val composeFontFamily = remember(template.fontFamily) {
        when (template.fontFamily) {
            TemplateFontFamily.SERIF -> FontFamily.Serif
            TemplateFontFamily.CURSIVE -> FontFamily.Cursive
            TemplateFontFamily.MONO -> FontFamily.Monospace
            TemplateFontFamily.SANS -> FontFamily.SansSerif
            TemplateFontFamily.DEFAULT -> FontFamily.Default
        }
    }

    val aspectRatio = if (template.isLandscape) 1.414f else 0.707f // A4 ratio

    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = pageBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            when (template.layoutType) {
                CalendarLayoutType.SIDEBAR_LEFT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Barra lateral esquerda
                        Box(
                            modifier = Modifier
                                .weight(template.sidebarWidthPercent / 100f)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                        ) {
                            SidebarContent(
                                template = template,
                                selectedMonth = selectedMonth,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                fontFamily = composeFontFamily
                            )
                        }

                        // Grade à direita
                        Box(
                            modifier = Modifier
                                .weight((100f - template.sidebarWidthPercent) / 100f)
                                .fillMaxHeight()
                        ) {
                            GridContent(
                                template = template,
                                selectedMonth = selectedMonth,
                                activities = activities,
                                holidays = holidays,
                                moonPhases = moonPhases,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                weekendColor = weekendColor,
                                holidayColor = holidayColor,
                                gridBg = gridBg,
                                gridBorderColor = gridBorderColor,
                                weekdayHeaderBg = weekdayHeaderBg,
                                weekdayTextColor = weekdayTextColor,
                                dayNumberColor = dayNumberColor,
                                fontFamily = composeFontFamily,
                                showHeader = false
                            )
                        }
                    }
                }
                CalendarLayoutType.SIDEBAR_RIGHT -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Grade à esquerda
                        Box(
                            modifier = Modifier
                                .weight((100f - template.sidebarWidthPercent) / 100f)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                        ) {
                            GridContent(
                                template = template,
                                selectedMonth = selectedMonth,
                                activities = activities,
                                holidays = holidays,
                                moonPhases = moonPhases,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                weekendColor = weekendColor,
                                holidayColor = holidayColor,
                                gridBg = gridBg,
                                gridBorderColor = gridBorderColor,
                                weekdayHeaderBg = weekdayHeaderBg,
                                weekdayTextColor = weekdayTextColor,
                                dayNumberColor = dayNumberColor,
                                fontFamily = composeFontFamily,
                                showHeader = true
                            )
                        }

                        // Barra lateral direita
                        Box(
                            modifier = Modifier
                                .weight(template.sidebarWidthPercent / 100f)
                                .fillMaxHeight()
                        ) {
                            SidebarContent(
                                template = template,
                                selectedMonth = selectedMonth,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                fontFamily = composeFontFamily
                            )
                        }
                    }
                }
                CalendarLayoutType.TOP_BOTTOM_SPLIT -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Header / Goals
                        TopHeaderContent(
                            template = template,
                            selectedMonth = selectedMonth,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            fontFamily = composeFontFamily
                        )

                        // Central Grid
                        Box(modifier = Modifier.weight(1f)) {
                            GridContent(
                                template = template,
                                selectedMonth = selectedMonth,
                                activities = activities,
                                holidays = holidays,
                                moonPhases = moonPhases,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                weekendColor = weekendColor,
                                holidayColor = holidayColor,
                                gridBg = gridBg,
                                gridBorderColor = gridBorderColor,
                                weekdayHeaderBg = weekdayHeaderBg,
                                weekdayTextColor = weekdayTextColor,
                                dayNumberColor = dayNumberColor,
                                fontFamily = composeFontFamily,
                                showHeader = false
                            )
                        }

                        // Bottom Tracker / Notes
                        if (template.habitTracker.enabled || template.notesSection.enabled) {
                            BottomSectionContent(
                                template = template,
                                primaryColor = primaryColor,
                                secondaryColor = secondaryColor,
                                fontFamily = composeFontFamily
                            )
                        }
                    }
                }
                CalendarLayoutType.MINIMALIST_CLEAN,
                CalendarLayoutType.PLANNER_BULLET,
                CalendarLayoutType.STANDARD_GRID -> {
                    GridContent(
                        template = template,
                        selectedMonth = selectedMonth,
                        activities = activities,
                        holidays = holidays,
                        moonPhases = moonPhases,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        weekendColor = weekendColor,
                        holidayColor = holidayColor,
                        gridBg = gridBg,
                        gridBorderColor = gridBorderColor,
                        weekdayHeaderBg = weekdayHeaderBg,
                        weekdayTextColor = weekdayTextColor,
                        dayNumberColor = dayNumberColor,
                        fontFamily = composeFontFamily,
                        showHeader = true
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarContent(
    template: CalendarAiTemplateSpec,
    selectedMonth: YearMonth,
    primaryColor: Color,
    secondaryColor: Color,
    fontFamily: FontFamily
) {
    val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = monthName,
            fontFamily = fontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = selectedMonth.year.toString(),
            fontFamily = fontFamily,
            fontSize = 10.sp,
            color = secondaryColor
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (template.goalsSection.enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        template.goalsSection.boxBackgroundColor?.let {
                            CalendarAiTemplateSpec.parseHexColor(it)
                        } ?: secondaryColor.copy(alpha = 0.15f)
                    )
                    .padding(4.dp)
            ) {
                Column {
                    Text(
                        text = template.goalsSection.title.uppercase(),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    repeat(template.goalsSection.itemsCount.coerceAtMost(3)) {
                        Text(text = "□ ________", fontSize = 6.sp, color = secondaryColor)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (template.notesSection.enabled) {
            Text(
                text = template.notesSection.title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
            repeat(8) {
                Text(
                    text = ". . . . . . . . . . . . .",
                    fontSize = 6.sp,
                    color = Color.LightGray,
                    lineHeight = 7.sp
                )
            }
        }

        if (!template.motivationalQuote.isNullOrBlank()) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "\"${template.motivationalQuote}\"",
                fontSize = 7.sp,
                fontStyle = FontStyle.Italic,
                color = secondaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TopHeaderContent(
    template: CalendarAiTemplateSpec,
    selectedMonth: YearMonth,
    primaryColor: Color,
    secondaryColor: Color,
    fontFamily: FontFamily
) {
    val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
        .replaceFirstChar { it.uppercase() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthName,
            fontFamily = fontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )

        if (template.goalsSection.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${template.goalsSection.title}: ",
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(text = "□ ____ □ ____", fontSize = 7.sp, color = secondaryColor)
            }
        }
    }
}

@Composable
private fun BottomSectionContent(
    template: CalendarAiTemplateSpec,
    primaryColor: Color,
    secondaryColor: Color,
    fontFamily: FontFamily
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (template.habitTracker.enabled) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.habitTracker.title,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(text = "Hábitos: ○ ○ ○ ○ ○", fontSize = 6.sp, color = secondaryColor)
            }
        }
        if (template.notesSection.enabled) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.notesSection.title,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )
                Text(text = "____________________", fontSize = 6.sp, color = Color.LightGray)
            }
        }
    }
}

@Composable
private fun GridContent(
    template: CalendarAiTemplateSpec,
    selectedMonth: YearMonth,
    activities: List<Activity>,
    holidays: List<Holiday>,
    moonPhases: List<MoonPhase>,
    primaryColor: Color,
    secondaryColor: Color,
    weekendColor: Color,
    holidayColor: Color,
    gridBg: Color,
    gridBorderColor: Color,
    weekdayHeaderBg: Color?,
    weekdayTextColor: Color,
    dayNumberColor: Color,
    fontFamily: FontFamily,
    showHeader: Boolean
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (showHeader) {
            val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
                .replaceFirstChar { it.uppercase() }
            Text(
                text = monthName,
                fontFamily = fontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                textAlign = when (template.monthTitleAlignment) {
                    TitleAlignment.LEFT -> TextAlign.Start
                    TitleAlignment.RIGHT -> TextAlign.End
                    TitleAlignment.CENTER -> TextAlign.Center
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
            )
        }

        // Cabeçalhos dos dias da semana
        val weekDays = if (template.weekStartsOnMonday) {
            when (template.weekdayFormat) {
                WeekDayDisplayFormat.FULL -> listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
                WeekDayDisplayFormat.SINGLE_LETTER -> listOf("S", "T", "Q", "Q", "S", "S", "D")
                WeekDayDisplayFormat.SHORT -> listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
            }
        } else {
            when (template.weekdayFormat) {
                WeekDayDisplayFormat.FULL -> listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
                WeekDayDisplayFormat.SINGLE_LETTER -> listOf("D", "S", "T", "Q", "Q", "S", "S")
                WeekDayDisplayFormat.SHORT -> listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (weekdayHeaderBg != null) Modifier.background(weekdayHeaderBg, RoundedCornerShape(2.dp))
                    else Modifier
                )
                .padding(vertical = 1.dp)
        ) {
            weekDays.forEach { dayName ->
                Text(
                    text = dayName,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold,
                    color = weekdayTextColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Grade de dias (6 semanas x 7 dias)
        val firstDayOfMonth = selectedMonth.atDay(1)
        val firstDayOfWeekVal = firstDayOfMonth.dayOfWeek.value
        val offset = if (template.weekStartsOnMonday) firstDayOfWeekVal - 1 else if (firstDayOfWeekVal == 7) 0 else firstDayOfWeekVal
        val startDate = firstDayOfMonth.minusDays(offset.toLong())

        Column(modifier = Modifier.weight(1f)) {
            for (week in 0..5) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    for (day in 0..6) {
                        val currentDate = startDate.plusDays((week * 7 + day).toLong())
                        val isCurrentMonth = currentDate.month == selectedMonth.month
                        val isWeekend = currentDate.dayOfWeek == DayOfWeek.SATURDAY || currentDate.dayOfWeek == DayOfWeek.SUNDAY

                        val dayNumColor = if (isWeekend) weekendColor else dayNumberColor
                        val dateStr = currentDate.toString()
                        val hasHoliday = holidays.any { it.date == dateStr }
                        val dayActivitiesCount = activities.count { it.date == dateStr }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(0.5.dp)
                                .clip(RoundedCornerShape(template.dayCellCornerRadius.coerceAtMost(3f).dp))
                                .background(gridBg)
                                .border(
                                    width = (template.gridBorderWidth.coerceIn(0.2f, 1f)).dp,
                                    color = if (template.gridBorderWidth > 0f) gridBorderColor else Color.Transparent,
                                    shape = RoundedCornerShape(template.dayCellCornerRadius.coerceAtMost(3f).dp)
                                )
                                .padding(1.dp)
                        ) {
                            if (isCurrentMonth) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = currentDate.dayOfMonth.toString(),
                                        fontSize = 6.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = dayNumColor
                                    )

                                    if (hasHoliday) {
                                        Box(
                                            modifier = Modifier
                                                .size(2.5.dp)
                                                .clip(CircleShape)
                                                .background(holidayColor)
                                        )
                                    }

                                    if (dayActivitiesCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.5.dp)
                                                .clip(RoundedCornerShape(0.5.dp))
                                                .background(secondaryColor.copy(alpha = 0.7f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
