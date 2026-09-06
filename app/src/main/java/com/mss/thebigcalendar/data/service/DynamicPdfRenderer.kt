package com.mss.thebigcalendar.data.service

import android.content.Context
import android.util.Log
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.DottedBorder
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.layout.properties.VerticalAlignment
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.CalendarAiTemplateSpec
import com.mss.thebigcalendar.data.model.CalendarLayoutType
import com.mss.thebigcalendar.data.model.EventDisplayDensity
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.JsonHoliday
import com.mss.thebigcalendar.data.model.MoonPhaseDisplayPosition
import com.mss.thebigcalendar.data.model.NotesPosition
import com.mss.thebigcalendar.data.model.NotesSectionStyle
import com.mss.thebigcalendar.data.model.TemplateFontFamily
import com.mss.thebigcalendar.data.model.TitleAlignment
import com.mss.thebigcalendar.data.model.WeekDayDisplayFormat
import com.mss.thebigcalendar.ui.components.MoonPhase
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class DynamicPdfRenderer(private val context: Context) {

    companion object {
        private const val TAG = "DynamicPdfRenderer"
    }

    private fun hexToColor(hex: String?, fallback: Color = ColorConstants.BLACK): Color {
        if (hex.isNullOrBlank()) return fallback
        return try {
            val clean = hex.trim().removePrefix("#")
            when (clean.length) {
                6 -> {
                    val r = clean.substring(0, 2).toInt(16)
                    val g = clean.substring(2, 4).toInt(16)
                    val b = clean.substring(4, 6).toInt(16)
                    DeviceRgb(r, g, b)
                }
                8 -> {
                    val r = clean.substring(2, 4).toInt(16)
                    val g = clean.substring(4, 6).toInt(16)
                    val b = clean.substring(6, 8).toInt(16)
                    DeviceRgb(r, g, b)
                }
                3 -> {
                    val r = ("" + clean[0] + clean[0]).toInt(16)
                    val g = ("" + clean[1] + clean[1]).toInt(16)
                    val b = ("" + clean[2] + clean[2]).toInt(16)
                    DeviceRgb(r, g, b)
                }
                else -> fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }

    private fun loadFont(spec: CalendarAiTemplateSpec): Pair<PdfFont, PdfFont> {
        var titleFont: PdfFont? = null
        var regularFont: PdfFont? = null

        if (!spec.customFontFileName.isNullOrBlank()) {
            try {
                val fontBytes = context.assets.open("fonts/${spec.customFontFileName}").readBytes()
                titleFont = PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao carregar fonte customizada: ${spec.customFontFileName}", e)
            }
        }

        if (titleFont == null) {
            titleFont = when (spec.fontFamily) {
                TemplateFontFamily.CURSIVE -> {
                    try {
                        val fontBytes = context.assets.open("fonts/Redressed.ttf").readBytes()
                        PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
                    } catch (_: Exception) {
                        PdfFontFactory.createFont()
                    }
                }
                TemplateFontFamily.SERIF -> {
                    try {
                        PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.TIMES_ROMAN)
                    } catch (_: Exception) {
                        PdfFontFactory.createFont()
                    }
                }
                TemplateFontFamily.MONO -> {
                    try {
                        PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.COURIER)
                    } catch (_: Exception) {
                        PdfFontFactory.createFont()
                    }
                }
                else -> PdfFontFactory.createFont()
            }
        }

        regularFont = try {
            PdfFontFactory.createFont()
        } catch (_: Exception) {
            titleFont
        }

        return Pair(titleFont, regularFont)
    }

    fun renderPdf(
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>
    ): File {
        val pageSize = when (template.pageSize.uppercase()) {
            "A3" -> if (template.isLandscape) PageSize.A3.rotate() else PageSize.A3
            "LETTER" -> if (template.isLandscape) PageSize.LETTER.rotate() else PageSize.LETTER
            else -> if (template.isLandscape) PageSize.A4.rotate() else PageSize.A4
        }

        val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "TheBigCalendar")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val fileName = "calendario_ai_${selectedMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))}_${System.currentTimeMillis() % 10000}.pdf"
        val outputFile = File(downloadsDir, fileName)

        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, pageSize)
        document.setMargins(16f, 16f, 16f, 16f)

        // Aplicar cor de fundo da página
        val pageBgColor = hexToColor(template.pageBackgroundColor, ColorConstants.WHITE)
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, object : IEventHandler {
            override fun handleEvent(event: Event?) {
                val docEvent = event as? PdfDocumentEvent ?: return
                val page = docEvent.page
                val canvas = PdfCanvas(page.newContentStreamBefore(), page.resources, docEvent.document)
                canvas.saveState()
                    .setFillColor(pageBgColor)
                    .rectangle(
                        page.pageSize.left.toDouble(),
                        page.pageSize.bottom.toDouble(),
                        page.pageSize.width.toDouble(),
                        page.pageSize.height.toDouble()
                    )
                    .fill()
                    .restoreState()
            }
        })

        try {
            val (titleFont, regularFont) = loadFont(template)

            when (template.layoutType) {
                CalendarLayoutType.SIDEBAR_LEFT -> {
                    renderSidebarLayout(
                        document = document,
                        template = template,
                        selectedMonth = selectedMonth,
                        activities = activities,
                        holidays = holidays,
                        jsonHolidays = jsonHolidays,
                        moonPhases = moonPhases,
                        titleFont = titleFont,
                        regularFont = regularFont,
                        isSidebarLeft = true
                    )
                }
                CalendarLayoutType.SIDEBAR_RIGHT -> {
                    renderSidebarLayout(
                        document = document,
                        template = template,
                        selectedMonth = selectedMonth,
                        activities = activities,
                        holidays = holidays,
                        jsonHolidays = jsonHolidays,
                        moonPhases = moonPhases,
                        titleFont = titleFont,
                        regularFont = regularFont,
                        isSidebarLeft = false
                    )
                }
                CalendarLayoutType.TOP_BOTTOM_SPLIT -> {
                    renderTopBottomSplitLayout(
                        document = document,
                        template = template,
                        selectedMonth = selectedMonth,
                        activities = activities,
                        holidays = holidays,
                        jsonHolidays = jsonHolidays,
                        moonPhases = moonPhases,
                        titleFont = titleFont,
                        regularFont = regularFont
                    )
                }
                CalendarLayoutType.MINIMALIST_CLEAN,
                CalendarLayoutType.PLANNER_BULLET,
                CalendarLayoutType.STANDARD_GRID -> {
                    renderStandardGridLayout(
                        document = document,
                        template = template,
                        selectedMonth = selectedMonth,
                        activities = activities,
                        holidays = holidays,
                        jsonHolidays = jsonHolidays,
                        moonPhases = moonPhases,
                        titleFont = titleFont,
                        regularFont = regularFont
                    )
                }
            }

            document.close()
            Log.d(TAG, "PDF gerado com sucesso: ${outputFile.absolutePath}")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Erro na renderização do PDF", e)
            try { document.close() } catch (_: Exception) {}
            throw e
        }
    }

    private fun renderSidebarLayout(
        document: Document,
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        titleFont: PdfFont,
        regularFont: PdfFont,
        isSidebarLeft: Boolean
    ) {
        val sidebarWidth = template.sidebarWidthPercent.coerceIn(20f, 40f)
        val gridWidth = 100f - sidebarWidth

        val columnWidths = if (isSidebarLeft) floatArrayOf(sidebarWidth, gridWidth) else floatArrayOf(gridWidth, sidebarWidth)
        val mainTable = Table(UnitValue.createPercentArray(columnWidths)).useAllAvailableWidth()
            .setBorder(Border.NO_BORDER)

        val sidebarCell = Cell().setBorder(Border.NO_BORDER).setPadding(4f)
        if (!template.sidebarBackgroundColor.isNullOrBlank()) {
            sidebarCell.setBackgroundColor(hexToColor(template.sidebarBackgroundColor))
        }

        // Conteúdo da barra lateral
        renderSidebarContent(sidebarCell, template, selectedMonth, titleFont, regularFont)

        // Conteúdo do grid
        val gridCell = Cell().setBorder(Border.NO_BORDER).setPadding(4f)
        renderGridContent(gridCell, template, selectedMonth, activities, holidays, jsonHolidays, moonPhases, titleFont, regularFont, showHeaderInGrid = !isSidebarLeft)

        if (isSidebarLeft) {
            mainTable.addCell(sidebarCell)
            mainTable.addCell(gridCell)
        } else {
            mainTable.addCell(gridCell)
            mainTable.addCell(sidebarCell)
        }

        document.add(mainTable)
    }

    private fun renderSidebarContent(
        sidebarCell: Cell,
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        titleFont: PdfFont,
        regularFont: PdfFont
    ) {
        val primaryColor = hexToColor(template.primaryColor, ColorConstants.BLACK)
        val secondaryColor = hexToColor(template.secondaryColor, ColorConstants.DARK_GRAY)

        val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase() }
        val yearString = selectedMonth.year.toString()

        // Título do Mês
        sidebarCell.add(
            Paragraph(monthName)
                .setFont(titleFont)
                .setFontSize(template.monthTitleFontSize)
                .setFontColor(primaryColor)
                .setMarginBottom(0f)
        )

        // Ano
        sidebarCell.add(
            Paragraph(yearString)
                .setFont(regularFont)
                .setFontSize(template.yearFontSize)
                .setFontColor(secondaryColor)
                .setMarginTop(0f)
                .setMarginBottom(12f)
        )

        // Metas na Barra Lateral
        if (template.goalsSection.enabled) {
            val goalsBox = Table(1).useAllAvailableWidth().setMarginBottom(10f)
            val headerCell = Cell().add(
                Paragraph(template.goalsSection.title.uppercase())
                    .setFont(regularFont)
                    .setFontSize(10f)
                    .setBold()
                    .setFontColor(primaryColor)
            ).setBorder(Border.NO_BORDER).setPadding(4f)

            if (!template.goalsSection.boxBackgroundColor.isNullOrBlank()) {
                headerCell.setBackgroundColor(hexToColor(template.goalsSection.boxBackgroundColor))
            }
            goalsBox.addCell(headerCell)

            for (i in 1..template.goalsSection.itemsCount) {
                goalsBox.addCell(
                    Cell().add(Paragraph("[  ] ____________________").setFontSize(9f).setFontColor(secondaryColor))
                        .setBorder(Border.NO_BORDER).setPadding(2f)
                )
            }
            sidebarCell.add(goalsBox)
        }

        // Seção de Anotações
        if (template.notesSection.enabled) {
            sidebarCell.add(
                Paragraph(template.notesSection.title)
                    .setFont(regularFont)
                    .setFontSize(11f)
                    .setBold()
                    .setFontColor(primaryColor)
                    .setMarginTop(8f)
                    .setMarginBottom(4f)
            )

            val linesCount = template.notesSection.linesCount.coerceIn(4, 20)
            when (template.notesSection.style) {
                NotesSectionStyle.DOTTED -> {
                    for (i in 1..linesCount) {
                        sidebarCell.add(
                            Paragraph(". . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .")
                                .setFontSize(8f)
                                .setFontColor(ColorConstants.LIGHT_GRAY)
                                .setMarginTop(-3f)
                        )
                    }
                }
                NotesSectionStyle.CHECKLIST -> {
                    for (i in 1..linesCount) {
                        sidebarCell.add(
                            Paragraph("[  ] ________________________________")
                                .setFontSize(8f)
                                .setFontColor(ColorConstants.GRAY)
                                .setMarginTop(1f)
                        )
                    }
                }
                NotesSectionStyle.LINED -> {
                    for (i in 1..linesCount) {
                        sidebarCell.add(
                            Paragraph("________________________________________")
                                .setFontSize(8f)
                                .setFontColor(ColorConstants.LIGHT_GRAY)
                                .setMarginTop(0f)
                        )
                    }
                }
                else -> {
                    // BLANK / GRID
                    val box = Table(1).useAllAvailableWidth().setHeight(100f)
                    box.addCell(Cell().setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f)))
                    sidebarCell.add(box)
                }
            }
        }

        // Frase Motivacional
        if (!template.motivationalQuote.isNullOrBlank()) {
            sidebarCell.add(
                Paragraph("\"${template.motivationalQuote}\"")
                    .setFont(titleFont)
                    .setFontSize(10f)
                    .setFontColor(secondaryColor)
                    .setItalic()
                    .setMarginTop(12f)
            )
        }
    }

    private fun renderGridContent(
        containerCell: Cell,
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        titleFont: PdfFont,
        regularFont: PdfFont,
        showHeaderInGrid: Boolean
    ) {
        val primaryColor = hexToColor(template.primaryColor, ColorConstants.BLACK)
        val secondaryColor = hexToColor(template.secondaryColor, ColorConstants.DARK_GRAY)

        if (showHeaderInGrid) {
            val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
                .replaceFirstChar { it.uppercase() }
            containerCell.add(
                Paragraph(monthName)
                    .setFont(titleFont)
                    .setFontSize(template.monthTitleFontSize)
                    .setFontColor(primaryColor)
                    .setTextAlignment(when (template.monthTitleAlignment) {
                        TitleAlignment.LEFT -> TextAlignment.LEFT
                        TitleAlignment.RIGHT -> TextAlignment.RIGHT
                        TitleAlignment.CENTER -> TextAlignment.CENTER
                    })
                    .setMarginBottom(6f)
            )
        }

        // Metas no Topo do Grid (se ativado)
        if (template.goalsSection.enabled && template.layoutType != CalendarLayoutType.SIDEBAR_LEFT) {
            val goalsTable = Table(1).useAllAvailableWidth().setMarginBottom(8f)
            val headerCell = Cell().add(
                Paragraph(template.goalsSection.title.uppercase())
                    .setFont(regularFont)
                    .setFontSize(9f)
                    .setBold()
                    .setFontColor(primaryColor)
            ).setBorder(Border.NO_BORDER).setPadding(4f).setPaddingLeft(10f)

            if (!template.goalsSection.boxBackgroundColor.isNullOrBlank()) {
                headerCell.setBackgroundColor(hexToColor(template.goalsSection.boxBackgroundColor))
            }
            goalsTable.addCell(headerCell)
            containerCell.add(goalsTable)
        }

        // Tabela do Calendário
        val calendarTable = Table(UnitValue.createPercentArray(7)).useAllAvailableWidth()

        // Cabeçalhos dos Dias da Semana
        val weekDayNames = if (template.weekStartsOnMonday) {
            when (template.weekdayFormat) {
                WeekDayDisplayFormat.FULL -> listOf("SEGUNDA", "TERÇA", "QUARTA", "QUINTA", "SEXTA", "SÁBADO", "DOMINGO")
                WeekDayDisplayFormat.SINGLE_LETTER -> listOf("S", "T", "Q", "Q", "S", "S", "D")
                WeekDayDisplayFormat.SHORT -> listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")
            }
        } else {
            when (template.weekdayFormat) {
                WeekDayDisplayFormat.FULL -> listOf("DOMINGO", "SEGUNDA", "TERÇA", "QUARTA", "QUINTA", "SEXTA", "SÁBADO")
                WeekDayDisplayFormat.SINGLE_LETTER -> listOf("D", "S", "T", "Q", "Q", "S", "S")
                WeekDayDisplayFormat.SHORT -> listOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SÁB")
            }
        }

        val weekdayTextColor = hexToColor(template.weekdayTextColor, ColorConstants.BLACK)
        val weekdayBgColor = if (!template.weekdayHeaderBackgroundColor.isNullOrBlank()) {
            hexToColor(template.weekdayHeaderBackgroundColor)
        } else null

        weekDayNames.forEach { dayName ->
            val headerCell = Cell().add(
                Paragraph(dayName)
                    .setFont(regularFont)
                    .setFontSize(template.weekdayFontSize)
                    .setBold()
                    .setFontColor(weekdayTextColor)
                    .setTextAlignment(TextAlignment.CENTER)
            ).setBorder(Border.NO_BORDER).setPadding(3f)

            if (weekdayBgColor != null) {
                headerCell.setBackgroundColor(weekdayBgColor)
            }
            calendarTable.addCell(headerCell)
        }

        // Dias do Calendário
        val firstDayOfMonth = selectedMonth.atDay(1)
        val firstDayOfWeekVal = firstDayOfMonth.dayOfWeek.value // 1 (Mon) a 7 (Sun)
        val offset = if (template.weekStartsOnMonday) {
            firstDayOfWeekVal - 1
        } else {
            if (firstDayOfWeekVal == 7) 0 else firstDayOfWeekVal
        }

        val startDate = firstDayOfMonth.minusDays(offset.toLong())
        val gridBgColor = hexToColor(template.gridBackgroundColor, ColorConstants.WHITE)
        val gridBorderColor = hexToColor(template.gridBorderColor, ColorConstants.LIGHT_GRAY)
        val cellHeight = template.dayCellHeight.coerceIn(35f, 90f)

        val dayNumberColor = hexToColor(template.dayNumberColor, ColorConstants.BLACK)
        val weekendColor = if (!template.weekendColor.isNullOrBlank()) hexToColor(template.weekendColor) else dayNumberColor
        val holidayColor = hexToColor(template.holidayColor, ColorConstants.RED)

        for (i in 0..41) {
            val date = startDate.plusDays(i.toLong())
            val isCurrentMonth = date.month == selectedMonth.month
            val isWeekend = date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY

            val cell = Cell().setMinHeight(cellHeight).setPadding(2f)
            cell.setBackgroundColor(gridBgColor)

            if (template.gridBorderWidth > 0f) {
                cell.setBorder(SolidBorder(gridBorderColor, template.gridBorderWidth))
            } else {
                cell.setBorder(Border.NO_BORDER)
            }

            if (isCurrentMonth) {
                val numColor = if (isWeekend) weekendColor else dayNumberColor

                // Linha superior da célula com Número e Fases da Lua
                val cellHeaderTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f))).useAllAvailableWidth().setBorder(Border.NO_BORDER)

                cellHeaderTable.addCell(
                    Cell().add(
                        Paragraph(date.dayOfMonth.toString())
                            .setFont(regularFont)
                            .setFontSize(template.dayNumberFontSize)
                            .setBold()
                            .setFontColor(numColor)
                    ).setBorder(Border.NO_BORDER).setPadding(0f)
                )

                // Fase da lua
                if (template.moonPhasesPosition == MoonPhaseDisplayPosition.IN_DAY_CELLS) {
                    val moon = moonPhases.firstOrNull { it.date == date }
                    if (moon != null) {
                        val moonSymbol = moon.phase.emoji
                        cellHeaderTable.addCell(
                            Cell().add(
                                Paragraph(moonSymbol)
                                    .setFontSize(8f)
                                    .setTextAlignment(TextAlignment.RIGHT)
                            ).setBorder(Border.NO_BORDER).setPadding(0f)
                        )
                    } else {
                        cellHeaderTable.addCell(Cell().setBorder(Border.NO_BORDER))
                    }
                } else {
                    cellHeaderTable.addCell(Cell().setBorder(Border.NO_BORDER))
                }

                cell.add(cellHeaderTable)

                // Eventos e Feriados do dia
                if (template.eventDisplayDensity != EventDisplayDensity.BLANK_FOR_WRITING) {
                    val dateStr = date.toString()
                    val dayHolidays = holidays.filter { it.date == dateStr }
                    val dayJsonHolidays = jsonHolidays.filter { it.date == dateStr }
                    val dayActivities = activities.filter { it.date == dateStr }

                    for (h in dayHolidays.take(1)) {
                        cell.add(
                            Paragraph("• ${h.name.take(18)}")
                                .setFont(regularFont)
                                .setFontSize(6.5f)
                                .setFontColor(holidayColor)
                                .setMarginTop(-1f)
                                .setMarginBottom(0f)
                        )
                    }

                    for (jh in dayJsonHolidays.take(1)) {
                        cell.add(
                            Paragraph("• ${jh.name.take(18)}")
                                .setFont(regularFont)
                                .setFontSize(6.5f)
                                .setFontColor(ColorConstants.BLUE)
                                .setMarginTop(-1f)
                                .setMarginBottom(0f)
                        )
                    }

                    for (act in dayActivities.take(2)) {
                        val actColor = when (act.categoryColor) {
                            "3" -> ColorConstants.ORANGE
                            "4" -> ColorConstants.RED
                            else -> ColorConstants.DARK_GRAY
                        }
                        cell.add(
                            Paragraph("▪ ${act.title.take(18)}")
                                .setFont(regularFont)
                                .setFontSize(6f)
                                .setFontColor(actColor)
                                .setMarginTop(-1f)
                                .setMarginBottom(0f)
                        )
                    }
                }

                // Linhas internas da célula (para escrita)
                if (template.showLinesInDayCells) {
                    for (l in 1..template.linesInDayCellsCount) {
                        cell.add(
                            Paragraph("........................................")
                                .setFontSize(5f)
                                .setFontColor(ColorConstants.LIGHT_GRAY)
                                .setMarginTop(-2f)
                        )
                    }
                }
            } else {
                // Outro mês
                cell.add(
                    Paragraph(date.dayOfMonth.toString())
                        .setFont(regularFont)
                        .setFontSize(template.dayNumberFontSize * 0.85f)
                        .setFontColor(ColorConstants.LIGHT_GRAY)
                )
            }

            calendarTable.addCell(cell)
        }

        containerCell.add(calendarTable)
    }

    private fun renderTopBottomSplitLayout(
        document: Document,
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        titleFont: PdfFont,
        regularFont: PdfFont
    ) {
        val primaryColor = hexToColor(template.primaryColor, ColorConstants.BLACK)
        val secondaryColor = hexToColor(template.secondaryColor, ColorConstants.DARK_GRAY)

        // Banner Superior
        val topTable = Table(UnitValue.createPercentArray(floatArrayOf(40f, 60f))).useAllAvailableWidth().setMarginBottom(8f)
        if (!template.headerBackgroundColor.isNullOrBlank()) {
            topTable.setBackgroundColor(hexToColor(template.headerBackgroundColor))
        }

        val monthName = selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase() }

        val titleCell = Cell().add(
            Paragraph(monthName)
                .setFont(titleFont)
                .setFontSize(template.monthTitleFontSize)
                .setFontColor(primaryColor)
        ).setBorder(Border.NO_BORDER).setPadding(8f)

        topTable.addCell(titleCell)

        // Metas no banner
        val goalsCell = Cell().setBorder(Border.NO_BORDER).setPadding(8f)
        if (template.goalsSection.enabled) {
            goalsCell.add(Paragraph(template.goalsSection.title).setFont(regularFont).setFontSize(10f).setBold().setFontColor(primaryColor))
            for (i in 1..template.goalsSection.itemsCount) {
                goalsCell.add(Paragraph("[  ] ________________________________").setFontSize(8f).setFontColor(secondaryColor))
            }
        }
        topTable.addCell(goalsCell)
        document.add(topTable)

        // Grid Central
        val gridCell = Cell().setBorder(Border.NO_BORDER)
        renderGridContent(gridCell, template, selectedMonth, activities, holidays, jsonHolidays, moonPhases, titleFont, regularFont, showHeaderInGrid = false)
        val gridContainer = Table(1).useAllAvailableWidth().setBorder(Border.NO_BORDER)
        gridContainer.addCell(gridCell)
        document.add(gridContainer)

        // Rodapé com Rastreador de Hábitos ou Notas
        if (template.habitTracker.enabled) {
            val habitTable = Table(UnitValue.createPercentArray(floatArrayOf(30f, 70f))).useAllAvailableWidth().setMarginTop(8f)
            val leftHabit = Cell().add(Paragraph(template.habitTracker.title).setFont(regularFont).setFontSize(9f).setBold().setFontColor(primaryColor))
                .setBorder(Border.NO_BORDER)
            habitTable.addCell(leftHabit)

            val daysCheckTable = Table(UnitValue.createPercentArray(FloatArray(31) { 1f })).useAllAvailableWidth()
            for (d in 1..31) {
                daysCheckTable.addCell(Cell().add(Paragraph(d.toString()).setFontSize(6f).setTextAlignment(TextAlignment.CENTER)).setPadding(1f))
            }
            val rightHabit = Cell().add(daysCheckTable).setBorder(Border.NO_BORDER)
            habitTable.addCell(rightHabit)

            document.add(habitTable)
        }
    }

    private fun renderStandardGridLayout(
        document: Document,
        template: CalendarAiTemplateSpec,
        selectedMonth: YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        titleFont: PdfFont,
        regularFont: PdfFont
    ) {
        val gridCell = Cell().setBorder(Border.NO_BORDER)
        renderGridContent(gridCell, template, selectedMonth, activities, holidays, jsonHolidays, moonPhases, titleFont, regularFont, showHeaderInGrid = true)

        val mainTable = Table(1).useAllAvailableWidth().setBorder(Border.NO_BORDER)
        mainTable.addCell(gridCell)

        // Seção inferior de anotações caso esteja habilitada na posição BOTTOM
        if (template.notesSection.enabled && template.notesSection.position == NotesPosition.BOTTOM) {
            val bottomCell = Cell().setBorder(Border.NO_BORDER).setPaddingTop(8f)
            bottomCell.add(
                Paragraph(template.notesSection.title)
                    .setFont(regularFont)
                    .setFontSize(10f)
                    .setBold()
                    .setFontColor(hexToColor(template.primaryColor))
            )
            for (i in 1..template.notesSection.linesCount.coerceIn(2, 6)) {
                bottomCell.add(
                    Paragraph("____________________________________________________________________________________")
                        .setFontSize(7f)
                        .setFontColor(ColorConstants.LIGHT_GRAY)
                        .setMarginTop(0f)
                )
            }
            mainTable.addCell(bottomCell)
        }

        document.add(mainTable)
    }
}
