package com.mss.thebigcalendar.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TasksForSelectedDaySection(
    modifier: Modifier = Modifier,
    tasks: List<Activity>,
    selectedDate: LocalDate,
    displayedYearMonth: java.time.YearMonth,
    activityIdWithDeleteVisible: String?,
    onTaskClick: (Activity) -> Unit,
    onTaskLongClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onCompleteClick: (String) -> Unit,
    onAddTaskClick: () -> Unit,
    onCommemorativeClick: (Activity) -> Unit,
    onUpdateTaskDescription: (Activity, String) -> Unit = { _, _ -> }
) {
    val dateFormat = stringResource(id = R.string.date_format_day_month)
    val dateFormatter = remember(dateFormat) { DateTimeFormatter.ofPattern(dateFormat, Locale.getDefault()) }

    val visibleTasks = tasks
    val isDifferentMonth = selectedDate.month != displayedYearMonth.month ||
            selectedDate.year != displayedYearMonth.year

    val headerText = if (isDifferentMonth) {
        val monthName = displayedYearMonth.month
            .getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        stringResource(id = R.string.appointments_for_month, monthName)
    } else {
        stringResource(id = R.string.appointments_for, selectedDate.format(dateFormatter))
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = headerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onAddTaskClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.add_task_or_event),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (isDifferentMonth) {
            Text(
                text = stringResource(id = R.string.select_a_day),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else if (visibleTasks.isEmpty()) {
            Text(
                text = stringResource(id = R.string.no_appointments),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleTasks.forEach { task ->
                    TaskItem(
                        task = task,
                        deleteButtonVisible = activityIdWithDeleteVisible == task.id,
                        onTaskClick = onTaskClick,
                        onTaskLongClick = onTaskLongClick,
                        onDeleteClick = onDeleteClick,
                        onCompleteClick = onCompleteClick,
                        onCommemorativeClick = onCommemorativeClick,
                        onUpdateTaskDescription = onUpdateTaskDescription
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskItem(
    task: Activity,
    deleteButtonVisible: Boolean,
    onTaskClick: (Activity) -> Unit,
    onTaskLongClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onCompleteClick: (String) -> Unit,
    onCommemorativeClick: (Activity) -> Unit,
    onUpdateTaskDescription: (Activity, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var showConfirmCompleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deleteButtonVisible) {
        if (deleteButtonVisible) {
            delay(200)
            bringIntoViewRequester.bringIntoView()
        }
    }

    val fallbackColor = MaterialTheme.colorScheme.secondary
    val taskColor = remember(task.categoryColor) {
        when (task.categoryColor) {
            "1" -> Color.White
            "2" -> Color.Blue
            "3" -> Color.Yellow
            "4" -> Color.Red
            else -> {
                try {
                    Color(android.graphics.Color.parseColor(task.categoryColor))
                } catch (e: IllegalArgumentException) {
                    fallbackColor
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .animateContentSize()
            .combinedClickable(
                onClick = {
                    if (task.activityType == ActivityType.COMMEMORATIVE) {
                        onCommemorativeClick(task)
                    } else {
                        onTaskLongClick(task.id)
                    }
                },
                onLongClick = if (task.activityType == ActivityType.COMMEMORATIVE) null else { { onTaskClick(task) } }
            )
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val boxModifier = if (taskColor == Color.White) {
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(taskColor, shape = RoundedCornerShape(2.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            } else {
                Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(taskColor, shape = RoundedCornerShape(2.dp))
            }

            Box(modifier = boxModifier)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.isCompleted) {
                        Text(
                            text = "✅ ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (task.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (task.startTime != null) {
                    Text(
                        text = stringResource(id = R.string.at_time, task.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!task.description.isNullOrBlank()) {
                Icon(
                    imageVector = if (deleteButtonVisible) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(id = if (deleteButtonVisible) R.string.collapse else R.string.expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        if (deleteButtonVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, bottom = 12.dp)
            ) {
                if (!task.description.isNullOrBlank()) {
                    InteractiveDescription(
                        description = task.description,
                        onDescriptionChanged = { newDesc ->
                            onUpdateTaskDescription(task, newDesc)
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                
                if (!task.isCompleted) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .height(40.dp)
                                .width(80.dp)
                                .clickable { 
                                    if (hasUncheckedChecklistItems(task.description)) {
                                        showConfirmCompleteDialog = true
                                    } else {
                                        onCompleteClick(task.id)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "OK",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .height(40.dp)
                                .width(80.dp)
                                .clickable { onDeleteClick(task.id) }
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "DEL",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onError,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmCompleteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCompleteDialog = false },
            title = { Text("Itens Pendentes") },
            text = { Text("Você ainda possui itens pendentes na lista de conclusão desta tarefa. Tem certeza de que deseja concluí-la?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmCompleteDialog = false
                        onCompleteClick(task.id)
                    }
                ) {
                    Text("Sim, concluir")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmCompleteDialog = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun hasUncheckedChecklistItems(description: String?): Boolean {
    if (description.isNullOrBlank()) return false
    return description.split("\n").any { it.trimStart().startsWith("[ ]") }
}

@Composable
fun InteractiveDescription(
    description: String,
    onDescriptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(description) { description.split("\n") }
    val checklistRegex = """^\[([ xX]?)]\s*(.*)$""".toRegex()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            val match = checklistRegex.matchEntire(line)
            if (match != null) {
                val isChecked = match.groupValues[1].lowercase() == "x"
                val content = match.groupValues[2]

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newPrefix = if (isChecked) "[ ]" else "[x]"
                            val newLines = lines.toMutableList()
                            newLines[index] = "$newPrefix $content"
                            onDescriptionChanged(newLines.joinToString("\n"))
                        }
                        .padding(vertical = 2.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isChecked,
                        onCheckedChange = null, // Click is handled by the Row for larger touch target
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (isChecked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
