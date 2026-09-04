# Funcionamento das Configurações de Tema e Visualização

Este documento descreve detalhadamente o funcionamento dos estilos visuais, temas, paleta de cores acentuadas e dimensionamento de tela no aplicativo TheBigCalendar.

---

## 1. Visão Geral
O aplicativo permite que o usuário customize extensivamente a interface gráfica. Toda a reatividade de cores e escalas é propagada instantaneamente utilizando Jetpack Compose baseado no estado reativo (`uiState`) emitido pelo ViewModel.

---

## 2. Controle de Tema e Pure Black Mode
O aplicativo possui suporte nativo para temas Claro, Escuro e integração automática com o Tema do Sistema Android.

### Localização do Código
- **Tela de Configurações**: [CalendarVisualizationSettingsScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/CalendarVisualizationSettingsScreen.kt)
- **Tema do Aplicativo**: `Theme.kt` na pasta `com/mss/thebigcalendar/ui/theme`

### Detalhamento da Lógica
- **Alternância de Tema** (linhas 253-279):
  - Um controle do tipo `Switch` permite ligar e desligar o tema escuro. Ao acionar o switch, chama `viewModel.onThemeChange(Theme.DARK)` ou `Theme.LIGHT`.
- **Modo Preto Puro (`pureBlackTheme`)** (linhas 282-322):
  - Projetado para economizar bateria em telas AMOLED desligando totalmente os pixels pretos.
  - A opção fica ativa e clicável apenas quando o tema escuro está ativado (linhas 282 a 286).
  - Ao ser habilitado, o switch chama `viewModel.setPureBlackTheme(enabled)`. Toda a paleta de fundo da interface do aplicativo é convertida de cinza escuro para preto absoluto (`Color.Black`).

---

## 3. Seleção de Cor Acentuada Dinâmica (`primaryColor`)
O aplicativo oferece a opção de cor de destaque dinâmica que modifica a cor de botões, seletores, cabeçalhos e elementos ativos do calendário.

### Localização do Código
- **Seletor de Cores**: Linhas 352 a 433 em `CalendarVisualizationSettingsScreen.kt`

### Detalhamento da Lógica
- O usuário pode escolher entre o modo **Automático (`AUTO`)** e **Cores Predefinidas**.
- **Modo Automático**: Utiliza a paleta de cores padrão baseada no esquema Material You (tons roxos tradicionais).
- **Cores Predefinidas**: Uma lista horizontal rolável (`LazyRow`) contendo 12 cores em notação hexadecimal (linhas 369 a 382) permite que o usuário selecione tons de Azul, Verde, Laranja, Vermelho, etc.
- Ao clicar em uma cor, o aplicativo salva o hexadecimal no DataStore local e recompõe dinamicamente toda a árvore de componentes utilizando o novo token de cor primária.

---

## 4. Escala e Dimensionamento do Calendário (`calendarScale`)
- **Linhas**: 184 a 201
- Fornece um controle deslizante (`Slider`) que varia a escala de visualização de 0.6x (super compacto) a 1.22x (ampliado) com 12 passos definidos.
- Ao mover o slider, chama `viewModel.setCalendarScale(clamped)`. A grade do calendário se ajusta em tempo real, diminuindo ou aumentando o tamanho das fontes e a altura das linhas da tabela de compromissos.

---

## 5. Idioma e Animações
- **Idioma do Aplicativo** (linhas 435 a 462):
  - Apresenta um botão que abre um diálogo de seleção contendo as bandeiras e nomes dos idiomas (Português, Inglês, Espanhol).
  - Salva a configuração no DataStore e nos SharedPreferences locais para recarregar o Locale nativo do Android imediatamente.
- **Tipo de Animação** (linhas 230-250):
  - Permite alterar o efeito de transição ao deslizar as telas do calendário (Deslizar, Fade, Sem Animação).
