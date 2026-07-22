# Funcionamento do Detector e Limpador de Agendamentos Duplicados

Este documento descreve detalhadamente o funcionamento da lógica de detecção e remoção em lote de agendamentos duplicados no aplicativo TheBigCalendar.

---

## 1. Visão Geral
Durante importações de calendários (ex: arquivos JSON) ou falhas de sincronização na nuvem, podem surgir registros de compromissos duplicados. O aplicativo implementa duas barreiras contra duplicidade:
1. **Prevenção Proativa**: Impede a inserção de novos agendamentos idênticos pela tela de cadastro.
2. **Remoção Reativa (Limpeza em Lote)**: Permite ao usuário escanear a base de dados local, identificar grupos de agendamentos duplicados e apagá-los de uma só vez.

---

## 2. Prevenção de Duplicados ao Salvar
Ao cadastrar ou atualizar um compromisso através da tela de criação, o ViewModel realiza uma checagem de integridade antes de autorizar a escrita no repositório.

### Localização do Código
- **Arquivo**: [CalendarViewModel.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/viewmodel/CalendarViewModel.kt)
- **Método**: `onSaveActivity` (linhas 1673 a 1702)

### Lógica de Prevenção
- Extrai o ID raiz (`baseIdToSave`) separando-o de eventuais sufixos de datas de recorrência (linha 1676).
- Compara a nova atividade com todas as atividades salvas no estado atual da UI (`_uiState.value.activities`).
- **Assinatura de Identidade**: São comparados de forma insensível a maiúsculas/minúsculas: Título, Data, Horário de Início e Fim, Sinalização de Dia Inteiro, Descrição, Localização, Tipo de Atividade e Regra de Recorrência (linhas 1681 a 1689).
- Se encontrar um registro correspondente com ID diferente, exibe um alerta do tipo Toast ("Já existe um agendamento idêntico!") e cancela a inserção (linhas 1693 a 1702).

---

## 3. Identificação e Limpeza em Lote (`removeAllDuplicates`)
O usuário pode abrir a janela de "Agendamentos Duplicados" no menu lateral para visualizar os grupos repetidos na memória e excluí-los.

### Localização do Código
- **Assinatura**: `getDuplicateGroups` (linhas 1639 a 1669) em `CalendarViewModel.kt`
- **Exclusão**: `removeAllDuplicates` (linhas 1620 a 1637) em `CalendarViewModel.kt`

### Lógica de Agrupamento (`getDuplicateGroups`)
- Filtra apenas compromissos criados localmente pelo usuário, ignorando feriados nacionais e comemorações vindas do arquivo JSON estático (`!it.location?.startsWith("JSON_IMPORTED_")` - linha 1666).
- Define a classe de dados interna `ActivitySignature` que encapsula os campos de identidade da atividade (linhas 1640 a 1650).
- Agrupa todas as atividades baseando-se nessa assinatura via `customActivities.groupBy { it.toSignature() }` (linha 1667).
- Filtra e retorna apenas as listas (grupos) que contêm mais de um elemento idêntico (`groups.values.filter { it.size > 1 }` - linha 1668).

### Lógica de Remoção (`removeAllDuplicates`)
- Executa em escopo assíncrono (`viewModelScope.launch`).
- Para cada grupo de atividades idênticas encontrado pelo detector:
  - Preserva o primeiro elemento intacto no banco de dados (`group.first()`).
  - Descarta e remove todos os elementos excedentes (`val toDelete = group.drop(1)` - linha 1627).
  - Adiciona cada cópia excluída ao histórico da lixeira (`deletedActivityRepository.addDeletedActivity(act)`) para possibilitar recuperação futura, e remove física e definitivamente o ID excedente de `activityRepository`.
- Recarrega a grade do calendário e fecha a caixa de diálogo.
