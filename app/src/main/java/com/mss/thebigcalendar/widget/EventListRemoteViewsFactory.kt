package com.mss.thebigcalendar.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.repository.ActivityRepository
import com.mss.thebigcalendar.service.RecurrenceService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import android.util.Log

class EventListRemoteViewsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private var activities: List<Activity> = emptyList()
    private lateinit var activityRepository: ActivityRepository
    
    companion object {
        private const val TAG = "EventListRemoteViewsFactory"
    }

    override fun onCreate() {
        activityRepository = ActivityRepository(context)
    }

    private var todayTasksCount: Int = 0

    override fun onDataSetChanged() {
        // This is called by the app widget manager whenever the data set has changed.
        // You can use this to update your data.
        Log.d(TAG, "🔄 onDataSetChanged() chamado - recarregando dados do widget")
        runBlocking {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val currentTime = LocalTime.now()
            val isNightTime = isNightTime(currentTime)

            val allActivities = activityRepository.activities.firstOrNull() ?: emptyList()
            
            val todayTasks = getActivitiesForDate(today, allActivities)
            val tomorrowTasks = if (isNightTime) {
                getActivitiesForDate(tomorrow, allActivities)
            } else {
                emptyList()
            }

            todayTasksCount = todayTasks.size
            activities = todayTasks + tomorrowTasks
            
            Log.d(TAG, "📋 Widget atualizado - ${activities.size} atividades encontradas")
            Log.d(TAG, "📋 Atividades de hoje: ${todayTasks.size}")
            Log.d(TAG, "📋 Atividades de amanhã: ${tomorrowTasks.size}")
        }
    }

    private fun getActivitiesForDate(
        targetDate: LocalDate,
        allActivities: List<Activity>
    ): List<Activity> {
        val tasks = mutableListOf<Activity>()

        allActivities.forEach { activity ->
            try {
                // Excluir notas e itens JSON importados de feriados
                if (activity.activityType == ActivityType.NOTE || activity.location?.startsWith("JSON_IMPORTED_") == true) {
                    return@forEach
                }

                val activityDate = LocalDate.parse(activity.date)

                // Para aniversários, verificar se coincide o dia e mês
                if (activity.activityType == ActivityType.BIRTHDAY) {
                    if (activityDate.month == targetDate.month && activityDate.dayOfMonth == targetDate.dayOfMonth) {
                        tasks.add(activity)
                    }
                    return@forEach
                }

                val isRecurring = !activity.recurrenceRule.isNullOrEmpty() && activity.recurrenceRule != "NONE"

                // Verificar se a atividade base coincide com a data alvo
                if (activityDate.isEqual(targetDate)) {
                    val isExcluded = if (isRecurring) {
                        if (activity.recurrenceRule?.startsWith("FREQ=HOURLY") == true || activity.recurrenceRule == "HOURLY") {
                            val timeString = activity.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                            activity.excludedInstances.contains("${activity.id}_${targetDate}_${timeString}")
                        } else {
                            activity.excludedDates.contains(targetDate.toString())
                        }
                    } else {
                        false
                    }

                    if (!isExcluded) {
                        tasks.add(activity)
                    }
                }

                // Se a atividade for recorrente, calcular instâncias para esta data alvo
                if (isRecurring) {
                    val recurringInstances = calculateRecurringInstancesForDate(activity, targetDate)
                    tasks.addAll(recurringInstances)
                }
            } catch (e: Exception) {
                // Ignorar atividades com datas inválidas
            }
        }

        tasks.sortWith(
            compareBy<Activity> { it.startTime ?: LocalTime.MAX }
                .thenByDescending { it.categoryColor.toIntOrNull() ?: 0 }
        )

        return tasks
    }

    private fun calculateRecurringInstancesForDate(baseActivity: Activity, targetDate: LocalDate): List<Activity> {
        val instances = mutableListOf<Activity>()
        
        try {
            val baseDate = LocalDate.parse(baseActivity.date)
            val targetDateString = targetDate.toString()
            
            // Verificar se esta data específica foi excluída
            if (baseActivity.excludedDates.contains(targetDateString)) {
                return instances
            }
            
            // Se a data base é posterior à data alvo, não há instâncias
            if (baseDate.isAfter(targetDate)) {
                return instances
            }
            
            // Para atividades não-HOURLY, a atividade base já é adicionada quando baseDate == targetDate
            if (baseDate.isEqual(targetDate) && 
                !(baseActivity.recurrenceRule == "HOURLY" || baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true)) {
                return instances
            }
            
            when {
                baseActivity.recurrenceRule == "HOURLY" || baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true -> {
                    if (baseActivity.recurrenceRule?.startsWith("FREQ=HOURLY") == true) {
                        val recurrenceService = RecurrenceService()
                        val startOfMonth = targetDate.withDayOfMonth(1)
                        val endOfMonth = targetDate.with(TemporalAdjusters.lastDayOfMonth())
                        
                        val recurringInstances = recurrenceService.generateRecurringInstances(
                            baseActivity,
                            startOfMonth,
                            endOfMonth
                        )
                        
                        val instancesForTargetDate = recurringInstances.filter { 
                            try {
                                LocalDate.parse(it.date).isEqual(targetDate)
                            } catch (_: Exception) {
                                false
                            }
                        }
                        
                        instancesForTargetDate.forEach { instance ->
                            val timeString = instance.startTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "00:00"
                            val instanceId = "${baseActivity.id}_${targetDate}_${timeString}"
                            val isExcluded = baseActivity.excludedInstances.contains(instanceId)
                            
                            if (!isExcluded) {
                                instances.add(instance)
                            }
                        }
                    } else {
                        val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                        if (daysDiff > 0) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                baseActivity.recurrenceRule == "DAILY" -> {
                    val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                    if (daysDiff > 0) {
                        val instance = baseActivity.copy(
                            id = "${baseActivity.id}_${targetDate}",
                            date = targetDate.toString()
                        )
                        instances.add(instance)
                    }
                }
                baseActivity.recurrenceRule == "WEEKLY" -> {
                    val daysDiff = ChronoUnit.DAYS.between(baseDate, targetDate)
                    if (daysDiff > 0 && daysDiff % 7 == 0L) {
                        val instance = baseActivity.copy(
                            id = "${baseActivity.id}_${targetDate}",
                            date = targetDate.toString()
                        )
                        instances.add(instance)
                    }
                }
                baseActivity.recurrenceRule == "MONTHLY" -> {
                    val monthsDiff = ChronoUnit.MONTHS.between(baseDate, targetDate)
                    if (monthsDiff > 0) {
                        val targetDay = minOf(baseDate.dayOfMonth, targetDate.lengthOfMonth())
                        val adjustedDate = targetDate.withDayOfMonth(targetDay)
                        
                        if (adjustedDate.isEqual(targetDate)) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                baseActivity.recurrenceRule == "YEARLY" -> {
                    val yearsDiff = ChronoUnit.YEARS.between(baseDate, targetDate)
                    if (yearsDiff > 0) {
                        val targetDay = minOf(baseDate.dayOfMonth, targetDate.lengthOfMonth())
                        val adjustedDate = targetDate.withDayOfMonth(targetDay)
                        if (baseDate.month == targetDate.month && adjustedDate.isEqual(targetDate)) {
                            val instance = baseActivity.copy(
                                id = "${baseActivity.id}_${targetDate}",
                                date = targetDate.toString()
                            )
                            instances.add(instance)
                        }
                    }
                }
                else -> {
                    if (baseActivity.recurrenceRule?.startsWith("FREQ=") == true || baseActivity.recurrenceRule == "CUSTOM") {
                        val recurrenceService = RecurrenceService()
                        val startOfMonth = targetDate.withDayOfMonth(1)
                        val endOfMonth = targetDate.with(TemporalAdjusters.lastDayOfMonth())
                        
                        val recurringInstances = recurrenceService.generateRecurringInstances(
                            baseActivity,
                            startOfMonth,
                            endOfMonth
                        )
                        
                        val matchingInstances = recurringInstances.filter { instance ->
                            try {
                                LocalDate.parse(instance.date).isEqual(targetDate)
                            } catch (_: Exception) {
                                false
                            }
                        }
                        
                        instances.addAll(matchingInstances)
                    }
                }
            }
        } catch (_: Exception) {
        }
        
        return instances
    }

    override fun onDestroy() {
        activities = emptyList()
    }

    override fun getCount(): Int {
        return activities.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= activities.size) {
            return RemoteViews(context.packageName, R.layout.event_list_widget_item)
        }

        val activity = activities[position]
        val views = RemoteViews(context.packageName, R.layout.event_list_widget_item)

        val isTomorrow = position >= todayTasksCount
        val tomorrowTag = if (isTomorrow) {
            val headerText = context.getString(R.string.widget_tomorrow_header).trimEnd(':', ' ', '：')
            "[$headerText] "
        } else {
            ""
        }

        val typePrefix = if (activity.activityType == ActivityType.BIRTHDAY) {
            "🎂 " // Birthday icon
        } else if (activity.startTime != null) {
            "${activity.startTime!!.format(DateTimeFormatter.ofPattern("HH:mm"))} "
        } else {
            ""
        }

        views.setTextViewText(R.id.event_item_title, "$tomorrowTag$typePrefix${activity.title}")

        val descriptionText = activity.description.takeIf { !it.isNullOrBlank() } ?: ""
        views.setTextViewText(R.id.event_item_time, descriptionText)

        // Set fill-in intent for click handling (optional, but good practice)
        val fillInIntent = Intent()
        // You can put extras here if you want to pass data to the activity when an item is clicked
        views.setOnClickFillInIntent(R.id.event_item_title, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null // You can return a custom loading view here if needed
    }

    override fun getViewTypeCount(): Int {
        return 1 // All items are the same type
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    /**
     * Checks if it's night time (sunset to midnight)
     * Considers approximate sunset times based on the season (Brazil)
     */
    private fun isNightTime(currentTime: LocalTime): Boolean {
        val currentDate = LocalDate.now()
        val month = currentDate.monthValue

        // Approximate sunset times by season (Brazil)
        val sunsetTime = when (month) {
            in 12..2 -> LocalTime.of(19, 30) // Summer (Dec-Feb): ~19:30
            in 3..5 -> LocalTime.of(18, 30)  // Autumn (Mar-May): ~18:30
            in 6..8 -> LocalTime.of(17, 30)  // Winter (Jun-Aug): ~17:30
            in 9..11 -> LocalTime.of(18, 0)  // Spring (Sep-Nov): ~18:00
            else -> LocalTime.of(18, 0)      // Default
        }

        val midnight = LocalTime.of(0, 0) // 00:00 (midnight)

        return currentTime.isAfter(sunsetTime) || currentTime.isBefore(midnight)
    }
}