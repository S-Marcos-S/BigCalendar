# Funcionamento das Estatísticas e Gráficos de Conclusão

Este documento descreve detalhadamente o funcionamento da lógica de cálculo e renderização gráfica de relatórios, gráficos de barras e distribuição de atividades no aplicativo TheBigCalendar.

---

## 1. Visão Geral
A tela de Estatísticas oferece uma visão analítica sobre a produtividade e a alocação de tempo do usuário. Ela exibe gráficos de barras históricos (7 dias e 12 meses), um gráfico de pizza mostrando a distribuição de categorias (atividades, tarefas, notas, aniversários) do mês atual, e cartões com métricas de desempenho.

---

## 2. Gráficos Históricos de Barras (`BarChartComponent`)
O aplicativo renderiza gráficos de barras horizontais/verticais usando elementos nativos do Jetpack Compose para comparar o volume de tarefas criadas e finalizadas.

### Localização do Código
- **Tela Principal**: [ChartScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/ChartScreen.kt)
- **Componente**: `BarChartComponent.kt` na pasta `com/mss/thebigcalendar/ui/components`

### Lógica de Agrupamento
- **Últimos 7 dias** (linha 194):
  - O ViewModel agrupa as atividades criadas na última semana dia a dia.
  - Plota no eixo X as abreviações dos dias da semana (Seg, Ter, Qua...) e no eixo Y o total de tarefas correspondentes.
- **Histórico Anual** (linha 203):
  - Agrupa os registros dos últimos 12 meses pelo mês de competência (Janeiro, Fevereiro...).
  - Mostra a evolução histórica de produtividade do usuário ao longo do ano.

---

## 3. Distribuição Mensal por Categorias (`PieChartComponent`)
Exibe a divisão proporcional dos tipos de atividades inseridas no mês de exibição ativo (`currentMonth`).

### Localização do Código
- **Componente**: `PieChartComponent.kt` na pasta `com/mss/thebigcalendar/ui/components`
- **Parâmetro**: `currentMonth: YearMonth` (linha 214)

### Lógica de Distribuição
- Filtra a lista de atividades mantendo apenas os registros cuja data pertença ao mês informado (linhas 212 a 217).
- Soma e agrupa as quantidades de cada tipo de atividade (`ActivityType`): Tarefas, Eventos, Notas e Aniversários.
- Desenha frações circulares proporcionais coloridas de acordo com as cores de prioridade de cada tipo no Jetpack Compose.

---

## 4. Métricas e Conclusão de Tarefas
Exibe cartões com os números absolutos extraídos dos repositórios locais (linhas 220 a 300).

### Lógica de Cálculo
- **Total de Atividades Ativas**: Obtido pelo tamanho da lista `activities.size`.
- **Tarefas Concluídas**: Obtido pelo tamanho da lista `completedActivities.size`. O clique sobre esta coluna redireciona para a tela de Tarefas Concluídas (`onNavigateToCompletedTasks()` - linha 267).
- **Eventos Agendados**: Contabiliza apenas os itens classificados como `ActivityType.EVENT` que não estão finalizados (linhas 240 a 243).
