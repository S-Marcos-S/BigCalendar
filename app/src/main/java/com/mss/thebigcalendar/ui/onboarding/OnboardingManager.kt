package com.mss.thebigcalendar.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.model.File as DriveFile
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
    isLoading: Boolean = false,
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
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.onboarding_connect_google),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Botão pular
                TextButton(
                    onClick = onSkip,
                    enabled = !isLoading,
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

enum class OnboardingStep {
    WELCOME,
    NOTIFICATION_PERMISSION,
    CHECK_BACKUP,
    RESTORE_BACKUP_PROMPT,
    COMPLETED
}

/**
 * Composable para a janela de restauração de backup do Google Drive
 */
@Composable
fun RestoreCloudBackupDialog(
    isLoading: Boolean,
    backupFile: DriveFile?,
    onRestore: () -> Unit,
    onSkip: () -> Unit
) {
    if (backupFile == null) return
    
    val appProperties = backupFile.appProperties ?: emptyMap()
    val totalActivities = appProperties["totalActivities"]?.toIntOrNull() ?: 0
    val format = stringResource(id = R.string.backup_date_time_format)
    
    val dateString = try {
        val instant = java.time.Instant.ofEpochMilli(backupFile.createdTime.value)
        val localDateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern(format)
        localDateTime.format(formatter)
    } catch (e: Exception) {
        ""
    }

    Dialog(
        onDismissRequest = onSkip,
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
                // Ícone de nuvem
                Text(
                    text = "☁️",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Título
                Text(
                    text = stringResource(R.string.onboarding_restore_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Descrição com detalhes do backup encontrado
                Text(
                    text = stringResource(R.string.onboarding_restore_desc, dateString, totalActivities),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                
                // Botão Restaurar
                Button(
                    onClick = onRestore,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.onboarding_restore_btn),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Botão Pular / Iniciar do zero
                TextButton(
                    onClick = onSkip,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_restore_skip),
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
    isLoggingIn: Boolean,
    googleSignInAccount: GoogleSignInAccount?,
    cloudBackupFiles: List<DriveFile>,
    isListingCloudBackups: Boolean,
    isRestoring: Boolean,
    onCheckBackup: () -> Unit,
    onRestoreBackup: (String, String) -> Unit,
    onComplete: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val onboardingManager = remember { OnboardingManager(context) }

    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var wasLoggingIn by remember { mutableStateOf(false) }
    var hasCheckedBackup by remember { mutableStateOf(false) }
    var wasListingBackups by remember { mutableStateOf(false) }
    var wasRestoring by remember { mutableStateOf(false) }
    
    // Inicializar o passo correto
    LaunchedEffect(Unit) {
        currentStep = when {
            onboardingManager.shouldShowWelcome() -> OnboardingStep.WELCOME
            onboardingManager.shouldShowNotificationPermission() -> OnboardingStep.NOTIFICATION_PERMISSION
            else -> OnboardingStep.COMPLETED
        }
        
        if (currentStep == OnboardingStep.COMPLETED) {
            onComplete()
        }
    }

    // Monitorar finalização do login no passo WELCOME
    LaunchedEffect(isLoggingIn) {
        if (wasLoggingIn && !isLoggingIn && currentStep == OnboardingStep.WELCOME) {
            onboardingManager.markWelcomeShown()
            currentStep = when {
                onboardingManager.shouldShowNotificationPermission() -> OnboardingStep.NOTIFICATION_PERMISSION
                googleSignInAccount != null -> OnboardingStep.CHECK_BACKUP
                else -> OnboardingStep.COMPLETED
            }
            if (currentStep == OnboardingStep.COMPLETED) {
                onboardingManager.markOnboardingCompleted()
                onComplete()
            }
        }
        wasLoggingIn = isLoggingIn
    }

    // Disparar busca de backups no passo CHECK_BACKUP
    LaunchedEffect(currentStep, googleSignInAccount) {
        if (currentStep == OnboardingStep.CHECK_BACKUP && googleSignInAccount != null && !hasCheckedBackup) {
            hasCheckedBackup = true
            onCheckBackup()
        }
    }

    // Monitorar a busca de backups
    LaunchedEffect(isListingCloudBackups) {
        if (wasListingBackups && !isListingCloudBackups && currentStep == OnboardingStep.CHECK_BACKUP) {
            if (cloudBackupFiles.isNotEmpty()) {
                currentStep = OnboardingStep.RESTORE_BACKUP_PROMPT
            } else {
                currentStep = OnboardingStep.COMPLETED
                onboardingManager.markOnboardingCompleted()
                onComplete()
            }
        }
        wasListingBackups = isListingCloudBackups
    }

    // Monitorar a restauração do backup
    LaunchedEffect(isRestoring) {
        if (wasRestoring && !isRestoring && currentStep == OnboardingStep.RESTORE_BACKUP_PROMPT) {
            currentStep = OnboardingStep.COMPLETED
            onboardingManager.markOnboardingCompleted()
            onComplete()
        }
        wasRestoring = isRestoring
    }

    val latestBackupFile = remember(cloudBackupFiles) {
        cloudBackupFiles.maxByOrNull { it.createdTime?.value ?: 0L }
    }

    // Tela de fundo com imagem - apenas quando há onboarding ativo
    if (currentStep != OnboardingStep.COMPLETED) {
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
            if (currentStep == OnboardingStep.WELCOME) {
                WelcomeDialog(
                    isLoading = isLoggingIn,
                    onDismiss = {
                        onboardingManager.markWelcomeShown()
                        currentStep = when {
                            onboardingManager.shouldShowNotificationPermission() -> OnboardingStep.NOTIFICATION_PERMISSION
                            googleSignInAccount != null -> OnboardingStep.CHECK_BACKUP
                            else -> OnboardingStep.COMPLETED
                        }
                        if (currentStep == OnboardingStep.COMPLETED) {
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    },
                    onGoogleSignIn = onGoogleSignIn,
                    onSkip = {
                        onboardingManager.markWelcomeShown()
                        currentStep = when {
                            onboardingManager.shouldShowNotificationPermission() -> OnboardingStep.NOTIFICATION_PERMISSION
                            else -> OnboardingStep.COMPLETED
                        }
                        if (currentStep == OnboardingStep.COMPLETED) {
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    }
                )
            }

            // Janela de permissão de notificação
            if (currentStep == OnboardingStep.NOTIFICATION_PERMISSION) {
                NotificationPermissionDialog(
                    onDismiss = {
                        onboardingManager.markNotificationPermissionShown()
                        currentStep = if (googleSignInAccount != null) {
                            OnboardingStep.CHECK_BACKUP
                        } else {
                            OnboardingStep.COMPLETED
                        }
                        if (currentStep == OnboardingStep.COMPLETED) {
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    },
                    onRequestPermission = {
                        onRequestNotificationPermission()
                        onboardingManager.markNotificationPermissionShown()
                        currentStep = if (googleSignInAccount != null) {
                            OnboardingStep.CHECK_BACKUP
                        } else {
                            OnboardingStep.COMPLETED
                        }
                        if (currentStep == OnboardingStep.COMPLETED) {
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    },
                    onSkip = {
                        onboardingManager.markNotificationPermissionShown()
                        currentStep = if (googleSignInAccount != null) {
                            OnboardingStep.CHECK_BACKUP
                        } else {
                            OnboardingStep.COMPLETED
                        }
                        if (currentStep == OnboardingStep.COMPLETED) {
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    }
                )
            }

            // Janela de carregamento ao buscar backups
            if (currentStep == OnboardingStep.CHECK_BACKUP && isListingCloudBackups) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.onboarding_checking_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Janela de restauração de backup do Google Drive
            if (currentStep == OnboardingStep.RESTORE_BACKUP_PROMPT && latestBackupFile != null) {
                RestoreCloudBackupDialog(
                    isLoading = isRestoring,
                    backupFile = latestBackupFile,
                    onRestore = {
                        onRestoreBackup(latestBackupFile.id, latestBackupFile.name)
                    },
                    onSkip = {
                        currentStep = OnboardingStep.COMPLETED
                        onboardingManager.markOnboardingCompleted()
                        onComplete()
                    }
                )
            }
        }
    }
}
