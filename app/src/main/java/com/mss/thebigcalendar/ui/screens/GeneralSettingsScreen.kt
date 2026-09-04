package com.mss.thebigcalendar.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Theme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    currentTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    onBackClick: () -> Unit,
    onImportJsonClick: () -> Unit = {},
    sidebarFilterVisibility: com.mss.thebigcalendar.data.model.SidebarFilterVisibility = com.mss.thebigcalendar.data.model.SidebarFilterVisibility(),
    onToggleSidebarFilterVisibility: (String) -> Unit = {},
    onOpenCalendarVisualization: () -> Unit = {},
    onOpenSyncSettings: () -> Unit = {},
    onOpenBackupSettings: () -> Unit = {},
    onOpenGeminiSettings: () -> Unit = {},
    unfixHeadersOnScroll: Boolean = false
) {
    Log.d("GeneralSettingsScreen", "📱 GeneralSettingsScreen iniciada")
    
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = if (unfixHeadersOnScroll) {
        TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    } else {
        null
    }

    val transitionFraction = scrollBehavior?.state?.let { state ->
        maxOf(state.collapsedFraction, state.overlappedFraction)
    } ?: 0f

    val appBarContainerColor = if (MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black) {
        androidx.compose.ui.graphics.Color.Black
    } else {
        lerp(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.surface,
            transitionFraction
        )
    }

    val appBarContentColor = if (MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black) {
        MaterialTheme.colorScheme.onSurface
    } else {
        lerp(
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.onSurface,
            transitionFraction
        )
    }

    Scaffold(
        modifier = if (scrollBehavior != null) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(id = R.string.general)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                    titleContentColor = appBarContentColor,
                    navigationIconContentColor = appBarContentColor,
                    actionIconContentColor = appBarContentColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues).padding(horizontal = 16.dp)
        ) {
            // Calendar Visualization entry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onOpenCalendarVisualization() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.calendar_visualization_settings),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(id = R.string.calendar_visualization_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Synchronization entry (placed directly below visual settings row)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onOpenSyncSettings() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.synchronization),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(id = R.string.synchronization_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Backup settings entry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onOpenBackupSettings() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.backup),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(id = R.string.backup_settings_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Gemini Assistant entry
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onOpenGeminiSettings() }
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.gemini_assistant_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(id = R.string.gemini_assistant_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Import Events entry (replacing the JSON button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onImportJsonClick() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.general_import_json_file),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(id = R.string.general_import_json_file_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lista de filtros que podem ser adicionados ao menu
            val hiddenFilters = buildList {
                add("showHolidays" to stringResource(id = R.string.national_holidays))
                add("showEvents" to stringResource(id = R.string.events))
                add("showTasks" to stringResource(id = R.string.tasks))
                add("showBirthdays" to stringResource(id = R.string.birthday))
                add("showNotes" to stringResource(id = R.string.note))
                add("showCommemorative" to stringResource(id = R.string.commemorative_dates))
                add("showCompletedActivities" to stringResource(id = R.string.completed_tasks_filter))
                add("showMoonPhases" to stringResource(id = R.string.moon_phases_filter))
            }
            
            // Verificar se há filtros ocultos
            val hasHiddenFilters = hiddenFilters.any { (key, _) ->
                when (key) {
                    "showHolidays" -> !sidebarFilterVisibility.showHolidays
                    "showEvents" -> !sidebarFilterVisibility.showEvents
                    "showTasks" -> !sidebarFilterVisibility.showTasks
                    "showBirthdays" -> !sidebarFilterVisibility.showBirthdays
                    "showNotes" -> !sidebarFilterVisibility.showNotes
                    "showCommemorative" -> !sidebarFilterVisibility.showCommemorative
                    "showCompletedActivities" -> !sidebarFilterVisibility.showCompletedTasks
                    "showMoonPhases" -> !sidebarFilterVisibility.showMoonPhases
                    else -> false
                }
            }
            
            // Seção "Mostrar no menu" - só aparece quando há filtros ocultos
            if (hasHiddenFilters) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(id = R.string.show_in_menu),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                hiddenFilters.forEach { (key, label) ->
                    val isHidden = when (key) {
                        "showHolidays" -> !sidebarFilterVisibility.showHolidays
                        "showEvents" -> !sidebarFilterVisibility.showEvents
                        "showTasks" -> !sidebarFilterVisibility.showTasks
                        "showBirthdays" -> !sidebarFilterVisibility.showBirthdays
                        "showNotes" -> !sidebarFilterVisibility.showNotes
                        "showCommemorative" -> !sidebarFilterVisibility.showCommemorative
                        "showCompletedActivities" -> !sidebarFilterVisibility.showCompletedTasks
                        "showMoonPhases" -> !sidebarFilterVisibility.showMoonPhases
                        else -> false
                    }
                    
                    if (isHidden) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSidebarFilterVisibility(key) }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Adicionar ao menu",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}