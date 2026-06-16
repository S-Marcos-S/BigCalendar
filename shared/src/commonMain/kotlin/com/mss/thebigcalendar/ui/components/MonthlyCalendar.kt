package com.mss.thebigcalendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.CalendarDay
import com.mss.thebigcalendar.data.model.Theme
import java.time.LocalDate
import java.util.Locale

@Composable
fun MonthlyCalendar(
    modifier: Modifier = Modifier,
    calendarDays: List<CalendarDay>,
    onDateSelected: (LocalDate) -> Unit,
    theme: Theme,
    verticalScale: Float = 1f,
    hideOtherMonthDays: Boolean = false
) {
    val weekDayAbbreviations = CalendarUtils.getWeekDayAbbreviations()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDayAbbreviations.forEach { dayAbbreviation ->
                Text(
                    text = dayAbbreviation,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
            if (hideOtherMonthDays) {
                val currentMonthDays = remember(calendarDays) { 
                    calendarDays.filter { it.isCurrentMonth } 
                }
                
                val firstDayOfMonth = remember(currentMonthDays) {
                    currentMonthDays.firstOrNull()?.date?.dayOfWeek?.value ?: 1
                }
                val startOffset = if (firstDayOfMonth == 7) 0 else firstDayOfMonth
                
                val daysPerRow = 7
                val totalCells = currentMonthDays.size + startOffset
                val rows = remember(totalCells) { 
                    (totalCells + daysPerRow - 1) / daysPerRow 
                }
                
                repeat(rows) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        repeat(daysPerRow) { colIndex ->
                            val cellIndex = rowIndex * daysPerRow + colIndex
                            Box(modifier = Modifier.weight(1f)) {
                                if (cellIndex >= startOffset && cellIndex < startOffset + currentMonthDays.size) {
                                    val dayIndex = cellIndex - startOffset
                                    DayCell(
                                        day = currentMonthDays[dayIndex],
                                        onDateSelected = onDateSelected,
                                        theme = theme,
                                        verticalScale = verticalScale
                                    )
                                } else {
                                    Spacer(modifier = Modifier
                                        .padding(1.dp)
                                        .aspectRatio((1f / 1.35f) / verticalScale.coerceAtLeast(0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val weeks = remember(calendarDays) { calendarDays.chunked(7) }
                weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f)) {
                                DayCell(
                                    day = day,
                                    onDateSelected = onDateSelected,
                                    theme = theme,
                                    verticalScale = verticalScale
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    theme: Theme,
    verticalScale: Float = 1f
) {
    val isDark = when (theme) {
        Theme.DARK -> true
        Theme.LIGHT -> false
        Theme.SYSTEM -> isSystemInDarkTheme()
    }
    val cellModifier = modifier
        .padding(1.dp)
        .aspectRatio((1f / 1.35f) / verticalScale.coerceAtLeast(0.5f))
        .clip(MaterialTheme.shapes.small)
        .then(
            when {
                day.isToday -> Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                day.isCurrentMonth -> Modifier.border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), MaterialTheme.shapes.small)
                else -> Modifier
            }
        )
        .background(
            when {
                day.isSelected -> MaterialTheme.colorScheme.primaryContainer
                day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else -> Color.Transparent
            }
        )
        .clickable {
            onDateSelected(day.date)
        }
        .padding(vertical = 0.dp, horizontal = 2.dp)

    val isCompact = verticalScale <= 0.65f

    Column(
        modifier = cellModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            textAlign = TextAlign.Center,
            style = if (day.isSelected) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            else MaterialTheme.typography.bodyLarge,
            color = when {
                day.isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                day.isNationalHoliday -> Color.Red
                day.isWeekend -> MaterialTheme.colorScheme.primary
                day.isCurrentMonth -> if (isDark) Color.White else Color.Black
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.padding(top = 2.dp)
        )
        var linesBudget = 4

        if (!isCompact && day.holiday != null && linesBudget > 0) {
            val isNationalHoliday = day.isNationalHoliday
            Text(
                text = day.holiday.name,
                color = when {
                    isNationalHoliday -> Color.Red
                    else -> MaterialTheme.colorScheme.secondary
                },
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 1.dp),
                fontWeight = if (isNationalHoliday) FontWeight.Bold else FontWeight.Normal
            )
            linesBudget -= 1
        }

        val visibleTasks = remember(day.tasks) { day.tasks.filter { it.showInCalendar } }

        if (isCompact) {
            val fallbackColor = MaterialTheme.colorScheme.secondaryContainer
            val allDotColors = visibleTasks.map { task ->
                resolveTaskColor(task, fallbackColor)
            }.take(6)
            
            if (allDotColors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(1.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    allDotColors.forEach { dotColor ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        } else if (visibleTasks.isNotEmpty() && linesBudget > 0) {
            Spacer(modifier = Modifier.height(1.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                val fallbackColor = MaterialTheme.colorScheme.secondaryContainer
                val totalTasks = visibleTasks.size
                visibleTasks.forEachIndexed { idx, task ->
                    if (linesBudget <= 0) return@forEachIndexed
                    val taskColor = resolveTaskColor(task, fallbackColor)

                    val rowYOffset = ((1f - verticalScale).coerceIn(0f, 0.5f) * -6).dp
                    val remainingTasks = totalTasks - idx
                    val minReservedForOthers = (remainingTasks - 1).coerceAtLeast(0)
                    val maxForThis = (linesBudget - minReservedForOthers).coerceAtLeast(1)
                    val allowedLinesForThisTask = minOf(2, maxForThis)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .scale(verticalScale.coerceIn(0.85f, 1f))
                    ) {
                        Box(
                            modifier = Modifier
                                .offset(y = rowYOffset)
                                .size(width = 3.dp, height = 7.dp)
                                .background(taskColor, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = task.title,
                            color = if (isDark) Color.White else Color.Black,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            maxLines = allowedLinesForThisTask,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.offset(y = rowYOffset)
                        )
                    }
                    linesBudget -= allowedLinesForThisTask
                }
            }
        }
    }
}

private fun resolveTaskColor(task: com.mss.thebigcalendar.data.model.Activity, fallback: Color): Color {
    return when {
        task.activityType == ActivityType.COMMEMORATIVE -> Color(0xFFFF9800)
        task.activityType == ActivityType.BIRTHDAY -> Color(0xFFE91E63)
        task.activityType == ActivityType.NOTE -> Color(0xFF9C27B0)
        // No Linux, não temos acesso fácil aos recursos R.string do Android aqui sem abstração.
        // Vamos usar os nomes das cores ou índices por enquanto.
        task.categoryColor == "Branco" || task.categoryColor == "White" -> Color.White
        task.categoryColor == "Azul" || task.categoryColor == "Blue" -> Color.Blue
        task.categoryColor == "Amarelo" || task.categoryColor == "Yellow" -> Color.Yellow
        task.categoryColor == "Vermelho" || task.categoryColor == "Red" -> Color.Red
        else -> fallback
    }
}
