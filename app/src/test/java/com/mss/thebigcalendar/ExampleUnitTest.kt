package com.mss.thebigcalendar

import com.google.gson.Gson
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.GeminiAction
import com.mss.thebigcalendar.data.model.GeminiCommandResult
import com.mss.thebigcalendar.service.RecurrenceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ExampleUnitTest {
    private val recurrenceService = RecurrenceService()
    private val gson = Gson()

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryNHours() {
        assertEquals("FREQ=HOURLY;INTERVAL=8", recurrenceService.normalizeRecurrenceRule("FREQ=HOURLY;INTERVAL=8"))
        assertEquals("FREQ=HOURLY;INTERVAL=4", recurrenceService.normalizeRecurrenceRule("FREQ=HOURLY;INTERVAL=4"))
        assertEquals("FREQ=HOURLY;INTERVAL=1", recurrenceService.normalizeRecurrenceRule("HOURLY"))
        assertEquals("FREQ=HOURLY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("a cada 2 horas"))
        assertEquals("FREQ=HOURLY;INTERVAL=6", recurrenceService.normalizeRecurrenceRule("de 6 em 6 horas"))
        assertEquals("FREQ=HOURLY;INTERVAL=1", recurrenceService.normalizeRecurrenceRule("de hora em hora"))
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryNDays() {
        assertEquals("DAILY", recurrenceService.normalizeRecurrenceRule("DAILY"))
        assertEquals("DAILY", recurrenceService.normalizeRecurrenceRule("FREQ=DAILY;INTERVAL=1"))
        assertEquals("FREQ=DAILY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("FREQ=DAILY;INTERVAL=2"))
        assertEquals("FREQ=DAILY;INTERVAL=3", recurrenceService.normalizeRecurrenceRule("FREQ=DAILY;INTERVAL=3"))
        assertEquals("FREQ=DAILY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("a cada 2 dias"))
        assertEquals("FREQ=DAILY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("dia sim dia não"))
        assertEquals("DAILY", recurrenceService.normalizeRecurrenceRule("todos os dias"))
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryWeek() {
        assertEquals("WEEKLY", recurrenceService.normalizeRecurrenceRule("WEEKLY"))
        assertEquals("WEEKLY", recurrenceService.normalizeRecurrenceRule("FREQ=WEEKLY;INTERVAL=1"))
        assertEquals("FREQ=WEEKLY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("FREQ=WEEKLY;INTERVAL=2"))
        assertEquals("WEEKLY", recurrenceService.normalizeRecurrenceRule("toda semana"))
        assertEquals("WEEKLY", recurrenceService.normalizeRecurrenceRule("semanalmente"))
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryMonth() {
        assertEquals("MONTHLY", recurrenceService.normalizeRecurrenceRule("MONTHLY"))
        assertEquals("MONTHLY", recurrenceService.normalizeRecurrenceRule("FREQ=MONTHLY;INTERVAL=1"))
        assertEquals("FREQ=MONTHLY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("FREQ=MONTHLY;INTERVAL=2"))
        assertEquals("MONTHLY", recurrenceService.normalizeRecurrenceRule("todo mês"))
        assertEquals("MONTHLY", recurrenceService.normalizeRecurrenceRule("mensalmente"))
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryYear() {
        assertEquals("YEARLY", recurrenceService.normalizeRecurrenceRule("YEARLY"))
        assertEquals("YEARLY", recurrenceService.normalizeRecurrenceRule("FREQ=YEARLY;INTERVAL=1"))
        assertEquals("YEARLY", recurrenceService.normalizeRecurrenceRule("todo ano"))
        assertEquals("YEARLY", recurrenceService.normalizeRecurrenceRule("anualmente"))
    }

    @Test
    fun testNormalizeRecurrenceRule_EveryNYears() {
        assertEquals("FREQ=YEARLY;INTERVAL=2", recurrenceService.normalizeRecurrenceRule("FREQ=YEARLY;INTERVAL=2"))
        assertEquals("FREQ=YEARLY;INTERVAL=5", recurrenceService.normalizeRecurrenceRule("FREQ=YEARLY;INTERVAL=5"))
        assertEquals("FREQ=YEARLY;INTERVAL=3", recurrenceService.normalizeRecurrenceRule("a cada 3 anos"))
        assertEquals("FREQ=YEARLY;INTERVAL=10", recurrenceService.normalizeRecurrenceRule("a cada 10 anos"))
    }

    @Test
    fun testNormalizeRecurrenceRule_NoneAndNull() {
        assertNull(recurrenceService.normalizeRecurrenceRule(null))
        assertNull(recurrenceService.normalizeRecurrenceRule(""))
        assertNull(recurrenceService.normalizeRecurrenceRule("NONE"))
        assertNull(recurrenceService.normalizeRecurrenceRule("null"))
    }

    @Test
    fun testFormatRecurrenceRuleForDisplay() {
        assertEquals("Repete todos os dias", recurrenceService.formatRecurrenceRuleForDisplay("DAILY"))
        assertEquals("Repete toda semana", recurrenceService.formatRecurrenceRuleForDisplay("WEEKLY"))
        assertEquals("Repete todo mês", recurrenceService.formatRecurrenceRuleForDisplay("MONTHLY"))
        assertEquals("Repete todo ano", recurrenceService.formatRecurrenceRuleForDisplay("YEARLY"))
        assertEquals("Repete a cada 8 horas", recurrenceService.formatRecurrenceRuleForDisplay("FREQ=HOURLY;INTERVAL=8"))
        assertEquals("Repete a cada 2 dias", recurrenceService.formatRecurrenceRuleForDisplay("FREQ=DAILY;INTERVAL=2"))
        assertEquals("Repete a cada 5 anos", recurrenceService.formatRecurrenceRuleForDisplay("FREQ=YEARLY;INTERVAL=5"))
    }

    @Test
    fun testGenerateRecurringInstances_EveryNHours() {
        val baseActivity = Activity(
            id = "test_act_hourly",
            title = "Tomar remédio",
            date = "2026-09-05",
            startTime = LocalTime.of(8, 0),
            recurrenceRule = "FREQ=HOURLY;INTERVAL=8"
        )

        val startDate = LocalDate.of(2026, 9, 5)
        val endDate = LocalDate.of(2026, 9, 6)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        assertTrue(instances.isNotEmpty())
        val times = instances.map { it.date to it.startTime }
        assertTrue(times.contains("2026-09-05" to LocalTime.of(16, 0)))
        assertTrue(times.contains("2026-09-06" to LocalTime.of(0, 0)))
        assertTrue(times.contains("2026-09-06" to LocalTime.of(8, 0)))
        assertTrue(times.contains("2026-09-06" to LocalTime.of(16, 0)))
    }

    @Test
    fun testGenerateRecurringInstances_EveryNDays() {
        val baseActivity = Activity(
            id = "test_act_days",
            title = "Treino",
            date = "2026-09-01",
            recurrenceRule = "FREQ=DAILY;INTERVAL=2"
        )

        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 10)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        val dates = instances.map { it.date }
        assertEquals(listOf("2026-09-03", "2026-09-05", "2026-09-07", "2026-09-09"), dates)
    }

    @Test
    fun testGenerateRecurringInstances_EveryWeek() {
        val baseActivity = Activity(
            id = "test_act_weekly",
            title = "Reunião semanal",
            date = "2026-09-01",
            recurrenceRule = "WEEKLY"
        )

        val startDate = LocalDate.of(2026, 9, 1)
        val endDate = LocalDate.of(2026, 9, 23)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        val dates = instances.map { it.date }
        assertEquals(listOf("2026-09-08", "2026-09-15", "2026-09-22"), dates)
    }

    @Test
    fun testGenerateRecurringInstances_EveryMonth() {
        val baseActivity = Activity(
            id = "test_act_monthly",
            title = "Pagar aluguel",
            date = "2026-01-10",
            recurrenceRule = "MONTHLY"
        )

        val startDate = LocalDate.of(2026, 1, 10)
        val endDate = LocalDate.of(2026, 4, 15)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        val dates = instances.map { it.date }
        assertEquals(listOf("2026-02-10", "2026-03-10", "2026-04-10"), dates)
    }

    @Test
    fun testGenerateRecurringInstances_EveryYear() {
        val baseActivity = Activity(
            id = "test_act_yearly",
            title = "Aniversário",
            date = "2026-05-15",
            recurrenceRule = "YEARLY"
        )

        val startDate = LocalDate.of(2026, 5, 15)
        val endDate = LocalDate.of(2029, 6, 1)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        val dates = instances.map { it.date }
        assertEquals(listOf("2027-05-15", "2028-05-15", "2029-05-15"), dates)
    }

    @Test
    fun testGenerateRecurringInstances_EveryNYears() {
        val baseActivity = Activity(
            id = "test_act_n_years",
            title = "Renovação CNH",
            date = "2026-05-15",
            recurrenceRule = "FREQ=YEARLY;INTERVAL=5"
        )

        val startDate = LocalDate.of(2026, 5, 15)
        val endDate = LocalDate.of(2040, 1, 1)

        val instances = recurrenceService.generateRecurringInstances(baseActivity, startDate, endDate)
        val dates = instances.map { it.date }
        assertEquals(listOf("2031-05-15", "2036-05-15"), dates)
    }

    @Test
    fun testGeminiJsonDeserializationWithRecurrenceRule() {
        val json = """
            {
              "action": "CREATE",
              "title": "Tomar antibiótico",
              "date": "2026-09-05",
              "startTime": "08:00",
              "endTime": "08:30",
              "isAllDay": false,
              "description": "Tomar com água",
              "activityType": "TASK",
              "visibility": "HIGH",
              "recurrenceRule": "FREQ=HOURLY;INTERVAL=8",
              "notificationEnabled": true,
              "notificationMinutesBefore": 0,
              "replyMessage": "Lembrete de tomar antibiótico criado a cada 8 horas."
            }
        """.trimIndent()

        val result = gson.fromJson(json, GeminiCommandResult::class.java)
        assertNotNull(result)
        assertEquals(GeminiAction.CREATE, result.action)
        assertEquals("Tomar antibiótico", result.title)
        assertEquals("FREQ=HOURLY;INTERVAL=8", result.recurrenceRule)
        assertEquals(false, result.isAllDay)
        assertEquals("08:00", result.startTime)
    }
}