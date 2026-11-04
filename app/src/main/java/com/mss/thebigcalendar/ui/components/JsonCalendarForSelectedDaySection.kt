package com.mss.thebigcalendar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.mss.thebigcalendar.ui.theme.glass.GlassCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.JsonCalendar

@Composable
fun JsonCalendarForSelectedDaySection(
    modifier: Modifier = Modifier,
    jsonCalendar: JsonCalendar,
    activities: List<Activity>,
    onActivityClick: (Activity) -> Unit
) {
    if (activities.isEmpty()) return
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Título da seção com a cor do calendário
        Text(
            text = jsonCalendar.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = jsonCalendar.color,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // Lista de atividades
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            activities.forEach { activity ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadiusDp = 8.dp,
                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, jsonCalendar.color.copy(alpha = 0.3f)),
                    onClick = { onActivityClick(activity) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activity.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (activity.description?.isNotEmpty() == true) {
                                Text(
                                    text = activity.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            if (activity.startTime != null) {
                                Text(
                                    text = "${activity.startTime.hour.toString().padStart(2, '0')}:${activity.startTime.minute.toString().padStart(2, '0')}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = jsonCalendar.color,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
