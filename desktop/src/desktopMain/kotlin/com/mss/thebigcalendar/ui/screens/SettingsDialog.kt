package com.mss.thebigcalendar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.mss.thebigcalendar.data.model.Theme

@Composable
fun SettingsDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    currentTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    pureBlackTheme: Boolean,
    onPureBlackThemeChange: (Boolean) -> Unit,
    welcomeName: String,
    onWelcomeNameChange: (String) -> Unit,
    googleEmail: String?,
    onGoogleConnect: () -> Unit,
    onGoogleDisconnect: () -> Unit,
    isSyncing: Boolean,
    onManualSync: () -> Unit,
    isEncryptionEnabled: Boolean,
    onEncryptionToggle: (Boolean, String) -> Unit
) {
    if (!show) return

    var activeTab by remember { mutableStateOf("Geral") }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .width(1400.dp)
                .height(800.dp)
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Painel de Navegação Esquerdo (Categorias)
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = "Configurações",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 16.dp)
                    )

                    val tabs = listOf(
                        Triple("Geral", "Geral", Icons.Outlined.Settings),
                        Triple("Visualização", "Visualização", Icons.Outlined.Visibility),
                        Triple("Sincronização", "Sincronização", Icons.Outlined.Sync)
                    )

                    tabs.forEach { (tabKey, tabLabel, icon) ->
                        val isSelected = activeTab == tabKey
                        NavigationRow(
                            label = tabLabel,
                            icon = icon,
                            isSelected = isSelected,
                            onClick = { activeTab = tabKey }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Botão Fechar
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fechar")
                    }
                }

                // Divisor Vertical
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                // Painel de Conteúdo Direito
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(32.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = activeTab,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    when (activeTab) {
                        "Geral" -> {
                            TabGeralContent(
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange,
                                pureBlackTheme = pureBlackTheme,
                                onPureBlackThemeChange = onPureBlackThemeChange
                            )
                        }
                        "Visualização" -> {
                            TabVisualizacaoContent(
                                welcomeName = welcomeName,
                                onWelcomeNameChange = onWelcomeNameChange
                            )
                        }
                        "Sincronização" -> {
                            TabSincronizacaoContent(
                                googleEmail = googleEmail,
                                onGoogleConnect = onGoogleConnect,
                                onGoogleDisconnect = onGoogleDisconnect,
                                isSyncing = isSyncing,
                                onManualSync = onManualSync,
                                isEncryptionEnabled = isEncryptionEnabled,
                                onEncryptionToggle = onEncryptionToggle
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TabGeralContent(
    currentTheme: Theme,
    onThemeChange: (Theme) -> Unit,
    pureBlackTheme: Boolean,
    onPureBlackThemeChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Tema do Aplicativo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Theme.LIGHT to "Claro",
                Theme.DARK to "Escuro",
                Theme.SYSTEM to "Sistema"
            ).forEach { (theme, label) ->
                val isSelected = currentTheme == theme
                OutlinedButton(
                    onClick = { onThemeChange(theme) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(label)
                }
            }
        }

        if (currentTheme == Theme.DARK || currentTheme == Theme.SYSTEM) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Preto Puro (AMOLED)", style = MaterialTheme.typography.bodyLarge)
                    Text("Desativa o cinza escuro em prol do preto absoluto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = pureBlackTheme, onCheckedChange = onPureBlackThemeChange)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Importação de Dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedButton(
            onClick = { /* Implementação de importar JSON */ },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Importar Arquivo de Eventos (JSON)")
        }
    }
}

@Composable
private fun TabVisualizacaoContent(
    welcomeName: String,
    onWelcomeNameChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Personalização da Visualização", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = welcomeName,
            onValueChange = onWelcomeNameChange,
            label = { Text("Nome de Boas-Vindas") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Ocultar dias de outros meses", style = MaterialTheme.typography.bodyLarge)
                Text("Não exibe os dias de preenchimento na grade mensal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var hideOtherMonths by remember { mutableStateOf(false) }
            Switch(checked = hideOtherMonths, onCheckedChange = { hideOtherMonths = it })
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Mostrar fases da lua", style = MaterialTheme.typography.bodyLarge)
                Text("Exibe a fase lunar correspondente em cada dia", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var showMoon by remember { mutableStateOf(false) }
            Switch(checked = showMoon, onCheckedChange = { showMoon = it })
        }
    }
}

@Composable
private fun TabSincronizacaoContent(
    googleEmail: String?,
    onGoogleConnect: () -> Unit,
    onGoogleDisconnect: () -> Unit,
    isSyncing: Boolean,
    onManualSync: () -> Unit,
    isEncryptionEnabled: Boolean,
    onEncryptionToggle: (Boolean, String) -> Unit
) {
    var showPasswordSetupDialog by remember { mutableStateOf(false) }
    var showDisableConfirmationDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Conta Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (googleEmail != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Conectado como", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(googleEmail, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onGoogleDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Text("Desconectar")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Criptografia de Dados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Mais informações sobre criptografia de dados",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = "Protege seus backups e dados de sincronização com criptografia de ponta a ponta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Criptografar Dados na Nuvem", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = isEncryptionEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showPasswordSetupDialog = true
                        } else {
                            showDisableConfirmationDialog = true
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("Sincronismo Manual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = onManualSync,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizando...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronizar Agora")
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Sua conta não está conectada ao Google Drive.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onGoogleConnect) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Conectar Conta Google")
                }
            }
        }
    }

    if (showPasswordSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showPasswordSetupDialog = false
                passwordInput = ""
                confirmPasswordInput = ""
                passwordError = null
            },
            title = { Text("Configurar Criptografia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ATENÇÃO: A criptografia é zero-knowledge. Seus dados serão criptografados localmente antes do envio. Para restaurar os backups ou sincronizar outros dispositivos, você DEVE lembrar desta senha. Não há como recuperar os dados se você esquecer a senha.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { 
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text("Senha de Criptografia") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { 
                            confirmPasswordInput = it
                            passwordError = null
                        },
                        label = { Text("Confirmar Senha") },
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
                TextButton(
                    onClick = {
                        if (passwordInput.length < 4) {
                            passwordError = "A senha deve ter pelo menos 4 caracteres."
                        } else if (passwordInput != confirmPasswordInput) {
                            passwordError = "As senhas não coincidem."
                        } else {
                            onEncryptionToggle(true, passwordInput)
                            showPasswordSetupDialog = false
                            passwordInput = ""
                            confirmPasswordInput = ""
                            passwordError = null
                        }
                    }
                ) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPasswordSetupDialog = false
                        passwordInput = ""
                        confirmPasswordInput = ""
                        passwordError = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDisableConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDisableConfirmationDialog = false },
            title = { Text("Desativar Criptografia?") },
            text = {
                Text("Seus dados na nuvem serão enviados sem criptografia a partir de agora. Os backups criptografados existentes ainda exigirão a senha original para restauração.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEncryptionToggle(false, "")
                        showDisableConfirmationDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Desativar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirmationDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
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
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Entendi")
                }
            }
        )
    }
}

