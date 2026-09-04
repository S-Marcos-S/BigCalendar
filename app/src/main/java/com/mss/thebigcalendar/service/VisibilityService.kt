package com.mss.thebigcalendar.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.ActivityType
import com.mss.thebigcalendar.data.model.NotificationSettings
import com.mss.thebigcalendar.data.model.VisibilityLevel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Serviço para gerenciar a visibilidade das atividades baseado no nível configurado
 */
class VisibilityService(private val context: Context) {

    companion object {
        private const val TAG = "VisibilityService"
        private const val VISIBILITY_CHANNEL_ID = "visibility_alerts"
        private const val VISIBILITY_CHANNEL_NAME = "Alertas de Visibilidade"
        private const val VISIBILITY_CHANNEL_DESCRIPTION = "Alertas para atividades com alta visibilidade"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    init {
        createVisibilityNotificationChannel()
    }

    /**
     * Cria o canal de notificação para alertas de visibilidade
     */
    private fun createVisibilityNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                VISIBILITY_CHANNEL_ID,
                VISIBILITY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = VISIBILITY_CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Verifica se o app tem permissão para sobrepor outros apps
     */
    fun hasOverlayPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true // Para versões anteriores ao Android 6.0
        }
        
        return hasPermission
    }

    /**
     * Solicita permissão para sobrepor outros apps
     */
    fun requestOverlayPermission(): Intent {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    /**
     * Exibe o alerta baseado no nível de visibilidade da atividade
     * NOTA: Para notificações de alta visibilidade, agora usamos o HighVisibilityNotificationService
     * que mantém o app ativo mesmo com a tela desligada
     */
    fun showVisibilityAlert(activity: Activity) {
        val hasPermission = hasOverlayPermission()
        
        when (activity.visibility) {
            VisibilityLevel.LOW -> {
                // Apenas notificação padrão (já gerenciada pelo NotificationService)
            }
            VisibilityLevel.MEDIUM -> {
                if (hasPermission) {
                    showMediumVisibilityAlert(activity)
                } else {
                    // Fallback para notificação se não tiver permissão
                    showFallbackNotification(activity, "Alerta Médio")
                }
            }
            VisibilityLevel.HIGH -> {
                // ✅ NOVA ESTRATÉGIA: Usar HighVisibilityNotificationService
                // que mantém o app ativo mesmo com a tela desligada
                
                // Iniciar serviço de alta visibilidade
                val highVisibilityIntent = Intent(context, com.mss.thebigcalendar.service.HighVisibilityNotificationService::class.java).apply {
                    action = com.mss.thebigcalendar.service.HighVisibilityNotificationService.ACTION_SHOW_NOTIFICATION
                    putExtra(com.mss.thebigcalendar.service.HighVisibilityNotificationService.EXTRA_ACTIVITY_ID, activity.id)
                    putExtra(com.mss.thebigcalendar.service.HighVisibilityNotificationService.EXTRA_ACTIVITY_TITLE, activity.title)
                    putExtra(com.mss.thebigcalendar.service.HighVisibilityNotificationService.EXTRA_ACTIVITY_DESCRIPTION, activity.description)
                    putExtra(com.mss.thebigcalendar.service.HighVisibilityNotificationService.EXTRA_ACTIVITY_DATE, activity.date)
                    putExtra(com.mss.thebigcalendar.service.HighVisibilityNotificationService.EXTRA_ACTIVITY_TIME, activity.startTime?.toString())
                }
                
                context.startService(highVisibilityIntent)
            }
        }
    }

    /**
     * Exibe alerta de visibilidade média (banner na parte inferior)
     */
    private fun showMediumVisibilityAlert(activity: Activity) {
        try {
            // ✅ Verificar se estamos na Main thread
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                // Usar Handler para executar na Main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showMediumVisibilityAlert(activity)
                }
                return
            }
            
            // 🔊 Tocar som de notificação padrão do dispositivo
            playNotificationSound()
            
            // Criar layout para o banner
            val layoutInflater = LayoutInflater.from(context)
            val bannerView = layoutInflater.inflate(R.layout.visibility_banner_medium, null)

            // Configurar texto do banner
            val titleText = bannerView.findViewById<TextView>(R.id.banner_title)
            val timeText = bannerView.findViewById<TextView>(R.id.banner_time)
            
            titleText.text = activity.title
            timeText.text = formatActivityTime(activity)

            // Configurar parâmetros da janela
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.BOTTOM
                y = 100 // Margem do fundo
            }

            // Adicionar banner à tela
            windowManager.addView(bannerView, layoutParams)

            // Remover banner após 5 segundos
            bannerView.postDelayed({
                try {
                    windowManager.removeView(bannerView)
                } catch (e: Exception) {
                    // Erro ao remover banner
                }
            }, 5000)

        } catch (e: Exception) {
            // Fallback para notificação
            showFallbackNotification(activity, "Alerta Médio")
        }
    }

    /**
     * Exibe alerta de visibilidade alta (tela inteira)
     */
    private fun showHighVisibilityAlert(activity: Activity) {
        try {
            // ✅ Verificar se estamos na Main thread
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                // Usar Handler para executar na Main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showHighVisibilityAlert(activity)
                }
                return
            }
            
            // 🔊 Tocar som de notificação padrão do dispositivo
            playNotificationSound()
            
            // Criar layout para o alerta de tela inteira
            val layoutInflater = LayoutInflater.from(context)
            val fullScreenView = layoutInflater.inflate(R.layout.visibility_alert_high, null)

            // Configurar texto do alerta
            val titleText = fullScreenView.findViewById<TextView>(R.id.alert_title)
            val descriptionText = fullScreenView.findViewById<TextView>(R.id.alert_description)
            val timeText = fullScreenView.findViewById<TextView>(R.id.alert_time)
            
            titleText.text = activity.title
            descriptionText.text = activity.description ?: "Sem descrição"
            timeText.text = formatActivityTime(activity)

            // Configurar parâmetros da janela
            val layoutParams = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                gravity = Gravity.CENTER
                systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                                   View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                                   View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            // Adicionar alerta à tela
            windowManager.addView(fullScreenView, layoutParams)

            // Configurar botão Adiar
            val snoozeButton = fullScreenView.findViewById<Button>(R.id.alert_snooze_button)
            snoozeButton.setOnClickListener {
                try {
                    showSnoozeOptionsDialog(activity, fullScreenView)
                } catch (e: Exception) {
                    // Erro ao mostrar opções de adiamento
                }
            }

            // Configurar botão Concluído
            val completeButton = fullScreenView.findViewById<Button>(R.id.alert_complete_button)
            completeButton.setOnClickListener {
                try {
                    // Remover a janela de sobreposição imediatamente
                    windowManager.removeView(fullScreenView)
                    
                    // Garantir que a janela seja removida dos apps recentes
                    fullScreenView.post {
                        try {
                            // Forçar remoção da janela se ainda estiver presente
                            windowManager.removeView(fullScreenView)
                        } catch (e: Exception) {
                            // Janela já foi removida, ignorar erro
                        }
                    }

                    // Enviar broadcast para que o NotificationReceiver marque a atividade como concluída
                    val completeIntent = Intent(context, NotificationReceiver::class.java).apply {
                        action = NotificationService.ACTION_DISMISS
                        putExtra(NotificationService.EXTRA_ACTIVITY_ID, activity.id)
                    }
                    context.sendBroadcast(completeIntent)

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao processar botão de conclusão do alerta", e)
                }
            }


        } catch (e: Exception) {
            // Fallback para notificação
            showFallbackNotification(activity, "Alerta Alto")
        }
    }

    /**
     * Notificação de fallback quando não há permissão de sobreposição
     */
    private fun showFallbackNotification(activity: Activity, alertType: String) {
        // Para atividades recorrentes, usar o ID da instância específica
        val activityIdForNotification = if (activity.id.contains("_")) {
            // Já é uma instância específica
            activity.id
        } else {
            // É uma atividade base, criar ID da instância específica
            // Para atividades HOURLY, incluir o horário no ID
            if (activity.recurrenceRule?.startsWith("FREQ=HOURLY") == true && activity.startTime != null) {
                val timeString = activity.startTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                "${activity.id}_${activity.date}_${timeString}"
            } else {
                "${activity.id}_${activity.date}"
            }
        }
        
        // Intent para marcar como concluída
        val completeIntent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationService.ACTION_DISMISS
            putExtra(NotificationService.EXTRA_ACTIVITY_ID, activityIdForNotification)
        }
        
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (activity.id + "_complete_fallback").hashCode(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
                    val notification = NotificationCompat.Builder(context, VISIBILITY_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notification_calendar)            .setContentTitle("$alertType: ${activity.title}")
            .setContentText("${activity.description ?: context.getString(R.string.no_description)} - ${formatActivityTime(activity)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_action_done),
                completePendingIntent
            )
            .build()

        notificationManager.notify(activity.id.hashCode(), notification)
    }

    /**
     * Formata o horário da atividade para exibição
     */
    private fun formatActivityTime(activity: Activity): String {
        return if (activity.isAllDay) {
            context.getString(R.string.all_day)
        } else if (activity.startTime != null) {
            val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            context.getString(R.string.at_time_prefix, activity.startTime.format(formatter))
        } else {
            context.getString(R.string.no_time_set)
        }
    }

    /**
     * Toca o som de notificação padrão do dispositivo
     * Funciona mesmo com a tela desligada e força audibilidade
     */
    private fun playNotificationSound() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Verificar se o som está habilitado
            if (audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_SILENT) {
                return
            }
            
            
            // Primeiro, tentar usar AudioManager.playSoundEffect() - mais confiável
            try {
                // Tocar som de notificação via AudioManager (mais confiável)
                audioManager.playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
                
                // Adicionar vibração para garantir que o usuário perceba
                playVibration()
                return
                
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ AudioManager falhou, tentando NotificationManager...", e)
            }
            
            // Segunda tentativa: usar NotificationManager para tocar som
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                
                // Criar uma notificação temporária apenas para tocar o som
                val tempNotification = NotificationCompat.Builder(context, VISIBILITY_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification_calendar)
                    .setContentTitle(context.getString(R.string.alert_title))
                    .setContentText(context.getString(R.string.high_visibility_notification))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    // REMOVIDO: .setSound() - pode causar som adicional
                    .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                    .setAutoCancel(true)
                    .build()
                
                // Enviar notificação temporária (será cancelada imediatamente)
                notificationManager.notify(99999, tempNotification)
                
                // Cancelar imediatamente após enviar
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    notificationManager.cancel(99999)
                }, 100)
                
                return
                
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ NotificationManager falhou, tentando MediaPlayer...", e)
            }
            
            // Fallback: usar MediaPlayer com configuração mais robusta
            val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
            
            // Configurar atributos de áudio para notificação
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()
            
            // Criar MediaPlayer com configuração assíncrona
            val mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, notificationUri)
                setVolume(1.0f, 1.0f) // Volume máximo
                
                // Configurar listeners antes de preparar
                setOnPreparedListener { mp ->
                    Log.d(TAG, "🎵 Som preparado, tocando...")
                    try {
                        mp.start()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Erro ao iniciar MediaPlayer", e)
                        mp.release()
                    }
                }
                
                setOnCompletionListener { mp ->
                    Log.d(TAG, "✅ Som de notificação finalizado")
                    mp.release()
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "❌ Erro no MediaPlayer: what=$what, extra=$extra")
                    mp.release()
                    true
                }
                
                setOnInfoListener { mp, what, extra ->
                    Log.d(TAG, "ℹ️ MediaPlayer info: what=$what, extra=$extra")
                    false
                }
                
                // Preparar de forma assíncrona
                prepareAsync()
            }
            
            // Adicionar vibração para garantir que o usuário perceba
            playVibration()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao configurar som de notificação", e)
            // Pelo menos tentar vibrar
            playVibration()
        }
    }
    
    /**
     * Toca vibração para alertas de alta visibilidade
     */
    private fun playVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Padrão de vibração para notificações importantes
                val vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                val vibrationEffect = android.os.VibrationEffect.createWaveform(
                    vibrationPattern, 
                    -1 // Não repetir
                )
                vibrator.vibrate(vibrationEffect)
            } else {
                // Para versões anteriores ao Android 8.0
                vibrator.vibrate(longArrayOf(0, 500, 200, 500, 200, 500), -1)
            }
            
            Log.d(TAG, "📳 Vibração de alerta ativada")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao ativar vibração", e)
        }
    }

    

    /**
     * Exibe o diálogo de opções de adiamento
     */
    private fun showSnoozeOptionsDialog(activity: Activity, currentView: View) {
        try {
            // Inflar o layout do diálogo
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.snooze_options_dialog, null)
            
            // Configurar parâmetros da janela para o diálogo
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                gravity = Gravity.CENTER
            }
            
            // Adicionar diálogo à tela
            windowManager.addView(dialogView, layoutParams)
            
            // Configurar botões de adiamento
            val snooze5minButton = dialogView.findViewById<Button>(R.id.snooze_5min_button)
            val snooze30minButton = dialogView.findViewById<Button>(R.id.snooze_30min_button)
            val snooze1hourButton = dialogView.findViewById<Button>(R.id.snooze_1hour_button)
            val cancelButton = dialogView.findViewById<Button>(R.id.snooze_cancel_button)
            
            // Botão 5 minutos
            snooze5minButton.setOnClickListener {
                snoozeActivity(activity, 5, currentView, dialogView)
            }
            
            // Botão 30 minutos
            snooze30minButton.setOnClickListener {
                snoozeActivity(activity, 30, currentView, dialogView)
            }
            
            // Botão 1 hora
            snooze1hourButton.setOnClickListener {
                snoozeActivity(activity, 60, currentView, dialogView)
            }
            
            // Botão cancelar
            cancelButton.setOnClickListener {
                try {
                    // Remover o diálogo imediatamente
                    windowManager.removeView(dialogView)
                    
                    // Garantir que a janela seja removida dos apps recentes
                    dialogView.post {
                        try {
                            windowManager.removeView(dialogView)
                        } catch (e: Exception) {
                            // Janela já foi removida, ignorar erro
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Erro ao remover diálogo de adiamento", e)
                }
            }
            
        } catch (e: Exception) {
            // Erro ao exibir diálogo de opções de adiamento
        }
    }

    /**
     * Adia a atividade pelo tempo especificado
     */
    private fun snoozeActivity(activity: Activity, minutes: Int, currentView: View, dialogView: View) {
        try {
            // Remover o diálogo primeiro
            windowManager.removeView(dialogView)
            
            // Remover o alerta principal
            windowManager.removeView(currentView)
            
            // Garantir que ambas as janelas sejam removidas dos apps recentes
            dialogView.post {
                try {
                    windowManager.removeView(dialogView)
                } catch (e: Exception) {
                    // Janela já foi removida, ignorar erro
                }
            }
            
            currentView.post {
                try {
                    windowManager.removeView(currentView)
                } catch (e: Exception) {
                    // Janela já foi removida, ignorar erro
                }
            }
            
            // Agendar nova notificação para o tempo de adiamento
            val notificationService = NotificationService(context)
            notificationService.scheduleSnoozedNotification(activity, minutes)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao adiar atividade", e)
        }
    }
    
    /**
     * Função de teste para verificar se o alerta de alta visibilidade está funcionando
     * NOVA ESTRATÉGIA: Usa o HighVisibilityNotificationService que mantém o app ativo
     */
    fun testHighVisibilityAlert() {
        val testActivity = Activity(
            id = "test_high_visibility_${System.currentTimeMillis()}",
            title = "TESTE - Notificação de Alta Visibilidade",
            description = "Este é um teste da nova estratégia que mantém o app ativo mesmo com a tela desligada",
            date = java.time.LocalDate.now().toString(),
            startTime = LocalTime.now(),
            endTime = null,
            isAllDay = false,
            location = null,
            categoryColor = "#FF0000",
            activityType = ActivityType.TASK,
            recurrenceRule = null,
            notificationSettings = NotificationSettings(),
            isCompleted = false,
            visibility = VisibilityLevel.HIGH,
            showInCalendar = true,
            isFromGoogle = false
        )
        
        Log.d(TAG, "🧪 Iniciando teste de notificação de alta visibilidade")
        showVisibilityAlert(testActivity)
    }
}
