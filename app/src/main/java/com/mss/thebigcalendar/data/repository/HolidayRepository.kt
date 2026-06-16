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
            Holiday(
                name = "Confraternização Universal",
                date = "$yearStr-01-01",
                type = HolidayType.NATIONAL,
                summary = "Comemorado em 1º de janeiro, é um feriado nacional que celebra o início de um novo ano civil. É também conhecido mundialmente como o Dia da Confraternização Universal ou Dia da Paz Mundial, instituído pela ONU para promover a harmonia entre as nações.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Confraterniza%C3%A7%C3%A3o_Universal"
            ),
            Holiday(
                name = "Carnaval",
                date = carnavalMonday.toString(),
                type = HolidayType.NATIONAL,
                summary = "O Carnaval é a maior festa popular do Brasil, celebrada tradicionalmente nos dias que antecedem a Quarta-feira de Cinzas. Caracteriza-se por desfiles de escolas de samba, blocos de rua, fantasias e manifestações culturais diversas por todo o país.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Carnaval_do_Brasil"
            ),
            Holiday(
                name = "Carnaval",
                date = carnavalTuesday.toString(),
                type = HolidayType.NATIONAL,
                summary = "O Carnaval é a maior festa popular do Brasil, celebrada tradicionalmente nos dias que antecedem a Quarta-feira de Cinzas. Caracteriza-se por desfiles de escolas de samba, blocos de rua, fantasias e manifestações culturais diversas por todo o país.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Carnaval_do_Brasil"
            ),
            Holiday(
                name = "Paixão de Cristo",
                date = goodFriday.toString(),
                type = HolidayType.NATIONAL,
                summary = "A Paixão de Cristo, ou Sexta-feira Santa, é uma data cristã que relembra a crucificação e morte de Jesus Cristo. É um dia de reflexão, oração e jejum para os fiéis católicos e ortodoxos, marcando o início das celebrações da Páscoa.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Sexta-feira_Santa"
            ),
            Holiday(
                name = "Tiradentes",
                date = "$yearStr-04-21",
                type = HolidayType.NATIONAL,
                summary = "Homenageia Joaquim José da Silva Xavier, o Tiradentes, patrono cívico do Brasil e herói da Inconfidência Mineira. Ele foi executado em 21 de abril de 1792 por lutar contra o domínio colonial português e pela independência do país.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Tiradentes"
            ),
            Holiday(
                name = "Dia do Trabalho",
                date = "$yearStr-05-01",
                type = HolidayType.NATIONAL,
                summary = "Celebrado mundialmente em 1º de maio, o Dia do Trabalhador homenageia as lutas históricas da classe trabalhadora por melhores condições de trabalho e direitos trabalhistas. No Brasil, foi oficializado como feriado nacional em 1924.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_Mundial_do_Trabalho"
            ),
            Holiday(
                name = "Corpus Christi",
                date = corpusChristi.toString(),
                type = HolidayType.NATIONAL,
                summary = "Festa religiosa da Igreja Católica destinada a celebrar o mistério da eucaristia, o sacramento do corpo e do sangue de Jesus Cristo. A celebração ocorre sempre em uma quinta-feira e é famosa pela confecção de belos tapetes coloridos nas ruas.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Corpus_Christi"
            ),
            Holiday(
                name = "Independência do Brasil",
                date = "$yearStr-09-07",
                type = HolidayType.NATIONAL,
                summary = "Marca a proclamação da independência do Brasil em relação a Portugal, realizada em 7 de setembro de 1822 por Dom Pedro I às margens do Rio Ipiranga. É a principal data cívica do país, com desfiles militares celebrados nacionalmente.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Independ%C3%AAncia_do_Brasil"
            ),
            Holiday(
                name = "Nossa Sr.a Aparecida - Padroeira do Brasil",
                date = "$yearStr-10-12",
                type = HolidayType.NATIONAL,
                summary = "Feriado que celebra Nossa Senhora da Conceição Aparecida, padroeira do Brasil. A data homenageia a imagem de terracota da Virgem Maria encontrada por pescadores no Rio Paraíba do Sul em 1717, coincidindo também com as comemorações do Dia das Crianças.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Nossa_Senhora_Aparecida"
            ),
            Holiday(
                name = "Finados",
                date = "$yearStr-11-02",
                type = HolidayType.NATIONAL,
                summary = "O Dia de Finados é uma data dedicada à memória e homenagem aos entes queridos que já faleceram. É um dia de visitas a cemitérios, orações e reflexão espiritual em diversas tradições religiosas, especialmente no catolicismo.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_de_Finados"
            ),
            Holiday(
                name = "Proclamação da República",
                date = "$yearStr-11-15",
                type = HolidayType.NATIONAL,
                summary = "Celebra o dia 15 de novembro de 1889, data em que o marechal Deodoro da Fonseca liderou um golpe militar que derrubou a monarquia constitucional do Império do Brasil e instituiu a forma republicana presidencialista de governo.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Proclama%C3%A7%C3%A3o_da_Rep%C3%BAblica_do_Brasil"
            ),
            Holiday(
                name = "Dia Nacional de Zumbi e da Consciência Negra",
                date = "$yearStr-11-20",
                type = HolidayType.NATIONAL,
                summary = "Celebrado em 20 de novembro, marca a data da morte de Zumbi dos Palmares, líder do maior quilombo do período colonial. O feriado nacional promove a reflexão sobre a resistência negra, a riqueza da cultura afro-brasileira e o combate ao racismo.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_Nacional_da_Consci%C3%AAncia_Negra"
            ),
            Holiday(
                name = "Natal",
                date = "$yearStr-12-25",
                type = HolidayType.NATIONAL,
                summary = "Festa cristã que celebra o nascimento de Jesus de Nazaré. É um momento tradicional de reunião familiar, troca de presentes e ceia, simbolizando a paz, a fraternidade e a união entre as pessoas ao redor do mundo.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Natal"
            )
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

    

    fun getCommemorativeDates(year: Int): List<Holiday> {
        val mothersDay = getMothersDay(year)
        val fathersDay = getFathersDay(year)
        val yearStr = year.toString()

        return listOf(
            Holiday(
                name = "Dia Internacional da Mulher",
                date = "$yearStr-03-08",
                type = HolidayType.COMMEMORATIVE,
                summary = "Celebrado mundialmente em 8 de março, destaca a luta histórica das mulheres por igualdade de direitos, melhores condições de trabalho e fim da violência de gênero. É uma data de conscientização social e celebração de conquistas femininas.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_Internacional_da_Mulher"
            ),
            Holiday(
                name = "Dia das Mães",
                date = mothersDay.toString(),
                type = HolidayType.COMMEMORATIVE,
                summary = "Celebrado no segundo domingo de maio, é uma data especial para homenagear o amor, a dedicação e a importância de todas as mães e figuras maternas na estrutura familiar e na sociedade. É uma das celebrações mais tradicionais do país.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_das_M%C3%A3es"
            ),
            Holiday(
                name = "Dia dos Namorados",
                date = "$yearStr-06-12",
                type = HolidayType.COMMEMORATIVE,
                summary = "Comemorado em 12 de junho na véspera do Dia de Santo António, o Dia dos Namorados no Brasil celebra a união amorosa entre casais, com troca de presentes, cartões e gestos de carinho, fomentando o afeto e o romantismo.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_dos_Namorados"
            ),
            Holiday(
                name = "Dia do Amigo",
                date = "$yearStr-07-20",
                type = HolidayType.COMMEMORATIVE,
                summary = "Celebrado em 20 de julho, o Dia do Amigo promove a amizade, o companheirismo e a fraternidade entre as pessoas. A data coincide com o dia em que o homem pisou na Lua em 1969, simbolizando a união e a superação de barreiras da humanidade.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_do_Amigo"
            ),
            Holiday(
                name = "Dia dos Pais",
                date = fathersDay.toString(),
                type = HolidayType.COMMEMORATIVE,
                summary = "Celebrado no segundo domingo de agosto, o Dia dos Pais homenageia a figura paterna e o papel essencial do pai na criação e orientação dos filhos, fortalecendo os laços familiares de afeto, gratidão e respeito.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_dos_Pais"
            ),
            Holiday(
                name = "Dia das Crianças",
                date = "$yearStr-10-12",
                type = HolidayType.COMMEMORATIVE,
                summary = "Celebrado no dia 12 de outubro, o Dia das Crianças homenageia a infância e busca conscientizar a sociedade sobre os direitos de proteção, saúde e educação de crianças e adolescentes. Coincide com o feriado de Nossa Senhora Aparecida.",
                wikipediaLink = "https://pt.wikipedia.org/wiki/Dia_das_Crian%C3%A7as"
            )
        )
    }

    private fun getMothersDay(year: Int): LocalDate {
        var date = LocalDate.of(year, 5, 1)
        while (date.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
            date = date.plusDays(1)
        }
        return date.plusDays(7)
    }

    private fun getFathersDay(year: Int): LocalDate {
        var date = LocalDate.of(year, 8, 1)
        while (date.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
            date = date.plusDays(1)
        }
        return date.plusDays(7)
    }
}