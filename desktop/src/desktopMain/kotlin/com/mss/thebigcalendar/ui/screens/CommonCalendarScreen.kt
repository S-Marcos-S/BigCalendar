package com.mss.thebigcalendar.ui.screens

import DesktopCalendarViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.HolidayType
import com.mss.thebigcalendar.data.model.Theme
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import java.awt.FileDialog
import java.awt.Frame

// Função utilitária para converter cores hexadecimais em Color do Compose sem depender de APIs do Android
fun parseHexColor(colorStr: String): Color {
    return when (colorStr) {
        "1" -> Color.White
        "2" -> Color.Blue
        "3" -> Color.Yellow
        "4" -> Color.Red
        else -> {
            try {
                val hex = colorStr.removePrefix("#")
                val argb = when (hex.length) {
                    6 -> 0xFF000000.toInt() or hex.toInt(16)
                    8 -> hex.toLong(16).toInt()
                    else -> 0xFFEF5350.toInt()
                }
                Color(argb)
            } catch (e: Exception) {
                Color.White
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CommonCalendarScreen(viewModel: DesktopCalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editDate by remember { mutableStateOf("") }
    var editStartTime by remember { mutableStateOf("") }
    var editEndTime by remember { mutableStateOf("") }
    var editIsAllDay by remember { mutableStateOf(true) }
    var editColor by remember { mutableStateOf("1") }
    var editType by remember { mutableStateOf(ActivityType.EVENT) }

    var editingWelcomeName by remember { mutableStateOf(false) }
    var tempWelcomeName by remember { mutableStateOf(uiState.welcomeName) }

    val selectedDate = uiState.selectedDate
    val displayedMonth = uiState.displayedYearMonth
    val activities = uiState.activities
    val filters = uiState.filterOptions

    val categoryColors = listOf("1", "2", "3", "4")

    // Lógica de filtragem de atividades
    val filteredActivities = remember(activities, filters, uiState.searchQuery) {
        activities.filter { act ->
            val matchesType = when (act.activityType) {
                ActivityType.EVENT -> filters.showEvents
                ActivityType.TASK -> filters.showTasks
                ActivityType.NOTE -> filters.showNotes
                ActivityType.BIRTHDAY -> filters.showBirthdays
            }
            val matchesQuery = if (uiState.searchQuery.isNotBlank()) {
                act.title.contains(uiState.searchQuery, ignoreCase = true) ||
                (act.description?.contains(uiState.searchQuery, ignoreCase = true) ?: false)
            } else true
            matchesType && matchesQuery
        }
    }

    // Obter todas as datas especiais do dia selecionado
    val holidaysForSelectedDate = remember(selectedDate, filters) {
        val list = mutableListOf<Holiday>()
        
        val monthStr = if (selectedDate.monthValue < 10) "0${selectedDate.monthValue}" else "${selectedDate.monthValue}"
        val dayStr = if (selectedDate.dayOfMonth < 10) "0${selectedDate.dayOfMonth}" else "${selectedDate.dayOfMonth}"
        val dateStringMMDD = "$monthStr-$dayStr"

        if (filters.showHolidays) {
            list.addAll(viewModel.nationalHolidays.filter { it.date == dateStringMMDD })
            list.addAll(viewModel.commemorativeDates.filter { it.date == dateStringMMDD })
        }
        if (filters.showSaintDays) {
            list.addAll(viewModel.saintDays.filter { it.date == dateStringMMDD })
        }
        if (filters.showProfessionalDays) {
            list.addAll(viewModel.professionalDays.filter { it.date == dateStringMMDD })
        }
        if (filters.showMilitaryHolidays) {
            list.addAll(viewModel.militaryHolidays.filter { it.date == dateStringMMDD })
        }
        list
    }

    // Estrutura Principal de Layout (Sidebar + Calendário + Detalhes)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(if (uiState.pureBlackTheme && uiState.theme == Theme.DARK) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // SIDEBAR ESQUERDA: Filtros, Busca e Configurações
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            // Boas Vindas Editável
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(8.dp))
                if (editingWelcomeName) {
                    OutlinedTextField(
                        value = tempWelcomeName,
                        onValueChange = { tempWelcomeName = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.setWelcomeName(tempWelcomeName)
                                editingWelcomeName = false
                            }) {
                                Icon(Icons.Default.Check, contentDescription = "Salvar")
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.weight(1f).clickable {
                            tempWelcomeName = uiState.welcomeName
                            editingWelcomeName = true
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Olá,", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                uiState.welcomeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Edit, contentDescription = "Editar nome", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Busca
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Buscar...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp)
            )

            // Botão de Sincronização Google Calendar
            Button(
                onClick = { viewModel.syncGoogleCalendar() },
                enabled = !uiState.isSyncing,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                if (uiState.isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizando...", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sincronizar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar Google", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Mensagem de Sincronização
            if (uiState.syncMessage != null) {
                val msg = uiState.syncMessage!!
                val isError = msg.contains("Erro", ignoreCase = true)
                val isWarning = msg.contains("Aviso", ignoreCase = true)
                val isSuccess = msg.contains("concluída", ignoreCase = true)

                val containerColor = when {
                    isError -> MaterialTheme.colorScheme.errorContainer
                    isWarning -> MaterialTheme.colorScheme.tertiaryContainer
                    isSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }

                val contentColor = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    isWarning -> MaterialTheme.colorScheme.onTertiaryContainer
                    isSuccess -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                val icon = when {
                    isError -> Icons.Default.Error
                    isWarning -> Icons.Default.Warning
                    isSuccess -> Icons.Default.CheckCircle
                    else -> Icons.Default.Info
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                        if (!uiState.isSyncing) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.clearSyncMessage() }
                            )
                        }
                    }
                }
            }

            // Botão de Restaurar Backup Local
            Button(
                onClick = {
                    try {
                        val fileDialog = FileDialog(null as Frame?, "Selecionar arquivo de backup", FileDialog.LOAD).apply {
                            file = "*.json"
                            isVisible = true
                        }
                        val directory = fileDialog.directory
                        val filename = fileDialog.file
                        if (directory != null && filename != null) {
                            val selectedFile = java.io.File(directory, filename)
                            viewModel.restoreBackup(selectedFile)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                enabled = !uiState.isSyncing,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = "Restaurar Backup Local",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar Local", style = MaterialTheme.typography.bodyMedium)
            }

            // Botão de Restaurar Backup da Nuvem
            Button(
                onClick = {
                    viewModel.fetchCloudBackups()
                },
                enabled = !uiState.isSyncing,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Restaurar Backup da Nuvem",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar da Nuvem", style = MaterialTheme.typography.bodyMedium)
            }

            Text(
                "Filtros de Visualização",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Lista de Filtros
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item { FilterRow("Eventos", filters.showEvents, "#EF5350", Icons.Default.Event) { viewModel.toggleFilter("events") } }
                item { FilterRow("Tarefas", filters.showTasks, "#66BB6A", Icons.Default.TaskAlt) { viewModel.toggleFilter("tasks") } }
                item { FilterRow("Notas", filters.showNotes, "#42A5F5", Icons.Default.Description) { viewModel.toggleFilter("notes") } }
                item { FilterRow("Aniversários", filters.showBirthdays, "#FFA726", Icons.Default.Cake) { viewModel.toggleFilter("birthdays") } }
                item { FilterRow("Feriados", filters.showHolidays, "#FFCDD2", Icons.Default.Star) { viewModel.toggleFilter("holidays") } }
                item { FilterRow("Santos do Dia", filters.showSaintDays, "#D1C4E9", Icons.Default.Church) { viewModel.toggleFilter("saintDays") } }
                item { FilterRow("Profissões", filters.showProfessionalDays, "#C8E6C9", Icons.Default.Work) { viewModel.toggleFilter("professionalDays") } }
                item { FilterRow("Feriados Militares", filters.showMilitaryHolidays, "#FFE082", Icons.Default.Shield) { viewModel.toggleFilter("militaryHolidays") } }
            }

            // Configurações de Tema no rodapé da Sidebar
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tema", style = MaterialTheme.typography.bodyMedium)
                Row {
                    IconButton(
                        onClick = { viewModel.setTheme(Theme.LIGHT) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = if (uiState.theme == Theme.LIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.LightMode, contentDescription = "Claro")
                    }
                    IconButton(
                        onClick = { viewModel.setTheme(Theme.DARK) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = if (uiState.theme == Theme.DARK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.DarkMode, contentDescription = "Escuro")
                    }
                }
            }
            
            if (uiState.theme == Theme.DARK) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preto Puro", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = uiState.pureBlackTheme,
                        onCheckedChange = { viewModel.setPureBlackTheme(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }

        // SEÇÃO CENTRAL: Grade do Calendário Mensal
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
                .onPointerEvent(PointerEventType.Scroll) { pointerEvent ->
                    val deltaY = pointerEvent.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (deltaY > 0f) {
                        viewModel.updateDisplayedMonth(1)
                    } else if (deltaY < 0f) {
                        viewModel.updateDisplayedMonth(-1)
                    }
                }
        ) {
            // Cabeçalho do Calendário (Mês e Ano + Navegação)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        displayedMonth.year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.updateDisplayedMonth(-1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Mês Anterior", modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.setDisplayedMonth(YearMonth.now())
                            viewModel.selectDate(LocalDate.now())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) {
                        Text("Hoje")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.updateDisplayedMonth(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Próximo Mês", modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Dias da Semana
            val daysOfWeek = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Grade do Calendário
            val firstOfMonth = displayedMonth.atDay(1)
            val firstDayOfWeek = firstOfMonth.dayOfWeek.value // 1 (Mon) - 7 (Sun)
            val startOffset = if (firstDayOfWeek == 7) 0 else firstDayOfWeek
            val startDate = firstOfMonth.minusDays(startOffset.toLong())
            val calendarDates = List(42) { index -> startDate.plusDays(index.toLong()) }

            Column(modifier = Modifier.weight(1f)) {
                for (weekIndex in 0 until 6) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (dayIndex in 0 until 7) {
                            val date = calendarDates[weekIndex * 7 + dayIndex]
                            val isCurrentMonth = date.monthValue == displayedMonth.monthValue
                            val isSelected = date == selectedDate
                            val isToday = date == LocalDate.now()

                            val dayActivities = filteredActivities.filter { it.date == date.toString() }

                            // Calcular feriados com string interpolation para MM-dd
                            val dayHolidays = remember(date, filters) {
                                val list = mutableListOf<Holiday>()
                                val dMonthStr = if (date.monthValue < 10) "0${date.monthValue}" else "${date.monthValue}"
                                val dDayStr = if (date.dayOfMonth < 10) "0${date.dayOfMonth}" else "${date.dayOfMonth}"
                                val dMMDD = "$dMonthStr-$dDayStr"

                                if (filters.showHolidays) {
                                    list.addAll(viewModel.nationalHolidays.filter { it.date == dMMDD })
                                    list.addAll(viewModel.commemorativeDates.filter { it.date == dMMDD })
                                }
                                if (filters.showSaintDays) {
                                    list.addAll(viewModel.saintDays.filter { it.date == dMMDD })
                                }
                                if (filters.showProfessionalDays) {
                                    list.addAll(viewModel.professionalDays.filter { it.date == dMMDD })
                                }
                                if (filters.showMilitaryHolidays) {
                                    list.addAll(viewModel.militaryHolidays.filter { it.date == dMMDD })
                                }
                                list
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                                            isToday -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else if (isToday) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.selectDate(date) }
                                    .padding(6.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = date.dayOfMonth.toString(),
                                            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isToday -> MaterialTheme.colorScheme.tertiary
                                                dayHolidays.any { it.type == HolidayType.NATIONAL } -> Color(0xFFE53935)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        
                                        if (dayHolidays.isNotEmpty()) {
                                            val color = if (dayHolidays.any { it.type == HolidayType.NATIONAL }) Color(0xFFE53935) else Color(0xFFB39DDB)
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        dayActivities.take(3).forEach { act ->
                                            val pillBg = parseHexColor(act.categoryColor)
                                            val textColor = if (pillBg == Color.White || pillBg == Color.Yellow) Color.Black else Color.White
                                            val isWhiteBg = pillBg == Color.White
                                            Text(
                                                text = act.title,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = textColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textDecoration = if (act.isCompleted) TextDecoration.LineThrough else null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(pillBg.copy(alpha = 0.85f))
                                                    .then(
                                                        if (isWhiteBg) {
                                                            Modifier.border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                        } else Modifier
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                        if (dayActivities.size > 3) {
                                            Text(
                                                text = "+${dayActivities.size - 3}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 4.dp)
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

        // PAINEL DIREITO: Detalhes do Dia Selecionado
        Column(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Text(
                text = "${selectedDate.dayOfMonth} de ${selectedDate.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Feriados e Datas Especiais do Dia
            if (holidaysForSelectedDate.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Datas Comemorativas/Feriados", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        holidaysForSelectedDate.forEach { holiday ->
                            Text(
                                text = "• ${holiday.name}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (holiday.type == HolidayType.NATIONAL) Color(0xFFE53935) else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            // Evitar erro de Smart Cast carregando summary em variável local
                            val summary = holiday.summary
                            if (!summary.isNullOrBlank()) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            val selectedDayActivities = filteredActivities.filter { it.date == selectedDate.toString() }
            
            Text(
                "Compromissos",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedDayActivities.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhum compromisso hoje", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    items(selectedDayActivities) { activity ->
                        ActivityItemCard(
                            activity = activity,
                            onToggleCompletion = { viewModel.toggleActivityCompletion(activity) },
                            onEdit = {
                                viewModel.setActivityToEdit(activity)
                                editTitle = activity.title
                                editDesc = activity.description ?: ""
                                editDate = activity.date
                                editStartTime = activity.startTime?.toString()?.take(5) ?: ""
                                editEndTime = activity.endTime?.toString()?.take(5) ?: ""
                                editIsAllDay = activity.isAllDay
                                editColor = activity.categoryColor
                                editType = activity.activityType
                                showCreateDialog = true
                            },
                            onDelete = { viewModel.deleteActivity(activity.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    viewModel.setActivityToEdit(null)
                    editTitle = ""
                    editDesc = ""
                    editDate = selectedDate.toString()
                    editStartTime = "09:00"
                    editEndTime = "10:00"
                    editIsAllDay = true
                    editColor = "1"
                    editType = ActivityType.EVENT
                    showCreateDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Adicionar")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Novo Compromisso")
            }
        }
    }

    // DIÁLOGO: Criar ou Editar Compromisso
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(420.dp).padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (uiState.activityToEdit != null) "Editar Compromisso" else "Novo Compromisso",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = editDesc,
                        onValueChange = { editDesc = it },
                        label = { Text("Descrição / Anotação") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActivityTypeButton("Evento", editType == ActivityType.EVENT, Modifier.weight(1f)) { editType = ActivityType.EVENT }
                        ActivityTypeButton("Tarefa", editType == ActivityType.TASK, Modifier.weight(1f)) { editType = ActivityType.TASK }
                        ActivityTypeButton("Nota", editType == ActivityType.NOTE, Modifier.weight(1f)) { editType = ActivityType.NOTE }
                    }

                    OutlinedTextField(
                        value = editDate,
                        onValueChange = { editDate = it },
                        label = { Text("Data (AAAA-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = editIsAllDay, onCheckedChange = { editIsAllDay = it })
                        Text("Dia todo")
                    }

                    AnimatedVisibility(visible = !editIsAllDay) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = editStartTime,
                                onValueChange = { editStartTime = it },
                                label = { Text("Início (HH:MM)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editEndTime,
                                onValueChange = { editEndTime = it },
                                label = { Text("Fim (HH:MM)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Prioridade", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                    val priorityLabel = when (editColor) {
                        "1" -> "Normal"
                        "2" -> "Média"
                        "3" -> "Alta"
                        "4" -> "Crítica"
                        else -> "Personalizada"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categoryColors.forEach { colorStr ->
                            val colorValue = parseHexColor(colorStr)
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(colorValue)
                                    .border(
                                        width = if (editColor == colorStr) 3.dp else 1.dp,
                                        color = if (editColor == colorStr) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else if (colorValue == Color.White) {
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = CircleShape
                                    )
                                    .clickable { editColor = colorStr }
                            )
                        }
                    }
                    Text(
                        text = "Nível selecionado: $priorityLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCreateDialog = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val start = if (!editIsAllDay && editStartTime.isNotBlank()) {
                                    try { LocalTime.parse(editStartTime) } catch(e: Exception) { null }
                                } else null
                                val end = if (!editIsAllDay && editEndTime.isNotBlank()) {
                                    try { LocalTime.parse(editEndTime) } catch(e: Exception) { null }
                                } else null

                                viewModel.addOrUpdateActivity(
                                    title = editTitle.ifBlank { "Compromisso sem título" },
                                    description = editDesc.takeIf { it.isNotBlank() },
                                    date = editDate,
                                    startTime = start,
                                    endTime = end,
                                    isAllDay = editIsAllDay,
                                    categoryColor = editColor,
                                    type = editType
                                )
                                showCreateDialog = false
                            }
                        ) {
                            Text("Salvar")
                        }
                    }
                }
            }
        }
    }

    // DIÁLOGO: Restaurar Backup da Nuvem
    if (uiState.showCloudBackupDialog) {
        Dialog(onDismissRequest = { viewModel.dismissCloudBackupDialog() }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.width(450.dp).heightIn(max = 500.dp).padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Text(
                        text = "Restaurar Backup da Nuvem",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (uiState.isFetchingCloudBackups) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Buscando backups no Google Drive...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (uiState.cloudBackups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum backup encontrado na nuvem.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uiState.cloudBackups) { backup ->
                                val formattedDate = try {
                                    val dt = com.google.api.client.util.DateTime(backup.createdTime)
                                    val instant = java.time.Instant.ofEpochMilli(dt.value)
                                    val zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault())
                                    val formatterOutput = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd 'às' HH:mm")
                                    zonedDateTime.format(formatterOutput)
                                } catch (e: Exception) {
                                    backup.createdTime
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = backup.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Criado em: $formattedDate",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.restoreCloudBackup(backup.id)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Restaurar", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { viewModel.dismissCloudBackupDialog() }
                        ) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRow(
    label: String,
    checked: Boolean,
    colorHex: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(parseHexColor(colorHex))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() },
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
fun ActivityTypeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun ActivityItemCard(
    activity: Activity,
    onToggleCompletion: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryColor = parseHexColor(activity.categoryColor)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val boxModifier = if (categoryColor == Color.White) {
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .background(categoryColor, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
            } else {
                Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(categoryColor)
            }
            Box(
                modifier = boxModifier
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (activity.activityType == ActivityType.TASK) {
                        Checkbox(
                            checked = activity.isCompleted,
                            onCheckedChange = { onToggleCompletion() },
                            modifier = Modifier.size(24.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (activity.isCompleted) TextDecoration.LineThrough else null,
                        color = if (activity.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!activity.isAllDay && activity.startTime != null) {
                    val timeText = if (activity.endTime != null) "${activity.startTime} - ${activity.endTime}" else "${activity.startTime}"
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Evitar erro de Smart Cast salvando description em variável local
                val desc = activity.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
