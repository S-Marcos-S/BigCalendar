package com.mss.thebigcalendar.data.model

import java.time.LocalDate

/**
 * Representa um resultado de pesquisa no calendário
 */
data class SearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: LocalDate?,
    val type: Type,
    val originalData: Any // Pode ser Activity, Holiday, etc.
) {
    enum class Type {
        ACTIVITY,    // Eventos, tarefas, aniversários
        HOLIDAY      // Feriados nacionais
    }
}

/**
 * Extensões para converter diferentes tipos de dados em SearchResult
 */
fun Activity.toSearchResult(): SearchResult {
    var date = try {
        LocalDate.parse(this.date)
    } catch (e: Exception) {
        null
    }
    
    // Se for um agendamento JSON importado, ajustar o ano para o ano atual para exibição e navegação correta
    if (date != null && this.location?.startsWith("JSON_IMPORTED_") == true) {
        val currentYear = LocalDate.now().year
        date = try {
            date.withYear(currentYear)
        } catch (e: Exception) {
            if (date.monthValue == 2 && date.dayOfMonth == 29) {
                LocalDate.of(currentYear, 2, 28)
            } else {
                date
            }
        }
    }
    
    val subtitle = when (this.activityType) {
        ActivityType.EVENT -> "Evento"
        ActivityType.TASK -> "Tarefa"
        ActivityType.BIRTHDAY -> "Aniversário"
        ActivityType.NOTE -> "Nota"
        ActivityType.COMMEMORATIVE -> "Data Comemorativa"
    }
    
    return SearchResult(
        id = this.id,
        title = this.title,
        subtitle = subtitle,
        date = date,
        type = SearchResult.Type.ACTIVITY,
        originalData = this
    )
}

fun Holiday.toSearchResult(): SearchResult {
    val date = parseHolidayDate(this.date, this.type)
    val subtitle = when (this.type) {
        HolidayType.NATIONAL -> "Feriado Nacional"
        HolidayType.COMMEMORATIVE -> "Data Comemorativa"
        HolidayType.JSON_IMPORT -> "Agendamento Importado"
    }
    
    return SearchResult(
        id = this.date, // Usa a data como ID para feriados
        title = this.name,
        subtitle = subtitle,
        date = date,
        type = SearchResult.Type.HOLIDAY,
        originalData = this
    )
}

/**
 * Função auxiliar para fazer o parsing de datas de feriados
 * que podem estar em diferentes formatos
 */
private fun parseHolidayDate(dateString: String, type: HolidayType): LocalDate? {
    return try {
        java.time.LocalDate.parse(dateString)
    } catch (e: Exception) {
        null
    }
}
