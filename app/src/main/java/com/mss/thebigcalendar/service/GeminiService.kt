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

        val primaryModel = (if (model.isBlank()) DEFAULT_MODEL else model.trim()).removePrefix("models/")
        val attemptsLog = mutableListOf<String>()

        try {
            val systemInstruction = buildSystemInstruction(existingActivities, userLocale)
            val jsonRequestBody = buildRequestBody(prompt, systemInstruction, jsonMimeType = true)
            val textRequestBody = buildRequestBody(prompt, systemInstruction, jsonMimeType = false)

            // Fila de modelos prioritários para contingência automática (Geração Gemini 3.x)
            val candidateModels = linkedSetOf<String>()
            candidateModels.add(primaryModel)
            listOf(
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

                // 1. Tentar com v1beta e formato JSON estrito
                attemptsLog.add("POST v1beta/models/$currentModel$fallbackSuffix")
                val (code, body) = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/v1beta/models/$currentModel:generateContent?key=$cleanApiKey",
                    jsonRequestBody
                )
                lastCode = code
                lastBody = body

                // Se não há conexão com a internet, interrompe de imediato para não demorar
                if (code == -1 && body.startsWith("NO_INTERNET")) {
                    return@withContext GeminiExecutionResult.Error(
                        message = "Sem conexão com a internet para contatar o Gemini.",
                        details = buildTechnicalDetails(
                            model = primaryModel,
                            httpCode = -1,
                            responseBody = "Falha de conexão com a internet / DNS indisponível.\n$body",
                            attempts = attemptsLog
                        )
                    )
                }

                // Se a chave de API for explicitamente inválida, interrompe de imediato
                val isExplicitKeyError = code == 403 ||
                        body.contains("API_KEY_INVALID", ignoreCase = true) ||
                        body.contains("key not valid", ignoreCase = true) ||
                        body.contains("API key not valid", ignoreCase = true)

                if (isExplicitKeyError) {
                    return@withContext GeminiExecutionResult.Error(
                        message = "Chave da API Gemini inválida ou sem permissão.",
                        details = buildTechnicalDetails(
                            model = primaryModel,
                            httpCode = code,
                            responseBody = body,
                            attempts = attemptsLog
                        ),
                        isApiKeyError = true
                    )
                }

                // Sucesso: tentar interpretar JSON
                if (code == HttpURLConnection.HTTP_OK) {
                    val parsedResult = parseGeminiResponse(body)
                    if (parsedResult != null) {
                        if (isFallback) {
                            Log.i(TAG, "Comando concluído com sucesso via modelo fallback: $currentModel (modelo original: $primaryModel)")
                        }
                        return@withContext GeminiExecutionResult.Success(parsedResult)
                    } else {
                        Log.w(TAG, "HTTP 200 para $currentModel, mas parsing de JSON falhou.")
                        attemptsLog.add("Resposta recebida de $currentModel não continha JSON de comando esperado.")
                    }
                }

                // Se for erro 400 (ex: modelo não suporta response_mime_type)
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
                            if (isFallback) {
                                Log.i(TAG, "Sucesso via fallback $currentModel (sem json mime)")
                            }
                            return@withContext GeminiExecutionResult.Success(parsedResult)
                        }
                    }
                }

                // Se for 404 no v1beta, tentar no endpoint v1
                if (code == 404) {
                    Log.w(TAG, "Tentando endpoint v1 para $currentModel...")
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
                            if (isFallback) {
                                Log.i(TAG, "Sucesso via fallback $currentModel (v1)")
                            }
                            return@withContext GeminiExecutionResult.Success(parsedResult)
                        }
                    }
                }

                // Se a resposta sugerir algum modelo específico no erro (ex: "Please update your code to use models/...")
                val suggestedModel = """use models/([a-zA-Z0-9._-]+)""".toRegex(RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.get(1)
                    ?: """models/([a-zA-Z0-9._-]+)""".toRegex(RegexOption.IGNORE_CASE)
                        .findAll(body)
                        .map { it.groupValues[1] }
                        .firstOrNull { it != currentModel && !body.contains("is no longer available", ignoreCase = true) }

                if (!suggestedModel.isNullOrBlank() && suggestedModel !in triedModels) {
                    Log.i(TAG, "Google sugeriu modelo alternativo: $suggestedModel. Enfileirando...")
                    modelQueue.addFirst(suggestedModel)
                }

                // Anotar motivo da falha para histórico técnico
                when (code) {
                    503 -> attemptsLog.add("HTTP 503 em $currentModel: Alta demanda temporária.")
                    429 -> attemptsLog.add("HTTP 429 em $currentModel: Cota ou limite de requisições excedido.")
                    -1 -> attemptsLog.add("Timeout/falha de conexão em $currentModel.")
                    else -> attemptsLog.add("Status $code em $currentModel.")
                }
            }

            // Se todos os modelos da lista estática falharam, consulta ListModels dinamicamente
            Log.w(TAG, "Todos os modelos candidatos falharam. Consultando ListModels dinamicamente...")
            attemptsLog.add("ListModels (consulta dinâmica de modelos disponíveis)")
            val dynamicModels = fetchAvailableModels(cleanApiKey, triedModels)
            for ((ver, dynModel) in dynamicModels) {
                Log.i(TAG, "Tentando modelo dinâmico: $dynModel ($ver)...")
                attemptsLog.add("POST $ver/models/$dynModel (descoberto dinamicamente)")
                val retry = tryPostGenerateContent(
                    "https://generativelanguage.googleapis.com/$ver/models/$dynModel:generateContent?key=$cleanApiKey",
                    jsonRequestBody
                )
                lastCode = retry.first
                lastBody = retry.second
                if (retry.first == HttpURLConnection.HTTP_OK) {
                    val parsedResult = parseGeminiResponse(retry.second)
                    if (parsedResult != null) {
                        Log.i(TAG, "Sucesso via modelo dinâmico: $dynModel")
                        return@withContext GeminiExecutionResult.Success(parsedResult)
                    }
                }
            }

            // Falha definitiva após esgotar todas as alternativas
            Log.e(TAG, "Todas as alternativas de modelos falharam ($lastCode): $lastBody")
            val userMessage = extractErrorMessage(lastBody, lastCode)

            GeminiExecutionResult.Error(
                message = userMessage,
                details = buildTechnicalDetails(
                    model = primaryModel,
                    httpCode = lastCode,
                    responseBody = lastBody,
                    attempts = attemptsLog
                ),
                isApiKeyError = false
            )
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Sem conexão com a internet", e)
            GeminiExecutionResult.Error(
                message = "Sem conexão com a internet para contatar o Gemini.",
                details = buildTechnicalDetails(
                    model = primaryModel,
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
                    model = primaryModel,
                    httpCode = -1,
                    responseBody = "O servidor do Google demorou mais que o esperado (> 30 segundos) para responder à requisição.\nExceção: ${e.javaClass.simpleName}: ${e.message}",
                    attempts = attemptsLog
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao chamar Gemini", e)
            GeminiExecutionResult.Error(
                message = "Erro ao processar comando: ${e.localizedMessage ?: e.message}",
                details = buildTechnicalDetails(
                    model = primaryModel,
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
                connectTimeout = 15000
                readTimeout = 30000
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

    private fun fetchAvailableModels(apiKey: String, excludedModels: Set<String> = emptySet()): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
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
                    for (i in 0 until models.size()) {
                        val m = models.get(i).asJsonObject
                        val name = m.get("name")?.asString ?: continue
                        val cleanName = name.removePrefix("models/")
                        if (cleanName in excludedModels) continue
                        val methods = m.getAsJsonArray("supportedGenerationMethods")?.map { it.asString } ?: emptyList()
                        if (methods.contains("generateContent")) {
                            if (results.none { it.second == cleanName }) {
                                results.add(Pair(ver, cleanName))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao listar modelos em $ver", e)
            }
        }
        return results.sortedByDescending { (_, name) ->
            when {
                name.contains("3.8") && name.contains("flash") -> 6
                name.contains("3.7") && name.contains("flash") -> 5
                name.contains("3.6") && name.contains("flash") -> 4
                name.contains("flash") -> 3
                name.contains("3.") && name.contains("pro") -> 2
                name.contains("pro") -> 1
                else -> 0
            }
        }
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
        if (defaultCode == 503 || errorBody.contains("high demand", ignoreCase = true)) {
            return "O Gemini está enfrentando alta demanda temporária. Tentamos modelos de contingência, mas os servidores continuam sobrecarregados. Tente novamente em instantes."
        }
        if (defaultCode == 429 || errorBody.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
            return "Limite de requisições excedido na API Gemini. Tente novamente em instantes."
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
                "recurrenceRule" to act.recurrenceRule,
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

RECORRÊNCIA E REPETIÇÃO (recurrenceRule):
Se o usuário solicitar que a atividade se repita ou seja recorrente, preencha o campo "recurrenceRule" estritamente de acordo com estes formatos:
1. A cada N horas (ex: "a cada 8 horas", "de 4 em 4 horas", "a cada 6h", "a cada 2 horas"):
   "recurrenceRule": "FREQ=HOURLY;INTERVAL=N" (ex: "FREQ=HOURLY;INTERVAL=8", "FREQ=HOURLY;INTERVAL=4")
   * Para repetição por horas, se o usuário não especificar horário inicial, defina "isAllDay": false e "startTime": "$currentTime".
2. A cada N dias (ex: "a cada 2 dias", "dia sim dia não", "a cada 3 dias"):
   "recurrenceRule": "FREQ=DAILY;INTERVAL=N" (ex: "FREQ=DAILY;INTERVAL=2", "FREQ=DAILY;INTERVAL=3")
3. Todos os dias / Diariamente (ex: "todo dia", "todos os dias", "diariamente"):
   "recurrenceRule": "DAILY"
4. Toda semana / Semanalmente (ex: "toda semana", "todas as semanas", "semanalmente", "toda terça", "toda segunda e quarta"):
   - Semanal geral ou no mesmo dia da data: "recurrenceRule": "WEEKLY"
   - A cada N semanas (ex: "a cada 2 semanas"): "recurrenceRule": "FREQ=WEEKLY;INTERVAL=N"
   - Dias específicos da semana: "recurrenceRule": "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE" (onde SU=Dom, MO=Seg, TU=Ter, WE=Qua, TH=Qui, FR=Sex, SA=Sáb)
5. Todo mês / Mensalmente (ex: "todo mês", "mensalmente", "todo dia 10", "mensal"):
   - "recurrenceRule": "MONTHLY"
   - A cada N meses (ex: "a cada 2 meses", "a cada 3 meses"): "recurrenceRule": "FREQ=MONTHLY;INTERVAL=N"
6. Todo ano / Anualmente (ex: "todo ano", "anualmente", "todo ano nesse dia", "anual"):
   "recurrenceRule": "YEARLY"
7. A cada N anos (ex: "a cada 2 anos", "a cada 3 anos", "a cada 5 anos", "de 10 em 10 anos"):
   "recurrenceRule": "FREQ=YEARLY;INTERVAL=N" (ex: "FREQ=YEARLY;INTERVAL=2", "FREQ=YEARLY;INTERVAL=5")
8. Sem repetição / Evento único:
   "recurrenceRule": null
9. Data limite ou quantidade (se especificado pelo usuário):
   - "até [data]": adicione ";UNTIL=yyyy-MM-dd" (ex: "FREQ=DAILY;INTERVAL=2;UNTIL=2026-12-31")
   - "por N vezes": adicione ";COUNT=N" (ex: "FREQ=HOURLY;INTERVAL=8;COUNT=10")

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
- Se a atividade possuir repetição, confirme a frequência na replyMessage (ex: "Lembrete 'Tomar remédio' agendado a cada 8 horas.", "Reunião de equipe agendada para toda semana às 14:00.", "Renovação agendada para repetir a cada 5 anos.").
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
  "recurrenceRule": "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY" | "FREQ=HOURLY;INTERVAL=N" | "FREQ=DAILY;INTERVAL=N" | "FREQ=YEARLY;INTERVAL=N" | null,
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
