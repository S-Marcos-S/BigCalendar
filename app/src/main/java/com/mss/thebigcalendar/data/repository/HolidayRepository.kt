package com.mss.thebigcalendar.data.repository

import android.content.Context
import com.mss.thebigcalendar.R
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.HolidayType
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate

class HolidayRepository(private val context: Context) {

    fun getNationalHolidays(year: Int): List<Holiday> {
        val easter = getEasterDate(year)
        val goodFriday = easter.minusDays(2)
        val carnavalTuesday = easter.minusDays(47)
        val carnavalMonday = easter.minusDays(48)
        val corpusChristi = easter.plusDays(60)

        val yearStr = year.toString()

        return listOf(
            Holiday("Confraternização Universal", "$yearStr-01-01", HolidayType.NATIONAL),
            Holiday("Carnaval", carnavalMonday.toString(), HolidayType.NATIONAL),
            Holiday("Carnaval", carnavalTuesday.toString(), HolidayType.NATIONAL),
            Holiday("Paixão de Cristo", goodFriday.toString(), HolidayType.NATIONAL),
            Holiday("Tiradentes", "$yearStr-04-21", HolidayType.NATIONAL),
            Holiday("Dia do Trabalho", "$yearStr-05-01", HolidayType.NATIONAL),
            Holiday("Corpus Christi", corpusChristi.toString(), HolidayType.NATIONAL),
            Holiday("Independência do Brasil", "$yearStr-09-07", HolidayType.NATIONAL),
            Holiday("Nossa Sr.a Aparecida - Padroeira do Brasil", "$yearStr-10-12", HolidayType.NATIONAL),
            Holiday("Finados", "$yearStr-11-02", HolidayType.NATIONAL),
            Holiday("Proclamação da República", "$yearStr-11-15", HolidayType.NATIONAL),
            Holiday("Dia Nacional de Zumbi e da Consciência Negra", "$yearStr-11-20", HolidayType.NATIONAL),
            Holiday("Natal", "$yearStr-12-25", HolidayType.NATIONAL)
        )
    }

    private fun getEasterDate(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun getSaintDays(): List<Holiday> {
        val inputStream = context.resources.openRawResource(R.raw.saints_data)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val jsonString = reader.readText()
        val jsonArray = JSONArray(jsonString)
        val saints = mutableListOf<Holiday>()

        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            saints.add(
                Holiday(
                    name = jsonObject.getString("name"),
                    date = jsonObject.getString("date"),
                    type = HolidayType.SAINT,
                    summary = jsonObject.optString("summary"),
                    wikipediaLink = jsonObject.optString("wikipediaLink")
                )
            )
        }
        return saints
    }

    fun getCommemorativeDates(): List<Holiday> {
        return listOf(
            Holiday("Dia Internacional da Mulher", "2024-03-08", HolidayType.COMMEMORATIVE),
            Holiday("Dia das Mães", "2024-05-12", HolidayType.COMMEMORATIVE),
            Holiday("Dia dos Namorados", "2024-06-12", HolidayType.COMMEMORATIVE),
            Holiday("Dia do Amigo", "2024-07-20", HolidayType.COMMEMORATIVE),
            Holiday("Dia dos Pais", "2024-08-11", HolidayType.COMMEMORATIVE),
            Holiday("Dia das Crianças", "2024-10-12", HolidayType.COMMEMORATIVE),

            Holiday("Dia Internacional da Mulher", "2025-03-08", HolidayType.COMMEMORATIVE),
            Holiday("Dia das Mães", "2025-05-11", HolidayType.COMMEMORATIVE)
        )
    }
}