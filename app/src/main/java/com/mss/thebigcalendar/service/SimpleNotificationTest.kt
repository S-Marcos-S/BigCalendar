package com.mss.thebigcalendar.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * Teste simples para verificar se as notificações estão funcionando
 */
class SimpleNotificationTest(private val context: Context) {

    companion object {
        private const val TAG = "SimpleNotificationTest"
        const val TEST_CHANNEL_ID = "simple_test_notifications"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createTestChannel()
    }

    private fun createTestChannel() {
        val channel = android.app.NotificationChannel(
            TEST_CHANNEL_ID,
            "Teste Simples",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Canal para teste simples de notificação"
            enableVibration(true)
            enableLights(true)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Mostra uma notificação de teste simples
     */
    fun showSimpleTestNotification() {
        
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(com.mss.thebigcalendar.R.drawable.ic_notification_calendar)
            .setContentTitle("🧪 Teste Simples")
            .setContentText("Esta é uma notificação de teste simples!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(888, notification)
    }
}
