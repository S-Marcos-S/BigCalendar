# Funcionamento dos Widgets do Aplicativo

Este documento descreve detalhadamente o funcionamento dos widgets da tela inicial (App Widgets) no Android no aplicativo TheBigCalendar.

---

## 1. Visão Geral dos Widgets Disponíveis
O aplicativo implementa diferentes tipos de widgets para a tela inicial do celular:
1. **GreetingWidgetProvider (Widget de Saudação com Tarefas)**: Apresenta uma saudação dinâmica com base no período do dia, o dia da semana atual, a data e a lista de tarefas e aniversários de hoje. À noite, exibe uma seção de tarefas de amanhã.
2. **CompactGreetingWidgetProvider**: Versão compacta do widget de saudação.
3. **SimpleGreetingWidgetProvider**: Versão minimalista de saudação.
4. **EventListWidgetProvider**: Exibe uma lista rolável das próximas atividades e compromissos através de um serviço de RemoteViews.

---

## 2. Widget de Saudação e Tarefas (`GreetingWidgetProvider`)
Este é o widget principal do aplicativo. Ele se atualiza dinamicamente e calcula as informações de forma assíncrona.

### Localização do Código
- **Arquivo**: [GreetingWidgetProvider.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/widget/GreetingWidgetProvider.kt)

### Lógica de Atualização (`updateAppWidget`)
- **Linhas**: 43 a 110
- Carrega as preferências de transparência configuradas pelo usuário para o ID correspondente do widget (`transparency_$appWidgetId`).
- Aplica um cálculo de opacidade Alpha à cor cinza escura de fundo (`#202124`) e atualiza o layout remoto via `views.setInt(R.id.widget_root, "setBackgroundColor", colorWithAlpha)` (linhas 55 a 62).
- Define o texto de saudação baseado no horário atual via `getGreetingBasedOnTime()` (linhas 274 a 284).
- Formata e define a data no padrão "Dia da Semana - Data" (ex: "Sáb - 21/07") (linhas 69 a 81).
- Inicia imediatamente um fluxo em segundo plano (`loadTodayTasks()`) para carregar os compromissos sem travar a interface da tela inicial.

### Busca de Atividades em Segundo Plano (`loadTodayTasks`)
- **Linhas**: 112 a 262
- Utiliza uma corrotina com escopo `Dispatchers.IO` para ler os dados do banco local (`ActivityRepository.activities.first()`). Define um limite máximo de tempo (`withTimeoutOrNull(5000)`) para que a leitura não cause travamento ou ANR (App Not Responding) na tela inicial.
- Filtra e processa as atividades para o dia atual:
  - Ignora atividades recorrentes que foram excluídas especificamente para hoje (`activity.excludedDates.contains(today.toString())`).
  - Para aniversários (`ActivityType.BIRTHDAY`), verifica o mesmo dia e mês independente do ano.
  - Para compromissos normais e recorrentes, resolve as regras de recorrência através do `RecurrenceService`.
- Se o horário atual for noturno (a partir das 18h), busca também as tarefas e aniversários de amanhã.
- Ordena a lista por horário de início crescente e constrói a mensagem final (`buildTasksText()`) exibindo até 5 tarefas de cada dia para evitar estouro do layout do widget.

---

## 3. Widget de Lista de Compromissos (`EventListWidgetProvider`)
Exibe os compromissos em uma lista de rolagem nativa da tela inicial do Android utilizando `RemoteViewsFactory` e `RemoteViewsService`.

### Localização do Código
- **Provider**: [EventListWidgetProvider.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/widget/EventListWidgetProvider.kt)
- **Service**: [EventListWidgetService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/widget/EventListWidgetService.kt)
- **Factory**: [EventListRemoteViewsFactory.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/widget/EventListRemoteViewsFactory.kt)

### Lógica da Lista Rolável
- O provider configura a listagem usando o serviço `EventListWidgetService`, que retorna o adaptador `EventListRemoteViewsFactory`.
- O adaptador acessa o banco local (`ActivityRepository`) e monta a lista de visualizações remotas (`RemoteViews`) contendo a cor da categoria, o título, o horário e a descrição das próximas atividades.

---

## 4. Atualização Automática e Manual dos Widgets
O aplicativo garante que os widgets sejam atualizados sempre que houver modificações nas atividades.

### Localização do Código
- **ViewModel**: `notifyWidgetsDataChanged` (linhas 3064) em [CalendarViewModel.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/viewmodel/CalendarViewModel.kt)
- **Service**: [WidgetUpdateService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/widget/WidgetUpdateService.kt)

### Fluxo de Notificação
- Ao cadastrar, editar, excluir compromissos ou importar um calendário no aplicativo, a função `notifyWidgetsDataChanged()` é disparada.
- Ela envia intents de atualização (`AppWidgetManager.ACTION_APPWIDGET_UPDATE`) para os providers registrados e inicia o `WidgetUpdateService` para atualizar as listas e saudações em background imediatamente.
