package com.mss.thebigcalendar.ui.screens

import DesktopCalendarViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import com.mss.thebigcalendar.data.model.ViewMode
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.gestures.detectTapGestures

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
    val isYearly = uiState.viewMode == ViewMode.YEARLY

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
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .width(320.dp)
                .fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            ) {
                // 1. Cabeçalho de Boas-vindas com emoji
                val greetingEmoji = remember {
                    val hour = java.time.LocalTime.now().hour
                    when (hour) {
                        in 5..11 -> "🌅"
                        in 12..17 -> "☀️"
                        in 18..23 -> "🌙"
                        else -> "🌃"
                    }
                }
                val greetingText = remember {
                    val hour = java.time.LocalTime.now().hour
                    when (hour) {
                        in 5..11 -> "Bom dia"
                        in 12..17 -> "Boa tarde"
                        in 18..23 -> "Boa noite"
                        else -> "Boa madrugada"
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
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
                            Text(
                                text = "$greetingEmoji $greetingText, ${uiState.welcomeName}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Edit, contentDescription = "Editar nome", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                
                // 2. Frase do Dia
                uiState.quote?.let { quote ->
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = quote.frase,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = "— ${quote.autor}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 3. Busca
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar...", style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))

                if (uiState.searchQuery.isNotBlank()) {
                    // Resultados da Busca
                    Text(
                        text = "Resultados da Busca",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (uiState.searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum resultado encontrado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.searchResults.forEach { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectSearchResult(result) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${result.subtitle} • ${result.date}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                
                // 4. Seção de Visualização (Mensal/Anual/Anotações/Alarmes)
                Text(
                    text = "Visualização",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationDrawerItem(
                    label = { Text("Mensal") },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                    selected = uiState.viewMode == ViewMode.MONTHLY,
                    onClick = { viewModel.setViewMode(ViewMode.MONTHLY) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Anual") },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    selected = uiState.viewMode == ViewMode.YEARLY,
                    onClick = { viewModel.setViewMode(ViewMode.YEARLY) },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Agendamentos") },
                    icon = { Icon(Icons.Default.Note, contentDescription = null) },
                    selected = false,
                    onClick = { /* Em breve no Desktop */ },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Alarmes") },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = null) },
                    selected = false,
                    onClick = { /* Em breve no Desktop */ },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 5. Seção de Filtros (Mostrar no Calendário)
                Text(
                    text = "Mostrar no Calendário",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState.sidebarFilterVisibility.showEvents) {
                        FilterRow(
                            label = "Eventos",
                            checked = uiState.filterOptions.showEvents,
                            colorHex = "#EF5350",
                            icon = Icons.Default.Event,
                            onCheckedChange = { viewModel.onFilterChange("showEvents", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showEvents") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showTasks) {
                        FilterRow(
                            label = "Tarefas",
                            checked = uiState.filterOptions.showTasks,
                            colorHex = "#66BB6A",
                            icon = Icons.Default.TaskAlt,
                            onCheckedChange = { viewModel.onFilterChange("showTasks", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showTasks") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showNotes) {
                        FilterRow(
                            label = "Notas",
                            checked = uiState.filterOptions.showNotes,
                            colorHex = "#42A5F5",
                            icon = Icons.Default.Description,
                            onCheckedChange = { viewModel.onFilterChange("showNotes", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showNotes") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showBirthdays) {
                        FilterRow(
                            label = "Aniversários",
                            checked = uiState.filterOptions.showBirthdays,
                            colorHex = "#FFA726",
                            icon = Icons.Default.Cake,
                            onCheckedChange = { viewModel.onFilterChange("showBirthdays", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showBirthdays") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showHolidays) {
                        FilterRow(
                            label = "Feriados",
                            checked = uiState.filterOptions.showHolidays,
                            colorHex = "#FFCDD2",
                            icon = Icons.Default.Star,
                            onCheckedChange = { viewModel.onFilterChange("showHolidays", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showHolidays") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showSaintDays) {
                        FilterRow(
                            label = "Santos do Dia",
                            checked = uiState.filterOptions.showSaintDays,
                            colorHex = "#D1C4E9",
                            icon = Icons.Default.Church,
                            onCheckedChange = { viewModel.onFilterChange("showSaintDays", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showSaintDays") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showProfessionalDays) {
                        FilterRow(
                            label = "Profissões",
                            checked = uiState.filterOptions.showProfessionalDays,
                            colorHex = "#C8E6C9",
                            icon = Icons.Default.Work,
                            onCheckedChange = { viewModel.onFilterChange("showProfessionalDays", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showProfessionalDays") }
                        )
                    }
                    if (uiState.sidebarFilterVisibility.showMilitaryHolidays) {
                        FilterRow(
                            label = "Feriados Militares",
                            checked = uiState.filterOptions.showMilitaryHolidays,
                            colorHex = "#FFE082",
                            icon = Icons.Default.Shield,
                            onCheckedChange = { viewModel.onFilterChange("showMilitaryHolidays", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showMilitaryHolidays") }
                        )
                    }
                    
                    // Opção para mostrar tarefas finalizadas
                    if (uiState.sidebarFilterVisibility.showCompletedTasks) {
                        FilterRow(
                            label = "Tarefas finalizadas",
                            checked = uiState.showCompletedActivities,
                            colorHex = "#4CAF50",
                            icon = Icons.Default.CheckCircle,
                            onCheckedChange = { viewModel.onFilterChange("showCompletedActivities", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showCompletedActivities") }
                        )
                    }
                    
                    // Opção para mostrar fases da lua
                    if (uiState.sidebarFilterVisibility.showMoonPhases) {
                        FilterRow(
                            label = "Fases da lua",
                            checked = uiState.showMoonPhases,
                            colorHex = "#FFF59D",
                            icon = Icons.Default.Brightness4,
                            onCheckedChange = { viewModel.onFilterChange("showMoonPhases", it) },
                            onLongPress = { viewModel.toggleSidebarFilterVisibility("showMoonPhases") }
                        )
                    }
                }
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 6. Configurações
                Text(
                    text = "Configurações",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                NavigationDrawerItem(
                    label = { Text("Geral") },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { viewModel.setShowSettings(true) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Imprimir Calendário") },
                    icon = { Icon(Icons.Default.Print, contentDescription = null) },
                    selected = false,
                    onClick = { /* Ação de imprimir */ },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 7. Backup
                Text(
                    text = "Backup",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Botão de Sincronização Nuvem (Google Drive)
                NavigationDrawerItem(
                    label = {
                        Column {
                            if (uiState.isSyncing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sincronizando...")
                                }
                            } else {
                                Text("Sincronizar Nuvem")
                            }
                            val email = uiState.googleAccountEmail
                            if (email != null) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) },
                    selected = false,
                    onClick = { if (!uiState.isSyncing) viewModel.syncActivitiesWithCloud() },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .alpha(if (uiState.isSyncing) 0.38f else 1f)
                )
                
                if (uiState.googleAccountEmail != null) {
                    NavigationDrawerItem(
                        label = { Text("Trocar Conta Google") },
                        icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                        selected = false,
                        onClick = { if (!uiState.isSyncing) viewModel.disconnectGoogleAccount() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                
                // Botão de Restaurar Backup Local
                NavigationDrawerItem(
                    label = { Text("Restaurar Local") },
                    icon = { Icon(Icons.Outlined.Backup, contentDescription = null) },
                    selected = false,
                    onClick = {
                        if (!uiState.isSyncing) {
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
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .alpha(if (uiState.isSyncing) 0.38f else 1f)
                )
                
                // Botão de Restaurar Backup da Nuvem
                NavigationDrawerItem(
                    label = { Text("Restaurar da Nuvem") },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null) },
                    selected = false,
                    onClick = { if (!uiState.isSyncing) viewModel.fetchCloudBackups() },
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .alpha(if (uiState.isSyncing) 0.38f else 1f)
                )
                
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
                            .padding(horizontal = 8.dp, vertical = 8.dp),
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
                
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                // 8. Configurações de Tema no rodapé da Sidebar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
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
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
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

                }
            
            // CustomScrollbar
            CustomScrollbar(
                scrollState = scrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }

        // SEÇÃO CENTRAL: Grade do Calendário Mensal/Anual
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
                .onPointerEvent(PointerEventType.Scroll) { pointerEvent ->
                    val deltaY = pointerEvent.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (deltaY > 0f) {
                        if (isYearly) viewModel.updateDisplayedYear(1) else viewModel.updateDisplayedMonth(1)
                    } else if (deltaY < 0f) {
                        if (isYearly) viewModel.updateDisplayedYear(-1) else viewModel.updateDisplayedMonth(-1)
                    }
                }
        ) {
            // Cabeçalho do Calendário (Mês/Ano + Navegação)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (isYearly) {
                        Text(
                            text = displayedMonth.year.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Calendário Anual",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = displayedMonth.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR")).replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = displayedMonth.year.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (isYearly) viewModel.updateDisplayedYear(-1) else viewModel.updateDisplayedMonth(-1) }) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = if (isYearly) "Ano Anterior" else "Mês Anterior",
                            modifier = Modifier.size(32.dp)
                        )
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
                    IconButton(onClick = { if (isYearly) viewModel.updateDisplayedYear(1) else viewModel.updateDisplayedMonth(1) }) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = if (isYearly) "Próximo Ano" else "Próximo Mês",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            if (isYearly) {
                YearlyCalendarView(
                    year = displayedMonth.year,
                    onMonthClicked = { viewModel.onYearlyMonthClicked(it) }
                )
            } else {
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
                                            width = when {
                                                isSelected || isToday -> 2.dp
                                                isCurrentMonth -> 1.5.dp
                                                else -> 1.dp
                                            },
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                isToday -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
                                                isCurrentMonth -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(8.dp)
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
                                                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
    onCheckedChange: (Boolean) -> Unit,
    onLongPress: () -> Unit = {}
) {
    var showRemoveIcon by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                if (!showRemoveIcon) {
                    onCheckedChange(!checked)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        showRemoveIcon = true
                    },
                    onTap = {
                        if (showRemoveIcon) {
                            showRemoveIcon = false
                        } else {
                            onCheckedChange(!checked)
                        }
                    }
                )
            }
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
        if (showRemoveIcon) {
            IconButton(
                onClick = {
                    showRemoveIcon = false
                    onLongPress()
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remover do menu",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
private fun CustomScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var isHovered by remember { mutableFloatStateOf(0f) }
    
    // Obter cor da scrollbar baseada no tema Material Design
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
        alpha = (0.1f + isHovered * 0.2f).coerceIn(0.1f, 0.3f)
    )
    
    // Anima a opacidade da scrollbar
    LaunchedEffect(isHovered) {
        // Implementação simples de hover - pode ser expandida
    }
    
    Canvas(
        modifier = modifier
            .width(4.dp)
            .pointerInput(Unit) {
                // Detectar hover/interação se necessário
            }
    ) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        
        // Calcular dimensões da scrollbar
        val scrollbarThickness = with(density) { 4.dp.toPx() }
        val scrollbarPadding = with(density) { 2.dp.toPx() }
        
        // Calcular posição e tamanho do thumb
        val maxScrollValue = scrollState.maxValue.toFloat()
        val currentScrollValue = scrollState.value.toFloat()
        
        if (maxScrollValue > 0) {
            val thumbHeight = (canvasHeight * canvasHeight / (canvasHeight + maxScrollValue)).coerceAtLeast(scrollbarThickness * 2)
            val thumbTop = (currentScrollValue / maxScrollValue) * (canvasHeight - thumbHeight)
            
            // Desenhar o thumb da scrollbar
            drawRoundRect(
                color = scrollbarColor,
                topLeft = Offset(
                    x = (canvasWidth - scrollbarThickness) / 2,
                    y = thumbTop + scrollbarPadding
                ),
                size = Size(
                    width = scrollbarThickness,
                    height = thumbHeight - scrollbarPadding * 2
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(scrollbarThickness / 2)
            )
        }
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
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
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
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = if (isExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            if (activity.location?.startsWith("JSON_IMPORTED_") != true) {
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
}
