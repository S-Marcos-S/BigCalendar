# Funcionamento da Sincronização com o Google

Este documento descreve detalhadamente o funcionamento da lógica de autenticação do Google, o serviço de backup e a sincronização progressiva de eventos com a API do Google Agenda no aplicativo TheBigCalendar.

---

## 1. Autenticação com o Google
A autenticação do usuário utiliza a biblioteca do Google Play Services (`GoogleSignIn`) para obter uma conta de login ativa com escopos específicos do Google Agenda e do Google Drive (AppFolder).

### Localização do Código
- **Arquivo**: [GoogleAuthService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/GoogleAuthService.kt)
- **Instanciação**: Linhas 14 a 31
  - Instancia `GoogleSignInOptions` com o ID do cliente Web (`webClientId = "662891819317-lngjv15bpi5v5asttejhmc1rlaoatefd.apps.googleusercontent.com"`) e adiciona os seguintes escopos:
    - `CalendarScopes.CALENDAR`
    - `CalendarScopes.CALENDAR_EVENTS`
    - `DriveScopes.DRIVE_APPDATA`
- **Fluxo de Login**: Linhas 33 a 45
  - `getSignInIntent()` obtém a `Intent` que dispara a janela de login padrão do Google no dispositivo Android.
  - `handleSignInResult()` decodifica a tarefa (`Task<GoogleSignInAccount>`) e retorna o objeto `GoogleSignInAccount` em caso de sucesso.
- **Desconexão**: Linhas 47 a 51
  - `signOut()` chama a desconexão assíncrona da conta do Google.

---

## 2. Serviço de Drive e Backup (`GoogleDriveService`)
O aplicativo utiliza uma pasta oculta específica do aplicativo no Google Drive (`appDataFolder`) para salvar o arquivo de sincronização criptografado ou em texto claro `TBCalendar_Sync_Data.json`.

### Localização do Código
- **Arquivo**: [GoogleDriveService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/GoogleDriveService.kt)
- **Instanciação do Cliente Drive**: Linhas 17 a 32
  - Configura a credencial baseada no token OAuth2 utilizando `GoogleAccountCredential.usingOAuth2` com escopo `DriveScopes.DRIVE_APPDATA`.
- **Upload do Arquivo**: Linhas 38 a 50
  - `uploadBackupFile()` cria ou sobrescreve o arquivo na pasta oculta `appDataFolder` contendo as propriedades adicionais da quantidade de atividades de backup.
- **Listagem e Download**: Linhas 52 a 66
  - `getBackupFiles()` lista os arquivos de backup ordenados por data de criação.
  - `downloadBackupFile()` e `deleteBackupFile()` realizam o download e a exclusão do arquivo pelo `fileId`.

---

## 3. Lógica de Sincronização Progressiva com o Google Agenda
Para otimizar o consumo de dados móveis, performance de processamento e limites de quota da API do Google Agenda, a sincronização é dividida em duas fases principais.

### Localização do Código
- **Arquivo**: [ProgressiveSyncService.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/service/ProgressiveSyncService.kt)

### Fluxo da Sincronização Progressiva (`syncProgressively`)
- **Fase 1 (Sincronização Rápida - `performQuickSync`)**: Linhas 73 a 172
  - Sincroniza prioritariamente a faixa crítica do calendário: o mês anterior, o mês atual e os dois meses seguintes (linhas 88 a 90).
  - Executa consultas rápidas à API e insere/atualiza essas atividades locais imediatamente para o usuário ver os dados o quanto antes.
  - Sincroniza aniversários dos contatos especiais do celular (`contacts` e `birthdays`) (linhas 400 a 420).
- **Fase 2 (Sincronização em Background - `performBackgroundSync`)**: Linhas 177 a 279
  - Busca os eventos remanescentes do resto do ano (de 2 a 12 meses futuros e 1 a 12 meses passados) sem travar a interface visual do usuário.

### Sincronização Incremental (`fetchEventsForPeriod`)
- **Linhas**: 284 a 345
- Utiliza a propriedade `setUpdatedMin` da API do Google Calendar informando o timestamp da última sincronização bem-sucedida obtida do `SyncRepository`. Isso garante que apenas eventos modificados ou novos desde o último ciclo de sincronização sejam transferidos da nuvem para o celular.

### Conversão de Dados (`convertEventsToActivities`)
- **Linhas**: 350 a 395
- Mapeia o formato do evento do Google (`com.google.api.services.calendar.model.Event`) para o modelo local `Activity` do aplicativo.
- Detecta aniversários automaticamente baseado no fato de ser um evento de dia inteiro com título ou descrição contendo "aniversário" ou "birthday" (linhas 425 a 430).

---

## 4. Sincronização Manual / Automática
No ViewModel, a sincronização pode ser chamada manualmente pelo usuário ou executada em segundo plano automaticamente via worker.

### Localização do Código
- **Arquivo**: [CalendarViewModel.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/ui/viewmodel/CalendarViewModel.kt)
- **Função**: `syncActivitiesWithCloud(providedPassword: String?)` (linhas 3048 a 3090)
  - Chama a sincronização das atividades e lida com exceções de descriptografia (`DecryptionRequiredException`, `DecryptionFailedException`) mostrando o diálogo de senha na tela do celular se necessário.
- **Função**: `scheduleAutomaticSync()` (linhas 3149 a 3169)
  - Agenda uma sincronização diária periódica em segundo plano utilizando `WorkManager` e a classe `GoogleCalendarSyncWorker`, configurada para rodar apenas quando houver conexão com a internet ativa.
