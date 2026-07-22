# Funcionamento da Repetição e Recorrência de Agendamentos

Este documento descreve detalhadamente o funcionamento da lógica de recorrência de atividades no aplicativo TheBigCalendar.

---

## 1. Visão Geral da Recorrência
A repetição permite que um agendamento único se repita de forma automatizada em intervalos contínuos (diários, semanais, mensais, anuais) ou personalizados, sem a necessidade de duplicar os dados físicos no banco local.

---

## 2. Serviço de Geração de Recorrências (`RecurrenceService`)
A geração e interpretação das regras de repetição são coordenadas pelo `RecurrenceService`.

### Localização do Código
- **Arquivo**: [RecurrenceService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/RecurrenceService.kt)

### Geração de Instâncias (`generateRecurringInstances`)
- **Linhas**: 15 a 42
- O método recebe a atividade base, a data de início e a data final do intervalo visualizado (ex: primeiro e último dia do mês na grade).
- Retorna uma lista de instâncias clonadas da atividade com IDs e datas incrementados de acordo com a regra.
- **Padrões de Repetição Suportados**:
  - `DAILY` (Diária - linhas 77 a 91): Repete a atividade todos os dias consecutivos.
  - `WEEKLY` (Semanal - linhas 96 a 110): Repete a atividade no mesmo dia da semana, de 7 em 7 dias.
  - `MONTHLY` (Mensal - linhas 115 a 132): Repete a atividade no mesmo dia do mês. Ajusta dinamicamente a data para meses mais curtos (por exemplo, se a data base for dia 31, em fevereiro repetirá no dia 28/29).
  - `YEARLY` (Anual - linhas 138 a 152): Repete no mesmo dia e mês de cada ano.

---

## 3. Regras Customizadas Padrão iCal (`parseCustomRecurrenceRule`)
- **Linhas**: 157 a 204
- Permite processar regras de repetição complexas que seguem a notação padrão iCalendar (RFC 5545), como:
  `FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE,FR;UNTIL=2026-12-31` (repetir a cada duas semanas, às segundas, quartas e sextas até o fim de 2026).
- **Parâmetros Tratados**:
  - `FREQ`: Frequência (HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY).
  - `INTERVAL`: Intervalo de salto (ex: a cada 3 dias).
  - `UNTIL`: Data limite de encerramento da recorrência.
  - `COUNT`: Número máximo de ocorrências totais geradas.
  - `BYDAY`: Dias específicos da semana (SU, MO, TU, WE, TH, FR, SA).

### Exclusão de Ocorrências (`excludedDates` e `excludedInstances`)
- Para evitar que alterações ou exclusões de uma única data afetem toda a série de repetição, a entidade possui duas listas de controle:
  1. `excludedDates`: Lista de datas excluídas (ex: "2026-07-22"). O gerador ignora essas datas no loop (linha 86).
  2. `excludedInstances`: Usado para repetições por hora, que inclui data e horário do evento específico no formato `ID_DATA_HORA` para permitir remoção granular (linhas 234 a 237).

---

## 4. Configuração na Interface (`CustomRepetitionScreen`)
A tela de repetição customizada permite ao usuário montar a string iCal interativamente.

### Localização do Código
- **Arquivo**: [CustomRepetitionScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/CustomRepetitionScreen.kt)

### Lógica de Construção da Regra (`buildCustomRecurrenceRule`)
- **Linhas**: 382 a 436
- Lê a frequência selecionada, concatena o intervalo e adiciona os dias selecionados no seletor de dias da semana (linhas 400 a 417).
- Converte a data limite no formato local para o padrão ISO-8601 exigido na especificação iCalendar (linhas 420 a 428).
