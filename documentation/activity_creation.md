# Funcionamento da Tela de Criação de Agendamentos

Este documento descreve detalhadamente o funcionamento da tela de criação e edição de agendamentos no aplicativo TheBigCalendar.

---

## 1. Visão Geral da Tela (`CreateActivityScreen`)
A tela de criação e edição gerencia a entrada de novos compromissos, tarefas, notas ou aniversários. O fluxo de dados lida com validação de texto, formatação automática em tempo de digitação, seleção de data, horários, prioridades e sincronização direta.

### Localização do Código
- **Arquivo**: [CreateActivityScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/CreateActivityScreen.kt)

---

## 2. Tipos de Atividades Disponíveis
O usuário pode alternar entre os seguintes tipos de agendamento (linhas 510 a 534):
- **Tarefa (`TASK`)**: Representa afazeres que possuem estado de conclusão (pendente ou concluído) exibido como caixa de seleção (Checkbox).
- **Evento (`EVENT`)**: Representa compromissos de calendário padrão (ex: reuniões, viagens) com horário de início e fim.
- **Nota (`NOTE`)**: Notas simples atreladas a um dia específico do calendário, sem horário definido.
- **Aniversário (`BIRTHDAY`)**: Destinado a marcar aniversários de pessoas. É salvo com repetição anual padrão e exibe um ícone especial de bolo (`🎂`) na interface.

---

## 3. Validação e Formatação Inteligente de Entradas
- **Capitalização Automática**: O campo do Título força a primeira letra maiúscula automaticamente ao digitar (`replaceFirstChar` - linha 392) e bloqueia a gravação caso esteja em branco (linha 365).
- **Formatação de Listas na Descrição** (linhas 109 a 173):
  - Oferece suporte a formatação inline de listas numeradas e listas de checagem (checklist) na caixa de texto.
  - Ao digitar no campo de descrição, a função `toggleListFormat()` (linhas 123 a 173) pode ser ativada por botões no canto superior direito para inserir os prefixos `[ ] ` (checklist) ou `1. ` (lista numerada) na linha atual.
  - Ao pressionar a tecla `Enter` para criar uma nova linha, o método `onValueChange` intercepta o caractere de nova linha (`\n`) nas linhas 412 a 459:
    - Se a linha anterior possuir o padrão de checklist (`[ ]` ou `[x]`) e contiver texto, gera automaticamente a próxima linha iniciada com `[ ]`.
    - Se a linha anterior for uma lista numerada (`1. `, `2. `), calcula o próximo índice (`num + 1`) e inicia a nova linha com o número sequencial (ex: `2. `).
    - Se a linha com o prefixo estiver vazia, pressionar `Enter` limpa o prefixo, permitindo sair da formatação de lista de forma intuitiva.

---

## 4. Seleção de Horários e Datas
- **Data do Agendamento**: Exibida no cabeçalho. Ao clicar sobre a data, abre o diálogo de calendário nativo do Android (`DatePicker` - linhas 740 a 808) para que o usuário alterne o dia rapidamente.
- **Horário Inicial e Final**: Controlados pela seção `TimeSelector` (linha 555). Se o switch "Dia todo" estiver desligado, abre caixas de diálogo (`TimePicker` - linhas 704 a 737) para ajustar as horas de início e término.

---

## 5. Salvar e Persistir as Configurações
- Ao clicar em "Salvar" no menu superior direito (linhas 339 a 371), todos os estados dos campos são consolidados na entidade `Activity`.
- O método executa a função de salvamento remoto e local via `onSaveActivity(updatedActivity, syncWithGoogle)` (linha 361) enviando os dados para persistência e notificando os widgets da tela inicial de que a grade precisa ser atualizada.
