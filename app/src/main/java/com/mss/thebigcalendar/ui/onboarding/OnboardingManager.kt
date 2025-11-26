package com.mss.thebigcalendar.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mss.thebigcalendar.R

/**
 * Gerenciador de janelas de onboarding (primeira inicialização)
 */
class OnboardingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "OnboardingManager"
        private const val PREFS_NAME = "onboarding_prefs"
        private const val KEY_WELCOME_SHOWN = "welcome_shown"
        private const val KEY_GOOGLE_CONNECTED = "google_connected"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_NOTIFICATION_PERMISSION_SHOWN = "notification_permission_shown"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Verifica se a janela de boas-vindas já foi exibida
     */
    fun isWelcomeShown(): Boolean {
        return prefs.getBoolean(KEY_WELCOME_SHOWN, false)
    }
    
    /**
     * Marca a janela de boas-vindas como exibida
     */
    fun markWelcomeShown() {
        prefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply()
    }

    /**
     * Verifica se a janela de permissão de notificação já foi exibida
     */
    fun isNotificationPermissionShown(): Boolean {
        return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_SHOWN, false)
    }

    /**
     * Marca a janela de permissão de notificação como exibida
     */
    fun markNotificationPermissionShown() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_SHOWN, true).apply()
    }
    
    /**
     * Verifica se o Google já foi conectado
     */
    fun isGoogleConnected(): Boolean {
        return prefs.getBoolean(KEY_GOOGLE_CONNECTED, false)
    }
    
    /**
     * Marca o Google como conectado
     */
    fun markGoogleConnected() {
        prefs.edit().putBoolean(KEY_GOOGLE_CONNECTED, true).apply()
    }
    
    /**
     * Verifica se o onboarding foi completado
     */
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    /**
     * Marca o onboarding como completado
     */
    fun markOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }
    
    /**
     * Reseta todas as configurações de onboarding (útil para testes)
     */
    fun resetOnboarding() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Verifica se deve exibir alguma janela de onboarding
     */
    fun shouldShowOnboarding(): Boolean {
        return !isOnboardingCompleted()
    }
    
    /**
     * Verifica se deve exibir a janela de boas-vindas
     */
    fun shouldShowWelcome(): Boolean {
        return !isWelcomeShown()
    }

    /**
     * Verifica se deve exibir a janela de permissão de notificação
     */
    fun shouldShowNotificationPermission(): Boolean {
        return isWelcomeShown() && !isNotificationPermissionShown()
    }
}

/**
 * Composable para a janela de boas-vindas
 */
@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ícone de boas-vindas
                Text(
                    text = stringResource(R.string.onboarding_welcome_emoji),
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Título
                Text(
                    text = stringResource(R.string.onboarding_welcome_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Subtítulo
                Text(
                    text = stringResource(R.string.onboarding_welcome_subtitle),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Descrição
                Text(
                    text = stringResource(R.string.onboarding_welcome_desc),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                // Botão conectar Google
                Button(
                    onClick = onGoogleSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_connect_google),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Botão pular
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip_now),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Composable para a janela de permissão de notificação
 */
@Composable
fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Ícone de notificação
                Text(
                    text = stringResource(R.string.onboarding_notification_emoji),
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Título
                Text(
                    text = stringResource(R.string.onboarding_notification_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Subtítulo
                Text(
                    text = stringResource(R.string.onboarding_notification_subtitle),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Descrição
                Text(
                    text = stringResource(R.string.onboarding_notification_desc),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                // Botão solicitar permissão
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_allow_notifications),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Botão pular
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_skip_now),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Composable para gerenciar o fluxo de onboarding
 */
@Composable
fun OnboardingFlow(
    onComplete: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val onboardingManager = remember { OnboardingManager(context) }

    var showWelcome by remember { mutableStateOf(false) }
    var showNotificationPermission by remember { mutableStateOf(false) }
    
    // Verificar se deve exibir onboarding
    LaunchedEffect(Unit) {
        when {
            onboardingManager.shouldShowWelcome() -> showWelcome = true
            onboardingManager.shouldShowNotificationPermission() -> showNotificationPermission = true
            else -> onComplete()
        }
    }

    // Tela de fundo com imagem - apenas quando há onboarding ativo
    if (showWelcome || showNotificationPermission) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Imagem de fundo
            Image(
                painter = painterResource(id = R.drawable.tbc_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Overlay escuro para melhorar legibilidade
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            )
            
            // Janela de boas-vindas
            if (showWelcome) {
                WelcomeDialog(
                    onDismiss = {
                        showWelcome = false
                        onboardingManager.markWelcomeShown()
                        if (onboardingManager.shouldShowNotificationPermission()) {
                            showNotificationPermission = true
                        } else {
                            onComplete()
                        }
                    },
                    onGoogleSignIn = {
                        onGoogleSignIn()
                        showWelcome = false
                        onboardingManager.markWelcomeShown()
                        if (onboardingManager.shouldShowNotificationPermission()) {
                            showNotificationPermission = true
                        } else {
                            onComplete()
                        }
                    },
                    onSkip = {
                        showWelcome = false
                        onboardingManager.markWelcomeShown()
                        if (onboardingManager.shouldShowNotificationPermission()) {
                            showNotificationPermission = true
                        } else {
                            onComplete()
                        }
                    }
                )
            }

            // Janela de permissão de notificação
            if (showNotificationPermission) {
                NotificationPermissionDialog(
                    onDismiss = {
                        showNotificationPermission = false
                        onboardingManager.markNotificationPermissionShown()
                        onComplete()
                    },
                    onRequestPermission = {
                        onRequestNotificationPermission()
                        showNotificationPermission = false
                        onboardingManager.markNotificationPermissionShown()
                        onComplete()
                    },
                    onSkip = {
                        showNotificationPermission = false
                        onboardingManager.markNotificationPermissionShown()
                        onComplete()
                    }
                )
            }
        }
    }
}
