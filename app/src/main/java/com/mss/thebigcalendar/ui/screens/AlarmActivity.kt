package com.mss.thebigcalendar.ui.screens

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.CalendarContract
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.mss.thebigcalendar.data.model.AlarmSettings
import com.mss.thebigcalendar.data.repository.AlarmRepository
import com.mss.thebigcalendar.service.AlarmService
import com.mss.thebigcalendar.service.NotificationService
import com.mss.thebigcalendar.ui.theme.TheBigCalendarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.mss.thebigcalendar.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Activity dedicada para exibir quando o alarme disparar
 * Esta activity acende a tela, toca o alarme e permite dismiss/snooze
 */
class AlarmActivity : ComponentActivity() {
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var isWakeLockReleased = false
    private var mediaPlayer: MediaPlayer? = null
    private var alarmSettings: AlarmSettings? = null
    
    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        private const val TAG = "AlarmActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Obter ID do alarme
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
        if (alarmId == null) {
            finish()
            return
        }
        
        // Configurar para acender a tela e forçar abertura
        setupWakeLock()
        setupScreen()
        forceBringToFront()
        
        // Carregar configurações do alarme
        val alarmRepository = AlarmRepository(this)
        val notificationService = NotificationService(this)
        val alarmService = AlarmService(this, alarmRepository, notificationService)
        
        lifecycleScope.launch {
            // Cancelar notificação de alarme quando a activity abrir
            alarmService.cancelAlarmNotification(alarmId)
            
            alarmSettings = alarmRepository.getAlarmById(alarmId)
            if (alarmSettings == null) {
                finish()
                return@launch
            }
            
            // Iniciar som do alarme
            startAlarmSound()
            
            // Exibir UI
            setContent {
                TheBigCalendarTheme {
                    AlarmScreen(
                        alarmSettings = alarmSettings!!,
                        onDismiss = { dismissAlarm() },
                        onSnooze = { snoozeAlarm() }
                    )
                }
            }
        }
    }
    
    /**
     * Configura WakeLock para manter a tela ligada
     */
    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "TheBigCalendar:AlarmWakeLock"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutos
        isWakeLockReleased = false
    }
    
    /**
     * Libera o WakeLock de forma segura, evitando liberação múltipla
     */
    private fun releaseWakeLockSafely() {
        if (!isWakeLockReleased && wakeLock != null) {
            try {
                wakeLock?.release()
                isWakeLockReleased = true
                Log.d("AlarmActivity", "✅ WakeLock liberado com sucesso")
            } catch (e: Exception) {
                Log.w("AlarmActivity", "⚠️ Erro ao liberar WakeLock: ${e.message}")
                isWakeLockReleased = true // Marcar como liberado mesmo com erro
            }
        } else {
            Log.d("AlarmActivity", "ℹ️ WakeLock já foi liberado ou é null")
        }
    }
    
    /**
     * Configura a tela para acender e desbloquear
     */
    private fun setupScreen() {
        // Acender a tela
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        
        // Desbloquear se necessário
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (keyguardManager.isKeyguardLocked) {
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
    
    /**
     * Força a activity a aparecer na frente
     */
    private fun forceBringToFront() {
        try {
            // Trazer para frente usando flags de window
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            
            // Forçar foco e tela cheia
            if (!isFinishing) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
            
            // Tentar trazer para frente usando ActivityManager (API 21+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                    val tasks = activityManager.getRunningTasks(1)
                    if (tasks.isNotEmpty()) {
                        val topTask = tasks[0]
                        if (topTask.topActivity?.packageName == packageName) {
                            // Já está na frente
                            Log.d(TAG, "🔔 Activity já está na frente")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Não foi possível verificar posição da activity: ${e.message}")
                }
            }
            
            Log.d(TAG, "🔔 Activity configurada para aparecer na frente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao forçar activity para frente", e)
        }
    }
    
    /**
     * Inicia o som do alarme
     */
    private fun startAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri != null) {
                // Obter o volume de alarme do sistema
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
                val volumeLevel = currentVolume.toFloat() / maxVolume.toFloat()
                
                Log.d(TAG, "🔔 Volume do alarme: $currentVolume/$maxVolume (${(volumeLevel * 100).toInt()}%)")
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, alarmUri)
                    isLooping = true
                    setVolume(volumeLevel, volumeLevel)
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                    prepare()
                    start()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao iniciar som do alarme", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Para o som do alarme
     */
    private fun stopAlarmSound() {
        Log.d("AlarmActivity", "🔇 Iniciando parada completa do som do alarme")
        
        // 1. Parar MediaPlayer local
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                    Log.d("AlarmActivity", "🔇 MediaPlayer local parado")
                }
                player.reset()
                Log.d("AlarmActivity", "🔇 MediaPlayer local resetado")
                player.release()
                Log.d("AlarmActivity", "🔇 MediaPlayer local liberado")
            } catch (e: Exception) {
                Log.w("AlarmActivity", "⚠️ Erro ao parar MediaPlayer local: ${e.message}")
            }
        }
        mediaPlayer = null
        
        // 2. Parar todos os Ringtones do sistema
        try {
            val ringtoneManager = RingtoneManager(this)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(this, alarmUri)
            ringtone?.stop()
            Log.d("AlarmActivity", "🔇 Ringtone do sistema parado")
        } catch (e: Exception) {
            Log.w("AlarmActivity", "⚠️ Erro ao parar Ringtone: ${e.message}")
        }
        
        // 3. REMOVIDO: Não zerar volume de alarme - apenas parar o MediaPlayer
        // O código anterior estava zerando o volume de alarme temporariamente, causando problemas
        
        // 4. Enviar broadcast para parar TODOS os MediaPlayers de outras Activities
        try {
            val intent = Intent("com.mss.thebigcalendar.STOP_ALL_SOUNDS")
            sendBroadcast(intent)
            Log.d("AlarmActivity", "🔇 Broadcast enviado para parar todos os sons")
        } catch (e: Exception) {
            Log.w("AlarmActivity", "⚠️ Erro ao enviar broadcast: ${e.message}")
        }

        // 5. Cancelar todas as notificações com som para parar sons do sistema
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancelAll()
            Log.d("AlarmActivity", "🔇 Todas as notificações canceladas")
        } catch (e: Exception) {
            Log.w("AlarmActivity", "⚠️ Erro ao cancelar notificações: ${e.message}")
        }
        
        // 6. REMOVIDO: Não zerar volumes de outros streams - apenas parar o MediaPlayer do alarme
        // O código anterior estava zerando TODOS os volumes do sistema, causando problemas
        // Agora apenas paramos o MediaPlayer específico do alarme
        
        Log.d("AlarmActivity", "🔇 Parada completa do som concluída")
    }
    
    /**
     * Desativa o alarme
     */
    private fun dismissAlarm() {
        Log.d("AlarmActivity", "🛑 Iniciando dismiss do alarme")
        
        // 1. Parar som de forma síncrona
        stopAlarmSound()
        
        // 2. Parar som do AlarmService IMEDIATAMENTE (fora da corrotina)
        try {
            Log.d("AlarmActivity", "🔇 Criando instâncias do AlarmService...")
            val alarmRepository = AlarmRepository(this@AlarmActivity)
            val notificationService = NotificationService(this@AlarmActivity)
            val alarmService = AlarmService(this@AlarmActivity, alarmRepository, notificationService)
            
            Log.d("AlarmActivity", "🔇 Chamando alarmService.stopAlarmSound()...")
            // PARAR TODOS OS SONS DO ALARME PRIMEIRO (SÍNCRONO)
            alarmService.stopAlarmSound()
            Log.d("AlarmActivity", "🔇 Som do AlarmService parado IMEDIATAMENTE")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "❌ Erro ao parar som do AlarmService: ${e.message}")
            e.printStackTrace()
        }
        
        // 3. Liberar WakeLock
        releaseWakeLockSafely()
        
        // 4. Cancelar alarme no sistema (assíncrono)
        alarmSettings?.let { settings ->
            lifecycleScope.launch {
                try {
                    val alarmRepository = AlarmRepository(this@AlarmActivity)
                    val notificationService = NotificationService(this@AlarmActivity)
                    val alarmService = AlarmService(this@AlarmActivity, alarmRepository, notificationService)
                    
                    // Cancelar alarme no sistema
                    alarmService.cancelAlarm(settings.id)
                    Log.d("AlarmActivity", "✅ Alarme cancelado no sistema")
                    
                    // Cancelar notificação de status do alarme
                    alarmService.cancelAlarmNotification(settings.id)
                    Log.d("AlarmActivity", "✅ Notificação de alarme cancelada")
                    
                } catch (e: Exception) {
                    Log.e("AlarmActivity", "❌ Erro ao cancelar alarme: ${e.message}")
                }
            }
        }
        
        // 5. Pequeno delay para garantir que tudo foi processado
        lifecycleScope.launch {
            kotlinx.coroutines.delay(100)
            Log.d("AlarmActivity", "🏁 Finalizando AlarmActivity")
            
            // Remover da lista de aplicativos recentes (já chama finish() internamente)
            removeFromRecentApps()
        }
    }
    
    /**
     * Adia o alarme (snooze)
     */
    private fun snoozeAlarm() {
        Log.d("AlarmActivity", "⏰ Iniciando snooze do alarme")
        
        // 1. Parar som de forma síncrona
        stopAlarmSound()
        
        // 2. Parar som do AlarmService IMEDIATAMENTE (fora da corrotina)
        try {
            val alarmRepository = AlarmRepository(this@AlarmActivity)
            val notificationService = NotificationService(this@AlarmActivity)
            val alarmService = AlarmService(this@AlarmActivity, alarmRepository, notificationService)
            
            // PARAR TODOS OS SONS DO ALARME PRIMEIRO (SÍNCRONO)
            alarmService.stopAlarmSound()
            Log.d("AlarmActivity", "🔇 Som do AlarmService parado IMEDIATAMENTE")
        } catch (e: Exception) {
            Log.e("AlarmActivity", "❌ Erro ao parar som do AlarmService: ${e.message}")
        }
        
        // 3. Liberar WakeLock
        releaseWakeLockSafely()
        
        // 4. Reagendar para snooze (assíncrono)
        alarmSettings?.let { settings ->
            val snoozeTime = LocalTime.now().plusMinutes(settings.snoozeMinutes.toLong())
            val snoozeSettings = settings.copy(
                id = "${settings.id}_snooze_${System.currentTimeMillis()}",
                time = snoozeTime,
                repeatDays = emptySet() // Snooze é sempre único
            )
            
            lifecycleScope.launch {
                try {
                    val alarmRepository = AlarmRepository(this@AlarmActivity)
                    val notificationService = NotificationService(this@AlarmActivity)
                    val alarmService = AlarmService(this@AlarmActivity, alarmRepository, notificationService)
                    
                    alarmRepository.saveAlarm(snoozeSettings)
                    alarmService.scheduleAlarm(snoozeSettings)
                    Log.d("AlarmActivity", "✅ Alarme reagendado para snooze: $snoozeTime")
                    
                } catch (e: Exception) {
                    Log.e("AlarmActivity", "❌ Erro ao reagendar alarme: ${e.message}")
                }
            }
        }
        
        // 5. Pequeno delay para garantir que tudo foi processado
        lifecycleScope.launch {
            kotlinx.coroutines.delay(100)
            Log.d("AlarmActivity", "🏁 Finalizando AlarmActivity após snooze")
            
            // Remover da lista de aplicativos recentes (já chama finish() internamente)
            removeFromRecentApps()
        }
    }
    
    /**
     * Remove a activity da lista de aplicativos recentes
     */
    private fun removeFromRecentApps() {
        try {
            Log.d("AlarmActivity", "🗑️ Removendo AlarmActivity dos aplicativos recentes")
            
            // Método principal: Usar finishAndRemoveTask() (API 21+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
                Log.d("AlarmActivity", "🗑️ finishAndRemoveTask() executado - activity removida dos aplicativos recentes")
                return
            }
            
            // Fallback para versões anteriores ao Android 5.0
            Log.d("AlarmActivity", "🗑️ Usando fallback para versões anteriores ao Android 5.0")
            
            // Para versões mais antigas, apenas finalizar normalmente
            // O sistema não suporta remoção programática dos aplicativos recentes
            finish()
            
        } catch (e: Exception) {
            Log.e("AlarmActivity", "❌ Erro ao remover dos aplicativos recentes", e)
            // Em caso de erro, pelo menos finalizar a activity
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmSound()
        releaseWakeLockSafely()
    }
}

@Composable
private fun AlarmScreen(
    alarmSettings: AlarmSettings,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    
    // Atualizar tempo a cada segundo
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Título do alarme
            Text(
                text = alarmSettings.label,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Horário atual
            Text(
                text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Horário do alarme
            Text(
                text = stringResource(R.string.alarm_time_format, alarmSettings.time.format(DateTimeFormatter.ofPattern("HH:mm"))),
                fontSize = 18.sp,
                color = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Botões de ação
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Botão Snooze
                Button(
                    onClick = onSnooze,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Magenta
                    ),
                    modifier = Modifier.size(120.dp, 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Snooze,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = stringResource(R.string.alarm_snooze_format, alarmSettings.snoozeMinutes),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
                
                // Botão Dismiss
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    modifier = Modifier.size(120.dp, 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AlarmOff,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = stringResource(R.string.alarm_turn_off),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
