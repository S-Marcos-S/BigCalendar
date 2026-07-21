# Funcionamento da Geração de PDF do Calendário

Este documento descreve detalhadamente o funcionamento da geração do arquivo PDF do calendário para impressão e exportação no aplicativo TheBigCalendar.

---

## 1. Biblioteca Utilizada
O aplicativo utiliza a biblioteca **iText 7** (`com.itextpdf`) para a criação de documentos PDF, definição de geometrias (tabelas, células, parágrafos), aplicação de fontes customizadas ttf e renderização de desenhos vetoriais (Canvas).

---

## 2. Estrutura do Serviço (`PdfGenerationService`)
A geração do PDF é centralizada no serviço responsável por converter os dados locais do aplicativo (atividades e feriados) em páginas impressas altamente customizáveis.

### Localização do Código
- **Arquivo**: [PdfGenerationService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/data/service/PdfGenerationService.kt)

### Lógica de Inicialização (`generateCalendarPdf`)
- **Linhas**: 49 a 302
- O método principal recebe as opções de impressão (`PrintOptions`), a lista de atividades, feriados nacionais, feriados do usuário e fases da lua.
- Cria o arquivo de saída no diretório de downloads privado do aplicativo (`getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) + /TheBigCalendar`) (linhas 70 a 77).
- Inicializa o `PdfWriter`, o `PdfDocument` e o layout `Document` informando o tamanho da página configurado (A4 ou A3) e orientação (retrato ou paisagem).
- Aplica um manipulador de eventos na página (`PdfDocumentEvent.END_PAGE`) para desenhar a cor de fundo selecionada pelo usuário usando o canvas vetorial do iText (linhas 98 a 118).

### Desenho das Fontes Customizadas
- **Linhas**: 128 a 138
- Permite carregar dinamicamente fontes TrueType (.ttf) salvas na pasta `assets/fonts` do projeto Android, fornecendo suporte a tipografias customizadas para títulos, dias da semana e compromissos.

---

## 3. Renderização da Grade do Calendário
A renderização usa uma estrutura de tabela com 7 colunas (dias da semana) e até 6 linhas (semanas).

### Criação da Tabela (`createCalendarTable`)
- **Linhas**: 304 a 371
- Adiciona o cabeçalho com os nomes dos dias da semana (abreviados ou completos) e aplica as cores personalizadas do tema (linhas 321 a 343).
- Obtém o primeiro domingo da semana que contém o primeiro dia do mês e preenche as células das semanas de forma sequencial utilizando o método `createDayCell()`.

### Customização das Células dos Dias (`createDayCell`)
- **Linhas**: 377 a 483
- Renderiza o número do dia com cor prioritária baseada nos feriados, aniversários e compromissos do dia.
- Adiciona o conteúdo do dia (descrição da atividade ou feriado).
- **Modo Pautado (`LinedCellRenderer`)** (linhas 759 a 800):
  - Se a opção "linhas nas células" estiver ativa, utiliza um renderizador customizado (`CellRenderer`) que desenha pautas horizontais cinzas através de coordenadas vetoriais do Canvas (`canvas.lineTo`) e insere o texto de forma alinhada em cima das linhas desenhadas.

---

## 4. Desenhos Vetoriais de Fases da Lua (`createMoonPhaseImage`)
O serviço possui um desenhista vetorial completo para as fases da lua que não depende de imagens bitmap (PNG/JPG). Ele desenha a lua com formas geométricas primitivas.

### Desenho Vetorial das Luas
- **Linhas**: 488 a 592
- Utiliza um objeto `PdfFormXObject` com coordenadas 2D.
- Dependendo do tipo de fase da lua (`MoonPhaseType`), realiza operações geométricas:
  - **Lua Nova**: Círculo preto completo (`canvas.circle` + `canvas.fill`).
  - **Lua Cheia**: Círculo branco completo.
  - **Quarto Crescente / Minguante**: Círculo preto com corte retangular preenchido de branco à direita ou esquerda.
  - **Gibosas e Crescentes**: Combinação de linhas retas, curvas e retângulos para colorir frações precisas do disco lunar.
