package com.mss.thebigcalendar.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.data.model.AppIconMode
import androidx.compose.ui.res.stringResource
import com.mss.thebigcalendar.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconSelectionDialog(
    currentMode: AppIconMode,
    onModeSelected: (AppIconMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(id = R.string.settings_app_icon_dialog_title))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(AppIconMode.values()) { mode ->
                    val label = when (mode) {
                        AppIconMode.DYNAMIC -> stringResource(id = R.string.settings_app_icon_mode_dynamic)
                        AppIconMode.WHITE -> stringResource(id = R.string.settings_app_icon_mode_white)
                        AppIconMode.BLACK -> stringResource(id = R.string.settings_app_icon_mode_black)
                    }
                    val iconText = when (mode) {
                        AppIconMode.DYNAMIC -> "🔄"
                        AppIconMode.WHITE -> "⚪"
                        AppIconMode.BLACK -> "⚫"
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onModeSelected(mode)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (mode == currentMode) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = iconText,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            
                            if (mode == currentMode) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selecionado",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel_button))
            }
        }
    )
}
