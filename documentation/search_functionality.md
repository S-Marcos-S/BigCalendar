# Funcionamento da Função de Pesquisa

Este documento descreve detalhadamente a lógica de pesquisa de atividades, tarefas, aniversários, feriados nacionais e datas comemorativas no aplicativo TheBigCalendar.

---

## 1. Visão Geral
A pesquisa permite ao usuário buscar por termos textuais ou datas específicas e consolida os resultados de múltiplos repositórios em uma lista única e ordenada por relevância.

---

## 2. Lógica do Serviço de Pesquisa (`SearchService`)
O processamento da pesquisa é centralizado no `SearchService`, isolado das camadas de interface de usuário.

### Localização do Código
- **Arquivo**: [SearchService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/SearchService.kt)

### Pesquisa por Texto (`search`)
- **Linhas**: 18 a 58
- Normaliza a string de busca removendo espaços em branco extras e convertendo todos os caracteres para letras minúsculas (linha 26) para busca insensível a maiúsculas/minúsculas.
- Realiza a busca em três origens de dados:
  1. **Atividades locais** (`searchActivities` - linhas 63 a 72): Filtra se a busca está presente no título, descrição ou localização da atividade.
  2. **Feriados nacionais** (`searchHolidays` - linhas 77 a 85): Filtra correspondências no nome ou sumário do feriado.
  3. **Datas comemorativas** (`searchCommemorativeDates` - linhas 90 a 98): Filtra correspondências no nome ou sumário.

### Ordenação por Relevância
- **Linhas**: 41 a 57
- Os resultados são pontuados e ordenados em duas etapas:
  1. **Critério de Prefixo**: Prioriza resultados cujo título inicia exatamente com a palavra buscada (linha 46). Em segundo lugar, títulos que contêm a busca no meio da palavra (linha 47).
  2. **Critério de Proximidade Temporal**: Para resultados equivalentes, calcula a distância em dias (`toEpochDay`) entre a data do evento e a data atual (`LocalDate.now()`), ordenando os eventos mais próximos do dia de hoje no topo da lista (linhas 52 a 55).

### Pesquisa por Data (`searchByDate`)
- **Linhas**: 103 a 135
- Se o usuário digitar um padrão que se assemelhe a uma data, o aplicativo tenta converter a query usando a função de parsing `parseDateQuery` (linhas 140 a 182).
- Padrões de data suportados:
  - `dd/MM` ou `dd-MM` (completa com o ano atual)
  - `dd/MM/yyyy` ou `dd-MM-yyyy` (data exata do ano digitado)
- Uma vez parseada a data, adiciona todas as atividades registradas no banco de dados local para aquele dia, assim como os feriados nacionais e datas comemorativas incidentes.

---

## 3. Interface de Pesquisa (`SearchScreen`)
A tela de busca gerencia o estado da caixa de texto e atualizações instantâneas.

### Localização do Código
- **Arquivo**: [SearchScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/SearchScreen.kt)

### Lógica de Foco e digitação rápida
- **Linhas**: 228 a 231
- Utiliza um `LaunchedEffect` e a classe `FocusRequester` para solicitar automaticamente foco no campo de texto de pesquisa assim que a tela abre, exibindo o teclado virtual do Android sem a necessidade de o usuário clicar no campo.
- Ao alterar o texto, chama `viewModel.onSearchQueryChange(query)` (linha 147), que recalcula os resultados e atualiza o estado de UI de forma dinâmica.
