package com.mss.thebigcalendar.widget

import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalTime

class WidgetUpdateService : Service() {
    
    private var updateJob: Job? = null
    private var lastUpdateDate: LocalDate? = null
    private var lastUpdateHour: Int? = null
    
    // Constantes de intervalo de atualização
    private val UPDATE_INTERVAL_NORMAL = 300000L // 5 minutos
    private val UPDATE_INTERVAL_FAST = 60000L    // 1 minuto (apenas quando necessário)
    private val UPDATE_INTERVAL_SLOW = 900000L   // 15 minutos (quando não há mudanças)
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🔋 WidgetUpdateService iniciado com otimizações de bateria")
        startSmartUpdates()
    }
    
    private fun startSmartUpdates() {
        updateJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val shouldUpdate = shouldUpdateWidgets()
                    if (shouldUpdate) {
                        updateAllWidgets()
                        updateLastUpdateTime()
                    }
                    
                    // Usar intervalo dinâmico baseado na necessidade
                    val updateInterval = getDynamicUpdateInterval()
                    Log.d(TAG, "🔋 Próxima atualização em ${updateInterval / 1000} segundos")
                    delay(updateInterval)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro na atualização do widget", e)
                    delay(UPDATE_INTERVAL_NORMAL) // Usar intervalo normal em caso de erro
                }
            }
        }
    }
    
    /**
     * Determina se os widgets precisam ser atualizados
     */
    private fun shouldUpdateWidgets(): Boolean {
        val currentDate = LocalDate.now()
        val currentHour = LocalTime.now().hour
        
        // Sempre atualizar se mudou o dia
        if (lastUpdateDate != currentDate) {
            Log.d(TAG, "📅 Data mudou - atualizando widgets")
            return true
        }
        
        // Atualizar a cada hora para mostrar mudanças de horário
        if (lastUpdateHour != currentHour) {
            Log.d(TAG, "🕐 Hora mudou - atualizando widgets")
            return true
        }
        
        // Atualizar se é um horário importante (início de cada hora)
        if (currentHour % 2 == 0 && LocalTime.now().minute < 5) {
            Log.d(TAG, "⏰ Horário importante - atualizando widgets")
            return true
        }
        
        return false
    }
    
    /**
     * Calcula intervalo dinâmico baseado na necessidade de atualização
     */
    private fun getDynamicUpdateInterval(): Long {
        val currentTime = LocalTime.now()
        val currentHour = currentTime.hour
        
        return when {
            // Horário de trabalho (8h-18h) - atualizar mais frequentemente
            currentHour in 8..18 -> UPDATE_INTERVAL_NORMAL
            
            // Madrugada (0h-6h) - atualizar menos frequentemente
            currentHour in 0..6 -> UPDATE_INTERVAL_SLOW
            
            // Noite (19h-23h) - intervalo normal
            else -> UPDATE_INTERVAL_NORMAL
        }
    }
    
    /**
     * Atualiza o timestamp da última atualização
     */
    private fun updateLastUpdateTime() {
        lastUpdateDate = LocalDate.now()
        lastUpdateHour = LocalTime.now().hour
    }
    
    private fun updateAllWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        
        // Atualizar EventListWidgetProvider
        val eventListComponentName = ComponentName(this, EventListWidgetProvider::class.java)
        val eventListWidgetIds = appWidgetManager.getAppWidgetIds(eventListComponentName)
        
        if (eventListWidgetIds.isNotEmpty()) {
            Log.d(TAG, "🔋 Atualizando ${eventListWidgetIds.size} EventListWidgets")
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, eventListWidgetIds)
            intent.component = eventListComponentName
            sendBroadcast(intent)
            appWidgetManager.notifyAppWidgetViewDataChanged(eventListWidgetIds, R.id.event_list_view)
        } else {
            // Se não há widgets ativos, parar o serviço para economizar bateria
            Log.d(TAG, "🔋 Nenhum widget ativo - parando serviço")
            stopSelf()
        }
    }
    
    /**
     * Verifica se há widgets ativos
     */
    private fun hasActiveWidgets(): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val eventListComponentName = ComponentName(this, EventListWidgetProvider::class.java)
        val eventListWidgetIds = appWidgetManager.getAppWidgetIds(eventListComponentName)
        return eventListWidgetIds.isNotEmpty()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🔋 WidgetUpdateService finalizado")
        updateJob?.cancel()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    companion object {
        private const val TAG = "WidgetUpdateService"
        
        fun start(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, WidgetUpdateService::class.java)
            context.stopService(intent)
        }
        
        /**
         * Inicia o serviço apenas se houver widgets ativos
         */
        fun startIfNeeded(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val eventListComponentName = ComponentName(context, EventListWidgetProvider::class.java)
            val eventListWidgetIds = appWidgetManager.getAppWidgetIds(eventListComponentName)
            
            if (eventListWidgetIds.isNotEmpty()) {
                Log.d(TAG, "🔋 Iniciando WidgetUpdateService - ${eventListWidgetIds.size} widgets ativos")
                start(context)
            } else {
                Log.d(TAG, "🔋 Nenhum widget ativo - não iniciando serviço")
            }
        }
    }
}