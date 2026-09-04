package com.mss.thebigcalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun YearlyCalendarView(
    modifier: Modifier = Modifier,
    year: Int,
    onMonthClicked: (YearMonth) -> Unit
) {
    val months = Month.values()

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), // 4 colunas se adapta perfeitamente a telas de desktop
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(months.size) { index ->
                val month = months[index]
                MiniMonthView(
                    yearMonth = YearMonth.of(year, month),
                    onMonthClicked = onMonthClicked
                )
            }
        }
    }
}

@Composable
fun MiniMonthView(
    modifier: Modifier = Modifier,
    yearMonth: YearMonth,
    onMonthClicked: (YearMonth) -> Unit,
    highlightWeekends: Boolean = true
) {
    val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }

    // Gera os dias para este mini calendário (Domingo como início da semana visual)
    val firstDayOfMonth = yearMonth.atDay(1)
    val daysFromPrevMonthOffset = (firstDayOfMonth.dayOfWeek.value % 7)
    val gridStartDate = firstDayOfMonth.minusDays(daysFromPrevMonthOffset.toLong())

    val daysInGrid = remember(yearMonth) {
        List(42) { i ->
            gridStartDate.plusDays(i.toLong())
        }
    }
    
    val weekDayAbbreviations = listOf("D", "S", "T", "Q", "Q", "S", "S")

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable { onMonthClicked(yearMonth) }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = monthName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Dias da semana (D S T Q Q S S)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekDayAbbreviations.forEach { dayAbbr ->
                Text(
                    text = dayAbbr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Grid dos dias
        Column(modifier = Modifier.padding(top = 4.dp)) {
            val chunkSize = 7
            daysInGrid.chunked(chunkSize).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    week.forEach { date ->
                        val isCurrentMonth = date.month == yearMonth.month
                        val isSunday = date.dayOfWeek == DayOfWeek.SUNDAY
                        val isSaturday = date.dayOfWeek == DayOfWeek.SATURDAY

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                color = when {
                                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    highlightWeekends && (isSunday || isSaturday) -> Color(0xFFEF5350).copy(alpha = 0.8f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
