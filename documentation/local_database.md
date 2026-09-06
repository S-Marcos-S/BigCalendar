# Funcionamento do Banco de Dados Local e Repositórios

Este documento descreve detalhadamente o funcionamento do armazenamento de dados local (persistência), o uso de Protocol Buffers e o Jetpack DataStore no aplicativo TheBigCalendar.

---

## 1. Estrutura de Armazenamento Local
O aplicativo não utiliza o banco de dados relacional SQLite tradicional (via Room) para salvar compromissos e tarefas. Em vez disso, adota a biblioteca **Jetpack DataStore**, que fornece uma solução moderna, assíncrona e altamente performática dividida em duas formas de persistência:
1. **DataStore Proto (Protocol Buffers)**: Para persistir coleções complexas estruturadas, como a lista de compromissos, tarefas, feriados e logs de exclusão.
2. **DataStore Preferences (Chave-Valor)**: Para persistir configurações globais do aplicativo (temas, senhas de criptografia, filtros da barra lateral, etc.).

---

## 2. Persistência de Atividades com Protocol Buffers (`ActivityRepository`)
As atividades do calendário são armazenadas em formato binário otimizado usando o arquivo de esquema do Protocol Buffers `activities.pb`.

### Localização do Código
- **Arquivo**: [ActivityRepository.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/data/repository/ActivityRepository.kt)
- **Instanciação do DataStore**: Linhas 20 a 23
  - Define o arquivo de persistência física como `activities.pb` usando a classe `ActivitySerializer` como serializador binário.
- **Obtenção de Dados via Kotlin Flow**: Linhas 27 a 32
  - Expõe a propriedade pública `activities` como um fluxo reativo (`Flow<List<Activity>>`), que emite automaticamente a lista de compromissos decodificada do formato Proto para o modelo Kotlin sempre que houver modificações.

### Lógica de Filtragem e Otimização Mensal (`getActivitiesForMonth`)
- **Linhas**: 38 a 77
- Para evitar sobrecarga de memória na renderização da tela principal do calendário, o aplicativo lê apenas as atividades pertinentes ao mês selecionado pelo usuário.
- Para compromissos recorrentes, o repositório executa a função interna `hasRecurringInstancesInMonth()` (linhas 82 a 129) analisando a regra de recorrência (por hora, diária, semanal, mensal ou anual) para verificar se há pelo menos uma instância válida no intervalo do mês correspondente antes de liberar o evento para a interface gráfica.

### Operações de Escrita (Insert, Update e Delete)
- **Salvar Atividade (`saveActivity`)** (linhas 160 a 172):
  - Executa a escrita assíncrona em bloco transacional através de `updateData {}` no DataStore.
  - Verifica se a atividade já existe pelo ID; se sim, substitui os dados, caso contrário, insere um novo item no array binário.
- **Exclusão de Atividade (`deleteActivity`)** (linhas 190 a 199):
  - Localiza o índice correspondente no builder do Protocol Buffers e remove a entrada física.

---

## 3. Repositório de Configurações (`SettingsRepository`)
Todas as configurações de visualização, escala do calendário, credenciais e criptografia são gerenciadas como pares chave-valor tipados no DataStore Preferences.

### Localização do Código
- **Arquivo**: [SettingsRepository.kt](file:///home/marcos/kotlin_projects/TheBigCalendar/app/src/main/java/com/mss/thebigcalendar/data/repository/SettingsRepository.kt)
- **Instanciação**: Linha 22 (arquivo de preferências físicas `settings.preferences_pb`).

### Parâmetros e Chaves Relevantes
- **isEncryptionEnabled / encryptionPassword** (linhas 66 a 67):
  - Controla o estado de ativação da criptografia local e a senha do usuário em formato string.
  - O método de escrita `saveEncryptionSettings(enabled, password)` (linhas 82 a 87) atualiza ambas as chaves de forma transacional.
- **autoBackupSettings** (linhas 268 a 277):
  - Retorna uma estrutura agrupada com o estado de ativação do backup automático local/nuvem, frequência e o horário configurado.
- **maxLocalBackups / maxCloudBackups** (linhas 111 a 119):
  - Controlam o limite máximo de arquivos de backup a serem preservados no armazenamento para a execução do autolimpador (pruning).
- **sidebarFilterVisibility** (linhas 254 a 267):
  - Mapeia quais categorias de compromissos (feriados, aniversários, notas, etc.) devem ser exibidas no menu lateral do calendário.
