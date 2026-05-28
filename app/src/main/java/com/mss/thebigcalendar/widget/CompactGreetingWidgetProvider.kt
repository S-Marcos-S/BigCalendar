package com.mss.thebigcalendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import com.mss.thebigcalendar.MainActivity
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.repository.ActivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

class CompactGreetingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ACTION_REFRESH_COMPACT_GREETING_WIDGET) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                context?.let {
                    val appWidgetManager = AppWidgetManager.getInstance(it)
                    updateAppWidget(it, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_COMPACT_GREETING_WIDGET = "com.mss.thebigcalendar.ACTION_REFRESH_COMPACT_GREETING_WIDGET"
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.compact_greeting_widget)

        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val transparency = prefs.getFloat("transparency_$appWidgetId", 0.0f)
        
        // Usar cor de fundo fixa para evitar problemas de tema no widget
        val baseBackgroundColor = Color.parseColor("#202124")

        // Calcular alpha (0.0 transparency = 255 alpha/opaco, 1.0 transparency = 0 alpha/transparente)
        val alpha = ((1.0f - transparency) * 255).toInt().coerceIn(0, 255)
        val colorWithAlpha = Color.argb(alpha, Color.red(baseBackgroundColor), Color.green(baseBackgroundColor), Color.blue(baseBackgroundColor))
        
        // Aplicar a cor de fundo ao widget_main_content
        views.setInt(R.id.widget_main_content, "setBackgroundColor", colorWithAlpha)

        // Atualiza a saudação baseada no horário
        val greeting = getGreetingBasedOnTime(context)
        views.setTextViewText(R.id.compact_widget_greeting, greeting)

        // Atualiza a data
        val locale = Locale.getDefault()
        val dayOfWeekFormat = SimpleDateFormat("EEE", locale)
        val dayMonthFormat = SimpleDateFormat("dd/MM", locale)
        val date = Date()

        val dayOfWeekShort = dayOfWeekFormat.format(date).let { 
            if (it.isNotEmpty()) it.first().uppercase() + it.substring(1) else ""
        }
        val dayMonth = dayMonthFormat.format(date)

        // Colocar o dia da semana primeiro e depois a data
        val dayWithDate = "$dayOfWeekShort - $dayMonth"
        views.setTextViewText(R.id.compact_widget_day_of_week, dayWithDate)

        // Resetar texto de tarefas
        views.setTextViewText(R.id.compact_widget_tasks, context.getString(R.string.widget_loading_tasks_text))

        // Configurar clique para abrir o app
        val appIntent = Intent(context, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.compact_widget_greeting, appPendingIntent)

        // Configurar botão de refresh
        val refreshIntent = Intent(context, CompactGreetingWidgetProvider::class.java).apply {
            action = ACTION_REFRESH_COMPACT_GREETING_WIDGET
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.compact_widget_refresh_button, refreshPendingIntent)

        // 1. Atualização IMEDIATA (fundo e data)
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // 2. Carregar tarefas em background
        loadTodayTasks(context, views, appWidgetManager, appWidgetId)
    }

    private fun loadTodayTasks(
        context: Context,
        views: RemoteViews,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = ActivityRepository(context)
                val today = LocalDate.now()
                
                // Timeout para evitar travar o widget
                val activities = withTimeoutOrNull(5000) {
                    repository.activities.first()
                } ?: emptyList()
                
                val todayTasks = activities.filter { activity ->
                    try {
                        val activityDate = LocalDate.parse(activity.date)
                        
                        // Verificar se é hoje (incluindo aniversários)
                        if (activity.activityType == ActivityType.BIRTHDAY) {
                            activityDate.month == today.month && activityDate.dayOfMonth == today.dayOfMonth
                        } else {
                            activityDate == today
                        }
                    } catch (e: Exception) {
                        false
                    }
                }.sortedBy { it.startTime ?: LocalTime.MAX }

                val tasksText = if (todayTasks.isEmpty()) {
                    context.getString(R.string.widget_no_tasks_today)
                } else {
                    todayTasks.take(3).joinToString("\n") { task ->
                        val timePrefix = if (task.startTime != null) {
                            "${task.startTime!!.format(DateTimeFormatter.ofPattern("HH:mm"))} "
                        } else ""
                        "$timePrefix${task.title}"
                    } + if (todayTasks.size > 3) "\n..." else ""
                }

                views.setTextViewText(R.id.compact_widget_tasks, tasksText)
                
                // Segunda atualização com tarefas
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                Log.e("CompactGreetingWidget", "Erro ao carregar tarefas", e)
                views.setTextViewText(R.id.compact_widget_tasks, context.getString(R.string.widget_no_tasks_today))
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }

    /**
     * Obtém a saudação baseada no horário atual
     */
    private fun getGreetingBasedOnTime(context: Context): String {
        val currentTime = LocalTime.now()
        val hour = currentTime.hour
        
        return when (hour) {
            in 5..11 -> context.getString(R.string.widget_greeting_morning)
            in 12..17 -> context.getString(R.string.widget_greeting_afternoon)
            in 18..23 -> context.getString(R.string.widget_greeting_evening)
            else -> context.getString(R.string.widget_greeting_dawn) // 0-4
        }
    }

}
