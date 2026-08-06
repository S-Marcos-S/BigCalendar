package com.mss.thebigcalendar.ui.onboarding

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.drive.model.File as DriveFile
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import com.mss.thebigcalendar.data.service.BackupInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
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
    RESTORE_LOCAL_BACKUP_PROMPT,
    CRASH_REPORT_CONSENT,
    COMPLETED
}

/**
 * Composable para a janela de restauração de backup do Google Drive
 */
@Composable
fun RestoreCloudBackupDialog(
    isLoading: Boolean,
    backupFile: DriveFile?,
    onRestore: (String?) -> Unit, // Alterado para receber a senha opcional
    onSelectLocalBackup: () -> Unit,
    onSkip: () -> Unit,
    showDecryptionField: Boolean = false,
    decryptionErrorMessage: String? = null
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
    var password by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
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
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                // Campo de senha (se estiver criptografado)
                if (showDecryptionField) {
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text(stringResource(id = R.string.encryption_password_placeholder)) },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    val currentError = passwordError ?: decryptionErrorMessage
                    if (currentError != null) {
                        Text(
                            text = currentError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                // Botão Restaurar
                Button(
                    onClick = {
                        if (showDecryptionField && password.isEmpty()) {
                            passwordError = "A senha não pode ser vazia!"
                        } else {
                            onRestore(password.takeIf { it.isNotEmpty() })
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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

                // Botão Buscar Backup Local
                OutlinedButton(
                    onClick = onSelectLocalBackup,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_restore_local_search_btn),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
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
 * Composable para a janela de prompt de backup local
 */
@Composable
fun RestoreLocalBackupPromptDialog(
    backupDirectoryUri: String?,
    backupFiles: List<BackupInfo>,
    isListing: Boolean,
    isRestoring: Boolean,
    localBackupUriBeingRestored: String?,
    onSelectFolder: () -> Unit,
    onRestoreBackup: (String) -> Unit,
    onSkip: () -> Unit
) {
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
                // Ícone de pasta/armazenamento
                Text(
                    text = "📂",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (backupDirectoryUri.isNullOrBlank()) {
                    // Título
                    Text(
                        text = stringResource(R.string.onboarding_restore_local_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Descrição
                    Text(
                        text = stringResource(R.string.onboarding_restore_local_desc),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    // Botão Escolher Pasta
                    Button(
                        onClick = onSelectFolder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_choose_folder),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Botão Negar/Cancelar
                    TextButton(
                        onClick = onSkip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_deny),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Pasta já foi selecionada
                    if (isListing) {
                        // Título / Estado de busca
                        Text(
                            text = stringResource(R.string.onboarding_checking_backup),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    } else if (backupFiles.isEmpty()) {
                        // Título
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_no_backups),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Descrição
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_no_backups_desc),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        
                        // Botão Escolher Outra Pasta
                        Button(
                            onClick = onSelectFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(stringResource(R.string.onboarding_restore_local_choose_other))
                        }
                        
                        // Botão Concluir
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_restore_local_finish), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // Título
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_found),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Descrição
                        Text(
                            text = stringResource(R.string.onboarding_restore_local_found_desc),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        // Lista de Backups
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            backupFiles.take(3).forEach { backupInfo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = backupInfo.fileName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val sizeKb = backupInfo.fileSize / 1024
                                            Text(
                                                text = stringResource(
                                                    R.string.onboarding_restore_local_activities_size,
                                                    backupInfo.totalActivities,
                                                    sizeKb
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { onRestoreBackup(backupInfo.uri) },
                                            enabled = !isRestoring,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            val isThisItemRestoring = isRestoring && localBackupUriBeingRestored == backupInfo.uri
                                            if (isThisItemRestoring) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                            } else {
                                                Text(stringResource(R.string.onboarding_restore_local_restore), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Botão Escolher Outra Pasta
                        TextButton(
                            onClick = onSelectFolder,
                            enabled = !isRestoring,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_restore_local_select_other))
                        }
                        
                        // Botão Pular / Concluir
                        TextButton(
                            onClick = onSkip,
                            enabled = !isRestoring,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.onboarding_restore_local_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Composable para a janela de consentimento de envio de relatórios de erros
 */
@Composable
fun CrashlyticsConsentDialog(
    initialEnabled: Boolean = true,
    onComplete: (Boolean) -> Unit
) {
    var isEnabled by remember { mutableStateOf(initialEnabled) }
    
    Dialog(
        onDismissRequest = { onComplete(isEnabled) },
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
                // Ícone/Emoji
                Text(
                    text = "📊",
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Título
                Text(
                    text = stringResource(R.string.crashlytics_setting_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Descrição
                Text(
                    text = stringResource(R.string.onboarding_crash_desc),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                
                // Opção (Switch)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { isEnabled = !isEnabled }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.crashlytics_setting_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.crashlytics_setting_description),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botão Concluir
                Button(
                    onClick = { onComplete(isEnabled) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_restore_local_finish),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
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
    onRestoreBackup: (String, String, String?) -> Unit,
    onComplete: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    backupDirectoryUri: String? = null,
    backupFiles: List<BackupInfo> = emptyList(),
    isListingLocalBackups: Boolean = false,
    isRestoringLocalBackup: Boolean = false,
    localBackupUriBeingRestored: String? = null,
    onBackupDirectorySelected: (Uri) -> Unit = {},
    onRestoreLocalBackup: (String) -> Unit = {},
    onLoadLocalBackups: () -> Unit = {},
    isCrashlyticsEnabled: Boolean = false,
    onCrashlyticsToggle: (Boolean) -> Unit = {},
    showDecryptionDialog: Boolean = false,
    decryptionErrorMessage: String? = null,
    decryptionBackupUri: String? = null,
    decryptionCloudFileId: String? = null,
    decryptionCloudFileName: String? = null,
    onConfirmDecryption: (String, String?, String?, String?) -> Unit = { _, _, _, _ ->},
    onDismissDecryption: () -> Unit = {}
) {
    val context = LocalContext.current
    val onboardingManager = remember { OnboardingManager(context) }

    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var wasLoggingIn by remember { mutableStateOf(false) }
    var hasCheckedBackup by remember { mutableStateOf(false) }
    var wasListingBackups by remember { mutableStateOf(false) }
    var wasRestoring by remember { mutableStateOf(false) }
    var wasRestoringLocal by remember { mutableStateOf(false) }

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                onBackupDirectorySelected(uri)
            }
        }
    )

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

    // Monitorar seleção de diretório local
    LaunchedEffect(backupDirectoryUri) {
        if (!backupDirectoryUri.isNullOrBlank()) {
            onLoadLocalBackups()
        }
    }

    // Monitorar finalização do login no passo WELCOME
    LaunchedEffect(isLoggingIn) {
        if (wasLoggingIn && !isLoggingIn && currentStep == OnboardingStep.WELCOME) {
            onboardingManager.markWelcomeShown()
            currentStep = when {
                onboardingManager.shouldShowNotificationPermission() -> OnboardingStep.NOTIFICATION_PERMISSION
                googleSignInAccount != null -> OnboardingStep.CHECK_BACKUP
                else -> OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                currentStep = OnboardingStep.CRASH_REPORT_CONSENT
            }
        }
        wasListingBackups = isListingCloudBackups
    }

    // Monitorar a restauração do backup
    LaunchedEffect(isRestoring, showDecryptionDialog) {
        if (wasRestoring && !isRestoring && currentStep == OnboardingStep.RESTORE_BACKUP_PROMPT) {
            if (!showDecryptionDialog) {
                currentStep = OnboardingStep.CRASH_REPORT_CONSENT
            }
        }
        wasRestoring = isRestoring
    }

    // Monitorar a restauração do backup local
    LaunchedEffect(isRestoringLocalBackup, showDecryptionDialog) {
        if (wasRestoringLocal && !isRestoringLocalBackup && currentStep == OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT) {
            if (!showDecryptionDialog) {
                if (googleSignInAccount != null) {
                    currentStep = OnboardingStep.CRASH_REPORT_CONSENT
                } else {
                    currentStep = OnboardingStep.COMPLETED
                    onboardingManager.markOnboardingCompleted()
                    onComplete()
                }
            }
        }
        wasRestoringLocal = isRestoringLocalBackup
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
                            else -> OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                            else -> OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                            OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                            OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                            OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
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
                    onRestore = { password ->
                        onRestoreBackup(latestBackupFile.id, latestBackupFile.name, password)
                    },
                    onSelectLocalBackup = {
                        onDismissDecryption()
                        currentStep = OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT
                        directoryPickerLauncher.launch(null)
                    },
                    onSkip = {
                        onDismissDecryption()
                        currentStep = OnboardingStep.CRASH_REPORT_CONSENT
                    },
                    showDecryptionField = showDecryptionDialog && decryptionCloudFileId == latestBackupFile.id,
                    decryptionErrorMessage = decryptionErrorMessage
                )
            }

            // Janela de restauração de backup local
            if (currentStep == OnboardingStep.RESTORE_LOCAL_BACKUP_PROMPT) {
                RestoreLocalBackupPromptDialog(
                    backupDirectoryUri = backupDirectoryUri,
                    backupFiles = backupFiles,
                    isListing = isListingLocalBackups,
                    isRestoring = isRestoringLocalBackup,
                    localBackupUriBeingRestored = localBackupUriBeingRestored,
                    onSelectFolder = {
                        directoryPickerLauncher.launch(null)
                    },
                    onRestoreBackup = onRestoreLocalBackup,
                    onSkip = {
                        if (googleSignInAccount != null) {
                            currentStep = OnboardingStep.CRASH_REPORT_CONSENT
                        } else {
                            currentStep = OnboardingStep.COMPLETED
                            onboardingManager.markOnboardingCompleted()
                            onComplete()
                        }
                    }
                )
            }

            // Janela de consentimento de envio de relatórios de erros
            if (currentStep == OnboardingStep.CRASH_REPORT_CONSENT) {
                CrashlyticsConsentDialog(
                    initialEnabled = true,
                    onComplete = { enabled ->
                        onCrashlyticsToggle(enabled)
                        currentStep = OnboardingStep.COMPLETED
                        onboardingManager.markOnboardingCompleted()
                        onComplete()
                    }
                )
            }

            // Janela de carregamento ao restaurar backup local
            if (isRestoringLocalBackup) {
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
                                text = stringResource(R.string.onboarding_restore_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // Diálogo de Descriptografia para o Onboarding
            if (showDecryptionDialog) {
                if (showDecryptionDialog && decryptionBackupUri != null) {
                    var decryptionPassword by remember { mutableStateOf("") }
                    var decryptionError by remember { mutableStateOf<String?>(null) }

                    AlertDialog(
                        onDismissRequest = onDismissDecryption,
                        title = { Text(stringResource(id = R.string.decryption_dialog_title)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(id = R.string.decryption_dialog_message))
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.material3.OutlinedTextField(
                                    value = decryptionPassword,
                                    onValueChange = {
                                        decryptionPassword = it
                                        decryptionError = null
                                    },
                                    label = { Text(stringResource(id = R.string.encryption_password_placeholder)) },
                                    singleLine = true,
                                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (decryptionError != null) {
                                    Text(
                                        text = decryptionError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (decryptionErrorMessage != null) {
                                    Text(
                                        text = decryptionErrorMessage,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (decryptionPassword.isEmpty()) {
                                        decryptionError = "A senha não pode ser vazia!"
                                    } else {
                                        onConfirmDecryption(
                                            decryptionPassword,
                                            decryptionBackupUri,
                                            decryptionCloudFileId,
                                            decryptionCloudFileName
                                        )
                                    }
                                }
                            ) {
                                Text(stringResource(id = R.string.confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = onDismissDecryption) {
                                Text(stringResource(id = R.string.cancel))
                            }
                        }
                    )
                }
            }
        }
    }
}
