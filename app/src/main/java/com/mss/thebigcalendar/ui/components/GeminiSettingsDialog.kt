package com.mss.thebigcalendar.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mss.thebigcalendar.R

@Composable
fun GeminiSettingsDialog(
    currentApiKey: String,
    currentVoiceFeedback: Boolean,
    currentModel: String,
    onSaveSettings: (apiKey: String, voiceFeedback: Boolean, model: String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var voiceFeedback by remember(currentVoiceFeedback) { mutableStateOf(currentVoiceFeedback) }
    var selectedModel by remember(currentModel) {
        mutableStateOf(
            if (currentModel.isBlank()) {
                "gemini-3.6-flash"
            } else {
                currentModel
            }
        )
    }

    val availableModels = listOf(
        "gemini-2.5-flash" to "Gemini 2.5 Flash (Recomendado)",
        "gemini-2.0-flash" to "Gemini 2.0 Flash (Rápido)",
        "gemini-1.5-flash" to "Gemini 1.5 Flash (Alta Disponibilidade)",
        "gemini-1.5-pro" to "Gemini 1.5 Pro",
        "gemini-3.8-flash" to "Gemini 3.8 Flash (Experimental)",
        "gemini-3.7-flash" to "Gemini 3.7 Flash (Experimental)",
        "gemini-3.6-flash" to "Gemini 3.6 Flash (Experimental)",
        "gemini-3.6-pro" to "Gemini 3.6 Pro (Experimental)"
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(id = R.string.gemini_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.gemini_settings_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Campo da Chave de API
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.trim() },
                    label = { Text(stringResource(id = R.string.gemini_api_key)) },
                    placeholder = { Text(stringResource(id = R.string.gemini_api_key_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Botão para obter chave no AI Studio
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val url = "https://aistudio.google.com/app/apikey"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.gemini_get_api_key),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Resposta por voz (TTS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { voiceFeedback = !voiceFeedback }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.gemini_voice_feedback),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(id = R.string.gemini_voice_feedback_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = voiceFeedback,
                        onCheckedChange = { voiceFeedback = it }
                    )
                }

                // Seleção de Modelo
                Column {
                    Text(
                        text = stringResource(id = R.string.gemini_model),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "Se o modelo configurado sofrer alta demanda temporária no Google (HTTP 503), o assistente alternará automaticamente para modelos de contingência (ex: Gemini 3.8 Flash ou 3.7 Flash) para não falhar a criação da tarefa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    availableModels.forEach { (modelId, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedModel = modelId }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = (selectedModel == modelId),
                                onClick = { selectedModel = modelId }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveSettings(
                        apiKey.trim().trim('"', '\'', ' '),
                        voiceFeedback,
                        selectedModel.trim().removePrefix("models/")
                    )
                    onDismissRequest()
                }
            ) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
