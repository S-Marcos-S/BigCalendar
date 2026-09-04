package com.mss.thebigcalendar.service

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.GeminiCommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class GeminiExecutionResult {
    data class Success(val command: GeminiCommandResult) : GeminiExecutionResult()
    data class Error(
        val message: String,
        val details: String? = null,
        val isApiKeyError: Boolean = false
    ) : GeminiExecutionResult()
}

class GeminiService {

    companion object {
        private const val TAG = "GeminiService"
        const val DEFAULT_MODEL = "gemini-3.6-flash"
    }

    private val gson = Gson()

    suspend fun executeCommand(
        prompt: String,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        existingActivities: List<Activity> = emptyList(),
        userLocale: Locale = Locale.getDefault()
    ): GeminiExecutionResult = withContext(Dispatchers.IO) {
        val cleanApiKey = apiKey.trim().trim('"', '\'', ' ')
        if (cleanApiKey.isBlank()) {
            return@withContext GeminiExecutionResult.Error(
                message = "Chave de API do Gemini não configurada.",
                details = "Nenhuma chave foi informada nas configurações do assistente.\nAbra as Configurações do Assistente Gemini no Big Calendar e informe sua chave de API gerada no Google AI Studio.",
                isApiKeyError = true
            )
        }

        var cleanModel = (if (model.isBlank() || model.contains("2.5") || model.contains("1.5")) {
            DEFAULT_MODEL
        } else {
            model.trim()
        }).removePrefix("models/")

        val attemptsLog = mutableListOf<String>()

        try {
            val systemInstruction = buildSystemInstruction(existingActivities, userLocale)
            var requestBody = buildRequestBody(prompt, systemInstruction, jsonMimeType = true)

            // 1. Tentar com versão v1beta e o modelo configurado
            attemptsLog.add("POST v1beta/models/$cleanModel")
            var (code, body) = tryPostGenerateContent(
                "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$cleanApiKey",
                requestBody
            )

            // Se der erro 400 (ex: formato de resposta não suportado pelo modelo), retentar com payload simplificado sem response_mime_type
            val isExplicitKeyError = code == 403 ||
                    body.contains("API_KEY_INVALID", ignoreCase = true) ||
                    body.contains("key not valid", ignoreCase = true) ||
                    body.contains("API key not valid", ignoreCase = true) ||
                    body.contains("PERMISSION_DENIED", ignoreCase = true)

            if (code == 400 && !isExplicitKeyError) {
                Log.w(TAG, "Tentando payload simplificado para $cleanModel...")
                attemptsLog.add("POST v1beta/models/$cleanModel (sem response_mime_type)")
                requestBody = buildRequestBody(prompt, systemInstruction, jsonMimeType = false)
                val retry = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$cleanApiKey",
                    requestBody
                )
                if (retry.first == HttpURLConnection.HTTP_OK) {
                    code = retry.first
                    body = retry.second
                }
            }

            var isModelError = code == 404 ||
                    body.contains("no longer available", ignoreCase = true) ||
                    body.contains("not found for API version", ignoreCase = true) ||
                    body.contains("not supported for generateContent", ignoreCase = true) ||
                    (code == 400 && !isExplicitKeyError)

            // 2. Se for erro de modelo no v1beta, tentar v1
            if (isModelError && code != 400) {
                Log.w(TAG, "Tentando v1 para $cleanModel...")
                attemptsLog.add("POST v1/models/$cleanModel")
                val v1Result = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1/models/$cleanModel:generateContent?key=$cleanApiKey",
                    requestBody
                )
                if (v1Result.first == HttpURLConnection.HTTP_OK) {
                    code = v1Result.first
                    body = v1Result.second
                    isModelError = false
                }
            }

            // 3. Se o Google sugeriu um modelo específico no erro (ex: "Please update your code to use models/gemini-3.6-flash")
            if (isModelError) {
                val suggestedModel = """use models/([a-zA-Z0-9._-]+)""".toRegex(RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)
                    ?: """models/([a-zA-Z0-9._-]+)""".toRegex(RegexOption.IGNORE_CASE)
                        .findAll(body)
                        .map { it.groupValues[1] }
                        .firstOrNull { it != cleanModel && !body.contains("This model models/$it is no longer available", ignoreCase = true) }

                if (!suggestedModel.isNullOrBlank()) {
                    Log.i(TAG, "Google sugeriu modelo alternativo: $suggestedModel. Retentando...")
                    attemptsLog.add("POST v1beta/models/$suggestedModel (sugerido pelo Google)")
                    val retryResult = tryPostGenerateContent(
                        "https://generativelanguage.googleapis.com/v1beta/models/$suggestedModel:generateContent?key=$cleanApiKey",
                        requestBody
                    )
                    code = retryResult.first
                    body = retryResult.second
                    if (code == HttpURLConnection.HTTP_OK) {
                        isModelError = false
                    }
                }
            }

            // 4. Se deu erro de modelo e não usamos gemini-3.6-flash, tentar gemini-3.6-flash diretamente
            if (isModelError && cleanModel != DEFAULT_MODEL) {
                Log.i(TAG, "Tentando modelo padrão $DEFAULT_MODEL...")
                attemptsLog.add("POST v1beta/models/$DEFAULT_MODEL (fallback padrão)")
                val retryResult = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1beta/models/$DEFAULT_MODEL:generateContent?key=$cleanApiKey",
                    requestBody
                )
                code = retryResult.first
                body = retryResult.second
                if (code == HttpURLConnection.HTTP_OK) {
                    isModelError = false
                }
            }

            // 5. Se deu TIMEOUT ou erro de modelo e não usamos gemini-2.0-flash, tentar gemini-2.0-flash (respostas quase instantâneas)
            val isTimeout = code == -1 && body.startsWith("TIMEOUT")
            if ((isModelError || isTimeout) && cleanModel != "gemini-2.0-flash") {
                Log.i(TAG, "Tentando fallback rápido para gemini-2.0-flash...")
                attemptsLog.add("POST v1beta/models/gemini-2.0-flash (fallback ultra rápido)")
                val fastFallback = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$cleanApiKey",
                    requestBody
                )
                if (fastFallback.first == HttpURLConnection.HTTP_OK) {
                    code = fastFallback.first
                    body = fastFallback.second
                    isModelError = false
                }
            }

            // 6. Se ainda persistir erro de modelo, consultar dinamicamente os modelos habilitados para esta chave
            if (isModelError) {
                Log.w(TAG, "Consultando modelos disponíveis da chave via ListModels...")
                attemptsLog.add("ListModels (consulta dinâmica de modelos disponíveis)")
                val available = fetchAvailableModel(cleanApiKey)
                if (available != null) {
                    val (ver, availableModel) = available
                    Log.i(TAG, "Modelo compatível encontrado: $availableModel (versão $ver). Retentando requisição...")
                    attemptsLog.add("POST $ver/models/$availableModel")
                    val retryResult = tryPostGenerateContent(
                        "https://generativelanguage.googleapis.com/$ver/models/$availableModel:generateContent?key=$cleanApiKey",
                        requestBody
                    )
                    code = retryResult.first
                    body = retryResult.second
                }
            }

            Log.d(TAG, "Gemini final response code: $code")

            if (code == HttpURLConnection.HTTP_OK) {
                val parsedResult = parseGeminiResponse(body)
                if (parsedResult != null) {
                    GeminiExecutionResult.Success(parsedResult)
                } else {
                    GeminiExecutionResult.Error(
                        message = "Não foi possível interpretar a resposta gerada pelo Gemini.",
                        details = buildTechnicalDetails(
                            model = cleanModel,
                            httpCode = code,
                            responseBody = "O modelo respondeu com sucesso (HTTP 200), mas o conteúdo não pôde ser convertido no JSON de comando esperado.\n\nResposta recebida do Gemini:\n$body",
                            attempts = attemptsLog
                        )
                    )
                }
            } else {
                Log.e(TAG, "Gemini API error ($code): $body")

                val isKeyError = code == 403 ||
                        body.contains("API_KEY_INVALID", ignoreCase = true) ||
                        body.contains("key not valid", ignoreCase = true) ||
                        body.contains("API key not valid", ignoreCase = true) ||
                        body.contains("PERMISSION_DENIED", ignoreCase = true)

                val detailedMsg = extractErrorMessage(body, code)
                val userMessage = when {
                    isKeyError -> "Chave da API Gemini inválida ou sem permissão."
                    code == 429 -> "Limite de requisições excedido na API Gemini. Tente novamente em instantes."
                    code == -1 -> detailedMsg
                    else -> detailedMsg
                }

                val technicalDetails = buildTechnicalDetails(
                    model = cleanModel,
                    httpCode = code,
                    responseBody = body,
                    attempts = attemptsLog
                )

                GeminiExecutionResult.Error(
                    message = userMessage,
                    details = technicalDetails,
                    isApiKeyError = isKeyError
                )
            }
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Sem conexão com a internet", e)
            GeminiExecutionResult.Error(
                message = "Sem conexão com a internet para contatar o Gemini.",
                details = buildTechnicalDetails(
                    model = cleanModel,
                    httpCode = -1,
                    responseBody = "Falha de DNS ao tentar resolver 'generativelanguage.googleapis.com'.\nExceção: ${e.javaClass.simpleName}: ${e.message}\nVerifique sua conexão de rede (Wi-Fi ou dados móveis).",
                    attempts = attemptsLog
                )
            )
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Tempo limite esgotado", e)
            GeminiExecutionResult.Error(
                message = "Tempo limite esgotado ao aguardar o Gemini.",
                details = buildTechnicalDetails(
                    model = cleanModel,
                    httpCode = -1,
                    responseBody = "O servidor do Google demorou mais que o esperado (> 60 segundos) para responder à requisição.\nExceção: ${e.javaClass.simpleName}: ${e.message}",
                    attempts = attemptsLog
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao chamar Gemini", e)
            GeminiExecutionResult.Error(
                message = "Erro ao processar comando: ${e.localizedMessage ?: e.message}",
                details = buildTechnicalDetails(
                    model = cleanModel,
                    httpCode = -1,
                    responseBody = "Exceção inesperada: ${e.javaClass.name}: ${e.message}\n${e.stackTraceToString().take(600)}",
                    attempts = attemptsLog
                )
            )
        }
    }

    private fun tryPostGenerateContent(
        endpointUrl: String,
        requestBody: String
    ): Pair<Int, String> {
        return try {
            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 25000
                readTimeout = 60000
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
            Log.w(TAG, "Timeout em tryPostGenerateContent para $endpointUrl: ${e.message}")
            Pair(-1, "TIMEOUT:${e.message ?: "Read timed out"}")
        } catch (e: java.net.UnknownHostException) {
            Log.w(TAG, "Sem DNS/Internet em tryPostGenerateContent: ${e.message}")
            Pair(-1, "NO_INTERNET:${e.message ?: "Host desconhecido"}")
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "Falha de conexão em tryPostGenerateContent: ${e.message}")
            Pair(-1, "CONNECTION_FAILED:${e.message ?: "Falha ao conectar"}")
        } catch (e: Exception) {
            Log.w(TAG, "Exceção em tryPostGenerateContent para $endpointUrl", e)
            Pair(-1, "NETWORK_ERROR:${e.message ?: "Erro desconhecido"}")
        }
    }

    private fun fetchAvailableModel(apiKey: String): Pair<String, String>? {
        val versions = listOf("v1beta", "v1")
        for (ver in versions) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/$ver/models?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 12000
                    readTimeout = 15000
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = gson.fromJson(body, JsonObject::class.java)
                    val models = root.getAsJsonArray("models") ?: continue
                    val candidates = mutableListOf<String>()
                    for (i in 0 until models.size()) {
                        val m = models.get(i).asJsonObject
                        val name = m.get("name")?.asString ?: continue
                        val cleanName = name.removePrefix("models/")
                        val methods = m.getAsJsonArray("supportedGenerationMethods")?.map { it.asString } ?: emptyList()
                        if (methods.contains("generateContent")) {
                            candidates.add(cleanName)
                        }
                    }
                    val selected = candidates.firstOrNull { it == "gemini-3.6-flash" }
                        ?: candidates.firstOrNull { it.contains("3.6-flash") }
                        ?: candidates.firstOrNull { it.contains("3.") && it.contains("flash") }
                        ?: candidates.firstOrNull { it == "gemini-2.0-flash" }
                        ?: candidates.firstOrNull { it.contains("flash") && !it.contains("2.5") && !it.contains("1.5") }
                        ?: candidates.firstOrNull { it.contains("flash") }
                        ?: candidates.firstOrNull { it.contains("pro") }
                        ?: candidates.firstOrNull()
                    if (selected != null) {
                        return Pair(ver, selected)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao listar modelos em $ver", e)
            }
        }
        return null
    }

    private fun extractErrorMessage(errorBody: String, defaultCode: Int): String {
        if (defaultCode == -1 || errorBody.startsWith("TIMEOUT:") || errorBody.startsWith("NO_INTERNET:") ||
            errorBody.startsWith("CONNECTION_FAILED:") || errorBody.startsWith("NETWORK_ERROR:")) {
            return when {
                errorBody.contains("TIMEOUT", ignoreCase = true) ->
                    "Tempo limite esgotado ao aguardar o Gemini. O servidor do Google demorou para responder. Verifique sua conexão e tente novamente."
                errorBody.contains("NO_INTERNET", ignoreCase = true) ->
                    "Sem conexão com a internet ou não foi possível alcançar os servidores do Google Gemini."
                errorBody.contains("CONNECTION_FAILED", ignoreCase = true) ->
                    "Falha ao conectar aos servidores do Google Gemini. Verifique sua conexão."
                else ->
                    "Falha na comunicação de rede com o Gemini (${errorBody.removePrefix("NETWORK_ERROR:").trim().take(80)})."
            }
        }
        try {
            val root = gson.fromJson(errorBody, JsonObject::class.java)
            val errorObj = root?.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString
            if (!message.isNullOrBlank()) {
                return message
            }
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

    private fun buildSystemInstruction(
        existingActivities: List<Activity>,
        locale: Locale
    ): String {
        val today = LocalDate.now()
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val dayOfWeek = today.dayOfWeek.toString()

        // Filtrar agendamentos relevantes próximos para dar contexto ao Gemini
        val contextActivities = existingActivities.take(30).map { act ->
            mapOf(
                "id" to act.id,
                "title" to act.title,
                "date" to act.date,
                "startTime" to act.startTime?.toString(),
                "activityType" to act.activityType.name,
                "isCompleted" to act.isCompleted
            )
        }
        val activitiesJson = gson.toJson(contextActivities)

        return """
Você é o assistente inteligente integrado ao aplicativo The Big Calendar.
Sua função é interpretar comandos de voz e texto do usuário e retornar EXCLUSIVAMENTE uma estrutura JSON estrita instruindo o aplicativo sobre o que fazer no calendário.

DATA E HORA ATUAIS:
- Data de hoje: ${today.toString()} ($dayOfWeek)
- Horário atual: $currentTime
- Idioma do usuário: ${locale.language} (${locale.displayLanguage})

AGENDAMENTOS EXISTENTES PARA CONTEXTO:
$activitiesJson

REGRAS CRÍTICAS DE FORMATAÇÃO DA DESCRIÇÃO (MUITO IMPORTANTE):
1. LISTAS DE COMPRAS / ITENS / CHECKLIST / CONCLUSÃO:
   Se o usuário pedir para criar ou adicionar uma lista de compras, lista de afazeres, tarefas ou qualquer checklist de conclusão:
   CADA item na descrição DEVE OBRIGATORIAMENTE começar uma nova linha com o prefixo "[ ] " (colchete aberto, espaço, colchete fechado, espaço) seguido do nome do item.
   Exemplo de descrição para lista de compras:
   "[ ] Queijo\n[ ] Pimenta\n[ ] Alho"
   MOTIVO: O Big Calendar detecta automaticamente o padrão "[ ] " e renderiza cada item como uma caixa de seleção interativa que o usuário pode marcar/desmarcar na tela!

2. LISTAS ORDENADAS / NUMERADAS:
   Se o usuário pedir expressamente uma lista ordenada ou numerada:
   CADA item na descrição DEVE começar na linha com o respectivo número seguido de ponto e espaço.
   Exemplo de descrição:
   "1. Comprar os ingredientes\n2. Bater a massa\n3. Levar ao forno"

3. TEXTO COMUM OU NOTAS:
   Se for apenas um recado ou nota simples sem lista, escreva o texto normalmente.

RESOLUÇÃO DE DATAS:
- "hoje": ${today.toString()}
- "amanhã": ${today.plusDays(1).toString()}
- "depois de amanhã": ${today.plusDays(2).toString()}
- Dias da semana ("nesta sexta", "próxima segunda", etc.): calcule a data exata da próxima ocorrência do dia mencionado a partir de ${today.toString()}.
- Sempre formate "date" como "yyyy-MM-dd".

RESOLUÇÃO DE HORÁRIOS:
- Se o usuário mencionar horário específico (ex: "às 14h", "às 3 da tarde", "10:30"):
  "isAllDay": false
  "startTime": formato "HH:mm" (ex: "14:00")
  "endTime": formato "HH:mm" (se não informado, coloque 1 hora após o início)
- Se o usuário NÃO mencionar horário:
  "isAllDay": true
  "startTime": null
  "endTime": null

TIPOS DE ATIVIDADE:
- "TASK": Tarefas diárias, lembretes, listas de compras, afazeres. (Padrão para lembretes).
- "EVENT": Compromissos, reuniões, viagens, eventos com hora marcada.
- "NOTE": Anotações gerais, ideias, observações.
- "BIRTHDAY": Aniversários.

NOTIFICAÇÕES:
- Se o usuário pedir para "lembrar", "notificar", "avisar" ou agendar com horário:
  "notificationEnabled": true
  "notificationMinutesBefore": 0 (ou minutos antes se pedido)

AÇÕES SUPORTADAS:
- "CREATE": Criar uma nova atividade no calendário.
- "UPDATE": Modificar um agendamento existente (identifique pelo targetActivityId ou targetActivityTitle mais próximo).
- "DELETE": Excluir um agendamento existente (identifique pelo targetActivityId ou targetActivityTitle).
- "COMPLETE": Marcar uma tarefa como concluída.
- "QUERY": Responder sobre os agendamentos existentes (ex: "o que tenho amanhã?").
- "NONE": Conversa geral ou quando não for possível realizar uma ação no calendário.

MENSAGEM DE RESPOSTA (replyMessage):
- Sempre retorne uma frase amigável, clara e concisa no idioma do usuário (${locale.displayLanguage}) resumindo a ação feita ou respondendo à dúvida.
- Essa mensagem poderá ser lida em voz alta para o usuário.

FORMATO DE RESPOSTA OBRIGATÓRIO (JSON PURO):
Retorne única e exclusivamente um objeto JSON com esta estrutura:
{
  "action": "CREATE" | "UPDATE" | "DELETE" | "COMPLETE" | "QUERY" | "NONE",
  "title": "Título claro do agendamento",
  "date": "yyyy-MM-dd",
  "startTime": "HH:mm" ou null,
  "endTime": "HH:mm" ou null,
  "isAllDay": true | false,
  "description": "descrição formatada conforme as regras acima",
  "activityType": "TASK" | "EVENT" | "NOTE" | "BIRTHDAY",
  "visibility": "LOW" | "MEDIUM" | "HIGH",
  "notificationEnabled": true | false,
  "notificationMinutesBefore": 0,
  "targetActivityId": "ID caso seja UPDATE ou DELETE ou null",
  "targetActivityTitle": "Título do agendamento alvo ou null",
  "replyMessage": "Mensagem amigável de confirmação ou resposta."
}
""".trimIndent()
    }

    private fun buildRequestBody(
        prompt: String,
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
        val userPart = JsonObject()
        userPart.addProperty("text", prompt)
        userParts.add(userPart)
        contentObj.add("parts", userParts)
        contentsArray.add(contentObj)
        root.add("contents", contentsArray)

        // generationConfig
        val genConfig = JsonObject()
        if (jsonMimeType) {
            genConfig.addProperty("response_mime_type", "application/json")
        }
        genConfig.addProperty("temperature", 0.1)
        root.add("generationConfig", genConfig)

        return gson.toJson(root)
    }

    private fun parseGeminiResponse(jsonString: String): GeminiCommandResult? {
        try {
            val root = gson.fromJson(jsonString, JsonObject::class.java)
            val candidates = root.getAsJsonArray("candidates")
            if (candidates == null || candidates.size() == 0) return null

            val firstCandidate = candidates.get(0).asJsonObject
            val content = firstCandidate.getAsJsonObject("content") ?: return null
            val parts = content.getAsJsonArray("parts") ?: return null
            if (parts.size() == 0) return null

            var rawText: String? = null

            // 1. Procurar nas partes a parte de resposta (que não seja pensamento/thought)
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

            // 2. Se todas foram marcadas ou não encontrou, procura por texto contendo "action"
            if (rawText == null) {
                for (i in 0 until parts.size()) {
                    val text = parts.get(i).asJsonObject.get("text")?.asString
                    if (!text.isNullOrBlank() && text.contains("\"action\"")) {
                        rawText = text
                        break
                    }
                }
            }

            // 3. Fallback para a última parte com texto
            if (rawText == null) {
                rawText = parts.get(parts.size() - 1).asJsonObject.get("text")?.asString ?: return null
            }

            rawText = rawText.trim()
            if (rawText.startsWith("```json")) {
                rawText = rawText.removePrefix("```json")
            } else if (rawText.startsWith("```")) {
                rawText = rawText.removePrefix("```")
            }
            if (rawText.endsWith("```")) {
                rawText = rawText.removeSuffix("```")
            }
            rawText = rawText.trim()

            // Extrair substring entre o primeiro '{' e o último '}'
            val firstBrace = rawText.indexOf('{')
            val lastBrace = rawText.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                rawText = rawText.substring(firstBrace, lastBrace + 1)
            }

            return gson.fromJson(rawText, GeminiCommandResult::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao desserializar JSON retornado pelo Gemini", e)
            return null
        }
    }
}
