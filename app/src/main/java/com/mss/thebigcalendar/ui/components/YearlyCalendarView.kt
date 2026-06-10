package com.mss.thebigcalendar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mss.thebigcalendar.R
import java.time.Month
import java.time.YearMonth

@Composable
fun YearlyCalendarView(
    modifier: Modifier = Modifier,
    year: Int,
    onMonthClicked: (YearMonth) -> Unit // Ação para quando um mês é clicado
) {
    val months = Month.entries // Obtém todos os 12 meses do enum java.time.Month
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    
    // Ajusta o número de colunas conforme a largura da tela
    val columns = when {
        screenWidth < 600.dp -> 2 // Telas estreitas (celulares em retrato)
        else -> 3 // Telas largas (tablets ou landscape)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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