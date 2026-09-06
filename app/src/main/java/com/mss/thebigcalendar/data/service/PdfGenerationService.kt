package com.mss.thebigcalendar.data.service

import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject
import com.itextpdf.kernel.geom.Rectangle
import com.mss.thebigcalendar.data.model.Activity
import com.mss.thebigcalendar.data.model.Holiday
import com.mss.thebigcalendar.data.model.JsonHoliday
import com.mss.thebigcalendar.ui.components.MoonPhase
import com.mss.thebigcalendar.ui.screens.PageOrientation
import com.mss.thebigcalendar.ui.screens.PrintOptions
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.mss.thebigcalendar.ui.screens.PageSize as CustomPageSize

import android.content.Context

class PdfGenerationService(private val context: Context) {
    
    private val dynamicPdfRenderer = DynamicPdfRenderer(context)
    
    /**
     * Converte uma cor do Compose para uma cor do iText PDF
     */
    private fun convertComposeColorToITextColor(composeColor: androidx.compose.ui.graphics.Color): Color {
        return DeviceRgb(
            composeColor.red,
            composeColor.green,
            composeColor.blue
        )
    }
    
    fun generateCalendarPdf(
        printOptions: PrintOptions,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>
    ): File {
        // Redirecionar se houver um modelo de IA selecionado
        if (printOptions.aiTemplateSpec != null) {
            return dynamicPdfRenderer.renderPdf(
                template = printOptions.aiTemplateSpec,
                selectedMonth = printOptions.selectedMonth,
                activities = activities,
                holidays = holidays,
                jsonHolidays = jsonHolidays,
                moonPhases = moonPhases
            )
        }

        // Redirecionar conforme o modelo selecionado
        when (printOptions.selectedModel) {
            "ideia1.jpeg" -> return generateIdea1Pdf(printOptions, activities, holidays, jsonHolidays, moonPhases)
            "ideia2.jpeg" -> return generateIdea2Pdf(printOptions, activities, holidays, jsonHolidays, moonPhases)
        }

        // Configurar página
        val pageSize = when (printOptions.pageSize) {
            CustomPageSize.A4 -> if (printOptions.orientation == PageOrientation.LANDSCAPE) 
                PageSize.A4.rotate() else PageSize.A4
            CustomPageSize.A3 -> if (printOptions.orientation == PageOrientation.LANDSCAPE) 
                PageSize.A3.rotate() else PageSize.A3
        }
        
        // Criar arquivo no diretório de Downloads (da própria aplicação para evitar problemas de permissão no Android 11+)
        val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "TheBigCalendar")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        val fileName = "calendario_${printOptions.selectedMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))}.pdf"
        val outputFile = File(downloadsDir, fileName)
        
        // Garantir que o arquivo seja sobrescrito se já existir
        if (outputFile.exists()) {
            outputFile.delete()
            android.util.Log.d("PdfGenerationService", "🗑️ Arquivo existente removido: ${outputFile.name}")
        }
        
        val writer = PdfWriter(outputFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf, pageSize)
        
        // Configurar margens mínimas para ocupar todo o espaço da página
        document.setMargins(20f, 20f, 20f, 20f)
        
        android.util.Log.d("PdfGenerationService", "📄 Criando novo arquivo PDF: ${outputFile.absolutePath}")
        
        // Aplicar cor de fundo da página
        val pageBackgroundColor = convertComposeColorToITextColor(printOptions.pageBackgroundColor)
        
        // Criar evento de página para aplicar cor de fundo
        pdf.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE, 
            object : com.itextpdf.kernel.events.IEventHandler {
                override fun handleEvent(event: com.itextpdf.kernel.events.Event?) {
                    val docEvent = event as? com.itextpdf.kernel.events.PdfDocumentEvent
                    if (docEvent != null) {
                        val pdfDoc = docEvent.document
                        val page = docEvent.page
                        val canvas = com.itextpdf.kernel.pdf.canvas.PdfCanvas(page.newContentStreamBefore(), page.resources, pdfDoc)
                        
                        val pageRect = page.pageSize
                        canvas
                            .saveState()
                            .setFillColor(pageBackgroundColor)
                            .rectangle(pageRect.left.toDouble(),
                                pageRect.bottom.toDouble(), pageRect.width.toDouble(), pageRect.height.toDouble()
                            )
                            .fill()
                            .restoreState()
                    }
                }
            })
        
        try {
            // Configurar fontes
            val fontPath = if (printOptions.fontFamily != "Default") {
                "fonts/${printOptions.fontFamily}"
            } else {
                null
            }

            val baseFont = if (fontPath != null) {
                try {
                    val fontBytes = context.assets.open(fontPath).readBytes()
                    PdfFontFactory.createFont(fontBytes, com.itextpdf.io.font.PdfEncodings.IDENTITY_H, com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
                } catch (e: java.io.IOException) {
                    android.util.Log.e("PdfGenerationService", "Error loading font: $fontPath", e)
                    PdfFontFactory.createFont() // Fallback to default
                }
            } else {
                PdfFontFactory.createFont() // Default
            }

            val titleFont = baseFont
            val headerFont = baseFont
            val dayFont = baseFont
            val contentFont = baseFont
            
            // Título do calendário - mês e ano na mesma linha
            val monthName = printOptions.selectedMonth.format(
                DateTimeFormatter.ofPattern("MMMM", Locale("pt", "BR"))
            ).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
            
            val yearNumber = printOptions.selectedMonth.format(
                DateTimeFormatter.ofPattern("yyyy", Locale("pt", "BR"))
            )
            
            // Criar tabela com 3 colunas para posicionar mês e ano na mesma linha
            val titleTable = Table(UnitValue.createPercentArray(3)).useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)
                .setMarginBottom(5f)
            
            // Preparar células vazias
            val leftContent = StringBuilder()
            val centerContent = StringBuilder()
            val rightContent = StringBuilder()
            
            // Adicionar mês à posição correta
            when (printOptions.monthPosition) {
                com.mss.thebigcalendar.ui.screens.TitlePosition.LEFT -> leftContent.append(monthName)
                com.mss.thebigcalendar.ui.screens.TitlePosition.CENTER -> centerContent.append(monthName)
                com.mss.thebigcalendar.ui.screens.TitlePosition.RIGHT -> rightContent.append(monthName)
            }
            
            // Adicionar ano à posição correta (com espaço se for na mesma célula que o mês)
            when (printOptions.yearPosition) {
                com.mss.thebigcalendar.ui.screens.TitlePosition.LEFT -> {
                    if (leftContent.isNotEmpty()) leftContent.append(" ")
                    leftContent.append(yearNumber)
                }
                com.mss.thebigcalendar.ui.screens.TitlePosition.CENTER -> {
                    if (centerContent.isNotEmpty()) centerContent.append(" ")
                    centerContent.append(yearNumber)
                }
                com.mss.thebigcalendar.ui.screens.TitlePosition.RIGHT -> {
                    if (rightContent.isNotEmpty()) rightContent.append(" ")
                    rightContent.append(yearNumber)
                }
            }
            
            // Determinar tamanho da fonte para cada célula
            // Se mês e ano estão na mesma célula, usar o tamanho do mês como padrão
            val leftFontSize = when {
                leftContent.contains(monthName) && leftContent.contains(yearNumber) -> printOptions.monthFontSize.size
                leftContent.contains(monthName) -> printOptions.monthFontSize.size
                leftContent.contains(yearNumber) -> printOptions.yearFontSize.size
                else -> 24f
            }
            
            val centerFontSize = when {
                centerContent.contains(monthName) && centerContent.contains(yearNumber) -> printOptions.monthFontSize.size
                centerContent.contains(monthName) -> printOptions.monthFontSize.size
                centerContent.contains(yearNumber) -> printOptions.yearFontSize.size
                else -> 24f
            }
            
            val rightFontSize = when {
                rightContent.contains(monthName) && rightContent.contains(yearNumber) -> printOptions.monthFontSize.size
                rightContent.contains(monthName) -> printOptions.monthFontSize.size
                rightContent.contains(yearNumber) -> printOptions.yearFontSize.size
                else -> 24f
            }
            
            // Determinar cor do texto para cada célula
            val leftTextColor = when {
                leftContent.contains(monthName) && leftContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                leftContent.contains(monthName) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                leftContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.yearTextColor)
                else -> com.itextpdf.kernel.colors.ColorConstants.BLACK
            }
            
            val centerTextColor = when {
                centerContent.contains(monthName) && centerContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                centerContent.contains(monthName) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                centerContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.yearTextColor)
                else -> com.itextpdf.kernel.colors.ColorConstants.BLACK
            }
            
            val rightTextColor = when {
                rightContent.contains(monthName) && rightContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                rightContent.contains(monthName) -> convertComposeColorToITextColor(printOptions.monthTextColor)
                rightContent.contains(yearNumber) -> convertComposeColorToITextColor(printOptions.yearTextColor)
                else -> com.itextpdf.kernel.colors.ColorConstants.BLACK
            }
            
            // Criar células com conteúdo, tamanhos de fonte e cores apropriados
            val leftCell = Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.LEFT)
            if (leftContent.isNotEmpty()) {
                leftCell.add(Paragraph(leftContent.toString())
                    .setFont(titleFont)
                    .setFontSize(leftFontSize)
                    .setFontColor(leftTextColor))
            }
            
            val centerCell = Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
            if (centerContent.isNotEmpty()) {
                centerCell.add(Paragraph(centerContent.toString())
                    .setFont(titleFont)
                    .setFontSize(centerFontSize)
                    .setFontColor(centerTextColor))
            }
            
            val rightCell = Cell()
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
            if (rightContent.isNotEmpty()) {
                rightCell.add(Paragraph(rightContent.toString())
                    .setFont(titleFont)
                    .setFontSize(rightFontSize)
                    .setFontColor(rightTextColor))
            }
            
            // Adicionar células à tabela
            titleTable.addCell(leftCell)
            titleTable.addCell(centerCell)
            titleTable.addCell(rightCell)
            
            document.add(titleTable)
            
            // Criar tabela do calendário
            val calendarTable = createCalendarTable(
                printOptions.selectedMonth,
                activities,
                holidays,
                jsonHolidays,
                moonPhases,
                printOptions,
                dayFont,
                contentFont,
                pdf,
                pageSize
            )
            
            document.add(calendarTable)
            
            // Adicionar legenda das fases da lua (se configurado)
            if (printOptions.includeMoonPhases && 
                printOptions.moonPhasePosition == com.mss.thebigcalendar.ui.screens.MoonPhasePosition.BELOW_CALENDAR) {
                val moonPhaseLegend = createMoonPhaseLegend(moonPhases, titleFont, pdf)
                document.add(moonPhaseLegend)
            }

            if (printOptions.includeNotesPage) {
                addNotesPage(document, pdf, pageSize, titleFont)
            }
            
        } finally {
            document.close()
        }
        
        return outputFile
    }
    
    private fun createCalendarTable(
        month: java.time.YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        printOptions: PrintOptions,
        dayFont: com.itextpdf.kernel.font.PdfFont,
        contentFont: com.itextpdf.kernel.font.PdfFont,
        pdfDocument: PdfDocument,
        pageSize: PageSize
    ): Table {
        
        // Criar tabela 7x6 (7 dias da semana, 6 semanas máximo)
        val table = Table(UnitValue.createPercentArray(7)).useAllAvailableWidth()
            .setMarginTop(2f)
        
        // Cabeçalho com dias da semana
        val weekDays = when (printOptions.weekDayAbbreviation) {
            com.mss.thebigcalendar.ui.screens.WeekDayAbbreviation.SHORT -> listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
            com.mss.thebigcalendar.ui.screens.WeekDayAbbreviation.FULL -> listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")
        }
        weekDays.forEachIndexed { index, day ->
            val headerBackgroundColor = if (printOptions.colorWeekDayHeader) {
                convertComposeColorToITextColor(printOptions.weekDayHeaderBackgroundColors[index])
            } else {
                ColorConstants.LIGHT_GRAY
            }
            val cell = Cell()
                .add(Paragraph(day)
                    .setFont(dayFont)
                    .setFontSize(printOptions.weekDayFontSize.size)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBold())
                .setBackgroundColor(headerBackgroundColor)
                .setBorder(SolidBorder(1f))
                .setPadding(2f)
                .setMinHeight(15f)
            table.addCell(cell)
        }
        
        // Obter primeiro dia do mês e ajustar para domingo
        val firstDayOfMonth = month.atDay(1)
        val firstSunday = firstDayOfMonth.minusDays((firstDayOfMonth.dayOfWeek.value % 7).toLong())
        
        // Gerar células do calendário (6 semanas)
        for (week in 0..5) {
            for (dayOfWeek in 0..6) {
                val currentDate = firstSunday.plusDays(((week * 7) + dayOfWeek).toLong())
                val cell = createDayCell(
                    currentDate,
                    month,
                    activities,
                    holidays,
                    jsonHolidays,
                    moonPhases,
                    printOptions,
                    dayFont,
                    contentFont,
                    pdfDocument,
                    pageSize
                )
                table.addCell(cell)
            }
        }
        
        return table
    }
    
    private fun cmToPoints(cm: Float): Float {
        return cm * 28.35f
    }

    private fun createDayCell(
        date: LocalDate,
        month: java.time.YearMonth,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        printOptions: PrintOptions,
        dayFont: com.itextpdf.kernel.font.PdfFont,
        contentFont: com.itextpdf.kernel.font.PdfFont,
        pdfDocument: PdfDocument,
        pageSize: PageSize
    ): Cell {
        
        val cell = Cell()
            .setPadding(1f)
            .setMinHeight(cmToPoints(printOptions.dayCellHeight))
        
        // Configurar bordas baseado na opção
        if (printOptions.showDayBorders) {
            cell.setBorder(SolidBorder(1f))
        } else {
            cell.setBorder(Border.NO_BORDER)
        }
        
        // Verificar se é do mês atual
        val isCurrentMonth = date.month == month.month
        
        // Conteúdo do dia
        val dayContent = getDayContent(date, activities, holidays, jsonHolidays, moonPhases, printOptions)

        if (printOptions.showLinesInDayCells) {
            cell.setNextRenderer(LinedCellRenderer(cell, dayContent, contentFont, isCurrentMonth))
        }

        if (!isCurrentMonth) {
            // Dias de outros meses
            cell.setBackgroundColor(convertComposeColorToITextColor(printOptions.backgroundColor))
            
            if (!printOptions.hideOtherMonthDays) {
                cell.add(Paragraph(date.dayOfMonth.toString())
                    .setFont(dayFont)
                    .setFontSize(printOptions.dayNumberFontSize.size * 0.7f)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setFontColor(ColorConstants.GRAY))
            }
            
            if (!printOptions.showLinesInDayCells) {
                dayContent.forEach { content ->
                    val contentParagraph = Paragraph(content)
                        .setFont(contentFont)
                        .setFontSize(8f)
                        .setTextAlignment(TextAlignment.LEFT)
                        .setMarginTop(2f)
                        .setFontColor(ColorConstants.GRAY)
                    
                    cell.add(contentParagraph)
                }
            }
        } else {
            // Dias do mês atual
            val backgroundColor = convertComposeColorToITextColor(printOptions.backgroundColor)
            cell.setBackgroundColor(backgroundColor)

            val dayNumberColor = when {
                // Prioridade 1: Colorir domingos se a opção estiver ativa
                printOptions.colorSundays && date.dayOfWeek == java.time.DayOfWeek.SUNDAY -> {
                    convertComposeColorToITextColor(printOptions.sundayColor)
                }
                // Prioridade 2: Colorir sábados se a opção estiver ativa
                printOptions.colorSaturdays && date.dayOfWeek == java.time.DayOfWeek.SATURDAY -> {
                    convertComposeColorToITextColor(printOptions.saturdayColor)
                }
                // Lógica existente para colorir por eventos
                printOptions.colorDayNumbersByEvents -> {
                    getDayNumberColor(date, activities, holidays, jsonHolidays, printOptions)
                }
                // Cor padrão
                else -> {
                    com.itextpdf.kernel.colors.ColorConstants.BLACK
                }
            }

            val dayParagraph = Paragraph(date.dayOfMonth.toString())
                .setFont(dayFont)
                .setFontSize(printOptions.dayNumberFontSize.size)
                .setTextAlignment(TextAlignment.CENTER)
                .setBold()
                .setFontColor(dayNumberColor)
            
            cell.add(dayParagraph)
            
            if (!printOptions.showLinesInDayCells) {
                dayContent.forEach { content ->
                    val contentParagraph = Paragraph(content)
                        .setFont(contentFont)
                        .setFontSize(8f)
                        .setTextAlignment(TextAlignment.LEFT)
                        .setMarginTop(2f)
                    
                    cell.add(contentParagraph)
                }
            }
        }
        
        return cell
    }
    
    /**
     * Cria um desenho vetorial da fase da lua
     */
    private fun createMoonPhaseImage(
        moonPhase: MoonPhase,
        pdfDocument: PdfDocument,
        size: Float = 30f
    ): Image {
        // Criar form XObject para desenhar a lua
        val form = PdfFormXObject(Rectangle(size, size))
        val canvas = PdfCanvas(form, pdfDocument)
        
        val centerX = (size / 2f).toDouble()
        val centerY = (size / 2f).toDouble()
        val radius = (size / 2.2f).toDouble()
        
        canvas.saveState()
        
        // Desenhar círculo externo (borda da lua)
        canvas.setStrokeColor(ColorConstants.DARK_GRAY)
        canvas.setLineWidth(1.5f)
        canvas.circle(centerX, centerY, radius)
        canvas.stroke()
        
        // Desenhar preenchimento baseado na fase
        when (moonPhase.phase) {
            com.mss.thebigcalendar.ui.components.MoonPhaseType.NEW_MOON -> {
                // Lua nova - círculo preto completo
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.FULL_MOON -> {
                // Lua cheia - círculo branco completo
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.FIRST_QUARTER -> {
                // Quarto crescente - metade direita branca
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.rectangle(centerX, centerY - radius, radius, radius * 2.0)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.LAST_QUARTER -> {
                // Quarto minguante - metade esquerda branca
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.rectangle(centerX - radius, centerY - radius, radius, radius * 2.0)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_CRESCENT -> {
                // Crescente - pequena fatia à direita
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.moveTo(centerX, centerY - radius)
                canvas.lineTo(centerX + (radius * 0.5), centerY)
                canvas.lineTo(centerX, centerY + radius)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_GIBBOUS -> {
                // Gibosa crescente - maior parte branca à direita
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.rectangle(centerX - radius, centerY - radius, radius * 0.5, radius * 2.0)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_CRESCENT -> {
                // Minguante - pequena fatia à esquerda
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.moveTo(centerX, centerY - radius)
                canvas.lineTo(centerX - (radius * 0.5), centerY)
                canvas.lineTo(centerX, centerY + radius)
                canvas.fill()
            }
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_GIBBOUS -> {
                // Gibosa minguante - maior parte branca à esquerda
                canvas.setFillColor(ColorConstants.WHITE)
                canvas.circle(centerX, centerY, radius)
                canvas.fill()
                canvas.setFillColor(ColorConstants.BLACK)
                canvas.rectangle(centerX + (radius * 0.5), centerY - radius, radius * 0.5, radius * 2.0)
                canvas.fill()
            }
        }
        
        // Redesenhar borda por cima
        canvas.setStrokeColor(ColorConstants.DARK_GRAY)
        canvas.setLineWidth(1.5f)
        canvas.circle(centerX, centerY, radius)
        canvas.stroke()
        
        canvas.restoreState()
        
        return Image(form)
    }
    
    /**
     * Adiciona o desenho da fase da lua em uma célula
     */
    private fun addMoonPhaseDrawing(
        cell: Cell,
        moonPhase: MoonPhase,
        font: com.itextpdf.kernel.font.PdfFont,
        pdfDocument: PdfDocument
    ) {
        // Criar e adicionar desenho da lua
        val moonImage = createMoonPhaseImage(moonPhase, pdfDocument, 35f)
        cell.add(Paragraph().add(moonImage)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(8f))
        
        // Adicionar nome da fase (opcional, pequeno)
        val phaseName = when (moonPhase.phase) {
            com.mss.thebigcalendar.ui.components.MoonPhaseType.NEW_MOON -> "Nova"
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_CRESCENT -> "Crescente"
            com.mss.thebigcalendar.ui.components.MoonPhaseType.FIRST_QUARTER -> "Qto. Cresc."
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_GIBBOUS -> "Gib. Cresc."
            com.mss.thebigcalendar.ui.components.MoonPhaseType.FULL_MOON -> "Cheia"
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_GIBBOUS -> "Gib. Ming."
            com.mss.thebigcalendar.ui.components.MoonPhaseType.LAST_QUARTER -> "Qto. Ming."
            com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_CRESCENT -> "Minguante"
        }
        
        cell.add(Paragraph(phaseName)
            .setFont(font)
            .setFontSize(7f)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginTop(2f))
    }
    
    /**
     * Cria uma legenda com todas as fases da lua do mês
     */
    private fun createMoonPhaseLegend(
        moonPhases: List<MoonPhase>,
        font: com.itextpdf.kernel.font.PdfFont,
        pdfDocument: PdfDocument
    ): Table {
        // Criar tabela para a legenda (todas as fases na horizontal, mas 50% da largura)
        val numColumns = moonPhases.size.coerceAtLeast(1)
        val legendTable = Table(UnitValue.createPercentArray(numColumns))
            .setWidth(UnitValue.createPercentValue(50f))  // 50% da largura
            .setBorder(Border.NO_BORDER)
            .setMarginTop(10f)
        
        moonPhases.forEach { moonPhase ->
            val cell = Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(4f)
            
            // Desenho vetorial da fase (menor para caber na legenda compacta)
            val moonImage = createMoonPhaseImage(moonPhase, pdfDocument, 20f)
            cell.add(Paragraph().add(moonImage)
                .setTextAlignment(TextAlignment.CENTER))
            
            // Nome da fase (abreviado)
            val phaseName = when (moonPhase.phase) {
                com.mss.thebigcalendar.ui.components.MoonPhaseType.NEW_MOON -> "Nova"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_CRESCENT -> "Cresc."
                com.mss.thebigcalendar.ui.components.MoonPhaseType.FIRST_QUARTER -> "Qto.C"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.WAXING_GIBBOUS -> "Gib.C"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.FULL_MOON -> "Cheia"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_GIBBOUS -> "Gib.M"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.LAST_QUARTER -> "Qto.M"
                com.mss.thebigcalendar.ui.components.MoonPhaseType.WANING_CRESCENT -> "Ming."
            }
            
            cell.add(Paragraph(phaseName)
                .setFont(font)
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.CENTER))
            
            // Data
            val dateFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale("pt", "BR"))
            cell.add(Paragraph(moonPhase.date.format(dateFormatter))
                .setFont(font)
                .setFontSize(7f)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY))
            
            legendTable.addCell(cell)
        }
        
        // Aplicar estilo à tabela (50% da largura, horizontal)
        legendTable.setBorder(SolidBorder(1f))
        legendTable.setBackgroundColor(DeviceRgb(0.9f, 0.9f, 0.9f))
        
        return legendTable
    }
    
    /**
     * Determina a cor do número do dia baseado nos eventos daquele dia
     * Prioridade: Feriados > Dias de Santos > Aniversários > Eventos > Tarefas > Notas
     */
    private fun getDayNumberColor(
        date: LocalDate,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        printOptions: PrintOptions
    ): Color {
        
        // Verificar feriados (maior prioridade)
        if (printOptions.includeHolidays) {
            val hasHoliday = holidays.any { LocalDate.parse(it.date) == date } ||
                           jsonHolidays.any { LocalDate.parse(it.date) == date }
            if (hasHoliday) {
                return convertComposeColorToITextColor(printOptions.holidayColor)
            }
        }
        
        
        
        // Verificar aniversários
        if (printOptions.includeBirthdays) {
            val hasBirthday = activities.any { 
                LocalDate.parse(it.date) == date && 
                it.activityType == com.mss.thebigcalendar.data.model.ActivityType.BIRTHDAY
            }
            if (hasBirthday) {
                return convertComposeColorToITextColor(printOptions.birthdayColor)
            }
        }
        
        // Verificar eventos
        if (printOptions.includeEvents) {
            val hasEvent = activities.any { 
                LocalDate.parse(it.date) == date && 
                it.activityType == com.mss.thebigcalendar.data.model.ActivityType.EVENT
            }
            if (hasEvent) {
                return convertComposeColorToITextColor(printOptions.eventColor)
            }
        }
        
        // Verificar tarefas
        if (printOptions.includeTasks) {
            val hasTask = activities.any { 
                LocalDate.parse(it.date) == date && 
                it.activityType == com.mss.thebigcalendar.data.model.ActivityType.TASK
            }
            if (hasTask) {
                return convertComposeColorToITextColor(printOptions.taskColor)
            }
        }
        
        // Verificar notas
        if (printOptions.includeNotes) {
            val hasNote = activities.any { 
                LocalDate.parse(it.date) == date && 
                it.activityType == com.mss.thebigcalendar.data.model.ActivityType.NOTE
            }
            if (hasNote) {
                return convertComposeColorToITextColor(printOptions.noteColor)
            }
        }
        
        // Se não há eventos, retornar preto
        return com.itextpdf.kernel.colors.ColorConstants.BLACK
    }
    
    private inner class LinedCellRenderer(cell: Cell, private val dayContent: List<String>, private val contentFont: com.itextpdf.kernel.font.PdfFont, private val isCurrentMonth: Boolean) : com.itextpdf.layout.renderer.CellRenderer(cell) {
        override fun draw(drawContext: com.itextpdf.layout.renderer.DrawContext) {
            super.draw(drawContext)
            val canvas = drawContext.canvas
            val box = occupiedArea.bBox
            val lineHeight = 10f
            var y = box.top - 34f

            val lineColor = if (isCurrentMonth) com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY else com.itextpdf.kernel.colors.DeviceRgb(0.9f, 0.9f, 0.9f)
            val textColor = if (isCurrentMonth) com.itextpdf.kernel.colors.ColorConstants.BLACK else com.itextpdf.kernel.colors.ColorConstants.GRAY
            canvas.setStrokeColor(lineColor)

            val cellWidth = box.width - 10f // 5f padding on each side
            val allLines = mutableListOf<String>()
            for (text in dayContent) {
                allLines.addAll(splitText(text, contentFont, 8f, cellWidth))
            }

            // Draw lines and text
            var i = 0
            while (y > box.bottom + 5f) {
                // Draw line
                canvas.moveTo((box.left + 2f).toDouble(), y.toDouble())
                canvas.lineTo((box.right - 2f).toDouble(), y.toDouble())
                canvas.stroke()

                // Draw text
                if (i < allLines.size) {
                    val line = allLines[i]
                    canvas.beginText()
                        .setFontAndSize(contentFont, 8f)
                        .setColor(textColor, true)
                        .moveText((box.left + 5f).toDouble(), (y + 2f).toDouble())
                        .showText(line)
                        .endText()
                }

                y -= lineHeight
                i++
            }
        }
    }

    private fun splitText(text: String, font: com.itextpdf.kernel.font.PdfFont, fontSize: Float, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in text.split(" ")) {
            if (font.getWidth(currentLine + " " + word, fontSize) > maxWidth) {
                lines.add(currentLine)
                currentLine = word
            } else {
                if (currentLine.isNotEmpty()) {
                    currentLine += " "
                }
                currentLine += word
            }
        }
        lines.add(currentLine)
        return lines
    }

    private fun getDayContent(
        date: LocalDate,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>,
        printOptions: PrintOptions
    ): List<String> {
        
        val dayContent = mutableListOf<String>()
        
        // Feriados nacionais
        if (printOptions.includeHolidays) {
            holidays.filter { holiday ->
                LocalDate.parse(holiday.date) == date
            }.forEach { holiday ->
                dayContent.add("🎉 ${holiday.name}")
            }
            
            jsonHolidays.filter { jsonHoliday ->
                LocalDate.parse(jsonHoliday.date) == date
            }.forEach { jsonHoliday ->
                dayContent.add("📅 ${jsonHoliday.name}")
            }
        }
        
        
        
        // Tarefas
        if (printOptions.includeTasks) {
            activities.filter { activity ->
                LocalDate.parse(activity.date) == date &&
                activity.activityType == com.mss.thebigcalendar.data.model.ActivityType.TASK
            }.forEach { activity ->
                val icon = if (activity.isCompleted == true) "✅" else "📝"
                dayContent.add("$icon ${activity.title}")
            }
        }
        
        // Eventos
        if (printOptions.includeEvents) {
            activities.filter { activity ->
                LocalDate.parse(activity.date) == date &&
                activity.activityType == com.mss.thebigcalendar.data.model.ActivityType.EVENT
            }.forEach { activity ->
                dayContent.add("🎪 ${activity.title}")
            }
        }
        
        // Aniversários
        if (printOptions.includeBirthdays) {
            activities.filter { activity ->
                LocalDate.parse(activity.date) == date &&
                activity.activityType == com.mss.thebigcalendar.data.model.ActivityType.BIRTHDAY
            }.forEach { activity ->
                dayContent.add("🎂 ${activity.title}")
            }
        }
        
        // Notas
        if (printOptions.includeNotes) {
            activities.filter { activity ->
                LocalDate.parse(activity.date) == date &&
                activity.activityType == com.mss.thebigcalendar.data.model.ActivityType.NOTE
            }.forEach { activity ->
                dayContent.add("📝 ${activity.title}")
            }
        }
        
        // Tarefas completadas (separado das tarefas normais)
        if (printOptions.includeCompletedTasks) {
            activities.filter { activity ->
                LocalDate.parse(activity.date) == date &&
                activity.activityType == com.mss.thebigcalendar.data.model.ActivityType.TASK &&
                activity.isCompleted == true
            }.forEach { activity ->
                dayContent.add("✅ ${activity.title}")
            }
        }
        
        // Fases da Lua - não adicionar aqui se estiverem sendo mostradas em outro lugar
        // (nos dias de outros meses ou na legenda abaixo)
        // Comentado para evitar duplicação
        // if (printOptions.includeMoonPhases) {
        //     moonPhases.filter { moonPhase ->
        //         moonPhase.date == date
        //     }.forEach { moonPhase ->
        //         dayContent.add("🌙 ${moonPhase.phase}")
        //     }
        // }
        
        // Limitar a 4 itens por dia para não sobrecarregar
        return dayContent.take(4)
    }

    private fun generateIdea1Pdf(
        printOptions: PrintOptions,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>
    ): File {
        // Cores do Modelo
        val peachColor = DeviceRgb(255, 235, 235) // Fundo geral suave
        val accentPink = DeviceRgb(240, 178, 178) // Rosa do cabeçalho de metas e rodapé
        val darkGrey = DeviceRgb(80, 80, 80)

        // Configurar página (sempre paisagem para este modelo se basear na imagem)
        val pageSize = if (printOptions.orientation == PageOrientation.LANDSCAPE)
            PageSize.A4.rotate() else PageSize.A4

        val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "TheBigCalendar")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val fileName = "calendario_design1_${printOptions.selectedMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))}.pdf"
        val outputFile = File(downloadsDir, fileName)

        val writer = PdfWriter(outputFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf, pageSize)
        document.setMargins(20f, 20f, 20f, 20f)

        // Fundo da página
        pdf.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE) { event ->
            val docEvent = event as com.itextpdf.kernel.events.PdfDocumentEvent
            val page = docEvent.page
            PdfCanvas(page.newContentStreamBefore(), page.resources, docEvent.document)
                .saveState()
                .setFillColor(peachColor)
                .rectangle(page.pageSize.left.toDouble(), page.pageSize.bottom.toDouble(),
                           page.pageSize.width.toDouble(), page.pageSize.height.toDouble())
                .fill()
                .restoreState()
        }

        try {
            // Fontes
            val scriptFontBytes = context.assets.open("fonts/Redressed.ttf").readBytes()
            val scriptFont = PdfFontFactory.createFont(scriptFontBytes, com.itextpdf.io.font.PdfEncodings.IDENTITY_H, com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED)
            val regularFont = PdfFontFactory.createFont()

            // Layout principal: Coluna esquerda (25%) e Coluna direita (75%)
            val mainTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 75f))).useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)

            // COLUNA ESQUERDA
            val leftCell = Cell().setBorder(Border.NO_BORDER).setPaddingRight(10f)

            // Mês e Ano
            val monthName = printOptions.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale("pt", "BR")))
                .replaceFirstChar { it.uppercase() }
            leftCell.add(Paragraph(monthName).setFont(scriptFont).setFontSize(45f).setFontColor(darkGrey).setMarginTop(10f))
            leftCell.add(Paragraph(printOptions.selectedMonth.year.toString()).setFont(regularFont).setFontSize(14f).setFontColor(darkGrey).setMarginTop(-10f))

            // Anotações
            leftCell.add(Paragraph("Anotações").setFont(regularFont).setFontSize(12f).setBold().setMarginTop(30f).setFontColor(darkGrey))
            for (i in 1..15) {
                leftCell.add(Paragraph("..................................................").setFontSize(10f).setFontColor(ColorConstants.LIGHT_GRAY).setMarginTop(-2f))
            }
            mainTable.addCell(leftCell)

            // COLUNA DIREITA
            val rightCell = Cell().setBorder(Border.NO_BORDER)

            // Metas Mensais
            val goalsTable = Table(1).useAllAvailableWidth().setMarginBottom(10f)
            val goalsHeader = Cell().add(Paragraph("METAS MENSAIS").setFontSize(10f).setBold().setFontColor(darkGrey))
                .setBackgroundColor(accentPink)
                .setBorder(Border.NO_BORDER)
                .setPadding(5f)
                .setPaddingLeft(15f)
            goalsTable.addCell(goalsHeader)
            rightCell.add(goalsTable)

            // Grade do Calendário
            val calendarTable = Table(UnitValue.createPercentArray(7)).useAllAvailableWidth()

            // Dias da Semana
            val weekDays = listOf("SEGUNDA", "TERÇA", "QUARTA", "QUINTA", "SEXTA", "SÁBADO", "DOMINGO")
            weekDays.forEach { day ->
                calendarTable.addCell(Cell().add(Paragraph(day).setFontSize(8f).setBold().setFontColor(darkGrey))
                    .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER))
            }

            // Lógica de dias (ajustado para começar na segunda conforme imagem)
            val firstDayOfMonth = printOptions.selectedMonth.atDay(1)
            // No iText/Java, 1=Segunda, 7=Domingo. Ajustamos o offset.
            val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value - 1
            val firstDateToShow = firstDayOfMonth.minusDays(dayOfWeekOffset.toLong())

            for (i in 0..41) {
                val currentDate = firstDateToShow.plusDays(i.toLong())
                val isCurrentMonth = currentDate.month == printOptions.selectedMonth.month

                val dayCell = Cell().setMinHeight(55f).setPadding(2f)

                // Estilo da célula conforme imagem (fundo branco, bordas arredondadas simuladas com bordas normais finas)
                dayCell.setBackgroundColor(ColorConstants.WHITE)
                dayCell.setBorder(SolidBorder(ColorConstants.LIGHT_GRAY, 0.5f))

                if (isCurrentMonth) {
                    val dayHeader = Paragraph(currentDate.dayOfMonth.toString())
                        .setFontSize(10f).setFontColor(accentPink).setTextAlignment(TextAlignment.RIGHT).setBold()
                    dayCell.add(dayHeader)

                    // Conteúdo do dia (feriados, etc)
                    getDayContent(currentDate, activities, holidays, jsonHolidays, moonPhases, printOptions).forEach { content ->
                        dayCell.add(Paragraph(content).setFontSize(6f).setFontColor(darkGrey).setMarginTop(0f))
                    }
                } else {
                    dayCell.setBackgroundColor(peachColor)
                }

                calendarTable.addCell(dayCell)
            }

            rightCell.add(calendarTable)

            // Rodapé rosa da grade (decorativo conforme imagem)
            val footerPink = Table(1).useAllAvailableWidth().setMarginTop(-2f)
            footerPink.addCell(Cell().setMinHeight(15f).setBackgroundColor(accentPink).setBorder(Border.NO_BORDER))
            rightCell.add(footerPink)

            mainTable.addCell(rightCell)
            document.add(mainTable)

        } finally {
            document.close()
        }
        return outputFile
    }

    private fun generateIdea2Pdf(
        printOptions: PrintOptions,
        activities: List<Activity>,
        holidays: List<Holiday>,
        jsonHolidays: List<JsonHoliday>,
        moonPhases: List<MoonPhase>
    ): File {
        // Cores do Modelo 2
        val darkGreyBg = DeviceRgb(51, 51, 51) // #333333
        val lightGreyLines = DeviceRgb(200, 200, 200)

        val pageSize = if (printOptions.orientation == PageOrientation.LANDSCAPE)
            PageSize.A4.rotate() else PageSize.A4

        val downloadsDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "TheBigCalendar")
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        val fileName = "calendario_design2_${printOptions.selectedMonth.format(DateTimeFormatter.ofPattern("yyyy_MM"))}.pdf"
        val outputFile = File(downloadsDir, fileName)

        val writer = PdfWriter(outputFile)
        val pdf = PdfDocument(writer)
        val document = Document(pdf, pageSize)
        document.setMargins(15f, 15f, 15f, 15f)

        // Fundo Escuro da Página
        pdf.addEventHandler(com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE) { event ->
            val docEvent = event as com.itextpdf.kernel.events.PdfDocumentEvent
            val page = docEvent.page
            PdfCanvas(page.newContentStreamBefore(), page.resources, docEvent.document)
                .saveState()
                .setFillColor(darkGreyBg)
                .rectangle(page.pageSize.left.toDouble(), page.pageSize.bottom.toDouble(),
                           page.pageSize.width.toDouble(), page.pageSize.height.toDouble())
                .fill()
                .restoreState()
        }

        try {
            val regularFont = PdfFontFactory.createFont()
            val boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD)

            // Header: Mês e Ano no canto superior direito
            val headerTable = Table(1).useAllAvailableWidth().setMarginBottom(10f)
            val monthName = printOptions.selectedMonth.format(DateTimeFormatter.ofPattern("MMMM", Locale("pt", "BR")))
                .replaceFirstChar { it.uppercase() }
            val year = printOptions.selectedMonth.year.toString()

            headerTable.addCell(Cell().add(Paragraph("$monthName $year").setFont(regularFont).setFontSize(32f).setFontColor(ColorConstants.WHITE))
                .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT).setPaddingRight(20f))
            document.add(headerTable)

            // Layout principal: Grade (60%) e Anotações (40%)
            val mainTable = Table(UnitValue.createPercentArray(floatArrayOf(60f, 40f))).useAllAvailableWidth()
                .setBorder(Border.NO_BORDER)

            // COLUNA ESQUERDA: Grade do Calendário
            val leftCell = Cell().setBorder(Border.NO_BORDER).setPaddingRight(10f)
            val calendarTable = Table(UnitValue.createPercentArray(7)).useAllAvailableWidth()

            // Dias da Semana (D S T Q Q S S)
            val weekDays = listOf("D", "S", "T", "Q", "Q", "S", "S")
            weekDays.forEach { day ->
                calendarTable.addCell(Cell().add(Paragraph(day).setFontSize(14f).setBold().setFontColor(ColorConstants.WHITE))
                    .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER).setPaddingBottom(5f))
            }

            // Lógica de dias (Domingo a Sábado)
            val firstDayOfMonth = printOptions.selectedMonth.atDay(1)
            val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7
            val firstDateToShow = firstDayOfMonth.minusDays(dayOfWeekOffset.toLong())

            for (i in 0..41) {
                val currentDate = firstDateToShow.plusDays(i.toLong())
                val isCurrentMonth = currentDate.month == printOptions.selectedMonth.month

                val dayCell = Cell().setMinHeight(45f).setPadding(3f)
                dayCell.setBackgroundColor(ColorConstants.WHITE)
                dayCell.setBorder(SolidBorder(darkGreyBg, 1f))

                if (isCurrentMonth) {
                    val dayHeader = Paragraph(currentDate.dayOfMonth.toString())
                        .setFontSize(16f).setFontColor(darkGreyBg).setTextAlignment(TextAlignment.RIGHT)
                    dayCell.add(dayHeader)

                    // Feriados/Eventos compactos
                    getDayContent(currentDate, activities, holidays, jsonHolidays, moonPhases, printOptions).take(2).forEach { content ->
                        dayCell.add(Paragraph(content).setFontSize(6f).setFontColor(darkGreyBg).setMarginTop(0f))
                    }
                } else {
                    dayCell.setBackgroundColor(darkGreyBg)
                    dayCell.setBorder(Border.NO_BORDER)
                }
                calendarTable.addCell(dayCell)
            }
            leftCell.add(calendarTable)
            mainTable.addCell(leftCell)

            // COLUNA DIREITA: Anotações e Feriados
            val rightCell = Cell().setBorder(Border.NO_BORDER)
            val notesBox = Cell().setBackgroundColor(ColorConstants.WHITE).setPadding(15f).setMinHeight(335f)
                .setBorder(Border.NO_BORDER)

            notesBox.add(Paragraph("Anotações").setFont(regularFont).setFontSize(16f).setFontColor(darkGreyBg).setMarginBottom(10f))

            // Linhas horizontais de anotações
            for (i in 1..14) {
                notesBox.add(Paragraph("").setBorderBottom(SolidBorder(lightGreyLines, 0.5f)).setMarginBottom(15f))
            }

            // Legenda de feriados no rodapé das anotações
            val holidayList = mutableListOf<String>()
            val currentMonthHolidays = holidays.filter {
                try {
                    java.time.LocalDate.parse(it.date).month == printOptions.selectedMonth.month
                } catch(e: Exception) { false }
            }
            currentMonthHolidays.forEach { h ->
                val date = java.time.LocalDate.parse(h.date)
                holidayList.add("*${date.dayOfMonth} - ${h.name}")
            }

            if (holidayList.isNotEmpty()) {
                notesBox.add(Paragraph("\nFeriados:").setFontSize(10f).setBold().setFontColor(darkGreyBg))
                holidayList.forEach { h ->
                    notesBox.add(Paragraph(h).setFontSize(8f).setFontColor(darkGreyBg))
                }
            }

            rightCell.add(notesBox)
            mainTable.addCell(rightCell)

            document.add(mainTable)

        } finally {
            document.close()
        }
        return outputFile
    }

    private fun addNotesPage(document: Document, pdf: PdfDocument, pageSize: PageSize, titleFont: com.itextpdf.kernel.font.PdfFont) {
        document.add(com.itextpdf.layout.element.AreaBreak())

        // Add title
        val title = Paragraph("Anotações")
            .setFont(titleFont)
            .setFontSize(24f)
            .setTextAlignment(TextAlignment.CENTER)
            .setMarginBottom(20f)
        document.add(title)

        val page = pdf.lastPage
        val canvas = PdfCanvas(page)
        val pageRect = page.pageSize

        // Draw lines
        val leftMargin = document.leftMargin
        val rightMargin = document.rightMargin
        val topMargin = document.topMargin
        val bottomMargin = document.bottomMargin

        val usableWidth = pageRect.width - leftMargin - rightMargin
        var y = pageRect.top - topMargin - 80f // Start below title
        val lineHeight = 20f

        canvas.setStrokeColor(ColorConstants.LIGHT_GRAY)
        canvas.setLineWidth(0.5f)

        while (y > bottomMargin) {
            canvas.moveTo(leftMargin.toDouble(), y.toDouble())
            canvas.lineTo((leftMargin + usableWidth).toDouble(), y.toDouble())
            canvas.stroke()
            y -= lineHeight
        }
    }
    
}