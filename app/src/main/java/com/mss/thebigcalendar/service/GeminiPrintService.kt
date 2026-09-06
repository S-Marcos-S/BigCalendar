package com.mss.thebigcalendar.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mss.thebigcalendar.data.model.CalendarAiTemplateSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

sealed class GeminiPrintResult {
    data class Success(val template: CalendarAiTemplateSpec, val replyMessage: String) : GeminiPrintResult()
    data class Error(
        val message: String,
        val details: String? = null,
        val isApiKeyError: Boolean = false
    ) : GeminiPrintResult()
}

class GeminiPrintService {

    companion object {
        private const val TAG = "GeminiPrintService"
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }

    private val gson = Gson()

    suspend fun generateOrModifyTemplate(
        prompt: String,
        referenceImages: List<Bitmap>? = null,
        currentTemplate: CalendarAiTemplateSpec? = null,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        userLocale: Locale = Locale.getDefault()
    ): GeminiPrintResult = withContext(Dispatchers.IO) {
        val cleanApiKey = apiKey.trim().trim('"', '\'', ' ')
        if (cleanApiKey.isBlank()) {
            return@withContext GeminiPrintResult.Error(
                message = "Chave de API do Gemini não configurada.",
                details = "Nenhuma chave foi informada nas configurações do assistente.\nAbra as Configurações do Assistente Gemini no Big Calendar e informe sua chave de API.",
                isApiKeyError = true
            )
        }

        val primaryModel = (if (model.isBlank()) DEFAULT_MODEL else model.trim()).removePrefix("models/")
        val attemptsLog = mutableListOf<String>()

        try {
            val systemInstruction = buildSystemInstruction(userLocale)
            val userPromptText = buildUserPrompt(prompt, currentTemplate)
            val base64Images = referenceImages?.mapNotNull { encodeBitmapToBase64(it) } ?: emptyList()

            val jsonRequestBody = buildRequestBody(userPromptText, base64Images, systemInstruction, jsonMimeType = true)
            val textRequestBody = buildRequestBody(userPromptText, base64Images, systemInstruction, jsonMimeType = false)

            val candidateModels = linkedSetOf<String>()
            candidateModels.add(primaryModel)
            listOf(
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-pro",
                "gemini-2.5-pro",
                "gemini-3.8-flash",
                "gemini-3.7-flash",
                "gemini-3.6-flash",
                "gemini-3.6-pro"
            ).forEach { candidateModels.add(it) }

            val modelQueue = java.util.ArrayDeque(candidateModels)
            val triedModels = mutableSetOf<String>()

            var lastCode = -1
            var lastBody = ""

            while (modelQueue.isNotEmpty()) {
                val currentModel = modelQueue.removeFirst()
                if (currentModel in triedModels) continue
                triedModels.add(currentModel)

                val isFallback = currentModel != primaryModel
                val fallbackSuffix = if (isFallback) " (fallback automático)" else ""

                attemptsLog.add("POST v1beta/models/$currentModel$fallbackSuffix")
                val (code, body) = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:generateContent?key=$cleanApiKey",
                    jsonRequestBody
                )
                lastCode = code
                lastBody = body

                if (code == -1 && body.startsWith("NO_INTERNET")) {
                    return@withContext GeminiPrintResult.Error(
                        message = "Sem conexão com a internet para contatar o Gemini.",
                        details = buildTechnicalDetails(primaryModel, -1, "Falha de conexão com a internet / DNS indisponível.\n$body", attemptsLog)
                    )
                }

                val isExplicitKeyError = code == 403 ||
                        body.contains("API_KEY_INVALID", ignoreCase = true) ||
                        body.contains("key not valid", ignoreCase = true) ||
                        body.contains("API key not valid", ignoreCase = true)

                if (isExplicitKeyError) {
                    return@withContext GeminiPrintResult.Error(
                        message = "Chave da API Gemini inválida ou sem permissão.",
                        details = buildTechnicalDetails(primaryModel, code, body, attemptsLog),
                        isApiKeyError = true
                    )
                }

                if (code == HttpURLConnection.HTTP_OK) {
                    val parsedResult = parseGeminiResponse(body)
                    if (parsedResult != null) {
                        return@withContext GeminiPrintResult.Success(parsedResult.first, parsedResult.second)
                    } else {
                        Log.w(TAG, "HTTP 200 para $currentModel, mas parsing do modelo de calendário falhou.")
                        attemptsLog.add("Resposta recebida de $currentModel não continha JSON de especificação válido.")
                    }
                }

                if (code == 400) {
                    Log.w(TAG, "Tentando payload sem response_mime_type para $currentModel...")
                    attemptsLog.add("POST v1beta/models/$currentModel (sem response_mime_type)")
                    val retry = tryPostGenerateContent(
                        "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:generateContent?key=$cleanApiKey",
                        textRequestBody
                    )
                    lastCode = retry.first
                    lastBody = retry.second
                    if (retry.first == HttpURLConnection.HTTP_OK) {
                        val parsedResult = parseGeminiResponse(retry.second)
                        if (parsedResult != null) {
                            return@withContext GeminiPrintResult.Success(parsedResult.first, parsedResult.second)
                        }
                    }
                }

                if (code == 404) {
                    attemptsLog.add("POST v1/models/$currentModel")
                    val v1Result = tryPostGenerateContent(
                        "https://generativelanguage.googleapis.com/v1/models/$currentModel:generateContent?key=$cleanApiKey",
                        jsonRequestBody
                    )
                    lastCode = v1Result.first
                    lastBody = v1Result.second
                    if (v1Result.first == HttpURLConnection.HTTP_OK) {
                        val parsedResult = parseGeminiResponse(v1Result.second)
                        if (parsedResult != null) {
                            return@withContext GeminiPrintResult.Success(parsedResult.first, parsedResult.second)
                        }
                    }
                }
            }

            Log.e(TAG, "Todas as alternativas de modelos falharam ($lastCode): $lastBody")
            val userMessage = extractErrorMessage(lastBody, lastCode)

            GeminiPrintResult.Error(
                message = userMessage,
                details = buildTechnicalDetails(primaryModel, lastCode, lastBody, attemptsLog),
                isApiKeyError = false
            )
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Sem conexão com a internet", e)
            GeminiPrintResult.Error(
                message = "Sem conexão com a internet para contatar o Gemini.",
                details = buildTechnicalDetails(primaryModel, -1, "Falha de DNS ao conectar aos servidores do Google: ${e.message}", attemptsLog)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao gerar modelo com Gemini", e)
            GeminiPrintResult.Error(
                message = "Erro ao processar criação de modelo: ${e.localizedMessage ?: e.message}",
                details = buildTechnicalDetails(primaryModel, -1, "${e.javaClass.name}: ${e.message}\n${e.stackTraceToString().take(600)}", attemptsLog)
            )
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val maxDimension = 1024
            val width = bitmap.width
            val height = bitmap.height
            val scaledBitmap = if (width > maxDimension || height > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
                val newW = (width * ratio).toInt()
                val newH = (height * ratio).toInt()
                Bitmap.createScaledBitmap(bitmap, newW, newH, true)
            } else {
                bitmap
            }

            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val byteArray = stream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao codificar bitmap para base64", e)
            null
        }
    }

    private fun buildUserPrompt(userPrompt: String, currentTemplate: CalendarAiTemplateSpec?): String {
        val sb = StringBuilder()
        if (currentTemplate != null) {
            sb.append("MODELO ATUAL DE BASE (JSON):\n")
            sb.append(currentTemplate.toJson())
            sb.append("\n\nSOLICITAÇÃO DE MODIFICAÇÃO DO USUÁRIO:\n")
            sb.append(userPrompt.ifBlank { "Ajuste o modelo acima com base na(s) imagem(ns) enviada(s)." })
        } else {
            sb.append("SOLICITAÇÃO DE NOVO MODELO DE CALENDÁRIO:\n")
            sb.append(userPrompt.ifBlank { "Crie um modelo de calendário idêntico ou inspirado no estilo visual da(s) imagem(ns) anexada(s)." })
        }
        return sb.toString()
    }

    private fun buildSystemInstruction(locale: Locale): String {
        return """
Você é um Designer Especialista em Calendários e Planners para o aplicativo The Big Calendar.
Sua missão é gerar ou modificar especificações técnicas de design de calendários (CalendarAiTemplateSpec) para impressão em alta qualidade (PDF vetorial).

Ao receber uma solicitação de texto e/ou imagens/fotos de modelos de calendário de referência:
1. Analise minuciosamente as cores (fundo da página, barra lateral, cabeçalho, dias, acentos de fim de semana e feriados).
2. Analise a arquitetura de layout:
   - "STANDARD_GRID": Grade completa de 7 colunas ocupando a página.
   - "SIDEBAR_LEFT": Barra lateral esquerda com anotações/metas + grade de dias à direita.
   - "SIDEBAR_RIGHT": Grade de dias à esquerda + barra lateral direita com anotações/rastreador.
   - "TOP_BOTTOM_SPLIT": Banner superior com título/metas + grade central + rodapé com notas/hábitos.
   - "MINIMALIST_CLEAN": Estilo super limpo e espaçoso.
   - "PLANNER_BULLET": Estilo bullet journal com pontilhados e rastreadores.
3. Seções adicionais solicitadas ou presentes na imagem:
   - Metas do Mês ("goalsSection"): enabled, title, itemsCount, boxBackgroundColor.
   - Rastreador de Hábitos ("habitTracker"): enabled, title, habits (lista de strings), style ("MONTHLY_DOTS" ou "WEEKLY_CHECKBOXES").
   - Seção de Anotações ("notesSection"): enabled, title, style ("LINED", "DOTTED", "CHECKLIST", "GRID", "BLANK"), position ("SIDEBAR", "BOTTOM", "TOP"), linesCount.
   - Mini Calendários ("miniCalendar"): enabled, showPreviousMonth, showNextMonth.
4. Tipografia e Cores:
   - Cores DEVEM ser códigos hexadecimais válidos (#RRGGBB).
   - "fontFamily": "DEFAULT", "SERIF", "SANS", "CURSIVE" (para estilos caligráficos/manuscritos/femininos), "MONO".
   - Ajuste contrastes para garantir legibilidade perfeita na impressão.

ESTRUTURA JSON DE SAÍDA OBRIGATÓRIA:
Retorne EXCLUSIVAMENTE um objeto JSON no seguinte formato:
{
  "replyMessage": "Breve explicação amigável do modelo criado ou das alterações feitas (em ${locale.displayLanguage}).",
  "template": {
    "name": "Nome criativo para o modelo (ex: 'Planner Rosé', 'Lavanda Floral', 'Clean Minimalista')",
    "description": "Breve descrição visual do modelo",
    "layoutType": "STANDARD_GRID" | "SIDEBAR_LEFT" | "SIDEBAR_RIGHT" | "TOP_BOTTOM_SPLIT" | "MINIMALIST_CLEAN" | "PLANNER_BULLET",
    "isLandscape": true | false,
    "pageSize": "A4" | "A3" | "LETTER",
    "weekStartsOnMonday": true | false,
    "sidebarWidthPercent": 26.0,
    "pageBackgroundColor": "#FFFFFF",
    "headerBackgroundColor": "#F5F5F5" | null,
    "sidebarBackgroundColor": "#F0F0F0" | null,
    "gridBackgroundColor": "#FFFFFF",
    "primaryColor": "#212121",
    "secondaryColor": "#757575",
    "weekdayHeaderBackgroundColor": "#E0E0E0" | null,
    "weekdayTextColor": "#424242",
    "dayNumberColor": "#212121",
    "weekendColor": "#D32F2F" | null,
    "holidayColor": "#C62828",
    "gridBorderColor": "#E0E0E0",
    "gridBorderWidth": 0.75,
    "dayCellCornerRadius": 4.0,
    "dayCellHeight": 60.0,
    "showLinesInDayCells": false,
    "linesInDayCellsCount": 3,
    "fontFamily": "DEFAULT" | "SERIF" | "SANS" | "CURSIVE" | "MONO",
    "customFontFileName": "Redressed.ttf" | null,
    "monthTitleFontSize": 38.0,
    "monthTitleAlignment": "LEFT" | "CENTER" | "RIGHT",
    "monthTitleAllCaps": false,
    "yearFontSize": 16.0,
    "weekdayFontSize": 9.0,
    "weekdayFormat": "SHORT" | "FULL" | "SINGLE_LETTER",
    "dayNumberFontSize": 12.0,
    "notesSection": {
      "enabled": true | false,
      "title": "Anotações",
      "style": "LINED" | "DOTTED" | "CHECKLIST" | "GRID" | "BLANK",
      "position": "SIDEBAR" | "BOTTOM" | "TOP",
      "linesCount": 12
    },
    "goalsSection": {
      "enabled": true | false,
      "title": "Metas do Mês",
      "itemsCount": 3,
      "boxBackgroundColor": "#E8A598" | null
    },
    "habitTracker": {
      "enabled": true | false,
      "title": "Rastreador de Hábitos",
      "habits": ["Exercício", "Leitura", "Água", "Estudo"],
      "style": "MONTHLY_DOTS" | "WEEKLY_CHECKBOXES"
    },
    "miniCalendar": {
      "enabled": false,
      "showPreviousMonth": true,
      "showNextMonth": true
    },
    "moonPhases": "IN_DAY_CELLS" | "BOTTOM_LEGEND" | "SIDEBAR" | "NONE",
    "motivationalQuote": "Frase inspiradora ou null",
    "eventDisplayDensity": "FULL_TEXT" | "COMPACT_BADGES" | "DOT_INDICATORS" | "BLANK_FOR_WRITING"
  }
}
""".trimIndent()
    }

    private fun buildRequestBody(
        prompt: String,
        base64Images: List<String>,
        systemInstruction: String,
        jsonMimeType: Boolean = true
    ): String {
        val root = JsonObject()

        // system_instruction
        val systemInstructionObj = JsonObject()
        val sysParts = com.google.gson.JsonArray()
        val sysPart = JsonObject()
        sysPart.addProperty("text", systemInstruction)
        sysParts.add(sysPart)
        systemInstructionObj.add("parts", sysParts)
        root.add("system_instruction", systemInstructionObj)

        // contents
        val contentsArray = com.google.gson.JsonArray()
        val contentObj = JsonObject()
        contentObj.addProperty("role", "user")
        val userParts = com.google.gson.JsonArray()

        // 1. Texto do prompt
        val textPart = JsonObject()
        textPart.addProperty("text", prompt)
        userParts.add(textPart)

        // 2. Imagens anexadas (se houver)
        for (imgBase64 in base64Images) {
            val imagePart = JsonObject()
            val inlineData = JsonObject()
            inlineData.addProperty("mime_type", "image/jpeg")
            inlineData.addProperty("data", imgBase64)
            imagePart.add("inline_data", inlineData)
            userParts.add(imagePart)
        }

        contentObj.add("parts", userParts)
        contentsArray.add(contentObj)
        root.add("contents", contentsArray)

        // generationConfig
        val genConfig = JsonObject()
        if (jsonMimeType) {
            genConfig.addProperty("response_mime_type", "application/json")
        }
        genConfig.addProperty("temperature", 0.3)
        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }

    private fun parseGeminiResponse(jsonString: String): Pair<CalendarAiTemplateSpec, String>? {
        return try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates") ?: return null
            if (candidates.size() == 0) return null

            val firstCandidate = candidates.get(0).asJsonObject
            val content = firstCandidate.getAsJsonObject("content") ?: return null
            val parts = content.getAsJsonArray("parts") ?: return null
            if (parts.size() == 0) return null

            var rawText: String? = null
            for (i in parts.size() - 1 downTo 0) {
                val p = parts.get(i).asJsonObject
                val isThought = p.get("thought")?.asBoolean ?: false
                if (!isThought && p.has("text")) {
                    val text = p.get("text")?.asString
                    if (!text.isNullOrBlank()) {
                        rawText = text
                        break
                    }
                }
            }

            if (rawText.isNullOrBlank()) return null

            val cleanJson = rawText.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsedObj = gson.fromJson(cleanJson, JsonObject::class.java)
            val replyMessage = parsedObj.get("replyMessage")?.asString ?: "Modelo gerado com sucesso!"

            val templateObj = if (parsedObj.has("template")) {
                parsedObj.getAsJsonObject("template")
            } else {
                parsedObj
            }

            val templateSpec = gson.fromJson(templateObj, CalendarAiTemplateSpec::class.java)
            if (templateSpec != null) {
                Pair(templateSpec, replyMessage)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao deserializar resposta do Gemini para CalendarAiTemplateSpec", e)
            null
        }
    }

    private fun tryPostGenerateContent(endpointUrl: String, requestBody: String): Pair<Int, String> {
        return try {
            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 40000
            }

            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            Pair(responseCode, responseBody)
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Timeout em tryPostGenerateContent: ${e.message}")
            Pair(-1, "TIMEOUT:${e.message ?: "Read timed out"}")
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Sem conexão/DNS: ${e.message}")
            Pair(-1, "NO_INTERNET:${e.message ?: "Host desconhecido"}")
        } catch (e: Exception) {
            Log.w(TAG, "Erro na requisição: ${e.message}")
            Pair(-1, "NETWORK_ERROR:${e.message ?: "Erro desconhecido"}")
        }
    }

    private fun extractErrorMessage(errorBody: String, defaultCode: Int): String {
        if (defaultCode == -1 || errorBody.startsWith("TIMEOUT:") || errorBody.startsWith("NO_INTERNET:")) {
            return "Falha de rede ao conectar com a IA Gemini. Verifique sua conexão com a internet."
        }
        if (defaultCode == 503) {
            return "O Gemini está enfrentando alta demanda temporária. Tente novamente em alguns instantes."
        }
        if (defaultCode == 429) {
            return "Limite de requisições excedido na API Gemini. Aguarde alguns instantes."
        }
        try {
            val root = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = root?.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString
            if (!message.isNullOrBlank()) return message
        } catch (_: Exception) {}
        return "Erro na comunicação com o Gemini (código $defaultCode)."
    }

    private fun buildTechnicalDetails(
        model: String,
        httpCode: Int,
        responseBody: String,
        attempts: List<String> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.append("Modelo solicitado: ").append(model).append("\n")
        sb.append("Status final: ").append(if (httpCode == -1) "Falha de Rede / Timeout" else "HTTP $httpCode").append("\n")
        if (attempts.isNotEmpty()) {
            sb.append("\nHistórico de tentativas:\n")
            attempts.forEach { sb.append("• ").append(it).append("\n") }
        }
        if (responseBody.isNotBlank()) {
            sb.append("\nResposta / Erro retornado:\n").append(responseBody.take(1500))
        }
        return sb.toString().trim()
    }
}
