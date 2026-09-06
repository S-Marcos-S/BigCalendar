package com.mss.thebigcalendar.data.model

import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.util.UUID

/**
 * Tipos de layout suportados pelo gerador de modelos de calendário
 */
enum class CalendarLayoutType {
    @SerializedName("STANDARD_GRID")
    STANDARD_GRID,      // Grade completa de 7 colunas ocupando a página

    @SerializedName("SIDEBAR_LEFT")
    SIDEBAR_LEFT,       // Barra lateral à esquerda (anotações, metas, frases) + grade à direita

    @SerializedName("SIDEBAR_RIGHT")
    SIDEBAR_RIGHT,      // Grade à esquerda + barra lateral à direita (notas, rastreador)

    @SerializedName("TOP_BOTTOM_SPLIT")
    TOP_BOTTOM_SPLIT,   // Banner superior (metas/título) + grade central + notas/hábitos no rodapé

    @SerializedName("MINIMALIST_CLEAN")
    MINIMALIST_CLEAN,   // Visual limpo, tipografia moderna e linhas suaves

    @SerializedName("PLANNER_BULLET")
    PLANNER_BULLET      // Estilo bullet journal / planner pessoal com pontilhados e trackers
}

/**
 * Estilos de seção de anotações
 */
enum class NotesSectionStyle {
    @SerializedName("LINED")
    LINED,          // Linhas horizontais contínuas

    @SerializedName("DOTTED")
    DOTTED,         // Linhas pontilhadas

    @SerializedName("CHECKLIST")
    CHECKLIST,      // Caixas de seleção com linhas

    @SerializedName("GRID")
    GRID,           // Malha quadriculada

    @SerializedName("BLANK")
    BLANK           // Caixa em branco com borda suave
}

/**
 * Posição da seção de anotações
 */
enum class NotesPosition {
    @SerializedName("SIDEBAR")
    SIDEBAR,

    @SerializedName("BOTTOM")
    BOTTOM,

    @SerializedName("TOP")
    TOP
}

/**
 * Estilo do rastreador de hábitos
 */
enum class HabitTrackerStyle {
    @SerializedName("MONTHLY_DOTS")
    MONTHLY_DOTS,        // Tabela com círculos/quadrados para cada dia do mês

    @SerializedName("WEEKLY_CHECKBOXES")
    WEEKLY_CHECKBOXES    // Caixas para cada semana
}

/**
 * Posição das fases da lua
 */
enum class MoonPhaseDisplayPosition {
    @SerializedName("IN_DAY_CELLS")
    IN_DAY_CELLS,

    @SerializedName("BOTTOM_LEGEND")
    BOTTOM_LEGEND,

    @SerializedName("SIDEBAR")
    SIDEBAR,

    @SerializedName("NONE")
    NONE
}

/**
 * Formato dos nomes dos dias da semana
 */
enum class WeekDayDisplayFormat {
    @SerializedName("SHORT")
    SHORT,              // SEG, TER, QUA...

    @SerializedName("FULL")
    FULL,               // SEGUNDA, TERÇA...

    @SerializedName("SINGLE_LETTER")
    SINGLE_LETTER       // S, T, Q, Q, S, S, D
}

/**
 * Alinhamento de título
 */
enum class TitleAlignment {
    @SerializedName("LEFT")
    LEFT,

    @SerializedName("CENTER")
    CENTER,

    @SerializedName("RIGHT")
    RIGHT
}

/**
 * Opções de família de fontes
 */
enum class TemplateFontFamily {
    @SerializedName("DEFAULT")
    DEFAULT,

    @SerializedName("SERIF")
    SERIF,

    @SerializedName("SANS")
    SANS,

    @SerializedName("CURSIVE")
    CURSIVE,

    @SerializedName("MONO")
    MONO
}

/**
 * Modo de exibição de eventos nos dias
 */
enum class EventDisplayDensity {
    @SerializedName("FULL_TEXT")
    FULL_TEXT,          // Texto legível com título do evento

    @SerializedName("COMPACT_BADGES")
    COMPACT_BADGES,     // Pílulas/badges coloridos com texto compacto

    @SerializedName("DOT_INDICATORS")
    DOT_INDICATORS,     // Pontos coloridos discretos

    @SerializedName("BLANK_FOR_WRITING")
    BLANK_FOR_WRITING   // Sem eventos no PDF, espaço livre para escrita manual
}

/**
 * Configuração da seção de anotações
 */
data class NotesSectionConfig(
    @SerializedName("enabled")
    val enabled: Boolean = true,

    @SerializedName("title")
    val title: String = "Anotações",

    @SerializedName("style")
    val style: NotesSectionStyle = NotesSectionStyle.DOTTED,

    @SerializedName("position")
    val position: NotesPosition = NotesPosition.SIDEBAR,

    @SerializedName("linesCount")
    val linesCount: Int = 12
) : Serializable

/**
 * Configuração da seção de metas
 */
data class GoalsSectionConfig(
    @SerializedName("enabled")
    val enabled: Boolean = false,

    @SerializedName("title")
    val title: String = "Metas do Mês",

    @SerializedName("itemsCount")
    val itemsCount: Int = 4,

    @SerializedName("boxBackgroundColor")
    val boxBackgroundColor: String? = null // Hex color
) : Serializable

/**
 * Configuração do rastreador de hábitos
 */
data class HabitTrackerConfig(
    @SerializedName("enabled")
    val enabled: Boolean = false,

    @SerializedName("title")
    val title: String = "Rastreador de Hábitos",

    @SerializedName("habits")
    val habits: List<String> = listOf("Exercício", "Leitura", "Água", "Estudo"),

    @SerializedName("style")
    val style: HabitTrackerStyle = HabitTrackerStyle.MONTHLY_DOTS
) : Serializable

/**
 * Configuração do mini calendário anterior/seguinte
 */
data class MiniCalendarConfig(
    @SerializedName("enabled")
    val enabled: Boolean = false,

    @SerializedName("showPreviousMonth")
    val showPreviousMonth: Boolean = true,

    @SerializedName("showNextMonth")
    val showNextMonth: Boolean = true
) : Serializable

/**
 * Especificação Completa do Modelo de Calendário Gerado por IA
 */
data class CalendarAiTemplateSpec(
    @SerializedName("id")
    val id: String = UUID.randomUUID().toString(),

    @SerializedName("name")
    val name: String = "Modelo Personalizado",

    @SerializedName("description")
    val description: String = "",

    @SerializedName("layoutType")
    val layoutType: CalendarLayoutType = CalendarLayoutType.STANDARD_GRID,

    @SerializedName("isLandscape")
    val isLandscape: Boolean = true,

    @SerializedName("pageSize")
    val pageSize: String = "A4", // "A4", "A3", "LETTER"

    @SerializedName("weekStartsOnMonday")
    val weekStartsOnMonday: Boolean = true,

    @SerializedName("sidebarWidthPercent")
    val sidebarWidthPercent: Float = 26f, // Para SIDEBAR_LEFT ou SIDEBAR_RIGHT

    // --- CORES (Hexadecimal: "#RRGGBB" ou "#AARRGGBB") ---
    @SerializedName("pageBackgroundColor")
    val pageBackgroundColor: String = "#FFFFFF",

    @SerializedName("headerBackgroundColor")
    val headerBackgroundColor: String? = null,

    @SerializedName("sidebarBackgroundColor")
    val sidebarBackgroundColor: String? = null,

    @SerializedName("gridBackgroundColor")
    val gridBackgroundColor: String = "#FFFFFF",

    @SerializedName("primaryColor")
    val primaryColor: String = "#212121", // Título do mês, destaques principais

    @SerializedName("secondaryColor")
    val secondaryColor: String = "#757575", // Subtítulos, anos, bordas de destaque

    @SerializedName("weekdayHeaderBackgroundColor")
    val weekdayHeaderBackgroundColor: String? = null,

    @SerializedName("weekdayTextColor")
    val weekdayTextColor: String = "#424242",

    @SerializedName("dayNumberColor")
    val dayNumberColor: String = "#212121",

    @SerializedName("weekendColor")
    val weekendColor: String? = "#D32F2F", // Destacar sábados/domingos

    @SerializedName("holidayColor")
    val holidayColor: String = "#C62828",

    @SerializedName("gridBorderColor")
    val gridBorderColor: String = "#E0E0E0",

    @SerializedName("gridBorderWidth")
    val gridBorderWidth: Float = 0.75f,

    @SerializedName("dayCellCornerRadius")
    val dayCellCornerRadius: Float = 0f, // 0 = retangular, > 0 = cantos arredondados

    @SerializedName("dayCellHeight")
    val dayCellHeight: Float = 60f,

    @SerializedName("showLinesInDayCells")
    val showLinesInDayCells: Boolean = false,

    @SerializedName("linesInDayCellsCount")
    val linesInDayCellsCount: Int = 3,

    // --- TIPOGRAFIA ---
    @SerializedName("fontFamily")
    val fontFamily: TemplateFontFamily = TemplateFontFamily.DEFAULT,

    @SerializedName("customFontFileName")
    val customFontFileName: String? = null, // ex: "Redressed.ttf"

    @SerializedName("monthTitleFontSize")
    val monthTitleFontSize: Float = 36f,

    @SerializedName("monthTitleAlignment")
    val monthTitleAlignment: TitleAlignment = TitleAlignment.CENTER,

    @SerializedName("monthTitleAllCaps")
    val monthTitleAllCaps: Boolean = false,

    @SerializedName("yearFontSize")
    val yearFontSize: Float = 16f,

    @SerializedName("weekdayFontSize")
    val weekdayFontSize: Float = 9f,

    @SerializedName("weekdayFormat")
    val weekdayFormat: WeekDayDisplayFormat = WeekDayDisplayFormat.SHORT,

    @SerializedName("dayNumberFontSize")
    val dayNumberFontSize: Float = 12f,

    // --- SEÇÕES ADICIONAIS ---
    @SerializedName("notesSection")
    val notesSection: NotesSectionConfig = NotesSectionConfig(),

    @SerializedName("goalsSection")
    val goalsSection: GoalsSectionConfig = GoalsSectionConfig(),

    @SerializedName("habitTracker")
    val habitTracker: HabitTrackerConfig = HabitTrackerConfig(),

    @SerializedName("miniCalendar")
    val miniCalendar: MiniCalendarConfig = MiniCalendarConfig(),

    @SerializedName("moonPhases")
    val moonPhasesPosition: MoonPhaseDisplayPosition = MoonPhaseDisplayPosition.IN_DAY_CELLS,

    @SerializedName("motivationalQuote")
    val motivationalQuote: String? = null,

    @SerializedName("eventDisplayDensity")
    val eventDisplayDensity: EventDisplayDensity = EventDisplayDensity.FULL_TEXT,

    @SerializedName("isUserCreated")
    val isUserCreated: Boolean = true
) : Serializable {

    fun toJson(): String {
        return Gson().toJson(this)
    }

    companion object {
        fun fromJson(json: String): CalendarAiTemplateSpec? {
            return try {
                Gson().fromJson(json, CalendarAiTemplateSpec::class.java)
            } catch (e: Exception) {
                null
            }
        }

        fun parseHexColor(hex: String?, fallback: Color = Color.Black): Color {
            if (hex.isNullOrBlank()) return fallback
            return try {
                val clean = hex.trim().removePrefix("#")
                val colorLong = when (clean.length) {
                    6 -> "FF$clean".toLong(16)
                    8 -> clean.toLong(16)
                    3 -> "FF${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}".toLong(16)
                    else -> return fallback
                }
                Color(colorLong)
            } catch (e: Exception) {
                fallback
            }
        }

        // --- PRESETS PRÉ-CONFIGURADOS E MODERNOS ---

        /**
         * Preset: Planner Rosé & Minimalista (Similar à Ideia 1)
         */
        val PRESET_PLANNER_ROSE = CalendarAiTemplateSpec(
            id = "preset_planner_rose",
            name = "Planner Rosé",
            description = "Barra lateral com notas pautadas, cabeçalho de metas e tons pastel rosé.",
            layoutType = CalendarLayoutType.SIDEBAR_LEFT,
            isLandscape = true,
            sidebarWidthPercent = 26f,
            pageBackgroundColor = "#FFF5F5",
            sidebarBackgroundColor = "#FFF0F0",
            gridBackgroundColor = "#FFFFFF",
            primaryColor = "#4A3E3D",
            secondaryColor = "#E8A598",
            weekdayHeaderBackgroundColor = null,
            weekdayTextColor = "#616161",
            dayNumberColor = "#333333",
            weekendColor = "#C96565",
            holidayColor = "#D9534F",
            gridBorderColor = "#F0D5D5",
            gridBorderWidth = 0.5f,
            dayCellCornerRadius = 4f,
            fontFamily = TemplateFontFamily.CURSIVE,
            customFontFileName = "Redressed.ttf",
            monthTitleFontSize = 44f,
            monthTitleAlignment = TitleAlignment.LEFT,
            notesSection = NotesSectionConfig(
                enabled = true,
                title = "Anotações",
                style = NotesSectionStyle.DOTTED,
                position = NotesPosition.SIDEBAR,
                linesCount = 14
            ),
            goalsSection = GoalsSectionConfig(
                enabled = true,
                title = "METAS MENSAIS",
                itemsCount = 3,
                boxBackgroundColor = "#F7C5BC"
            ),
            isUserCreated = false
        )

        /**
         * Preset: Grid Clean & Moderno (Similar à Ideia 2)
         */
        val PRESET_CLEAN_MINIMAL = CalendarAiTemplateSpec(
            id = "preset_clean_minimal",
            name = "Minimalista Moderno",
            description = "Design limpo, tipografia contemporânea e ampla área de escrita.",
            layoutType = CalendarLayoutType.STANDARD_GRID,
            isLandscape = true,
            pageBackgroundColor = "#FFFFFF",
            gridBackgroundColor = "#FFFFFF",
            primaryColor = "#1E293B",
            secondaryColor = "#64748B",
            weekdayHeaderBackgroundColor = "#F8FAFC",
            weekdayTextColor = "#475569",
            dayNumberColor = "#0F172A",
            weekendColor = "#EF4444",
            holidayColor = "#DC2626",
            gridBorderColor = "#E2E8F0",
            gridBorderWidth = 0.8f,
            dayCellCornerRadius = 6f,
            fontFamily = TemplateFontFamily.SANS,
            monthTitleFontSize = 38f,
            monthTitleAlignment = TitleAlignment.CENTER,
            notesSection = NotesSectionConfig(
                enabled = false
            ),
            isUserCreated = false
        )

        /**
         * Preset: Rastreador de Hábitos & Produtividade
         */
        val PRESET_HABIT_TRACKER = CalendarAiTemplateSpec(
            id = "preset_habit_tracker",
            name = "Hábitos & Foco",
            description = "Divisão com rastreador mensal de hábitos e bloco de notas no rodapé.",
            layoutType = CalendarLayoutType.TOP_BOTTOM_SPLIT,
            isLandscape = true,
            pageBackgroundColor = "#F4F7F6",
            headerBackgroundColor = "#E3ECE9",
            gridBackgroundColor = "#FFFFFF",
            primaryColor = "#2D3748",
            secondaryColor = "#4A7C59",
            weekdayHeaderBackgroundColor = "#EAEFEF",
            weekdayTextColor = "#2F4F4F",
            dayNumberColor = "#1A202C",
            weekendColor = "#E53E3E",
            holidayColor = "#DD6B20",
            gridBorderColor = "#CBD5E0",
            gridBorderWidth = 0.6f,
            fontFamily = TemplateFontFamily.SERIF,
            monthTitleFontSize = 34f,
            monthTitleAlignment = TitleAlignment.LEFT,
            goalsSection = GoalsSectionConfig(
                enabled = true,
                title = "Foco Principal",
                itemsCount = 3,
                boxBackgroundColor = "#D0E1D4"
            ),
            habitTracker = HabitTrackerConfig(
                enabled = true,
                title = "Rastreador de Hábitos",
                habits = listOf("Exercício Físico", "Leitura (30m)", "Hidratação 2L", "Estudos"),
                style = HabitTrackerStyle.MONTHLY_DOTS
            ),
            notesSection = NotesSectionConfig(
                enabled = true,
                title = "Lembretes Importantes",
                style = NotesSectionStyle.LINED,
                position = NotesPosition.BOTTOM,
                linesCount = 4
            ),
            isUserCreated = false
        )

        /**
         * Preset: Elegante Serifado / Dark Accent
         */
        val PRESET_ELEGANT_DARK = CalendarAiTemplateSpec(
            id = "preset_elegant_dark",
            name = "Elegante Clássico",
            description = "Tons sóbrios de azul-marinho e cinza com tipografia clássica serifada.",
            layoutType = CalendarLayoutType.SIDEBAR_RIGHT,
            isLandscape = true,
            sidebarWidthPercent = 28f,
            pageBackgroundColor = "#FAF9F6",
            sidebarBackgroundColor = "#F0F3F6",
            gridBackgroundColor = "#FFFFFF",
            primaryColor = "#0F172A",
            secondaryColor = "#334155",
            weekdayHeaderBackgroundColor = "#1E293B",
            weekdayTextColor = "#FFFFFF",
            dayNumberColor = "#0F172A",
            weekendColor = "#BE123C",
            holidayColor = "#B91C1C",
            gridBorderColor = "#CBD5E1",
            gridBorderWidth = 0.75f,
            fontFamily = TemplateFontFamily.SERIF,
            monthTitleFontSize = 36f,
            monthTitleAlignment = TitleAlignment.CENTER,
            notesSection = NotesSectionConfig(
                enabled = true,
                title = "Prioridades & Notas",
                style = NotesSectionStyle.CHECKLIST,
                position = NotesPosition.SIDEBAR,
                linesCount = 10
            ),
            goalsSection = GoalsSectionConfig(
                enabled = true,
                title = "Metas do Mês",
                itemsCount = 3,
                boxBackgroundColor = "#E2E8F0"
            ),
            isUserCreated = false
        )

        fun getDefaultPresets(): List<CalendarAiTemplateSpec> {
            return listOf(
                PRESET_PLANNER_ROSE,
                PRESET_CLEAN_MINIMAL,
                PRESET_HABIT_TRACKER,
                PRESET_ELEGANT_DARK
            )
        }
    }
}
