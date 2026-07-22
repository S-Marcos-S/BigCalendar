# Funcionamento do Sistema de Lixeira e Recuperação

Este documento descreve detalhadamente o funcionamento do histórico de lixeira e recuperação de agendamentos excluídos no aplicativo TheBigCalendar.

---

## 1. Visão Geral (Soft Delete)
Para proteger o usuário contra toques acidentais, a exclusão de compromissos no aplicativo não apaga os dados físicos imediatamente do dispositivo. Em vez disso, o aplicativo executa uma "exclusão lógica" (Soft Delete): a atividade é movida do repositório principal para a lixeira (`deleted_activities.pb`), onde é mantida até ser explicitamente restaurada ou limpa permanentemente.

---

## 2. Repositório de Atividades Excluídas (`DeletedActivityRepository`)
O controle de lixeira é persistido no arquivo binário `deleted_activities.pb` usando Protocol Buffers.

### Localização do Código
- **Arquivo**: [DeletedActivityRepository.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/data/repository/DeletedActivityRepository.kt)

### Lógica para Mover para a Lixeira (`addDeletedActivity`)
- **Linhas**: 32 a 44
- O método recebe a entidade `Activity` que o usuário solicitou deletar.
- Embrulha a atividade original no modelo `DeletedActivity` gerando:
  - Um ID único de rastreamento da lixeira (`UUID.randomUUID()`).
  - O timestamp do momento da exclusão (`LocalDateTime.now()`).
  - A cópia íntegra de todos os campos da atividade original (título, descrição, categoria, etc.) serializados no formato Proto (linhas 108 a 125).
- Insere a nova entrada de lixeira assincronamente no DataStore `deletedActivitiesDataStore`.

### Restauração de Agendamentos (`restoreActivity`)
- **Linhas**: 46 a 58
- Localiza o registro na lixeira através do ID do rastreador da exclusão.
- Se o registro existir:
  - Remove permanentemente o registro da lixeira chamando `removeDeletedActivity(deletedActivityId)`.
  - Retorna o objeto `Activity` original intacto para o chamador.
  - O ViewModel intercepta o retorno e reinsere a atividade de volta no `ActivityRepository`, restaurando o compromisso na grade do calendário exatamente como estava antes de ser deletado.

---

## 3. Limpeza Permanente
O usuário tem a opção de apagar um item de forma definitiva ou esvaziar a lixeira por completo.
- **Limpeza Individual (`removeDeletedActivity`)** (linhas 60 a 69): Remove fisicamente a entrada do array Protocol Buffers, destruindo os dados permanentemente.
- **Limpar Lixeira (`clearAllDeletedActivities`)** (linhas 71 a 75): Zera o DataStore gravando um objeto `TrashActivities` vazio.

---

## 4. Interface da Lixeira (`TrashScreen`)
A lixeira é acessível no menu lateral do aplicativo e apresenta a listagem de todas as atividades salvas para descarte.

### Localização do Código
- **Tela**: [TrashScreen.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/TrashScreen.kt)
- **ViewModel**: `restoreActivity` e `deleteActivityPermanently` em [CalendarViewModel.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/viewmodel/CalendarViewModel.kt)
- Ao clicar em "Restaurar", o item volta instantaneamente a figurar na grade mensal. Ao clicar em "Excluir permanentemente" ou "Esvaziar lixeira", os dados são apagados sem possibilidade de reversão local.
