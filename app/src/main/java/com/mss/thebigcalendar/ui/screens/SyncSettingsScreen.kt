package com.mss.thebigcalendar.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.SyncedDevice
import androidx.compose.ui.res.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    googleAccount: GoogleSignInAccount?,
    onSignInClicked: () -> Unit,
    onSignOutClicked: () -> Unit,
    isSyncing: Boolean,
    onManualSync: () -> Unit,
    syncProgress: com.mss.thebigcalendar.data.model.SyncProgress?,
    isCrashlyticsEnabled: Boolean,
    onCrashlyticsToggle: (Boolean) -> Unit,
    isEncryptionEnabled: Boolean,
    onEncryptionToggle: (Boolean, String) -> Unit,
    onBackClick: () -> Unit,
    unfixHeadersOnScroll: Boolean,
    syncedDevices: List<SyncedDevice>
) {
    Log.d("SyncSettingsScreen", "📱 SyncSettingsScreen iniciada")

    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDisableConfirmDialog by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showCrashlyticsInfoDialog by remember { mutableStateOf(false) }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = if (unfixHeadersOnScroll) {
        TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    } else {
        null
    }

    val transitionFraction = scrollBehavior?.state?.let { state ->
        maxOf(state.collapsedFraction, state.overlappedFraction)
    } ?: 0f

    val appBarContainerColor = if (MaterialTheme.colorScheme.surface == Color.Black) {
        Color.Black
    } else {
        lerp(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.surface,
            transitionFraction
        )
    }

    val appBarContentColor = if (MaterialTheme.colorScheme.surface == Color.Black) {
        MaterialTheme.colorScheme.onSurface
    } else {
        lerp(
            MaterialTheme.colorScheme.onPrimary,
            MaterialTheme.colorScheme.onSurface,
            transitionFraction
        )
    }

    Scaffold(
        modifier = if (scrollBehavior != null) {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        } else {
            Modifier
        },
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(id = R.string.synchronization)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appBarContainerColor,
                    scrolledContainerColor = appBarContainerColor,
                    titleContentColor = appBarContentColor,
                    navigationIconContentColor = appBarContentColor,
                    actionIconContentColor = appBarContentColor
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Conta Google
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.google_account),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                if (googleAccount != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(googleAccount.email ?: "", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onSignOutClicked) {
                            Text(stringResource(id = R.string.disconnect))
                        }
                    }
                } else {
                    Button(onClick = onSignInClicked) {
                        Text(stringResource(id = R.string.connect))
                    }
                }
            }

            // Botão de sincronização manual (só aparece quando conectado)
            if (googleAccount != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.synchronization),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onManualSync,
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.syncing))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = stringResource(id = R.string.sync_content_description),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.sync_now))
                        }
                    }
                }

                // Mostrar progresso detalhado se disponível
                if (isSyncing && syncProgress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = syncProgress.currentStep,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { syncProgress.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (syncProgress.totalEvents > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(id = R.string.sync_progress_format, syncProgress.processedEvents, syncProgress.totalEvents),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Seção de Dispositivos Sincronizados
            Text(
                text = "Dispositivos Sincronizados",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val platforms = listOf(
                Triple("windows", "Windows Desktop", R.drawable.ic_windows),
                Triple("linux", "Linux Desktop", R.drawable.ic_linux),
                Triple("wearos", "WearOS Smartwatch", R.drawable.ic_wearos)
            )

            platforms.forEach { (platformKey, platformName, iconRes) ->
                val activeDevice = syncedDevices.filter { it.platform == platformKey }
                    .maxByOrNull { it.lastSyncTime }
                
                val isConnected = activeDevice != null && (System.currentTimeMillis() - activeDevice.lastSyncTime < 30L * 24L * 60L * 60L * 1000L)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = platformName,
                        modifier = Modifier.size(32.dp),
                        tint = if (isConnected) {
                            when (platformKey) {
                                "windows" -> Color(0xFF0078D7)
                                "linux" -> Color.Unspecified
                                "wearos" -> Color(0xFF4285F4)
                                else -> MaterialTheme.colorScheme.primary
                            }
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = platformName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isConnected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (isConnected && activeDevice != null) {
                            Text(
                                text = activeDevice.deviceName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date(activeDevice.lastSyncTime))
                            Text(
                                text = "Última sincronização: $dateStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Não conectado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // Criptografia de Dados (Zero-Knowledge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable {
                        if (isEncryptionEnabled) {
                            showDisableConfirmDialog = true
                        } else {
                            showPasswordDialog = true
                            password = ""
                            confirmPassword = ""
                            passwordError = null
                        }
                    }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.encryption_setting_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Mais informações sobre criptografia de dados",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.encryption_setting_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isEncryptionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showPasswordDialog = true
                            password = ""
                            confirmPassword = ""
                            passwordError = null
                        } else {
                            showDisableConfirmDialog = true
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showInfoDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showInfoDialog = false },
                    title = { Text("Sobre a Criptografia de Dados") },
                    text = {
                        Text(
                            text = "A criptografia de dados protege seus compromissos e tarefas contra acessos não autorizados.\n\n" +
                                    "• Privacidade Absoluta (Zero-Knowledge): Seus dados são criptografados diretamente no seu dispositivo antes de serem enviados para a nuvem. Isso significa que apenas você, com a sua senha, pode descriptografá-los.\n\n" +
                                    "• Algoritmo de Alta Segurança: Utilizamos o padrão AES com chaves de 256 bits geradas a partir de sua senha usando derivação robusta (PBKDF2). Nem mesmo o Google ou os desenvolvedores do app podem acessar suas informações.\n\n" +
                                    "• Proteção de Backups: Seus arquivos de backup locais salvos no armazenamento do aparelho também ficam totalmente protegidos contra leituras por outros aplicativos.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(onClick = { showInfoDialog = false }) {
                            Text("Entendi")
                        }
                    }
                )
            }

            if (showPasswordDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showPasswordDialog = false },
                    title = { Text(stringResource(id = R.string.encryption_dialog_title)) },
                    text = {
                        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(id = R.string.encryption_dialog_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = password,
                                onValueChange = { 
                                    password = it
                                    passwordError = null
                                },
                                label = { Text(stringResource(id = R.string.encryption_password_placeholder)) },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            androidx.compose.material3.OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { 
                                    confirmPassword = it
                                    passwordError = null
                                },
                                label = { Text(stringResource(id = R.string.encryption_confirm_password_placeholder)) },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (passwordError != null) {
                                Text(
                                    text = passwordError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (password.isEmpty()) {
                                    passwordError = "A senha não pode ser vazia!"
                                } else if (password != confirmPassword) {
                                    passwordError = "As senhas não coincidem!"
                                } else {
                                    onEncryptionToggle(true, password)
                                    showPasswordDialog = false
                                }
                            }
                        ) {
                            Text(stringResource(id = R.string.confirm))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showPasswordDialog = false }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }

            if (showDisableConfirmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDisableConfirmDialog = false },
                    title = { Text(stringResource(id = R.string.encryption_disable_title)) },
                    text = { Text(stringResource(id = R.string.encryption_disable_message)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                onEncryptionToggle(false, "")
                                showDisableConfirmDialog = false
                            }
                        ) {
                            Text(stringResource(id = R.string.confirm))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDisableConfirmDialog = false }) {
                            Text(stringResource(id = R.string.cancel))
                        }
                    }
                )
            }

            // Envio de Relatório de Erros (Crashlytics Opt-in)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onCrashlyticsToggle(!isCrashlyticsEnabled) }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.crashlytics_setting_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showCrashlyticsInfoDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Mais informações sobre relatórios de erros",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(id = R.string.crashlytics_setting_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isCrashlyticsEnabled,
                    onCheckedChange = onCrashlyticsToggle
                )
            }

            if (showCrashlyticsInfoDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCrashlyticsInfoDialog = false },
                    title = { Text("Sobre os Relatórios de Erros") },
                    text = {
                        Text(
                            text = "Os relatórios de erros nos ajudam a identificar e corrigir travamentos no aplicativo de forma automática e rápida.\n\n" +
                                    "• Anonimato Completo: Não coletamos nenhuma informação pessoal identificável, como seu nome, e-mail, tarefas ou compromissos. Apenas dados de diagnóstico do sistema são enviados.\n\n" +
                                    "• Informações Técnicas: São enviados detalhes de hardware (modelo do aparelho, versão do Android) e rastreamento de pilha (stack trace) da falha ocorrida.\n\n" +
                                    "• Melhoria Contínua: Com esses dados, podemos corrigir bugs e instabilidades antes mesmo que afetem outros usuários.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(onClick = { showCrashlyticsInfoDialog = false }) {
                            Text("Entendi")
                        }
                    }
                )
            }
        }
    }
}
