# Funcionamento do Sistema de Alarmes e Notificações

Este documento descreve detalhadamente o funcionamento dos alarmes do sistema, agendamento através do `AlarmManager`, controle de concorrência com `WorkManager` de segurança e envio de notificações interativas no aplicativo TheBigCalendar.

---

## 1. Agendamento de Alarmes (`AlarmService`)
Os alarmes do sistema são controlados por meio do `AlarmManager` do sistema operacional Android, garantindo que o dispositivo "desperte" no horário estipulado pelo usuário.

### Localização do Código
- **Arquivo**: [AlarmService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/AlarmService.kt)

### Lógica de Agendamento (`scheduleAlarm`)
- **Linhas**: 77 a 105
- O método verifica se o alarme está ativado. Se estiver desativado, remove qualquer agendamento pendente utilizando a função `cancelAlarm()`.
- Se o alarme for de ocorrência única (sem dias repetidos), chama `scheduleSingleAlarm()` (linhas 335 a 361) calculando o próximo disparo baseado no horário atual e data pulada (`skippedDate`).
- Se for recorrente, chama `scheduleRepeatingAlarm()` (linhas 366 a 401) agendando ocorrências individuais para hoje e para os próximos 7 dias para cada dia da semana selecionado.

### Múltiplas Estratégias de Disparo (`scheduleAlarmAtTime`)
- **Linhas**: 406 a 476
- Para garantir robustez frente a diferentes versões do Android e políticas agressivas de economia de bateria, o aplicativo tenta registrar o alarme nas seguintes prioridades:
  1. **Estratégia 1 (`setAlarmClock`)**: A mais prioritária e confiável do Android, que define um alarme real no relógio do sistema, exibindo inclusive o ícone correspondente na barra de status.
  2. **Estratégia 2 (`setExactAndAllowWhileIdle`)**: Caso a primeira falhe, define um alarme exato e com permissão para acordar a CPU mesmo com o dispositivo no modo ocioso (Doze Mode).
  3. **Estratégia 3 (`setExact`)**: Fallback básico para alarmes exatos.
  4. **Estratégia 4 (`set`)**: Fallback final caso nenhuma das anteriores funcione.

### Mecanismo de Backup com `WorkManager` (`scheduleWorkManagerBackup`)
- **Linhas**: 478 a 510
- Adicionalmente ao `AlarmManager`, o aplicativo agenda uma tarefa única no `WorkManager` (`AlarmBackupWorker`) programada para rodar exatamente no mesmo horário do alarme. Esse mecanismo serve como uma contingência caso o sistema Android descarte ou atrase o broadcast do `AlarmManager`.

---

## 2. Receptores de Alarmes (`AlarmReceiver` & `AlarmBackupWorker`)
Quando o alarme dispara, o Android envia uma mensagem do tipo Broadcast com a ação `com.mss.thebigcalendar.ALARM_TRIGGERED`.

### Localização do Código
- **Broadcast Receiver**: [AlarmReceiver.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/AlarmReceiver.kt)
- **Backup Worker**: [AlarmBackupWorker.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/AlarmBackupWorker.kt)

### Fluxo de Disparo
1. O `AlarmReceiver` recebe a notificação no método `onReceive()` (linhas 24 a 53).
2. Se a ação for `ACTION_ALARM_TRIGGERED`, extrai o ID do alarme e delega para `handleAlarmTriggered()` executando uma corrotina assíncrona (linhas 58 a 74).
3. `AlarmReceiver` também intercepta eventos de reinicialização do sistema operacional (`ACTION_BOOT_COMPLETED` e `ACTION_MY_PACKAGE_REPLACED`) nas linhas 43 a 48. Ele chama `rescheduleAllAlarms()` para ler todos os alarmes marcados como ativos no banco local (`AlarmRepository`) e reagendá-los no `AlarmManager` para garantir que os alarmes não sejam perdidos ao reiniciar o celular.

---

## 3. Exibição e Tela Cheia (`AlarmActivity` & Notificação de Tela Cheia)
Ao processar o disparo, o aplicativo força a abertura da tela do alarme mesmo se o celular estiver bloqueado ou com a tela desligada.

### Localização do Código
- **Disparo no Serviço**: `forceOpenAlarmScreen` (linhas 586 a 660) em [AlarmService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/AlarmService.kt)
- **Tela de Alarme**: [AlarmActivity.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/screens/AlarmActivity.kt)

### Notificação com Intent em Tela Cheia (`FullScreenIntent`)
- Para contornar restrições das versões mais novas do Android que impedem o lançamento direto de `Activity` em segundo plano, o aplicativo cria um canal de notificação específico de alta prioridade (`alarm_fullscreen_channel`) e associa a `AlarmActivity` usando o método `.setFullScreenIntent(pendingIntent, true)`.
- Isso faz com que, caso o celular esteja com a tela desligada ou bloqueada, a `AlarmActivity` seja mostrada cobrindo toda a tela de forma imediata.
- O som do alarme e a vibração são controlados e executados diretamente dentro da `AlarmActivity` para garantir sincronia com os botões de "Snooze" (Soneca) e "Dismiss" (Desligar).
