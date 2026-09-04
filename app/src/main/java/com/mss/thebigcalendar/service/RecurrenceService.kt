package com.mss.thebigcalendar.service

import com.mss.thebigcalendar.data.model.Activity
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Serviço para gerenciar repetições de atividades
 */
class RecurrenceService {

    /**
     * Gera todas as instâncias de uma atividade baseada em sua regra de repetição
     */
    fun generateRecurringInstances(
        baseActivity: Activity,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<Activity> {
        val recurrenceRule = baseActivity.recurrenceRule ?: return listOf(baseActivity)
        
        if (recurrenceRule.isEmpty() || recurrenceRule == "NONE") {
            return listOf(baseActivity)
        }

        val instances = mutableListOf<Activity>()
        val baseDate = LocalDate.parse(baseActivity.date)
        
        // NÃO adicionar a atividade base aqui - ela já aparece no calendário como atividade original
        
        // Gerar instâncias recorrentes
        when (recurrenceRule) {
            "HOURLY" -> generateHourlyInstances(baseActivity, baseDate, startDate, endDate, instances)
            "DAILY" -> generateDailyInstances(baseActivity, baseDate, startDate, endDate, instances)
            "WEEKLY" -> generateWeeklyInstances(baseActivity, baseDate, startDate, endDate, instances)
            "MONTHLY" -> generateMonthlyInstances(baseActivity, baseDate, startDate, endDate, instances)
            "YEARLY" -> generateYearlyInstances(baseActivity, baseDate, startDate, endDate, instances)
            else -> parseCustomRecurrenceRule(baseActivity, baseDate, startDate, endDate, instances)
        }
        
        return instances
    }

    /**
     * Gera instâncias por hora
     */
    private fun generateHourlyInstances(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        // Parsear a regra para obter o intervalo
        val interval = parseIntervalFromRule(baseActivity.recurrenceRule ?: "HOURLY")
        val hoursPerDay = 24
        val daysPerInterval = if (interval > 0) (interval.toDouble() / hoursPerDay).toInt().coerceAtLeast(1) else 1
        
        var currentDate = baseDate.plusDays(daysPerInterval.toLong())
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(daysPerInterval.toLong())
        }
    }

    /**
     * Gera instâncias diárias
     */
    private fun generateDailyInstances(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        var currentDate = baseDate.plusDays(1)
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(1)
        }
    }

    /**
     * Gera instâncias semanais
     */
    private fun generateWeeklyInstances(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        var currentDate = baseDate.plusWeeks(1)
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusWeeks(1)
        }
    }

    /**
     * Gera instâncias mensais
     */
    private fun generateMonthlyInstances(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        var currentDate = baseDate.plusMonths(1)
        while (!currentDate.isAfter(endDate)) {
            val targetDay = minOf(baseDate.dayOfMonth, currentDate.lengthOfMonth())
            val adjustedDate = currentDate.withDayOfMonth(targetDay)
            
            if (!adjustedDate.isBefore(startDate) && !baseActivity.excludedDates.contains(adjustedDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, adjustedDate))
            }
            currentDate = currentDate.plusMonths(1)
        }
    }

    /**
     * Gera instâncias anuais
     */
    private fun generateYearlyInstances(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        var currentDate = baseDate.plusYears(1)
        while (!currentDate.isAfter(endDate)) {
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusYears(1)
        }
    }

    /**
     * Parse de regras de repetição customizadas (formato: FREQ=DAILY;INTERVAL=1;UNTIL=2025-12-31)
     */
    private fun parseCustomRecurrenceRule(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        instances: MutableList<Activity>
    ) {
        try {
            val rule = baseActivity.recurrenceRule ?: return
            val parts = rule.split(";")
            val freq = parts.find { it.startsWith("FREQ=") }?.substringAfter("=") ?: "DAILY"
            val interval = parts.find { it.startsWith("INTERVAL=") }?.substringAfter("=")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val until = parts.find { it.startsWith("UNTIL=") }?.substringAfter("=")?.let { 
                try {
                    if (it.contains("/")) LocalDate.parse(it, java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    else LocalDate.parse(it)
                } catch (_: Exception) { null }
            }
            val count = parts.find { it.startsWith("COUNT=") }?.substringAfter("=")?.toIntOrNull()
            val byDay = parts.find { it.startsWith("BYDAY=") }?.substringAfter("=")
            
            val actualEndDate = when {
                until != null && until.isBefore(endDate) -> until
                else -> endDate
            }
            
            when (freq) {
                "HOURLY" -> generateCustomHourlyInstancesWithCount(baseActivity, baseDate, startDate, actualEndDate, interval, count, instances)
                "DAILY" -> generateCustomDailyInstancesWithCount(baseActivity, baseDate, startDate, actualEndDate, interval, count, instances)
                "WEEKLY" -> generateCustomWeeklyInstancesWithCount(baseActivity, baseDate, startDate, actualEndDate, interval, count, byDay, instances)
                "MONTHLY" -> generateCustomMonthlyInstancesWithCount(baseActivity, baseDate, startDate, actualEndDate, interval, count, instances)
                "YEARLY" -> generateCustomYearlyInstancesWithCount(baseActivity, baseDate, startDate, actualEndDate, interval, count, instances)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Gera instâncias por hora customizadas com intervalo e contagem
     */
    private fun generateCustomHourlyInstancesWithCount(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<Activity>
    ) {
        val baseTime = baseActivity.startTime ?: java.time.LocalTime.of(0, 0)
        var currentDateTime = baseDate.atTime(baseTime)
        var occurrenceCount = 1 // Atividade base é a 1ª ocorrência
        
        currentDateTime = currentDateTime.plusHours(interval.toLong())
        
        while (!currentDateTime.toLocalDate().isAfter(endDate) && (count == null || occurrenceCount < count)) {
            val currentDate = currentDateTime.toLocalDate()
            val currentTime = currentDateTime.toLocalTime()
            
            occurrenceCount++
            
            val instanceId = "${baseActivity.id}_${currentDate}_${currentTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
            val isExcluded = baseActivity.excludedInstances.contains(instanceId)
            
            if (!currentDate.isBefore(startDate) && !isExcluded) {
                val instanceWithTime = createRecurringInstanceWithTime(baseActivity, currentDate, currentTime)
                instances.add(instanceWithTime)
            }
            
            currentDateTime = currentDateTime.plusHours(interval.toLong())
        }
    }

    /**
     * Gera instâncias diárias customizadas com intervalo e contagem
     */
    private fun generateCustomDailyInstancesWithCount(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<Activity>
    ) {
        val step = if (interval > 0) interval else 1
        var currentDate = baseDate.plusDays(step.toLong())
        var occurrenceCount = 1 // Atividade base é a 1ª ocorrência
        
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusDays(step.toLong())
        }
    }

    /**
     * Gera instâncias semanais customizadas com intervalo e contagem
     */
    private fun generateCustomWeeklyInstancesWithCount(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        byDay: String?,
        instances: MutableList<Activity>
    ) {
        val step = if (interval > 0) interval else 1
        
        // Se não há BYDAY especificado, usar comportamento padrão (mesmo dia da semana)
        if (byDay.isNullOrEmpty()) {
            var currentDate = baseDate.plusWeeks(step.toLong())
            var occurrenceCount = 1 // Atividade base é a 1ª ocorrência
            
            while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
                occurrenceCount++
                if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                    instances.add(createRecurringInstance(baseActivity, currentDate))
                }
                currentDate = currentDate.plusWeeks(step.toLong())
            }
            return
        }
        
        // Parse dos dias da semana especificados
        val targetDays = byDay.split(",").mapNotNull { day ->
            when (day.trim()) {
                "SU" -> 7 // Domingo
                "MO" -> 1 // Segunda
                "TU" -> 2 // Terça
                "WE" -> 3 // Quarta
                "TH" -> 4 // Quinta
                "FR" -> 5 // Sexta
                "SA" -> 6 // Sábado
                else -> null
            }
        }
        
        if (targetDays.isEmpty()) return
        
        var occurrenceCount = 1
        var currentWeekStart = baseDate
        
        while (!currentWeekStart.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            targetDays.forEach { targetDay ->
                val targetDate = currentWeekStart.with(TemporalAdjusters.nextOrSame(
                    java.time.DayOfWeek.of(targetDay)
                ))
                
                if (!targetDate.isBefore(baseDate) && !targetDate.isEqual(baseDate) && !targetDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
                    occurrenceCount++
                    
                    if (!targetDate.isBefore(startDate) && !baseActivity.excludedDates.contains(targetDate.toString())) {
                        instances.add(createRecurringInstance(baseActivity, targetDate))
                    }
                }
            }
            
            currentWeekStart = currentWeekStart.plusWeeks(step.toLong())
        }
    }

    /**
     * Gera instâncias mensais customizadas com intervalo e contagem
     */
    private fun generateCustomMonthlyInstancesWithCount(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<Activity>
    ) {
        val step = if (interval > 0) interval else 1
        var currentDate = baseDate.plusMonths(step.toLong())
        var occurrenceCount = 1 // Atividade base é a 1ª ocorrência
        
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            
            val targetDay = minOf(baseDate.dayOfMonth, currentDate.lengthOfMonth())
            val adjustedDate = currentDate.withDayOfMonth(targetDay)
            
            if (!adjustedDate.isBefore(startDate) && !baseActivity.excludedDates.contains(adjustedDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, adjustedDate))
            }
            currentDate = currentDate.plusMonths(step.toLong())
        }
    }

    /**
     * Gera instâncias anuais customizadas com intervalo e contagem
     */
    private fun generateCustomYearlyInstancesWithCount(
        baseActivity: Activity,
        baseDate: LocalDate,
        startDate: LocalDate,
        endDate: LocalDate,
        interval: Int,
        count: Int?,
        instances: MutableList<Activity>
    ) {
        val step = if (interval > 0) interval else 1
        var currentDate = baseDate.plusYears(step.toLong())
        var occurrenceCount = 1 // Atividade base é a 1ª ocorrência
        
        while (!currentDate.isAfter(endDate) && (count == null || occurrenceCount < count)) {
            occurrenceCount++
            if (!currentDate.isBefore(startDate) && !baseActivity.excludedDates.contains(currentDate.toString())) {
                instances.add(createRecurringInstance(baseActivity, currentDate))
            }
            currentDate = currentDate.plusYears(step.toLong())
        }
    }

    /**
     * Cria uma instância de atividade para uma data específica
     */
    private fun createRecurringInstance(baseActivity: Activity, date: LocalDate): Activity {
        return baseActivity.copy(
            id = "${baseActivity.id}_${date}",
            date = date.toString()
        )
    }

    /**
     * Cria uma instância recorrente com hora específica
     */
    private fun createRecurringInstanceWithTime(baseActivity: Activity, date: LocalDate, time: java.time.LocalTime): Activity {
        val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        val timeString = time.format(timeFormatter)
        
        return baseActivity.copy(
            id = "${baseActivity.id}_${date}_${timeString}",
            title = baseActivity.title, // Manter título original
            date = date.toString(),
            startTime = time // Definir a hora específica
        )
    }

    /**
     * Extrai o intervalo de uma regra de recorrência
     */
    private fun parseIntervalFromRule(rule: String): Int {
        return try {
            val intervalMatch = Regex("INTERVAL=(\\d+)").find(rule)
            intervalMatch?.groupValues?.get(1)?.toInt() ?: 1
        } catch (e: Exception) {
            1
        }
    }

    /**
     * Converte a opção de repetição selecionada para uma regra de repetição
     */
    fun convertRepetitionOptionToRule(option: String): String {
        return when (option) {
            "NONE" -> ""
            "DAILY" -> "DAILY"
            "WEEKLY" -> "WEEKLY"
            "MONTHLY" -> "MONTHLY"
            "YEARLY" -> "YEARLY"
            else -> ""
        }
    }

    /**
     * Verifica se uma atividade é recorrente
     */
    fun isRecurring(activity: Activity): Boolean {
        val rule = activity.recurrenceRule ?: return false
        return rule.isNotEmpty() && rule != "NONE"
    }

    /**
     * Obtém a próxima ocorrência de uma atividade recorrente
     */
    fun getNextOccurrence(activity: Activity, fromDate: LocalDate): LocalDate? {
        val rule = activity.recurrenceRule ?: return null
        if (!isRecurring(activity)) return null

        if (rule.startsWith("FREQ=")) {
            val futureEndDate = fromDate.plusYears(5)
            val instances = generateRecurringInstances(activity, fromDate.plusDays(1), futureEndDate)
            return instances
                .map { LocalDate.parse(it.date) }
                .firstOrNull { it.isAfter(fromDate) && !activity.excludedDates.contains(it.toString()) }
        }

        var currentDate = fromDate
        // Loop a reasonable number of times to find the next valid, non-excluded date
        for (i in 1..365 * 5) { // Check for the next 5 years
            val nextDate = when (rule) {
                "DAILY" -> currentDate.plusDays(1)
                "WEEKLY" -> currentDate.plusWeeks(1)
                "MONTHLY" -> {
                    val baseDate = LocalDate.parse(activity.date)
                    val nextMonth = currentDate.plusMonths(1)
                    val day = minOf(baseDate.dayOfMonth, nextMonth.lengthOfMonth())
                    nextMonth.withDayOfMonth(day)
                }
                "YEARLY" -> {
                    val baseDate = LocalDate.parse(activity.date)
                    val nextYear = currentDate.plusYears(1)
                    val day = minOf(baseDate.dayOfMonth, nextYear.lengthOfMonth())
                    nextYear.withDayOfMonth(day)
                }
                else -> return null
            }

            if (!activity.excludedDates.contains(nextDate.toString())) {
                return nextDate
            }
            currentDate = nextDate // Continue searching from the excluded date
        }
        return null // No valid occurrence found in the next 5 years
    }

    /**
     * Obtém todas as ocorrências de uma atividade em um período
     */
    fun getOccurrencesInPeriod(
        activity: Activity,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LocalDate> {
        if (!isRecurring(activity)) {
            val activityDate = LocalDate.parse(activity.date)
            return if (activityDate.isBefore(startDate) || activityDate.isAfter(endDate)) {
                emptyList()
            } else {
                listOf(activityDate)
            }
        }

        val occurrences = mutableListOf<LocalDate>()
        val baseDate = LocalDate.parse(activity.date)
        
        // Adicionar a data base se estiver no período
        if (!baseDate.isBefore(startDate) && !baseDate.isAfter(endDate)) {
            occurrences.add(baseDate)
        }
        
        // Gerar ocorrências recorrentes
        val rule = activity.recurrenceRule ?: return occurrences
        
        if (rule.startsWith("FREQ=")) {
            val instances = generateRecurringInstances(activity, startDate, endDate)
            val recurringDates = instances.map { LocalDate.parse(it.date) }
            return (occurrences + recurringDates).distinct().sorted()
        }
        
        when (rule) {
            "DAILY" -> {
                var currentDate = baseDate.plusDays(1)
                while (!currentDate.isAfter(endDate)) {
                    if (!currentDate.isBefore(startDate)) {
                        occurrences.add(currentDate)
                    }
                    currentDate = currentDate.plusDays(1)
                }
            }
            "WEEKLY" -> {
                var currentDate = baseDate.plusWeeks(1)
                while (!currentDate.isAfter(endDate)) {
                    if (!currentDate.isBefore(startDate)) {
                        occurrences.add(currentDate)
                    }
                    currentDate = currentDate.plusWeeks(1)
                }
            }
            "MONTHLY" -> {
                var currentDate = baseDate.plusMonths(1)
                while (!currentDate.isAfter(endDate)) {
                    if (!currentDate.isBefore(startDate)) {
                        val targetDay = minOf(baseDate.dayOfMonth, currentDate.lengthOfMonth())
                        val adjustedDate = currentDate.withDayOfMonth(targetDay)
                        occurrences.add(adjustedDate)
                    }
                    currentDate = currentDate.plusMonths(1)
                }
            }
            "YEARLY" -> {
                var currentDate = baseDate.plusYears(1)
                while (!currentDate.isAfter(endDate)) {
                    if (!currentDate.isBefore(startDate)) {
                        occurrences.add(currentDate)
                    }
                    currentDate = currentDate.plusYears(1)
                }
            }
        }
        
        return occurrences
    }
}
