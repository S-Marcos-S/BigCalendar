import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.services.calendar.Calendar
import java.io.InputStreamReader
import java.io.File

fun main() {
    println("🔍 Iniciando script de limpeza do Google Agenda...")
    
    val secretStream = Thread.currentThread().contextClassLoader.getResourceAsStream("client_secrets.json")
        ?: File("client_secrets.json").let { if (it.exists()) it.inputStream() else null }
        ?: File("desktop/src/desktopMain/resources/client_secrets.json").let { if (it.exists()) it.inputStream() else null }
        
    if (secretStream == null) {
        println("❌ Arquivo client_secrets.json não encontrado! Por favor, coloque-o na raiz do projeto ou na pasta resources do desktop.")
        return
    }

    try {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val clientSecrets = GoogleClientSecrets.load(jsonFactory, InputStreamReader(secretStream))

        val flow = GoogleAuthorizationCodeFlow.Builder(
            transport, jsonFactory, clientSecrets,
            listOf(
                "https://www.googleapis.com/auth/calendar",
                "https://www.googleapis.com/auth/calendar.events"
            )
        )
            .setDataStoreFactory(FileDataStoreFactory(File(System.getProperty("user.home"), ".thebigcalendar/tokens")))
            .setAccessType("offline")
            .build()

        val receiver = LocalServerReceiver.Builder().setPort(8888).build()
        val credential = AuthorizationCodeInstalledApp(flow, receiver).authorize("user")

        val calendarService = Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName("TheBigCalendar")
            .build()

        println("✅ Conectado ao Google Agenda. Listando agendas disponíveis (incluindo ocultas):")
        val calendarList = calendarService.calendarList().list().setShowHidden(true).execute()
        val calendars = calendarList.items ?: emptyList()
        val fileWriter = java.io.FileWriter("calendar_events.txt")
        
        val allEventsWithCalendar = mutableListOf<Pair<com.google.api.services.calendar.model.CalendarListEntry, com.google.api.services.calendar.model.Event>>()
        
        for (cal in calendars) {
            val isPrimary = cal.primary ?: false
            val isReadOnly = cal.accessRole == "reader"
            println("📅 Agenda: '${cal.summary}' | ID: '${cal.id}' | Primary: $isPrimary | AccessRole: ${cal.accessRole}")
            
            if (isReadOnly) {
                println("   ⚠️ Ignorando busca de eventos (somente leitura)")
                continue
            }
            
            var pageToken: String? = null
            val events = mutableListOf<com.google.api.services.calendar.model.Event>()
            do {
                val listRequest = calendarService.events().list(cal.id)
                    .setMaxResults(2500)
                if (pageToken != null) {
                    listRequest.pageToken = pageToken
                }
                val result = listRequest.execute()
                val items = result.items
                if (items != null) {
                    events.addAll(items)
                }
                pageToken = result.nextPageToken
            } while (pageToken != null)
            
            println("   Found ${events.size} events total in agenda '${cal.summary}'.")
            
            for (event in events) {
                val location = event.location ?: ""
                val summary = event.summary ?: ""
                val start = event.start?.dateTime ?: event.start?.date
                val description = event.description ?: ""
                fileWriter.write("📅 [Agenda: ${cal.summary}] Evento: '$summary' | Loc: '$location' | Desc: '$description' | Data: $start | ID: ${event.id}\n")
                allEventsWithCalendar.add(Pair(cal, event))
            }
        }
        fileWriter.close()
        
        // Carregar nomes de eventos pré-instalados dos arquivos JSON do projeto
        println("📦 Carregando banco de dados de feriados, santos e profissões...")
        val preInstalledNames = mutableSetOf<String>()
        
        // Feriados nacionais hardcoded
        val nationalHolidays = listOf(
            "Confraternização Universal", "Carnaval", "Sexta-feira Santa", "Páscoa",
            "Tiradentes", "Dia do Trabalhador", "Corpus Christi", "Independência do Brasil",
            "Nossa Senhora Aparecida", "Finados", "Proclamação da República", "Natal"
        )
        preInstalledNames.addAll(nationalHolidays.map { it.trim().lowercase() })
        
        val jsonPaths = listOf(
            "app/src/main/res/raw/military_holidays.json",
            "app/src/main/res/raw/professional_days.json",
            "app/src/main/res/raw/saints_data.json",
            "app/src/main/res/raw/eucharistic_miracles_data.json"
        )
        
        val nameRegex = """"(name)":\s*"([^"]+)"""".toRegex()
        for (path in jsonPaths) {
            var file = File(path)
            if (!file.exists()) {
                file = File("../$path")
            }
            
            if (file.exists()) {
                val content = file.readText()
                nameRegex.findAll(content).forEach { match ->
                    val name = match.groupValues[2]
                    preInstalledNames.add(name.trim().lowercase())
                }
            } else {
                println("   ⚠️ Arquivo não encontrado para carregamento de nomes: $path")
            }
        }
        println("✅ Carregados ${preInstalledNames.size} nomes de eventos pré-instalados para varredura.")
        println("----------------------------------------------")

        // Agrupar por calendarId + summary + start para encontrar duplicatas na mesma agenda
        val groups = allEventsWithCalendar.groupBy { (cal, event) ->
            val summary = event.summary?.trim() ?: ""
            val start = event.start?.dateTime?.toString() ?: event.start?.date?.toString() ?: ""
            "${cal.id}|$summary|$start"
        }

        var deletedCount = 0
        for ((key, pairList) in groups) {
            val parts = key.split("|")
            val calendarId = parts.getOrNull(0) ?: ""
            val summary = parts.getOrNull(1) ?: ""
            val start = parts.getOrNull(2) ?: ""
            
            val summaryLower = summary.trim().lowercase()
            
            // Determinar se o evento é pré-instalado (santo, feriado, profissão)
            val hasImportedLocation = pairList.any { (_, event) -> event.location?.startsWith("JSON_IMPORTED_") == true }
            val isPreInstalledName = preInstalledNames.contains(summaryLower) || 
                                     summaryLower.startsWith("dia de são ") || 
                                     summaryLower.startsWith("dia de santa ") || 
                                     summaryLower.startsWith("dia de santo ") ||
                                     summaryLower.startsWith("são ") || 
                                     summaryLower.startsWith("santa ") || 
                                     summaryLower.startsWith("santo ") ||
                                     summaryLower.startsWith("nossa senhora ")
            
            val isPreInstalled = hasImportedLocation || isPreInstalledName
            
            if (isPreInstalled && summary.isNotEmpty()) {
                // Deletar TODOS os eventos pré-instalados na agenda
                for ((cal, event) in pairList) {
                    println("   🗑️ Deletando evento pré-instalado: '$summary' na agenda '${cal.summary}'")
                    try {
                        calendarService.events().delete(cal.id, event.id).execute()
                        deletedCount++
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        println("   ❌ Erro ao deletar pré-instalado: ${e.message}")
                    }
                }
            } else if (summary.isNotEmpty() && pairList.size > 1) {
                // Excluir duplicatas de eventos normais na mesma agenda (manter o primeiro)
                println("⚠️ Encontradas ${pairList.size} duplicatas para: '$summary' em $start na agenda")
                val toDelete = pairList.drop(1)
                for ((cal, duplicate) in toDelete) {
                    println("   🗑️ Deletando duplicata de evento: '$summary' na agenda '${cal.summary}' (ID: ${duplicate.id})")
                    try {
                        calendarService.events().delete(cal.id, duplicate.id).execute()
                        deletedCount++
                        Thread.sleep(100)
                    } catch (e: Exception) {
                        println("   ❌ Erro ao deletar duplicata: ${e.message}")
                    }
                }
            }
        }
        
        println("🎉 Limpeza concluída! $deletedCount eventos duplicados/pré-instalados foram apagados do seu Google Agenda.")
    } catch (e: Exception) {
        println("❌ Ocorreu um erro durante a execução: ${e.message}")
        e.printStackTrace()
    }
}
