package com.mss.thebigcalendar.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mss.thebigcalendar.MainActivity
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.repository.SettingsRepository
import com.mss.thebigcalendar.worker.GeminiSchedulerWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Atividade transparente que aciona diretamente a janela nativa de reconhecimento de voz
 * sobre a tela inicial (launcher) do celular, sem entrar na tela principal do aplicativo.
 */
class GeminiVoiceTriggerActivity : ComponentActivity() {

    private var speechLaunched = false

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                processVoiceCommand(spokenText)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        // Suporte a exibição sobre a tela de bloqueio se acionado via QS tile
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }

        speechLaunched = savedInstanceState?.getBoolean("speech_launched") ?: false
        if (!speechLaunched) {
            checkAndLaunchVoice()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("speech_launched", speechLaunched)
    }

    private fun checkAndLaunchVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.gemini_speech_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val settingsRepository = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            val apiKey = settingsRepository.geminiApiKey.first()
            if (apiKey.isBlank()) {
                Toast.makeText(
                    this@GeminiVoiceTriggerActivity,
                    R.string.gemini_no_api_key,
                    Toast.LENGTH_LONG
                ).show()

                // Abre a tela principal nas configurações do Gemini para cadastrar a chave
                val settingsIntent = Intent(this@GeminiVoiceTriggerActivity, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_GEMINI
                    putExtra(MainActivity.EXTRA_OPEN_GEMINI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(settingsIntent)
                finish()
                return@launch
            }

            launchSpeechRecognition()
        }
    }

    private fun launchSpeechRecognition() {
        speechLaunched = true
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.gemini_speak_now))
            }
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.gemini_speech_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun processVoiceCommand(prompt: String) {
        Toast.makeText(this, getString(R.string.gemini_processing), Toast.LENGTH_SHORT).show()

        val settingsRepository = SettingsRepository(applicationContext)
        lifecycleScope.launch {
            val apiKey = settingsRepository.geminiApiKey.first()
            val model = settingsRepository.geminiModel.first()

            val workData = Data.Builder()
                .putString(GeminiSchedulerWorker.KEY_PROMPT, prompt)
                .putString(GeminiSchedulerWorker.KEY_API_KEY, apiKey)
                .putString(GeminiSchedulerWorker.KEY_MODEL, model)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<GeminiSchedulerWorker>()
                .setInputData(workData)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                GeminiSchedulerWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            finish()
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
