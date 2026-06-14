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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.mss.thebigcalendar.R

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
    onBackClick: () -> Unit,
    unfixHeadersOnScroll: Boolean
) {
    Log.d("SyncSettingsScreen", "📱 SyncSettingsScreen iniciada")

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

            Spacer(modifier = Modifier.height(16.dp))

            // Envio de Relatório de Erros (Crashlytics Opt-in)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .clickable { onCrashlyticsToggle(!isCrashlyticsEnabled) }
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.crashlytics_setting_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
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
        }
    }
}
