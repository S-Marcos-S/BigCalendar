# Funcionamento do Gerenciamento de Backups Locais

Este documento descreve detalhadamente o funcionamento da criação de backups locais, agendamento de rotinas automatizadas e autolimpeza (pruning) de arquivos antigos no aplicativo TheBigCalendar.

---

## 1. Visão Geral
Para garantir que o usuário não perca seus dados mesmo sem conectividade com a internet, o aplicativo oferece um sistema robusto de backups locais armazenados em formato JSON (criptografados ou em texto claro) em uma pasta selecionada do dispositivo através do Storage Access Framework (SAF) do Android.

---

## 2. Criação de Backups Locais (`createBackup`)
O processo de backup lê todas as tabelas locais (atividades ativas, atividades concluídas, histórico da lixeira e alarmes) e gera um arquivo único.

### Localização do Código
- **Arquivo**: [BackupService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/data/service/BackupService.kt)
- **Função**: `createBackup(directoryUri: Uri)` (linhas 161 a 210)

### Lógica de Criação
- Lê de forma assíncrona as listas completas do `ActivityRepository`, `DeletedActivityRepository` e `CompletedActivityRepository`.
- Consolida as informações no método helper `createBackupJson()` gerando um objeto JSON estruturado com metadados (versão do backup, data de criação e versão do aplicativo).
- **Criptografia Local**:
  - Verifica se a criptografia local está ativada. Em caso afirmativo, encripta a string JSON usando a senha do usuário através do `CryptoHelper.encrypt` antes de gravá-la em disco.
- Utiliza a classe `DocumentFile` do Android para criar um novo arquivo na pasta do URI fornecido com o padrão de nomenclatura `TBCalendar_Backup_yyyyMMdd_HHmmss.json`.

---

## 3. Autolimpeza (Pruning) de Backups Antigos
Para evitar que o armazenamento do celular fique cheio com centenas de arquivos de backup antigos, o aplicativo implementa uma rotina de autolimpeza automática de arquivos excedentes.

### Localização do Código
- **Autolimpeza Local (`pruneLocalBackups`)**: Linhas 770 a 783 em `BackupService.kt`
- **Autolimpeza na Nuvem (`pruneCloudBackups`)**: Linhas 785 a 803 em `BackupService.kt`

### Lógica de Pruning
- Lê os limites configurados pelo usuário (`maxLocalBackups` e `maxCloudBackups`) no DataStore de preferências. O limite padrão é de 10 arquivos.
- **Limpeza Local**:
  - Lista todos os arquivos da pasta do SAF que começam com o prefixo `TBCalendar_Backup_`.
  - Se a quantidade total de backups encontrados superar o limite estabelecido, ordena a lista por data de modificação (`lastModified()`) do mais antigo para o mais recente.
  - Exclui fisicamente os arquivos mais antigos (`sorted[i].delete()`) até restarem apenas os arquivos dentro da quantidade limite.
- **Limpeza na Nuvem**:
  - Lista os backups armazenados no Google Drive, ordena-os pela data de criação (`createdTime`) e realiza chamadas exclusivas para deletar os arquivos remotos mais antigos excedentes.

---

## 4. Agendamento de Backup Automático (`BackupScheduler`)
O aplicativo gerencia a execução em segundo plano de backups periódicos usando a biblioteca `WorkManager` do Jetpack.

### Localização do Código
- **Arquivo**: [BackupScheduler.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/BackupScheduler.kt)
- **Classe**: `AutoBackupWorker.kt` na pasta `com/mss/thebigcalendar/worker/`

### Lógica de Agendamento
- Ao ativar a opção "Backup Automático" nas configurações do aplicativo, a rotina `scheduleBackup()` calcula o atraso inicial (`initialDelay`) necessário para que a primeira execução ocorra exatamente no horário (hora e minuto) definido pelo usuário.
- Registra uma tarefa recorrente (`PeriodicWorkRequest`) com o intervalo configurado (Diário, Semanal ou Mensal).
- O `AutoBackupWorker` roda em segundo plano, lê as configurações, executa o backup local e/ou sincroniza com a nuvem, e realiza a autolimpeza dos arquivos excedentes de forma silenciosa.
